package net.runelite.client.plugins.microbot.plankrunner.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum Plank {
    PLANK("Wood", ItemID.LOGS, ItemID.PLANK, 100),
    OAK_PLANK("Oak", ItemID.OAK_LOGS, ItemID.OAK_PLANK, 250),
    TEAK_PLANK("Teak", ItemID.TEAK_LOGS, ItemID.TEAK_PLANK, 500),
    MAHOGANY_PLANK("Mahogany", ItemID.MAHOGANY_LOGS, ItemID.MAHOGANY_PLANK, 1500);
    
    private final String dialogueOption;
    private final int logItemId;
    private final int plankItemId;
    private final int costPerPlank;
}
