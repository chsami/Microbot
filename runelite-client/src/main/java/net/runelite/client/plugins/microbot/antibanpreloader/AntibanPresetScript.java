package net.runelite.client.plugins.microbot.antibanpreloader;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;

import java.util.concurrent.TimeUnit;

public class AntibanPresetScript extends Script {

    public boolean run(AntibanPresetConfig config) {
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;

                long startTime = System.currentTimeMillis();

                // Reset and apply all antiban settings
                Rs2Antiban.resetAntibanSettings();

                Rs2AntibanSettings.usePlayStyle = config.usePlayStyle();
                Rs2AntibanSettings.takeMicroBreaks = config.takeMicroBreaks();
                Rs2AntibanSettings.microBreakChance = config.microBreakChance();
                Rs2AntibanSettings.microBreakDurationLow = config.microBreakDurationLow();
                Rs2AntibanSettings.microBreakDurationHigh = config.microBreakDurationHigh();

                Rs2AntibanSettings.simulateFatigue = config.simulateFatigue();
                Rs2AntibanSettings.simulateAttentionSpan = config.simulateAttentionSpan();
                Rs2AntibanSettings.simulateMistakes = config.simulateMistakes();
                Rs2AntibanSettings.nonLinearIntervals = config.nonLinearIntervals();
                Rs2AntibanSettings.dynamicActivity = config.dynamicActivity();
                Rs2AntibanSettings.profileSwitching = config.profileSwitching();

                Rs2AntibanSettings.naturalMouse = config.naturalMouse();
                Rs2AntibanSettings.moveMouseOffScreen = config.moveMouseOffScreen();
                Rs2AntibanSettings.moveMouseOffScreenChance = config.moveMouseOffScreenChance();
                Rs2AntibanSettings.moveMouseRandomly = config.moveMouseRandomly();
                Rs2AntibanSettings.moveMouseRandomlyChance = config.moveMouseRandomlyChance();

                Rs2Antiban.setActivityIntensity(config.activityIntensity());

                long endTime = System.currentTimeMillis();
                Microbot.log("Script has finished running. Total time for loop: " + (endTime - startTime) + "ms.");
                shutdown();

            } catch (Exception ex) {
                System.out.println("Error in AntibanPresetScript: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}