package net.runelite.client.plugins.microbot.kittentracker;


import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import javax.inject.Inject;


public class FeedKittenEvent implements BlockingEvent {
    private final KittenPlugin kittenPlugin;
    @Inject
    public FeedKittenEvent(KittenPlugin kittenPlugin) {
        this.kittenPlugin = kittenPlugin;
    }

    @Override
    public boolean validate() {
        return Rs2Inventory.contains(ItemID.RAW_KARAMBWANJI)
                && (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeHungry();

    }

    @Override
    public boolean execute() {
        Rs2Npc.getNpcs("Kitten").findFirst().ifPresent(kitten -> Rs2Inventory.useItemOnNpc(ItemID.RAW_KARAMBWANJI, kitten));
        Global.sleepUntil(() -> (KittenPlugin.HUNGRY_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeHungry());
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
