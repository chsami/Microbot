package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.NPC;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

import java.util.List;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class RitualEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;

    public RitualEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        var dryad = Rs2Npc.getNpc(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD);
        return dryad != null;
    }

    @Override
    public boolean execute() {
        plugin.currentForestryEvent = ForestryEvents.RITUAL_CIRCLES;
        while (this.validate()) {
            var targetCircle = this.solveCircles(plugin.ritualCircles);
            if (targetCircle == null) {
                Microbot.log("RitualEvent: No target circle found, cannot proceed with the ritual.", Level.INFO);
                continue; // If no target circle is found, we cannot proceed with the event
            }

            if (Rs2Player.getWorldLocation().equals(targetCircle.getWorldLocation())) {
                // Microbot.log("RitualEvent: Already at the target circle, performing the ritual.", Level.INFO);
                sleepGaussian(600, 600);
                continue; // TODO find some condition to wait for here
            }

            // Move to the target circle
            Microbot.log("RitualEvent: Moving to the target circle to perform the ritual.", Level.INFO);
            Rs2Walker.walkFastCanvas(targetCircle.getWorldLocation());
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(targetCircle.getWorldLocation()), 5000);
        }
        Microbot.log("RitualEvent: Finished executing the ritual event.", Level.INFO);
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }

    private NPC solveCircles(List<NPC> ritualCircles) {
        if (ritualCircles.size() != 5) {
            return null;
        }

        int s = 0;
        for (NPC npc : ritualCircles) {
            int off = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = off / 4;
            int color = off % 4;
            int id = (16 << shape) | (1 << color);
            s = s ^ id; // XOR operation
        }

        for (NPC npc : ritualCircles) {
            int off = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = off / 4;
            int color = off % 4;
            int id = (16 << shape) | (1 << color);
            if ((id & s) == id) { // Bitwise AND
                return npc;
            }
        }

        return null;
    }
}
