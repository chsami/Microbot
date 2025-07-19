package net.runelite.client.plugins.microbot.crafting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum DragonLeatherArmour {
    NONE("", 0, 0, 0, '0'),

    GREEN_DHIDE_VAMBRACES("Green d'hide vambraces", ItemID.DRAGON_LEATHER, ItemID.DRAGON_VAMBRACES, 57, '2'),
    GREEN_DHIDE_CHAPS("Green d'hide chaps", ItemID.DRAGON_LEATHER, ItemID.DRAGONHIDE_CHAPS, 60, '3'),
    GREEN_DHIDE_BODY("Green d'hide body", ItemID.DRAGON_LEATHER, ItemID.DRAGONHIDE_BODY, 63, '1'),

    BLUE_DHIDE_VAMBRACES("Blue d'hide vambraces", ItemID.DRAGON_LEATHER_BLUE, ItemID.BLUE_DRAGON_VAMBRACES, 66, '2'),
    BLUE_DHIDE_CHAPS("Blue d'hide chaps", ItemID.DRAGON_LEATHER_BLUE, ItemID.BLUE_DRAGONHIDE_CHAPS, 68, '3'),
    BLUE_DHIDE_BODY("Blue d'hide body", ItemID.DRAGON_LEATHER_BLUE, ItemID.BLUE_DRAGONHIDE_BODY, 71, '1'),

    RED_DHIDE_VAMBRACES("Red d'hide vambraces", ItemID.DRAGON_LEATHER_RED, ItemID.RED_DRAGON_VAMBRACES, 73, '2'),
    RED_DHIDE_CHAPS("Red d'hide chaps", ItemID.DRAGON_LEATHER_RED, ItemID.RED_DRAGONHIDE_CHAPS, 75, '3'),
    RED_DHIDE_BODY("Red d'hide body", ItemID.DRAGON_LEATHER_RED, ItemID.RED_DRAGONHIDE_BODY, 77, '1'),

    BLACK_DHIDE_VAMBRACES("Black d'hide vambraces", ItemID.DRAGON_LEATHER_BLACK, ItemID.BLACK_DRAGON_VAMBRACES, 79, '2'),
    BLACK_DHIDE_CHAPS("Black d'hide chaps", ItemID.DRAGON_LEATHER_BLACK, ItemID.BLACK_DRAGONHIDE_CHAPS, 82, '3'),
    BLACK_DHIDE_BODY("Black d'hide body", ItemID.DRAGON_LEATHER_BLACK, ItemID.BLACK_DRAGONHIDE_BODY, 84, '1');

    private final String name;
    private final int leatherId;
    private final int itemId;
    private final int levelRequired;
    private final char menuEntry;

    @Override
    public String toString() {
        return name;
    }
}