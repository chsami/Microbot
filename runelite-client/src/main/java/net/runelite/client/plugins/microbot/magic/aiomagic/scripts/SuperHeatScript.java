package net.runelite.client.plugins.microbot.magic.aiomagic.scripts;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.magic.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.MagicState;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.SuperHeatItem;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SuperHeatScript extends Script {

    private MagicState state;
    private final AIOMagicPlugin plugin;

    @Inject
    public SuperHeatScript(AIOMagicPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.SUPERHEATING);
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

                if (!plugin.getSuperHeatItem().hasRequiredLevel()) {
                    Microbot.showMessage("You do not have the required level for this item");
                    shutdown();
                    return;
                }

                switch (state) {
                    case BANKING:
                        boolean isBankOpen = Rs2Bank.isNearBank(15) ? Rs2Bank.useBank() : Rs2Bank.walkToBankAndUseBank();
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        Rs2Bank.depositAllExcept(ItemID.NATURE_RUNE);
                        Rs2Inventory.waitForInventoryChanges(1200);

                        List<Rs2Staff> staffList = Rs2Magic.findStavesByRunes(List.of(Runes.FIRE));

                        boolean hasFireStaffEquipped = staffList.stream()
                                .map(Rs2Staff::getItemID)
                                .anyMatch(Rs2Equipment::hasEquipped);

                        if (!hasFireStaffEquipped) {
                            Rs2ItemModel staffItem = Rs2Bank.bankItems().stream()
                                    .filter(rs2Item -> staffList.stream()
                                            .map(Rs2Staff::getItemID)
                                            .anyMatch(id -> id == rs2Item.getId()))
                                    .findFirst()
                                    .orElse(null);

                            if (staffItem == null) {
                                Microbot.showMessage("Unable to find staff");
                                shutdown();
                                return;
                            }

                            Rs2Bank.withdrawAndEquip(staffItem.getId());
                        }

                        if (!Rs2Inventory.hasItem(ItemID.NATURE_RUNE)) {
                            if (!Rs2Bank.hasItem(ItemID.NATURE_RUNE)) {
                                Microbot.showMessage("Nature Runes not found");
                                shutdown();
                                return;
                            }

                            Rs2Bank.withdrawAll(ItemID.NATURE_RUNE);
                            Rs2Inventory.waitForInventoryChanges(1200);
                        }

                        int[] requiredOreAndCoal = calculateOreAndCoal(plugin.getSuperHeatItem(), Rs2Inventory.getEmptySlots());
                        int requiredOre = requiredOreAndCoal[0];
                        int requiredCoal = requiredOreAndCoal[1];

                        if (!Rs2Bank.hasBankItem(plugin.getSuperHeatItem().getItemID(), requiredOre)) {
                            Microbot.showMessage("Missing Ore Requirement!");
                            shutdown();
                            return;
                        }

                        Rs2Bank.withdrawX(plugin.getSuperHeatItem().getItemID(), requiredOre);

                        if (requiredCoal > 0) {
                            if (!Rs2Bank.hasBankItem(ItemID.COAL, requiredCoal)) {
                                Microbot.showMessage("Missing Coal Requirement!");
                                shutdown();
                                return;
                            }

                            Rs2Bank.withdrawX(ItemID.COAL, requiredCoal);
                        }
                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                        break;
                    case CASTING:
                        if (!Rs2Inventory.hasItem(ItemID.NATURE_RUNE)) {
                            Microbot.showMessage("Nature Runes not found");
                            shutdown();
                            return;
                        }
                        Rs2Magic.superHeat(plugin.getSuperHeatItem().getItemID());
                        Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
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
        if (state == MagicState.BANKING && hasRequiredItems()) return true;
        if (state == MagicState.CASTING && !hasRequiredItems()) return true;
        return false;
    }

    private MagicState updateState() {
        if (state == null) return hasRequiredItems() ? MagicState.CASTING : MagicState.BANKING;
        if (state == MagicState.BANKING && hasRequiredItems()) return MagicState.CASTING;
        if (state == MagicState.CASTING && !hasRequiredItems()) return MagicState.BANKING;
        return null;
    }

    private boolean hasRequiredItems() {
        if (plugin.getSuperHeatItem().getCoalAmount() > 0) {
            return Rs2Inventory.hasItem(plugin.getSuperHeatItem().getItemID()) && Rs2Inventory.hasItemAmount(ItemID.COAL, plugin.getSuperHeatItem().getCoalAmount());
        }
        return Rs2Inventory.hasItem(plugin.getSuperHeatItem().getItemID());
    }

    /**
     * Determines the amount of ore and coal to withdraw based on available empty slots.
     *
     * @param superHeatItem The SuperHeatItem for which to calculate the withdrawal amounts.
     * @param emptySlots    The number of empty slots available in the inventory.
     * @return An array where the first element is the number of ore and the second element is the amount of coal.
     */
    public static int[] calculateOreAndCoal(SuperHeatItem superHeatItem, int emptySlots) {
        int coalAmount = superHeatItem.getCoalAmount();

        // If no coal is required, all slots are for the ore
        if (coalAmount == 0) {
            return new int[]{emptySlots, 0};
        }

        // Calculate the maximum number of ore that can fit given the coal requirement
        int maxOre = emptySlots / (coalAmount + 1); // 1 slot for ore + N slots for coal

        // Calculate the corresponding amount of coal
        int oreToWithdraw = maxOre;
        int coalToWithdraw = oreToWithdraw * coalAmount;

        return new int[]{oreToWithdraw, coalToWithdraw};
    }
}
