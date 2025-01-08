package net.runelite.client.plugins.microbot.prayer.burybones;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.prayer.burybones.enums.State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class BuryBonesScript extends Script {
    private final BuryBonesPlugin plugin;
    private final BuryBonesConfig config;
    private State state;

    @Inject
    public BuryBonesScript(BuryBonesPlugin plugin, BuryBonesConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.BURY_BONES);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                long startTime = System.currentTimeMillis();

                if (config.Afk() && Rs2Random.between(1, 100) == 2) {
                    int afkMin = config.AfkMin() * 1000;
                    int afkMax = config.AfkMax() * 1000;

                    sleep(afkMin, afkMax);
                }

                state = updateState();

                switch (state) {
                    case BANKING:
                        handleBanking();
                        break;
                    case BURYING_BONES:
                        handleBuryingBones();
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);
            } catch (Exception exception) {
                System.out.println(exception.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleBanking() {
        boolean isBankOpen = Rs2Bank.isNearBank(15) ? Rs2Bank.useBank() : Rs2Bank.walkToBankAndUseBank();
        if (!isBankOpen || !Rs2Bank.isOpen()) return;

        if (!Rs2Bank.hasItem(plugin.getBones().getItemID())) {
            Microbot.showMessage("Bones not found in bank!");
            shutdown();
            return;
        }

        Rs2Bank.withdrawAll(plugin.getBones().getItemID());
        Rs2Bank.closeBank();

        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    private void handleBuryingBones() {
        if (!hasBones()) {
            state = State.BANKING;
            return;
        }

        Rs2Inventory.interact(plugin.getBones().getItemID(), "Bury");
        Rs2Player.waitForXpDrop(Skill.PRAYER, 10000, false);
    }

    private State updateState() {
        if (hasBones()) {
            return State.BURYING_BONES;
        }

        return State.BANKING;
    }

    private boolean hasBones() {
        return Rs2Inventory.hasItem(plugin.getBones().getItemID());
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
