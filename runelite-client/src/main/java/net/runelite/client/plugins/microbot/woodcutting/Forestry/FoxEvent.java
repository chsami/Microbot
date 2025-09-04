package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.birdhouseruns.enums.Log;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FoxEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public FoxEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try{
            if (plugin == null || !Microbot.isPluginEnabled(plugin)) return false;
            if (Microbot.getClient() == null || !Microbot.isLoggedIn()) return false;
            var outDoorFox = Rs2Npc.getNpc(NpcID.GATHERING_EVENT_POACHERS_FOX_OUTDOORS);
            var indoorFox = Rs2Npc.getNpc(NpcID.GATHERING_EVENT_POACHERS_FOX_INDOORS);
            return outDoorFox != null || indoorFox != null;
        } catch (Exception e) {
            log.error("FoxEvent: Exception in validate method", e);
            return false;
        }
    }

    @Override
    public boolean execute() {
        Microbot.log("FoxEvent: Executing Fox event");
        plugin.currentForestryEvent = ForestryEvents.FOX_TRAP;
        Rs2Walker.setTarget(null); // stop walking
        
        // ensure inventory space for potential fox whistle (1/30 chance)
        if (!plugin.ensureInventorySpace(1)) {
            Microbot.log("FoxEvent: Cannot make inventory space for potential rewards, ending event.");
            return true;
        }
        
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
        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
