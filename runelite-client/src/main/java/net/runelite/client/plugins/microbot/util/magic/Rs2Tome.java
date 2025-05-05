package net.runelite.client.plugins.microbot.util.magic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

import java.util.Collections;
import java.util.List;
@Getter
@RequiredArgsConstructor
public enum Rs2Tome {

    NONE(0, Collections.emptyList()),
    TOME_OF_FIRE(ItemID.TOME_OF_FIRE, List.of(Runes.FIRE)),
    TOME_OF_WATER(ItemID.TOME_OF_WATER, List.of(Runes.WATER)),
    TOME_OF_EARTH(ItemID.TOME_OF_EARTH, List.of(Runes.EARTH));


    private final int itemID;
    private final List<Runes> runes;
}
