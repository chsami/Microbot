package net.runelite.client.plugins.microbot.qualityoflife.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
public enum FletchingDarts {
    BRONZE("Bronze dart tip", "Feather", "Bronze dart", ItemID.BRONZE_DART_TIP, ItemID.FEATHER,10),
    IRON("Iron dart tip", "Feather", "Iron dart", ItemID.IRON_DART_TIP, ItemID.FEATHER, 22),
    STEEL("Steel dart tip", "Feather", "Steel dart", ItemID.STEEL_DART_TIP, ItemID.FEATHER, 37),
    MITHRIL("Mithril dart tip", "Feather", "Mithril dart", ItemID.MITHRIL_DART_TIP, ItemID.FEATHER, 52),
    ADAMANT("Adamant dart tip", "Feather", "Adamant dart", ItemID.ADAMANT_DART_TIP, ItemID.FEATHER, 67),
    RUNE("Rune dart tip", "Feather", "Rune dart", ItemID.RUNE_DART_TIP, ItemID.FEATHER, 81),
    DRAGON("Dragon dart tip", "Feather", "Dragon dart", ItemID.DRAGON_DART_TIP, ItemID.FEATHER, 95),
    AMETHYST("Amethyst dart tip", "Feather", "Amethyst dart", ItemID.AMETHYST_DART_TIP, ItemID.FEATHER, 90);

    private final String dartTip;
    private final String feather;
    private final String dart;
    private final int dartTipId;
    private final int featherId;
    private final int levelRequirement;

    FletchingDarts(String dartTip, String feather, String dart, int dartTipId, int featherId, int levelRequirement) {
        this.dartTip = dartTip;
        this.feather = feather;
        this.dart = dart;
        this.dartTipId = dartTipId;
        this.featherId = featherId;
        this.levelRequirement = levelRequirement;
    }

    public boolean meetsLevelRequirement() {
        return levelRequirement <= Rs2Player.getRealSkillLevel(Skill.FLETCHING);
    }

    public static FletchingDarts getDartByDartTipId(int dartTipId) {
        for (FletchingDarts dart : FletchingDarts.values()) {
            if (dart.getDartTipId() == dartTipId) {
                return dart;
            }
        }
        return null;
    }
}
