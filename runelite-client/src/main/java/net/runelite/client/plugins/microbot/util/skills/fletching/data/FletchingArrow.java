package net.runelite.client.plugins.microbot.util.skills.fletching.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Arrow types for fletching - compatible with QoL plugin patterns
 */
@Getter
@RequiredArgsConstructor
public enum FletchingArrow {
    BRONZE("Bronze arrowtips", "Headless arrow", "Bronze arrow", ItemID.BRONZE_ARROWHEADS, 1),
    IRON("Iron arrowtips", "Headless arrow", "Iron arrow", ItemID.IRON_ARROWHEADS, 15),
    STEEL("Steel arrowtips", "Headless arrow", "Steel arrow", ItemID.STEEL_ARROWHEADS, 30),
    MITHRIL("Mithril arrowtips", "Headless arrow", "Mithril arrow", ItemID.MITHRIL_ARROWHEADS, 45),
    BROAD("Broad arrowheads", "Headless arrow", "Broad arrow", ItemID.SLAYER_BROAD_ARROWHEAD, 52),
    ADAMANT("Adamant arrowtips", "Headless arrow", "Adamant arrow", ItemID.ADAMANT_ARROWHEADS, 60),
    RUNE("Rune arrowtips", "Headless arrow", "Rune arrow", ItemID.RUNE_ARROWHEADS, 75),
    AMETHYST("Amethyst arrowtips", "Headless arrow", "Amethyst arrow", ItemID.AMETHYST_ARROWHEADS, 82),
    DRAGON("Dragon arrowtips", "Headless arrow", "Dragon arrow", ItemID.DRAGON_ARROWHEADS, 90);

    private final String arrowTipName;
    private final String headlessArrowName;
    private final String finishedArrowName;
    private final int arrowTipId;
    private final int levelRequirement;

    /**
     * check if player meets level requirement
     */
    public boolean meetsLevelRequirement() {
        return levelRequirement <= Rs2Player.getRealSkillLevel(Skill.FLETCHING);
    }

    /**
     * find arrow type by arrow tip ID
     */
    public static FletchingArrow getArrowByTipId(int arrowTipId) {
        for (FletchingArrow arrow : FletchingArrow.values()) {
            if (arrow.getArrowTipId() == arrowTipId) {
                return arrow;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return finishedArrowName;
    }
}