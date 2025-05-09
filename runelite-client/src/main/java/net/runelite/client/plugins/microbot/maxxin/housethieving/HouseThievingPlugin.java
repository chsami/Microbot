package net.runelite.client.plugins.microbot.maxxin.housethieving;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;


@PluginDescriptor(
        name = PluginDescriptor.Maxxin + "House Thieving",
        description = "House Thieving",
        tags = {"thieving", "house thieving"},
        enabledByDefault = false
)
@Slf4j
public class HouseThievingPlugin extends Plugin {
    @Inject
    private HouseThievingConfig config;

    @Provides
    HouseThievingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(HouseThievingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private HouseThievingOverlay houseThievingOverlay;

    private HouseThievingScript houseThievingScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(houseThievingOverlay);
        }

        houseThievingScript = new HouseThievingScript(this);
        houseThievingScript.run(config);
    }

    protected void shutDown() {
        houseThievingScript.shutdown();
        overlayManager.remove(houseThievingOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && !Microbot.pauseAllScripts) {
            if (event.getMessage().toLowerCase().contains("you don't have enough coins.")) {
                Microbot.status = "[Shutting down] - Reason: Not enough coins.";
                Microbot.showMessage(Microbot.status);
                houseThievingScript.shutdown();
            }
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
//        System.out.println("varbit changed");
//        System.out.println(event);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(HouseThievingConfig.configGroup)) return;
    }

    @Subscribe
    public void onOverheadTextChanged(OverheadTextChanged event) {
        /* ignore */
    }
}
