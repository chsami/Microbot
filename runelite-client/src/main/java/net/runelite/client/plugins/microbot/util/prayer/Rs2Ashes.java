package net.runelite.client.plugins.microbot.util.prayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum Rs2Ashes {
    FIENDISH_ASHES(ItemID.FIENDISH_ASHES),
    VILE_ASHES(ItemID.VILE_ASHES),
    MALICIOUS_ASHES(ItemID.MALICIOUS_ASHES),
    ABYSSAL_ASHES(ItemID.ABYSSAL_ASHES),
    INFERNAL_ASHES(ItemID.INFERNAL_ASHES);

    private final int itemID;
}

