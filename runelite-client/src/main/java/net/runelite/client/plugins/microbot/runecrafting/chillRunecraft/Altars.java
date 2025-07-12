package net.runelite.client.plugins.microbot.runecrafting.chillRunecraft;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public enum Altars
{
    AIR_ALTAR("Air Altar", ObjectID.AIR_ALTAR, 34813, 34748 ,new WorldPoint(2990,3291,0), "Air tiara", "Air Talisman", "Air Rune", ItemID.AIR_TALISMAN),
    EARTH_ALTAR("Earth Altar", ObjectID.EARTH_ALTAR, 34816, 34751 ,new WorldPoint(3302,3470,0), "Earth tiara", "Earth Talisman", "Earth Rune", ItemID.EARTH_TALISMAN),
    WATER_ALTAR("Water Altar", ObjectID.WATER_ALTAR, 34815, 34750 ,new WorldPoint(3190,3162,0), "Water tiara", "Water Talisman", "Water Rune", ItemID.WATER_TALISMAN),
    FIRE_ALTAR("Fire Altar", ObjectID.FIRE_ALTAR, 34817, 34752 ,new WorldPoint(3309,3252,0), "Fire tiara", "Fire Talisman", "Fire Rune", ItemID.FIRE_TALISMAN),
    BODY_ALTAR("Body Altar", ObjectID.BODY_ALTAR, 34818, 34753 ,new WorldPoint(3053,3443,0), "Body tiara", "Body Talisman", "Body Rune", ItemID.BODY_TALISMAN);

    private final String altarName;
    private final int altarID;
    private final int altarRuinsID;
    private final int portalID;
    private final WorldPoint altarWorldPoint;
    private final String tiaraName;
    private final String talismanName;
    private final String runeName;
    private final int talismanID;

}
