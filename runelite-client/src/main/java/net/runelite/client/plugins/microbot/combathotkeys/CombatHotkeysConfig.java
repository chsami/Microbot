package net.runelite.client.plugins.microbot.combathotkeys;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.*;

@ConfigInformation("IMPORTANT!<br/>"
        + "When Setting up hotkeys make sure to use something like CTRL +, ALT + or SHIFT +<br/><br/>"
        + "This because it does not disable chat yet and will spam it."
        + "</html>")

@ConfigGroup("combathotkeys")
public interface CombatHotkeysConfig extends Config {
    @ConfigSection(
            name = "Prayers",
            description = "Prayer hotkeys",
            position = 1
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
            position = 2
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
            position = 3
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
            position = 4
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
            position = 5
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
            position = 6
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

//    // config item for a keybind to enable the dance feature
//    @ConfigItem(
//            keyName = "dance",
//            name = "Dance",
//            description = "Dance",
//            position = 7
//    )
//    default Keybind dance() {
//        return Keybind.NOT_SET;
//    }

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
}
