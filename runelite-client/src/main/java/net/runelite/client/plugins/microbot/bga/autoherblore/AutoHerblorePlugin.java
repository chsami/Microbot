package net.runelite.client.plugins.microbot.bga.autoherblore;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
    name = "[bga] Auto Herblore",
    description = "Performs various herblore tasks...",
    tags = {"herblore","skilling"},
    enabledByDefault = false
)
public class AutoHerblorePlugin extends Plugin {
    @Inject
    private AutoHerbloreConfig config;
    @Provides
    AutoHerbloreConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(AutoHerbloreConfig.class); }
    @Inject
    private AutoHerbloreScript script;
    @Override
    protected void startUp() throws AWTException { script.run(config); }
    @Override
    protected void shutDown() { script.shutdown(); }
}
