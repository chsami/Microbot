package net.runelite.client.plugins.microbot.mining.shootingstar.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@AllArgsConstructor
public enum Pickaxe {

    BRONZE_PICKAXE("bronze pickaxe", ItemID.BRONZE_PICKAXE, 1, 1),
    IRON_PICKAXE("iron pickaxe", ItemID.IRON_PICKAXE, 1, 1),
    STEEL_PICKAXE("steel pickaxe", ItemID.STEEL_PICKAXE, 6, 5),
    BLACK_PICKAXE("black pickaxe", ItemID.BLACK_PICKAXE, 11, 10),
    MITHRIL_PICKAXE("mithril pickaxe", ItemID.MITHRIL_PICKAXE, 21, 20),
    ADAMANT_PICKAXE("adamant pickaxe", ItemID.ADAMANT_PICKAXE, 31, 30),
    RUNE_PICKAXE("rune pickaxe", ItemID.RUNE_PICKAXE, 41, 40),
    DRAGON_PICKAXE("dragon pickaxe", ItemID.DRAGON_PICKAXE, 61, 60),
    CRYSTAL_PICKAXE("crystal pickaxe", ItemID.CRYSTAL_PICKAXE, 71, 70);

    private final String itemName;
    private final int itemID;
    private final int miningLevel;
    private final int attackLevel;

    public boolean hasRequirements() {
        return Rs2Player.getSkillRequirement(Skill.MINING, this.miningLevel) && Rs2Player.getSkillRequirement(Skill.ATTACK, this.attackLevel);
    }
}
