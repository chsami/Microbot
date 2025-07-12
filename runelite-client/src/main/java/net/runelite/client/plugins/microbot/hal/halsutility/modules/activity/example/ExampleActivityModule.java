package net.runelite.client.plugins.microbot.hal.halsutility.modules.activity.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.AbstractHalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
public class ExampleActivityModule extends AbstractHalModule<ExampleActivityConfig> {

    @Inject
    private OverlayManager overlayManager;
    
    private ExampleActivityOverlay activityOverlay;

    public ExampleActivityModule() {
        super(HalModuleCategory.ACTIVITY, "Example Activity", ExampleActivityConfig.class);
    }

    @Override
    protected void onStart() {
        log.info("Example Activity module started");
        
        // Create and add overlay
        activityOverlay = new ExampleActivityOverlay();
        if (overlayManager != null && activityOverlay != null) {
            overlayManager.add(activityOverlay);
        }
        
        // Add your activity logic here
    }

    @Override
    protected void onStop() {
        log.info("Example Activity module stopped");
        
        // Remove overlay
        if (overlayManager != null && activityOverlay != null) {
            overlayManager.remove(activityOverlay);
        }
        
        // Clean up your activity logic here
    }
} 