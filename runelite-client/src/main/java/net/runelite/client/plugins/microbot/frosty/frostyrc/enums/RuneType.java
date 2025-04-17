package net.runelite.client.plugins.microbot.frosty.frostyrc.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum RuneType {
    BLOOD(ItemID.BLOODRUNE),
    WRATH(ItemID.WRATHRUNE);

    private final int itemIds;

    RuneType(int itemIds) {
        this.itemIds = itemIds;
    }


}
