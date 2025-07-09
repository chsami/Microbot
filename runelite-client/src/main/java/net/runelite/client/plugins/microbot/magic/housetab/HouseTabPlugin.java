package net.runelite.client.plugins.microbot.magic.housetab;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.magic.housetab.enums.HOUSETABS_CONFIG;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "HouseTab",
        description = "Microbot HouseTab plugin",
        tags = {"microbot", "magic", "moneymaking"},
        enabledByDefault = false
)
@Slf4j
public class HouseTabPlugin extends Plugin {

    @Inject
    private HouseTabConfig config;

    @Provides
    HouseTabConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HouseTabConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HouseTabOverlay houseTabOverlay;

    private final HouseTabScript houseTabScript = new HouseTabScript(HOUSETABS_CONFIG.FRIENDS_HOUSE,
            new String[]{"xGrace", "workless", "Lego Batman", "Batman 321", "Batman Chest"});

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(houseTabOverlay);
        }
        houseTabScript.run(config);
    }

    protected void shutDown() {
        houseTabScript.shutdown();
        overlayManager.remove(houseTabOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().contains("That player is offline")) {
            Microbot.showMessage("Player is offline.");
            houseTabScript.shutdown();
        }
    }
}
