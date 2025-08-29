package net.runelite.client.plugins.microbot.zerozero.bluedragons;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@PluginDescriptor(
        name = PluginDescriptor.zerozero + "Blue Dragons",
        description = "Blue dragon farmer for bones",
        tags = {"blue", "dragons", "prayer"},
        enabledByDefault = false
)
public class BlueDragonsPlugin extends Plugin {
    static final String CONFIG = "bluedragons";

    @Inject
    private BlueDragonsScript script;

    @Inject
    private BlueDragonsConfig config;
    
    @Inject
    private BlueDragonsOverlay overlay;
    
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private Client client;

    @Override
    protected void startUp() {
        overlay.setScript(script);
        
        overlay.setConfig(config);
        
        overlayManager.add(overlay);

        Rs2Antiban.activateAntiban();

        Rs2Antiban.resetAntibanSettings();

        Rs2Antiban.antibanSetupTemplates.applyCombatSetup();

        Rs2Antiban.setActivity(Activity.KILLING_BLUE_DRAGONS);
        
        if (config.startPlugin()) {
            script.run(config);
        }
    }

    @Override
    protected void shutDown() {
        script.logOnceToChat("Stopping Blue Dragons plugin...", false, config);
        overlayManager.remove(overlay);
        script.shutdown();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("bluedragons")) return;

        switch (event.getKey()) {
            case "startPlugin":
                if (config.startPlugin()) {
                    script.logOnceToChat("Starting Blue Dragon plugin...", false, config);
                    script.run(config);
                } else {
                    script.logOnceToChat("Stopping Blue Dragon plugin!", false, config);
                    script.shutdown();
                }
                break;

            case "lootDragonhide":
            case "foodType":
            case "foodAmount":
            case "eatAtHealthPercent":
            case "lootEnsouledHead":
            case "debugLogs":
                script.logOnceToChat("Configuration changed. Updating script settings.", true, config);
                if (config.startPlugin()) {
                    script.updateConfig(config);
                }
                break;

            default:
                break;
        }
    }


    @Provides
    BlueDragonsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BlueDragonsConfig.class);
    }
}
