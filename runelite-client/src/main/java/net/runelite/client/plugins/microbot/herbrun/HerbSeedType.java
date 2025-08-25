package net.runelite.client.plugins.microbot.herbrun;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public enum HerbSeedType {
    BEST("Best available", -1, 0), // Special option for dynamic selection
    GUAM("Guam seed", ItemID.GUAM_SEED, 9),
    MARRENTILL("Marrentill seed", ItemID.MARRENTILL_SEED, 14),
    TARROMIN("Tarromin seed", ItemID.TARROMIN_SEED, 19),
    HARRALANDER("Harralander seed", ItemID.HARRALANDER_SEED, 26),
    RANARR("Ranarr seed", ItemID.RANARR_SEED, 32),
    TOADFLAX("Toadflax seed", ItemID.TOADFLAX_SEED, 38),
    IRIT("Irit seed", ItemID.IRIT_SEED, 44),
    AVANTOE("Avantoe seed", ItemID.AVANTOE_SEED, 50),
    KWUARM("Kwuarm seed", ItemID.KWUARM_SEED, 56),
    SNAPDRAGON("Snapdragon seed", ItemID.SNAPDRAGON_SEED, 62),
    CADANTINE("Cadantine seed", ItemID.CADANTINE_SEED, 67),
    LANTADYME("Lantadyme seed", ItemID.LANTADYME_SEED, 73),
    DWARF_WEED("Dwarf weed seed", ItemID.DWARF_WEED_SEED, 79),
    TORSTOL("Torstol seed", ItemID.TORSTOL_SEED, 85);

    private final String seedName;
    private final int itemId;
    private final int levelRequired;

    HerbSeedType(String seedName, int itemId, int levelRequired) {
        this.seedName = seedName;
        this.itemId = itemId;
        this.levelRequired = levelRequired;
    }
    
    /**
     * Gets all herb types that can be planted at the given farming level,
     * sorted by level requirement (highest first)
     * 
     * @param farmingLevel The player's farming level
     * @return List of plantable herbs sorted by level (highest first)
     */
    public static List<HerbSeedType> getPlantableHerbs(int farmingLevel) {
        return Arrays.stream(values())
                .filter(herb -> herb != BEST && herb.levelRequired <= farmingLevel)
                .sorted(Comparator.comparingInt(HerbSeedType::getLevelRequired).reversed())
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if this herb type can be planted at the given farming level
     * 
     * @param farmingLevel The player's farming level
     * @return true if the herb can be planted
     */
    public boolean canPlant(int farmingLevel) {
        return this != BEST && farmingLevel >= this.levelRequired;
    }
    
    @Override
    public String toString() {
        return seedName;
    }
    
    /**
     * Find HerbSeedType by item id; returns null when not found.
     */
    public static HerbSeedType fromItemId(int id) {
        for (HerbSeedType t : values()) {
            if (t != BEST && t.itemId == id) return t;
        }
        return null;
    }
}