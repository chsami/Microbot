package net.runelite.client.plugins.microbot.util.prayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.annotations.Component;
import net.runelite.api.annotations.Varbit;

@Getter
@RequiredArgsConstructor
public enum Rs2PrayerEnum {
    THICK_SKIN("Thick Skin", InterfaceID.Prayerbook.PRAYER1, 1, VarbitID.PRAYER_THICKSKIN, 0),
    BURST_STRENGTH("Burst of Strength", InterfaceID.Prayerbook.PRAYER2, 4, VarbitID.PRAYER_BURSTOFSTRENGTH, 1),
    CLARITY_THOUGHT("Clarity of Thought", InterfaceID.Prayerbook.PRAYER3, 7, VarbitID.PRAYER_CLARITYOFTHOUGHT, 2),
    SHARP_EYE("Sharp Eye", InterfaceID.Prayerbook.PRAYER19, 8, VarbitID.PRAYER_SHARPEYE, 3),
    MYSTIC_WILL("Mystic Will", InterfaceID.Prayerbook.PRAYER22, 9, VarbitID.PRAYER_MYSTICWILL, 19),
    ROCK_SKIN("Rock Skin", InterfaceID.Prayerbook.PRAYER4, 10, VarbitID.PRAYER_ROCKSKIN, 3),
    SUPERHUMAN_STRENGTH("Superhuman Strength", InterfaceID.Prayerbook.PRAYER5, 13, VarbitID.PRAYER_SUPERHUMANSTRENGTH, 4),
    IMPROVED_REFLEXES("Improved Reflexes", InterfaceID.Prayerbook.PRAYER6, 16, VarbitID.PRAYER_IMPROVEDREFLEXES, 5),
    RAPID_RESTORE("Rapid Restore", InterfaceID.Prayerbook.PRAYER7, 19, VarbitID.PRAYER_RAPIDRESTORE, 6),
    RAPID_HEAL("Rapid heal", InterfaceID.Prayerbook.PRAYER8, 22, VarbitID.PRAYER_RAPIDHEAL, 7),
    PROTECT_ITEM("Protect Item", InterfaceID.Prayerbook.PRAYER9, 25, VarbitID.PRAYER_PROTECTITEM, 8),
    HAWK_EYE("Hawk eye", InterfaceID.Prayerbook.PRAYER20, 26, VarbitID.PRAYER_HAWKEYE, 20),
    MYSTIC_LORE("Mystic Lore", InterfaceID.Prayerbook.PRAYER23, 27, VarbitID.PRAYER_MYSTICLORE, 21),
    STEEL_SKIN("Steel Skin", InterfaceID.Prayerbook.PRAYER10, 28, VarbitID.PRAYER_STEELSKIN, 9),
    ULTIMATE_STRENGTH("Ultimate Strength", InterfaceID.Prayerbook.PRAYER11, 31, VarbitID.PRAYER_ULTIMATESTRENGTH, 10),
    INCREDIBLE_REFLEXES("Incredible Reflexes", InterfaceID.Prayerbook.PRAYER12, 34, VarbitID.PRAYER_INCREDIBLEREFLEXES, 11),
    PROTECT_MAGIC("Protect From Magic", InterfaceID.Prayerbook.PRAYER13, 37, VarbitID.PRAYER_PROTECTFROMMAGIC, 12),
    PROTECT_RANGE("Protect From Missiles", InterfaceID.Prayerbook.PRAYER14, 40, VarbitID.PRAYER_PROTECTFROMMISSILES, 13),
    PROTECT_MELEE("Protect From Melee", InterfaceID.Prayerbook.PRAYER15, 43, VarbitID.PRAYER_PROTECTFROMMELEE, 14),
    EAGLE_EYE("Eagle Eye", InterfaceID.Prayerbook.PRAYER21, 44, VarbitID.PRAYER_EAGLEEYE, 22),
    DEAD_EYE("Deadeye", InterfaceID.Prayerbook.PRAYER21, 44, VarbitID.PRAYER_EAGLEEYE, 22),
    MYSTIC_MIGHT("Mystic Might", InterfaceID.Prayerbook.PRAYER24, 45, VarbitID.PRAYER_MYSTICMIGHT, 23),
    MYSTIC_VIGOUR("Mystic Vigour", InterfaceID.Prayerbook.PRAYER24, 45, VarbitID.PRAYER_MYSTICMIGHT, 23),
    RETRIBUTION("Retribution", InterfaceID.Prayerbook.PRAYER16, 46, VarbitID.PRAYER_RETRIBUTION, 15),
    REDEMPTION("Redemption", InterfaceID.Prayerbook.PRAYER17, 49, VarbitID.PRAYER_REDEMPTION, 16),
    SMITE("Smite", InterfaceID.Prayerbook.PRAYER18, 52, VarbitID.PRAYER_SMITE, 17),
    PRESERVE("Preserve", InterfaceID.Prayerbook.PRAYER29, 55, VarbitID.PRAYER_PRESERVE, 28),
    CHIVALRY("Chivalry", InterfaceID.Prayerbook.PRAYER26,60, VarbitID.PRAYER_CHIVALRY, 25),
    PIETY("Piety", InterfaceID.Prayerbook.PRAYER27, 70, VarbitID.PRAYER_PIETY, 26),
    RIGOUR("Rigour", InterfaceID.Prayerbook.PRAYER25, 74, VarbitID.PRAYER_RIGOUR, 24),
    AUGURY("Augury", InterfaceID.Prayerbook.PRAYER28, 77, VarbitID.PRAYER_AUGURY, 27);

    private final String name;
    @Component
    private final int index;
    private final int level;
    @Varbit
    private final int varbit;
    private final int quickPrayerIndex;
}
