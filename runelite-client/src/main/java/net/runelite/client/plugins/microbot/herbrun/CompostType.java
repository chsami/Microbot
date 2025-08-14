package net.runelite.client.plugins.microbot.herbrun;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum CompostType {
    NONE("None", -1),
    REGULAR("Compost", ItemID.BUCKET_COMPOST),
    SUPER("Supercompost", ItemID.BUCKET_SUPERCOMPOST),
    ULTRA("Ultracompost", ItemID.BUCKET_ULTRACOMPOST),
    BOTTOMLESS("Bottomless compost bucket", ItemID.BOTTOMLESS_COMPOST_BUCKET);

    private final String compostName;
    private final int itemId;

    CompostType(String compostName, int itemId) {
        this.compostName = compostName;
        this.itemId = itemId;
    }

    public boolean isBottomless() {
        return this == BOTTOMLESS;
    }
}