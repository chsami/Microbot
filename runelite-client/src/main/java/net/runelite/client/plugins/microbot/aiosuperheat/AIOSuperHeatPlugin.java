package net.runelite.client.plugins.microbot.aiosuperheat;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(name = PluginDescriptor.See1Duck
        + "AIO Superheat", description = "Microbot AIO Superheat plugin", tags = {
                "magic", "microbot", "skilling", "superheat" }, hidden = false, enabledByDefault = false)
public class AIOSuperHeatPlugin extends Plugin {
    @Inject
    private AIOSuperHeatConfig config;

    @Provides
    AIOSuperHeatConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AIOSuperHeatConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AIOSuperHeatOverlay aioSuperHeatOverlay;

    @Inject
    Client client;

    public static String version = "1.0.0";

    @Inject
    private AIOSuperHeatScript superHeatScript;

    @Override
    protected void startUp() throws Exception {
        if (aioSuperHeatOverlay != null) {
            overlayManager.add(aioSuperHeatOverlay);
        }
        net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings.loadFromProfile();
        superHeatScript.run(config);
    }

    @Override
    protected void shutDown() {
        superHeatScript.shutdown();
        overlayManager.remove(aioSuperHeatOverlay);
    }
}
