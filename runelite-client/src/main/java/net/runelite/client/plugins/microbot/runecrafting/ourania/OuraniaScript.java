package net.runelite.client.plugins.microbot.runecrafting.ourania;

import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch.Pouch;
import net.runelite.client.plugins.microbot.runecrafting.ourania.enums.OuraniaState;
import net.runelite.client.plugins.microbot.runecrafting.ourania.enums.Path;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.RunePouchType;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class OuraniaScript extends Script {
    
    private final WorldArea ouraniaAltarArea = new WorldArea(new WorldPoint(3054, 5574, 0), 12, 12);
    private final OuraniaPlugin plugin;
    public static OuraniaState state;
    
    @Inject
    public OuraniaScript(OuraniaPlugin plugin) {
        this.plugin = plugin;
    }
    
    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.CRAFTING_RUNES_AT_OURANIA_ALTAR);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();
                
                if (!Rs2Magic.isLunar()) {
                    Microbot.showMessage("Not currently on Lunar Spellbook");
                    shutdown();
                    return;
                }
                
                if (Rs2Inventory.anyPouchUnknown()) {
                    Rs2Inventory.checkPouches();
                    return;
                }

                if (hasStateChanged()) {
                    state = updateState();
                }

                if (state == null) {
                    Microbot.showMessage("Unable to evaluate state");
                    shutdown();
                    return;
                }

                System.out.println("State: " + state.name());

                switch (state) {
                    case CRAFTING:
                        Rs2GameObject.interact(ObjectID.ALTAR_29631, "craft-rune");
                        Rs2Player.waitForXpDrop(Skill.RUNECRAFT, false);
                        if (Rs2Inventory.hasAnyPouch() && !Rs2Inventory.allPouchesEmpty()) {
                            Rs2Inventory.emptyPouches();
                            return;
                        }
                        break;
                    case RESETTING:
                        if (Rs2Player.getWorldLocation().distanceTo(new WorldPoint(2468, 3246, 0)) > 24) {
                            Rs2Magic.cast(MagicAction.OURANIA_TELEPORT);
                        }
                        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(new WorldPoint(2468, 3246, 0)) < 24);
                        Rs2Walker.walkTo(new WorldPoint(3014, 5625, 0));
                        break;
                    case BANKING:
                        if (plugin.isRanOutOfAutoPay()) {
                            Microbot.showMessage("You have ran out of auto-pay runes, check runepouch!");
                            shutdown();
                            return;
                        }
                        
                        if (Rs2Inventory.hasDegradedPouch() && Rs2Magic.hasRequiredRunes(Rs2Spells.NPC_CONTACT)) {
                            Rs2Magic.repairPouchesWithLunar();
                            return;
                        }
                        
                        if (!Rs2Bank.isOpen()) {
                            NPC eniola = Rs2Npc.getNpc(NpcID.ENIOLA);
                            if (eniola == null) return;
                            Rs2Npc.interact(eniola, "bank");
                            return;
                        }
                        
                        plugin.calcuateProfit();
                        
                        // Get all RunePouchType IDs
                        Integer[] runePouchIds = Arrays.stream(RunePouchType.values())
                                .map(RunePouchType::getItemId)
                                .toArray(Integer[]::new);

                        // Get all eligible pouch IDs based on Runecrafting level
                        Integer[] eligiblePouchIds = Arrays.stream(Pouch.values())
                                .filter(Pouch::hasRequiredRunecraftingLevel)
                                .flatMap(pouch -> Arrays.stream(pouch.getItemIds()).boxed())
                                .toArray(Integer[]::new);

                        // Combine RunePouchType IDs and eligible pouch IDs into a single array
                        Integer[] excludedIds = Stream.concat(Arrays.stream(runePouchIds), Arrays.stream(eligiblePouchIds))
                                .toArray(Integer[]::new);
                        
                        Rs2Bank.depositAllExcept(excludedIds);
                        Rs2Inventory.waitForInventoryChanges(1800);

                        if (plugin.isUseEnergyRestorePotions() && Rs2Player.getRunEnergy() <= plugin.getDrinkAtPercent()) {
                            boolean hasStaminaPotion = Rs2Bank.hasItem(Rs2Potion.getStaminaPotion());
                            boolean hasEnergyRestorePotion = Rs2Bank.hasItem(Rs2Potion.getRestoreEnergyPotionsVariants());

                            if ((Rs2Player.hasStaminaBuffActive() && hasEnergyRestorePotion) || (!hasStaminaPotion && hasEnergyRestorePotion)) {
                                Rs2ItemModel energyRestoreItem = Rs2Bank.bankItems().stream()
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
                                Rs2ItemModel staminaPotionItem = Rs2Bank.bankItems().stream()
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

                        if (Rs2Player.getHealthPercentage() <= plugin.getEatAtPercent()) {
                            while (Rs2Player.getHealthPercentage() < 100 && isRunning()) {
                                if (!Rs2Bank.hasItem(plugin.getRs2Food().getId())) {
                                    Microbot.showMessage("Missing Food in Bank!");
                                    shutdown();
                                    break;
                                }

                                Rs2Bank.withdrawOne(plugin.getRs2Food().getId());
                                Rs2Inventory.waitForInventoryChanges(1800);
                                Rs2Player.useFood();
                                sleepUntil(() -> !Rs2Inventory.hasItem(plugin.getRs2Food().getId()));
                            }

                            if (Rs2Inventory.hasItem(ItemID.JUG)) {
                                Rs2Bank.depositAll(ItemID.JUG);
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                        }
                        
                        int requiredEssence = Rs2Inventory.getEmptySlots() + Rs2Inventory.getRemainingCapacityInPouches();
                        
                        if (!Rs2Bank.hasBankItem(plugin.getEssence().getItemId(), requiredEssence)) {
                            Microbot.showMessage("Not enough essence to full run");
                            shutdown();
                            return;
                        }
                        
                        if (Rs2Inventory.hasAnyPouch()) {
                            while (!Rs2Inventory.allPouchesFull() && isRunning()) {
                                Rs2Bank.withdrawAll(plugin.getEssence().getItemId());
                                Rs2Inventory.fillPouches();
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                        }
                        
                        Rs2Bank.withdrawAll(plugin.getEssence().getItemId());
                        Rs2Inventory.waitForInventoryChanges(1800);
                        
                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                        break;
                    case RUNNING_TO_ALTAR:
                        Rs2Walker.walkTo(plugin.getPath().getWorldPoint());
                        if (plugin.getPath().equals(Path.LONG)) {
                            Rs2GameObject.interact(ObjectID.CRACK_29626, "squeeze-through");
                            sleepUntil(this::isNearAltar, 10000);
                        }
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                Microbot.log("Error in Ourania Altar Script: " + ex.getMessage());
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
        if (hasRequiredItems() && !isNearAltar()) return true;
        if (hasRequiredItems() && isNearAltar()) return true;
        if ((!hasRequiredItems() && isNearAltar()) || (!hasRequiredItems() && !isNearEniola())) return true;
        if (!hasRequiredItems() && isNearEniola()) return true;
        return false;
    }
    
    private OuraniaState updateState() {
        if (hasRequiredItems() && !isNearAltar()) return OuraniaState.RUNNING_TO_ALTAR;
        if (hasRequiredItems() && isNearAltar()) return OuraniaState.CRAFTING;
        if ((!hasRequiredItems() && isNearAltar()) || (!hasRequiredItems() && !isNearEniola()))  return OuraniaState.RESETTING;
        if (!hasRequiredItems() && isNearEniola()) return OuraniaState.BANKING;
        return null;
    }

    private boolean hasRequiredItems() {
        if (Rs2Inventory.hasAnyPouch()) {
            boolean pouchesContainEssence = !Rs2Inventory.allPouchesEmpty();
            boolean inventoryContainsEssence = Rs2Inventory.hasItem(plugin.getEssence().getItemId());
            return pouchesContainEssence || inventoryContainsEssence;
        } else {
            return Rs2Inventory.hasItem(plugin.getEssence().getItemId());
        }
    }
    
    private boolean isNearAltar() {
        return ouraniaAltarArea.contains(Rs2Player.getWorldLocation());
    }
    
    private boolean isNearEniola() {
        NPC eniola = Rs2Npc.getNpc(NpcID.ENIOLA);
        if (eniola == null) return false;
        return Rs2Player.getWorldLocation().distanceTo2D(eniola.getWorldLocation()) < 12;
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
