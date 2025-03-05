package net.runelite.client.plugins.microbot.frosty.wildthingsarehere;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.frosty.wildthingsarehere.WildScript;

import javax.inject.Inject;

@PluginDescriptor(
        name = "Artio Fighter",
        description = "A script to automatically fight Artio",
        tags = {"pvm", "boss", "combat"},
        enabledByDefault = false
)
public class WildPlugin extends Plugin {
    @Inject
    private WildScript script;

    @Override
    protected void startUp() throws Exception {
        script.start();
    }

    @Override
    protected void shutDown() throws Exception {
        script.stop();
    }
}
