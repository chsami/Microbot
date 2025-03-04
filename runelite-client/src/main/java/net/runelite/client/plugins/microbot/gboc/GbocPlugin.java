package net.runelite.client.plugins.microbot.gboc;

import com.google.inject.Injector;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Script;
//import net.runelite.client.plugins.microbot.gboc.scripts.HerbRunScript;
import net.runelite.client.plugins.microbot.gboc.scripts.NMZScript;
import net.runelite.client.plugins.microbot.gboc.utils.ActionTask;
import net.runelite.client.plugins.microbot.gboc.utils.AutoClick;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;


@PluginDescriptor(name = "<html>[<font color=#E32636>Gb</font>] OneClick Scripts", description = "Click and go.", tags = {"microbot", "OC", "one-click"}, enabledByDefault = false)
@Slf4j
public class GbocPlugin extends Plugin {

    public static ActionTask currentAction = new ActionTask();
    @Inject
    private GbocConfig pluginConfig;

    @Provides
    GbocConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GbocConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GbocOverlay pluginOverlay;
    public Script currentScript;
    @Inject
    private Injector injector;
    @Inject
    private MouseManager mouseManager;
    private GbocMouseListener mouseListener;

    @Inject
    private AutoClick autoClick;


    @Override
    protected void startUp() {
        overlayManager.add(pluginOverlay);
        mouseListener = new GbocMouseListener(pluginConfig);
        mouseManager.registerMouseListener(mouseListener);
        instantiateScript();


        autoClick.setReady(pluginConfig.clickMode() == GbocConfig.ClickMode.AUTO);
        autoClick.run();
        currentAction.reset();

    }

    @Override
    protected void shutDown() {
        overlayManager.remove(pluginOverlay);
        mouseManager.unregisterMouseListener(mouseListener);
        if (currentScript != null) currentScript.shutdown();
        currentAction.reset();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("gboc")) {
            switch (event.getKey()) {
                case "selectedScript":
                    instantiateScript();
                case "clickMode":
                    autoClick.setReady(pluginConfig.clickMode() == GbocConfig.ClickMode.AUTO);
                    break;
            }
        }
    }

    private void instantiateScript() {
        if (currentScript != null) {
            currentScript.shutdown();
            currentScript = null;
        }

        if (pluginConfig != null) {
            switch (pluginConfig.selectedScript()) {
//                case HERB_RUN:
//                    currentScript = injector.getInstance(HerbRunScript.class);
//                    break;
                case NMZ:
                    currentScript = injector.getInstance(NMZScript.class);
                    break;
                case NONE:
                default:
                    break;
            }

            if (currentScript != null) {
                currentScript.run();
            }
        }
    }
}
