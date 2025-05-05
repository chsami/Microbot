package net.runelite.client.plugins.microbot.TaF.RoyalTitans;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GraphicsObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Royal Titans",
        description = "Kills the Royal Titans boss with another bot",
        tags = {"Combat", "bossing", "TaF", "Royal Titans", "Ice giant", "Fire giant", "Duo"},
        enabledByDefault = false
)
@Slf4j
public class RoyalTitansPlugin extends Plugin {
    private final Integer GRAPHICS_OBJECT_FIRE = 3218;
    private final Integer GRAPHICS_OBJECT_ICE = 3221;
    private final Integer ENRAGE_ELEMENTAL_BLAST_SAFESPOT = 56003;
    private final RoyalTitansLooterScript royalTitansLooterScript = new RoyalTitansLooterScript();
    @Inject
    public RoyalTitansScript royalTitansScript;
    private ScheduledExecutorService scheduledExecutorService;
    @Inject
    private RoyalTitansConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private RoyalTitansOverlay royalTitansOverlay;
    private Instant scriptStartTime;

    @Provides
    RoyalTitansConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RoyalTitansConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        scheduledExecutorService = Executors.newScheduledThreadPool(50);
        if (overlayManager != null) {
            overlayManager.add(royalTitansOverlay);
        }
        royalTitansScript.run(config);
        royalTitansLooterScript.run(config, royalTitansScript);
        Rs2Tile.init();
    }

    @Override
    protected void shutDown() {
        royalTitansScript.shutdown();
        royalTitansLooterScript.shutdown();
        scriptStartTime = null;
        overlayManager.remove(royalTitansOverlay);
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Subscribe
    public void onGraphicsObjectCreated(GraphicsObjectCreated event) {
        final GraphicsObject graphicsObject = event.getGraphicsObject();
        if (graphicsObject.getId() == GRAPHICS_OBJECT_ICE || graphicsObject.getId() == GRAPHICS_OBJECT_FIRE) {
            Rs2Tile.addDangerousGraphicsObjectTile(graphicsObject, 600 * 5);
        }
    }

    @Subscribe
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        final GroundObject groundObject = event.getGroundObject();
        if (groundObject.getId() == ENRAGE_ELEMENTAL_BLAST_SAFESPOT) {
            royalTitansScript.enrageTile = null;
            Microbot.log("Enrage tile despawned");
        }
    }

    @Subscribe
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        final GroundObject groundObject = event.getGroundObject();
        final Tile tile = event.getTile();
        if (groundObject.getId() == ENRAGE_ELEMENTAL_BLAST_SAFESPOT) {
            try {
                royalTitansScript.enrageTile = tile;
                scheduledExecutorService.schedule(() -> {
                    if (!Rs2Player.getWorldLocation().equals(tile.getWorldLocation())) {
                        Rs2Walker.walkFastCanvas(tile.getWorldLocation());
                        Rs2Walker.walkFastCanvas(tile.getWorldLocation());
                    }
                }, 100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                Microbot.log("Error while walking to enrage tile: " + e.getMessage());
            }
        }
    }
}
