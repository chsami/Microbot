package net.runelite.client.plugins.microbot.giantsfoundry;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "GiantsFoundry",
        description = "Microbot giants foundry plugin",
        tags = {"minigame", "microbot", "smithing"},
        enabledByDefault = false
)
@Slf4j
public class GiantsFoundryPlugin extends Plugin {

    @Inject
    private GiantsFoundryConfig config;

    @Provides
    GiantsFoundryConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GiantsFoundryConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GiantsFoundryOverlay giantsFoundryOverlay;

    private final GiantsFoundryScript giantsFoundryScript = new GiantsFoundryScript();

    @Override
    protected void startUp() throws AWTException {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(giantsFoundryOverlay);
        }
        giantsFoundryScript.run(config);
    }

    // previous heat varbit value, used to filter out passive heat decay.
    private int previousHeat = 0;
    private static final int VARBIT_HEAT = 13948;
    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {


        // start the heating state-machine when the varbit updates
        // if heat varbit updated and the user clicked, start the state-machine
        if (event.getVarbitId() == VARBIT_HEAT)
        {
            // ignore passive heat decay, one heat per two ticks
            if (event.getValue() - previousHeat != -1)
            {

                GiantsFoundryState.heatingCoolingState.onTick();
            }
            previousHeat = event.getValue();
        }
    }



    protected void shutDown() {
        giantsFoundryScript.shutdown();
        overlayManager.remove(giantsFoundryOverlay);
    }
}
