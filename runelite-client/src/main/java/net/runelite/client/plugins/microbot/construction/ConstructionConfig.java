package net.runelite.client.plugins.microbot.construction;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ConstructionConfig.GROUP)
@ConfigInformation(ConstructionConfig.INFORMATION)
public interface ConstructionConfig extends Config {

    String GROUP = "Construction";
    String INFORMATION = "" +
            "Requirements: " +
            "<br />" +
            "1. Hammer" +
            "<br />" +
            "2. Saw" +
            "<br />" +
            "3. Demon butler OR GP in Inventory for Phials" +
            "<br />" +
            "4. 24 empty spaces OR 8/16/24 unnoted oak planks" +
            "<br />" +
            "<br />" +
            "Recommended:" +
            "<br />" +
            "1. Servant's money bag" +
            "<br />" +
            "<br />" +
            " Starting: Start in your house in <b>Edit/Build</b> mode near your oak larder space and with your butler nearby. " +
            "<b>Make sure you have your butler already setup to withdraw 24 oak planks.</b>" +
            "<br /> " +
            "<br />" +
            "Config by offline";
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
