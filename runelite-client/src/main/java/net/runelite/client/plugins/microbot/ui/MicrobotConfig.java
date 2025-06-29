package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(MicrobotConfig.configGroup)
public interface MicrobotConfig extends Config
{
	String configGroup = "microbot";
	String exampleConfigOption = "example";

	@ConfigSection(
		name = "General",
		description = "The overall global settings for microbot",
		position = 0,
		closedByDefault = true
	)
	String generalSection = "generalSection";

	@ConfigItem(
		keyName = exampleConfigOption,
		name = "Example Global config option",
		description = "",
		position = 0,
		section = generalSection,
		hidden = true
	)
	default boolean exampleConfigOption()
	{
		return false;
	}
}
