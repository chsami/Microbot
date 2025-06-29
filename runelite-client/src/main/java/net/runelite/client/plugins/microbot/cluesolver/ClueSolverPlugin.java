package net.runelite.client.plugins.microbot.cluesolver;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.ClueScrollPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
        name = "Clue Solver",
        description = "Automates solving clue scrolls by managing interactions",
        tags = {"clue", "solver", "automation"},
        enabledByDefault = false
)
@PluginDependency(ClueScrollPlugin.class)
public class ClueSolverPlugin extends Plugin {

    @Inject
    private ClueSolverScript clueSolverScript;

    @Inject
    private ClueSolverOverlay clueSolverOverlay;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClueSolverConfig clueSolverConfig;

    public static boolean isSolverRunning = false;

    @Provides
    ClueSolverConfig provideClueSolverConfig(ConfigManager configManager) {
        return configManager.getConfig(ClueSolverConfig.class);
    }

    @Override
    protected void startUp() {
        log.info("Starting Clue Solver Plugin");
        if (null != overlayManager) {
            overlayManager.add(clueSolverOverlay);
            clueSolverOverlay.myButton.hookMouseListener();
        }
    }

    @Override
    protected void shutDown() {
        log.info("Shutting down Clue Solver Plugin");
        overlayManager.remove(clueSolverOverlay);
        clueSolverOverlay.myButton.unhookMouseListener();
        stopClueSolver();
    }

    private synchronized void startClueSolver() {
        if (!ClueSolverPlugin.isSolverRunning) {
            clueSolverScript.start();
            ClueSolverPlugin.isSolverRunning = true;
            log.info("Clue Solver Script started.");
        }
    }

    private synchronized void stopClueSolver() {
        if (ClueSolverPlugin.isSolverRunning) {
            clueSolverScript.shutdown();
            ClueSolverPlugin.isSolverRunning = false;
            log.info("Clue Solver Script stopped.");
        }
    }

    /**
     * Configures the solver based on current settings.
     * Ensures the solver state reflects the config.
     */
    public void configureSolver() {
        if (!ClueSolverPlugin.isSolverRunning) {
            startClueSolver();
        } else {
            stopClueSolver();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("cluesolver")) {
            log.info("Configuration change detected for Clue Solver Plugin");
            configureSolver();
        }
    }


}
