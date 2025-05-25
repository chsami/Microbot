package net.runelite.client.plugins.microbot.kebbitHunter;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;

/**
 * Main plugin class for Kebbit hunting automation.
 * Manages the script lifecycle and event handlers.
 */
@Slf4j
@PluginDescriptor(
        name = "Kebbits made By Vip",
        description = "Automates Kebbits hunting in Piscatoris Hunter area it can only Hunt Spotted Kebbits, Dark Kebbits and Dashing Kebbits ",
        tags = {"hunter", "kebbits", "skilling"},
        enabledByDefault = false
)
public class HunterKebbitsPlugin extends Plugin {

    @Inject
    private Client client;
    @Inject
    private HunterKebbitsConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HunterKebbitsOverlay kebbitsOverlay;
    private HunterKabbitsScript script;
    private Instant scriptStartTime;

    @Provides
    HunterKebbitsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HunterKebbitsConfig.class);
    }

    @Override
    protected void startUp() {
        scriptStartTime = Instant.now();
        overlayManager.add(kebbitsOverlay);
        script = new HunterKabbitsScript();
        script.run(config, this);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(kebbitsOverlay);
        if (script != null) {
            script.shutdown();
        }
        scriptStartTime = null;
    }

    /**
     * Handles death messages to update script state.
     *
     * @param event The chat message event
     */
    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE &&
                event.getMessage().equalsIgnoreCase("oh dear, you are dead!")) {
            script.hasDied = true;
        }
    }

    /**
     * Gets the formatted runtime duration.
     *
     * @return Formatted time string
     */
    public String getTimeRunning() {
        return scriptStartTime != null ?
                TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }
}