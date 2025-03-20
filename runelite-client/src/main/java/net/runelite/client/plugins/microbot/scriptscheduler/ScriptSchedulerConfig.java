package net.runelite.client.plugins.microbot.scriptscheduler;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("ScriptScheduler")
public interface ScriptSchedulerConfig extends Config {
//    @ConfigItem(
//            keyName = "defaultScript",
//            name = "Default Script",
//            description = "Script to run when no scheduled script is active",
//            position = 1
//    )
//    default String defaultScript() {
//        return "";
//    }

    @ConfigItem(
            keyName = "scheduledScripts",
            name = "Scheduled Scripts",
            description = "JSON representation of scheduled scripts",
            hidden = true
    )
    default String scheduledScripts() {
        return "";
    }

    @ConfigItem(
            keyName = "scheduledScripts",
            name = "Scheduled Scripts",
            description = "JSON representation of scheduled scripts",
            hidden = true
    )
    void setScheduledScripts(String json);
}