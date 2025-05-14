package net.runelite.client.plugins.microbot.TaF.TearsOfGuthix;

import net.runelite.client.config.*;

@ConfigInformation(
        "This plugin automates the Tears of Guthix minigame."
                + "</html>")
@ConfigGroup("TearsOfGuthix")
public interface TearsOfGuthixConfig extends Config {
    @ConfigSection(
            name = "Settings",
            description = "Settings",
            position = 1
    )
    String settings = "settings";

    @ConfigItem(
            keyName = "queryForOptimalWorld",
            name = "Fetch optimal world from API",
            description = "If enabled, an external API will be asked for the best tears of guthix world, otherwise current world will be used",
            section = settings,
            position = 0
    )
    default boolean queryForOptimalWorld() {
        return false;
    }

}
