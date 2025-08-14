package net.runelite.client.plugins.microbot.herbrun;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum HerbSeedType {
    GUAM("Guam seed", ItemID.GUAM_SEED),
    MARRENTILL("Marrentill seed", ItemID.MARRENTILL_SEED),
    TARROMIN("Tarromin seed", ItemID.TARROMIN_SEED),
    HARRALANDER("Harralander seed", ItemID.HARRALANDER_SEED),
    RANARR("Ranarr seed", ItemID.RANARR_SEED),
    TOADFLAX("Toadflax seed", ItemID.TOADFLAX_SEED),
    IRIT("Irit seed", ItemID.IRIT_SEED),
    AVANTOE("Avantoe seed", ItemID.AVANTOE_SEED),
    KWUARM("Kwuarm seed", ItemID.KWUARM_SEED),
    SNAPDRAGON("Snapdragon seed", ItemID.SNAPDRAGON_SEED),
    CADANTINE("Cadantine seed", ItemID.CADANTINE_SEED),
    LANTADYME("Lantadyme seed", ItemID.LANTADYME_SEED),
    DWARF_WEED("Dwarf weed seed", ItemID.DWARF_WEED_SEED),
    TORSTOL("Torstol seed", ItemID.TORSTOL_SEED);

    private final String seedName;
    private final int itemId;

    HerbSeedType(String seedName, int itemId) {
        this.seedName = seedName;
        this.itemId = itemId;
    }
}