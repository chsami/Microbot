package net.runelite.client.plugins.microbot.aiosuperheat.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum SuperHeatItem {
    BRONZE("Bronze bar", 436, 1, 14), // Copper ore
    IRON("Iron bar", 440, 0, 15), // Iron ore
    SILVER("Silver bar", 442, 0, 20), // Silver ore
    STEEL("Steel bar", 440, 2, 30), // Iron ore + 2 coal
    GOLD("Gold bar", 444, 0, 40), // Gold ore
    MITHRIL("Mithril bar", 447, 4, 50), // Mithril ore + 4 coal
    ADAMANTITE("Adamantite bar", 449, 6, 70), // Adamantite ore + 6 coal
    RUNITE("Runite bar", 451, 8, 85); // Runite ore + 8 coal

    private final String name;
    private final int itemID;
    private final int coalAmount;
    private final int requiredLevel;

    public boolean hasRequiredLevel() {
        return Rs2Player.getRealSkillLevel(Skill.MAGIC) >= 43 &&
                Rs2Player.getRealSkillLevel(Skill.SMITHING) >= requiredLevel;
    }
}
