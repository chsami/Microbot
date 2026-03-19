package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehavior;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.models.RS2Item;

import java.util.List;

@Slf4j
public class LootingBehavior implements CCBehavior {

    private final List<String> itemNames;
    private final int radius;

    public LootingBehavior(List<String> itemNames, int radius) {
        this.itemNames = itemNames;
        this.radius = radius;
    }

    @Override public int priority() { return 40; }
    @Override public String name() { return "Looting"; }

    @Override
    public boolean shouldActivate() {
        return !itemNames.isEmpty() && hasMatchingGroundItem();
    }

    @Override
    public void execute() {
        LootingParameters params = new LootingParameters(
            radius, 1, 1, 1,
            false, false,
            itemNames.toArray(new String[0])
        );
        if (Rs2GroundItem.lootItemsBasedOnNames(params)) {
            log.debug("Looted item");
        }
    }

    @Override
    public void reset() { /* stateless */ }

    // --- Overridable for tests ---

    protected boolean hasMatchingGroundItem() {
        RS2Item[] items = Rs2GroundItem.getAll(radius);
        if (items == null) return false;
        for (RS2Item item : items) {
            for (String name : itemNames) {
                if (item.getItem().getName() != null &&
                    item.getItem().getName().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
