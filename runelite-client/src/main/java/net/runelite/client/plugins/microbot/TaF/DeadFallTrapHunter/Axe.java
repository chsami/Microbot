package net.runelite.client.plugins.microbot.TaF.DeadFallTrapHunter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@AllArgsConstructor
public enum Axe {

    BRONZE_AXE("bronze axe", ItemID.BRONZE_AXE, 1, 1),
    BRONZE_FELLING_AXE("bronze felling axe", ItemID.BRONZE_AXE_2H, 1, 1),
    IRON_AXE("iron axe", ItemID.IRON_AXE, 1, 1),
    IRON_FELLING_AXE("iron felling axe", ItemID.IRON_AXE_2H, 1, 1),
    STEEL_AXE("steel felling axe", ItemID.STEEL_AXE_2H, 6, 5),
    STEEL_FELLING_AXE("steel axe", ItemID.STEEL_AXE, 6, 5),
    BLACK_FELLING_AXE("black felling axe", ItemID.BLACK_AXE_2H, 11, 10),
    BLACK_AXE("black axe", ItemID.BLACK_AXE, 11, 10),
    MITHRIL_AXE("mithril axe", ItemID.MITHRIL_AXE, 21, 20),
    MITHRIL_FELLING_AXE("mithril felling axe", ItemID.MITHRIL_AXE_2H, 21, 20),
    ADAMANT_AXE("adamant axe", ItemID.ADAMANT_AXE, 31, 30),
    ADAMANT_FELLING_AXE("adamant felling axe", ItemID.ADAMANT_AXE_2H, 31, 30),
    RUNE_AXE("rune axe", ItemID.RUNE_AXE, 41, 40),
    RUNE_FELLING_AXE("rune felling axe", ItemID.RUNE_AXE_2H, 41, 40),
    DRAGON_AXE("dragon axe", ItemID.DRAGON_AXE, 61, 60),
    DRAGON_FELLING_AXE("dragon felling axe", ItemID.DRAGON_AXE_2H, 61, 60),
    CRYSTAL_AXE("crystal axe", ItemID.CRYSTAL_AXE, 71, 70),
    CRYSTAL_FELLING_AXE("crystal felling axe", ItemID.CRYSTAL_AXE_2H, 71, 70);

    private final String itemName;
    private final int itemID;
    private final int woodcuttingLevel;
    private final int attackLevel;

    public boolean hasRequirements(boolean axeInInventory) {
        return Rs2Player.getSkillRequirement(Skill.WOODCUTTING, this.woodcuttingLevel) && (Rs2Player.getSkillRequirement(Skill.ATTACK, this.attackLevel) || axeInInventory);
    }
}