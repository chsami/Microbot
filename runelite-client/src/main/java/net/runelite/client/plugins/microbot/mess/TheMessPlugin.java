package net.runelite.client.plugins.microbot.mess;

import ch.qos.logback.classic.Logger;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.flogger.Flogger;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Bolado + "The Mess",
        description = "A plugin to automate cooking in The Mess hall.",
        tags = {"cooking", "skilling", "microbot"},
        enabledByDefault = false
)

@Slf4j
public class TheMessPlugin extends Plugin {
    @Inject
    private TheMessConfig config;

    @Provides
    TheMessConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TheMessConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private static TheMessOverlay overlay;

    @Inject
    TheMessScript theMessScript;

    @Getter
    @Setter
    private Instant startTime;

    @Getter
    @Setter
    private long runningTime;

    @Getter
    @Setter
    private Integer startXp = 0;

    @Getter
    @Setter
    private Integer xpGained = 0;

    @Getter
    @Setter
    private Integer currentLevel = 0;

    @Getter
    @Setter
    private Integer xpPerHour = 0;

    @Getter
    @Setter
    private Level defaultLoggerLevel;

    private Logger logger;

    @Override
    protected void startUp() throws AWTException {
        logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        setDefaultLoggerLevel(logger.getLevel());

        if (config.debug_mode()) {
            defaultLoggerLevel = logger.getLevel();
            logger.setLevel(Level.DEBUG);
        }

        Microbot.log(org.slf4j.event.Level.INFO,"The Mess Plugin is starting up...");

        setStartTime(Instant.now());
        setStartXp(Microbot.getClient().getSkillExperience(Skill.COOKING));

        overlay = new TheMessOverlay(this);
        overlayManager.add(overlay);

        theMessScript.run(config, overlay);
    }

    protected void shutDown() {
        Microbot.log(org.slf4j.event.Level.INFO,"The Mess Plugin is shutting down...");
        if (logger != null && defaultLoggerLevel != null) {
            logger.setLevel(defaultLoggerLevel);
        }
        overlayManager.remove(overlay);
        setXpGained(0);
        setRunningTime(0);
        setStartXp(0);
        setStartTime(null);
        setCurrentLevel(0);
        setXpPerHour(0);
        theMessScript.shutdown();
    }

    public int calculateXpPerHour() {
        if (getRunningTime() <= 1 || getStartXp() <= 1) {
            return 0;
        }

        int currentXP = Microbot.getClient().getSkillExperience(Skill.COOKING);
        int xpGained = currentXP - getStartXp();
        setXpGained(xpGained);
        return (int) (xpGained * 3600 / getRunningTime());
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        setRunningTime(Instant.now().getEpochSecond() - getStartTime().getEpochSecond());
        setCurrentLevel(Microbot.getClient().getRealSkillLevel(Skill.COOKING));
        setXpPerHour(calculateXpPerHour());
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getKey() != null && event.getKey().equals("debug_mode")) {
            if (config.debug_mode()) {
                logger.setLevel(Level.DEBUG);
            } else {
                logger.setLevel(defaultLoggerLevel);
            }
        }
    }

}