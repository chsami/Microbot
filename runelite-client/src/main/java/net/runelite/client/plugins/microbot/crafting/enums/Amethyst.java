package net.runelite.client.plugins.microbot.crafting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum Amethyst {
        NONE(
                        "",
                        0,
                        0,
                        0),
        PROGRESSIVE(
                        "Progressive",
                        0,
                        83,
                        0),
        BOLT_TIPS(
                        "Bolt tips",
                        ItemID.AMETHYST_BOLT_TIPS,
                        83,
                        1),
        ARROWTIPS(
                        "Arrowtips",
                        ItemID.AMETHYST_ARROWTIPS,
                        85,
                        2),
        JAVELIN_HEADS(
                        "Javelin heads",
                        ItemID.AMETHYST_JAVELIN_HEADS,
                        87,
                        3),
        DART_TIP(
                        "Dart tip",
                        ItemID.AMETHYST_DART_TIP,
                        89,
                        4);

        private final String itemName;
        private final int itemId;
        private final int requiredLevel;
        private final int craftOptionKey;
}
