package net.runelite.client.plugins.microbot.smelting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Ores {
    TIN("tin ore"),
    COPPER("copper ore"),
    CLAY("clay"),
    BLURITE("blurite ore"),
    IRON("iron ore"),
    SILVER("silver ore"),
    COAL("coal"),
    GOLD("gold ore"),
    MITHRIL("mithril ore"),
    ADAMANTITE("adamantite ore"),
    RUNITE("runite ore"),
    BUCKET_OF_SAND("bucket of sand"),
    SODA_ASH("soda ash"),;

    private final String name;
    @Override
    public String toString() {
        return name;
    }
}
