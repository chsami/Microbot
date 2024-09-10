package net.runelite.client.plugins.microbot.fletching;


import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingItem;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingMaterial;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.TimeUnit;

@Getter
class ProgressiveFletchingModel {
    @Setter
    private FletchingItem fletchingItem;
    @Setter
    private FletchingMaterial fletchingMaterial;
}

public class FletchingScript extends Script {

    public static String version = "1.6.1";
    ProgressiveFletchingModel model = new ProgressiveFletchingModel();

    String primaryItemToFletch = "";
    String secondaryItemToFletch = "";

    FletchingMode fletchingMode;

    public void run(FletchingConfig config) {
        fletchingMode = config.fletchingMode();
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFletchingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn())
                    return;
                if (!super.run()) return;

                if (fletchingMode == FletchingMode.PROGRESSIVE && model.getFletchingItem() == null)
                    calculateItemToFletch();


                if (!configChecks(config)) return;

                if (Rs2AntibanSettings.actionCooldownActive)
                    return;

//                if (config.Afk() && Random.random(1, 100) == 2)
//                    sleep(1000, 60000);

                boolean hasRequirementsToFletch;
                boolean hasRequirementsToBank;
                primaryItemToFletch = fletchingMode.getItemName();

                if (fletchingMode == FletchingMode.PROGRESSIVE) {
                    secondaryItemToFletch = (model.getFletchingMaterial().getName() + " logs").trim();
                    hasRequirementsToFletch = Rs2Inventory.hasItem(primaryItemToFletch)
                            && Rs2Inventory.hasItemAmount(secondaryItemToFletch, model.getFletchingItem().getAmountRequired());
                    hasRequirementsToBank = !Rs2Inventory.hasItem(primaryItemToFletch)
                            || !Rs2Inventory.hasItemAmount(secondaryItemToFletch, model.getFletchingItem().getAmountRequired());
                } else {
                    secondaryItemToFletch = fletchingMode == FletchingMode.STRUNG
                            ? config.fletchingMaterial().getName() + " " + config.fletchingItem().getContainsInventoryName() + " (u)"
                            : (config.fletchingMaterial().getName() + " logs").trim();
                    hasRequirementsToFletch = Rs2Inventory.hasItem(primaryItemToFletch)
                            && Rs2Inventory.hasItemAmount(secondaryItemToFletch, config.fletchingItem().getAmountRequired());
                    hasRequirementsToBank = !Rs2Inventory.hasItem(primaryItemToFletch)
                            || !Rs2Inventory.hasItemAmount(secondaryItemToFletch, config.fletchingItem().getAmountRequired());
                }

                if (hasRequirementsToFletch) {
                    fletch(config);
                }
                if (hasRequirementsToBank) {
                    bankItems(config);
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }

    private void bankItems(FletchingConfig config) {
        Rs2Bank.openBank();

        // make sure there's no long bows left
        if (fletchingMode == FletchingMode.STRUNG) {
            Rs2Bank.depositAll();
        } else if (fletchingMode == FletchingMode.PROGRESSIVE) {
            Rs2Bank.depositAll(model.getFletchingItem().getContainsInventoryName());
            calculateItemToFletch();
            secondaryItemToFletch = (model.getFletchingMaterial().getName() + " logs").trim();
        } else {
            // make sure there's no long bows left
            Rs2Bank.depositAll(config.fletchingItem().getContainsInventoryName());
        }


        if (Rs2Bank.isOpen() && !Rs2Bank.hasItem(primaryItemToFletch) && !Rs2Inventory.hasItem(primaryItemToFletch)) {
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + primaryItemToFletch + " not found in the bank.";
            Microbot.showMessage(Microbot.status);
            shutdown();
            return;
        }

        // Extra check if we for some reason have a full inventory without a knife
        if (!Rs2Inventory.hasItem(primaryItemToFletch) && Rs2Inventory.isFull()) {
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty, 10000);
        }

        if (!Rs2Inventory.hasItem(primaryItemToFletch)) {
            Rs2Bank.withdrawX(true, primaryItemToFletch, fletchingMode.getAmount(), true);
            sleepUntil(() -> Rs2Inventory.hasItem(primaryItemToFletch));
        }

        if (Rs2Bank.isOpen() && !Rs2Bank.hasItem(secondaryItemToFletch)) {
            if (Rs2Bank.hasBankItem("bow string") && fletchingMode == FletchingMode.UNSTRUNG_STRUNG) {
                Rs2Bank.depositAll();
                fletchingMode = FletchingMode.STRUNG;
                return;
            }
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + secondaryItemToFletch + " not found in the bank.";
            Microbot.showMessage(Microbot.status);
            shutdown();
            return;
        }

