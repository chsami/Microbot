package net.runelite.client.plugins.microbot.TaF.TzhaarVenatorBow;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grandexchange.GrandExchangeSearchMode;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.TaF.TzhaarVenatorBow.TzhaarVenatorBowScript.TravelStatus.TO_BANK;
import static net.runelite.client.plugins.microbot.TaF.TzhaarVenatorBow.TzhaarVenatorBowScript.TravelStatus.TO_TZHAAR;
import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.*;

public class TzhaarVenatorBowScript extends Script {
    public static final String VERSION = "1.0";
    public static State BOT_STATUS = State.BANKING;
    public static int TotalLootValue = 0;
    private final WorldPoint COMBAT_LOCATION = new WorldPoint(2462, 5098, 0);
    private final List<String> VALID_NPCS = List.of("TzHaar-Ket", "TzHaar-Xil", "TzHaar-Hur");
    private final List<String> INVALID_NPCS = List.of("TzHaar-Mej");
    private final List<String> LOOT = List.of("Tzhaar-ket-em","Tzhaar-ket-om", "Toktz-ket-xil", "Obsidian cape", "Obsidian helmet", "Obsidian platebody", "Obsidian platelegs", "Onyx bolt tips", "Toktz-xil-ul", "Toktz-xil-ak", "Toktz-xil-ek", "Toktz-mej-tal");
    private boolean isRunning;
    private TravelStatus TRAVEL_STATUS = TO_BANK;

    {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(VERY_LOW);
    }

