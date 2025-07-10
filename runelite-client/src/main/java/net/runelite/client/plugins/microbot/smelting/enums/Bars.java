package net.runelite.client.plugins.microbot.smelting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
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

    public Map<Ores, Integer> getWithdrawalsWithCoalBag(int totalInventorySlots) {
        Map<Ores, Integer> result = new java.util.HashMap<>();

        if (!requiredMaterials.containsKey(Ores.COAL)) {
            return requiredMaterials.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, e -> e.getValue()));
        }
        int invSlots = totalInventorySlots - 1;
        int coalPerBar = requiredMaterials.get(Ores.COAL);
        int totalMatsPerBar = requiredMaterials.values().stream().mapToInt(Integer::intValue).sum();
        for (int bars = invSlots; bars > 0; bars--) {
            int totalCoal = coalPerBar * bars;
            int coalInInv = Math.max(0, totalCoal - 27);
            int nonCoalMats = bars * totalMatsPerBar - totalCoal;
            int totalUsed = coalInInv + nonCoalMats + 1;
            if (totalUsed <= totalInventorySlots) {
                for (Map.Entry<Ores, Integer> entry : requiredMaterials.entrySet()) {
                    Ores ore = entry.getKey();
                    int totalAmount = entry.getValue() * bars;

                    if (ore == Ores.COAL) {
                        result.put(ore, coalInInv); // only inv coal needed
                    } else {
                        result.put(ore, totalAmount);
                    }
                }
                break;
            }
        }
        return result;
    }
}
