package net.runelite.client.plugins.microbot.autoworldhopper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.autoworldhopper.scripts.WorldHopScript;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Auto World Hopper",
        description = "Automatically hops worlds based on player count, time, or chat activity",
        tags = {"world", "hopper", "auto", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class AutoWorldHopperPlugin extends Plugin {
    public static final String version = "1.0.0";

    @Inject
    private Client client;

    @Inject
    private AutoWorldHopperConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoWorldHopperInfoOverlay infoOverlay;

    @Inject
    private AutoWorldHopperOverlay visualOverlay;

    @Inject
    private WorldService worldService;

    @Inject
    private net.runelite.client.chat.ChatMessageManager chatMessageManager;

    private final WorldHopScript worldHopScript = new WorldHopScript();
    private long lastChatTime = 0;

    @Provides
    AutoWorldHopperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoWorldHopperConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        
        if (overlayManager != null) {
            overlayManager.add(infoOverlay);
            overlayManager.add(visualOverlay);
        }
        
        if (config.enabled()) {
            worldHopScript.run(config, worldService, chatMessageManager);
            log.info("Auto World Hopper started");
        }
    }

    @Override
    protected void shutDown() throws Exception {
        worldHopScript.shutdown();
        
        if (overlayManager != null) {
            overlayManager.remove(infoOverlay);
            overlayManager.remove(visualOverlay);
        }
        
        log.info("Auto World Hopper stopped");
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(AutoWorldHopperConfig.GROUP)) {
            return;
        }

        if (event.getKey().equals("enabled")) {
            if (config.enabled()) {
                worldHopScript.run(config, worldService, chatMessageManager);
                log.info("Auto World Hopper enabled via config");
            } else {
                worldHopScript.shutdown();
                log.info("Auto World Hopper disabled via config");
            }
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            // Reset timers and resume when logging in
            worldHopScript.resetTimers();
            worldHopScript.resume();
        } else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
            // Stop script when logged out
            worldHopScript.pause();
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (!config.enabled() || !Microbot.isLoggedIn()) {
            return;
        }

        // Update the script with current game tick
        worldHopScript.onGameTick();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (!config.enabled() || !config.enableChatDetection()) {
            return;
        }

        // Only react to public chat messages
        if (chatMessage.getType() != ChatMessageType.PUBLICCHAT) {
            return;
        }

        String playerName = Text.removeTags(chatMessage.getName());
        String localPlayerName = client.getLocalPlayer().getName();

        // Ignore our own messages
        if (playerName.equals(localPlayerName)) {
            return;
        }

        // Ignore friends if configured
        if (config.ignoreFriends() && client.getFriendContainer() != null 
            && client.getFriendContainer().findByName(playerName) != null) {
            return;
        }

        // Trigger world hop due to chat activity
        lastChatTime = System.currentTimeMillis();
        worldHopScript.triggerChatHop(playerName, chatMessage.getMessage());
        
        if (config.showNotifications()) {
            Microbot.showMessage("Chat detected from " + playerName + ", hopping worlds...");
        }
    }

    public long getLastChatTime() {
        return lastChatTime;
    }

    public WorldHopScript getWorldHopScript() {
        return worldHopScript;
    }
}