    public boolean run(TzHaarVenatorBowConfig config) {
        isRunning = true;
        BOT_STATUS = State.TRAVELLING;
        TRAVEL_STATUS = TO_BANK;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                switch (BOT_STATUS) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case TRAVELLING:
                        handleTravel(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Exception message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleFighting(TzHaarVenatorBowConfig config) {
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
        }
        Rs2Player.eatAt(config.minEatPercent());
        Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
        if (!isRangingPotionActive(config.boostedStatsThreshold())) {
            consumePotion(Rs2Potion.getRangePotionsVariants());
        }

        if (!Rs2Player.getWorldLocation().equals(COMBAT_LOCATION)) {
            var walkedFast = Rs2Walker.walkFastCanvas(COMBAT_LOCATION);
            if (!walkedFast) {
                Rs2Walker.walkTo(COMBAT_LOCATION);
            }
        }

        InitiateCombat();
        var isSafe = EnsureMagerSafety();
        if (isSafe) {
            Loot(config);
        }

        if (shouldRetreat(config)) {
            Microbot.log("Retreating due to low health or no food/prayer potions");
            BOT_STATUS = State.TRAVELLING;
            TRAVEL_STATUS = TO_BANK;
        }
    }

    private boolean shouldRetreat(TzHaarVenatorBowConfig config) {
        int currentHealth = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        var hasArrows = Rs2Equipment.hasEquippedSlot(EquipmentInventorySlot.AMMO);
        boolean shouldEscapeBasedOnFoodAndHealth = Rs2Inventory.getInventoryFood().isEmpty() && currentHealth < 50;
        boolean noPrayerPotions = Rs2Inventory.items().stream()
                .noneMatch(item -> item != null && item.getName() != null && item.getName().toLowerCase().contains("prayer potion"));

        return !hasArrows || shouldEscapeBasedOnFoodAndHealth || (noPrayerPotions && currentPrayer < 10);
    }

    private void Loot(TzHaarVenatorBowConfig config) {
        var nearbyItems = Rs2GroundItem.getAll(10);
        var itemsToPickup = Arrays.stream(nearbyItems).filter(item -> LOOT.contains(item.getItem().getName())).collect(Collectors.toList());
        if (config.teleGrabLoot()) {
            for (var item : itemsToPickup) {
                Rs2Magic.cast(MagicAction.TELEKINETIC_GRAB);
                Rs2GroundItem.interact(item);
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));
                var gePrice = Rs2GrandExchange.getPrice(item.getItem().getId());
                TotalLootValue += gePrice == -1 ? item.getItem().getPrice() * item.getTileItem().getQuantity() : gePrice * item.getTileItem().getQuantity();
            }
        } else {
            for (var item : itemsToPickup) {
                Rs2GroundItem.interact(item);
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));
                var gePrice = Rs2GrandExchange.getPrice(item.getItem().getId());
                TotalLootValue += gePrice == -1 ? item.getItem().getPrice() : gePrice;
            }

        }
    }

    private boolean EnsureMagerSafety() {
        var invalidNpcs = getInvalidNpcs();
        if (!invalidNpcs.isEmpty()) {
            Microbot.log("Under attack from mager, focusing it");
            Rs2Npc.attack(invalidNpcs.get(0));
            return false;
        }
        return true;
    }

    private void InitiateCombat() {
        if (!Rs2Combat.inCombat()) {
            var npcs = getValidNpcs();
            if (!npcs.isEmpty()) {
                // Collect the filtered NPCs into a list first
                var hursList = npcs.stream()
                        .filter(npc -> Objects.equals(npc.getName(), "TzHaar-Hur"))
                        .collect(Collectors.toList());

                if (!hursList.isEmpty()) {
                    // Attack the first TzHaar-Hur
                    Rs2Npc.attack(hursList.get(0));
                } else {
                    // Attack the first valid NPC
                    Rs2Npc.attack(npcs.get(0));
                }
            }
        }
    }

    private List<Rs2NpcModel> getValidNpcs() {
        return Rs2Npc.getAttackableNpcs(true)
                .filter(npc -> npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 6)
                .filter(npc -> VALID_NPCS.contains(npc.getName()))
                .filter(npc -> !INVALID_NPCS.contains(npc.getName())).collect(Collectors.toList());
    }

    private List<Rs2NpcModel> getInvalidNpcs() {
        return Rs2Npc.getNpcsForPlayer("TzHaar-Mej").stream()
                .filter(npc -> npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 15)
                .filter(npc -> !npc.isDead() && npc.isInteracting())
                .filter(npc -> Rs2Npc.hasLineOfSight(npc)).collect(Collectors.toList());
    }

    private void handleTravel(TzHaarVenatorBowConfig config) {
        switch (TRAVEL_STATUS) {
            case TO_BANK:
                Rs2Bank.walkToBank();
                sleepUntil(() -> Rs2Bank.isNearBank(5));
                BOT_STATUS = State.BANKING;
                break;
            case TO_TZHAAR:
                Rs2Walker.walkTo(COMBAT_LOCATION);
                if (Rs2Player.getWorldLocation().equals(COMBAT_LOCATION)) {
                    BOT_STATUS = State.FIGHTING;
                    TRAVEL_STATUS = TO_BANK;
                } else {
                    Rs2Walker.walkFastCanvas(COMBAT_LOCATION);
                    BOT_STATUS = State.FIGHTING;
                    TRAVEL_STATUS = TO_BANK;
                }
                break;
        }
    }

    private void handleBanking(TzHaarVenatorBowConfig config) {
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
        if (Rs2Bank.openBank()) {
            Rs2Bank.depositAll();
            if (config.teleGrabLoot()) {
                Rs2Bank.withdrawX("Law rune", 100);
                Rs2Bank.withdrawX("Air rune", 300);
            }
            Rs2Bank.withdrawX(config.foodToWithdraw().getId(), 2);
            if (config.withdrawRangePotsCount() > 0) {
                var potionType = Rs2Potion.getRangePotionsVariants();
                for (var potion : potionType) {
                    var extracted = Rs2Bank.withdrawX(potion + "(4)", config.withdrawRangePotsCount());
                    if (extracted) {
                        break;
                    }
                }
            }

            Rs2Bank.withdrawX("Prayer potion(4)", config.withdrawPrayerPotsCount() == 0 ? Rs2Inventory.getEmptySlots() - 2 : config.withdrawPrayerPotsCount());
            Rs2Bank.closeBank();
            if (Rs2Inventory.count("Prayer potion(4)") == 0) {
                Microbot.log("Out of prayer potions, stopping script.");
                shutdown();
            }
            BOT_STATUS = State.TRAVELLING;
            TRAVEL_STATUS = TO_TZHAAR;
        } else {
            Microbot.log("Failed to open bank, trying to get back to bank.");
            BOT_STATUS = State.TRAVELLING;
            TRAVEL_STATUS = TO_BANK;
        }
    }

    private boolean isRangingPotionActive(int threshold) {
        return Rs2Player.hasRangingPotionActive(threshold) || Rs2Player.hasDivineBastionActive() || Rs2Player.hasDivineRangedActive();
    }

    private void consumePotion(List<String> keyword) {
        var potion = Rs2Inventory.get(keyword);
        if (potion != null) {
            Rs2Inventory.interact(potion, "Drink");
        }
    }

    public enum State {BANKING, TRAVELLING, FIGHTING}

    public enum TravelStatus {TO_BANK, TO_TZHAAR}

}
