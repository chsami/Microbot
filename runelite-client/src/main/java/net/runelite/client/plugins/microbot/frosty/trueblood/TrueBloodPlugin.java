
package net.runelite.client.plugins.microbot.frosty.trueblood;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "TRUE BLOOD",
        description = "A plugin to automate Blood Rune crafting",
        tags = {"blood", "runecrafting", "automation"},
        enabledByDefault = false
)

@Slf4j
public class TrueBloodPlugin extends Plugin {
    @Inject
    private TrueBloodConfig config;
    @Inject
    private TrueBloodOverlay overlay;
    @Inject
    private Client client;
    @Provides
    TrueBloodConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TrueBloodConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    TrueBloodScript exampleScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        exampleScript.run(config);
    }

    @Override
    protected void shutDown() {
        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }
        exampleScript.shutdown();
    }


    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick) {
        int currentXp = client.getSkillExperience(Skill.RUNECRAFT);
        int xpGained = currentXp - exampleScript.getInitialRcXp(); // Ensure script initializes XP properly

        // Debugging logs
        log.info("XP Tracking - Current XP: " + currentXp + ", Initial XP: " + exampleScript.getInitialRcXp() + ", Gained XP: " + xpGained);

        // Update overlay manually
        overlay.updateXpGained(xpGained);
    }


}