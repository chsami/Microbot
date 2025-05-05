package net.runelite.client.plugins.microbot.TaF.RoyalTitans;

import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.apache.commons.lang3.tuple.Pair;

import java.awt.event.KeyEvent;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.TaF.RoyalTitans.RoyalTitansShared.*;
import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.EXTREME;
import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.disableAllPrayers;

public class RoyalTitansScript extends Script {

    public static String version = "1.0";

    private final Integer MELEE_TITAN_ICE_REGION_X = 34;
    private final Integer MELEE_TITAN_FIRE_REGION_X = 26;
    private final Integer ARENA_X_START = 28;
    private final Integer ARENA_X_END = 33;
    private final Integer FIRE_MINION_ID = 14150;
    private final Integer ICE_MINION_ID = 14151;
    private final Integer FIRE_WALL = 14152;
    private final Integer ICE_WALL = 14153;
    private final Integer TUNNEL_ID = 55986;
    private final Integer TUNNEL_ID_ESCAPE = 55987;
    private final Integer WIDGET_START_A_FIGHT = 14352385;
    private final WorldPoint BOSS_LOCATION = new WorldPoint(2951, 9574, 0);
    public RoyalTitansBotStatus state = RoyalTitansBotStatus.TRAVELLING;
    public volatile Tile enrageTile = null;
    public String subState = "";
    public int kills = 0;
    private Rs2InventorySetup inventorySetup = null;
    private Rs2InventorySetup magicInventorySetup = null;
    private Rs2InventorySetup meleeInventorySetup = null;
    private Rs2InventorySetup specialAttackInventorySetup = null;
    private Rs2InventorySetup rangedInventorySetup = null;
    private RoyalTitansTravelStatus travelStatus = RoyalTitansTravelStatus.TO_BANK;
    private Instant waitingTimeStart = null;
    private boolean waitedLastIteration = false;
    private boolean isRunning;

    {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = false;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.profileSwitching = false;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(EXTREME);
        kills = 0;
    }

