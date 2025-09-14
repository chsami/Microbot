package net.runelite.client.plugins.microbot.util.discord;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.discord.models.DiscordEmbed;
import net.runelite.client.plugins.microbot.util.discord.models.DiscordPayload;
import net.runelite.client.plugins.microbot.util.security.Login;
import okhttp3.*;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Rs2Discord {

    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final Gson GSON = new Gson();

    /**
     * Sends a message to the configured Webhook URL with optional embeds and files as attachments.
     *
     * @param bodyMessage The message content
     * @param embeds      The list of Discord embeds
     * @param files       The list of file paths to upload as attachments
     * @return boolean
     */
    public static boolean sendWebhookMessage(String bodyMessage, List<DiscordEmbed> embeds, List<String> files) {
        // Retrieve and validate the Discord Webhook URL
        String webHookUrl = Optional.ofNullable(getDiscordWebhookUrl())
                .filter(url -> !url.isEmpty())
                .orElseGet(() -> {
                    Microbot.log("The webhook URL is not configured in the RuneLite profile. Please check the configuration.");
                    return null;
                });

        if (webHookUrl == null) return false;

        // Create the payload
        DiscordPayload payload = new DiscordPayload(bodyMessage, embeds);
        String jsonPayload = GSON.toJson(payload);

        // Build the multipart request body
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", jsonPayload);

        // Add files to the request
        files.stream()
                .map(File::new)
                .filter(File::exists)
                .forEach(file -> {
                    try {
                        String mimeType = Optional.ofNullable(Files.probeContentType(file.toPath()))
                                .orElse("application/octet-stream");
                        builder.addFormDataPart(
                                "file",
                                file.getName(),
                                RequestBody.create(MediaType.parse(mimeType), file)
                        );
                    } catch (IOException e) {
                        Microbot.log("Failed to determine MIME type for file: " + file.getPath() + " - " + e.getMessage());
                    }
                });

        RequestBody requestBody = builder.build();

        // Build and execute the request
        Request request = new Request.Builder()
                .url(webHookUrl)
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                Microbot.log("Failed to send Discord notification. Error Code: " + response.code());
            }
            return response.isSuccessful();
        } catch (IOException e) {
            Microbot.log("Error while sending Discord notification: " + e.getMessage());
            return false;
        }
    }

    /**
     * Overloaded method to send a message with optional embeds.
     *
     * @param bodyMessage The message content
     * @param embeds      The list of Discord embeds
     * @return boolean
     */
    public static boolean sendWebhookMessage(String bodyMessage, List<DiscordEmbed> embeds) {
        return sendWebhookMessage(bodyMessage, embeds, Collections.emptyList());
    }

    /**
     * Overloaded method to send a plain message.
     *
     * @param bodyMessage The message content
     * @return boolean
     */
    public static boolean sendWebhookMessage(String bodyMessage) {
        return sendWebhookMessage(bodyMessage, Collections.emptyList(), Collections.emptyList());
    }

    /**
     * Gets the Discord Webhook URL from the active RuneLite profile.
     *
     * @return String
     */
    private static String getDiscordWebhookUrl() {
        return Optional.ofNullable(Login.activeProfile)
                .map(ConfigProfile::getDiscordWebhookUrl)
                .orElse(null);
    }

    /**
     * Converts a hex color code to an integer representation compatible with embed.setColor().
     * The input can be in the format "#RRGGBB" or "RRGGBB".
     *
     * @param hexCode the hex color code as a String, e.g., "#FF5733" or "FF5733"
     * @return an integer representation of the color, e.g., 0xFF5733
     * @throws NumberFormatException if the hexCode is not a valid hex color
     */
    public static int convertHexToInt(String hexCode) {
        if (hexCode.startsWith("#")) {
            hexCode = hexCode.substring(1);
        }

        return Integer.parseInt(hexCode, 16);
    }

    /**
     * Converts a java.awt.Color object to an integer representation compatible with embed.setColor().
     *
     * @param color the Color object to convert
     * @return an integer representation of the color in the format 0xRRGGBB
     */
    public static int convertColorToInt(Color color) {
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();

        // Shift the red, green, and blue components to create the integer color representation
        return (red << 16) | (green << 8) | blue;
    }

    /**
     * sends a custom notification with configurable title, description, and color
     *
     * @param title the notification title
     * @param description the notification description
     * @param color the embed color (hex format)
     * @param playerName the player name to include
     * @param source the source of the notification
     * @return true if notification was sent successfully
     */
    public static boolean sendCustomNotification(String title, String description, int color, String playerName, String source) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        DiscordEmbed embed = new DiscordEmbed();
        embed.setTitle(title);
        embed.setDescription(description);
        embed.setColor(color);
        embed.addField(new DiscordEmbed.Field("Player", 
                playerName.isEmpty() ? "Unknown" : playerName, true));
        embed.addField(new DiscordEmbed.Field("Time", timestamp, true));
        embed.addField(new DiscordEmbed.Field("Source", source, true));
        embed.setFooter(new DiscordEmbed.Footer("Microbot", null));

        return sendWebhookMessage("", Collections.singletonList(embed));
    }

    /**
     * sends a notification with custom fields
     *
     * @param title the notification title
     * @param description the notification description
     * @param color the embed color
     * @param fields list of custom fields to add
     * @param footerText custom footer text
     * @return true if notification was sent successfully
     */
    public static boolean sendNotificationWithFields(String title, String description, int color, 
                                                   java.util.List<DiscordEmbed.Field> fields, String footerText) {
        DiscordEmbed embed = new DiscordEmbed();
        embed.setTitle(title);
        embed.setDescription(description);
        embed.setColor(color);
        
        // add custom fields
        if (fields != null) {
            for (DiscordEmbed.Field field : fields) {
                embed.addField(field);
            }
        }
        
        embed.setFooter(new DiscordEmbed.Footer(footerText != null ? footerText : "Microbot", null));

        return sendWebhookMessage("", Collections.singletonList(embed));
    }

    /**
     * sends a simple text message notification
     *
     * @param message the message to send
     * @param playerName the player name to include
     * @param source the source of the notification
     * @return true if notification was sent successfully
     */
    public static boolean sendSimpleNotification(String message, String playerName, String source) {
        return sendCustomNotification("📢 Notification", message, 0x3498DB, playerName, source);
    }

    /**
     * sends an alert notification with customizable icon and color
     *
     * @param alertType the type of alert (e.g., "ERROR", "WARNING", "SUCCESS")
     * @param message the alert message
     * @param color the embed color
     * @param playerName the player name
     * @param source the source of the alert
     * @return true if notification was sent successfully
     */
    public static boolean sendAlert(String alertType, String message, int color, String playerName, String source) {
        String icon = getIconForAlertType(alertType);
        String title = icon + " " + alertType.toUpperCase();
        return sendCustomNotification(title, message, color, playerName, source);
    }

    /**
     * gets appropriate icon for alert type
     */
    private static String getIconForAlertType(String alertType) {
        switch (alertType.toUpperCase()) {
            case "ERROR": return "❌";
            case "WARNING": return "⚠️";
            case "SUCCESS": return "✅";
            case "INFO": return "ℹ️";
            case "BAN": return "🚫";
            case "SHUTDOWN": return "🔴";
            default: return "📢";
        }
    }

    /**
     * creates a field for discord embed
     */
    public static DiscordEmbed.Field createField(String name, String value, boolean inline) {
        return new DiscordEmbed.Field(name, value, inline);
    }

    /**
     * creates a timestamp field for current time
     */
    public static DiscordEmbed.Field createTimestampField() {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return createField("Time", timestamp, true);
    }

    /**
     * creates a player field
     */
    public static DiscordEmbed.Field createPlayerField(String playerName) {
        return createField("Player", playerName.isEmpty() ? "Unknown" : playerName, true);
    }
}
