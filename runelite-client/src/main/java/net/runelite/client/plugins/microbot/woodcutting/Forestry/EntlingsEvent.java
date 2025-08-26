package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;

import java.util.Comparator;
import java.util.stream.Collectors;

public class EntlingsEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public EntlingsEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {

        var entlings = Rs2Npc.getNpcs(npc -> npc.getId() == NpcID.GATHERING_EVENT_ENTLINGS_NPC_01)
        .collect(Collectors.toList());
        return !entlings.isEmpty();
    }

    @Override
    public boolean execute() {
        Microbot.log("EntlingsEvent: Starting Entlings event execution");
        plugin.currentForestryEvent = ForestryEvents.ENTLING;
        while (this.validate()) {
            var entlings = Rs2Npc.getNpcs(npc -> npc.getId() == NpcID.GATHERING_EVENT_ENTLINGS_NPC_01)
            .sorted(Comparator.comparingInt(e ->
                    e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())
            )).collect(Collectors.toList());

            for (Rs2NpcModel entling : entlings) {
                String request = entling.getOverheadText();
                String action;

                if (request == null || request.isEmpty()) {
                    continue; // Skip if no overhead text is present
                }
                switch (request) {
                    case "Breezy at the back!":
                    case "Short back and sides!":
                        action = "Prune-back";
                        break;
                    case "A leafy mullet!":
                    case "Short on top!":
                        action = "Prune-top";
                        break;
                    default:
                        continue;
                }

                Microbot.log("EntlingsEvent: Interacting with entling: with action: " + action);
                Rs2Npc.interact(entling, action);
                Rs2Player.waitForAnimation(1000); // Wait for the pruning animation to finish
            }
        }
        Microbot.log("EntlingsEvent: Ending event execution");
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
