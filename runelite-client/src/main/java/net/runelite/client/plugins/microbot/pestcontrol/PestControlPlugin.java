package net.runelite.client.plugins.microbot.pestcontrol;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.pestcontrol.Portal;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.runelite.client.plugins.microbot.pestcontrol.PestControlScript.portals;



@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Pest Control",
        description = "Microbot Pest Control plugin, this only supports the combat 100+ boat. Start at the front of the boat",
        tags = {"pest control", "microbot", "minigames"},
        enabledByDefault = false
)
@Slf4j
public class PestControlPlugin extends Plugin implements SchedulablePlugin {

    @Inject
    PestControlScript pestControlScript;

    @Inject
    private PestControlConfig config;

    @Provides
    PestControlConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PestControlConfig.class);
    }



    @Override
    public LogicalCondition getStartCondition() {
        // Create conditions that determine when your plugin can start
        // Return null if the plugin can start anytime
        return null;
    }

    public LockCondition lockCondition = new LockCondition("Void insists you finish this round");


    @Override
    public LogicalCondition getStopCondition() {
        // Create a new stop condition
        OrCondition orCondition = new OrCondition();
        AndCondition andCondition = new AndCondition();
        andCondition.addCondition(orCondition);   // Other stop conditions
        andCondition.addCondition(lockCondition); // Add the lock condition
        return andCondition;
    }

    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            Microbot.log("Scheduler about to turn off Pest Control");
            if(pestControlScript.isInBoat()) {
                Microbot.log("Getting off boat");
                pestControlScript.exitBoat();
                Global.sleepUntil(pestControlScript::isOutside, 5000);
            }
            Microbot.log("Reached outside");
            pestControlScript.shutdown();
            Microbot.stopPlugin(this);
        }
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PestControlOverlay pestControlOverlay;

    private final Pattern SHIELD_DROP = Pattern.compile("The ([a-z]+), [^ ]+ portal shield has dropped!", Pattern.CASE_INSENSITIVE);


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(pestControlOverlay);
        }
        pestControlScript.initialise = true;
        pestControlScript.run(config);
    }

    protected void shutDown() {
        pestControlScript.shutdown();
        overlayManager.remove(pestControlOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            Matcher matcher = SHIELD_DROP.matcher(chatMessage.getMessage());
            if (matcher.lookingAt()) {
                switch (matcher.group(1)) {
                    case "purple":
                        portals.stream().filter(x -> x == Portal.PURPLE).findFirst().get().setHasShield(false);
                        break;
                    case "blue":
                        portals.stream().filter(x -> x == Portal.BLUE).findFirst().get().setHasShield(false);
                        break;
                    case "red":
                        portals.stream().filter(x -> x == Portal.RED).findFirst().get().setHasShield(false);
                        break;
                    case "yellow":
                        portals.stream().filter(x -> x == Portal.YELLOW).findFirst().get().setHasShield(false);
                        break;
                }
            }
        }
    }
}
