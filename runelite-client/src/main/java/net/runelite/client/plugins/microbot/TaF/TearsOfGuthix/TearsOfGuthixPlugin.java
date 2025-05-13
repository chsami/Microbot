package net.runelite.client.plugins.microbot.TaF.TearsOfGuthix;

import com.google.inject.Provides;
import net.runelite.api.DecorativeObject;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Tears of Guthix",
        description = "Optimally complete the Tears of Guthix D&D",
        tags = {"Tears", "of", "guthix", "taf", "xp", "microbot"},
        enabledByDefault = false
)
public class TearsOfGuthixPlugin extends Plugin {

    private static final int TOG_REGION = 12948;
    @Inject
    public TearsOfGuthixScript tearsOfGuthixScript;
    private Instant scriptStartTime;
    @Inject
    private TearsOfGuthixConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TearsOfGuthixOverlay tearsOfGuthixOverlay;

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(tearsOfGuthixOverlay);
        }
        tearsOfGuthixScript.run(config);
    }

    @Override
    protected void shutDown() {
        tearsOfGuthixScript.shutdown();
        overlayManager.remove(tearsOfGuthixOverlay);
    }

    @Provides
    TearsOfGuthixConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TearsOfGuthixConfig.class);
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event) {
        DecorativeObject object = event.getDecorativeObject();

        if (object.getId() == ObjectID.TOG_WEEPING_WALL_GOOD_R ||
                object.getId() == ObjectID.TOG_WEEPING_WALL_GOOD_L ||
                object.getId() == ObjectID.TOG_WEEPING_WALL_BAD_R ||
                object.getId() == ObjectID.TOG_WEEPING_WALL_BAD_L) {
            if (Rs2Player.getWorldLocation().getRegionID() == TOG_REGION) {
                tearsOfGuthixScript.Streams.put(event.getDecorativeObject(), Instant.now());
            }
        }
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event) {
        if (tearsOfGuthixScript.Streams.isEmpty()) {
            return;
        }

        DecorativeObject object = event.getDecorativeObject();
        tearsOfGuthixScript.Streams.remove(object);
    }
}
