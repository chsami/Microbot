package net.runelite.client.plugins.microbot.hal.halsutility.modules.moneymaking.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("examplemoney")
public interface ExampleMoneyConfig extends Config {
    
    @ConfigSection(
            name = "General Settings",
            description = "General settings for the money making module",
            position = 0
    )
    String generalSection = "general";
    
    @ConfigItem(
            keyName = "enabled",
            name = "Enabled",
            description = "Enable example money making module",
            section = generalSection,
            position = 0
    )
    default boolean enabled() {
        return false;
    }
    
    @ConfigItem(
            keyName = "method",
            name = "Money Making Method",
            description = "Which money making method to use",
            section = generalSection,
            position = 1
    )
    default String method() {
        return "Flipping";
    }
    
    @ConfigSection(
            name = "Profit Settings",
            description = "Settings related to profit calculation",
            position = 1
    )
    String profitSection = "profit";
    
    @ConfigItem(
            keyName = "minProfit",
            name = "Minimum Profit",
            description = "Minimum profit per item to consider",
            section = profitSection,
            position = 0
    )
    default int minProfit() {
        return 100;
    }
    
    @ConfigItem(
            keyName = "maxInvestment",
            name = "Maximum Investment",
            description = "Maximum amount to invest in a single item",
            section = profitSection,
            position = 1
    )
    default int maxInvestment() {
        return 1000000;
    }
} 