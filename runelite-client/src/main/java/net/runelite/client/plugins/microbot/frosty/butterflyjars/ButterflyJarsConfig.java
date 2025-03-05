package net.runelite.client.plugins.microbot.frosty.butterflyjars;

import net.runelite.client.config.*;

@ConfigGroup("Frosty")
public interface ButterflyJarsConfig extends Config {

    @ConfigItem(
            keyName = "guide",
            name = "How to use",
            description = "How to use this plugin",
            position = 0
    )
    default String GUIDE() {
        return "Must have comepleted 'Spritis of the Elid for Nardah\n" +
                "Must have minimum 10k in bank\n";
    }

    @ConfigItem(
            keyName = "purpose",
            name = "What does it do",
            description = "Butterfly jars in Nardah",
            position = 1
    )
    default String PURPOSE() {
        return "This plugin buys, banks and world hops to buy Butterfly jars in Nardah";
    }
}
