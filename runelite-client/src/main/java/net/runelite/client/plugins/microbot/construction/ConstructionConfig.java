package net.runelite.client.plugins.microbot.construction;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ConstructionConfig.GROUP)
public interface ConstructionConfig extends Config {

    String GROUP = "Construction";

    @ConfigItem(
            keyName = "Guide",
            name = "How to use",
            description = "How to use the script",
            position = 0
    )

    default String GUIDE()
    {
        return "This script only supports oak larder with a demon butler or using Phials that goes outside of the home.";
    }

    @ConfigItem(
            keyName = "usePhials",
            name = "Use Phials",
            description = "Allows you to use Phials outside to unnote your planks.",
            position = 1
    )
    default boolean usePhials()
    {
        return false;
    }


}
