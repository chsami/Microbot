package net.runelite.client.plugins.microbot.rsnhider;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("rsnhider")
public interface RsnHiderConfig extends Config {
    @ConfigItem(
            name = "Ocultar en widgets (advertencia de retraso)",
            keyName = "hideWidgets",
            description = "Oculta tu RSN en todas partes. Podría retrasar tu juego."
    )
    default boolean hideWidgets() {
        return false;
    }

    @ConfigItem(
            name = "RSN personalizado",
            keyName = "customRsn",
            description = "Utilice un rsn personalizado en lugar de un rsn aleatorio"
    )
    default String customRsn() {
        return "";
    }
}