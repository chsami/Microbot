package net.runelite.client.plugins.microbot.hunterno.birdhunter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("birdhunterno")
public interface BirdHunterConfig extends Config {

    @ConfigItem(
            keyName = "buryBones",
            name = "Bury Bones",
            description = "Select whether to bury bones during hunting",
            position = 2
    )
    default boolean buryBones() {
        return true;
    }

    @ConfigItem(
            keyName = "keepItemNames",
            name = "Keep Item Names",
            description = "Comma-separated list of item names that should not be dropped",
            position = 3
    )
    default String keepItemNames() {
        return "Bird snare";
    }

    @ConfigItem(
            keyName = "huntingRadiusValue",
            name = "Hunting radius",
            description = "The radius in which the player will set traps and hunt birds. Indicated by yellow borders. " +
                    "The center of the area is where the plugin is started",
            position = 4
    )
    default int huntingRadiusValue() {
        return 2;
    }

    @ConfigItem(
            keyName = "startScript",
            name = "Start/Stop Script",
            description = "Toggle to start or stop the Bird Hunter script",
            position = 5
    )
    default boolean startScript() {
        return false;
    }
}
