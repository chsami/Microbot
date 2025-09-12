package net.runelite.client.plugins.microbot.combathotkeys;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

@ConfigInformation("IMPORTANT!<br/>"
        + "When Setting up hotkeys make sure to use something like CTRL +, ALT + or SHIFT +<br/><br/>"
        + "This because it does not disable chat yet and will spam it."
        + "</html>")

@ConfigGroup("combathotkeys")
public interface CombatHotkeysConfig extends Config {

    @ConfigSection(
            name = "Offensive Prayers",
            description = "Offensive Prayer hotkeys",
            position = 1
    )
    String offensivePrayerSection = "offensivePrayers";

    // Melee
    @ConfigItem(
            keyName = "offensiveMeleeKey",
            name = "Melee Prayer Hotkey",
            description = "Hotkey for offensive melee prayer",
            position = 0,
            section = offensivePrayerSection
    )
    default Keybind offensiveMeleeKey() { return Keybind.NOT_SET; }

    @ConfigItem(
            keyName = "offensiveMeleePrayer",
            name = "Melee Prayer",
            description = "Prayer to toggle with the melee hotkey",
            position = 1,
            section = offensivePrayerSection
    )
    default MeleePrayerOption offensiveMeleePrayer() { return MeleePrayerOption.PIETY; }

    // Ranged
    @ConfigItem(
            keyName = "offensiveRangeKey",
            name = "Ranged Prayer Hotkey",
            description = "Hotkey for offensive ranged prayer",
            position = 2,
            section = offensivePrayerSection
    )
    default Keybind offensiveRangeKey() { return Keybind.NOT_SET; }

    @ConfigItem(
            keyName = "offensiveRangePrayer",
            name = "Ranged Prayer",
            description = "Prayer to toggle with the ranged hotkey",
            position = 3,
            section = offensivePrayerSection
    )
    default RangedPrayerOption offensiveRangePrayer() { return RangedPrayerOption.RIGOUR; }

    // Magic
    @ConfigItem(
            keyName = "offensiveMagicKey",
            name = "Magic Prayer Hotkey",
            description = "Hotkey for offensive magic prayer",
            position = 4,
            section = offensivePrayerSection
    )
    default Keybind offensiveMagicKey() { return Keybind.NOT_SET; }

    @ConfigItem(
            keyName = "offensiveMagicPrayer",
            name = "Magic Prayer",
            description = "Prayer to toggle with the magic hotkey",
            position = 5,
            section = offensivePrayerSection
    )
    default MagicPrayerOption offensiveMagicPrayer() { return MagicPrayerOption.AUGURY; }

    @ConfigSection(
            name = "Defensive Prayers",
            description = "Defensive Prayer hotkeys",
            position = 2
    )
    String prayerSection = "prayers";

