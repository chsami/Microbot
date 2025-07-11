package net.runelite.client.plugins.microbot.hal.halsutility.modules.bossing.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.AbstractHalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
public class ExampleBossingModule extends AbstractHalModule<ExampleBossingConfig> {

    @Inject
    private OverlayManager overlayManager;
    
    private ExampleBossingOverlay bossingOverlay;

    public ExampleBossingModule() {
        super(HalModuleCategory.BOSSING, "Example Bossing", ExampleBossingConfig.class);
    }

    @Override
    protected void onStart() {
        log.info("Example Bossing module started");
        
        // Create and add overlay
        bossingOverlay = new ExampleBossingOverlay(this);
        if (overlayManager != null && bossingOverlay != null) {
            overlayManager.add(bossingOverlay);
        }
        
        // Add your bossing logic here
    }

    @Override
    protected void onStop() {
        log.info("Example Bossing module stopped");
        
        // Remove overlay
        if (overlayManager != null && bossingOverlay != null) {
            overlayManager.remove(bossingOverlay);
        }
        
        // Clean up your bossing logic here
    }
} 