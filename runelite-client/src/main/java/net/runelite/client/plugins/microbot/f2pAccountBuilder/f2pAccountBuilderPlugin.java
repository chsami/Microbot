package net.runelite.client.plugins.microbot.f2pAccountBuilder;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.example.ExampleConfig;
import net.runelite.client.plugins.microbot.example.ExampleOverlay;
import net.runelite.client.plugins.microbot.example.ExampleScript;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Gage + "F2P Acc Builder",
        description = "F2P Account Builder",
        tags = {"F2P Account Builder", "F2P", "Account", "Builder"},
        enabledByDefault = false
)
@Slf4j
public class f2pAccountBuilderPlugin extends Plugin {
    @Inject
    private net.runelite.client.plugins.microbot.f2pAccountBuilder.f2pAccountBuilderConfig config;
    @Provides
    net.runelite.client.plugins.microbot.f2pAccountBuilder.f2pAccountBuilderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(f2pAccountBuilderConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private f2pAccountBuilderOverlay f2paccountbuilderOverlay;

    @Inject
    f2pAccountBuilderScript f2paccountbuilderScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(f2paccountbuilderOverlay);
            f2paccountbuilderOverlay.myButton.hookMouseListener();
        }
        f2paccountbuilderScript.run(config);
        f2paccountbuilderScript.shouldThink = true;
        f2paccountbuilderScript.scriptStartTime = System.currentTimeMillis();
    }

    protected void shutDown() {
        f2paccountbuilderScript.shutdown();
        overlayManager.remove(f2paccountbuilderOverlay);
        f2paccountbuilderOverlay.myButton.unhookMouseListener();
    }
    int ticks = 10;
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        //System.out.println(getName().chars().mapToObj(i -> (char)(i + 3)).map(String::valueOf).collect(Collectors.joining()));

        if (ticks > 0) {
            ticks--;
        } else {
            ticks = 10;
        }

    }

}
