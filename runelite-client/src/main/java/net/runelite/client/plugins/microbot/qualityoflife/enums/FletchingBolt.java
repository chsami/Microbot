package net.runelite.client.plugins.microbot.qualityoflife.enums;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.api.gameval.ItemID;

public enum FletchingBolt {
    BRONZE("Bronze bolts (unf)", "Feather", "Bronze bolts", ItemID.XBOWS_CROSSBOW_BOLTS_BRONZE_UNFEATHERED, 9),
    BLURITE("Blurite bolts (unf)", "Feather", "Blurite bolts", ItemID.XBOWS_CROSSBOW_BOLTS_BLURITE_UNFEATHERED, 24),
    IRON("Iron bolts (unf)", "Feather", "Iron bolts", ItemID.XBOWS_CROSSBOW_BOLTS_IRON_UNFEATHERED, 39),
    SILVER("Silver bolts (unf)", "Feather", "Silver bolts", ItemID.XBOWS_CROSSBOW_BOLTS_SILVER_UNFEATHERED, 43),
    STEEL("Steel bolts (unf)", "Feather", "Steel bolts", ItemID.XBOWS_CROSSBOW_BOLTS_STEEL_UNFEATHERED, 46),
    MITHRIL("Mithril bolts (unf)", "Feather", "Mithril bolts", ItemID.XBOWS_CROSSBOW_BOLTS_MITHRIL_UNFEATHERED, 54),
    BROAD("Broad bolts (unf)", "Feather", "Broad bolts", ItemID.SLAYER_BROAD_BOLT_UNFINISHED, 55),
    ADAMANT("Adamant bolts (unf)", "Feather", "Adamant bolts", ItemID.XBOWS_CROSSBOW_BOLTS_ADAMANTITE_UNFEATHERED, 61),
    RUNITE("Runite bolts (unf)", "Feather", "Runite bolts", ItemID.XBOWS_CROSSBOW_BOLTS_RUNITE_UNFEATHERED, 69),
    DRAGON("Dragon bolts (unf)", "Feather", "Dragon bolts", ItemID.DRAGON_BOLTS_UNFEATHERED, 84);

    @Getter
    private final String boltTip;
    @Getter
    private final String feather;
    @Getter
    private final String bolt;
    @Getter
    private final int boltTipId;
    private final int levelRequirement;

    FletchingBolt(String boltTip, String feather, String bolt, int boltTipId, int levelRequirement) {
        this.boltTip = boltTip;
        this.feather = feather;
        this.bolt = bolt;
        this.boltTipId = boltTipId;
        this.levelRequirement = levelRequirement;
    }

    public boolean meetsLevelRequirement() {
        return levelRequirement <= Rs2Player.getRealSkillLevel(Skill.FLETCHING);
    }

    public static FletchingBolt getBoltByBoltTipId(int boltTipId) {
        for (FletchingBolt bolt : FletchingBolt.values()) {
            if (bolt.getBoltTipId() == boltTipId) {
                return bolt;
            }
        }
        return null;
    }


}
