package net.runelite.client.plugins.microbot.crafting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

@Getter
@RequiredArgsConstructor
public enum Loom {
    NONE("", null, null),
    PRIFDDINAS("Prifddinas", new WorldPoint(3187, 3434, 0), new WorldPoint(3257, 6106, 0)),
    HOSIDIUS("Hodidius", new WorldPoint(3033, 3288, 0), new WorldPoint(1676, 3617, 0)),
    SOUTH_FALADOR_FARM("South Falador Farm", new WorldPoint(3039, 3287, 0), new WorldPoint(1398, 2927, 0)),
    ALDARIN("Aldarin", new WorldPoint(3040, 3284, 0), new WorldPoint(1398, 2927, 0)),
    MUSEUM_CAMP("Fossil Island", new WorldPoint(3730, 3822, 0), new WorldPoint(3739, 3804, 0));

    public final String label;
    public final WorldPoint loomWorldPoint;
    public final WorldPoint bankLocation;

    @Override
    public String toString() {
        return label;
    }
}
