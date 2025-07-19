package net.runelite.client.plugins.microbot.crafting;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.crafting.enums.Activities;
import net.runelite.client.plugins.microbot.crafting.scripts.*;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Crafting",
        description = "Microbot crafting plugin",
        tags = {"skilling", "microbot", "crafting"},
        enabledByDefault = false
)
@Slf4j
public class CraftingPlugin extends Plugin {

    private final DefaultScript defaultScript = new DefaultScript();
    private final GemsScript gemsScript = new GemsScript();
    private final GlassblowingScript glassblowingScript = new GlassblowingScript();
    private final StaffScript staffScript = new StaffScript();
    private final FlaxSpinScript flaxSpinScript = new FlaxSpinScript();
    private final DragonLeatherScript dragonLeatherScript = new DragonLeatherScript();

    public ICraftingScript currentScript = null;

    @Inject
    private CraftingConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private CraftingOverlay craftingOverlay;

    @Provides
    CraftingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CraftingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);
        if (overlayManager != null) {
            overlayManager.add(craftingOverlay);
        }

//        if (config.activityType() == Activities.DEFAULT) {

        if (config.activityType() == Activities.GEM_CUTTING) {
            currentScript = gemsScript;
            gemsScript.run(config);
        } else if (config.activityType() == Activities.GLASSBLOWING) {
            currentScript = glassblowingScript;
            glassblowingScript.run(config);
        } else if (config.activityType() == Activities.STAFF_MAKING) {
            currentScript = staffScript;
            staffScript.run(config);
        } else if (config.activityType() == Activities.FLAX_SPINNING) {
            currentScript = flaxSpinScript;
            flaxSpinScript.run(config);
        } else if (config.activityType() == Activities.DRAGON_LEATHER) {
            currentScript = dragonLeatherScript;
            dragonLeatherScript.run(config);
        }
    }

    protected void shutDown() {
        staffScript.shutdown();
        glassblowingScript.shutdown();
        gemsScript.shutdown();
        defaultScript.shutdown();
        flaxSpinScript.shutdown();
        dragonLeatherScript.shutdown();
        overlayManager.remove(craftingOverlay);
    }
}
