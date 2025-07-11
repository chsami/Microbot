package net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.blessedwine;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.AbstractHalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
public class BlessedWineModule extends AbstractHalModule<BlessedWineConfig> {

    @Inject
    private OverlayManager overlayManager;
    
    private BlessedWineOverlay blessedWineOverlay;
    private BlessedWineScript blessedWineScript;

    // Static fields for overlay access (matching original)
    public static String status = "Initializing...";
    public static int loopCount = 0;
    public static int expectedXp = 0;
    public static int startingXp = 0;
    public static int totalWinesToBless = 0;
    public static int totalLoops = 0;
    public static int endingXp = 0;

    public BlessedWineModule() {
        super(HalModuleCategory.SKILLING, "Blessed Wine", BlessedWineConfig.class);
    }

    @Override
    protected void onStart() {
        log.info("Blessed Wine module started");
        
        // Create overlay and script instances (following Quest Helper pattern)
        blessedWineOverlay = new BlessedWineOverlay(this);
        blessedWineScript = new BlessedWineScript();
        
        // Add overlay
        if (overlayManager != null && blessedWineOverlay != null) {
            overlayManager.add(blessedWineOverlay);
        }
        
        // Start the script
        if (blessedWineScript != null) {
            blessedWineScript.run();
        }
    }

    @Override
    protected void onStop() {
        log.info("Blessed Wine module stopped");
        
        // Stop the script
        if (blessedWineScript != null) {
            blessedWineScript.shutdown();
        }
        
        // Remove overlay
        if (overlayManager != null && blessedWineOverlay != null) {
            overlayManager.remove(blessedWineOverlay);
        }
        
        status = "Stopped";
    }

    @Override
    public void onConfigChanged(ConfigChanged event) {
        // Handle Blessed Wine specific config changes
        log.debug("Blessed Wine config changed: {} = {}", event.getKey(), event.getNewValue());
        
        // You can add specific config change handling here if needed
        // For example, if you add config options to BlessedWineConfig
    }
} 