package net.runelite.client.plugins.microbot.commandcenter;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.Properties;

@PluginDescriptor(
    name = "CC Script Auto-Start",
    description = "Auto-start a script plugin after login from Command Center profile",
    enabledByDefault = false
)
@Slf4j
public class ScriptAutoStartPlugin extends Plugin {
    @Inject
    private PluginManager pluginManager;

    private String targetScriptName;
    private boolean startAttempted;

    @Override
    protected void startUp() {
        startAttempted = false;
        String profileDir = System.getProperty("cc-profile-dir");
        if (profileDir == null || profileDir.isEmpty()) {
            log.warn("No --cc-profile-dir set, ScriptAutoStart disabled");
            return;
        }

        try (var fis = new FileInputStream(
                Paths.get(profileDir).resolve("commandcenter.properties").toFile())) {
            Properties config = new Properties();
            config.load(fis);
            targetScriptName = config.getProperty("script");
        } catch (Exception e) {
            log.warn("Failed to read commandcenter.properties: {}", e.getMessage());
        }

        if (targetScriptName == null || targetScriptName.isEmpty()) {
            log.info("No script configured for auto-start");
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() != GameState.LOGGED_IN) return;
        if (startAttempted) return;
        if (targetScriptName == null || targetScriptName.isEmpty()) return;

        startAttempted = true;

        // Find matching plugin by @PluginDescriptor name
        for (Plugin plugin : pluginManager.getPlugins()) {
            PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
            if (descriptor != null && descriptor.name().equals(targetScriptName)) {
                try {
                    pluginManager.setPluginEnabled(plugin, true);
                    pluginManager.startPlugin(plugin);
                    log.info("ScriptAutoStart: enabled '{}'", targetScriptName);
                    return;
                } catch (Exception e) {
                    log.error("Failed to start plugin '{}': {}", targetScriptName, e.getMessage());
                    return;
                }
            }
        }

        log.warn("ScriptAutoStart: plugin '{}' not found", targetScriptName);
    }

    @Override
    protected void shutDown() {
        targetScriptName = null;
    }
}
