package net.runelite.client.plugins.microbot.hal.halsutility.modules.skilling.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.AbstractHalModule;
import net.runelite.client.plugins.microbot.hal.halsutility.modules.HalModuleCategory;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

@Slf4j
public class ExampleSkillingModule extends AbstractHalModule<ExampleSkillingConfig> {

    @Inject
    private OverlayManager overlayManager;
    
    private ExampleSkillingOverlay skillingOverlay;

    public ExampleSkillingModule() {
        super(HalModuleCategory.SKILLING, "Example Skilling", ExampleSkillingConfig.class);
    }

    @Override
    protected void onStart() {
        log.info("Example Skilling module started");
        
        // Create and add overlay
        skillingOverlay = new ExampleSkillingOverlay(this);
        if (overlayManager != null && skillingOverlay != null) {
            overlayManager.add(skillingOverlay);
        }
        
        // Add your skilling logic here
    }

    @Override
    protected void onStop() {
        log.info("Example Skilling module stopped");
        
        // Remove overlay
        if (overlayManager != null && skillingOverlay != null) {
            overlayManager.remove(skillingOverlay);
        }
        
        // Clean up your skilling logic here
    }

    @Override
    public void onConfigChanged(ConfigChanged event) {
        // Handle specific config changes for this module
        switch (event.getKey()) {
            case "enabled":
                boolean enabled = Boolean.parseBoolean(event.getNewValue());
                if (enabled && !isRunning()) {
                    start();
                } else if (!enabled && isRunning()) {
                    stop();
                }
                break;
            case "skillToTrain":
                log.info("Skill to train changed to: {}", event.getNewValue());
                // Handle skill change logic here
                break;
            case "autoDrop":
                boolean autoDrop = Boolean.parseBoolean(event.getNewValue());
                log.info("Auto drop setting changed to: {}", autoDrop);
                // Handle auto drop setting change
                break;
            default:
                log.debug("Config changed: {} = {}", event.getKey(), event.getNewValue());
                break;
        }
    }
} 