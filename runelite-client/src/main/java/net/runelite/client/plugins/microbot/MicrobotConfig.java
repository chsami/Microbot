package net.runelite.client.plugins.microbot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(MicrobotConfig.configGroup)
public interface MicrobotConfig extends Config
{
	String configGroup = "microbot";
	String keyLogType = "logType";

	@ConfigSection(
		name = "General",
		description = "The overall global settings for microbot",
		position = 0
    )
	String generalSection = "generalSection";

	@ConfigItem(
		keyName = keyLogType,
		name = "Game Chat Logging",
		description = "",
		position = 1,
		section = generalSection
	)
	default LogType getLogType() {
		return LogType.SIMPLE;
	}
}
