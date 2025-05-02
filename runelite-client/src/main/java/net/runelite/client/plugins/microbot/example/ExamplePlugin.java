package net.runelite.client.plugins.microbot.example;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Example",
        description = "Microbot example plugin",
        tags = {"example", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class ExamplePlugin extends Plugin {
    @Inject
    private ExampleConfig config;
    @Provides
    ExampleConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ExampleConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ExampleOverlay exampleOverlay;

    @Inject
    ExampleScript exampleScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(exampleOverlay);
        }
        exampleScript.run(config);
    }

    protected void shutDown() {
        exampleScript.shutdown();
        overlayManager.remove(exampleOverlay);
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

    /**
     * Walks to the specified world coordinates.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The plane (z-coordinate).
     * @return true if the walk action was initiated, false otherwise.
     */
    private boolean walkTo(int x, int y, int z){
        return Rs2Walker.walkTo(x, y, z);
    }

    /**
     * Placeholder method for navigating to a specific city.
     * Currently does nothing.
     *
     * @param name The name of the city.
     */
    private void goToCity(String name){
        // Implementation needed
    }

    /**
     * Gets a WorldPoint representing a specific tile within a given region ID.
     * Defaults to tile (1, 1) on plane 0 within that region.
     *
     * @param regionId The ID of the region.
     * @return A WorldPoint within the specified region.
     */
    private WorldPoint getPointFromRegionId(int regionId)
    {
        return WorldPoint.fromRegion(regionId,1 ,1,0);
    }

    /**
     * Finds the WorldPoint from an array that is closest to the player's current location.
     *
     * @param points An array of WorldPoints to check.
     * @return The WorldPoint from the array closest to the player, or null if the input array is empty.
     */
    private WorldPoint getClosestPointFromPlayer(WorldPoint[] points){
        WorldPoint playerPoint = Rs2Player.getLocalPlayer().getWorldLocation();
        WorldPoint closestPoint = null;
        int closestDistance = Integer.MAX_VALUE;
        if (points == null || points.length == 0) {
            return null;
        }
        for (WorldPoint point : points) {
            int distance = playerPoint.distanceTo(point);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPoint = point;
            }
        }
        return closestPoint;
    }

    /**
     * Attempts to follow another player specified by their in-game name.
     *
     * @param name The name of the player to follow.
     * @return true if the follow action was initiated, false if the player was not found or couldn't be followed.
     */
    private boolean followPlayerByName(String name){
        var player  = Rs2Player.getPlayer(name);
        return Rs2Player.follow(player);
    }

    /**
     * Finds an NPC by name and interacts with the specified action.
     *
     * @param name The name of the NPC to interact with.
     * @param action The action to perform (e.g., "Talk-to", "Trade", "Attack").
     * @return true if the interaction was successful, false otherwise.
     */
    private boolean interactWith(String name, String action) {
        NPC npc = Rs2Npc.getNpc(name);
        if (npc != null) {
            return Rs2Npc.interact(npc, action);
        }
        return false;
    }

    /**
     * Helper class to store the result of handling a dialogue.
     */
    @Getter
    public static class DialogueResult {
        private final List<String> dialogueTexts;
        private final List<String> options;

        DialogueResult(List<String> dialogueTexts, List<String> options) {
            this.dialogueTexts = dialogueTexts != null ? dialogueTexts : Collections.emptyList();
            this.options = options != null ? options : Collections.emptyList();
        }

        public boolean hasOptions() {
            return !options.isEmpty();
        }
    }

    /**
     * Handles the flow of dialogue by clicking "continue" until options are presented or the dialogue ends.
     * Captures the text of each dialogue screen that is continued through.
     *
     * @return A DialogueResult containing the list of captured dialogue texts and the list of options if presented,
     *         or an empty list for options if the dialogue ended without choices. Returns null if not in dialogue.
     */
    private DialogueResult handleDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return null; // Not in dialogue
        }

        List<String> dialogueTexts = new ArrayList<>();

        // Keep clicking continue while available
        while (Rs2Dialogue.hasContinue()) {
            String currentText = Rs2Dialogue.getDialogText(); // Get text before clicking
            if (currentText != null && !currentText.trim().isEmpty()) {
                dialogueTexts.add(currentText);
            }

            Rs2Dialogue.clickContinue();
            // Wait until the continue button is gone or options appear
            Microbot.sleepUntil(() -> !Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption());
            Microbot.sleep(50, 100); // Small extra delay
        }

        // Check if options are presented
        if (Rs2Dialogue.hasSelectAnOption()) {
            List<String> options = Rs2Dialogue.getDialogOptions();
            return new DialogueResult(dialogueTexts, options);
        } else {
            // Dialogue ended without options
            // Check if there was one final dialogue screen (e.g., NPC text without continue)
            String finalText = Rs2Dialogue.getDialogText();
            if (finalText != null && !finalText.trim().isEmpty()) {
                 dialogueTexts.add(finalText);
            }
            return new DialogueResult(dialogueTexts, Collections.emptyList());
        }
    }
}
