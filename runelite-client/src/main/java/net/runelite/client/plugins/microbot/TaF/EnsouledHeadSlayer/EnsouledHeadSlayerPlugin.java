package net.runelite.client.plugins.microbot.TaF.EnsouledHeadSlayer;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Ensouled Heads",
        description = "EnsouledHeadSlayer",
        tags = {"Ensould", "head", "slayer", "prayer", "taf", "microbot"},
        enabledByDefault = false
)
public class EnsouledHeadSlayerPlugin extends Plugin implements SchedulablePlugin {

    private Instant scriptStartTime;
    @Inject
    private EnsouledHeadSlayerConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private EnsouledHeadSlayerOverlay ensouledHeadSlayerOverlay;
    @Inject
    private EnsouledHeadSlayerScript ensouledHeadSlayerScript;
    private LogicalCondition stopCondition = new AndCondition();
    private ScheduledExecutorService scheduledExecutorService;
    private int startingXp = 0;
    private int startingLevel = 0;

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    protected String getTotalXpGained() {
        var currentXp = Microbot.getClient().getSkillExperience(Skill.PRAYER);
        return startingXp > 0 ? String.valueOf(currentXp - startingXp) : "0";
    }

    protected String getLevelGained() {
        var currentLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        return String.valueOf(startingLevel + "/" + currentLevel);
    }

    protected String getXpAnHour() {
        if (scriptStartTime == null || startingXp <= 0) {
            return "0";
        }
        var currentXp = Microbot.getClient().getSkillExperience(Skill.PRAYER);
        var xpGained = currentXp - startingXp;
        var durationInSeconds = TimeUtils.getDurationInSeconds(scriptStartTime, Instant.now());
        return durationInSeconds > 0 ? String.valueOf((xpGained * 3600L) / durationInSeconds) : "0";
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(ensouledHeadSlayerOverlay);
        }
        ensouledHeadSlayerScript.run(config);
        if (startingXp == 0) {
            startingXp = Microbot.getClient().getSkillExperience(Skill.PRAYER);
            startingLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        }
        scheduledExecutorService = Executors.newScheduledThreadPool(10);
    }

    @Override
    protected void shutDown() {
        ensouledHeadSlayerScript.shutdown();
        overlayManager.remove(ensouledHeadSlayerOverlay);
        startingXp = 0;
        startingLevel = 0;
        scriptStartTime = null;
    }

    @Provides
    EnsouledHeadSlayerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(EnsouledHeadSlayerConfig.class);
    }

    @Override
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            if (ensouledHeadSlayerScript != null) {
                Rs2Bank.walkToBank();
            }
            Microbot.stopPlugin(this);
        }
    }

    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        return this.stopCondition;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && event.getMessage().equalsIgnoreCase("The creature wouldn't have room to re-animate there.")) {
            Microbot.log("Can't summon here, walking to altar.");
            scheduledExecutorService.schedule(() ->
            {
                Rs2Walker.walkTo(ensouledHeadSlayerScript.ALTAR_LOCATION);
            }, 100, TimeUnit.MILLISECONDS);
        }
    }
}
