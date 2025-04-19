package net.runelite.client.plugins.microbot.util.magic;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.Varbits;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Map;

@Getter
public enum Rs2CombatSpells
{
    WIND_STRIKE(MagicAction.WIND_STRIKE, Map.of(
            Runes.AIR, 1,
            Runes.MIND, 1
    ), Rs2Spellbook.MODERN),
    WATER_STRIKE(MagicAction.WATER_STRIKE, Map.of(
            Runes.WATER, 1,
            Runes.AIR, 1,
            Runes.MIND, 1
    ), Rs2Spellbook.MODERN),
    EARTH_STRIKE(MagicAction.EARTH_STRIKE, Map.of(
            Runes.EARTH, 2,
            Runes.AIR, 1,
            Runes.MIND, 1
    ), Rs2Spellbook.MODERN),
    FIRE_STRIKE(MagicAction.FIRE_STRIKE, Map.of(
            Runes.FIRE, 3,
            Runes.AIR, 2,
            Runes.MIND, 1
    ), Rs2Spellbook.MODERN),
    WIND_BOLT(MagicAction.WIND_BOLT, Map.of(
            Runes.AIR, 2,
            Runes.CHAOS, 1
    ), Rs2Spellbook.MODERN),
    WATER_BOLT(MagicAction.WATER_BOLT, Map.of(
            Runes.WATER, 2,
            Runes.AIR, 2,
            Runes.CHAOS, 1
    ), Rs2Spellbook.MODERN),
    EARTH_BOLT(MagicAction.EARTH_BOLT, Map.of(
            Runes.EARTH, 3,
            Runes.AIR, 2,
            Runes.CHAOS, 1
    ), Rs2Spellbook.MODERN),
    FIRE_BOLT(MagicAction.FIRE_BOLT, Map.of(
            Runes.FIRE, 4,
            Runes.AIR, 3,
            Runes.CHAOS, 1
    ), Rs2Spellbook.MODERN),
    WIND_BLAST(MagicAction.WIND_BLAST, Map.of(
            Runes.AIR, 3,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN),
    WATER_BLAST(MagicAction.WATER_BLAST, Map.of(
            Runes.WATER, 3,
            Runes.AIR, 3,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN),
    EARTH_BLAST(MagicAction.EARTH_BLAST, Map.of(
            Runes.EARTH, 4,
            Runes.AIR, 3,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN),
    FIRE_BLAST(MagicAction.FIRE_BLAST, Map.of(
            Runes.FIRE, 5,
            Runes.AIR, 4,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN),
    WIND_WAVE(MagicAction.WIND_WAVE, Map.of(
            Runes.AIR, 5,
            Runes.BLOOD, 1
    ), Rs2Spellbook.MODERN),
    WATER_WAVE(MagicAction.WATER_WAVE, Map.of(
            Runes.WATER, 7,
            Runes.AIR, 5,
            Runes.BLOOD, 1
    ), Rs2Spellbook.MODERN),
    EARTH_WAVE(MagicAction.EARTH_WAVE, Map.of(
            Runes.EARTH, 7,
            Runes.AIR, 5,
            Runes.BLOOD, 1
    ), Rs2Spellbook.MODERN),
    FIRE_WAVE(MagicAction.FIRE_WAVE, Map.of(
            Runes.FIRE, 7,
            Runes.AIR, 5,
            Runes.BLOOD, 1
    ), Rs2Spellbook.MODERN),
    WIND_SURGE(MagicAction.WIND_SURGE, Map.of(
            Runes.AIR, 7,
            Runes.BLOOD, 1,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN, 48),
    WATER_SURGE(MagicAction.WATER_SURGE, Map.of(
            Runes.WATER, 10,
            Runes.AIR, 7,
            Runes.BLOOD, 1,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN, 49),
    EARTH_SURGE(MagicAction.EARTH_SURGE, Map.of(
            Runes.EARTH, 10,
            Runes.AIR, 7,
            Runes.BLOOD, 1,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN, 50),
    FIRE_SURGE(MagicAction.FIRE_SURGE, Map.of(
            Runes.FIRE, 10,
            Runes.AIR, 7,
            Runes.BLOOD, 1,
            Runes.DEATH, 1
    ), Rs2Spellbook.MODERN, 51),

    // --- Ancient Magicks ---
    SMOKE_RUSH(MagicAction.SMOKE_RUSH, Map.of(
            Runes.AIR, 1,
            Runes.FIRE, 1,
            Runes.CHAOS, 2,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    SHADOW_RUSH(MagicAction.SHADOW_RUSH, Map.of(
            Runes.AIR, 1,
            Runes.CHAOS, 2,
            Runes.DEATH, 2,
            Runes.SOUL, 1
    ), Rs2Spellbook.ANCIENT),
    BLOOD_RUSH(MagicAction.BLOOD_RUSH, Map.of(
            Runes.BLOOD, 1,
            Runes.CHAOS, 2,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    ICE_RUSH(MagicAction.ICE_RUSH, Map.of(
            Runes.WATER, 2,
            Runes.CHAOS, 2,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    SMOKE_BURST(MagicAction.SMOKE_BURST, Map.of(
            Runes.AIR, 2,
            Runes.FIRE, 2,
            Runes.CHAOS, 4,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    SHADOW_BURST(MagicAction.SHADOW_BURST, Map.of(
            Runes.AIR, 1,
            Runes.CHAOS, 4,
            Runes.DEATH, 2,
            Runes.SOUL, 2
    ), Rs2Spellbook.ANCIENT),
    BLOOD_BURST(MagicAction.BLOOD_BURST, Map.of(
            Runes.BLOOD, 2,
            Runes.CHAOS, 4,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    ICE_BURST(MagicAction.ICE_BURST, Map.of(
            Runes.WATER, 4,
            Runes.CHAOS, 4,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    SMOKE_BLITZ(MagicAction.SMOKE_BLITZ, Map.of(
            Runes.AIR, 2,
            Runes.FIRE, 2,
            Runes.BLOOD, 2,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    SHADOW_BLITZ(MagicAction.SHADOW_BLITZ, Map.of(
            Runes.AIR, 2,
            Runes.BLOOD, 2,
            Runes.DEATH, 2,
            Runes.SOUL, 2
    ), Rs2Spellbook.ANCIENT),
    BLOOD_BLITZ(MagicAction.BLOOD_BLITZ, Map.of(
            Runes.BLOOD, 4,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    ICE_BLITZ(MagicAction.ICE_BLITZ, Map.of(
            Runes.WATER, 3,
            Runes.BLOOD, 2,
            Runes.DEATH, 2
    ), Rs2Spellbook.ANCIENT),
    SMOKE_BARRAGE(MagicAction.SMOKE_BARRAGE, Map.of(
            Runes.AIR, 4,
            Runes.FIRE, 4,
            Runes.BLOOD, 2,
            Runes.DEATH, 4
    ), Rs2Spellbook.ANCIENT),
    SHADOW_BARRAGE(MagicAction.SHADOW_BARRAGE, Map.of(
            Runes.AIR, 4,
            Runes.BLOOD, 2,
            Runes.DEATH, 4,
            Runes.SOUL, 3
    ), Rs2Spellbook.ANCIENT),
    BLOOD_BARRAGE(MagicAction.BLOOD_BARRAGE, Map.of(
            Runes.BLOOD, 4,
            Runes.DEATH, 4,
            Runes.SOUL, 1
    ), Rs2Spellbook.ANCIENT),
    ICE_BARRAGE(MagicAction.ICE_BARRAGE, Map.of(
            Runes.WATER, 6,
            Runes.BLOOD, 2,
            Runes.DEATH, 4
    ), Rs2Spellbook.ANCIENT);

    private final String name;
    private final MagicAction magicAction;
    private final Map<Runes, Integer> requiredRunes;
    private final Integer varbitValue; // Varbit 276
    private final Rs2Spellbook spellbook;
    private final int requiredLevel;

    public boolean hasRequiredLevel() {
        return Rs2Player.getSkillRequirement(Skill.MAGIC, this.requiredLevel);
    }

    public boolean hasRequiredSpellbook() {
        return Microbot.getVarbitValue(Varbits.SPELLBOOK) == getSpellbook().getValue();
    }

    private boolean hasRequirements() {
        return hasRequiredLevel() && hasRequiredSpellbook();
    }

    Rs2CombatSpells(MagicAction magicAction, Map<Runes, Integer> requiredRunes, Rs2Spellbook spellbook)
    {
        this.magicAction = magicAction;
        this.requiredRunes = requiredRunes;
        this.spellbook = spellbook;
        this.name = magicAction.getName();
        this.requiredLevel = magicAction.getLevel();
        this.varbitValue = ordinal() + 1;
    }

    Rs2CombatSpells(MagicAction magicAction, Map<Runes, Integer> requiredRunes, Rs2Spellbook spellbook, int varbitValue)
    {
        this.magicAction = magicAction;
        this.requiredRunes = requiredRunes;
        this.spellbook = spellbook;
        this.name = magicAction.getName();
        this.requiredLevel = magicAction.getLevel();
        this.varbitValue = varbitValue;
    }
}
