package net.runelite.client.plugins.microbot.smelting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.Map;
@Getter
@RequiredArgsConstructor
public enum Bars {
    BRONZE("Bronze bar", ItemID.BRONZE_BAR, 1, Map.of(Ores.COPPER, 1, Ores.TIN, 1)),
    BLURITE("Blurite bar", ItemID.BLURITE_BAR,  13, Map.of(Ores.BLURITE, 1)),
    IRON("Iron bar", ItemID.IRON_BAR,  15, Map.of(Ores.IRON, 1)),
    SILVER("Silver bar", ItemID.SILVER_BAR,  20, Map.of(Ores.SILVER, 1)),
    STEEL("Steel bar", ItemID.STEEL_BAR,  30, Map.of(Ores.IRON, 1, Ores.COAL, 2)),
    GOLD("Gold bar", ItemID.GOLD_BAR,  40, Map.of(Ores.GOLD, 1)),
    MITHRIL("Mithril bar", ItemID.MITHRIL_BAR,  50, Map.of(Ores.MITHRIL, 1, Ores.COAL, 4)),
    ADAMANTITE("Adamantite bar", ItemID.ADAMANTITE_BAR,  70, Map.of(Ores.ADAMANTITE, 1, Ores.COAL, 6)),
    RUNITE("Runite bar", ItemID.RUNITE_BAR,  85, Map.of(Ores.RUNITE, 1, Ores.COAL, 8)),
    MOLTEN_GLASS("Molten glass", ItemID.MOLTEN_GLASS,  1, Map.of(Ores.SODA_ASH, 1, Ores.BUCKET_OF_SAND, 1)),;;

    private final String name;
    private final int id;
    private final int requiredSmithingLevel;
    private final Map<Ores, Integer> requiredMaterials;

    @Override
    public String toString() {
        return name;
    }
    public int getId() { return id; }

    public int maxBarsForFullInventory() {
        int amountForOneBar = requiredMaterials.values().stream().reduce(0, Integer::sum);
        return Rs2Inventory.capacity() / amountForOneBar;
    }
}
