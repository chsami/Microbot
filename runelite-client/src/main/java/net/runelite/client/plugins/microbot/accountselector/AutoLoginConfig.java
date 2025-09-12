package net.runelite.client.plugins.microbot.accountselector;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.util.world.RegionPreference;
import net.runelite.client.plugins.microbot.util.world.WorldSelectionMode;

@ConfigGroup("AutoLoginConfig")
public interface AutoLoginConfig extends Config {
    @ConfigSection(
            name = "Credentials",
            description = "Login credentials settings",
            position = 0,
            closedByDefault = false
    )
    String credentialsSection = "credentials";

    @ConfigSection(
            name = "World Selection",
            description = "Configure world selection for auto login",
            position = 1,
            closedByDefault = false
    )
    String worldSelectionSection = "worldSelection";
    
    @ConfigSection(
            name = "Login Behavior",
            description = "Configure login retry and watchdog behavior",
            position = 2,
            closedByDefault = false
    )
    String loginBehaviorSection = "loginBehavior";

    // Credentials configuration items
    @ConfigItem(
            keyName = "username",
            name = "Username",
            description = "RuneScape username",
            section = credentialsSection,
            position = 0
    )
    default String username() {
        return "";
    }

    @ConfigItem(
            keyName = "username",
            name = "",
            description = ""
    )
    void username(String username);

    @ConfigItem(
            keyName = "password",
            name = "Password",
            description = "RuneScape password (stored securely)",
            section = credentialsSection,
            position = 1,
            secret = true
    )
    default String password() {
        return "";
    }

    @ConfigItem(
            keyName = "password",
            name = "",
            description = ""
    )
    void password(String password);

    @ConfigItem(
            keyName = "enableDirectLogin",
            name = "Enable Direct Login",
            description = "Use direct login with saved credentials instead of world selection logic",
            section = credentialsSection,
            position = 2
    )
    default boolean enableDirectLogin() {
        return false;
    }

    // World selection configuration items
    @ConfigItem(
            keyName = "worldSelectionMode",
            name = "World selection mode",
            description = "How to select worlds for auto login",
            position = 0,
            section = worldSelectionSection
    )
    default WorldSelectionMode worldSelectionMode() {
        return WorldSelectionMode.RANDOM_WORLD;
    }
    
    @ConfigItem(
            keyName = "usePreferredWorld",
            name = "Use Preferred world",
            description = "When enabled, auto login will use the legacy numeric world defined below instead of the selection mode.",
            position = 1,
            section = worldSelectionSection
    )
    default boolean usePreferredWorld() {
        return true;
    }

     // Legacy options for compatibility
    @ConfigItem(
            keyName = "World Preferred",
            name = "World (Preferred)",
            description = "Legacy world setting",
            position = 2,
            section = worldSelectionSection
    )
    default int world() { return 360; }
    
    @ConfigItem(
            keyName = "regionPreference",
            name = "Region preference",
            description = "Preferred region for world selection",
            position = 3,
            section = worldSelectionSection
    )
    default RegionPreference regionPreference() {
        return RegionPreference.ANY_REGION;
    }
    
    @ConfigItem(
            keyName = "avoidEmptyWorlds",
            name = "Avoid empty worlds",
            description = "Avoid worlds with very low population (< 50 players)",
            position = 4,
            section = worldSelectionSection
    )
    default boolean avoidEmptyWorlds() {
        return true;
    }
    
    @ConfigItem(
            keyName = "avoidOvercrowdedWorlds",
            name = "Avoid overcrowded worlds",
            description = "Avoid worlds with very high population (> 1800 players)",
            position = 5,
            section = worldSelectionSection
    )
    default boolean avoidOvercrowdedWorlds() {
        return true;
    }

   

    @ConfigItem(
            keyName = "Members Only",
            name = "Members Only",
            description = "Only select worlds that are member worlds",
            position = 6,
            section = worldSelectionSection
    )
    default boolean membersOnly() { return true; }
  
    // Login behavior options
    @ConfigItem(
            keyName = "enableLoginWatchdog",
            name = "Enable login watchdog",
            description = "Enable watchdog to handle login failures and server issues",
            position = 0,
            section = loginBehaviorSection
    )
    default boolean enableLoginWatchdog() { return true; }
    
    @ConfigItem(
            keyName = "loginWatchdogTimeout",
            name = "Login watchdog timeout",
            description = "Maximum time to attempt login before entering extended sleep (in minutes)",
            position = 1,
            section = loginBehaviorSection
    )
    @Range(min = 1, max = 50)
    default int loginWatchdogTimeout() {
        return 2;
    }
    
    @ConfigItem(
            keyName = "extendedSleepDuration",
            name = "Extended sleep duration",
            description = "How long to sleep after login watchdog timeout (in minutes)",
            position = 2,
            section = loginBehaviorSection
    )
    @Range(min = 5, max = 120)
    default int extendedSleepDuration() {
        return 30;
    }
    
    @ConfigItem(
            keyName = "maxLoginRetries",
            name = "Max login retries",
            description = "Maximum number of login attempts before triggering watchdog",
            position = 3,
            section = loginBehaviorSection
    )
    @Range(min = 1, max = 50)
    default int maxLoginRetries() {
        return 15;
    }
    
    @ConfigItem(
            keyName = "loginRetryDelay",
            name = "Login retry delay",
            description = "Delay between login attempts (in seconds)",
            position = 4,
            section = loginBehaviorSection
    )
    @Range(min = 1, max = 60)
    default int loginRetryDelay() {
        return 5;
    }
}
