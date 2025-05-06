package net.runelite.client.plugins.microbot.maxxin.astralrc;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Maxxin + "Astral Runes",
        description = "Astral Runes",
        tags = {"astral", "rune"},
        enabledByDefault = false
)
@Slf4j
public class AstralRunesPlugin extends Plugin {
    @Inject
    private AstralRunesConfig config;

    @Provides
    AstralRunesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AstralRunesConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AstralRunesOverlay astralRunesOverlay;

    private AstralRunesScript astralRunesScript;

    @Getter
    @Setter
    private String debugText1;
    @Getter
    @Setter
    private String debugText2;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(astralRunesOverlay);
        }

        astralRunesScript = new AstralRunesScript(this);
        astralRunesScript.run(config);
    }

    protected void shutDown() {
        astralRunesScript.shutdown();
        overlayManager.remove(astralRunesOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && !Microbot.pauseAllScripts) {
            if (event.getMessage().toLowerCase().contains("you don't have enough coins.")) {
                Microbot.status = "[Shutting down] - Reason: Not enough coins.";
                Microbot.showMessage(Microbot.status);
                astralRunesScript.shutdown();
            }
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(AstralRunesConfig.configGroup)) return;
    }
}
