package net.runelite.client.plugins.microbot.TaF.DemonicGorillaKiller;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Projectile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.containers.FixedSizeQueue;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + "Demonic Gorillas",
        description = "Automates restocking, prayer flicking, and gear switching during Demonic Gorillas",
        tags = {"demonic", "Gorilla", "flicker", "weapon", "switch", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class DemonicGorillaPlugin extends Plugin {

    public static final int DEMONIC_GORILLA_ROCK = 856;
    public static FixedSizeQueue<WorldPoint> lastLocation = new FixedSizeQueue<>(2);
    private ScheduledExecutorService scheduledExecutorService;
    @Inject
    private ConfigManager configManager;
    @Inject
    private DemonicGorillaConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private DemonicGorillaOverlay demonicGorillaOverlay;
    @Inject
    private DemonicGorillaScript demonicGorillaScript;
    private Instant scriptStartTime;

    @Provides
    DemonicGorillaConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DemonicGorillaConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        scriptStartTime = Instant.now();
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        if (overlayManager != null) {
            overlayManager.add(demonicGorillaOverlay);
        }
        demonicGorillaScript.run(config);
    }

    @Override
    protected void shutDown() {
        demonicGorillaScript.shutdown();
        overlayManager.remove(demonicGorillaOverlay);
        if (scheduledExecutorService != null && !scheduledExecutorService.isShutdown()) {
            scheduledExecutorService.shutdown();
        }
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Subscribe
    private void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals("DemonicGorilla")) {
            return;
        }

        if (event.getKey().equals("copyGear")) {
            Microbot.getClientThread().invoke(() -> {
                try {
                    StringJoiner gearList = new StringJoiner(",");
                    for (Item item : Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT).getItems()) {
                        if (item != null && item.getId() != -1 && item.getId() != 6512) {
                            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(item.getId());
                            gearList.add(itemComposition.getName());
                        }
                    }

                    String gearString = gearList.toString();
                    if (gearString.isEmpty()) {
                        Microbot.log("No gear found to copy.");
                        return;
                    }

                    StringSelection selection = new StringSelection(gearString);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                    Microbot.log("Current gear copied to clipboard: " + gearString);
                } catch (Exception e) {
                    Microbot.log("Failed to copy gear to clipboard: " + e.getMessage());
                }
            });
        }
    }

    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        final Projectile projectile = event.getProjectile();
        var dist = projectile.getTarget().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation());
        var dist2 = Rs2Player.getLocalLocation().distanceTo(projectile.getTarget());

        if (projectile.getId() == DEMONIC_GORILLA_ROCK && (dist < 9000 || dist2 < 9000)) {
            demonicGorillaScript.demonicGorillaRockPosition = event.getPosition();
            demonicGorillaScript.demonicGorillaRockLifeCycle = projectile.getEndCycle();
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        var currentLocation = Rs2Player.getWorldLocation();
        DemonicGorillaScript.playerMoved = !lastLocation.contains(currentLocation);
        lastLocation.add(currentLocation);
        DemonicGorillaScript.gameTickCount++;
    }
}
