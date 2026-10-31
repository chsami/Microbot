package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.Level;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.bans.BanReportMode;
import net.runelite.client.plugins.microbot.util.security.HopBudget;

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

	String keyDisableLevelUpInterface = "disableLevelUpInterface";
	@ConfigItem(
		keyName = keyDisableLevelUpInterface,
		name = "Disable level-up interface",
		description = "Automatically close the level-up interface when it appears",
		position = 0,
		section = generalSection
	)
	default boolean disableLevelUpInterface()
	{
		return true;
	}

	String keyDisableWorldSwitcherConfirmation = "disableWorldSwitcherConfirmation";
	@ConfigItem(
		keyName = keyDisableWorldSwitcherConfirmation,
		name = "Disable world switcher confirmation",
		description = "Automatically disable the world switcher confirmation prompt",
		position = 1,
		section = generalSection
	)
	default boolean disableWorldSwitcherConfirmation()
	{
		return true;
	}

	String keyHideRoofs = "hideRoofs";
	@ConfigItem(
		keyName = keyHideRoofs,
		name = "Hide roofs",
		description = "Automatically enable the hide roofs display setting",
		position = 2,
		section = generalSection
	)
	default boolean hideRoofs()
	{
		return true;
	}

	String keySpoofDeviceId = "spoofDeviceId";
	@ConfigItem(
		keyName = keySpoofDeviceId,
		name = "Spoof device ID",
		description = "Replace the hardware device ID and random.dat seed sent to the game server at login with random values cached per account",
		position = 3,
		section = generalSection
	)
	default boolean spoofDeviceId()
	{
		return true;
	}

	@ConfigSection(
		name = "Movement",
		description = "Movement and stamina settings. Plugins may override these, but any changes will be reflected here.",
		position = 1,
		closedByDefault = true
	)
	String movementSection = "movementSection";

	String keyEnableAutoRunOn = "enableAutoRunOn";
	@ConfigItem(
		keyName = keyEnableAutoRunOn,
		name = "Enable auto run",
		description = "Automatically toggle run on when you have run energy",
		position = 0,
		section = movementSection
	)
	default boolean enableAutoRunOn()
	{
		return true;
	}

	String keyUseStaminaPotsIfNeeded = "useStaminaPotsIfNeeded";
	@ConfigItem(
		keyName = keyUseStaminaPotsIfNeeded,
		name = "Use stamina potions",
		description = "Automatically use stamina potions from inventory when run energy is low and the player is moving",
		position = 1,
		section = movementSection
	)
	default boolean useStaminaPotsIfNeeded()
	{
		return true;
	}

	@ConfigSection(
		name = "Logging",
		description = "Game chat logging configuration",
		position = 2
	)
	String loggingSection = "loggingSection";
	@ConfigSection(
			name = "Caching",
			description = "Caching ingame data",
			position = 3
	)
	String cacheSection = "cacheSection";

	String keyEnableGameChatLogging = "enableGameChatLogging";
	@ConfigItem(
		keyName = keyEnableGameChatLogging,
		name = "Enable Game Chat Logging",
		description = "Enable or disable logging to game chat",
		position = 0,
		section = loggingSection
	)
	default boolean enableGameChatLogging() {
		return true;
	}

	String keyGameChatLogLevel = "gameChatLogLevel";
	@ConfigItem(
		keyName = keyGameChatLogLevel,
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

	String keyGameChatLogPattern = "gameChatLogPattern";
	@ConfigItem(
		keyName = keyGameChatLogPattern,
		name = "Log Pattern",
		description = "Format of log messages in game chat",
		position = 2,
		section = loggingSection
	)
	default GameChatLogPattern getGameChatLogPattern() {
		return GameChatLogPattern.SIMPLE;
	}

	String keyOnlyMicrobotLogging = "onlyMicrobotLogging";
	@ConfigItem(
		keyName = keyOnlyMicrobotLogging,
		name = "Only Microbot Logs",
		description = "Show only Microbot plugin logs in game chat (filters out other RuneLite logs)",
		position = 3,
		section = loggingSection
	)
	default boolean onlyMicrobotLogging() {
		return false;
	}

	String keyEnableMenuEntryLogging = "enableMenuEntryLogging";

	@ConfigItem(
			keyName = keyEnableMenuEntryLogging,
			name = "Enable Menu Entry Logging",
			description = "Enable or disable logging menu entry clicked",
			position = 4,
			section = loggingSection
	)
	default boolean enableMenuEntryLogging() {
		return false;
	}

	@AllArgsConstructor
	enum GameChatLogLevel {
		ERROR("Error", Level.ERROR),
		WARN("Warning", Level.WARN),
		INFO("Info", Level.INFO),
		DEBUG("Debug", Level.DEBUG);

		private final String displayName;
		@Getter
		private final Level level;

		@Override
		public String toString() {
			return displayName;
		}
	}

	enum GameChatLogPattern {
		SIMPLE("Simple", "[%d{HH:mm:ss}] %msg%ex{0}%n"),
		DETAILED("Detailed", "%d{HH:mm:ss} [%thread] %-5level %logger{36} - %msg%ex{0}%n");

		private final String displayName;
		@Getter
        private final String pattern;

		GameChatLogPattern(String displayName, String pattern) {
			this.displayName = displayName;
			this.pattern = pattern;
		}

		@Override
		public String toString() {
			return displayName;
		}
    }

	@ConfigItem(
		keyName = "showCacheInfo",
		name = "Show Cache Information",
		description = "Display cache statistics and management button in overlays",
		position = 2,
		section = generalSection
	)
	default boolean showCacheInfo() {
		return false;
	}

	String keyDisableTelemetry = "disableTelemetry";
	@ConfigItem(
		keyName = keyDisableTelemetry,
		name = "Disable telemetry",
		description = "Stop outbound calls to microbot.cloud (update check, random-fact splash, session ping, script error reports, " +
				"plugin runtime totals, ban reports). " +
				"Equivalent to launching with -Dmicrobot.disableTelemetry=true.",
		position = 6,
		section = generalSection
	)
	default boolean disableTelemetry() {
		return false;
	}

	String keyBanReports = "banReports";
	@ConfigItem(
		keyName = keyBanReports,
		name = "Ban reports",
		description = "When an account Microbot botted on shows the \"account disabled\" login screen, share an anonymous report: " +
				"scripts run in the last 30 days and for how long, membership, rounded total level, longest session and whether breaks were on. " +
				"Never your account name, email, IP or location, just a scrambled ID so the same ban isn't counted twice. " +
				"Ask me and Always send also share hourly script runtime totals (no account info) so ban rates per script can be worked out; " +
				"Never send stops both. Always send sends the ban type as unknown, so locked accounts may be counted. " +
				"Ignored while Disable telemetry is on.",
		position = 5,
		section = generalSection
	)
	default BanReportMode banReports() {
		return BanReportMode.ASK;
	}

	String keyDisableInputYielding = "disableInputYielding";
	@ConfigItem(
		keyName = keyDisableInputYielding,
		name = "Disable input yielding",
		description = "Stop scripts pausing when you use the real mouse or keyboard on the game canvas. " +
				"Enabled by default; turn this off to opt into human-input yielding.",
		position = 7,
		section = generalSection
	)
	default boolean disableInputYielding()
	{
		return true;
	}

	String keyInputMotionThresholdPx = "inputMotionThresholdPx";
	@ConfigItem(
		keyName = keyInputMotionThresholdPx,
		name = "Input yield: motion threshold",
		description = "How far the real mouse must move, in pixels from where the bot last put it, before scripts yield. " +
				"Lower reacts sooner and risks yielding on an accidental nudge.",
		position = 8,
		section = generalSection
	)
	default int inputMotionThresholdPx()
	{
		return 10;
	}

	String keyInputIdleResumeMs = "inputIdleResumeMs";
	@ConfigItem(
		keyName = keyInputIdleResumeMs,
		name = "Input yield: resume delay (ms)",
		description = "How long the real mouse and keyboard must stay quiet before scripts resume. " +
				"Never resumes while you are holding a button or key, whatever this is set to.",
		position = 9,
		section = generalSection
	)
	default int inputIdleResumeMs()
	{
		return 1800;
	}

	String keyAlertLinkToken = "alertLinkToken";
	@ConfigItem(
		keyName = keyAlertLinkToken,
		name = "Alert link token",
		description = "Filled in by Link to Microbot account in the Microbot Cloud panel",
		secret = true,
		hidden = true
	)
	default String alertLinkToken()
	{
		return "";
	}

	String keyAllowDashboardScreenshots = "allowDashboardScreenshots";
	@ConfigItem(
		keyName = keyAllowDashboardScreenshots,
		name = "Allow dashboard screenshots",
		description = "Let the Microbot dashboard take a screenshot of the game when this client is linked to your account",
		position = 10,
		section = generalSection
	)
	default boolean allowDashboardScreenshots()
	{
		return true;
	}

	String keyAllowDashboardControl = "allowDashboardControl";
	@ConfigItem(
		keyName = keyAllowDashboardControl,
		name = "Allow dashboard control",
		description = "Let the Microbot dashboard stop your scripts or log this client out when it is linked to your account",
		position = 11,
		section = generalSection
	)
	default boolean allowDashboardControl()
	{
		return true;
	}

	@ConfigSection(
		name = "World hop limit",
		description = "Pace world hops so the per-IP login limit (\"Too many login attempts\") is never reached",
		position = 4
	)
	String hopLimitSection = "hopLimitSection";

	String keyEnableHopLimit = "enableHopLimit";
	@ConfigItem(
		keyName = keyEnableHopLimit,
		name = "Pace world hops",
		description = "Wait before a scripted world hop when the estimated hop allowance runs low, instead of getting locked out. " +
				"Shared by every client on the same IP or proxy.",
		position = 0,
		section = hopLimitSection
	)
	default boolean enableHopLimit()
	{
		return true;
	}

	String keyHopLimitCapacity = "hopLimitCapacity";
	@Range(min = 1, max = 1000)
	@ConfigItem(
		keyName = keyHopLimitCapacity,
		name = "Hop allowance",
		description = "Estimated number of hops and logins Jagex allows before locking the IP out",
		position = 1,
		section = hopLimitSection
	)
	default int hopLimitCapacity()
	{
		return HopBudget.DEFAULT_CAPACITY;
	}

	String keyHopLimitRefillPerHour = "hopLimitRefillPerHour";
	@Range(min = 1, max = 1000)
	@ConfigItem(
		keyName = keyHopLimitRefillPerHour,
		name = "Hops regained per hour",
		description = "Estimated number of hops the allowance regains every hour",
		position = 2,
		section = hopLimitSection
	)
	default int hopLimitRefillPerHour()
	{
		return HopBudget.DEFAULT_REFILL_PER_HOUR;
	}

	String keyHopLimitReserve = "hopLimitReserve";
	@Range(min = 0, max = 200)
	@ConfigItem(
		keyName = keyHopLimitReserve,
		name = "Reserve for logins",
		description = "Hops never spent on world hopping, kept for logging back in after breaks or disconnects",
		position = 3,
		section = hopLimitSection
	)
	default int hopLimitReserve()
	{
		return HopBudget.DEFAULT_RESERVE;
	}
}
