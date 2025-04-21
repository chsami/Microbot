package net.runelite.client.plugins.microbot.nmz;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.combat.PrayerPotionScript;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Nmz",
        description = "Microbot NMZ",
        tags = {"nmz", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class NmzPlugin extends Plugin implements SchedulablePlugin{
    @Inject
    private NmzConfig config;

    @Provides
    NmzConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NmzConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NmzOverlay nmzOverlay;

    @Inject
    NmzScript nmzScript;
    @Inject
    PrayerPotionScript prayerPotionScript;
    private LogicalCondition stopCondition = new AndCondition();

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(nmzOverlay);
        }
        nmzScript.run();
        if (config.togglePrayerPotions()) {
            prayerPotionScript.run(config);
        }
    }

    protected void shutDown() {
        nmzScript.shutdown();
        overlayManager.remove(nmzOverlay);
        NmzScript.setHasSurge(false);
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            nmzScript.shutdown();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                if(!nmzScript.isOutside()) {
                    Rs2GameObject.interact(26276, "Drink");
                    Global.sleepUntil(nmzScript::isOutside, 10000);
                }
                Microbot.stopPlugin(this);
                return true;
            });
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath) {
        if (config.stopAfterDeath() && actorDeath.getActor() == Microbot.getClient().getLocalPlayer()) {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Global.sleepUntil(nmzScript::isOutside, 10000);
                Microbot.stopPlugin(this);
                return true;
            });
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE) {
            if (event.getMessage().equalsIgnoreCase("you feel a surge of special attack power!")) {
                NmzScript.setHasSurge(true);
            } else if (event.getMessage().equalsIgnoreCase("your surge of special attack power has ended.")) {
                NmzScript.setHasSurge(false);
            }
        }
    }
    @Override     
    public LogicalCondition getStopCondition() {
        // Create a new stop condition        
        return this.stopCondition;
    }

}
