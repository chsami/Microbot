package net.runelite.client.plugins.microbot.util.antiban.enums;

import net.runelite.api.Skill;

public enum CombatSkills {
    ATTACK(Skill.ATTACK),
    STRENGTH(Skill.STRENGTH),
    DEFENCE(Skill.DEFENCE),
    HITPOINTS(Skill.HITPOINTS),
    RANGED(Skill.RANGED),
    PRAYER(Skill.PRAYER);

    CombatSkills(Skill skill) {
    }

    public static boolean isCombatSkill(Skill skill) {
        return skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE || skill == Skill.HITPOINTS || skill == Skill.RANGED || skill == Skill.PRAYER;
    }
}
