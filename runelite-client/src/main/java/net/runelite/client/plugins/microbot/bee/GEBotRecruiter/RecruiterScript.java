package net.runelite.client.plugins.microbot.bee.GEBotRecruiter;

import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.RandomUtils;

import javax.inject.Inject;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class RecruiterScript extends Script {

    @Inject
    private Client client;
    private ScheduledFuture<?> mainScheduledFuture;
    private Set<String> recruitedPlayers = new HashSet<>(); // Track players we've already interacted with

    @Inject
    public RecruiterScript(Client client) {
        this.client = client;
        this.recruitedPlayers = new HashSet<>();  // Initialize it properly here too
    }

    private Player localPlayer;
    private final WorldArea GEarea = new WorldArea(3153, 3478, 24, 23, 0);

    public boolean run(RecruiterConfig config) {

        Microbot.enableAutoRunOn = false;
        localPlayer = client.getLocalPlayer(); // Access localPlayer here, not in the constructor

        if (recruitedPlayers == null) {
            recruitedPlayers = new HashSet<>();
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                // If "recruit" is disabled in config, skip execution
                if (!config.recruit() && !config.sendMessage()) {
                    System.out.println("No options selected");
                    return;
                }

                if (client == null) {
                    System.out.println("Client is null! Ensure proper injection.");
                    return;
                }

                if (!Microbot.isLoggedIn()) {
                    System.out.println("Not logged in");
                    return;
                }
                if (config.recruit()) {
                    checkAndReturnToGeArea();

                    // Fetch all players in the local area
                    List<Rs2PlayerModel> localPlayers = Rs2Player.getPlayers(p -> true).collect(Collectors.toList());
                    System.out.println("Found " + localPlayers.size() + " players.");
                    // Calculate and print the statistics
                    int recruitedCount = recruitedPlayers.size();
                    int foundInRecruited = (int) localPlayers.stream().filter(p -> recruitedPlayers.contains(p.getName())).count();
                    int difference = localPlayers.size() - foundInRecruited;

                    System.out.println("Total recruited players in HashSet: " + recruitedCount);
                    System.out.println("Players found in both local and recruited sets: " + foundInRecruited);
                    System.out.println("Difference in count between found players and recruited HashSet: " + difference);

                    // Loop through all the players and attempt to recruit them (one player at a time)
                    for (Rs2PlayerModel player : localPlayers) {
                        if (player == null || player.getName() == null || recruitedPlayers.contains(player.getName())) {
                            continue; // Skip invalid, null, or already recruited players
                        }

                        System.out.println("Processing player: " + player.getName());


                        Rs2Camera.turnTo(player);

                        sleep(1000);

                        // Simulate hovering over the player
                        moveMouseToPlayer(player);

                        // Find the "Recruit" action in the right-click menu
                        MenuAction menuAction = findRecruitAction();

                        // If the "Recruit" option is available, invoke it
                        if (menuAction != null) {
                            // Create a new menu entry for the "Recruit" action
                            NewMenuEntry recruitMenuEntry = new NewMenuEntry(
                                    "Recruit",                // The action name (Recruit)
                                    player.getName(),          // The player's name
                                    player.getId(),            // The player's identifier
                                    MenuAction.of(menuAction.getId()),        // The MenuAction ID (PLAYER_FIFTH_OPTION for Recruit)
                                    0,                         // param0 (set to 0 as seen in the menu entry data)
                                    0,                         // param1 (set to 0 as seen in the menu entry data)
                                    false                      // forceLeftClick (false, since it's not forced left-click)
                            );

                            // Set this menu entry as the target menu in Microbot
                            Microbot.targetMenu = recruitMenuEntry;

                            // Invoke the "Recruit" action programmatically
                            Microbot.doInvoke(recruitMenuEntry, player.getCanvasTilePoly().getBounds());
                            System.out.println("Invoked 'Recruit' on player: " + player.getName());

                            System.out.println("the sleep before pressing 1");
                            inviteToClanWithRetry(player.getName());

                            // Add the player to the recruited list to avoid interacting with them again
                            recruitedPlayers.add(player.getName());
                        }

                        final Random random = new Random();

                        System.out.println("entering loop sleep");
                        if (random.nextInt(5) == 0) {
                            if (config.sendMessage()) {
                                sleep(5000);
                                sendMessage(config.customMessage());
                                return;
                            }
                        }
                        break; // Process only one player at a time, then wait for the next run
                    }
                }

                if (config.sendMessage() && !config.recruit()){
                    final Random random = new Random();

                    System.out.println("entering loop sleep");
                    if (random.nextInt(5) == 0) {
                        if (config.sendMessage()) {
                            sleep(5000);
                            sendMessage(config.customMessage());
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS); // Repeat every 2 seconds
        return true;
    }

    private boolean isInGeArea() {
        // Check if player is in the defined monk area
        return GEarea.contains(localPlayer.getWorldLocation());
    }

    public void checkAndReturnToGeArea() {
        // If player is outside the GE area, select a random point within it to walk back
        if (!isInGeArea()) {
            WorldPoint randomPoint = getRandomPointInGeArea();
            Rs2Walker.walkTo(randomPoint);
        }
    }

    private final Random random = new Random();

    private WorldPoint getRandomPointInGeArea() {
        int x = GEarea.getX() + random.nextInt(GEarea.getWidth());
        int y = GEarea.getY() + random.nextInt(GEarea.getHeight());
        return new WorldPoint(x, y, GEarea.getPlane());
    }

    public void sendMessage(String message) {
            if (message.length() > 80) {
                message = message.substring(0, 80);
            }
            System.out.println("Sending message: " + message);

            // Type the message in the chatbox
            Rs2Keyboard.typeString(message);

            // Press Enter to send the message
            Rs2Keyboard.enter();

            sleep(1000);
        }

    public void inviteToClanWithRetry(String playerName) {
        for (int attempt = 0; attempt < 3; attempt++) {
            sleep(600);

            boolean clicked = inviteToClan(playerName); // Call method and check result
            if (clicked) {
                System.out.println("Successfully clicked 'Invite to join the clan' for " + playerName);
                break; // Exit loop on success
            } else {
                sleep(1000, 1500);
                System.out.println("Attempt " + (attempt + 1) + " failed to click 'Invite to join the clan' for " + playerName);
            }
        }
    }


    public boolean inviteToClan(String playerName) {
        CompletableFuture<Boolean> resultFuture = new CompletableFuture<>();

        Microbot.getClientThread().invoke(() -> {
            System.out.println("Attempting to click 'Invite to join the clan' for player: " + playerName);

            // Construct the full widget text with the player name included
            String inviteText = "Invite " + playerName + " to join the clan.";

            // Use Rs2Widget to find and click the widget with this text
            boolean clicked = Rs2Widget.clickWidget(inviteText);

            if (clicked) {
                System.out.println("Successfully clicked 'Invite to join the clan' for " + playerName);
            } else {
                System.out.println("Failed to click 'Invite to join the clan' for " + playerName);
            }

            resultFuture.complete(clicked); // Complete the future with the result
        });

        try {
            return resultFuture.get(); // Wait for and return the result
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Simulates hovering the mouse over the player to trigger menu population.
     */
    private void moveMouseToPlayer(Player player) {
        if (player == null) {
            return;
        }

        // Get the player's bounding box (canvas tile poly)
        Shape playerBounds = player.getCanvasTilePoly();
        if (playerBounds != null) {
            Rectangle playerRectangle = playerBounds.getBounds();
            Point randomPoint = getRandomPointInRectangle(playerRectangle); // Get a random point within the player's bounds

            // Move the mouse to this point (hover)
            if (randomPoint != null) {
                System.out.println("Hovering over player: " + player.getName());
                Microbot.getMouse().move(randomPoint.getX(), randomPoint.getY());
                sleep(500); // Small delay to allow the menu to populate
            }
        }
    }

    /**
     * Generates a random point within a rectangle for mouse hover.
     */
    private Point getRandomPointInRectangle(Rectangle rectangle) {
        int x = rectangle.x + RandomUtils.nextInt(0, rectangle.width);
        int y = rectangle.y + RandomUtils.nextInt(0, rectangle.height);
        return new Point(x, y);
    }

    /**
     * Attempts to find the "Recruit" action in the player's right-click menu.
     * This returns the MenuAction corresponding to the "Recruit" option.
     */
    private MenuAction findRecruitAction() {
        MenuEntry[] menuEntries = Microbot.getClient().getMenuEntries();
        for (MenuEntry entry : menuEntries) {
            if ("Recruit".equals(entry.getOption())) {
                return entry.getType(); // Return the MenuAction if "Recruit" is found
            }
        }
        return null; // Return null if the "Recruit" option is not found
    }

    @Override
    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        System.out.println("Script shutdown");
    }
}