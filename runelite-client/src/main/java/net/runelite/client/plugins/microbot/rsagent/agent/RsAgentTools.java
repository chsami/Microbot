package net.runelite.client.plugins.microbot.rsagent.agent;

import lombok.Getter;
import net.runelite.api.NPC;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class RsAgentTools {
    /**
     * Walks to the specified world coordinates.
     *
     * @param x The x-coordinate.
     * @param y The y-coordinate.
     * @param z The plane (z-coordinate).
     * @return true if the walk action was initiated, false otherwise.
     */
    static public boolean walkTo(int x, int y, int z){
        return Rs2Walker.walkTo(x, y, z);
    }

    /**
     * Placeholder method for navigating to a specific city.
     * Currently does nothing.
     *
     * @param name The name of the city.
     */
    static public void goToCity(String name){
        // Implementation needed
    }

    /**
     * Gets a WorldPoint representing a specific tile within a given region ID.
     * Defaults to tile (1, 1) on plane 0 within that region.
     *
     * @param regionId The ID of the region.
     * @return A WorldPoint within the specified region.
     */
    static public WorldPoint getPointFromRegionId(int regionId)
    {
        return WorldPoint.fromRegion(regionId,1 ,1,0);
    }

    /**
     * Finds the WorldPoint from an array that is closest to the player's current location.
     *
     * @param points An array of WorldPoints to check.
     * @return The WorldPoint from the array closest to the player, or null if the input array is empty.
     */
    static public WorldPoint getClosestPointFromPlayer(WorldPoint[] points){
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
    static public boolean followPlayerByName(String name){
        var player  = Rs2Player.getPlayer(name);
        return Rs2Player.follow(player);
    }

    /**
     * Finds an NPC by name and interacts with the specified action.
     *
     * @param name The name of the NPC to interact with.
     * @param action The action to perform (e.g. "Trade", "Attack").
     * @return true if the interaction was successful, false otherwise.
     */
    static public boolean interactWith(String name, String action) {
        NPC npc = Rs2Npc.getNpc(name);
        if (npc != null) {
            return Rs2Npc.interact(new Rs2NpcModel(npc), action);
        }
        return false;
    }

    /**
     * Finds an item on the ground by name within a default radius and interacts with it using the "Take" action.
     *
     * @param name The name of the item to pick up.
     * @return true if the item was found and the "Take" action was initiated, false otherwise.
     */
    static public boolean pickupGroundItem(String name) {
        return Rs2GroundItem.loot(name, 255); // 255 tile radius
    }

    /**
     * Helper class to store the result of handling a dialogue.
     */
    @Getter
    public static class DialogueResult {
        public final List<String> dialogueTexts;
        public final List<String> options;

        DialogueResult(List<String> dialogueTexts, List<String> options) {
            this.dialogueTexts = dialogueTexts != null ? dialogueTexts : Collections.emptyList();
            this.options = options != null ? options : Collections.emptyList();
        }

        public boolean hasOptions() {
            return !options.isEmpty();
        }
    }

    /**
     * Chooses the option passed in by index 1-n and then continues dialogue
     * @param option Index of option to choose
     * @return A DialogueResult containing conversation
     */
    static public DialogueResult chooseOptionAndContinueDialogue(int option){
        Rs2Dialogue.keyPressForDialogueOption(option);
        return handleDialogue();
    }

    /**
     * Handles the flow of dialogue by clicking "continue" until options are presented or the dialogue ends.
     * Captures the text of each dialogue screen that is continued through.
     *
     * @return A DialogueResult containing the list of captured dialogue texts and the list of options if presented,
     *         or an empty list for options if the dialogue ended without choices. Returns null if not in dialogue.
     */
    static public DialogueResult handleDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return null; // Not in dialogue
        }

        List<String> dialogueTexts = new ArrayList<>();

        // Keep clicking continue while available
        while (Rs2Dialogue.hasContinue()) {
            String currentText = Rs2Dialogue.getDialogueText(); // Get text before clicking
            if (currentText != null && !currentText.trim().isEmpty()) {
                dialogueTexts.add(currentText);
            }
            System.out.println(currentText);

            Rs2Dialogue.clickContinue();
            sleep(500, 1000); // Small extra delay
            // Wait until the continue button is gone, chat ended, or options appear
            sleepUntil(() -> Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption() || !Rs2Dialogue.isInDialogue());
        }

        // Check if options are presented
        if (Rs2Dialogue.hasSelectAnOption()) {
            List<String> options = Rs2Dialogue.getDialogueOptions().stream()
                    .map(Widget::getText)
                    .collect(Collectors.toList());

            return new DialogueResult(dialogueTexts, options);
        } else {
            // Dialogue ended without options
            // Check if there was one final dialogue screen (e.g., NPC text without continue)
            String finalText = Rs2Dialogue.getDialogueText();
            if (finalText != null && !finalText.trim().isEmpty()) {
                dialogueTexts.add(finalText);
            }
            return new DialogueResult(dialogueTexts, Collections.emptyList());
        }
    }
}
