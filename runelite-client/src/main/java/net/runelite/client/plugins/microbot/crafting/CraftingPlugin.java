package net.runelite.client.plugins.microbot.crafting;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.crafting.enums.Activities;
import net.runelite.client.plugins.microbot.crafting.scripts.*;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "Crafting", description = "Microbot crafting plugin", tags = {
        "skilling",
        "microbot",
        "crafting",
        "staffs",
        "gems",
        "glass",
        "flax"
}, enabledByDefault = false)
@Slf4j
public class CraftingPlugin extends Plugin {

    public static String version = "V1.1.0";
    public Instant scriptStartTime;

    private final DefaultScript defaultScript = new DefaultScript();
    private final GemsScript gemsScript = new GemsScript();
    private final GlassblowingScript glassblowingScript = new GlassblowingScript();
    private final StaffScript staffScript = new StaffScript();
    private final FlaxSpinScript flaxSpinScript = new FlaxSpinScript();
    private final AmethystScript amethystScript = new AmethystScript();
    private final DriftNetScript driftNetScript = new DriftNetScript();
    @Inject
    private CraftingConfig config;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private Notifier notifier;
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
        Microbot.pauseAllScripts = false;
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        Microbot.setMouse(new VirtualMouse());
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCraftingSetup();
        // Everyone makes mistakes
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.microBreakChance = 0.2;
        Rs2Walker.disableTeleports = true;

        if (overlayManager != null) {
            overlayManager.add(craftingOverlay);
        }

        scriptStartTime = Instant.now();
        if (config.activityType() == Activities.GEM_CUTTING) {
            gemsScript.run(config);
        } else if (config.activityType() == Activities.GLASSBLOWING) {
            glassblowingScript.run(config);
        } else if (config.activityType() == Activities.STAFF_MAKING) {
            staffScript.run(config);
        } else if (config.activityType() == Activities.FLAX_SPINNING) {
            flaxSpinScript.run(config);
        } else if (config.activityType() == Activities.CUTTING_AMETHYST) {
            amethystScript.run(config);
        } else if (config.activityType() == Activities.WEAVING_NETS) {
            driftNetScript.run(config);
        }
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    protected void shutDown() {
        staffScript.shutdown();
        glassblowingScript.shutdown();
        gemsScript.shutdown();
        defaultScript.shutdown();
        flaxSpinScript.shutdown();
        amethystScript.shutdown();
        driftNetScript.shutdown();
        scriptStartTime = null;
        overlayManager.remove(craftingOverlay);
        Microbot.pauseAllScripts = true;
        Rs2Antiban.resetAntibanSettings();
    }
}
