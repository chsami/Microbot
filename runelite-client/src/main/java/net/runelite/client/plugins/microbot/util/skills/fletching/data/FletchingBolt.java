package net.runelite.client.plugins.microbot.util.skills.fletching.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Bolt types for fletching - supports unfinished bolts + feathers and bolt tip attachment
 */
@Getter
@RequiredArgsConstructor
public enum FletchingBolt {
    BRONZE("Unfinished bronze bolts", "Bronze bolts", "Bronze bolt tips", 9375, 877, 9140, 9),
    IRON("Unfinished iron bolts", "Iron bolts", "Iron bolt tips", 9377, 9423, 9141, 39),
    STEEL("Unfinished steel bolts", "Steel bolts", "Steel bolt tips", 9378, 9424, 9142, 46),
    MITHRIL("Unfinished mithril bolts", "Mithril bolts", "Mithril bolt tips", 9379, 9425, 9143, 54),
    BROAD("Unfinished broad bolts", "Broad bolts", null, 13279, 13280, -1, 55), // no tips for broad
    ADAMANT("Unfinished adamant bolts", "Adamant bolts", "Adamant bolt tips", 9380, 9426, 9144, 61),
    RUNITE("Unfinished runite bolts", "Runite bolts", "Runite bolt tips", 9381, 9427, 9145, 69),
    DRAGON("Unfinished dragon bolts", "Dragon bolts", "Dragon bolt tips", 21905, 21926, 21924, 84);

    private final String unfinishedBoltName;
    private final String finishedBoltName;
    private final String boltTipName; // null if no tips available
    private final int unfinishedBoltId;
    private final int finishedBoltId;
    private final int boltTipId; // -1 if no tips available
    private final int levelRequirement;

    /**
     * check if player meets level requirement
     */
    public boolean meetsLevelRequirement() {
        return levelRequirement <= Rs2Player.getRealSkillLevel(Skill.FLETCHING);
    }

    /**
     * check if this bolt type supports tip attachment
     */
    public boolean supportsTips() {
        return boltTipName != null && boltTipId != -1;
    }

    /**
     * find bolt type by unfinished bolt ID
     */
    public static FletchingBolt getBoltByUnfinishedId(int unfinishedBoltId) {
        for (FletchingBolt bolt : FletchingBolt.values()) {
            if (bolt.getUnfinishedBoltId() == unfinishedBoltId) {
                return bolt;
            }
        }
        return null;
    }

    /**
     * find bolt type by bolt tip ID
     */
    public static FletchingBolt getBoltByTipId(int boltTipId) {
        for (FletchingBolt bolt : FletchingBolt.values()) {
            if (bolt.getBoltTipId() == boltTipId) {
                return bolt;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return finishedBoltName;
    }
}