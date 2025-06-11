package net.runelite.client.plugins.microbot.maxxin.astralrc;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup(AstralRunesConfig.configGroup)
@ConfigInformation(
        "Plugin to craft Astral runes. <br/>" +
        "<b>REQUIRED ITEMS:</b><br/>" +
        "-Rune pouch w/ Law, Astral, and Cosmic runes, Dust staff, pure essence, stamina pots, configured food in bank<br/>" +
        "Make sure spell book is set to Lunar. <br/><br/>" +
        "Only supports Colossal pouch and will use NPC contact for repairs.<br/><br/>" +
        "Food is required to survive hits from mobs when banking. <br/><br/>" +
        "<b>EXPERIMENTAL: </b>Auto Setup supports Inventory Setup, Teleport to Lunar Isle (Lunar Isle Teleport scroll in bank or spell), Spellbook switch to Lunar using altar on Lunar Isle, Loading rune pouch loadout from bank <b>(Make sure this is configured with right runes and correct loadout configured!)</b><br/><br/>"
)
public interface AstralRunesConfig extends Config {
    String configGroup = "astral-runes";
    String generalSection = "general";
    String autoSetup = "autoSetup";
    String inventorySetup = "inventorySetup";
    String foodType = "foodType";
    String runePouchLoadout = "runePouchLoadout";

    @ConfigSection(name = "Food Options", description = "Settings for selecting food", position = 0)
    String foodSection = "foodSection";
    @ConfigItem(
            keyName = foodType,
            name = "Food Type",
            description = "Select the type of food to use",
            position = 1,
            section = foodSection
    )
    default Rs2Food foodType() {
        return Rs2Food.LOBSTER;
    }

    @ConfigSection(name = "Auto Setup Options", description = "Settings for experimental auto setup", position = 1)
    String autoSetupSection = "autoSetupSection";
    @ConfigItem(
            keyName = autoSetup,
            name = "Auto Setup",
            description = "Equip setup for astral runes based on MInventory setup and set spellbook by Lunar altar",
            position = 2,
            section = autoSetupSection
    )
    default boolean autoSetup() {
        return false;
    }

    @ConfigItem(
            keyName = inventorySetup,
            name = "Inventory Setup",
            description = "Inventory setup to equip",
            position = 2,
            section = autoSetupSection
    )
    default InventorySetup inventorySetup() {
        return null;
    }

    @ConfigItem(
            keyName = runePouchLoadout,
            name = "Rune Pouch",
            description = "Select the rune pouch loadout to use",
            position = 2,
            section = autoSetupSection
    )
    default RunePouchLoadout runePouchLoadout() {
        return RunePouchLoadout.LOADOUT_A;
    }

    @Getter
    @AllArgsConstructor
    enum RunePouchLoadout {
        LOADOUT_A("Loadout A", 983069),
        LOADOUT_B("Loadout B", 983071),
        LOADOUT_C("Loadout C", 983073),
        LOADOUT_D("Loadout D", 983075);
        final String name;
        final int widgetId;
    }
}
