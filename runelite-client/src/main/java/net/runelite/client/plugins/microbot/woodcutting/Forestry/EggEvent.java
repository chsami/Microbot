package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.Rs2NpcCache;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;

import java.util.Comparator;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class EggEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public EggEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        var forester = Rs2NpcCache.getClosestNpcByGameId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER);
        return forester.isPresent();
    }

    @Override
    public boolean execute() {

        Microbot.log("EggEvent: Executing Egg event");
        var forester = Rs2NpcCache.getClosestNpcByGameId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER);
        if (forester.isEmpty()) {
            Microbot.log("EggEvent: Forester not found, cannot proceed with egg event.");
            return false; // If the forester is not found, we cannot proceed with the event
        }

        plugin.currentForestryEvent = ForestryEvents.PHEASANT;

        //if inventory is full, we cannot proceed with the event
        if (Rs2Inventory.isFull()) {
            Microbot.log("EggEvent: Inventory is full.");
            // drop a log to make space for the egg
            if (Rs2Inventory.contains(plugin.config.TREE().getLog())) {
                Microbot.log("EggEvent: Dropping a log to make space for the egg.");
                Rs2Inventory.drop(plugin.config.TREE().getLog());
                sleepUntil(() -> !Rs2Inventory.isFull(), 5000);
            }
        }

        while (this.validate()) {

            // If we have an egg, interact with the forester
            if (Rs2Inventory.contains("Pheasant egg")) {
                Microbot.log("EggEvent: Interacting with the forester to give the egg.");
                Rs2Npc.interact(forester.get(), "Talk-to");
                sleepUntil(() -> Rs2Widget.findWidget("Freaky Forester", null, false) != null, 5000);
                while (Rs2Dialogue.isInDialogue()) Rs2Dialogue.clickContinue();
                continue;
            }

            // If we don't have an egg, interact with the pheasant nest
            var nests = Rs2GameObject.getGameObjects(ObjectID.GATHERING_EVENT_PHEASANT_NEST02);
            var pheasants = Rs2Npc.getNpcs(NpcID.GATHERING_EVENT_PHEASANT).collect(Collectors.toList());

            if (nests.isEmpty() || pheasants.isEmpty()) {
                Microbot.log("EggEvent: No pheasant nests found, cannot proceed with egg event.");
                continue;
            }
            // find nest without pheasants
            var emptyNests = nests.stream()
                    .filter(Rs2GameObject::isReachable)
                    .filter(nest -> pheasants.stream()
                            .noneMatch(pheasant -> pheasant.getWorldLocation() == nest.getWorldLocation()))
                    .collect(Collectors.toList());

            Microbot.log("EggEvent: Interacting with the pheasant nest to collect an egg.");
            var closestNest = emptyNests.stream()
                    .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
                    .orElse(null);

            var interact = Rs2GameObject.interact(closestNest);
            if (!interact) {
                Microbot.log("EggEvent: Failed to interact with the pheasant nest.");
                Microbot.log("EggEvent: Closest nest is null? " + (closestNest == null));
            }
            Rs2Player.waitForAnimation();
        }
        Microbot.log("EggEvent: Ending Egg event.");
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
