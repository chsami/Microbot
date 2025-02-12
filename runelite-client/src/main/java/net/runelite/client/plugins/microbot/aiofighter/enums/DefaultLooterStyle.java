package net.runelite.client.plugins.microbot.aiofighter.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DefaultLooterStyle {
    MIXED("Mixed"),
    ITEM_LIST("Item List"),
    GE_PRICE_RANGE("GE Price Range");

    private final String name;
}