package net.runelite.client.plugins.microbot.bee.salamanders;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Salamander")
public interface SalamanderConfig extends Config {

    @ConfigItem(
            keyName = "pluginDescription",
            name = "Plugin Info & How to Use",
            description = "Instructions and plugin status",
            position = 1
    )
    default String pluginDescription() {
        return "This plugin is a WORK IN PROGRESS and needs testers!\n\n"
                + "Join the Microbot Discord -> Community Plugins -> Salamanders\n"
                + "to leave feedback and help improve the plugin.\n\n"
                + "How to Use:\n"
                + "- Have at least 5 Small Fishing Nets & 5 Ropes in your INVENTORY\n"
                + "- Keep more Nets and Ropes in your BANK\n"
                + "- You need the Hunter level to hunt minimum green salamanders to use this plugin\n"
                + "\n"
                + "Coded by the hands of a Venezuelan gold-farmer under B's instruction. Under the hood this is the "
                + "First Microbot Plugin fully developed in Spanish! 🌎";
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show Overlay",
            description = "Displays overlay with traps and status"
    )
    default boolean showOverlay() {
        return true;
    }


}