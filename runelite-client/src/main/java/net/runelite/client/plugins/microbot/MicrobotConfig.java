package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.Level;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(MicrobotConfig.configGroup)
public interface MicrobotConfig extends Config
{
	String configGroup = "microbot";

	@ConfigSection(
		name = "General",
		description = "The overall global settings for microbot",
		position = 0
    )
	String generalSection = "generalSection";

	@ConfigSection(
		name = "Logging",
		description = "Game chat logging configuration",
		position = 1
	)
	String loggingSection = "loggingSection";

	@ConfigItem(
		keyName = "enableGameChatLogging",
		name = "Enable Game Chat Logging",
		description = "Enable or disable logging to game chat",
		position = 0,
		section = loggingSection
	)
	default boolean enableGameChatLogging() {
		return true;
	}

	@ConfigItem(
		keyName = "gameChatLogLevel",
		name = "Log Level",
		description = "Minimum log level to show in game chat:<br>" +
				"• ERROR: Only error messages<br>" +
				"• WARN: Warning and error messages<br>" +
				"• INFO: Info, warning, and error messages<br>" +
				"• DEBUG: All messages (debug mode only)",
		position = 1,
		section = loggingSection
	)
	default GameChatLogLevel getGameChatLogLevel() {
		return GameChatLogLevel.WARN;
	}

	@ConfigItem(
		keyName = "gameChatLogPattern",
		name = "Log Pattern",
		description = "Format of log messages in game chat",
		position = 2,
		section = loggingSection
	)
	default GameChatLogPattern getGameChatLogPattern() {
		return GameChatLogPattern.SIMPLE;
	}

	@ConfigItem(
		keyName = "onlyMicrobotLogging",
		name = "Only Microbot Logs",
		description = "Show only Microbot plugin logs in game chat (filters out other RuneLite logs)",
		position = 3,
		section = loggingSection
	)
	default boolean onlyMicrobotLogging() {
		return false;
	}

	enum GameChatLogLevel {
		ERROR("Error"),
		WARN("Warning"),
		INFO("Info"),
		DEBUG("Debug");

		private final String displayName;

		GameChatLogLevel(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}

		public Level toLogbackLevel() {
			switch (this) {
				case ERROR:
					return Level.ERROR;
				case WARN:
					return Level.WARN;
				case INFO:
					return Level.INFO;
				case DEBUG:
					return Level.DEBUG;
				default:
					return Level.WARN;
			}
		}
	}

	enum GameChatLogPattern {
		SIMPLE("Simple", "[%d{HH:mm:ss}] %msg%ex{0}%n"),
		DETAILED("Detailed", "%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%ex{0}%n");

		private final String displayName;
		private final String pattern;

		GameChatLogPattern(String displayName, String pattern) {
			this.displayName = displayName;
			this.pattern = pattern;
		}

		@Override
		public String toString() {
			return displayName;
		}

		public String getPattern() {
			return pattern;
		}
	}
}
