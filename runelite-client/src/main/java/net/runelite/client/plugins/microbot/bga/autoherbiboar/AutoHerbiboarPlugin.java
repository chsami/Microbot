package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import com.google.inject.Provides;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.herbiboars.HerbiboarPlugin;
import net.runelite.client.ui.overlay.OverlayManager;
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
    private PluginManager pluginManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoHerbiboarOverlay overlay;
    private HerbiboarPlugin herbiboarPlugin;
    @Inject
    private AutoHerbiboarScript script;
    @Override
    protected void startUp() throws AWTException {
        herbiboarPlugin = pluginManager.getPlugins().stream().filter(HerbiboarPlugin.class::isInstance).map(HerbiboarPlugin.class::cast).findFirst().orElse(null);
        if (herbiboarPlugin != null && !pluginManager.isPluginEnabled(herbiboarPlugin)) pluginManager.setPluginEnabled(herbiboarPlugin, true);
        if (herbiboarPlugin != null && !pluginManager.getActivePlugins().contains(herbiboarPlugin)) try { pluginManager.startPlugin(herbiboarPlugin); } catch (Exception ignored) {}
        script.setHerbiboarPlugin(herbiboarPlugin);
        overlayManager.add(overlay);
        script.run(config);
    }
    @Override
    protected void shutDown() { 
        overlayManager.remove(overlay);
        script.shutdown(); 
    }
}
