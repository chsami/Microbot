package net.runelite.client.plugins.microbot.fletching;


import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingItem;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingMaterial;
import net.runelite.client.plugins.microbot.fletching.enums.FletchingMode;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
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

    public static String version = "1.6.2";

    // The fletching interface widget group ID
    private static final int FLETCHING_WIDGET_GROUP_ID = 17694736;

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

                if ((fletchingMode == FletchingMode.PROGRESSIVE || fletchingMode == FletchingMode.PROGRESSIVE_STRUNG)
                        && model.getFletchingItem() == null) {
                    calculateItemToFletch();
                }


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
                } else if (fletchingMode == FletchingMode.PROGRESSIVE_STRUNG) {
                    secondaryItemToFletch = model.getFletchingMaterial().getName() + " "
                            + model.getFletchingItem().getContainsInventoryName() + " (u)";
                    hasRequirementsToFletch = Rs2Inventory.hasItem(primaryItemToFletch) && Rs2Inventory.hasItem(secondaryItemToFletch);
                    hasRequirementsToBank = !Rs2Inventory.hasItem(primaryItemToFletch) || !Rs2Inventory.hasItem(secondaryItemToFletch);
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

        // Deposit items based on the fletching mode
        switch (fletchingMode) {
            case STRUNG:
                Rs2Bank.depositAll();
                break;
            case PROGRESSIVE:
                Rs2Bank.depositAll(model.getFletchingItem().getContainsInventoryName());
                calculateItemToFletch();
                secondaryItemToFletch = (model.getFletchingMaterial().getName() + " logs").trim();
                break;
            case PROGRESSIVE_STRUNG:
                Rs2Bank.depositAll();
                calculateItemToFletch();
                secondaryItemToFletch = model.getFletchingMaterial().getName() + " "
                        + model.getFletchingItem().getContainsInventoryName() + " (u)";
                break;
            default:
                Rs2Bank.depositAll(config.fletchingItem().getContainsInventoryName());
                Rs2Inventory.waitForInventoryChanges(5000);
                break;
        }

        // Check if the primary item is available
        if (!Rs2Bank.hasItem(primaryItemToFletch) && !Rs2Inventory.hasItem(primaryItemToFletch)) {
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + primaryItemToFletch + " not found in the bank.";
            Microbot.showMessage(Microbot.status);
            shutdown();
            return;
        }

        // Ensure the inventory isn't full without the primary item
        if (!Rs2Inventory.hasItem(primaryItemToFletch)) {
            Rs2Bank.depositAll();
        }

        // Withdraw the primary item if not already in the inventory
        if (!Rs2Inventory.hasItem(primaryItemToFletch)) {
            Rs2Bank.withdrawX(primaryItemToFletch, fletchingMode.getAmount(), true);
        }

        // Check if the secondary item is available
        if (!Rs2Bank.hasItem(secondaryItemToFletch)) {
            if (fletchingMode == FletchingMode.UNSTRUNG_STRUNG && Rs2Bank.hasBankItem("bow string")) {
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

        // Withdraw the secondary item if not already in the inventory
        if (!Rs2Inventory.hasItem(secondaryItemToFletch)) {
            if (fletchingMode == FletchingMode.STRUNG) {
                Rs2Bank.withdrawDeficit(secondaryItemToFletch, fletchingMode.getAmount());
            } else {
                Rs2Bank.withdrawAll(secondaryItemToFletch);
            }
        }
        if (Rs2AntibanSettings.naturalMouse) {
            // Testing if completing the mouse movement before the final item check improves the overall flow.
            // This should allow time for the inventory to update while the mouse is moving.
            // Enhances the bot's behavior to appear more natural and less automated.
            Widget closeButton = Rs2Widget.getWidget(786434).getChild(11);
            Point closePoint = Rs2UiHelper.getClickingPoint(closeButton != null ? closeButton.getBounds() : null, true);
            Rs2Random.waitEx(200, 100);
            Microbot.naturalMouse.moveTo(closePoint.getX(), closePoint.getY());
        }

        // Final check to ensure both items are in the inventory
        if (!Rs2Inventory.hasItem(primaryItemToFletch) || !Rs2Inventory.hasItem(secondaryItemToFletch)) {
            Microbot.log("waiting for inventory changes.");
            Rs2Inventory.waitForInventoryChanges(5000);
        }

        Rs2Random.waitEx(200, 100);
        Rs2Bank.closeBank();
    }


    private void fletch(FletchingConfig config) {
        Rs2Inventory.combineClosest(primaryItemToFletch, secondaryItemToFletch);
        sleepUntil(() -> Rs2Widget.getWidget(FLETCHING_WIDGET_GROUP_ID) != null, 5000);
        char option;
        if (fletchingMode == FletchingMode.PROGRESSIVE || fletchingMode == FletchingMode.PROGRESSIVE_STRUNG) {

            option = model.getFletchingItem().getOption(model.getFletchingMaterial(), fletchingMode);
            Rs2Keyboard.keyPress(option);
        } else {
            option = config.fletchingItem().getOption(config.fletchingMaterial(), fletchingMode);
            Rs2Keyboard.keyPress(option);
        }

        sleepUntil(() ->
                        !Rs2Inventory.hasItem(secondaryItemToFletch) ||
                        hasLeveledUp,
                60000);
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
        Rs2Bank.preHover();
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
        int level = Microbot.getClient().getRealSkillLevel(Skill.FLETCHING);
        FletchingItem item = null;
        FletchingMaterial material = null;



        if (fletchingMode == FletchingMode.PROGRESSIVE_STRUNG && level < 5) {
            Microbot.showMessage("Can't String Bows Below Level 5");
            shutdown();
            return;
        }
        if (level < 5) {
            item = FletchingItem.ARROW_SHAFT;
            material = FletchingMaterial.LOG;
        } else if (level < 10) {
            item = FletchingItem.SHORT;
            material = (fletchingMode == FletchingMode.PROGRESSIVE) ? FletchingMaterial.LOG : FletchingMaterial.WOOD;
        } else if (level < 20) {
            item = FletchingItem.LONG;
            material = (fletchingMode == FletchingMode.PROGRESSIVE) ? FletchingMaterial.LOG : FletchingMaterial.WOOD;
        } else if (level < 25) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.OAK;
        } else if (level < 35) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.OAK;
        } else if (level < 40) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.WILLOW;
        } else if (level < 50) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.WILLOW;
        } else if (level < 55) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.MAPLE;
        } else if (level < 65) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.MAPLE;
        } else if (level < 70) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.YEW;
        } else if (level < 80) {
            item = FletchingItem.LONG;
            material = FletchingMaterial.YEW;
        } else if (level < 85) {
            item = FletchingItem.SHORT;
            material = FletchingMaterial.MAGIC;
        } else {
            item = FletchingItem.LONG;
            material = FletchingMaterial.MAGIC;
        }

        model.setFletchingItem(item);
        model.setFletchingMaterial(material);
    }


    @Override
    public void shutdown() {

        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() == FLETCHING_WIDGET_GROUP_ID) {
            hasLeveledUp = false;
            Microbot.status = "Fletching";
            Microbot.showMessage(Microbot.status);
        }
    }
}
