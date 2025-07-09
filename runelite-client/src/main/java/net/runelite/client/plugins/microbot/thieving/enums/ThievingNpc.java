package net.runelite.client.plugins.microbot.thieving.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ThievingNpc {
    NONE("None", 0),
    MEN("man", 1),
    WOMAN("woman", 1),
    FARMER("farmer", 10),
    WARRIOR("warrior", 25),
    ROGUE("rogue", 32),
    MASTER_FARMER("master farmer", 38),
    GUARD("guard", 40),
    WEALTHY_CITIZEN("Wealthy citizen", 50),
    ARDOUGNE_KNIGHT("knight of ardougne", 55),
    PALADIN("Paladin", 70),
    HERO("Hero", 80),
    VYRES("Vyres", 82),
    ELVES("Elves", 85);

    private final String name;
    private final int thievingLevel;

    @Override
    public String toString() {
        return name;
    }

}
