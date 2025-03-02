package net.runelite.client.plugins.microbot.frosty.butterflyjars;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Butterfly jar buyer",
        description = "Automates buying butterfly jars from Artimeus",
        tags = {"butterfly", "jars", "script", "frosty", "hunter"},
        enabledByDefault = false
)
public class ButterflyJarsPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private ButterflyJarsConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ButterflyJarsOverlay overlay;

    private ButterflyJarsScript script = new ButterflyJarsScript();

    @Getter
    private boolean running = false;

    @Provides
    ButterflyJarsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ButterflyJarsConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
        log.info("Butterfly Jars Plugin Started!");
        startScript(); // <--- Ensure script starts when plugin is enabled
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(overlay);
        stopScript();
        log.info("Butterfly Jars Plugin Stopped!");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGIN_SCREEN && running) {
            log.info("Player logged out, stopping script.");
            stopScript();
        }
    }

    public void startScript() {
        if (!running) {
            log.info("Attempting to start Butterfly Jars Script...");
            running = script.run(config);
            if (running) {
                log.info("Butterfly Jars Script Started Successfully!");
            } else {
                log.error("Butterfly Jars Script Failed to Start!");
            }
        } else {
            log.warn("Butterfly Jars Script is already running.");
        }
    }

    public void stopScript() {
        if (running) {
            script.shutdown();
            running = false;
            log.info("Butterfly Jars Script Stopped!");
        }
    }
}
