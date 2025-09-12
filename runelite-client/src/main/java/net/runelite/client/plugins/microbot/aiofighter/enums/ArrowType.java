package net.runelite.client.plugins.microbot.aiofighter.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ArrowType {
    BRONZE_ARROW("Bronze arrow"),
    IRON_ARROW("Iron arrow"),
    STEEL_ARROW("Steel arrow"),
    MITHRIL_ARROW("Mithril arrow"),
    ADAMANT_ARROW("Adamant arrow"),
    RUNE_ARROW("Rune arrow"),
    AMETHYST_ARROW("Amethyst arrow"),
    DRAGON_ARROW("Dragon arrow");

    private final String name;
}
