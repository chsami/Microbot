package net.runelite.client.plugins.microbot.plankrunner;

import com.google.inject.Inject;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.plankrunner.enums.PlankRunnerState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class PlankRunnerScript extends Script {

    public static PlankRunnerState state;
    private final PlankRunnerPlugin plugin;

    @Inject
    public PlankRunnerScript(PlankRunnerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
        Rs2Walker.disableTeleports = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (hasStateChanged()) {
                    state = updateState();
                }

                if (state == null) {
                    Microbot.showMessage("Unable to evaluate state");
                    shutdown();
                    return;
                }

                switch (state) {
                    case BANKING:
                        boolean isNearBank = Rs2Bank.isNearBank(plugin.getSawmillLocation().getBankLocation(), 15) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(plugin.getSawmillLocation().getBankLocation());
                        if (!isNearBank || !Rs2Bank.isOpen()) return;

                        if (Rs2Inventory.contains(plugin.getPlank().getPlankItemId())) {
                            Rs2Bank.depositAll(plugin.getPlank().getPlankItemId());
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }

                        if (Rs2Inventory.getEmptySlots() < 26) {
                            Rs2Bank.depositAll();
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }

                        if (!Rs2Inventory.contains(ItemID.COINS_995)) {
                            Rs2Bank.withdrawAll(ItemID.COINS_995);
                            Rs2Inventory.waitForInventoryChanges(1800);
                        }

                        if (plugin.isUseEnergyRestorePotions() && Rs2Player.getRunEnergy() <= plugin.getDrinkAtPercent()) {
                            boolean hasStaminaPotion = Rs2Bank.hasItem(Rs2Potion.getStaminaPotion());
                            boolean hasEnergyRestorePotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());
                            
                            if ((Rs2Player.hasStaminaBuffActive() && hasEnergyRestorePotion) || (!hasStaminaPotion && hasEnergyRestorePotion)) {
                                Rs2Item energyRestoreItem = Rs2Bank.bankItems().stream()
                                        .filter(rs2Item -> Rs2Potion.getRestoreEnergyPotionsVariants().stream()
                                                .anyMatch(variant -> rs2Item.getName().toLowerCase().contains(variant.toLowerCase())))
                                        .findFirst()
                                        .orElse(null);
                                
                                if (energyRestoreItem == null) {
                                    Microbot.showMessage("Unable to find Restore Energy Potion but hasItem?");
                                    shutdown();
                                    return;
                                }
                                
                                withdrawAndDrink(energyRestoreItem.getName());
                            } else if (hasStaminaPotion) {
                                Rs2Item staminaPotionItem = Rs2Bank.bankItems().stream()
                                        .filter(rs2Item -> rs2Item.getName().toLowerCase().contains(Rs2Potion.getStaminaPotion().toLowerCase()))
                                        .findFirst()
                                        .orElse(null);
                                
                                if (staminaPotionItem == null) {
                                    Microbot.showMessage("Unable to find Stamina Potion but hasItem?");
                                    shutdown();
                                    return;
                                }
                                
                                withdrawAndDrink(staminaPotionItem.getName());
                            } else {
                                Microbot.showMessage("Unable to find Stamina Potion OR Energy Restore Potions");
                                shutdown();
                                return;
                            }
                        }

                        int logsToWithdraw = Rs2Inventory.getEmptySlots();
                        if (!Rs2Bank.hasBankItem(plugin.getPlank().getLogItemId(), logsToWithdraw)) {
                            Microbot.showMessage("Not enough logs for a full run!");
                            shutdown();
                            return;
                        }
                        Rs2Bank.withdrawX(plugin.getPlank().getLogItemId(), logsToWithdraw);

                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                        break;
                    case RUNNING_TO_SAWMILL:
                        boolean isNearSawmill = Rs2Walker.getTotalTiles(plugin.getSawmillLocation().getWorldPoint()) < 15;
                        if (!isNearSawmill) {
                            Microbot.status = "Running to Sawmill";
                            Rs2Walker.walkTo(plugin.getSawmillLocation().getWorldPoint());
                            return;
                        }

                        NPC sawmillOperator = Rs2Npc.getNpc(NpcID.SAWMILL_OPERATOR);
                        if (sawmillOperator == null) {
                            Microbot.showMessage("Unable to find Sawmill Operator!");
                            shutdown();
                            return;
                        }

                        Rs2Npc.interact(sawmillOperator, "Buy-plank");
                        Microbot.status = "Buying Planks";
                        Rs2Dialogue.sleepUntilHasCombinationDialogue();
                        Rs2Dialogue.clickCombinationOption(plugin.getPlank().getDialogueOption());
                        sleepUntil(() -> Rs2Inventory.hasItem(plugin.getPlank().getPlankItemId()));
                        plugin.calculateProfit();
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private boolean hasStateChanged() {
        if (state == null) return true;
        if (hasRequiredItems()) return true;
        return !hasRequiredItems();
    }

    private PlankRunnerState updateState() {
        if (hasRequiredItems()) return PlankRunnerState.RUNNING_TO_SAWMILL;
        if (!hasRequiredItems()) return PlankRunnerState.BANKING;
        return null;
    }

    private boolean hasRequiredItems() {
        int logsInInventory = Rs2Inventory.items().stream()
                .filter(rs2Item -> rs2Item.getId() == plugin.getPlank().getLogItemId())
                .mapToInt(rs2Item -> 1)
                .sum();
        return Rs2Inventory.hasItem(plugin.getPlank().getLogItemId()) &&
                Rs2Inventory.hasItemAmount(ItemID.COINS_995, logsInInventory * plugin.getPlank().getCostPerPlank());
    }
    
    private void withdrawAndDrink(String potionItemName) {
        String simplifiedPotionName = potionItemName.replaceAll("\\s*\\(\\d+\\)", "").trim();
        Rs2Bank.withdrawOne(simplifiedPotionName);
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Inventory.interact(simplifiedPotionName, "drink");
        Rs2Inventory.waitForInventoryChanges(1800);
        if (Rs2Inventory.hasItem(simplifiedPotionName)) {
            Rs2Bank.depositOne(simplifiedPotionName);
            Rs2Inventory.waitForInventoryChanges(1800);
        }
    }
}
