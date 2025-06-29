package net.runelite.client.plugins.microbot.eventdismiss;

import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

public class DismissNpcEvent implements BlockingEvent {

    private final EventDismissConfig config;

    public DismissNpcEvent(EventDismissConfig config) {
        this.config = config;
    }

    @Override
    public boolean validate() {
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        return Rs2Npc.hasLineOfSight(randomEventNPC);
    }

    @Override
    public boolean execute() {
        Rs2NpcModel randomEventNPC = Rs2Npc.getRandomEventNPC();
        boolean shouldDismiss = shouldDismissNpc(randomEventNPC);
        if (shouldDismiss) {
            Rs2Npc.interact(randomEventNPC, "Dismiss");
            Global.sleepUntil(() -> Rs2Npc.getRandomEventNPC() == null);
            return true;
        } else if (!Rs2Inventory.isFull()) {
            Rs2Npc.interact(randomEventNPC, "Talk-to");
            Rs2Dialogue.sleepUntilHasContinue();
            Rs2Dialogue.clickContinue();
            return true;
        }
        return false;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.LOWEST;
    }

    private boolean shouldDismissNpc(Rs2NpcModel npc) {
        String npcName = npc.getName();
        if (npcName == null) return false;
        switch (npcName) {
            case "Bee keeper":
                return config.dismissBeekeeper();
            case "Capt' Arnav":
                return config.dismissArnav();
            case "Niles":
            case "Miles":
            case "Giles":
                return config.dismissCerters();
            case "Count Check":
                return config.dismissCountCheck();
            case "Sergeant Damien":
                return config.dismissDrillDemon();
            case "Drunken Dwarf":
                return config.dismissDrunkenDwarf();
            case "Evil Bob":
                return config.dismissEvilBob();
            case "Postie Pete":
                return config.dismissEvilTwin();
            case "Freaky Forester":
                return config.dismissFreakyForester();
            case "Genie":
                return config.dismissGenie();
            case "Leo":
                return config.dismissGravedigger();
            case "Dr Jekyll":
                return config.dismissJekyllAndHyde();
            case "Frog":
                return config.dismissKissTheFrog();
            case "Mysterious Old Man":
                return config.dismissMysteriousOldMan();
            case "Pillory Guard":
                return config.dismissPillory();
            case "Flippa":
            case "Tilt":
                return config.dismissPinball();
            case "Quiz Master":
                return config.dismissQuizMaster();
            case "Rick Turpentine":
                return config.dismissRickTurpentine();
            case "Sandwich lady":
                return config.dismissSandwichLady();
            case "Strange plant":
                return config.dismissStrangePlant();
            case "Dunce":
                return config.dismissSurpriseExam();
            default:
                return false;
        }
    }
}
