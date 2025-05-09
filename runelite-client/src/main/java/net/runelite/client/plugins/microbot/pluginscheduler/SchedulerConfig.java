package net.runelite.client.plugins.microbot.pluginscheduler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.Microbot;

@ConfigGroup("PluginScheduler")
public interface SchedulerConfig extends Config {
    final static String CONFIG_GROUP = "PluginScheduler";
    
    @ConfigSection(
            name = "Control",
            description = "Control settings for the plugin scheduler, force stop, etc.",
            position = 10,
            closedByDefault = true
    )
    String controlSection = "Control Settings";
    @ConfigSection(
            name = "Conditions",
            description = "Conditions settings for the plugin scheduler, enforce conditions, etc.",            
            position = 100,
            closedByDefault = true
    )
    String conditionsSection = "Conditions Settings";
    @ConfigSection(
            name = "Log-In",
            description = "Log-In settings for the plugin scheduler, auto log in, etc.",            
            position = 200,
            closedByDefault = true
    )
    String loginLogOutSection = "Log-In\\Out Settings";

    @ConfigSection(
            name = "Break",
            description = "Break settings for the plugin scheduler, auto-enable break handler, etc.",            
            position = 300,
            closedByDefault = true
    )
    String breakSection = "Break Settings";
    // hidden settings for saving config automatically via runelite config menager
    @ConfigItem(
            keyName = "scheduledPlugins",
            name = "Scheduled Plugins",
            description = "JSON representation of scheduled scripts",
            hidden = true
    )

    default String scheduledPlugins() {
        return "";
    }
   
