package net.runelite.client.plugins.microbot.frostyastrals;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
        name = "Frosty Astrals",
        description = "Crafts Astral Runes with various options like outfits, stamina potions, and essence pouches.",
        tags = {"astral", "runes", "crafting", "lunar", "runecrafting"}
)
public class FrostyAstralsPlugin extends Plugin {

    @Inject
    private FrostyAstralsConfig config;

    private FrostyAstralsScript script;

    @Provides
    FrostyAstralsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(FrostyAstralsConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        log.info("Frosty Astrals Plugin started!");
//        script = new FrostyAstralsScript(config);
//        script.start();
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Frosty Astrals Plugin stopped!");
        if (script != null) {
//            script.stop();
            script = null;
        }
    }
}
