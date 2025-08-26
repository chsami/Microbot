package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

public class FoxEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public FoxEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        var outDoorFox = Rs2Npc.getNpc(NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS);
        var indoorFox = Rs2Npc.getNpc(NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS);
        return outDoorFox != null || indoorFox != null;
    }

    @Override
    public boolean execute() {
        Microbot.log("FoxEvent: Executing Fox event");
        plugin.currentForestryEvent = ForestryEvents.FOX_TRAP;
        while (this.validate()) {
            var trap = Rs2Npc.getNpc(NpcID.GATHERING_EVENT_POACHERS_TRAP);
            if (trap == null) {
                continue; // If the trap is not found, we cannot proceed with the event
            }
            Microbot.log("FoxEvent: Interacting with the trap to disarm it.", Level.INFO);
            // Interact with the trap if it exists
            Rs2Npc.interact(trap, "Disarm");
            Rs2Player.waitForAnimation(1000);
        }
        Microbot.log("FoxEvent: Finished executing the Fox event.", Level.INFO);
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