    public boolean run(RoyalTitansConfig config) {
        isRunning = true;
        enrageTile = null;
        waitingTimeStart = null;
        travelStatus = RoyalTitansTravelStatus.TO_BANK;
        state = RoyalTitansBotStatus.TRAVELLING;
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!isRunning) return;
                if (!this.isRunning()) return;
                meleeInventorySetup = new Rs2InventorySetup(config.meleeEquipment(), mainScheduledFuture);
                magicInventorySetup = new Rs2InventorySetup(config.magicEquipment(), mainScheduledFuture);
                rangedInventorySetup = new Rs2InventorySetup(config.rangedEquipment(), mainScheduledFuture);
                specialAttackInventorySetup = new Rs2InventorySetup(config.specialAttackWeapon(), mainScheduledFuture);
                inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                switch (state) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case TRAVELLING:
                        handleTravelling(config);
                        break;
                    case WAITING:
                        handleWaiting(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                }


            } catch (Exception e) {
                e.printStackTrace();
                Microbot.log("Exception: " + e.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        scheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!isRunning) return;
                if (!this.isRunning()) return;
                detectState(config);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 10000, 10000, TimeUnit.MILLISECONDS);

        return true;
    }

    /**
     * Handles edgecases where the bot get stuck due to the other players actions
     * @param config
     */
    private void detectState(RoyalTitansConfig config) {
        if (RoyalTitansShared.isInBossRegion() && state != RoyalTitansBotStatus.FIGHTING) {
            Microbot.log("Boss region detected - But not in state FIGHTING, changing state");
            state = RoyalTitansBotStatus.FIGHTING;
        }
        if (state == RoyalTitansBotStatus.FIGHTING && !RoyalTitansShared.isInBossRegion()) {
            Microbot.log("Not in boss region - Changing state to TRAVELLING to instance");
            state = RoyalTitansBotStatus.TRAVELLING;
            travelStatus = RoyalTitansTravelStatus.TO_INSTANCE;
        }
    }

    private void equipArmor(Rs2InventorySetup inventorySetup) {
        if (!inventorySetup.doesEquipmentMatch()) {
            inventorySetup.wearEquipment();
        }
    }

    private void handleWaiting(RoyalTitansConfig config) {
        var teammate = Rs2Player.getPlayers(x -> Objects.equals(x.getName(), config.teammateName())).findFirst().orElse(null);
        if (waitingTimeStart == null && teammate == null && !waitedLastIteration) {
            waitingTimeStart = Instant.now();
            waitedLastIteration = true;
            return;
        }
        if (teammate != null) {
            if (teammate.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) < 5) {
                waitedLastIteration = false;
                waitingTimeStart = null;
                state = RoyalTitansBotStatus.TRAVELLING;
                travelStatus = RoyalTitansTravelStatus.TO_INSTANCE;
                evaluateAndConsumePotions(config);
                sleep(1200, 2400);
                return;
            }

        }

        if (waitingTimeStart != null && teammate == null && Instant.now().isAfter(waitingTimeStart.plusSeconds(config.waitingTimeForTeammate()))) {
            Microbot.log("Teammate did not show after " + config.waitingTimeForTeammate() + " seconds, shutting down");
            shutdown();
        }
    }

    private void handleFighting(RoyalTitansConfig config) {
        var wantedToEScape = handleEscaping(config);
        handleEating(config);
        handlePrayers(config);
        handleDangerousTiles();
        var handledMinions = handleMinions(config);
        if (handledMinions) {
            return;
        }
        var handledWalls = handleWalls(config);
        if (handledWalls) {
            return;
        }
        attackBoss(config);
    }

    private void handleEating(RoyalTitansConfig config) {
        subState = "Handling eating";
        var ate = Rs2Player.eatAt(config.minEatPercent());
        var ppot = Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
    }

    private boolean handleEscaping(RoyalTitansConfig config) {
        var shouldLeave = false;
        int currentHealth = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        boolean noFood = Rs2Inventory.getInventoryFood().isEmpty();
        boolean noPrayerPotions = Rs2Inventory.items().stream()
                .noneMatch(item -> item != null && item.getName() != null && !Rs2Potion.getPrayerPotionsVariants().contains(item.getName()));
        var teammate = Rs2Player.getPlayers(x -> Objects.equals(x.getName(), config.teammateName())).findFirst().orElse(null);
        if (teammate != null) {
            waitingTimeStart = null;
        }
        if (teammate == null && waitingTimeStart == null && config.resupplyWithTeammate()) {
            waitingTimeStart = Instant.now();
        } else if (config.resupplyWithTeammate() && teammate == null && Instant.now().isAfter(waitingTimeStart.plusSeconds(60))) { /*if teammate is gone for 60 seconds, we assume they went to resupply*/
            shouldLeave = true;
        }
        if ((noFood && currentHealth <= config.healthThreshold()) || (noPrayerPotions && currentPrayer < 10)) {
            shouldLeave = true;
        }
        var iceTitanDead = Rs2Npc.getNpcs(ICE_TITAN_DEAD_ID).findFirst().orElse(null);
        var fireTitanDead = Rs2Npc.getNpcs(FIRE_TITAN_DEAD_ID).findFirst().orElse(null);
        if (shouldLeave && iceTitanDead != null && fireTitanDead != null && !LootedTitanLastIteration) {
            Microbot.log("We want to escape, but Titans are dead, lets loot first");
        }
        if (shouldLeave) {
            if (config.emergencyTeleport() != 0) {
                enrageTile = null;
                Rs2Inventory.interact(config.emergencyTeleport(), config.emergencyTeleport() == 8013 ? "Outside" : "Break");
                Rs2Player.waitForAnimation(1200);
            } else {
                enrageTile = null;
                Rs2GameObject.interact(TUNNEL_ID_ESCAPE, "Quick-escape");
            }
            state = RoyalTitansBotStatus.TRAVELLING;
            travelStatus = RoyalTitansTravelStatus.TO_BANK;
            Rs2Prayer.disableAllPrayers();
            return true;
        }
        return false;
    }

    private void handlePrayers(RoyalTitansConfig config) {
        subState = "Handling prayers";
        handleOffensivePrayers(config);
        if (Rs2Combat.inCombat()) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            return;
        }
        if (Rs2Npc.getNpcs().anyMatch(x -> x.getId() == ICE_TITAN_ID || x.getId() == FIRE_TITAN_ID)) {
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            return;
        }
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
    }

    private void handleOffensivePrayers(RoyalTitansConfig config) {
        if (config.enableOffensivePrayer() && Rs2Player.isInCombat()) {
            if (meleeInventorySetup.doesEquipmentMatch()) {
                var bestMeleePrayer = Rs2Prayer.getBestMeleePrayer();
                if (bestMeleePrayer != null) {
                    Rs2Prayer.toggle(bestMeleePrayer, true);
                }
                return;
            }
            if (rangedInventorySetup.doesEquipmentMatch()) {
                var bestRangedPrayer = Rs2Prayer.getBestRangePrayer();
                if (bestRangedPrayer != null) {
                    Rs2Prayer.toggle(bestRangedPrayer, true);
                }
                return;
            }
            if (magicInventorySetup.doesEquipmentMatch()) {
                var bestMagicPrayer = Rs2Prayer.getBestMagePrayer();
                if (bestMagicPrayer != null) {
                    Rs2Prayer.toggle(bestMagicPrayer, true);
                }
            }
        }
    }

    private void attackBoss(RoyalTitansConfig config) {
        var iceTitan = Rs2Npc.getNpcs(ICE_TITAN_ID).findFirst().orElse(null);
        var fireTitan = Rs2Npc.getNpcs(FIRE_TITAN_ID).findFirst().orElse(null);
        if (iceTitan == null && fireTitan == null) {
            Microbot.log("No titans found");
            return;
        }
        LootedTitanLastIteration = false;
        handleBossFocus(config, iceTitan, fireTitan);
    }

    private void handleSpecialAttacks(RoyalTitansConfig config, Rs2NpcModel titan) {
        var specEnergy = Rs2Combat.getSpecEnergy() / 10;
        if (specEnergy < config.specEnergyConsumed()) {
            return;
        }
        if (titan == null || titan.isDead()) {
            return;
        }
        // Failsafe to handle special attack weapons that require to unequip 2 items
        if (Rs2Inventory.isFull()) {
            return;
        }
        if (enrageTile != null) {
            return;
        }


        // We assume that if we are currently wearing melee armor, it's okay to use a melee special attack weapon. This avoids all of the other targeting logic being duplicated
        if (meleeInventorySetup.doesEquipmentMatch() && config.specialAttackWeaponStyle() == RoyalTitansConfig.SpecialAttackWeaponStyle.MELEE) {
            specialAttackInventorySetup.wearEquipment();
            Rs2Combat.setSpecState(true, config.specEnergyConsumed() * 10);
            Rs2Npc.attack(titan);
            Rs2Player.waitForAnimation(600);
            return;
        }
        if ((magicInventorySetup.doesEquipmentMatch() || rangedInventorySetup.doesEquipmentMatch()) && config.specialAttackWeaponStyle() == RoyalTitansConfig.SpecialAttackWeaponStyle.RANGED) {
            specialAttackInventorySetup.wearEquipment();
            Rs2Combat.setSpecState(true, config.specEnergyConsumed() * 10);
            Rs2Npc.attack(titan);
            Rs2Player.waitForAnimation(600);
        }
    }

    private void handleBossFocus(RoyalTitansConfig config, Rs2NpcModel iceTitan, Rs2NpcModel fireTitan) {
        if (enrageTile != null) {
            subState = "Handling enrage tile";
            if (Rs2Player.getWorldLocation().equals(enrageTile.getWorldLocation())) {
                equipArmor(rangedInventorySetup);
            } else {
                Rs2Walker.walkFastCanvas(enrageTile.getWorldLocation());
                equipArmor(rangedInventorySetup);
            }

            if (fireTitan != null && !fireTitan.isDead()) {
                Rs2Npc.attack(fireTitan);
                handleSpecialAttacks(config, fireTitan);
                return;
            } else if (iceTitan != null) {
                Rs2Npc.attack(iceTitan);
                handleSpecialAttacks(config, iceTitan);
                return;
            }
        }
        // Both bosses alive - Handle focus
        if (config.royalTitanToFocus() == RoyalTitansConfig.RoyalTitan.FIRE_TITAN && fireTitan != null && !fireTitan.isDead()) {
            subState = "Attacking fire titan";
            int fireX = fireTitan.getWorldLocation().getRegionX();
            if (enrageTile == null && (fireX == MELEE_TITAN_FIRE_REGION_X ||
                    (fireX > MELEE_TITAN_FIRE_REGION_X && fireX < MELEE_TITAN_ICE_REGION_X))) {
                equipArmor(meleeInventorySetup);
            } else {
                equipArmor(rangedInventorySetup);
            }
            Rs2Npc.attack(fireTitan);
            handleSpecialAttacks(config, fireTitan);
            return;
        } else if (config.royalTitanToFocus() == RoyalTitansConfig.RoyalTitan.ICE_TITAN && iceTitan != null && !iceTitan.isDead()) {
            subState = "Attacking ice titan";
            int iceX = iceTitan.getWorldLocation().getRegionX();
            if (enrageTile == null && (iceX == MELEE_TITAN_ICE_REGION_X ||
                    (iceX > MELEE_TITAN_FIRE_REGION_X && iceX < MELEE_TITAN_ICE_REGION_X))) {
                equipArmor(meleeInventorySetup);
            } else {
                equipArmor(rangedInventorySetup);
            }
            // Don't try to attack a Titan if enragetile is active and we are wearing melee armor
            if (enrageTile != null && meleeInventorySetup.doesEquipmentMatch()) {
                return;
            }
            Rs2Npc.attack(iceTitan);
            handleSpecialAttacks(config, iceTitan);
            return;
        }
        // Only one boss alive
        if (iceTitan != null && !iceTitan.isDead()) {
            subState = "Only 1 boss alive, attacking ice titan";
            int iceX = iceTitan.getWorldLocation().getRegionX();
            if (enrageTile == null && (iceX == MELEE_TITAN_ICE_REGION_X ||
                    (iceX > MELEE_TITAN_FIRE_REGION_X && iceX < MELEE_TITAN_ICE_REGION_X))) {
                equipArmor(meleeInventorySetup);
            } else {
                equipArmor(rangedInventorySetup);
            }
            Rs2Npc.attack(iceTitan);
            handleSpecialAttacks(config, iceTitan);
            return;
        }
        if (fireTitan != null && !fireTitan.isDead()) {
            subState = "Only 1 boss alive, attacking fire titan";
            int fireX = fireTitan.getWorldLocation().getRegionX();
            if (enrageTile == null && (fireX == MELEE_TITAN_FIRE_REGION_X ||
                    (fireX > MELEE_TITAN_FIRE_REGION_X && fireX < MELEE_TITAN_ICE_REGION_X))) {
                equipArmor(meleeInventorySetup);
            } else {
                equipArmor(rangedInventorySetup);
            }
            Rs2Npc.attack(fireTitan);
            handleSpecialAttacks(config, fireTitan);
        }
    }

    private boolean handleWalls(RoyalTitansConfig config) {
        subState = "Handling walls";
        var walls = Rs2Npc.getNpcs(config.minionResponsibility() == RoyalTitansConfig.Minions.FIRE_MINIONS ? FIRE_WALL : ICE_WALL).collect(Collectors.toList());
        if (walls.isEmpty()) {
            return false;
        }
        if (walls.size() < 8) {
            return false;
        }
        equipArmor(magicInventorySetup);
        for (var wall : walls) {
            if (wall != null && wall.getId() != -1 && !wall.isDead()) {
                Rs2Npc.interact(wall, config.minionResponsibility() == RoyalTitansConfig.Minions.FIRE_MINIONS ? "Douse" : "Melt");
            }
        }

        return true;
    }

    private boolean handleMinions(RoyalTitansConfig config) {
        subState = "Handling minions";
        var minions = Rs2Npc.getNpcs(config.minionResponsibility() == RoyalTitansConfig.Minions.FIRE_MINIONS ? FIRE_MINION_ID : ICE_MINION_ID).collect(Collectors.toList());
        if (minions.isEmpty()) {
            return false;
        }
        equipArmor(magicInventorySetup);
        for (var minion : minions) {
            if (minion != null && !minion.isDead()) {
                Rs2Npc.attack(minion);
            }
        }
        return true;
    }

    private void handleDangerousTiles() {
        subState = "Handling dangerous tiles";
        if (enrageTile != null && !Rs2Player.getWorldLocation().equals(enrageTile.getWorldLocation())) {
            Rs2Walker.walkFastCanvas(enrageTile.getWorldLocation());
            return;
        }

        var dangerousGraphicsObjectTiles = Rs2Tile.getDangerousGraphicsObjectTiles().stream()
                .filter(x -> x.getValue() > 0)
                .collect(Collectors.toList());

        List<WorldPoint> dangerousWorldPoints = dangerousGraphicsObjectTiles
                .stream()
                .map(Pair::getKey)
                .collect(Collectors.toList());

        if (dangerousWorldPoints.isEmpty()) {
            return;
        }

        // Check if player is on OR adjacent to a dangerous tile
        boolean playerInDanger = dangerousWorldPoints.stream()
                .anyMatch(x -> x.equals(Rs2Player.getWorldLocation()) ||
                        x.distanceTo(Rs2Player.getWorldLocation()) <= 1);

        if (!playerInDanger) {
            return;
        }

        final WorldPoint safeTile = findSafeTile(Rs2Player.getWorldLocation(), dangerousWorldPoints);
        if (safeTile != null) {
            Rs2Walker.walkFastCanvas(safeTile);
            if (Rs2Player.getWorldLocation().equals(safeTile)) {
                Microbot.log("Successfully moved to safe tile: " + safeTile);
            } else {
                Microbot.log("Trying again to walk to safe tile: " + safeTile);
                Rs2Walker.walkFastCanvas(safeTile); // Try again
            }
        } else {
            Microbot.log("No safe tiles found nearby!");
        }
    }

    private WorldPoint findSafeTile(WorldPoint playerLocation, List<WorldPoint> dangerousWorldPoints) {
        Microbot.log("Finding safe tile");
        List<WorldPoint> nearbyTiles = List.of(
                new WorldPoint(playerLocation.getX(), playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() + 1, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() + 2, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() - 1, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX() - 2, playerLocation.getY(), playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() + 1, playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() + 2, playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() - 1, playerLocation.getPlane()),
                new WorldPoint(playerLocation.getX(), playerLocation.getY() - 2, playerLocation.getPlane())
        );

        for (WorldPoint tile : nearbyTiles) {
            // Tiles outside the arena returns true for isWalkable - Discard them
            if (tile.getRegionX() < ARENA_X_START || tile.getRegionX() > ARENA_X_END) {
                Microbot.log("Tile is outside the arena, skipping");
                continue;
            }
            if (!dangerousWorldPoints.contains(tile)) {
                Microbot.log("Found safe tile: " + tile);
                return tile;
            }
        }

        return null;
    }

    private void handleTravelling(RoyalTitansConfig config) {
        Rs2Prayer.disableAllPrayers();
        switch (travelStatus) {
            case TO_BANK:
                if (inventorySetup.doesInventoryMatch() && inventorySetup.doesEquipmentMatch()) {
                    state = RoyalTitansBotStatus.TRAVELLING;
                    travelStatus = RoyalTitansTravelStatus.TO_TITANS;
                    return;
                }
                subState = "Walking to bank";
                var isAtBank = Rs2Bank.walkToBank();
                if (isAtBank) {
                    state = RoyalTitansBotStatus.BANKING;
                }
                break;
            case TO_TITANS:
                subState = "Walking to titans";
                var gotToTitans = Rs2Walker.walkTo(BOSS_LOCATION, 1);
                if (gotToTitans) {
                    state = RoyalTitansBotStatus.WAITING;
                    travelStatus = RoyalTitansTravelStatus.TO_BANK;
                } else {
                    Rs2Walker.walkTo(BOSS_LOCATION, 1);
                }
                break;
            case TO_INSTANCE:
                subState = "Walking to instance";
                var isVisible = Rs2Widget.isWidgetVisible(WIDGET_START_A_FIGHT);
                if (isVisible) {
                    if (config.currentBotInstanceOwner()) {
                        sleep(600, 1200);
                        Rs2Widget.clickWidget("Start a fight (Your friends will be able to join you).");
                        sleep(600, 1200);
                        state = RoyalTitansBotStatus.FIGHTING;
                    } else {
                        var teammate = Rs2Player.getPlayers(x -> Objects.equals(x.getName(), config.teammateName())).findFirst().orElse(null);
                        if (teammate != null) {
                            Microbot.log("Waiting for teammate to enter the instance");
                            return;
                        }
                        Rs2Widget.clickWidget("Join a fight.");
                        sleep(1200, 1600);
                        Rs2Keyboard.typeString(config.teammateName());
                        sleep(600, 1200);
                        Rs2Keyboard.keyPress(KeyEvent.VK_ENTER);
                        sleep(600, 1200);
                        state = RoyalTitansBotStatus.FIGHTING;
                        sleep(1200, 1600);
                    }
                } else {
                    Rs2GameObject.interact(TUNNEL_ID, "Enter");
                }
                break;
        }
    }

    private void handleBanking(RoyalTitansConfig config) {
        subState = "Equipping gear";
        // Rs2Inventory setup is not happy if a full set of gear is not equipped TODO: Find workaround
        equipArmor(meleeInventorySetup);
        boolean equipmentLoaded;
        boolean inventoryLoaded;
        if (!inventorySetup.doesEquipmentMatch()) {
            inventorySetup.loadEquipment();
        }
        subState = "Loading inventory";
        if (!inventorySetup.doesInventoryMatch()) {
            inventorySetup.loadInventory();
        }
        equipmentLoaded = inventorySetup.doesEquipmentMatch();
        inventoryLoaded = inventorySetup.doesInventoryMatch();
        if (equipmentLoaded && inventoryLoaded) {
            var ate = false;
            boolean ateFood = false;
            boolean drankPrayerPot = false;
            while (Rs2Player.getHealthPercentage() < 70 || ateFood || drankPrayerPot) {
                ateFood = Rs2Player.eatAt(80);
                drankPrayerPot = Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
                ate = true;
                sleep(1200);
                if (!isRunning) {
                    break;
                }
            }
            if (ate) {
                inventorySetup.loadInventory();
            }
            Rs2Bank.closeBank();
            travelStatus = RoyalTitansTravelStatus.TO_TITANS;
            state = RoyalTitansBotStatus.TRAVELLING;
        } else {
            var items = inventorySetup.getEquipmentItems();
            var inventory = inventorySetup.getInventoryItems();
            for (var item : items) {
                if (item != null && item.getId() != -1) {
                    Rs2Bank.wearItem(item.getName(), true);
                }
            }
            Rs2Bank.depositAll();
            for (var item : inventory) {
                if (item != null && item.getId() != -1) {
                    Rs2Bank.withdrawX(item.getName(), item.getQuantity(), true);
                }
            }
            if (inventorySetup.doesInventoryMatch() && inventorySetup.doesEquipmentMatch()) {
                Rs2Bank.closeBank();
                travelStatus = RoyalTitansTravelStatus.TO_TITANS;
                state = RoyalTitansBotStatus.TRAVELLING;
            } else {
                Microbot.log("Failed to load inventory or equipment");
                shutdown();
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        isRunning = false;
        state = RoyalTitansBotStatus.BANKING;
        travelStatus = RoyalTitansTravelStatus.TO_BANK;
        enrageTile = null;
        kills = 0;
        disableAllPrayers();
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        Microbot.log("Shutting down Royal Titans script");
    }
}
