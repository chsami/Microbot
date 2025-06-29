package net.runelite.client.plugins.microbot.TaF.EnsouledHeadSlayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

@Getter
@RequiredArgsConstructor
public enum EnsouledHeads {
    // Level 16 - Basic Reanimation
    GOBLIN("Ensouled goblin head", 13448, 16, MagicAction.BASIC_REANIMATION),
    MONKEY("Ensouled monkey head", 13451, 16, MagicAction.BASIC_REANIMATION),
    IMP("Ensouled imp head", 13454, 16, MagicAction.BASIC_REANIMATION),
    MINOTAUR("Ensouled minotaur head", 13457, 16, MagicAction.BASIC_REANIMATION),
    SCORPION("Ensouled scorpion head", 13460, 16, MagicAction.BASIC_REANIMATION),
    BEAR("Ensouled bear head", 13463, 16, MagicAction.BASIC_REANIMATION),
    UNICORN("Ensouled unicorn head", 13466, 16, MagicAction.BASIC_REANIMATION),

    // Level 41 - Adept Reanimation
    DOG("Ensouled dog head", 13469, 41, MagicAction.ADEPT_REANIMATION),
    CHAOS_DRUID("Ensouled chaos druid head", 13472, 41, MagicAction.ADEPT_REANIMATION),
    GIANT("Ensouled giant head", 13475, 41, MagicAction.ADEPT_REANIMATION),
    OGRE("Ensouled ogre head", 13478, 41, MagicAction.ADEPT_REANIMATION),
    ELF("Ensouled elf head", 13481, 41, MagicAction.ADEPT_REANIMATION),
    TROLL("Ensouled troll head", 13484, 41, MagicAction.ADEPT_REANIMATION),
    HORROR("Ensouled horror head", 13487, 41, MagicAction.ADEPT_REANIMATION),

    // Level 72 - Expert Reanimation
    KALPHITE("Ensouled kalphite head", 13490, 72, MagicAction.EXPERT_REANIMATION),
    DAGANNOTH("Ensouled dagannoth head", 13493, 72, MagicAction.EXPERT_REANIMATION),
    BLOODVELD("Ensouled bloodveld head", 13496, 72, MagicAction.EXPERT_REANIMATION),
    TZHAAR("Ensouled tzhaar head", 13499, 72, MagicAction.EXPERT_REANIMATION),
    DEMON("Ensouled demon head", 13502, 72, MagicAction.EXPERT_REANIMATION),
    HELLHOUND("Ensouled hellhound head",26997, 72, MagicAction.EXPERT_REANIMATION),

    // Level 90 - Master Reanimation
    AVIANSIE("Ensouled aviansie head", 13505, 90, MagicAction.MASTER_REANIMATION),
    ABYSSAL("Ensouled abyssal head", 13508, 90, MagicAction.MASTER_REANIMATION),
    DRAGON("Ensouled dragon head", 13511, 90, MagicAction.MASTER_REANIMATION),
    ALL_IN_BANK("All", 1337,0, null);

    private final String name;
    private final int ItemId;
    private final int magicLevelRequirement;
    private final MagicAction magicSpell;

    public boolean hasRequirements() {
        return Microbot.getClient().getRealSkillLevel(Skill.MAGIC) >= magicLevelRequirement;
    }
}