package net.runelite.client.plugins.microbot.globval.enums;


import net.runelite.client.plugins.microbot.globval.WidgetIndices;

public enum Prayers {
    THICK_SKIN(0, 1, WidgetIndices.PrayersTab.THICK_SKIN, 0),
    BURST_OF_STRENGTH(1, 4, WidgetIndices.PrayersTab.BURST_OF_STRENGTH, 1),
    CLARITY_OF_THOUGHT(2, 7, WidgetIndices.PrayersTab.CLARITY_OF_THOUGHT, 2),
    SHARP_EYE(3, 8, WidgetIndices.PrayersTab.SHARP_EYE, 18),
    MYSTIC_WILL(4, 9, WidgetIndices.PrayersTab.MYSTIC_WILL, 19),
    ROCK_SKIN(5, 10, WidgetIndices.PrayersTab.ROCK_SKIN, 3),
    SUPERHUMAN_STRENGTH(6, 13, WidgetIndices.PrayersTab.SUPERHUMAN_STRENGTH, 4),
    IMPROVED_REFLEXES(7, 16, WidgetIndices.PrayersTab.IMPROVED_REFLEXES, 5),
    RAPID_RESTORE(8, 19, WidgetIndices.PrayersTab.RAPID_RESTORE, 6),
    RAPID_HEAL(9, 22, WidgetIndices.PrayersTab.RAPID_HEAL, 7),
    PROTECT_ITEM(10, 25, WidgetIndices.PrayersTab.PROTECT_ITEM, 8),
    HAWK_EYE(11, 26, WidgetIndices.PrayersTab.HAWK_EYE, 20),
    MYSTIC_LORE(12, 27, WidgetIndices.PrayersTab.MYSTIC_LORE, 21),
    STEEL_SKIN(13, 28, WidgetIndices.PrayersTab.STEEL_SKIN, 9),
    ULTIMATE_STRENGTH(14, 31, WidgetIndices.PrayersTab.ULTIMATE_STRENGTH, 10),
    INCREDIBLE_REFLEXES(15, 34, WidgetIndices.PrayersTab.INCREDIBLE_REFLEXES, 11),
    PROTECT_FROM_MAGIC(17, 37, WidgetIndices.PrayersTab.PROTECT_FROM_MAGIC, 12),
    PROTECT_FROM_MISSILES(18, 40, WidgetIndices.PrayersTab.PROTECT_FROM_MISSILES, 13),
    PROTECT_FROM_MELEE(19, 43, WidgetIndices.PrayersTab.PROTECT_FROM_MELEE, 14),

    EAGLE_EYE(20, 44, WidgetIndices.PrayersTab.EAGLE_EYE, 22),
    DEAD_EYE(20, 44, WidgetIndices.PrayersTab.DEAD_EYE, 22),

    MYSTIC_MIGHT(21, 45, WidgetIndices.PrayersTab.MYSTIC_MIGHT, 23),
    MYSTIC_VIGOUR(21, 45, WidgetIndices.PrayersTab.MYSTIC_VIGOUR, 23),

    RETRIBUTION(22, 46, WidgetIndices.PrayersTab.RETRIBUTION, 15),
    REDEMPTION(23, 49, WidgetIndices.PrayersTab.REDEMPTION, 16),
    SMITE(24, 52, WidgetIndices.PrayersTab.SMITE, 17),
    PRESERVE(25, 55, WidgetIndices.PrayersTab.PRESERVE, 28),
    CHIVALRY(26, 60, WidgetIndices.PrayersTab.CHIVALRY, 25),
    PIETY(27, 70, WidgetIndices.PrayersTab.PIETY, 26),
    RIGOUR(28, 74, WidgetIndices.PrayersTab.RIGOUR, 24),
    AUGURY(29, 77, WidgetIndices.PrayersTab.AUGURY, 27);

    private final int index;
    private final int level;
    private final int prayerId;
    private final int quickPrayerId;

    Prayers(int index, int level, int prayerId, int quickPrayerId) {
        this.index = index;
        this.level = level;
        this.prayerId = prayerId;
        this.quickPrayerId = quickPrayerId;
    }

    public int getPrayerId() {
        return prayerId;
    }
    public int getQuickPrayerId() {
        return quickPrayerId;
    }
    public int getRequiredLevel() {
        return level;
    }
}