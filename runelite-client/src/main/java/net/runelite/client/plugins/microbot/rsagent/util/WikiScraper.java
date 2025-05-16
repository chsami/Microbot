package net.runelite.client.plugins.microbot.rsagent.util;

import net.runelite.client.plugins.microbot.Microbot;
import org.slf4j.event.Level;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WikiScraper {

    private static final String WIKI_BASE_URL = "https://oldschool.runescape.wiki/w/";
    private static final int TIMEOUT_MS = 10000; // 10 seconds timeout

    // Basic regex to strip HTML tags. This is very naive and won't handle all
    // cases.
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]*>");
    // Pattern to find the main content area using the "mw-parser-output" class.
    // This is specific to OSRSWiki structure and might break if the structure
    // changes.
    // More robust parsing would require a proper HTML parser.
    private static final Pattern MAIN_CONTENT_PATTERN = Pattern
            .compile("<div class=\"mw-parser-output\"[^>]*>(.*?)<div id=\"catlinks\"", Pattern.DOTALL);
    private static final Pattern BODY_CONTENT_PATTERN = Pattern
            .compile("<div id=\"bodyContent\"[^>]*>(.*?)<div id=\"catlinks\"", Pattern.DOTALL);

    /**
     * Fetches and extracts text content from an Old School RuneScape Wiki page.
     *
     * @param pageTitle The title of the wiki page (e.g., "Cook's Assistant").
     * @return The extracted text content, or an error message if fetching/parsing
     *         fails.
     */
    public static String fetchWikiPageContent(String pageTitle) {
        HttpURLConnection connection = null;
        try {
            String encodedPageTitle = URLEncoder.encode(pageTitle.replace(" ", "_"), StandardCharsets.UTF_8.toString());
            URL url = new URL(WIKI_BASE_URL + encodedPageTitle);

            Microbot.log(Level.INFO, "Fetching OSRS Wiki page: " + url.toString());

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "RuneLite RSAgent/1.0");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String htmlContent = reader.lines().collect(Collectors.joining("\n"));
                    return parseHtmlContent(htmlContent, pageTitle);
                }
            } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                Microbot.log(Level.WARN, "OSRS Wiki page not found (404): " + pageTitle);
                return "Error: Wiki page '" + pageTitle + "' not found.";
            } else {
                Microbot.log(Level.ERROR,
                        "Failed to fetch OSRS Wiki page '" + pageTitle + "'. HTTP Response Code: " + responseCode);
                return "Error: Failed to fetch wiki page '" + pageTitle + "'. HTTP " + responseCode;
            }
        } catch (java.net.UnknownHostException e) {
            Microbot.log(Level.ERROR,
                    "Error fetching OSRS Wiki page '" + pageTitle + "': Unknown host. Check internet connection.", e);
            return "Error: Could not connect to the OSRS Wiki. Please check your internet connection.";
        } catch (java.io.IOException e) {
            Microbot.log(Level.ERROR, "Error fetching OSRS Wiki page '" + pageTitle + "': " + e.getMessage(), e);
            return "Error: Could not retrieve content from wiki page '" + pageTitle + "'. IO Exception: "
                    + e.getMessage();
        } catch (Exception e) {
            Microbot.log(Level.ERROR, "Unexpected error fetching OSRS Wiki page '" + pageTitle + "': " + e.getMessage(),
                    e);
            return "Error: An unexpected error occurred while fetching wiki page '" + pageTitle + "'. "
                    + e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * A very basic HTML parser to extract text content.
     * Attempts to find the main content div and strips HTML tags.
     *
     * @param htmlContent The full HTML content of the page.
     * @param pageTitle   The title of the page (for logging).
     * @return Extracted plain text or a message if content extraction fails.
     */
    private static String parseHtmlContent(String htmlContent, String pageTitle) {
        Matcher mainContentMatcher = MAIN_CONTENT_PATTERN.matcher(htmlContent);
        String targetedContent = null;

        if (mainContentMatcher.find()) {
            targetedContent = mainContentMatcher.group(1);
        } else {
            Matcher bodyContentMatcher = BODY_CONTENT_PATTERN.matcher(htmlContent);
            if (bodyContentMatcher.find()) {
                targetedContent = bodyContentMatcher.group(1);
            } else {
                Microbot.log(Level.WARN,
                        "Could not find main content div (class=\"mw-parser-output\" or id=\"bodyContent\") for wiki page: "
                                + pageTitle
                                + ". Using full HTML body (tags stripped).");
                // Fallback to using a larger portion if specific divs aren't found.
                // This might include more noise.
                int bodyStart = htmlContent.toLowerCase().indexOf("<body");
                int bodyEnd = htmlContent.toLowerCase().lastIndexOf("</body>");
                if (bodyStart != -1 && bodyEnd != -1 && bodyEnd > bodyStart) {
                    targetedContent = htmlContent.substring(bodyStart, bodyEnd + 7);
                } else {
                    targetedContent = htmlContent; // Full content as last resort
                }
            }
        }

        if (targetedContent == null || targetedContent.trim().isEmpty()) {
            Microbot.log(Level.WARN,
                    "Extracted content for wiki page '" + pageTitle + "' is empty before tag stripping.");
            return "Error: No meaningful content could be extracted from wiki page '" + pageTitle
                    + "' after targeting content areas.";
        }

        // Strip HTML tags
        String textOnly = HTML_TAG_PATTERN.matcher(targetedContent).replaceAll("");

        // Further clean-up:
        // Replace multiple newlines/whitespace with a single space or newline
        textOnly = textOnly.replaceAll("\\s{2,}", " ").trim(); // Collapse multiple whitespace to single space
        textOnly = textOnly.replaceAll("(\r\n|\r|\n){2,}", "\n").trim(); // Collapse multiple newlines

        // Decode HTML entities (very basic set)
        textOnly = textOnly.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&#160;", " "); // Non-breaking space

        if (textOnly.length() > 4000) { // Limit length to avoid excessive output
            textOnly = textOnly.substring(0, 3997) + "...";
            Microbot.log(Level.INFO, "Wiki content for '" + pageTitle + "' was truncated to 4000 characters.");
        }

        if (textOnly.trim().isEmpty()) {
            Microbot.log(Level.WARN,
                    "Content for wiki page '" + pageTitle + "' became empty after stripping HTML tags and cleaning.");
            return "Error: Content extracted from wiki page '" + pageTitle + "' was empty after processing.";
        }

        Microbot.log(Level.INFO,
                "Successfully parsed content for OSRS Wiki page: " + pageTitle + ". Length: " + textOnly.length());
        return "Content for '" + pageTitle + "':\n" + textOnly;
    }
}