    @ConfigItem(
        keyName = "Protect from Magic",
        name = "Protect from Magic",
        description = "Protect from Magic keybind",
        position = 0,
        section = prayerSection
    )
    default Keybind protectFromMagic()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "Protect from Missiles",
            name = "Protect from Missiles",
            description = "Protect from Missiles keybind",
            position = 1,
            section = prayerSection
    )
    default Keybind protectFromMissles()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "Protect from Melee",
            name = "Protect from Melee",
            description = "Protect from Melee keybind",
            position = 2,
            section = prayerSection
    )
    default Keybind protectFromMelee()
    {
        return Keybind.NOT_SET;
    }

    @ConfigSection(
            name = "Gear setup 1",
            description = "Gear setup 1",
            position = 3
    )
    String gearSetup1 = "gearSetup1";

    @ConfigItem(
            keyName = "Hotkey for gear 1",
            name = "Hotkey for gear 1",
            description = "Hotkey for gear 1",
            position = 1,
            section = gearSetup1
    )
    default Keybind gear1()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "Gear IDs 1",
            name = "Gear IDs",
            description = "List of Gear IDs comma separated",
            position = 2,
            section = gearSetup1
    )
    default String gearList1()
    {
        return "";
    }

    @ConfigSection(
            name = "Gear setup 2",
            description = "Gear setup 2",
            position = 4
    )
    String gearSetup2 = "gearSetup2";

    @ConfigItem(
            keyName = "Hotkey for gear 2",
            name = "Hotkey for gear 2",
            description = "Hotkey for gear 2",
            position = 1,
            section = gearSetup2
    )
    default Keybind gear2()
    {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "Gear IDs 2",
            name = "Gear IDs",
            description = "List of Gear IDs comma separated",
            position = 2,
            section = gearSetup2
    )
    default String gearList2()
    {
        return "";
    }

    @ConfigSection(
            name = "Gear setup 3",
            description = "Gear setup 3",
            position = 5
    )
    String gearSetup3 = "gearSetup3";

    @ConfigItem(
            keyName = "Hotkey for gear 3",
            name = "Hotkey for gear 3",
            description = "Hotkey for gear 3",
            position = 1,
            section = gearSetup3
    )
    default Keybind gear3() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "Gear IDs 3",
            name = "Gear IDs",
            description = "List of Gear IDs comma separated",
            position = 2,
            section = gearSetup3
    )
    default String gearList3() {
        return "";
    }

    @ConfigSection(
            name = "Gear setup 4",
            description = "Gear setup 4",
            position = 6
    )
    String gearSetup4 = "gearSetup4";

    @ConfigItem(
            keyName = "Hotkey for gear 4",
            name = "Hotkey for gear 4",
            description = "Hotkey for gear 4",
            position = 1,
            section = gearSetup4
    )
    default Keybind gear4() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "Gear IDs 4",
            name = "Gear IDs",
            description = "List of Gear IDs comma separated",
            position = 2,
            section = gearSetup4
    )
    default String gearList4() {
        return "";
    }

    @ConfigSection(
            name = "Gear setup 5",
            description = "Gear setup 5",
            position = 7
    )
    String gearSetup5 = "gearSetup5";

    @ConfigItem(
            keyName = "Hotkey for gear 5",
            name = "Hotkey for gear 5",
            description = "Hotkey for gear 5",
            position = 1,
            section = gearSetup5
    )
    default Keybind gear5() {
        return Keybind.NOT_SET;
    }

    @ConfigItem(
            keyName = "Gear IDs 5",
            name = "Gear IDs",
            description = "List of Gear IDs comma separated",
            position = 2,
            section = gearSetup5
    )
    default String gearList5() {
        return "";
    }

    @ConfigItem(
            keyName = "dance boolean",
            name = "dance",
            description = "Select this if you want to setup dance",
            position = 8
    )
    default boolean yesDance() {
        return false;
    }

    // config item for a keybind to enable the dance feature
    @ConfigItem(
            keyName = "dance",
            name = "Dance",
            description = "Dance",
            position = 7
    )
    default Keybind dance() {
        return Keybind.NOT_SET;
    }

    // hidden config for worldpoint called tile1
    @ConfigItem(
            keyName = "tile1",
            name = "",
            description = "",
            hidden = true
    )
    default WorldPoint tile1() {
        return null;
    }
    @ConfigItem(
            keyName = "tile2",
            name = "",
            description = "",
            hidden = true
    )
    default WorldPoint tile2() {
        return null;
    }

    @ConfigSection(
            name = "Gear Equip Settings",
            description = "Settings for randomized equip delays",
            position = 9
    )
    String gearEquipSettings = "gearEquipSettings";

    @ConfigItem(
            keyName = "maxDelay",
            name = "Max Equip Delay (ms)",
            description = "Maximum random delay (in milliseconds) between equipping items",
            position = 0,
            section = gearEquipSettings
    )
    default int maxDelay() {
        return 500; // default max delay of 500ms
    }

    public enum MeleePrayerOption {
        SUPERHUMAN_STRENGTH(Rs2PrayerEnum.SUPERHUMAN_STRENGTH),
        ULTIMATE_STRENGTH(Rs2PrayerEnum.ULTIMATE_STRENGTH),
        CHIVALRY(Rs2PrayerEnum.CHIVALRY),
        PIETY(Rs2PrayerEnum.PIETY);

        private final Rs2PrayerEnum prayer;

        MeleePrayerOption(Rs2PrayerEnum prayer) {
            this.prayer = prayer;
        }

        public Rs2PrayerEnum getPrayer() {
            return prayer;
        }
    }

    public enum RangedPrayerOption {
        SHARP_EYE(Rs2PrayerEnum.SHARP_EYE),
        HAWK_EYE(Rs2PrayerEnum.HAWK_EYE),
        EAGLE_EYE(Rs2PrayerEnum.EAGLE_EYE),
        //DEADEYE(Rs2PrayerEnum.DEADEYE),
        RIGOUR(Rs2PrayerEnum.RIGOUR);

        private final Rs2PrayerEnum prayer;

        RangedPrayerOption(Rs2PrayerEnum prayer) {
            this.prayer = prayer;
        }

        public Rs2PrayerEnum getPrayer() {
            return prayer;
        }
    }

    public enum MagicPrayerOption {
        MYSTIC_WILL(Rs2PrayerEnum.MYSTIC_WILL),
        MYSTIC_LORE(Rs2PrayerEnum.MYSTIC_LORE),
        MYSTIC_MIGHT(Rs2PrayerEnum.MYSTIC_MIGHT),
        AUGURY(Rs2PrayerEnum.AUGURY);

        private final Rs2PrayerEnum prayer;

        MagicPrayerOption(Rs2PrayerEnum prayer) {
            this.prayer = prayer;
        }

        public Rs2PrayerEnum getPrayer() {
            return prayer;
        }
    }
}
