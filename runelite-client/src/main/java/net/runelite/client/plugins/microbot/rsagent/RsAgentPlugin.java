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
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "RSAgent",
        description = "Microbot AI Agent using LLMs", // Updated description
        tags = {"ai", "llm", "agent", "microbot"}, // Updated tags
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

        // Initialize Agent only if API key is present
        if (config.llmApiKey() != null && !config.llmApiKey().isBlank()) {
            agent = new Agent(config.llmApiKey());

            // Start agent thread only if goal is set
            String goal = config.goal();
            if (goal != null && !goal.trim().isEmpty()) {
                log.info("Starting RSAgent with goal: {}", goal);
                agentThread = new Thread(() -> agent.run(goal));
                agentThread.start();
            } else {
                log.info("RSAgent enabled, but no goal set in config. Agent thread not started.");
            }
        } else {
            log.warn("RSAgent enabled, but OpenAI API key is missing in config. Agent cannot start.");
        }

//        rsAgentScript.run(config);
    }

    protected void shutDown() {
//         rsAgentScript.shutdown();
        overlayManager.remove(rsAgentOverlay);
        if (agentThread != null && agentThread.isAlive()) {
            agentThread.interrupt(); // Attempt to interrupt the agent thread
            log.info("RSAgent thread interrupted.");
        }
        agentThread = null; // Clear the reference
        agent = null; // Clear agent reference
        log.info("RSAgent stopped.");
    }

    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {
        if (!event.getGroup().equals("rsagent")) { // Check if the change is for our config group
            return;
        }

        if (event.getKey().equals(RsAgentConfig.llmApiKey)) {
            if (agent != null) {
                log.info("API key changed, updating agent.");
                agent.setApiKey(config.llmApiKey());
            } else if (config.llmApiKey() != null && !config.llmApiKey().isBlank()) {
                // Initialize agent if it wasn't initialized before (e.g., key added after startup)
                log.info("API key added, initializing agent.");
                agent = new Agent(config.llmApiKey());
                // Optionally, check goal again and start thread if needed, or require plugin restart
                 String goal = config.goal();
                 if (goal != null && !goal.trim().isEmpty() && (agentThread == null || !agentThread.isAlive())) {
                     log.info("Starting RSAgent thread after API key was added (Goal: {}).", goal);
                     agentThread = new Thread(() -> agent.run(goal));
                     agentThread.start();
                 }
            }
        }

        // Handle goal change - Requires stopping/restarting the agent thread
        if (event.getKey().equals(RsAgentConfig.goal)) {
            log.info("Goal changed. Restart the plugin to apply the new goal.");
            // Stop existing thread if running
            if (agentThread != null && agentThread.isAlive()) {
                agentThread.interrupt();
                log.info("Stopping existing agent thread due to goal change.");
                agentThread = null;
            }
            // Start new thread if goal is set and agent is initialized
            String newGoal = config.goal();
            if (agent != null && newGoal != null && !newGoal.trim().isEmpty()) {
                 log.info("Starting new agent thread with goal: {}", newGoal);
                 agentThread = new Thread(() -> agent.run(newGoal));
                 agentThread.start();
            } else if (agent != null) {
                 log.info("Goal cleared or empty, agent thread stopped.");
            }
        }
    }

    // Removed unused GameTick handler
    // int ticks = 10;
    // @Subscribe
    // public void onGameTick(GameTick tick)
    // {
    //     //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));
    //
    //     if (ticks > 0) {
    //         ticks--;
    //     } else {
    //         ticks = 10;
    //     }
    //
    // }
}
