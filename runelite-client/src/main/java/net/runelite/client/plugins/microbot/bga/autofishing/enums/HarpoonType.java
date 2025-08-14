package net.runelite.client.plugins.microbot.bga.autofishing.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum HarpoonType {
    NONE("None", -1),
    CRYSTAL_HARPOON("Crystal harpoon", ItemID.CRYSTAL_HARPOON),
    DRAGON_HARPOON("Dragon harpoon", ItemID.DRAGON_HARPOON),
    INFERNAL_HARPOON("Infernal harpoon", ItemID.INFERNAL_HARPOON);

    private final String name;
    private final int itemId;

    HarpoonType(String name, int itemId) {
        this.name = name;
        this.itemId = itemId;
    }

    @Override
    public String toString() {
        return name;
    }
}