    void setScheduledPlugins(String json);
    /// Control settings
    @Range(
		min = 60,
        max = 3600
	)  
    @ConfigItem(
        keyName = "softStopRetrySeconds",
        name = "Soft Stop Retry (seconds)",
        description = "Time in seconds between soft stop retry attempts",
        position = 1,
        section = controlSection
    )
    default int softStopRetrySeconds() {
        return 60;
    }
    @ConfigItem(
        keyName = "enableHardStop",
        name = "Enable Hard Stop",
        description = "Enable hard stop after soft stop attempts",
        position = 2,
        section = controlSection
    )
    default boolean enableHardStop() {
        return false;
    }
    void setEnableHardStop(boolean enable);
    @ConfigItem(
        keyName = "hardStopTimeoutSeconds",
        name = "Hard Stop Timeout (seconds)",
        description = "Time in seconds before forcing a hard stop after initial soft stop attempt",
        position = 3,
        section = controlSection
    )
    default int hardStopTimeoutSeconds() {
        return 0;
    }
    default void setHardStopTimeoutSeconds(int seconds){
        if  (Microbot.getConfigManager() == null){
            return;
        }
        Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, "hardStopTimeoutSeconds", seconds);
    }

    @ConfigItem(
        keyName = "minManualStartThresholdMinutes",
        name = "Manual Start Threshold (minutes)",
        description = "Minimum time (in minutes) before a scheduled plugin can be manually started",
        position = 4,
        section = controlSection
    )
    default int minManualStartThresholdMinutes() {
        return 5;
    }
    default void setMinManualStartThresholdMinutes(int minutes){
        if  (Microbot.getConfigManager() == null){
            return;
        }
        Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, "minManualStartThresholdMinutes", minutes);
    }
    @ConfigItem(
        keyName = "prioritizeNonDefaultPlugins",
        name = "Prioritize Non-Default Plugins",
        description = "Stop automatically running default plugins when a non-default plugin is due within the grace period", 
        position = 5,
        section = controlSection
    )
    default boolean prioritizeNonDefaultPlugins() {
        return true;
    }
    void setPrioritizeNonDefaultPlugins(boolean prioritizeNonDefaultPlugins);

    @ConfigItem(
        keyName = "nonDefaultPluginLookAheadMinutes",
        name = "Non-Default Plugin Look-Ahead (minutes)",
        description = "Time window in minutes to look ahead for non-default plugins when deciding to stop a default plugin",
        position = 6,
        section = controlSection
    )
    default int nonDefaultPluginLookAheadMinutes() {
        return 1;
    }
    @ConfigItem(
        keyName ="notifcationsOn",
        name = "Notifications On",
        description = "Enable notifications for plugin scheduler events",
        position = 7,
        section = controlSection
    )
    default boolean notificationsOn() {
        return false;
    }
  


    // Conditions settings
    @ConfigItem(
        keyName = "enforceTimeBasedStopCondition",
        name = "Enforce Stop Conditions",
        description = "Prompt for confirmation before running plugins without time based stop conditions",
        position = 1,
        section = conditionsSection
    )
    default boolean enforceTimeBasedStopCondition() {
        return true;
    }
    void setEnforceStopConditions(boolean enforce);
    
    @ConfigItem(
        keyName = "dialogTimeoutSeconds",
        name = "Dialog Timeout (seconds)",
        description = "Time in seconds before the 'No Stop Conditions' dialog automatically closes",
        position = 2,
        section = conditionsSection
    )
    default int dialogTimeoutSeconds() {
        return 30;
    }
    
    @ConfigItem(
        keyName = "conditionConfigTimeoutSeconds",
        name = "Config Timeout (seconds)",
        description = "Time in seconds to wait for a user to add time based stop conditions before canceling plugin start",
        position = 3,
        section = conditionsSection
    )
    default int conditionConfigTimeoutSeconds() {
        return 60;
    }
    
    // Log-In  settings
    @ConfigItem(
        keyName = "autoLogIn",
        name = "Enable Auto Log In",
        description = "Enable auto login before starting the a plugin",        
        position = 1,
        section =  loginLogOutSection
    )
    default boolean autoLogIn() {
        return false;
    }
    void setAutoLogIn(boolean autoLogIn);
    @ConfigItem(
        keyName = "autoLogInWorld",
        name = "Auto Log In World",
        description = "World to log in to, 0 for random world",
        position = 2,
        section =  loginLogOutSection
    )
    default int autoLogInWorld() {
        return 0;
    }
    @Range(
        min = 0,
        max = 2
    )
    @ConfigItem(
        keyName = "WorldType",
        name = "World Type",
        description = "World type to log in to, 0 for F2P, 1 for P2P, 2 for any world",
        position = 3,
        section =  loginLogOutSection
    )
    default int worldType() {
        return 2;
    }
    @ConfigItem(
        keyName = "autoLogOutOnStop",
        name = "Auto Log Out on Stop",
        description = "Automatically log out when stopping the scheduler",
        position = 3,
        section =  loginLogOutSection
    )
    default boolean autoLogOutOnStop() {
        return false;
    }
    
   
    // Break settings
    @ConfigItem(
        keyName = "enableBreakHandlerAutomatically",
        name = "Auto-enable BreakHandler",
        description = "Automatically enable the BreakHandler plugin when starting a plugin",
        position = 1,
        section = breakSection
    )
    default boolean enableBreakHandlerAutomatically() {
        return true;
    }
    
 
    
    @ConfigItem(
        keyName = "breakDuringWait",
        name = "Break During Wait",
        description = "Break when waiting for the next schedule",
        position = 2,
        section = breakSection
    )
    default boolean breakDuringWait() {
        return true;
    }
    @Range(
        min = 2,
        max = 60
    )
    @ConfigItem(
        keyName = "minTimeToNextScheduleForTakingABreak",
        name = "Min Break Time (minutes)",        
        description = "The Minimum Time until to the next scheduled plugin is due to run for taking a break",
        position = 3,
        section = breakSection
    )
    default int minTimeToNextScheduleForTakingABreak() {
        return 2;
    }
    @Range(
        min = 2,
        max = 60
    )
    @ConfigItem(
        keyName = "maxBreakDuratation",
        name = "Max Break Duration (minutes)",        
        description = "When taking a break, the maximum duration of the break",
        position = 4,
        section = breakSection
    )
    default int maxBreakDuratation() {
        return 2;
    }
    @ConfigItem(
        keyName = "autoLogOutOnBreak",
        name = "Auto Log Out on Break",        
        description = "Automatically log out when taking a break",
        position = 5,
        section =  breakSection
    )
    default boolean autoLogOutOnBreak() {
        return false;
    }

   
}