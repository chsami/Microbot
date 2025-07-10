package net.runelite.client.plugins.microbot.qualityoflife.enums;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum DragonhideCrafting {
    GREEN_DHIDE_VAMBRACES("Green d'hide vambraces", ItemID.DRAGON_LEATHER),
    GREEN_DHIDE_CHAPS("Green d'hide chaps", ItemID.DRAGON_LEATHER),
    GREEN_DHIDE_SHIELD("Green d'hide shield", ItemID.DRAGON_LEATHER),
    GREEN_DHIDE_BODY("Green d'hide body", ItemID.DRAGON_LEATHER),

    BLUE_DHIDE_VAMBRACES("Blue d'hide vambraces", ItemID.DRAGON_LEATHER_BLUE),
    BLUE_DHIDE_CHAPS("Blue d'hide chaps", ItemID.DRAGON_LEATHER_BLUE),
    BLUE_DHIDE_SHIELD("Blue d'hide shield", ItemID.DRAGON_LEATHER_BLUE),
    BLUE_DHIDE_BODY("Blue d'hide body", ItemID.DRAGON_LEATHER_BLUE),

    RED_DHIDE_VAMBRACES("Red d'hide vambraces", ItemID.DRAGON_LEATHER_RED),
    RED_DHIDE_CHAPS("Red d'hide chaps", ItemID.DRAGON_LEATHER_RED),
    RED_DHIDE_SHIELD("Red d'hide shield", ItemID.DRAGON_LEATHER_RED),
    RED_DHIDE_BODY("Red d'hide body", ItemID.DRAGON_LEATHER_RED),

    BLACK_DHIDE_VAMBRACES("Black d'hide vambraces", ItemID.DRAGON_LEATHER_BLACK),
    BLACK_DHIDE_CHAPS("Black d'hide chaps", ItemID.DRAGON_LEATHER_BLACK),
    BLACK_DHIDE_SHIELD("Black d'hide shield", ItemID.DRAGON_LEATHER_BLACK),
    BLACK_DHIDE_BODY("Black d'hide body", ItemID.DRAGON_LEATHER_BLACK);

    private final String name;
    private final int id;

    DragonhideCrafting(String name, int id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    private static final Map<DragonhideCrafting, Integer> LEVEL_REQUIREMENTS = new EnumMap<>(DragonhideCrafting.class);

    static {
        LEVEL_REQUIREMENTS.put(GREEN_DHIDE_VAMBRACES, 57);
        LEVEL_REQUIREMENTS.put(GREEN_DHIDE_CHAPS, 60);
        LEVEL_REQUIREMENTS.put(GREEN_DHIDE_SHIELD, 62);
        LEVEL_REQUIREMENTS.put(GREEN_DHIDE_BODY, 63);
        LEVEL_REQUIREMENTS.put(BLUE_DHIDE_VAMBRACES, 66);
        LEVEL_REQUIREMENTS.put(BLUE_DHIDE_CHAPS, 68);
        LEVEL_REQUIREMENTS.put(BLUE_DHIDE_SHIELD, 69);
        LEVEL_REQUIREMENTS.put(BLUE_DHIDE_BODY, 71);
        LEVEL_REQUIREMENTS.put(RED_DHIDE_VAMBRACES, 73);
        LEVEL_REQUIREMENTS.put(RED_DHIDE_CHAPS, 75);
        LEVEL_REQUIREMENTS.put(RED_DHIDE_SHIELD, 76);
        LEVEL_REQUIREMENTS.put(RED_DHIDE_BODY, 77);
        LEVEL_REQUIREMENTS.put(BLACK_DHIDE_VAMBRACES, 79);
        LEVEL_REQUIREMENTS.put(BLACK_DHIDE_CHAPS, 82);
        LEVEL_REQUIREMENTS.put(BLACK_DHIDE_SHIELD, 83);
        LEVEL_REQUIREMENTS.put(BLACK_DHIDE_BODY, 84);
    }

    public static int getLevelRequirement(DragonhideCrafting item) {
        return LEVEL_REQUIREMENTS.getOrDefault(item, 1);
    }

    public static List<Rs2ItemModel> getCraftableHides() {
        return Rs2Inventory.all(item -> {
            for (DragonhideCrafting hide : DragonhideCrafting.values()) {
                if (item.getId() == hide.getId() && getLevelRequirement(hide) <= Rs2Player.getRealSkillLevel(Skill.CRAFTING)) {
                    return true;
                }
            }
            return false;
        });
    }
}
