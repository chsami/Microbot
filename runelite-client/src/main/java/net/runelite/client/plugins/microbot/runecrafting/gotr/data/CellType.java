package net.runelite.client.plugins.microbot.runecrafting.gotr.data;


import com.google.common.collect.ImmutableList;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

import java.util.List;


public enum CellType {
    WEAK,
    MEDIUM,
    STRONG,
    OVERCHARGED;

    public static int GetCellTier(int cellID) {
        switch (cellID) {
            case ItemID.GOTR_CELL_TIER1:
                return 1;
            case ItemID.GOTR_CELL_TIER2:
                return 2;
            case ItemID.GOTR_CELL_TIER3:
                return 3;
            case ItemID.GOTR_CELL_TIER4:
                return 4;
            default:
                return -1;
        }
    }

    public static int GetShieldTier(int shieldID) {
        switch (shieldID) {
            case ObjectID.GOTR_CELL_TILE_INACTIVE_NOOP:
                return 0;
            case ObjectID.GOTR_CELL_TILE_TIER1:
                return 1;
            case ObjectID.GOTR_CELL_TILE_TIER2:
                return 2;
            case ObjectID.GOTR_CELL_TILE_TIER3:
                return 3;
            case ObjectID.GOTR_CELL_TILE_TIER4:
                return 4;
            default:
                return -1;
        }
    }

    public static List<Integer> PoweredCellList() {
        return List.of(ItemID.GOTR_CELL_TIER1, ItemID.GOTR_CELL_TIER4, ItemID.GOTR_CELL_TIER3, ItemID.GOTR_CELL_TIER2);
    }
}
