package net.runelite.client.plugins.microbot.thieving;

import java.time.Duration;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
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
    @Provides
    ThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ThievingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ThievingOverlay thievingOverlay;
    @Inject
    private ThievingScript thievingScript;

    public static String version = "1.6.6";
    private int startXp = 0;
	@Getter
	private int maxCoinPouch;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(thievingOverlay);
        }
        startXp = Microbot.getClient().getSkillExperience(Skill.THIEVING);
		maxCoinPouch = determineMaxCoinPouch();
        thievingScript.run();
    }

    protected void shutDown() {
		maxCoinPouch = 0;
        thievingScript.shutdown();
        overlayManager.remove(thievingOverlay);
    }

    public int xpGained() {
        int currentXp = Microbot.getClient().getSkillExperience(Skill.THIEVING);
        return currentXp - startXp;
    }

	public int determineMaxCoinPouch() {
		if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_ELITE_COMPLETE) == 1) {
			return 140;
		} else if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_HARD_COMPLETE) == 1) {
			return 84;
		} else if (Microbot.getVarbitValue(VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1) {
			return 56;
		} else {
			return 28;
		}
	}

	public Duration getRunTime() {
		return thievingScript.getRunTime();
	}

    public String getState() {
        return String.valueOf(thievingScript.currentState);
    }
}
