package net.runelite.client.plugins.microbot.runecrafting.gotr2.data;

import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum Elemental {
    AIR(22, 1, "Air rune", ItemID.AIR_RUNE),
    WATER(23, 5, "Water rune", ItemID.WATER_RUNE),
    EARTH(24, 9, "Earth rune", ItemID.EARTH_RUNE),
    FIRE(25, 14, "Fire rune", ItemID.FIRE_RUNE);

    private final int id;
    private final int lvl;
    private final String name;
    private final int itemId;

    Elemental(int id, int lvl, String name, int itemId) {
        this.id = id;
        this.lvl = lvl;
        this.name = name;
        this.itemId = itemId;
    }

    @Override
    public String toString() {
        return name + " (" + getlvl() + ", " + getItemId() + ")";
    }

    public int getlvl() {
        return lvl;
    }

    public static Set<Integer> getIds() {
        return Arrays.stream(values()).map(Elemental::getId).collect(Collectors.toSet());
    }
}
