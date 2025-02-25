package net.runelite.client.plugins.microbot.frosty.bloodx;

import com.google.inject.Provides;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;

@PluginDescriptor(
        name = "Bloodx Plugin",
        description = "A plugin to automate Blood Rune crafting",
        tags = {"blood", "runecrafting", "automation"}
)
public class BloodxPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private BloodxScript bloodxScript;

    @Inject
    private BloodxConfig config;

    private boolean isRunning = false;

    @Provides
    BloodxConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BloodxConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        isRunning = true;
        bloodxScript.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        isRunning = false;

        // Ensure the script shuts down properly
        if (bloodxScript != null) {
            bloodxScript.shutdown();
        }

        Microbot.log("BloodxPlugin stopped.");
    }


    @Subscribe
    public void onGameTick(GameTick event) {
        if (isRunning) {
            bloodxScript.run(config);
        }
    }

}
