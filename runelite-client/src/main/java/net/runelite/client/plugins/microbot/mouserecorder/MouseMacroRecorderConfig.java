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
		description = "Your input is uploaded to your own Microbot account and only you can read it. Recordings from older versions of this plugin stay in .runelite/mouse-macro-recordings.",
		position = 0
	)
	String recordingSection = "recording";

	@ConfigItem(
		keyName = "uploadEnabled",
		name = "Upload my input",
		description = "Uploads your mouse, game keys, position, camera, menus, XP and what you are doing to your Microbot account. Letters you type are never uploaded. Pauses while a script runs, while you type, on the login screen, the bank PIN and when the window is not focused. Needs this client linked: Microbot Cloud at the bottom of the Microbot sidebar, then Link to Microbot account.",
		position = 0,
		section = recordingSection
	)
	default boolean uploadEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "recordMouseMovement",
		name = "Record mouse movement",
		description = "Include mouse moves, not only clicks and scrolls",
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
		description = "Minimum time between recorded mouse moves. Lower gives more detail but reaches the daily upload limit sooner; 0 records every move.",
		position = 2,
		section = recordingSection
	)
	@Range(min = 0, max = 500)
	default int movementSampleIntervalMs()
	{
		return 10;
	}
}
