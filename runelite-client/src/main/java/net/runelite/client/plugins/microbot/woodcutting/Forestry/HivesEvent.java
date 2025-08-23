package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingConfig;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import org.slf4j.event.Level;

import java.awt.event.KeyEvent;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class HivesEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    public HivesEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        var beehives = Rs2Npc.getNpcs(x -> x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_1 || x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_2);
        return beehives.findAny().isPresent() && Rs2Inventory.count(plugin.config.TREE().getLogID()) > 1;
    }

    @Override
    public boolean execute() {
        plugin.currentForestryEvent = ForestryEvents.BEE_HIVE;
        while (this.validate()) {
            if (Rs2Widget.findWidget("How many logs would you like to add", null, false) != null) {
                Microbot.log("HivesEvent: Adding logs to the beehive.", Level.INFO);
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isAnimating(1200), 6000);
                continue;
            }
            var beehive = Rs2Npc.getNpcs(x -> x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_1 || x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_2)
                    .findFirst()
                    .orElse(null);
            if (beehive != null) {
                Microbot.log("HivesEvent: Interacting with the beehive to build it.", Level.INFO);
                Rs2Npc.interact(beehive, "Build");
                Rs2Player.waitForAnimation();
                sleepUntil(() -> !Rs2Player.isAnimating(), 6000);
            }
            //TODO Player might want to drop sturdy Beehive parts if they are in the inventory
        }
        Microbot.log("HivesEvent: Finished building the beehives.", Level.INFO);
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
