package net.runelite.client.plugins.microbot.bga.autoboltenchanter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
    name = "[bga] Auto Bolt Enchanter",
    description = "Automatically enchants crossbow bolts at the bank",
    tags = {"magic"},
    enabledByDefault = false
)
@Slf4j
public class AutoBoltEnchanterPlugin extends Plugin {
    
    @Inject
    private AutoBoltEnchanterConfig config;
    
    @Inject
    private AutoBoltEnchanterScript script;

    @Provides
    AutoBoltEnchanterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBoltEnchanterConfig.class); // give the config to dependency injection system
    }

    @Override
    protected void startUp() throws AWTException {
        script.run(config); // start running the main script with our config
    }

    @Override
    protected void shutDown() {
        script.shutdown(); // tell the script to stop running
    }
}