package net.runelite.client.plugins.microbot.rsagent.agent;

import lombok.Getter;
import net.runelite.api.NPC;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.event.Level;

import java.awt.event.KeyEvent;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.*;

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
     * Finds an NPC by name, interacts with "Talk-to", and handles the initial dialogue.
     *
     * @param name The name of the NPC to talk to.
     * @return A DialogueResult containing the initial conversation, or null if the NPC wasn't found or dialogue didn't start.
     */
    static public DialogueResult talkToNpc(String name) {
        NPC npc = Rs2Npc.getNpc(name);
        if (npc == null) {
            throw new RuntimeException("NPC " + name + " not found");
        }
        boolean walkedTo = Rs2Npc.walkToNearestMonster(name);
        if (!walkedTo) {
            throw  new RuntimeException("Cannot walk to NPC " + name);
        }
        boolean interacted = Rs2Npc.interact(new Rs2NpcModel(npc), "Talk-to");
        if (!interacted) {
            throw new RuntimeException("Couldn't interact with NPC"); // Interaction failed
        }

        // Wait for dialogue to appear
        boolean dialogueStarted = sleepUntil(Rs2Dialogue::isInDialogue, 5000); // Wait up to 5 seconds
        if (!dialogueStarted) {
            throw  new RuntimeException("Couldn't start dialogue");
        }

        // Handle the dialogue that appears
        return handleDialogue();
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
     * Captures the text of each dialogue screen that is continued through, prefixed by the speaker's name.
     *
     * @return A DialogueResult containing the list of captured dialogue texts (prefixed) and the list of options if presented,
     *         or an empty list for options if the dialogue ended without choices. Returns null if not in dialogue.
     */
    static public DialogueResult handleDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return null; // Not in dialogue
        }

        List<String> dialogueTexts = new ArrayList<>();

        // Keep clicking continue while available
        while (Rs2Dialogue.hasContinue()) {
            String npcName = Rs2Dialogue.getNpcNameInDialogue();
            String playerText = Rs2Dialogue.getPlayerDialogueText();
            String generalText = Rs2Dialogue.getDialogueText(); // Fallback or for other types
            String speaker = "Game"; // Default speaker if not identified
            String currentText = null;

            if (npcName != null) {
                speaker = npcName;
                // Prefer general text if NPC name is present, assuming it's the NPC's line
                currentText = generalText;
            } else if (playerText != null) {
                speaker = "Player";
                currentText = playerText;
            } else if (generalText != null) {
                // Use general text if neither specific type was found
                currentText = generalText;
                // We might not know the speaker here, keep default or leave as is
            }

            if (currentText != null && !currentText.trim().isEmpty()) {
                String prefixedText = speaker + ": " + currentText;
                dialogueTexts.add(prefixedText);
                System.out.println(prefixedText); // Log the prefixed text
            } else {
                 System.out.println("Could not capture dialogue text this iteration.");
            }


            Rs2Dialogue.clickContinue();
            sleep(500, 1000); // Small extra delay
            // Wait until the continue button is gone, chat ended, or options appear
            sleepUntil(() -> !Rs2Dialogue.hasContinue() || Rs2Dialogue.hasSelectAnOption() || !Rs2Dialogue.isInDialogue(), 2000); // Added timeout
        }

        // Check if options are presented
        if (Rs2Dialogue.hasSelectAnOption()) {
            List<String> options = Rs2Dialogue.getDialogueOptions().stream()
                    .map(Widget::getText)
                    .collect(Collectors.toList());

            // Capture the question text if available and prefix it
            String question = Rs2Dialogue.getQuestion();
            if (question != null && !question.trim().isEmpty()) {
                 // Assume question is asked by the last speaker or system if unknown
                 String lastSpeaker = "Game"; // Default if no prior text
                 if (!dialogueTexts.isEmpty()) {
                     String lastLine = dialogueTexts.get(dialogueTexts.size() - 1);
                     if (lastLine.contains(":")) {
                         lastSpeaker = lastLine.substring(0, lastLine.indexOf(':'));
                     }
                 } else {
                     // If no prior text, check if NPC name is visible now
                     String npcNameNow = Rs2Dialogue.getNpcNameInDialogue();
                     if (npcNameNow != null) lastSpeaker = npcNameNow;
                 }
                 dialogueTexts.add(lastSpeaker + " (Question): " + question);
                 System.out.println(lastSpeaker + " (Question): " + question);
            }


            return new DialogueResult(dialogueTexts, options);
        } else {
            // Dialogue ended without options
            // Check if there was one final dialogue screen (e.g., NPC text without continue)
            String npcName = Rs2Dialogue.getNpcNameInDialogue();
            String playerText = Rs2Dialogue.getPlayerDialogueText();
            String generalText = Rs2Dialogue.getDialogueText();
            String speaker = "System";
            String finalText = null;

             if (npcName != null) {
                speaker = npcName;
                finalText = generalText;
            } else if (playerText != null) {
                speaker = "Player";
                finalText = playerText;
            } else if (generalText != null) {
                finalText = generalText;
            }

            if (finalText != null && !finalText.trim().isEmpty()) {
                 String prefixedText = speaker + ": " + finalText;
                 dialogueTexts.add(prefixedText);
                 System.out.println(prefixedText);
            }
            return new DialogueResult(dialogueTexts, Collections.emptyList());
        }
    }

    static public String checkQuestStatus(String questName){
        Rs2Tab.switchToQuestTab();
        sleep(100,200);
        var questBox = Rs2Widget.getWidget(ComponentID.QUEST_LIST_BOX);
        var searchQuests = questBox.getChild(0);
        Rs2Widget.clickWidget(searchQuests);
        sleepUntilTrue(()->Rs2Widget.isWidgetVisible(ComponentID.CHATBOX_CONTAINER), 100, 1000);
        Rs2Keyboard.typeString(questName);
        sleepUntilTrue(()->Rs2Widget.isWidgetVisible(399,7),100,1000);

        Rs2Widget.clickWidget(questName, Optional.of(399), 7, false);
        sleepUntilTrue(()->Rs2Widget.isWidgetVisible(119,5),100,1000);

//        var widget = Rs2Widget.getWidget(119,5);
//        if (widget == null) {
//            Microbot.log(Level.ERROR,
//                    "Quest log not opened");
//            throw new RuntimeException("Quest log not opened");
//        }
//        List<Widget[]> childGroups = Stream.of(widget.getChildren(), widget.getNestedChildren(), widget.getDynamicChildren(), widget.getStaticChildren())
//                    .filter(Objects::nonNull)
//                    .collect(Collectors.toList());

//        System.out.println("Got child groups: " + childGroups);
        Microbot.log(Level.INFO,
                "Got child groups");

//        List<String> texts =  new ArrayList<>();
//        for (Widget[] childGroup : childGroups) {
//            Microbot.log(Level.INFO,
//                    "child"+childGroup);
//
//            if (childGroup != null) {
//
//                for (Widget nestedChild : Arrays.stream(childGroup).filter(w -> w != null && !w.isHidden()).collect(Collectors.toList())) {
//                    System.out.println(" text " + nestedChild.getText());
//                    String clean = Rs2UiHelper.stripColTags(nestedChild.getText());
//                    Microbot.log(Level.INFO,
//                            "Cleand text " + clean);
//
//                    if (!clean.isEmpty()){
//                        texts.add(clean);
//                    }
//                }
//            }
//        }
//        System.out.println("Got texts: " + texts);
        sleep(1000,5000);

        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        sleep(1000,5000);
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);

//        return String.join("", );
        return "";
    }
}
