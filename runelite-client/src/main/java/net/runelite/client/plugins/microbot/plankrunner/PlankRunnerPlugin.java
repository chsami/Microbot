package net.runelite.client.plugins.microbot.plankrunner;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.plankrunner.enums.Plank;
import net.runelite.client.plugins.microbot.plankrunner.enums.SawmillLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.GMason + "Plank Runner",
        description = "Microbot plank runner plugin",
        tags = {"money making", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class PlankRunnerPlugin extends Plugin {
    @Inject
    private PlankRunnerConfig config;

    @Provides
    PlankRunnerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PlankRunnerConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private PlankRunnerOverlay plankRunnerOverlay;

    @Inject
    private PlankRunnerScript plankRunnerScript;

    public static String version = "1.0.0";

    @Getter
    private Plank plank;
    @Getter
    private SawmillLocation sawmillLocation;
    @Getter
    private boolean useEnergyRestorePotions;
    @Getter
    private int drinkAtPercent;
    @Getter
    private boolean toggleOverlay;
    @Getter
    private int profit;
    @Getter
    public Instant startTime;


    @Override
    protected void startUp() throws AWTException {
        plank = config.plank();
        sawmillLocation = config.sawmillLocation();
        useEnergyRestorePotions = config.useEnergyRestorePotions();
        drinkAtPercent = config.drinkAtPercent();
        toggleOverlay = config.toggleOverlay();
        startTime = Instant.now();

        if (overlayManager != null) {
            overlayManager.add(plankRunnerOverlay);
        }
        plankRunnerScript.run();
    }

    protected void shutDown() {
        plankRunnerScript.shutdown();
        overlayManager.remove(plankRunnerOverlay);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(PlankRunnerConfig.configGroup)) return;

        if (event.getKey().equals(PlankRunnerConfig.plank)) {
            plank = config.plank();
        }

        if (event.getKey().equals(PlankRunnerConfig.sawmillLocation)) {
            sawmillLocation = config.sawmillLocation();
        }

        if (event.getKey().equals(PlankRunnerConfig.useEnergyRestorePotions)) {
            useEnergyRestorePotions = config.useEnergyRestorePotions();
        }

        if (event.getKey().equals(PlankRunnerConfig.drinkAtPercent)) {
            drinkAtPercent = config.drinkAtPercent();
        }

        if (event.getKey().equals(PlankRunnerConfig.toggleOverlay)) {
            toggleOverlay = config.toggleOverlay();
            toggleOverlay(toggleOverlay);
        }
    }

    private void toggleOverlay(boolean hideOverlay) {
        if (overlayManager != null) {
            boolean hasOverlay = overlayManager.anyMatch(ov -> ov.getName().equalsIgnoreCase(PlankRunnerOverlay.class.getSimpleName()));

            if (hideOverlay) {
                if (!hasOverlay) return;

                overlayManager.remove(plankRunnerOverlay);
            } else {
                if (hasOverlay) return;

                overlayManager.add(plankRunnerOverlay);
            }
        }
    }

    public void calculateProfit() {
        int plankCount = Rs2Inventory.items().stream()
                .filter(rs2Item -> rs2Item.getId() == plank.getPlankItemId())
                .mapToInt(rs2Item -> 1)
                .sum();

        int logCost = Rs2GrandExchange.getPrice(plank.getLogItemId()) * plankCount;
        int plankProfit = Rs2GrandExchange.getPrice(plank.getPlankItemId()) * plankCount;
        int sawmillCost = plankCount * plank.getCostPerPlank();

        int profitFromRun = plankProfit - logCost - sawmillCost;

        profit += profitFromRun;
    }
}
