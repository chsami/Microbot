package net.runelite.client.plugins.microbot.util.skills.fletching.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Dart types for fletching - dart tips + feathers = darts
 */
@Getter
@RequiredArgsConstructor
public enum FletchingDart {
    BRONZE("Bronze dart tip", "Bronze dart", 819, 806, 10),
    IRON("Iron dart tip", "Iron dart", 820, 807, 22),
    STEEL("Steel dart tip", "Steel dart", 821, 808, 37),
    MITHRIL("Mithril dart tip", "Mithril dart", 822, 809, 52),
    ADAMANT("Adamant dart tip", "Adamant dart", 823, 810, 67),
    RUNE("Rune dart tip", "Rune dart", 824, 811, 81),
    DRAGON("Dragon dart tip", "Dragon dart", 11230, 11233, 95);

    private final String dartTipName;
    private final String finishedDartName;
    private final int dartTipId;
    private final int finishedDartId;
    private final int levelRequirement;

    /**
     * check if player meets level requirement
     */
    public boolean meetsLevelRequirement() {
        return levelRequirement <= Rs2Player.getRealSkillLevel(Skill.FLETCHING);
    }

    /**
     * find dart type by dart tip ID
     */
    public static FletchingDart getDartByTipId(int dartTipId) {
        for (FletchingDart dart : FletchingDart.values()) {
            if (dart.getDartTipId() == dartTipId) {
                return dart;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return finishedDartName;
    }
}