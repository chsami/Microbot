package net.runelite.client.plugins.microbot.zerozero.moonlightmoth;

import net.runelite.client.config.*;

@ConfigInformation("This will automatically catch and bank moonlight moths")
@ConfigGroup(MoonlightMothPlugin.CONFIG)
public interface MoonlightMothConfig extends Config {

    @ConfigItem(
            keyName = "debugMessages",
            name = "Debug Messages",
            description = "enable debug messages if any problems",
            position = 5
    )
    static boolean debugMessages() {
        return false;
    }

    @ConfigItem(
            keyName = "equipGraceful",
            name = "Graceful?",
            description = "Will equip graceful",
            position = 0
    )
    default boolean equipGraceful() {
        return true;
    }

    @ConfigItem(
            keyName = "actionPreference",
            name = "Get jars from?",
            description = "Choose whether to Bank or Shop",
            position = 1
    )
    default ActionPreference actionPreference() {
        return ActionPreference.BANK;
    }

    @ConfigItem(
            keyName = "enableWorldHopping",
            name = "Enable World Hopping",
            description = "Toggle to enable or disable world hopping when players are detected nearby.",
            position = 2

    )
    default boolean enableWorldHopping() {
        return false; // Default to false
    }

    @ConfigItem(
            keyName = "useStamina",
            name = "Use Stamina Potions",
            description = "Toggle to use stamina potions when run energy is low during banking.",
            position = 3

    )
    default boolean useStamina() {
        return false; // Default to false
    }

    @ConfigItem(
            keyName = "staminaThreshold",
            name = "Stamina Threshold",
            description = "The run energy threshold below which a stamina potion will be used at the bank",
            position = 4
    )
    @Range(
            min = 10,
            max = 100
    )
    default int staminaThreshold() {
        return 60; // Default threshold
    }

    enum ActionPreference {
        BANK,
        SHOP
    }
}
