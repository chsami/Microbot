package net.runelite.client.plugins.microbot.TaF.DeadFallTrapHunter;

import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.hunter.HunterTrap;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DeadFallTrapHunterScript extends Script {
    public static final int SMALL_FISHING_NET = 303;
    public static final int ROPE = 954;
    public static int creaturesCaught = 0;
    private final int MAX_DEADFALL_TRAPS = 1;
    public boolean hasDied = false;
    public boolean forceBank = false;
    public boolean forceDrop = false;

    private static void WalkBackToHunterArea(DeadFallTrapHunterConfig config, DeadFallTrapHunterPlugin plugin) {
        if (!plugin.getTraps().isEmpty()) {
            var trap = plugin.getTraps().values().stream().findFirst().orElse(null);
            if (trap != null) {
                Rs2Walker.walkTo(trap.getWorldLocation());
            } else {
                Rs2Walker.walkTo(config.deadFallTrapHunting().getHuntingPoint());
            }
        } else {
            Rs2Walker.walkTo(config.deadFallTrapHunting().getHuntingPoint());
        }
    }

    public boolean run(DeadFallTrapHunterConfig config, DeadFallTrapHunterPlugin plugin) {
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_HUNTER);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!this.isRunning()) return;

                // Check if we have enough supplies
                if (forceBank) {
                    Microbot.log("Inventory too full - Banking");
                    handleBanking(config);
                }
                if (hasDied) {
                    Microbot.log("We died - Shutting down");
                    shutdown();
                }
                if ((Rs2Inventory.count("axe") < 1 && !Rs2Equipment.isWearing("axe")) && plugin.getTraps().isEmpty()) {
                    Microbot.log("No axe found - Banking");
                    handleBanking(config);
                    return;
                }
                if (!Rs2Inventory.hasItem("Knife")) {
                    Microbot.log("No knife found - Banking");
                    handleBanking(config);
                    return;
                }
                if (Rs2Inventory.isFull()) {
                    Microbot.log("Inventory is full - Banking");
                    handleBanking(config);
                }

                if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) <= config.runToBankHP()) {
                    handleBanking(config);
                }

                // Get selected creature type from config
                DeadFallTrapHunting deadFallTrapHunting = getDeadFallHunting(config);
                if (deadFallTrapHunting == null) {
                    return;
                }

                // Walk to area if not nearby
                if (!isNearArea(deadFallTrapHunting)) {
                    Microbot.log("Walking to " + deadFallTrapHunting.getName() + " hunting area");
                    Rs2Walker.walkTo(deadFallTrapHunting.getHuntingPoint());
                    return;
                }

                // Count existing traps from plugin's trap map
                int activeTrapCount = plugin.getTraps().size();

                // Tend to active traps
                boolean handledTrap = handleExistingTraps(plugin, config);
                if (handledTrap) return;

                // Set new traps if we don't have a trap - Make sure we're done with current woodcutting animation
                if (activeTrapCount < MAX_DEADFALL_TRAPS && Rs2Inventory.hasItem("logs")) {
                    if (!Rs2Player.isAnimating()) {
                        setNewTrap(deadFallTrapHunting, config);
                    }
                } else {
                    handleWoodCutting(config, plugin);
                }

            } catch (Exception ex) {
                System.out.println("DeadfallTrapHunter Script Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleWoodCutting(DeadFallTrapHunterConfig config, DeadFallTrapHunterPlugin plugin) {
        // We walked too far away, return to hunter area
        if (Rs2Player.isInCombat()) {
            Rs2Walker.walkTo(config.deadFallTrapHunting().getHuntingPoint());
            return;
        }

        // Stop woodcutting if we have enough logs
        if (Rs2Inventory.count("logs") > 5) {
            WalkBackToHunterArea(config, plugin);
            return;
        }

        // We're probably doing something else - don't interrupt
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        GameObject tree = Rs2GameObject.findReachableObject("tree", true, 20, config.deadFallTrapHunting().getHuntingPoint());
        if (tree == null) {
            tree = Rs2GameObject.findReachableObject("dead tree", true, 20, config.deadFallTrapHunting().getHuntingPoint());
        }
        if (tree != null) {
            if (Rs2GameObject.interact(tree, "Chop down")) {
                Rs2Player.waitForAnimation();
                Rs2Antiban.actionCooldown();
            }
        } else {
            Microbot.log("No nearby tree found - Walking back to origin");
            WalkBackToHunterArea(config, plugin);
        }
    }

    private DeadFallTrapHunting getDeadFallHunting(DeadFallTrapHunterConfig config) {
        if (config.progressiveHunting()) {
            return getBestDeadFallHuntingCreature();
        }

        return config.deadFallTrapHunting();
    }

    private DeadFallTrapHunting getBestDeadFallHuntingCreature() {
        var skillLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        if (skillLevel > 56) {
            return DeadFallTrapHunting.PYRE_FOX;
        } else if (skillLevel > 50) {
            return DeadFallTrapHunting.SABRE_TOOTHED_KEBBIT;
        } else if (skillLevel > 36) {
            return DeadFallTrapHunting.PRICKLY_KEBBIT;
        } else if (skillLevel > 32) {
            return DeadFallTrapHunting.BARBTAILED_KEBBIT;
        } else if (skillLevel > 22) {
            return DeadFallTrapHunting.WILD_KEBBIT;
        }
        Microbot.log("Not high enough hunter level for any deadfall creature");
        shutdown();
        return null;
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
    }

    private void handleBanking(DeadFallTrapHunterConfig config) {
        Rs2Bank.walkToBank();
        Rs2Bank.openBank();

        if (config.UseMeatPouch()) {
            if (!Rs2Inventory.hasItem(config.MeatPouch().getClosedItemID()) && !Rs2Inventory.hasItem(config.MeatPouch().getOpenItemID())) {
                Rs2Bank.withdrawOne(config.MeatPouch().getOpenItemID());
                sleep(600, 1200);
                Rs2Bank.withdrawOne(config.MeatPouch().getClosedItemID());
                sleep(600, 1200);
            }
            if (Rs2Inventory.hasItem(config.MeatPouch().getClosedItemID())) {
                Rs2Inventory.interact(config.MeatPouch().getClosedItemID(), "Open");
                sleep(400, 600);
            }
            if (Rs2Inventory.hasItem(config.MeatPouch().getOpenItemID())) {
                Rs2Inventory.interact(config.MeatPouch().getOpenItemID(), "Empty");
                sleep(400, 600);
            }
        }


        if (!Rs2Inventory.hasItem(ItemID.KNIFE)) {
            Rs2Bank.withdrawOne(ItemID.KNIFE);
        }

        getBestAxe(config);
        var loot = config.deadFallTrapHunting().getLootId();
        if (loot != null) {
            Rs2Bank.depositAll(loot);
        }
        sleep(50, 1200);

        // Auto-eat until full.
        if (config.AutoEat()) {
            while (!Rs2Player.isFullHealth()) {
                if (!isRunning()) {
                    break;
                }

                Rs2Bank.withdrawOne(config.FoodToEatAtBank().getId());
                sleep(200, 400);
                Rs2Inventory.interact(config.FoodToEatAtBank().getId(), "Eat");
                sleep(200, 400);
            }
        }

        if (Rs2Inventory.contains(config.FoodToEatAtBank().getId())) {
            Rs2Bank.depositAll(config.FoodToEatAtBank().getId());
        }

        // Close the bank
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
            sleep(600, 900);
        }
        hasDied = false;
        forceBank = false;
    }

    private void getBestAxe(DeadFallTrapHunterConfig config) {
        // Find the best axe available in bank, inventory, and equipment
        Axe bestAxeInInventory = getBestAxe(Rs2Inventory.items(), config);
        Axe bestAxeEquipped = getBestAxe(Rs2Equipment.items(), config);
        Axe bestAxeInBank = getBestAxe(Rs2Bank.bankItems(), config);

        // Determine the overall best axe
        Axe bestAxeOverall = null;
        if (bestAxeInInventory != null && (bestAxeOverall == null || bestAxeInInventory.getWoodcuttingLevel() > bestAxeOverall.getWoodcuttingLevel())) {
            bestAxeOverall = bestAxeInInventory;
        }
        if (bestAxeEquipped != null && (bestAxeOverall == null || bestAxeEquipped.getWoodcuttingLevel() > bestAxeOverall.getWoodcuttingLevel())) {
            bestAxeOverall = bestAxeEquipped;
        }
        if (bestAxeInBank != null && (bestAxeOverall == null || bestAxeInBank.getWoodcuttingLevel() > bestAxeOverall.getWoodcuttingLevel())) {
            bestAxeOverall = bestAxeInBank;
        }

        // Handle axe based on config preference
        if (bestAxeOverall != null) {
            // User wants axe in inventory
            if (config.axeInInventory()) {
                // If best axe is equipped, unequip it
                if (bestAxeOverall == bestAxeEquipped && bestAxeInInventory == null) {
                    Rs2Equipment.unEquip(bestAxeOverall.getItemID());
                    sleep(600, 800);
                }
                // If best axe is in bank and not in inventory, withdraw it
                else if (bestAxeOverall == bestAxeInBank && bestAxeInInventory == null) {
                    Rs2Bank.withdrawItem(bestAxeOverall.getItemID());
                    sleep(600, 800);
                }
            }
            // User wants axe equipped
            else {
                // If best axe is in bank and not equipped, withdraw and equip it
                if (bestAxeOverall == bestAxeInBank && bestAxeEquipped == null) {
                    Rs2Bank.withdrawItem(bestAxeOverall.getItemID());
                    sleep(600, 800);
                    Rs2Inventory.interact(bestAxeOverall.getItemID(), "Wield");
                    sleep(600, 800);
                }
                // If best axe is in inventory and not equipped, equip it
                else if (bestAxeOverall == bestAxeInInventory && bestAxeEquipped == null) {
                    Rs2Inventory.interact(bestAxeOverall.getItemID(), "Wield");
                }
            }
        } else {
            Microbot.log("No suitable axe found in bank, inventory, or equipment");
            shutdown();
        }
    }

    private Axe getBestAxe(List<Rs2ItemModel> items, DeadFallTrapHunterConfig config) {
        Axe bestPickaxe = null;

        for (Axe pickaxe : Axe.values()) {
            if (items.stream().noneMatch(i -> i.getName().toLowerCase().contains(pickaxe.getItemName()))) continue;
            if (pickaxe.hasRequirements(config.axeInInventory())) {
                if (bestPickaxe == null || pickaxe.getWoodcuttingLevel() > bestPickaxe.getWoodcuttingLevel()) {
                    bestPickaxe = pickaxe;
                }
            }
        }
        return bestPickaxe;
    }

    private boolean isNearArea(DeadFallTrapHunting deadFallTrapHunting) {
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        return currentLocation.distanceTo(deadFallTrapHunting.getHuntingPoint()) <= 50;
    }

    private boolean handleExistingTraps(DeadFallTrapHunterPlugin plugin, DeadFallTrapHunterConfig config) {
        // Filter for FULL traps and sort by time (traps about to collapse first) and then pick the first one
        var trapToHandle = plugin.getTraps().entrySet().stream()
                .filter(entry -> entry.getValue().getState() == HunterTrap.State.FULL)
                .sorted((a, b) -> Double.compare(b.getValue().getTrapTimeRelative(), a.getValue().getTrapTimeRelative())).collect(Collectors.toList()).stream().findFirst().orElse(null);
        if (trapToHandle == null) return false;
        WorldPoint location = trapToHandle.getKey();
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            var gameObject = Rs2GameObject.getGameObject(location);
            if (gameObject != null) {
                if (Rs2Inventory.size() > 24) {
                    forceDrop = true;
                    Rs2Inventory.waitForInventoryChanges(8000);
                }
                Rs2GameObject.interact(gameObject, "Check");
                creaturesCaught++;
                sleep(config.minSleepAfterCatch(), config.maxSleepAfterCatch());
                return true;
            }
        }
        return false;
    }

    private void setNewTrap(DeadFallTrapHunting deadFallTrapHunting, DeadFallTrapHunterConfig config) {
        if (Rs2GameObject.exists(deadFallTrapHunting.getTrapId())) {
            Rs2GameObject.interact(deadFallTrapHunting.getTrapId(), "Set-trap");
            sleep(config.minSleepAfterLay(), config.maxSleepAfterLay());
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        creaturesCaught = 0;
    }
}
