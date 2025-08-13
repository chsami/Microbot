package net.runelite.client.plugins.microbot.bga.autochompykiller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = "[bga] Auto Chompy Killer",
        description = "Automated chompy bird hunting plugin...",
        tags = {"chompy", "combat"},
        enabledByDefault = false
)
@Slf4j
public class AutoChompyKillerPlugin extends Plugin {
    @Inject
    private AutoChompyKillerConfig config;

    @Provides
    AutoChompyKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoChompyKillerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoChompyKillerOverlay autoChompyKillerOverlay;

    @Inject
    AutoChompyKillerScript autoChompyKillerScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(autoChompyKillerOverlay);
        }
        autoChompyKillerScript.startup();
        autoChompyKillerScript.run(config);
    }

    protected void shutDown() {
        autoChompyKillerScript.shutdown();
        overlayManager.remove(autoChompyKillerOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (!((chatMessage.getType() == ChatMessageType.SPAM) || (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) || (chatMessage.getType() == ChatMessageType.ENGINE))) {
            return;
        }

        String message = chatMessage.getMessage().toLowerCase();
        if (message.contains("you scratch a notch on your bow for the chompy bird kill")) {
            autoChompyKillerScript.incrementChompyKills();
        }
        if (message.contains("This is not your Chompy Bird to shoot".toLowerCase())) {
            autoChompyKillerScript.handleNotMyChompy();
        }
        if (message.contains("can't reach that")) {
            autoChompyKillerScript.handleCantReachBubbles();
        }
        if (message.contains("your bow isn't powerful enough for those arrows")) {
            autoChompyKillerScript.handleBowNotPowerfulEnough();
        }
        if (config.stopOnChompyChickPet() && (message.contains("you have a funny feeling like you're being followed") || 
            message.contains("you feel something weird sneaking into your backpack"))) {
            autoChompyKillerScript.handlePetReceived(config.logoutOnCompletion());
        }
    }

    int ticks = 10;

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }
    }
}