        final String finalSecondaryItemToFletch = secondaryItemToFletch;

        do {
            if (fletchingMode == FletchingMode.STRUNG)
                Rs2Bank.withdrawX(true, secondaryItemToFletch, fletchingMode.getAmount());
            else
                Rs2Bank.withdrawAll(secondaryItemToFletch);

            sleepUntil(() -> Rs2Inventory.hasItem(finalSecondaryItemToFletch), 2000);
        } while (!Rs2Inventory.hasItem(finalSecondaryItemToFletch));

        sleep(600, 3000);
        Rs2Bank.closeBank();
    }

    private void fletch(FletchingConfig config) {
        Rs2Inventory.combineClosest(primaryItemToFletch, secondaryItemToFletch);
        sleepUntil(() -> Rs2Widget.getWidget(17694736) != null);
        if (fletchingMode == FletchingMode.PROGRESSIVE) {
            keyPress(model.getFletchingItem().getOption(model.getFletchingMaterial(), fletchingMode));

        } else {
            keyPress(config.fletchingItem().getOption(config.fletchingMaterial(), fletchingMode));
        }
        sleepUntil(() -> Rs2Widget.getWidget(17694736) == null);
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
        Rs2Bank.preHover();
        if (fletchingMode == FletchingMode.PROGRESSIVE) {
            sleepUntil(() -> !Rs2Inventory.hasItemAmount(secondaryItemToFletch, model.getFletchingItem().getAmountRequired()) || hasLeveledUp, 60000);
        } else {
            sleepUntil(() -> !Rs2Inventory.hasItemAmount(secondaryItemToFletch, config.fletchingItem().getAmountRequired()) || hasLeveledUp, 60000);
        }
    }

    private boolean configChecks(FletchingConfig config) {
        if (config.fletchingMaterial() == FletchingMaterial.REDWOOD && config.fletchingItem() != FletchingItem.SHIELD) {
            Microbot.getNotifier().notify("[Wrong Configuration] You can only make shields with redwood logs.");
            shutdown();
            return false;
        }
        return true;
    }

    public void calculateItemToFletch() {
        int fletchingLevel = Microbot.getClient().getRealSkillLevel(Skill.FLETCHING);
        if (fletchingLevel < 5) {
            model.setFletchingItem(FletchingItem.ARROW_SHAFT);
            model.setFletchingMaterial(FletchingMaterial.LOG);
        } else if (fletchingLevel < 10) {
            model.setFletchingItem(FletchingItem.SHORT);
            model.setFletchingMaterial(FletchingMaterial.LOG);
        } else if (fletchingLevel < 20) {
            model.setFletchingItem(FletchingItem.LONG);
            model.setFletchingMaterial(FletchingMaterial.LOG);
        } else if (fletchingLevel < 25) {
            model.setFletchingItem(FletchingItem.SHORT);
            model.setFletchingMaterial(FletchingMaterial.OAK);
        } else if (fletchingLevel < 35) {
            model.setFletchingItem(FletchingItem.LONG);
            model.setFletchingMaterial(FletchingMaterial.OAK);
        } else if (fletchingLevel < 40) {
            model.setFletchingItem(FletchingItem.SHORT);
            model.setFletchingMaterial(FletchingMaterial.WILLOW);
        } else if (fletchingLevel < 50) {
            model.setFletchingItem(FletchingItem.LONG);
            model.setFletchingMaterial(FletchingMaterial.WILLOW);
        } else if (fletchingLevel < 55) {
            model.setFletchingItem(FletchingItem.SHORT);
            model.setFletchingMaterial(FletchingMaterial.MAPLE);
        } else if (fletchingLevel < 65) {
            model.setFletchingItem(FletchingItem.LONG);
            model.setFletchingMaterial(FletchingMaterial.MAPLE);
        } else if (fletchingLevel < 70) {
            model.setFletchingItem(FletchingItem.SHORT);
            model.setFletchingMaterial(FletchingMaterial.YEW);
        } else if (fletchingLevel < 80) {
            model.setFletchingItem(FletchingItem.LONG);
            model.setFletchingMaterial(FletchingMaterial.YEW);
        } else if (fletchingLevel < 85) {
            model.setFletchingItem(FletchingItem.SHORT);
            model.setFletchingMaterial(FletchingMaterial.YEW);
        } else if (fletchingLevel < 99) {
            model.setFletchingItem(FletchingItem.LONG);
            model.setFletchingMaterial(FletchingMaterial.MAGIC);
        }
    }

    @Override
    public void shutdown() {

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}

