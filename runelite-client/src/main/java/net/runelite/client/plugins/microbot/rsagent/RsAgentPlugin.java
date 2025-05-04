package net.runelite.client.plugins.microbot.rsagent;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.rsagent.agent.Agent;
import net.runelite.client.plugins.microbot.rsagent.agent.RsAgentTools;
import net.runelite.client.plugins.microbot.tutorialisland.TutorialIslandConfig;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Example",
        description = "Microbot example plugin",
        tags = {"example", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class RsAgentPlugin extends Plugin {
    @Inject
    private RsAgentConfig config;
    @Provides
    RsAgentConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RsAgentConfig.class);
    }
    @Getter
    private Agent agent;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private RsAgentOverlay rsAgentOverlay;

    @Inject
    RsAgentScript rsAgentScript;

    Thread agentThread;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(rsAgentOverlay);
        }
        var r = RsAgentTools.handleDialogue();

//        agent = new Agent(config.llmApiKey());
//        agentThread = new Thread(()->
//        agent.run("Complete the restless ghost quest"));
//        agentThread.start();
        rsAgentScript.run(config);
    }

    protected void shutDown() {
        rsAgentScript.shutdown();
        overlayManager.remove(rsAgentOverlay);
        agentThread.interrupt();
    }

    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {

        if (event.getKey().equals(RsAgentConfig.llmApiKey)) {
            agent.setApiKey(config.llmApiKey());
        }
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
