package net.runelite.client.plugins.microbot.hal.halsutility.modules;

import lombok.Getter;
import net.runelite.client.config.Config;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.activity.example.ExampleActivityModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.bossing.example.ExampleBossingModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.moneymaking.example.ExampleMoneyModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.blessedwine.BlessedWineModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.example.ExampleSkillingModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.utility.example.ExampleUtilityModule;

public enum HalModule {
    // Example modules - you can add more as needed
    EXAMPLE_SKILLING(new ExampleSkillingModule(), HalModuleCategory.SKILLING, "Example Skilling"),
    BLESSED_WINE(new BlessedWineModule(), HalModuleCategory.SKILLING, "Blessed Wine"),
    EXAMPLE_MONEY(new ExampleMoneyModule(), HalModuleCategory.MONEY, "Example Money Making"),
    EXAMPLE_UTILITY(new ExampleUtilityModule(), HalModuleCategory.UTILITY, "Example Utility"),
    EXAMPLE_ACTIVITY(new ExampleActivityModule(), HalModuleCategory.ACTIVITY, "Example Activity"),
    EXAMPLE_BOSSING(new ExampleBossingModule(), HalModuleCategory.BOSSING, "Example Bossing");

    @Getter
    private final HalSubPlugin plugin;
    
    @Getter
    private final HalModuleCategory category;
    
    @Getter
    private final String displayName;
    
    @Getter
    private Config runtimeConfig;

    HalModule(HalSubPlugin plugin, HalModuleCategory category, String displayName) {
        this.plugin = plugin;
        this.category = category;
        this.displayName = displayName;
    }

    public void setRuntimeConfig(Config config) {
        this.runtimeConfig = config;
    }

    public Class<? extends Config> getConfigClass() {
        return plugin.getConfigClass();
    }
}