package net.runelite.client.plugins.microbot.gabplugs.karambwans;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Gabulhas + "Karambwan fisher",
        description = "",
        tags = {"GabulhasKarambwans", "Gabulhas"},
        enabledByDefault = false
)
@Slf4j
public class GabulhasKarambwansPlugin extends Plugin {
    @Inject
    private GabulhasKarambwansConfig config;
    @Provides
    GabulhasKarambwansConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GabulhasKarambwansConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PluginManager pluginManager;
    @Inject
    private GabulhasKarambwansOverlay gabulhasKarambwansOverlay;

    @Inject
    GabulhasKarambwansScript gabulhasKarambwansScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(gabulhasKarambwansOverlay);
        }
        gabulhasKarambwansScript.run(config);
        GabulhasKarambwansInfo.botStatus = config.STARTING_STATE();
    }

    protected void shutDown() {
        gabulhasKarambwansScript.shutdown();
        overlayManager.remove(gabulhasKarambwansOverlay);
    }
}
