package net.runelite.client.plugins.microbot.barrows;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.example.ExampleConfig;
import net.runelite.client.plugins.microbot.example.ExampleOverlay;
import net.runelite.client.plugins.microbot.example.ExampleScript;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.runecrafting.gotr.GotrScript;
import net.runelite.client.plugins.microbot.runecrafting.gotr.GotrState;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;

@PluginDescriptor(
        name = PluginDescriptor.Gage + "Barrows",
        description = "Runs barrows for you",
        tags = {"Barrows", "mm", "Money making"},
        enabledByDefault = false
)
@Slf4j
public class BarrowsPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private BarrowsConfig config;
    @Provides
    BarrowsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BarrowsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BarrowsOverlay barrowsOverlay;

    @Inject
    BarrowsScript barrowsScript;
    LogicalCondition stopCondition = new AndCondition();
    LockCondition lockCondition = new LockCondition();


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(barrowsOverlay);
        }
        Rs2Antiban.activateAntiban();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();
        Rs2Antiban.setActivity(Activity.BARROWS);
        barrowsScript.run(config, this);
        barrowsScript.outOfPoweredStaffCharges = false;
        barrowsScript.firstRun = true;
    }

    protected void shutDown() {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.deactivateAntiban();
        barrowsScript.neededRune = "unknown";
        barrowsScript.shutdown();
        overlayManager.remove(barrowsOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String msg = chatMessage.getMessage();
        //need to add the chat message we get when we try to attack an NPC with an empty staff.

        if (msg.contains("out of charges")) {
            BarrowsScript.outOfPoweredStaffCharges = true;
        }

        if (msg.contains("no charges")) {
            BarrowsScript.outOfPoweredStaffCharges = true;
        }

    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        try{
            if (event.getPlugin() == this) {
                Microbot.stopPlugin(this);
            }
        } catch (Exception e) {
            log.error("Error stopping plugin: ", e);
        }
    }

    public LockCondition getLockCondition(){
        return lockCondition;
    }

    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        return this.stopCondition;
    }

    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
