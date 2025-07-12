package net.runelite.client.plugins.microbot.mahoganyhomez;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@AllArgsConstructor
@Getter
public enum PlankEnum {
    NORMAL(ItemID.WOODPLANK, 1,1),
    OAK(ItemID.PLANK_OAK, 2,20),
    TEAK(ItemID.PLANK_TEAK, 3,50),
    MAHOGANY(ItemID.PLANK_MAHOGANY, 4,70);

    private final int plankId;
    private final int chatOption;
    private final int levelRequirement;
}
