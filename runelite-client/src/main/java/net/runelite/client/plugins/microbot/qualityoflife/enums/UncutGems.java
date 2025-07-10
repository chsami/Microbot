package net.runelite.client.plugins.microbot.qualityoflife.enums;

import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

public enum UncutGems {
    UNCUT_OPAL("Uncut opal", ItemID.UNCUT_OPAL, 1),
    UNCUT_JADE("Uncut jade", ItemID.UNCUT_JADE, 13),
    UNCUT_RED_TOPAZ("Uncut red topaz", ItemID.UNCUT_RED_TOPAZ, 16),
    UNCUT_SAPPHIRE("Uncut sapphire", ItemID.UNCUT_SAPPHIRE, 20),
    UNCUT_EMERALD("Uncut emerald", ItemID.UNCUT_EMERALD, 27),
    UNCUT_RUBY("Uncut ruby", ItemID.UNCUT_RUBY, 34),
    UNCUT_DIAMOND("Uncut diamond", ItemID.UNCUT_DIAMOND, 43),
    UNCUT_DRAGONSTONE("Uncut dragonstone", ItemID.UNCUT_DRAGONSTONE, 55),
    UNCUT_ONYX("Uncut onyx", ItemID.UNCUT_ONYX, 67),
    UNCUT_ZENYTE("Uncut zenyte", ItemID.UNCUT_ZENYTE, 89);

    private final String name;
    private final int id;
    private final int levelRequirement;

    UncutGems(String name, int id, int levelRequirement) {
        this.name = name;
        this.id = id;
        this.levelRequirement = levelRequirement;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public int getLevelRequirement() {
        return levelRequirement;
    }

    public static UncutGems getById(int id) {
        for (UncutGems gem : values()) {
            if (gem.getId() == id) {
                return gem;
            }
        }
        return null;
    }

    public static List<Rs2ItemModel> getAvailableGems() {
        return Rs2Inventory.all(gem -> {
            for (UncutGems uncutGem : values()) {
                if (uncutGem.getId() == gem.getId() && uncutGem.getLevelRequirement() <= Rs2Player.getRealSkillLevel(Skill.CRAFTING)) {
                    return true;
                }
            }
            return false;
        });
    }
}
