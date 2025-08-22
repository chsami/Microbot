package net.runelite.client.plugins.microbot.GlassBlowing;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.crafting.enums.Glass;

@ConfigInformation("Start near a bank with a glass blowing pipe and an inventory full of molten glass")


@ConfigGroup("Glass")
public interface GlassBlowingConfig extends Config {

    @ConfigItem(
            keyName = "product",
            name = "Glass Product",
            description = "Which item are we crafting?",
            position = 0
    )
    default Glass glassProduct() {
        return Glass.UNPOWERED_ORB;
    }
}
