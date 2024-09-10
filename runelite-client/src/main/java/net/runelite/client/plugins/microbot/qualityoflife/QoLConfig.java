package net.runelite.client.plugins.microbot.qualityoflife;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("QoL")
public interface QoLConfig extends Config {
    // boolean to render Max Hit Overlay
    @ConfigItem(
            keyName = "renderMaxHitOverlay",
            name = "Render Max Hit Overlay",
            description = "Render Max Hit Overlay",
            position = 0
    )
    default boolean renderMaxHitOverlay() {
        return true;
    }

    // boolean to use Withdraw-Last from bank
    @ConfigItem(
            keyName = "useDoLastBank",
            name = "Use Do-Last Bank",
            description = "Use Do-Last Bank",
            position = 1
    )
    default boolean useDoLastBank() {
        return true;
    }

    // boolean to use DoLast action on furnace
    @ConfigItem(
            keyName = "useDoLastFurnace",
            name = "Use Do-Last Furnace",
            description = "Use Do-Last Furnace",
            position = 2
    )
    default boolean useDoLastFurnace() {
        return true;
    }

    // boolean to use Dialogue auto continue
    @ConfigItem(
            keyName = "useDialogueAutoContinue",
            name = "Dialogue Auto Continue",
            description = "Use Dialogue Auto Continue",
            position = 3
    )
    default boolean useDialogueAutoContinue() {
        return true;
    }
}
