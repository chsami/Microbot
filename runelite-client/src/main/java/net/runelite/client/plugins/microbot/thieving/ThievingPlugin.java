package net.runelite.client.plugins.microbot.thieving;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import javax.inject.Inject;
import java.time.Instant;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Thieving",
        description = "Microbot thieving plugin",
        tags = {"thieving", "microbot", "skilling"},
        enabledByDefault = false
)
@Slf4j
public class ThievingPlugin extends Plugin {
    @Inject
    private ThievingConfig config;
    @Inject
    protected Client client;
    @Provides
    ThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ThievingConfig.class);
    }
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ThievingOverlay thievingOverlay;
    @Inject
    ThievingScript thievingScript;
    @Getter
    public Instant startTime;

    public static String version = "1.6.6";
    private static int startXp = 0;

    @Override
    protected void startUp() throws AWTException {
        startTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(thievingOverlay);
        }
        startXp = client.getSkillExperience(Skill.THIEVING);
        thievingScript.run(config);
    }

    protected void shutDown() {
        thievingScript.shutdown();
        overlayManager.remove(thievingOverlay);
    }

    public int xpGained() {
        int currentXp = client.getSkillExperience(Skill.THIEVING);
        return currentXp - startXp;
    }
}
