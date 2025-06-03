package net.runelite.client.plugins.microbot.BurgerLooter;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
    name = "Burger Looter",
    description = "Loots Red Eclipse bottles, hops worlds, banks, and sells.",
    tags = {"loot", "burger", "eclipse"}
)
public class BurgerLooterPlugin extends Plugin {
    @Inject private Client client;
    @Inject private OverlayManager overlayManager;
    @Inject private BurgerLooterOverlay overlay;
    @Inject private BurgerLooterConfig config;
    @Getter private BurgerLooterScript script;

    @Override
    protected void startUp() throws Exception {
        script = new BurgerLooterScript();
        script.run(config);
        overlayManager.add(overlay);
    }

    @Override
    protected void shutDown() throws Exception {
        if (script != null) script.shutdown();
        overlayManager.remove(overlay);
    }

    @Provides
    BurgerLooterConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BurgerLooterConfig.class);
    }

    public String getStateName() {
        return script != null ? script.getStateName() : "N/A";
    }
    public int getTotalLooted() {
        return script != null ? script.getTotalLooted() : 0;
    }
    public String getRunningTime() {
        return script != null ? script.getRunningTime() : "00:00:00";
    }
}
