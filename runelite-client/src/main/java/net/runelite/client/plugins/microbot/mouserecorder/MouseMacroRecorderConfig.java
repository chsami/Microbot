package net.runelite.client.plugins.microbot.mouserecorder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(MouseMacroRecorderConfig.CONFIG_GROUP)
public interface MouseMacroRecorderConfig extends Config
{
	String CONFIG_GROUP = "mouseMacroRecorder";

	@ConfigSection(
		name = "Recording",
		description = "Your input is uploaded to your Microbot account only; nothing is saved on this computer",
		position = 0
	)
	String recordingSection = "recording";

	@ConfigItem(
		keyName = "recordingEnabled",
		name = "Recording enabled",
		description = "Record your own mouse and game-key input and upload it to your linked Microbot account. Pauses while a script runs, on the login screen, the bank PIN and text prompts. Needs the client linked (Microbot alerts).",
		position = 0,
		section = recordingSection
	)
	default boolean recordingEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "recordMouseMovement",
		name = "Record mouse movement",
		description = "Capture mouse move/drag events",
		position = 1,
		section = recordingSection
	)
	default boolean recordMouseMovement()
	{
		return true;
	}

	@ConfigItem(
		keyName = "movementSampleIntervalMs",
		name = "Movement sample interval (ms)",
		description = "Minimum time between recorded mouse moves; 0 records every move",
		position = 2,
		section = recordingSection
	)
	@Range(min = 0, max = 500)
	default int movementSampleIntervalMs()
	{
		return 0;
	}
}
