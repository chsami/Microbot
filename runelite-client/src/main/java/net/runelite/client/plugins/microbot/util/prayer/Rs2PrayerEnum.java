package net.runelite.client.plugins.microbot.util.prayer;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.annotations.Component;
import net.runelite.api.annotations.Varbit;

@Getter
@RequiredArgsConstructor
public enum Rs2PrayerEnum {
    THICK_SKIN("Thick Skin", 35454985, 1, VarbitID.PRAYER_THICKSKIN, 0),
    BURST_STRENGTH("Burst of Strength", 35454986, 4, VarbitID.PRAYER_BURSTOFSTRENGTH, 1),
    CLARITY_THOUGHT("Clarity of Thought", 35454987, 7, VarbitID.PRAYER_CLARITYOFTHOUGHT, 2),
    SHARP_EYE("Sharp Eye", 35455003, 8, VarbitID.PRAYER_SHARPEYE, 3),
    MYSTIC_WILL("Mystic Will", 35455006, 9, VarbitID.PRAYER_MYSTICWILL, 19),
    ROCK_SKIN("Rock Skin", 35454988, 10, VarbitID.PRAYER_ROCKSKIN, 3),
    SUPERHUMAN_STRENGTH("Superhuman Strength", 35454989, 13, VarbitID.PRAYER_SUPERHUMANSTRENGTH, 4),
    IMPROVED_REFLEXES("Improved Reflexes", 35454990, 16, VarbitID.PRAYER_IMPROVEDREFLEXES, 5),
    RAPID_RESTORE("Rapid Restore", 35454991, 19, VarbitID.PRAYER_RAPIDRESTORE, 6),
    RAPID_HEAL("Rapid heal", 35454992, 22, VarbitID.PRAYER_RAPIDHEAL, 7),
    PROTECT_ITEM("Protect Item", 35454993, 25, VarbitID.PRAYER_PROTECTITEM, 8),
    HAWK_EYE("Hawk eye", 35455004, 26, VarbitID.PRAYER_HAWKEYE, 20),
    MYSTIC_LORE("Mystic Lore", 35455007, 27, VarbitID.PRAYER_MYSTICLORE, 21),
    STEEL_SKIN("Steel Skin", 35454994, 28, VarbitID.PRAYER_STEELSKIN, 9),
    ULTIMATE_STRENGTH("Ultimate Strength", 35454995, 31, VarbitID.PRAYER_ULTIMATESTRENGTH, 10),
    INCREDIBLE_REFLEXES("Incredible Reflexes", 35454996, 34, VarbitID.PRAYER_INCREDIBLEREFLEXES, 11),
    PROTECT_MAGIC("Protect From Magic", 35454997, 37, VarbitID.PRAYER_PROTECTFROMMAGIC, 12),
    PROTECT_RANGE("Protect From Missiles", 35454998, 40, VarbitID.PRAYER_PROTECTFROMMISSILES, 13),
    PROTECT_MELEE("Protect From Melee", 35454999, 43, VarbitID.PRAYER_PROTECTFROMMELEE, 14),
    EAGLE_EYE("Eagle Eye", 35455005, 44, VarbitID.PRAYER_EAGLEEYE, 22),
    MYSTIC_MIGHT("Mystic Might", 35455008, 45, VarbitID.PRAYER_MYSTICMIGHT, 23),
    RETRIBUTION("Retribution", 35455000, 46, VarbitID.PRAYER_RETRIBUTION, 15),
    REDEMPTION("Redemption", 35455001, 49, VarbitID.PRAYER_REDEMPTION, 16),
    SMITE("Smite", 35455002, 52, VarbitID.PRAYER_SMITE, 17),
    PRESERVE("Preserve", 35455013, 55, VarbitID.PRAYER_PRESERVE, 28),
    CHIVALRY("Chivalry", 35455010,60, VarbitID.PRAYER_CHIVALRY, 25),
    PIETY("Piety", 35455011, 70, VarbitID.PRAYER_PIETY, 26),
    RIGOUR("Rigour", 35455009, 74, VarbitID.PRAYER_RIGOUR, 24),
    AUGURY("Augury", 35455012, 77, VarbitID.PRAYER_AUGURY, 27);

    private final String name;
    @Component
    private final int index;
    private final int level;
    @Varbit
    private final int varbit;
    private final int quickPrayerIndex;
}
