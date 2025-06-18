package net.runelite.client.plugins.microbot.runecrafting.gotr2.data;

import lombok.Getter;
import net.runelite.api.ItemID;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
public enum Catalytic {
    MIND(26, 2, "Mind rune", ItemID.MIND_RUNE),
    BODY(27, 20, "Body rune", ItemID.BODY_RUNE),
    COSMIC(28, 27, "Cosmic rune", ItemID.COSMIC_RUNE),
    NATURE(29, 44, "Nature rune", ItemID.NATURE_RUNE),
    LAW(30, 54, "Law rune", ItemID.LAW_RUNE),
    DEATH(31, 65, "Death rune", ItemID.DEATH_RUNE),
    BLOOD(32, 77, "Blood rune", ItemID.BLOOD_RUNE);

    private final int id;
    private final int lvl;
    private final String name;
    private final int itemId;

    Catalytic(int id, int lvl, String name, int itemId) {
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
        return Arrays.stream(values()).map(Catalytic::getId).collect(Collectors.toSet());
    }
}
