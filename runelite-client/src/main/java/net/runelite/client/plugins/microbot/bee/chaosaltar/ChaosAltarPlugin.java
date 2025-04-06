package net.runelite.client.plugins.microbot.bee.chaosaltar;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginPlugin;
import net.runelite.client.plugins.microbot.storm.plugins.PlayerMonitor.PlayerMonitorPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Bee + "Chaos Altar",
        description = "Automates bone offering at the Chaos Altar",
        tags = {"prayer", "bones", "altar"},
        enabledByDefault = false
)
@Slf4j
public class ChaosAltarPlugin extends Plugin {
    @Inject
    private ChaosAltarScript chaosAltarScript;
    @Inject
    private ChaosAltarConfig config;
    @Provides
    ChaosAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChaosAltarConfig.class);
    }
    @Inject
    private PlayerMonitorPlugin playerMonitorPlugin;
    @Inject
    private AutoLoginPlugin autoLoginPlugin;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    ChaosAltarOverlay chaosAltarOverlay;


    @Override
    protected void startUp() throws AWTException, PluginInstantiationException {
        if (overlayManager != null) {
            overlayManager.add(chaosAltarOverlay);
        }
            if (!Microbot.getPluginManager().isPluginEnabled(playerMonitorPlugin)) {Microbot.getPluginManager().startPlugin(playerMonitorPlugin);}
            if (!Microbot.getPluginManager().isPluginEnabled(autoLoginPlugin)) {Microbot.getPluginManager().startPlugin(autoLoginPlugin);}
        chaosAltarScript.run(config);
    }

    protected void shutDown() {
        chaosAltarScript.shutdown();
        overlayManager.remove(chaosAltarOverlay);
    }


}
