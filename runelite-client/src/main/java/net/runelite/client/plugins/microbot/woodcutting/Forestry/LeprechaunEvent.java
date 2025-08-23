package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

public class LeprechaunEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;

    public LeprechaunEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        var leprechaun = Rs2Npc.getNpc(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN);
        return leprechaun != null;
    }

    @Override
    public boolean execute() {
        Microbot.log("LeprechaunEvent: Executing Leprechaun event");
        plugin.currentForestryEvent = ForestryEvents.RAINBOW;

        while (this.validate()) {
            var endOfRainbow = Rs2ObjectCache.getClosestObjectById(ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW);
            if (endOfRainbow.isEmpty()) {
                continue; // If the end of the rainbow is not found, we cannot proceed with the event
            }
            // Move to the end of the rainbow
            var location = endOfRainbow.get().getWorldLocation();
            if (!Rs2Player.getWorldLocation().equals(location)) {
                Microbot.log("LeprechaunEvent: Walking to the end of the rainbow at " + location, Level.INFO);
                Rs2Walker.walkFastCanvas(location);
                Global.sleepUntil(() -> Rs2Player.getWorldLocation().equals(location), 5000);
            }
        }
        return true;

        //TODO: Implement interaction with the leprechaun for banking
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
