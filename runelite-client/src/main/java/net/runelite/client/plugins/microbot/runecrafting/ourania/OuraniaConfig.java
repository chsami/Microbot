package net.runelite.client.plugins.microbot.runecrafting.ourania;

import net.runelite.client.config.*;
import net.runelite.client.plugins.microbot.magic.orbcharger.enums.Teleport;
import net.runelite.client.plugins.microbot.runecrafting.ourania.enums.Essence;
import net.runelite.client.plugins.microbot.runecrafting.ourania.enums.Path;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup(OuraniaConfig.configGroup)
@ConfigInformation(
        "• This plugin will craft runes at Ourania Altar (ZMI Altar) <br />" +
        "• Ensure you have auto-pay configured with Eniola. <br />" +
        "• Requires Lunar Spellbook and to speak with Baby Yaga to learn Ourania Teleport. <br />" +
        "• Ensure you have a runepouch with your runes for teleport to Ourania & payment runes. <br />"
)
public interface OuraniaConfig extends Config {
    String configGroup = "micro-ourania";
    
    String essence = "essence";
    String path = "path";
    String useEnergyRestorePotions = "useEnergyRestorePotions";
    String food = "food";
    String eatAtPercent = "eatAtPercent";
    String drinkAtPercent = "drinkAtPercent";
    String toggleOverlay = "toggleOverlay";
    
    @ConfigSection(
            name = "General",
            description = "General Plugin Settings",
            position = 0
    )
    String generalSection = "general";

    @ConfigSection(
            name = "Supplies",
            description = "Supplies Settings",
            position = 1
    )
    String suppliesSection = "supplies";

    @ConfigSection(
            name = "Overlay",
            description = "Overlay Settings",
            position = 2
    )
    String overlaySection = "overlay";


    @ConfigItem(
            keyName = path,
            name = "Path to Walk",
            description = "Select what path you would like to take",
            position = 0,
            section = generalSection
    )
    default Path path() {
        return Path.SHORT;
    }

    @ConfigItem(
            keyName = essence,
            name = "Essence",
            description = "Select essence you would like to train with",
            position = 0,
            section = generalSection
    )
    default Essence essence() {
        return Essence.PURE_ESSENCE;
    }

    @ConfigItem(
            keyName = food,
            name = "Food",
            description = "Select food that should be used when low HP (eats when at bank)",
            position = 0,
            section = suppliesSection
    )
    default Rs2Food food() {
        return Rs2Food.SALMON;
    }

    @Range(
            min = 1,
            max = 100
    )
    @ConfigItem(
            keyName = eatAtPercent,
            name = "Eat At",
            description = "Percent health player should eat at the bank",
            position = 1,
            section = suppliesSection
    )
    default int eatAtPercent() {
        return 65;
    }

    @ConfigItem(
            keyName = useEnergyRestorePotions,
            name = "Use Energy Restore Potions",
            description = "Should withdraw & use stamina potions OR energy potions",
            position = 2,
            section = suppliesSection
    )
    default boolean useEnergyRestorePotions() {
        return false;
    }


    @Range(
            min = 1,
            max = 100
    )
    @ConfigItem(
            keyName = drinkAtPercent,
            name = "Drink Stamina At",
            description = "Run energy should drink stamina at",
            position = 3,
            section = suppliesSection
    )
    default int drinkAtPercent() {
        return 45;
    }

    @ConfigItem(
            keyName = toggleOverlay,
            name = "Toggle Overlay",
            description = "Should hide the overlay",
            position = 0,
            section = overlaySection
    )
    default boolean toggleOverlay() {
        return false;
    }
}
