package net.runelite.client.plugins.microbot.util.magic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing the different spellbooks in RuneScape.
 * The integer values correspond to the varbit {@link net.runelite.api.Varbits#SPELLBOOK}.
 */
@Getter
@RequiredArgsConstructor
public enum Rs2Spellbook {
    MODERN(0),
    ANCIENT(1),
    LUNAR(2),
    ARCEUUS(3);

    private final int value;
}