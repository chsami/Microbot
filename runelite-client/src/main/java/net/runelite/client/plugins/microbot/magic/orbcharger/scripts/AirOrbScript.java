package net.runelite.client.plugins.microbot.magic.orbcharger.scripts;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.magic.orbcharger.OrbChargerPlugin;
import net.runelite.client.plugins.microbot.magic.orbcharger.enums.OrbChargerState;
import net.runelite.client.plugins.microbot.magic.orbcharger.enums.Teleport;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AirOrbScript extends Script {

    private final OrbChargerPlugin plugin;
    public OrbChargerState state;

    public boolean hasDied = false;
    private int unpoweredOrbAmount = 0;
    private int cosmicRuneAmount = 0;
    private boolean shouldFlee = false;

    @Inject
    public AirOrbScript(OrbChargerPlugin plugin) {
        this.plugin = plugin;
    }

    private final WorldPoint airObelisk = new WorldPoint(3088, 3568, 0);
    private final WorldPoint outsideOfWilderness = new WorldPoint(3131, 9910, 0);
    private final int[] airStaves = {
            ItemID.STAFF_OF_AIR,
            ItemID.AIR_BATTLESTAFF,
            ItemID.MYSTIC_AIR_STAFF,
            ItemID.DUST_BATTLESTAFF,
    };

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2Antiban.setActivity(Activity.CHARGING_AIR_ORBS);
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

                if (shouldFlee) return;

                switch (state) {
                    case BANKING:
                        if (!Rs2Bank.isOpen()) return;
                        
                        boolean hasAirStaffEquipped = Arrays.stream(airStaves).anyMatch(Rs2Equipment::isWearing);

                        if (!hasAirStaffEquipped) {
                            List<Rs2ItemModel> filteredAirStaves = Rs2Bank.bankItems().stream()
                                    .filter(item -> Arrays.stream(airStaves).anyMatch(id -> item.getId() == id))
                                    .collect(Collectors.toList());

                            if (filteredAirStaves.isEmpty()) {
                                Microbot.showMessage("Missing Air Staff in Bank!");
                                shutdown();
                                return;
                            }

                            Rs2Bank.withdrawOne(filteredAirStaves.get(0).getId());
                            Rs2Inventory.waitForInventoryChanges(1200);
                            Rs2Inventory.equip(filteredAirStaves.get(0).getId());
                            Rs2Inventory.waitForInventoryChanges(1200);
                        }

                        handleReplaceTeleportItem();

                        if (!hasRequiredItems()) {
                            List<String> importantPotionNames = new ArrayList<>(Rs2Potion.getRestoreEnergyPotionsVariants());
                            importantPotionNames.add(Rs2Potion.getStaminaPotion());

                            List<Rs2ItemModel> filteredPotions = Rs2Inventory.getFilteredPotionItemsInInventory(importantPotionNames);
                            List<String> importantItemNames = filteredPotions.stream()
                                    .map(Rs2ItemModel::getName)
                                    .collect(Collectors.toList());
                            
                            Rs2ItemModel cosmicRune = Rs2Inventory.get(ItemID.COSMIC_RUNE);
                            Rs2ItemModel unpoweredOrb = Rs2Inventory.get(ItemID.UNPOWERED_ORB);

                            if (cosmicRune != null) {
                                importantItemNames.add(cosmicRune.getName());
                            }
                            if (unpoweredOrb != null) {
                                importantItemNames.add(unpoweredOrb.getName());
                            }

                            if (Rs2Bank.depositAllExcept(importantItemNames)) {
                                Rs2Inventory.waitForInventoryChanges(1200);
                            }
                        }

                        if (Rs2Player.getHealthPercentage() <= plugin.getEatAtPercent()) {
                            while (Rs2Player.getHealthPercentage() < 100 && isRunning()) {
                                if (!Rs2Bank.hasItem(plugin.getRs2Food().getId())) {
                                    Microbot.showMessage("Missing Food in Bank!");
                                    shutdown();
                                    break;
                                }
                                
                                if (Rs2Inventory.isFull()) {
                                    Rs2Bank.depositOne(ItemID.UNPOWERED_ORB);
                                    Rs2Inventory.waitForInventoryChanges(1200);
                                }

                                Rs2Bank.withdrawOne(plugin.getRs2Food().getId());
                                Rs2Inventory.waitForInventoryChanges(1200);
                                Rs2Player.useFood();
                                sleep(1000);
                            }

                            if (Rs2Inventory.hasItem(ItemID.JUG)) {
                                Rs2Bank.depositAll(ItemID.JUG);
                                Rs2Inventory.waitForInventoryChanges(1200);
                            }
                        }

                        if (plugin.isUseEnergyPotions()) {
                            if (!Rs2Inventory.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants())) {
                                if (!Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants())) {
                                    Microbot.showMessage("Missing Energy Restore Potions");
                                    shutdown();
                                    return;
                                }

                                Rs2ItemModel energyRestoreItem = Rs2Bank.bankItems()
                                        .stream()
                                        .filter(item -> Rs2Potion.getRestoreEnergyPotionsVariants().stream()
                                                .anyMatch(potion -> item.name.contains(potion)))
                                        .findFirst()
                                        .orElse(null);

                                if (energyRestoreItem == null) {
                                    Microbot.showMessage("Unable to find Energy Restore Potions");
                                    shutdown();
                                    return;
                                }

                                Rs2Bank.withdrawOne(energyRestoreItem.getId());
                                Rs2Inventory.waitForInventoryChanges(1200);
                            }
                        }

                        if (plugin.isUseStaminaPotions()) {
                            if (!Rs2Inventory.hasItem(Rs2Potion.getStaminaPotion())) {
                                if (!Rs2Bank.hasItem(Rs2Potion.getStaminaPotion())) {
                                    Microbot.showMessage("Missing Stamina Potions");
                                    shutdown();
                                    return;
                                }

                                Rs2Bank.withdrawOne(Rs2Potion.getStaminaPotion());
                                Rs2Inventory.waitForInventoryChanges(1200);
                            }
                        }

                        unpoweredOrbAmount = Rs2Inventory.getEmptySlots() - 1;
                        cosmicRuneAmount = unpoweredOrbAmount * 3;

                        int currentCosmicRunes = Rs2Inventory.itemQuantity(ItemID.COSMIC_RUNE);
                        int currentUnpoweredOrbs = Rs2Inventory.itemQuantity(ItemID.UNPOWERED_ORB);

                        int neededCosmicRunes = Math.max(0, cosmicRuneAmount - currentCosmicRunes);
                        int neededUnpoweredOrbs = Math.max(0, unpoweredOrbAmount - currentUnpoweredOrbs);

                        if (neededCosmicRunes > 0) {
                            if (!Rs2Bank.hasBankItem(ItemID.COSMIC_RUNE, neededCosmicRunes)) {
                                Microbot.showMessage("Missing required cosmic runes in bank.");
                                shutdown();
                                return;
                            }
                            Rs2Bank.withdrawX(ItemID.COSMIC_RUNE, neededCosmicRunes);
                            Rs2Inventory.waitForInventoryChanges(1200);
                        }

                        if (neededUnpoweredOrbs > 0) {
                            if (!Rs2Bank.hasBankItem(ItemID.UNPOWERED_ORB, neededUnpoweredOrbs)) {
                                Microbot.showMessage("Missing required unpowered orbs in bank.");
                                shutdown();
                                return;
                            }
                            Rs2Bank.withdrawX(ItemID.UNPOWERED_ORB, neededUnpoweredOrbs);
                            Rs2Inventory.waitForInventoryChanges(1200);
                        }

                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                        break;
                    case CHARGING:
                        Rs2Walker.setTarget(null);

                        shouldFlee = !plugin.getDangerousPlayers().isEmpty();
                        if (shouldFlee) {
                            break;
                        }

                        Rs2Magic.cast(MagicAction.CHARGE_AIR_ORB);
                        Rs2GameObject.interact(ObjectID.OBELISK_OF_AIR);
                        Rs2Dialogue.sleepUntilHasCombinationDialogue();
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        sleepUntil(() -> Rs2Player.isAnimating(1200), 5000);
                        Rs2Tab.switchToInventoryTab();

                        sleepUntil(() -> !Rs2Player.isAnimating(5000) || !Rs2Inventory.hasItem(ItemID.UNPOWERED_ORB) || shouldFlee, () -> shouldFlee = !plugin.getDangerousPlayers().isEmpty(), 96000, 1000);
                        break;
                    case DRINKING:
                        Rs2GameObject.interact(ObjectID.POOL_OF_REFRESHMENT);
                        sleepUntil(() -> Rs2Player.getRunEnergy() == 100 && !Rs2Player.isAnimating(2000));
                        break;
                    case WALKING:
                        shouldFlee = !plugin.getDangerousPlayers().isEmpty();
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println("Error in AirOrbScript: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void handleWalk() {
        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (shouldContinueWalking()) {
                    Rs2Walker.walkTo(airObelisk, 2);
                } else if (shouldHandleBankingOrFleeing()) {
                    handleBankingOrFleeing();
                }
            } catch (Exception ex) {
                System.out.println("Error in handleWalk: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private boolean hasStateChanged() {
        if (shouldBank()) return true;
        if (shouldDrinkFromPool()) return true;
        if (shouldCharge()) return true;
        if (hasRequiredItems()) return true;
        return false;
    }

    private OrbChargerState updateState() {
        if (shouldBank()) return OrbChargerState.BANKING;
        if (shouldDrinkFromPool()) return OrbChargerState.DRINKING;
        if (shouldCharge()) return OrbChargerState.CHARGING;
        if (hasRequiredItems()) return OrbChargerState.WALKING;
        return null;
    }

    private boolean shouldContinueWalking() {
        return (plugin.getTeleport() != Teleport.RING_OF_DUELING) ? !shouldBank() && !shouldCharge() && hasRequiredItems() : !shouldBank() && !shouldCharge() && !shouldDrinkFromPool() && hasRequiredItems();
    }

    private boolean shouldHandleBankingOrFleeing() {
        if (hasDied || shouldFlee) {
            return true;
        }

        if (shouldBank() && !isTeleportItemEquipped()) {
            Microbot.log("Banking needed due to missing teleport item.");
            return true;
        }

        if (shouldBank() && !hasRequiredItems()) {
            Microbot.log("Banking needed due to missing required items.");
            return true;
        }

        return false;
    }

    private void handleBankingOrFleeing() {
        if (hasDied) {
            handleDeathReset();
        }

        if (shouldFlee) {
            handleFleeing();
        } else {
            Rs2Bank.walkToBankAndUseBank(plugin.getTeleport().getBankLocation());
        }
    }

    private void handleDeathReset() {
        Rs2Walker.setTarget(null);
        hasDied = false;
    }

    private void handleFleeing() {
        if (!Rs2Player.isInCombat() && !Rs2Player.isTeleBlocked()) {
            if (!Rs2Bank.isNearBank(plugin.getTeleport().getBankLocation(), 8)) {
                Rs2Bank.walkToBank(plugin.getTeleport().getBankLocation());
            } else {
                handleLogout();
            }
        } else {
            Rs2Walker.walkTo(outsideOfWilderness, 2);
            if (Rs2Player.getWorldLocation().distanceTo(outsideOfWilderness) <= 2 && !Rs2Player.isMoving()) {
                if (Rs2Player.isInCombat()) {
                    sleepUntil(() -> !Rs2Player.isInCombat(), 12000);
                }
                handleLogout();
            }
        }
    }

    private void handleLogout() {
        if (Rs2Player.isInCombat() || Rs2Player.isTeleBlocked()) return;
        Rs2Player.logout();
        sleepUntil(() -> Microbot.getClient().getGameState() != GameState.LOGGED_IN, 5000);

        if (Microbot.getClient().getGameState() == GameState.LOGGED_IN) {
            Microbot.log("Logout unsuccessful, retrying logout.");
            System.out.println("Logout unsuccessful, retrying logout.");
            return;
        }
        new Login(Login.getRandomWorld(Login.activeProfile.isMember()));
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 10000);
        shouldFlee = false;
    }

    private boolean shouldBank() {
        return !hasRequiredItems() || !isTeleportItemEquipped();
    }

    private boolean isTeleportItemEquipped() {
        final Pattern regexPattern = Pattern.compile(plugin.getTeleport().getRegexPattern());
        switch (plugin.getTeleport()) {
            case AMULET_OF_GLORY:
                Rs2ItemModel equippedAmulet = Rs2Equipment.get(EquipmentInventorySlot.AMULET);
                if (equippedAmulet == null) return false;
                return regexPattern.matcher(equippedAmulet.getName()).matches();
            case RING_OF_DUELING:
                Rs2ItemModel equippedRing = Rs2Equipment.get(EquipmentInventorySlot.RING);
                if (equippedRing == null) return false;
                return regexPattern.matcher(equippedRing.getName()).matches();
            default:
                return false;
        }
    }

    private boolean hasRequiredItems() {
        boolean hasOrbs = Rs2Inventory.hasItem(ItemID.UNPOWERED_ORB);
        boolean hasRunes = Rs2Inventory.hasItem(ItemID.COSMIC_RUNE);

        return hasOrbs && hasRunes;
    }

    private boolean shouldCharge() {
        return Rs2Player.getWorldLocation().distanceTo(airObelisk) <= 3 || Rs2GameObject.exists(ObjectID.OBELISK_OF_AIR);
    }

    private boolean shouldDrinkFromPool() {
        if (plugin.getTeleport() != Teleport.RING_OF_DUELING) return false;
        if (Rs2Player.getRunEnergy() > 80) return false;
        if (!hasRequiredItems()) return false;
        if (!Rs2GameObject.exists(ObjectID.POOL_OF_REFRESHMENT)) return false;
        
        TileObject refreshmentPool = Rs2GameObject.findObjectById(ObjectID.POOL_OF_REFRESHMENT);
        if (refreshmentPool == null) return false;
        
        return Rs2Player.getWorldLocation().distanceTo(refreshmentPool.getWorldLocation()) < 8;
    }

    private void handleReplaceTeleportItem() {
        if (isTeleportItemEquipped()) return;

        final Pattern regexPattern = Pattern.compile(plugin.getTeleport().getRegexPattern());
        Rs2ItemModel teleportItem = Rs2Bank.bankItems().stream()
                .filter(item -> regexPattern.matcher(item.getName()).matches())
                .findFirst()
                .orElse(null);

        if (teleportItem == null) {
            Microbot.showMessage("Missing required teleport item in bank!");
            shutdown();
            return;
        }

        if (Rs2Inventory.isFull()) {
            // Deposit one unpowered orb if inventory is full
            if (Rs2Inventory.hasItem(ItemID.UNPOWERED_ORB)) {
                Rs2Bank.depositOne(ItemID.UNPOWERED_ORB);
                Rs2Inventory.waitForInventoryChanges(1200);
            } else {
                Microbot.showMessage("Inventory full, but no unpowered orbs to deposit!");
                shutdown();
                return;
            }
        }

        Rs2Bank.withdrawOne(teleportItem.getId());
        Rs2Inventory.waitForInventoryChanges(1200);
        Rs2Inventory.equip(teleportItem.getId());
        Rs2Inventory.waitForInventoryChanges(1200);
        if (plugin.getTeleport() == Teleport.AMULET_OF_GLORY) {
            Rs2Bank.depositOne(ItemID.AMULET_OF_GLORY);
            Rs2Inventory.waitForInventoryChanges(1200);
        }
    }
}
