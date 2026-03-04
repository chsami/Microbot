package net.runelite.client.plugins.microbot.aiosuperheat.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum SuperHeatItem {
    BRONZE("Bronze bar", ItemID.COPPER_ORE, ItemID.TIN_ORE, 1, 1),
    IRON("Iron bar", ItemID.IRON_ORE, -1, 0, 15),
    SILVER("Silver bar", ItemID.SILVER_ORE, -1, 0, 20),
    STEEL("Steel bar", ItemID.IRON_ORE, ItemID.COAL, 2, 30),
    GOLD("Gold bar", ItemID.GOLD_ORE, -1, 0, 40),
    MITHRIL("Mithril bar", ItemID.MITHRIL_ORE, ItemID.COAL, 4, 50),
    ADAMANTITE("Adamantite bar", ItemID.ADAMANTITE_ORE, ItemID.COAL, 6, 70),
    RUNITE("Runite bar", ItemID.RUNITE_ORE, ItemID.COAL, 8, 85);

    private final String name;
    private final int itemID;
    private final int secondaryItemID;
    private final int secondaryAmount;
    private final int requiredLevel;

    public boolean hasRequiredLevel() {
        return Rs2Player.getRealSkillLevel(Skill.MAGIC) >= 43 &&
                Rs2Player.getRealSkillLevel(Skill.SMITHING) >= requiredLevel;
    }
}
