package net.runelite.client.plugins.microbot.cooking.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum HumidifyItem {
    
    BOWL("bowl", ItemID.BOWL_EMPTY, "bowl of water", ItemID.BOWL_WATER),
    BUCKET("bucket", ItemID.BUCKET_EMPTY, "bucket of water", ItemID.BUCKET_WATER),
    JUG("jug", ItemID.JUG_EMPTY, "jug of water", ItemID.JUG_WATER);

    private final String itemName;
    private final int itemID;
    private final String filledItemName;
    private final int filledItemID;
}