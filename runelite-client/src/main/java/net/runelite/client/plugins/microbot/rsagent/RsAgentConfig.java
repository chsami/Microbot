package net.runelite.client.plugins.microbot.rsagent;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("example")
public interface RsAgentConfig extends Config {
    String llmApiKey = "llmApiKey";
    @ConfigItem(
            keyName = llmApiKey,
            name = "OpenAI API key",
            description = "Enter your API key",
            position = 0
    )
    String llmApiKey();
}
