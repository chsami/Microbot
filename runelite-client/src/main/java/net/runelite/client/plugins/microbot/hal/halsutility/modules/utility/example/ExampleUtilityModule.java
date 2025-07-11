package net.runelite.client.plugins.microbot.hal.halsutility.modules.utility.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.AbstractHalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
public class ExampleUtilityModule extends AbstractHalModule<ExampleUtilityConfig> {

    @Inject
    private OverlayManager overlayManager;
    
    private ExampleUtilityOverlay utilityOverlay;

    public ExampleUtilityModule() {
        super(HalModuleCategory.UTILITY, "Example Utility", ExampleUtilityConfig.class);
    }

    @Override
    protected void onStart() {
        log.info("Example Utility module started");
        
        // Create and add overlay
        utilityOverlay = new ExampleUtilityOverlay(this);
        if (overlayManager != null && utilityOverlay != null) {
            overlayManager.add(utilityOverlay);
        }
        
        // Add your utility logic here
    }

    @Override
    protected void onStop() {
        log.info("Example Utility module stopped");
        
        // Remove overlay
        if (overlayManager != null && utilityOverlay != null) {
            overlayManager.remove(utilityOverlay);
        }
        
        // Clean up your utility logic here
    }
} 