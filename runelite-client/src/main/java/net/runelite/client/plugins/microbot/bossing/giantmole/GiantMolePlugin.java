package net.runelite.client.plugins.microbot.bossing.giantmole;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "Giant Mole",
        description = "Giant Mole plugin",
        tags = {"Giant Mole", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GiantMolePlugin extends Plugin {
    @Inject
    private GiantMoleConfig config;
    @Getter
    @Setter
    public static InfoBoxManager infoBoxManager;

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GiantMoleOverlay giantMoleOverlay;

    @Inject
    GiantMoleScript giantMoleScript;
    @Inject
    private HighAlchScript highAlchScript;
    @Provides
    GiantMoleConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GiantMoleConfig.class);
    }



    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(giantMoleOverlay);
        }
        giantMoleScript.run(config);
        highAlchScript.run(config);
    }

    protected void shutDown() {
        giantMoleScript.shutdown();
        highAlchScript.shutdown();
        overlayManager.remove(giantMoleOverlay);
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

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if(event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }
        if(event.getMessage().contains("You look inside the mole hill and see")) {
            GiantMoleScript.isWorldOccupied = !event.getMessage().contains("no adventurers inside the mole tunnels.");
            GiantMoleScript.checkedIfWorldOccupied = true;
        }
    }

}
