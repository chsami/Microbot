package net.runelite.client.plugins.microbot.magic.aiomagic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum SuperHeatItem {
    
    IRON(ItemID.IRON_ORE, 0, 15),
    SILVER(ItemID.SILVER_ORE, 0, 20),
    STEEL(ItemID.IRON_ORE, 2, 30),
    GOLD(ItemID.GOLD_ORE, 0, 40),
    MITHRIL(ItemID.MITHRIL_ORE, 4, 50),
    ADAMANTITE(ItemID.ADAMANTITE_ORE, 6, 70),
    RUNITE(ItemID.RUNITE_ORE, 8, 85);
    
    private final int itemID;
    private final int coalAmount;
    private final int levelRequired;
    
    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.SMITHING, this.levelRequired);
    }
}
