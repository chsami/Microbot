package net.runelite.client.plugins.microbot.magic.aiomagic.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

@Getter
@RequiredArgsConstructor
public enum StunSpell {
    CONFUSE(Rs2Spells.CONFUSE),
    WEAKEN(Rs2Spells.WEAKEN),
    CURSE(Rs2Spells.CURSE),
    VULNERABILITY(Rs2Spells.VULNERABILITY),
    ENFEEBLE(Rs2Spells.ENFEEBLE),
    STUN(Rs2Spells.STUN),;

    private final Rs2Spells rs2Spell;

    @Override
    public String toString() {
        return rs2Spell != null ? rs2Spell.getName() : "None";
    }
}
