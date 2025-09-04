package net.runelite.client.plugins.microbot.bee.chaosaltar;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.Bee + "Chaos Altar",
        description = "Automates bone offering at the Chaos Altar",
        tags = {"prayer", "bones", "altar"},
        enabledByDefault = false
)
@Slf4j
public class ChaosAltarPlugin extends Plugin implements SchedulablePlugin {
    @Inject
    private ChaosAltarScript chaosAltarScript;
    @Inject
    private ChaosAltarConfig config;
    @Provides
    ChaosAltarConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ChaosAltarConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    ChaosAltarOverlay chaosAltarOverlay;

    LogicalCondition stopCondition = new AndCondition();
    LockCondition lockCondition = new LockCondition("ChaosAlterPlugin",false, true);


    @Override
    protected void startUp() {
        if (overlayManager != null) {
            overlayManager.add(chaosAltarOverlay);
        }
        chaosAltarScript.run(config, this);
    }

    protected void shutDown() {
        chaosAltarScript.shutdown();
        if (lockCondition != null && lockCondition.isLocked()) {
            lockCondition.unlock();
        }
        overlayManager.remove(chaosAltarOverlay);
    }

    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
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

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String msg = chatMessage.getMessage();
        //need to add the chat message we get when we try to attack an NPC with an empty staff.

        if (msg.contains("Oh dear, you are dead!")) {
            ChaosAltarScript.didWeDie = true;
        }


    }

    @Override
    public ConfigDescriptor getConfigDescriptor() {
        if (Microbot.getConfigManager() == null) {
            return null;
        }
        ChaosAltarConfig conf = Microbot.getConfigManager().getConfig(ChaosAltarConfig.class);
        return Microbot.getConfigManager().getConfigDescriptor(conf);
    }

}
