package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.awt.AWTException;

@PluginDescriptor(
    name = "[bga] Auto Herbiboar",
    description = "Automatically hunts herbiboars...",
    tags = {"skilling", "hunter"},
    enabledByDefault = false
)
public class AutoHerbiboarPlugin extends Plugin {
    @Inject
    private AutoHerbiboarConfig config;
    @Provides
    AutoHerbiboarConfig provideConfig(ConfigManager configManager) { return configManager.getConfig(AutoHerbiboarConfig.class); }
    @Inject
    private AutoHerbiboarScript script;
    @Override
    protected void startUp() throws AWTException { script.run(config); }
    @Override
    protected void shutDown() { script.shutdown(); }
}
