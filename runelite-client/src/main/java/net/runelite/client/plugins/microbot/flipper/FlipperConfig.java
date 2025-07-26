package net.runelite.client.plugins.microbot.flipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("Flipper Config")
public interface FlipperConfig extends Config {

        @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0
    )
    default String GUIDE() {
        return "Automates the Flipping copilot plugin from the plugin hub,  \n" +
        "1.Make sure to have to have the flipping copilot plugin downloaded from the plugin hub,"+
        "and make sure to make an account, log in, and resume suggestions from flipping copilot  \n" +
        "2.have gp in inventory or bank(1m+ starting is recommended)  \n" +
        "3.preferably start at the ge or have a tele close to the ge in your inventory  \n" +
        "~made by chocken";
    }
    
}
