package net.runelite.client.plugins.microbot.runecrafting.gotr2.data;

import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum Combination {
    // Format: (id, lvl, name, ItemID)
    NONE(-1, 0, "None"),
    MIST(15, 6, "Mist rune"),
    MUD(16, 13, "Mud rune"),
    DUST(17, 10, "Dust rune"),
    LAVA(18, 23, "Lava rune"),
    STEAM(19, 19, "Steam rune"),
    SMOKE(20, 15, "Smoke rune"),
    ALL(-1, 23, "All");

    private final int id;
    private final int lvl;
    private final String name;

    Combination(int id, int lvl, String name) {
        this.id = id;
        this.lvl = lvl;
        this.name = name;
    }

    @Override
    public String toString() {
        if (this == NONE) {
            return "None";
        }
        if (this == ALL) {
            return "All";
        }
        return name + " (" + getlvl() + ")";
    }

    public int getlvl() {
        return lvl;
    }

    // get all ids as a set
    public static Set<Integer> getIds() {
        return Arrays.stream(values()).map(Combination::getId).collect(Collectors.toSet());
    }
}

