package net.runelite.client.plugins.microbot.rsagent;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("rsagent") // Changed group name to be more specific
public interface RsAgentConfig extends Config {

    @ConfigSection(
            name = "General",
            description = "General settings",
            position = 0,
            closedByDefault = false
    )
    String generalSection = "general";

    String llmApiKey = "llmApiKey";
    @ConfigItem(
            keyName = llmApiKey,
            name = "OpenAI API key",
            description = "Enter your API key",
            position = 1,
            section = generalSection,
            secret = true // Mark as secret
    )
    String llmApiKey();

    String goal = "goal";
    @ConfigItem(
            keyName = goal,
            name = "Agent Goal",
            description = "Enter the task for the agent to perform (e.g., 'Complete Cook's Assistant'). The agent will start when the plugin is enabled if this is set.",
            position = 2,
            section = generalSection
    )
    default String goal() {
        return ""; // Default to empty string
    }
}
