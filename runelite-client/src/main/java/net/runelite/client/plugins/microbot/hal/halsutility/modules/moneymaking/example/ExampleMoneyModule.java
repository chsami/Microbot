package net.runelite.client.plugins.microbot.hal.halsutility.modules.moneymaking.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.AbstractHalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
public class ExampleMoneyModule extends AbstractHalModule<ExampleMoneyConfig> {

    @Inject
    private OverlayManager overlayManager;
    
    private ExampleMoneyOverlay moneyOverlay;

    public ExampleMoneyModule() {
        super(HalModuleCategory.MONEY, "Example Money Making", ExampleMoneyConfig.class);
    }

    @Override
    protected void onStart() {
        log.info("Example Money Making module started");
        
        // Create and add overlay
        moneyOverlay = new ExampleMoneyOverlay(this);
        if (overlayManager != null && moneyOverlay != null) {
            overlayManager.add(moneyOverlay);
        }
        
        // Add your money making logic here
    }

    @Override
    protected void onStop() {
        log.info("Example Money Making module stopped");
        
        // Remove overlay
        if (overlayManager != null && moneyOverlay != null) {
            overlayManager.remove(moneyOverlay);
        }
        
        // Clean up your money making logic here
    }
} 