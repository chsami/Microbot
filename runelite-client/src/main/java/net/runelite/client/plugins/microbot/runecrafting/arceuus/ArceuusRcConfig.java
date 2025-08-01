package net.runelite.client.plugins.microbot.runecrafting.arceuus;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("arceuusRc")
@ConfigInformation("<div style='font-family: Arial, sans-serif; line-height: 1.6;'>"
        + "<h2 style='color: #4CAF50;'>S-1D Arceuus Runecrafting</h2>"
        + "<p>Start the plugin near the <strong>Dense runestone pillars</strong>.</p><br />"
        + "<p>You only need a <strong>pickaxe</strong> and a <strong>chisel</strong> in your inventory.</p> <br />"
        + "</div>")
public interface ArceuusRcConfig extends Config {
    @ConfigItem(
            keyName = "altar",
            name = "Altar",
            description = "Which altar to craft runes at",
            position = 1
    )
    default Altar getAltar() {
        return Altar.AUTO;
    }

    @ConfigItem(
            keyName = "chipEssenceFast",
            name = "Chip Essence Fast",
            description = "Should the Chisel & Essence be repeatably combined",
            position = 2
    )
    default boolean getChipEssenceFast() {
        return false;
    }

    @ConfigItem(
            keyName = "updateMessage",
            name = "Show Update Message",
            description = "Whether the update message should be shown",
            position = 3
    )
    default boolean showUpdateMessage() {
        return true;
    }
}
