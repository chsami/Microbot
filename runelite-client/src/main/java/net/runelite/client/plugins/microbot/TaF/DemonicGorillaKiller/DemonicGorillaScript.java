package net.runelite.client.plugins.microbot.TaF.DemonicGorillaKiller;

import net.runelite.api.HeadIcon;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLPlugin;
import net.runelite.client.plugins.microbot.qualityoflife.QoLScript;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.EXTREME;

// Script heavily inspired by Tormented Demon script
public class DemonicGorillaScript extends Script {

    public static final double VERSION = 1.1;
    public static final int DEMONIC_GORILLA_PRAYER_SWITCH = 7224;
    public static final int DEMONIC_GORILLA_MAGIC_ATTACK = 7225;
    public static final int DEMONIC_GORILLA_MELEE_ATTACK = 7226;
    public static final int DEMONIC_GORILLA_RANGED_ATTACK = 7227;
    public static final int DEMONIC_GORILLA_AOE_ATTACK = 7228;

    // Behind rope in the entrance of the cave
    private static final WorldPoint SAFE_LOCATION = new WorldPoint(2465, 3494, 0);
    private static final WorldPoint GORILLA_LOCATION = new WorldPoint(2100, 5643, 0);
    public static int killCount = 0;
    public static int currentTripKillCount = 0;
    public static Rs2PrayerEnum currentDefensivePrayer = null;
    public static Rs2NpcModel currentTarget = null;
    public static boolean lootAttempted = false;
    public static State BOT_STATUS = State.BANKING;
    public static TravelStep travelStep = TravelStep.GNOME_STRONGHOLD;
    public static int npcAnimationCount = 0;
    public static int lastAnimation = 0;
    public static int lastRealAnimation = 0;
    public static int lastAttackAnimation = 0;
    public static int gameTickCount = 0;
    public static boolean playerMoved;
    public static ArmorEquiped currentGear = ArmorEquiped.MELEE;
    public LocalPoint demonicGorillaRockPosition = null;
    public int demonicGorillaRockLifeCycle = -1;
    private Rs2PrayerEnum currentOffensivePrayer = null;
    private HeadIcon currentOverheadIcon = null;
    private String lastChatMessage = "";
    private BankingStep bankingStep = BankingStep.BANK;
    private Instant outOfCombatTime = Instant.now();
    // In some cases, the player may be stuck out of combat with an invalid target
    private int failedCount = 0;
    private int lastGameTick = 0;
    private WorldPoint lastGorillaLocation;
    private int failedAttacks = 0;
    private boolean isRunning = false;
    private Rs2InventorySetup rangeGear = null;
    private Rs2InventorySetup magicGear = null;
    private Rs2InventorySetup meleeGear = null;

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
        Rs2AntibanSettings.moveMouseOffScreen = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(EXTREME);
    }

    public boolean run(DemonicGorillaConfig config) {
        bankingStep = BankingStep.BANK;
        travelStep = TravelStep.GNOME_STRONGHOLD;
        isRunning = true;
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                preflightChecks(config);
                switch (BOT_STATUS) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case TRAVEL_TO_GORILLAS:
                        handleTravel(config);
                        break;
                    case FIGHTING:
                        handleFighting(config);
                        break;
                }
            } catch (Exception ex) {
                logOnceToChat("Error in main loop: " + ex.getMessage());
                System.out.println("Exception message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    private void preflightChecks(DemonicGorillaConfig config) {
        if (config.useMagicStyle() && config.magicGear() == null) {
            Microbot.showMessage("You've selected magic combatstyle, but your magic inventory setup is null. Please set it in the config. If you already have one selected. Select another inventory setup and then select your magic setup again.");
            shutdown();
            return;
        }
        if (config.useRangeStyle() && config.rangeGear() == null) {
            Microbot.showMessage("You've selected ranged combatstyle, but your range inventory setup is null. Please set it in the config. If you already have one selected. Select another inventory setup and then select your range setup again.");
            shutdown();
            return;
        }
        if (config.useMeleeStyle() && config.meleeGear() == null) {
            Microbot.showMessage("You've selected melee combatstyle, but your melee inventory setup is null. Please set it in the config. If you already have one selected. Select another inventory setup and then select your melee setup again.");
            shutdown();
            return;
        }
        if (config.gearSetup() == null) {
            Microbot.showMessage("Your banking gear setup is null. Please set it in the config. If you already have one selected. Select another inventory setup and then select your gear setup again.");
            shutdown();
            return;
        }
        rangeGear = new Rs2InventorySetup(config.rangeGear(), mainScheduledFuture);
        magicGear = new Rs2InventorySetup(config.magicGear(), mainScheduledFuture);
        meleeGear = new Rs2InventorySetup(config.meleeGear(), mainScheduledFuture);
        var isQoLEnabled = Microbot.getActiveScripts().stream().anyMatch(x -> x instanceof QoLScript);
        if (isQoLEnabled) {
            Microbot.log("QoL script interferes with banking when using inventory setups, disabling QoL.");
            Microbot.stopPlugin(QoLPlugin.class);
        }
    }

    private void handleTravel(DemonicGorillaConfig config) {
        if (Rs2Player.distanceTo(GORILLA_LOCATION) < 5) {
            BOT_STATUS = State.FIGHTING;
            return;
        }
        if (Rs2Walker.walkTo(GORILLA_LOCATION)) {
            BOT_STATUS = State.FIGHTING;
        }
    }

    private void handleBanking(DemonicGorillaConfig config) {
        Rs2InventorySetup inventorySetup = new Rs2InventorySetup(config.gearSetup(), mainScheduledFuture);
        switch (bankingStep) {
            case BANK:
                if (inventorySetup.doesInventoryMatch() && inventorySetup.doesEquipmentMatch()) {
                    bankingStep = BankingStep.BANK;
                    BOT_STATUS = State.TRAVEL_TO_GORILLAS;
                    return;
                }
                Rs2Bank.walkToBank();
                Microbot.status = "Opening bank...";
                Rs2Bank.openBank();
                sleepUntil(Rs2Bank::isOpen, 5000);
                bankingStep = BankingStep.LOAD_INVENTORY;
                break;

            case LOAD_INVENTORY:
                Microbot.status = "Loading inventory and equipment setup...";
                boolean equipmentLoaded = inventorySetup.loadEquipment();
                boolean inventoryLoaded = inventorySetup.loadInventory();

                if (equipmentLoaded && inventoryLoaded) {
                    var ate = false;
                    boolean ateFood = false;
                    boolean drankPrayerPot = false;
                    while (Rs2Player.getHealthPercentage() < 70 || ateFood || drankPrayerPot) {
                        ateFood = Rs2Player.eatAt(80);
                        drankPrayerPot = Rs2Player.drinkPrayerPotionAt(config.minEatPercent());
                        ate = true;
                        sleep(1200);
                        if (!isRunning || !this.isRunning()) {
                            break;
                        }
                        Microbot.log("Eating food or drinking prayer potion before going to gorillas.");
                        if (Rs2Player.getHealthPercentage() > 70) {
                            break;
                        }
                    }
                    if (ate) {
                        inventorySetup.loadInventory();
                    }
                    Rs2Bank.closeBank();
                    bankingStep = BankingStep.BANK;
                    BOT_STATUS = State.TRAVEL_TO_GORILLAS;
                } else {
                    shutdown();
                }
                break;
        }
    }

    private void handleFighting(DemonicGorillaConfig config) {
        if (currentTarget == null || currentTarget.isDead()) {
            npcAnimationCount = 0;
            logOnceToChat("Target is null or dead");
            handleNewTarget(config);
        }

        if (shouldRetreat(config)) {
            retreatToSafety(config);
            return;
        }
        handleTargetSelection();
        attackGorilla(config);
        if (currentTarget == null) return;
        handleDemonicGorillaAttacks(config);
        if (currentTarget == null) return;
        handleGearSwitching(config);
        if (currentTarget == null) return;
        evaluateAndConsumePotions(config);

        if (config.enableOffensivePrayer()) {
            activateOffensivePrayer(config);
        }
    }

    private void handleTargetSelection() {
        // Ensure currently selected target is also who we are attacking
        if (currentTarget != null) {
            var tempTarget = getTarget(true);
            if ((tempTarget != null && tempTarget.getIndex() != currentTarget.getIndex()) || currentTarget.isDead()) {
                logOnceToChat("Invalid target was selected, switching to correct enemy");
                currentTarget = tempTarget;
            }
        }
        if (!Rs2Player.isInCombat()) {
            if (outOfCombatTime == null) {
                outOfCombatTime = Instant.now();
            } else if (Instant.now().isAfter(outOfCombatTime.plusSeconds(6))) {
                logOnceToChat("Out of combat for 6 seconds, forcing new target");
                currentTarget = getTarget(true);
                if (currentTarget != null) {
                    Rs2Npc.attack(currentTarget);
                }
                else {
                    logOnceToChat("Unable to force new target, walking to gorillas and trying again");
                    Rs2Walker.walkTo(GORILLA_LOCATION);
                    currentTarget = getTarget(true);
                    // Last attempt, just attack a gorilla
                    if (currentTarget == null) {
                        Rs2Npc.attack("Demonic gorilla");
                    }
                }
                outOfCombatTime = null; // Reset after forcing new target
            }
        } else {
            outOfCombatTime = null;
        }
    }

    private void retreatToSafety(DemonicGorillaConfig config) {
        currentTarget = null;
        currentOverheadIcon = null;
        currentTripKillCount = 0;
		Microbot.pauseAllScripts.compareAndSet(false, true);
        escapeTosafety();
        sleepUntil(() -> Microbot.getClient().getLocalPlayer().getWorldLocation().equals(SAFE_LOCATION), 5000);
        disableAllPrayers();
        Microbot.pauseAllScripts.compareAndSet(true, false);
        BOT_STATUS = State.BANKING;
        Microbot.log("Changing to default gear");
        switchGear(config, HeadIcon.RANGED);
    }

    private void handleNewTarget(DemonicGorillaConfig config) {
        if (!lootAttempted) {
            Rs2Player.eatAt(80);
            Rs2Player.drinkPrayerPotionAt(config.minEatPercent());
            lootAttempted = true;
            if (currentTarget != null && currentTarget.isDead()) {
                killCount++;
                currentTripKillCount++;
            }
            currentTarget = null;
            lastGorillaLocation = null;
        }

        currentTarget = getTarget();
        if (currentTarget != null) {
            try {
                currentOverheadIcon = Rs2Reflection.getHeadIcon(currentTarget);
                if (currentOverheadIcon == null) {
                    logOnceToChat("Failed to retrieve HeadIcon for target - NULL");
                    return;
                }
            } catch (Exception e) {
                logOnceToChat("Failed to retrieve HeadIcon for target - Exception");
                return;
            }

            switchGear(config, currentOverheadIcon);
            lootAttempted = false;
        } else {
            logOnceToChat("No target found for attack.");
        }
    }

    private void attackGorilla(DemonicGorillaConfig config) {
        if (currentTarget != null && !currentTarget.isDead()) {
            Rs2Player.eatAt(config.minEatPercent());
            Rs2Player.drinkPrayerPotionAt(config.minPrayerPercent());
            if (currentTarget != null) {
                if (!Rs2Player.isAnimating(1600)) {
                    if (currentTarget != null && !currentTarget.isDead()) {
                        if (config.enableAutoSpecialAttacks()) {
                            Rs2Combat.setSpecState(true, 500);
                        }
                        var didWeAttack = Rs2Npc.attack(currentTarget);
                        if (didWeAttack) {
                            failedAttacks = 0;
                        } else {
                            failedAttacks++;
                            if (failedAttacks >= 7) {
                                currentTarget = getTarget(true);
                                failedAttacks = 0;
                            }
                        }
                    }
                }
            }
        } else {
            logOnceToChat("CurrentTarget is null or dead");
        }
    }

    private void handleDemonicGorillaAttacks(DemonicGorillaConfig config) {
        Rs2PrayerEnum newDefensivePrayer = null;
        boolean dodgedRock = false;

        if (currentTarget != null && !currentTarget.isDead()) {
            int currentAnimation = currentTarget.getAnimation();
            var location = currentTarget.getWorldLocation();
            if (lastGameTick != gameTickCount) {
                if (currentAnimation == DEMONIC_GORILLA_MAGIC_ATTACK || currentAnimation == DEMONIC_GORILLA_RANGED_ATTACK || currentAnimation == DEMONIC_GORILLA_MELEE_ATTACK) {
                    if (currentAnimation != lastRealAnimation) {
                        npcAnimationCount = 1;
                    } else {
                        npcAnimationCount++;
                    }
                    lastRealAnimation = currentAnimation;
                }
            }
            // Handle normal prayer switching
            if (currentAnimation == DEMONIC_GORILLA_MAGIC_ATTACK) {
                newDefensivePrayer = Rs2PrayerEnum.PROTECT_MAGIC;
            } else if (currentAnimation == DEMONIC_GORILLA_RANGED_ATTACK) {
                newDefensivePrayer = Rs2PrayerEnum.PROTECT_RANGE;
            } else if (currentAnimation == DEMONIC_GORILLA_MELEE_ATTACK) {
                newDefensivePrayer = Rs2PrayerEnum.PROTECT_MELEE;
            } else if (currentAnimation == DEMONIC_GORILLA_PRAYER_SWITCH) {
                handleGearSwitching(config);
            } else if (lastGorillaLocation != null && currentDefensivePrayer != Rs2PrayerEnum.PROTECT_MELEE) {
                var dist = lastGorillaLocation.distanceTo(location);
                if (dist > 0) {
                    newDefensivePrayer = Rs2PrayerEnum.PROTECT_MELEE;
                } else if (dist == 0 && npcAnimationCount == 3) {
                    if (currentDefensivePrayer == Rs2PrayerEnum.PROTECT_MAGIC) {
                        newDefensivePrayer = Rs2PrayerEnum.PROTECT_RANGE;
                    } else if (currentDefensivePrayer == Rs2PrayerEnum.PROTECT_RANGE) {
                        newDefensivePrayer = Rs2PrayerEnum.PROTECT_MAGIC;
                    }
                }
            }

            // Switch defensive prayer if needed
            if (newDefensivePrayer != null && newDefensivePrayer != currentDefensivePrayer && !Rs2Prayer.isPrayerActive(newDefensivePrayer)) {
                switchDefensivePrayer(newDefensivePrayer);
            }

            // Handle AOE attack
            if (currentAnimation == DEMONIC_GORILLA_AOE_ATTACK && demonicGorillaRockPosition != null) {
                List<WorldPoint> dangerousWorldPoints = new ArrayList<>(Rs2Tile.getDangerousGraphicsObjectTiles().keySet());
                dangerousWorldPoints.add(Microbot.getClient().getLocalPlayer().getWorldLocation());
                dangerousWorldPoints.add(currentTarget.getWorldLocation());
                dangerousWorldPoints.add(location);
                dangerousWorldPoints.addAll(DemonicGorillaPlugin.lastLocation.getAll());
                if (demonicGorillaRockPosition != null) {
                    dangerousWorldPoints.add(new WorldPoint(demonicGorillaRockPosition.getX(), demonicGorillaRockPosition.getY(), demonicGorillaRockPosition.getWorldView()));
                }
                final WorldPoint safeTile = findSafeTile(Rs2Player.getWorldLocation(), dangerousWorldPoints);
                if (safeTile != null) {
                    dodgedRock = Rs2Walker.walkFastCanvas(safeTile);
                    logOnceToChat("Walking to safe tile");
                    sleep(1600);
                }
                demonicGorillaRockPosition = null;
                demonicGorillaRockLifeCycle = -1;
            }
            if (!dodgedRock) {
                if ((currentGear == ArmorEquiped.RANGED || currentGear == ArmorEquiped.MAGIC) && (currentAnimation != DEMONIC_GORILLA_MELEE_ATTACK && currentAnimation != -1 && currentDefensivePrayer != Rs2PrayerEnum.PROTECT_MELEE)) {
                    var isMeleeDist = currentTarget.getWorldArea().isInMeleeDistance(Microbot.getClient().getLocalPlayer().getWorldArea());
                    if (isMeleeDist) {
                        moveAwayFromTarget();
                    }
                }
            }

            if (currentAnimation != -1) {
                lastAttackAnimation = currentAnimation;
            }
            lastAnimation = currentAnimation;
        } else {
            logOnceToChat("CurrentTarget is null or dead - HandleGorillaAttacks");
        }
        lastGorillaLocation = currentTarget.getWorldLocation();
        lastGameTick = gameTickCount;
    }

    private void handleGearSwitching(DemonicGorillaConfig config) {
        try {
            if (failedCount >= 3) {
                currentTarget = getTarget(true);
                logOnceToChat("Forcing new target");
            }
            HeadIcon newOverheadIcon = Rs2Reflection.getHeadIcon(currentTarget);
            if (newOverheadIcon == null) return;
            if (newOverheadIcon != currentOverheadIcon) {
                currentOverheadIcon = newOverheadIcon;
                switchGear(config, currentOverheadIcon);
                sleep(100);
                failedCount = 0;
            }
        } catch (Exception e) {
            logOnceToChat("Failed to retrieve HeadIcon for target.");
            failedCount++;
        }
    }

    public boolean moveAwayFromTarget() {
        if (currentTarget == null) {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint targetLocation = currentTarget.getWorldLocation();

        // Calculate the direction vector from the target to the player
        int directionX = playerLocation.getX() - targetLocation.getX();
        int directionY = playerLocation.getY() - targetLocation.getY();

        // Normalize the direction vector
        double length = Math.sqrt(directionX * directionX + directionY * directionY);
        double normalizedX = directionX / length;
        double normalizedY = directionY / length;

        // Calculate the movement vector (2 tiles backwards)
        int moveX = (int) Math.round(normalizedX * 2);
        int moveY = (int) Math.round(normalizedY * 2);

        // Calculate the new position
        WorldPoint newPosition = new WorldPoint(playerLocation.getX() - moveX, playerLocation.getY() - moveY, playerLocation.getPlane());

        // Check if the new position is walkable and not inside the target's location or the player's current location
        if (!Rs2Tile.isWalkable(newPosition) || newPosition.equals(targetLocation) || newPosition.equals(playerLocation)) {
            // Try other directions if the backward position is not walkable or inside the target's location or the player's current location
            List<WorldPoint> alternativePositions = List.of(
                    new WorldPoint(playerLocation.getX() + moveX, playerLocation.getY(), playerLocation.getPlane()),
                    new WorldPoint(playerLocation.getX() - moveX, playerLocation.getY(), playerLocation.getPlane()),
                    new WorldPoint(playerLocation.getX(), playerLocation.getY() + moveY, playerLocation.getPlane()),
                    new WorldPoint(playerLocation.getX(), playerLocation.getY() - moveY, playerLocation.getPlane())
            );

            for (WorldPoint alternativePosition : alternativePositions) {
                if (Rs2Tile.isWalkable(alternativePosition) && !alternativePosition.equals(targetLocation) && !alternativePosition.equals(playerLocation)) {
                    newPosition = alternativePosition;
                    break;
                }
            }
        }

        if (newPosition.equals(playerLocation) || newPosition.equals(targetLocation)) {
            return false;
        }

        // Move the player to the new position
        return Rs2Walker.walkFastCanvas(newPosition);
    }

    private void escapeTosafety() {
        Rs2Inventory.interact("Royal seed pod", "Commune");
    }

    public Rs2NpcModel getTarget() {
        return getTarget(false);
    }

    public Rs2NpcModel getTarget(boolean force) {
        if (currentTarget != null && !currentTarget.isDead() && !force) {
            return currentTarget;
        }
        var interacting = Rs2Player.getInteracting();
        if (interacting != null) {
            if (Objects.equals(interacting.getName(), "Demonic gorilla")) {
                return (Rs2NpcModel) interacting;
            }
        }
        var playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

        var alreadyInteractingNpcs = Rs2Npc.getNpcsForPlayer("Demonic gorilla");
        if (!alreadyInteractingNpcs.isEmpty()) {
            return alreadyInteractingNpcs.stream()
                    .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(playerLocation))).get();
        }

        var demonicGorillaStream = Rs2Npc.getNpcs("Demonic gorilla");
        if (demonicGorillaStream == null) {
            logOnceToChat("No demonic gorilla found.");
            return null;
        }

        var player = Rs2Player.getLocalPlayer();
        String playerName = player.getName();
        List<Rs2NpcModel> demonicGorillas = demonicGorillaStream.collect(Collectors.toList());

        for (Rs2NpcModel demonicGorilla : demonicGorillas) {
            if (demonicGorilla != null) {
                var interactingTwo = demonicGorilla.getInteracting();
                String interactingName = interactingTwo != null ? interactingTwo.getName() : "None";
                if (interactingTwo != null && Objects.equals(interactingName, playerName)) {
                    return demonicGorilla;
                }
            }
        }

        logOnceToChat("Finding closest demonic gorilla.");
        return demonicGorillas.stream()
                .filter(npc -> npc != null && !npc.isDead() && !npc.isInteracting())
                .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(playerLocation))).stream().findFirst()
                .orElse(null);
    }

    private WorldPoint findSafeTile(WorldPoint playerLocation, List<WorldPoint> dangerousWorldPoints) {
        List<WorldPoint> nearbyTiles = List.of(
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
            final LocalPoint location = LocalPoint.fromWorld(Microbot.getClient(), tile);
            if (!dangerousWorldPoints.contains(tile) && Rs2Tile.isWalkable(location)) {
                logOnceToChat("Found safe tile: " + tile);
                return tile;
            }
        }
        logOnceToChat("No safe tile found!");
        return null;
    }

    private void switchDefensivePrayer(Rs2PrayerEnum newDefensivePrayer) {
        if (currentDefensivePrayer != null) {
            Rs2Prayer.toggle(currentDefensivePrayer, false);
        }
        Rs2Prayer.toggle(newDefensivePrayer, true);
        currentDefensivePrayer = newDefensivePrayer;
    }

    private void activateOffensivePrayer(DemonicGorillaConfig config) {
        Rs2PrayerEnum newOffensivePrayer = null;

        if (config.useMagicStyle() && currentGear == ArmorEquiped.MAGIC) {
            newOffensivePrayer = Rs2Prayer.getBestMagePrayer();
        } else if (config.useRangeStyle() && currentGear == ArmorEquiped.RANGED) {
            newOffensivePrayer = Rs2Prayer.getBestRangePrayer();
        } else if (config.useMeleeStyle() && currentGear == ArmorEquiped.MELEE) {
            newOffensivePrayer = Rs2Prayer.getBestMeleePrayer();
        }

        if (newOffensivePrayer != null && newOffensivePrayer != currentOffensivePrayer) {
            switchOffensivePrayer(newOffensivePrayer);
        }
    }

    private void switchOffensivePrayer(Rs2PrayerEnum newOffensivePrayer) {
        if (currentOffensivePrayer != null) {
            Rs2Prayer.toggle(currentOffensivePrayer, false);
        }
        Rs2Prayer.toggle(newOffensivePrayer, true);
        currentOffensivePrayer = newOffensivePrayer;
    }

    private void switchGear(DemonicGorillaConfig config, HeadIcon combatNpcHeadIcon) {
        boolean useRange = config.useRangeStyle();
        boolean useMagic = config.useMagicStyle();
        boolean useMelee = config.useMeleeStyle();

        switch (combatNpcHeadIcon) {
            case RANGED:
                if (useMelee && useMagic) {
                    var randomizedChoice = Math.random() < 0.5;
                    currentGear = randomizedChoice ? ArmorEquiped.MELEE : ArmorEquiped.MAGIC;
                } else if (useMelee) {
                    currentGear = ArmorEquiped.MELEE;
                } else if (useMagic) {
                    currentGear = ArmorEquiped.MAGIC;
                }
                break;

            case MAGIC:
                if (useRange && useMelee) {
                    var randomizedChoice = Math.random() < 0.5;
                    currentGear = randomizedChoice ? ArmorEquiped.RANGED : ArmorEquiped.MELEE;
                } else if (useRange) {
                    currentGear = ArmorEquiped.RANGED;
                } else if (useMelee) {
                    currentGear = ArmorEquiped.MELEE;
                }
                break;

            case MELEE:
                if (useRange && useMagic) {
                    var randomizedChoice = Math.random() < 0.5;
                    currentGear = randomizedChoice ? ArmorEquiped.RANGED : ArmorEquiped.MAGIC;
                } else if (useRange) {
                    currentGear = ArmorEquiped.RANGED;
                } else if (useMagic) {
                    currentGear = ArmorEquiped.MAGIC;
                }
                break;
        }
        if (currentGear == ArmorEquiped.MELEE) {
            equipGear(meleeGear);
        } else if (currentGear == ArmorEquiped.MAGIC) {
            equipGear(magicGear);
        } else if (currentGear == ArmorEquiped.RANGED) {
            equipGear(rangeGear);
        }
    }

    private void equipGear(Rs2InventorySetup gear) {
        var success = gear.wearEquipment();
        if (!success && Rs2Inventory.isFull()) {
            logOnceToChat("Failed to equip gear - Inventory full");
            Rs2Player.useFood();
            Rs2Inventory.waitForInventoryChanges(1200);
            gear.wearEquipment();
        }
    }

    private boolean shouldRetreat(DemonicGorillaConfig config) {
        int currentHealth = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int currentPrayer = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        boolean noFood = Rs2Inventory.getInventoryFood().isEmpty();
        boolean noPrayerPotions = Rs2Inventory.items()
                .noneMatch(item -> item != null && item.getName() != null && !Rs2Potion.getPrayerPotionsVariants().contains(item.getName()));

        return (noFood && currentHealth <= config.healthThreshold()) || (noPrayerPotions && currentPrayer < 10);
    }

    public void disableAllPrayers() {
        Rs2Prayer.disableAllPrayers();
        currentDefensivePrayer = null;
        currentOffensivePrayer = null;
    }

    private void evaluateAndConsumePotions(DemonicGorillaConfig config) {
        int threshold = config.boostedStatsThreshold();

        if (!isCombatPotionActive(threshold)) {
            consumePotion(Rs2Potion.getCombatPotionsVariants());
        }

        if (!isRangingPotionActive(threshold)) {
            consumePotion(Rs2Potion.getRangePotionsVariants());
        }
    }

    private boolean isCombatPotionActive(int threshold) {
        return Rs2Player.hasDivineCombatActive() || (Rs2Player.hasAttackActive(threshold) && Rs2Player.hasStrengthActive(threshold));
    }

    private boolean isRangingPotionActive(int threshold) {
        return Rs2Player.hasRangingPotionActive(threshold) || Rs2Player.hasDivineBastionActive() || Rs2Player.hasDivineRangedActive();
    }

    private void consumePotion(List<String> keyword) {
        var potion = Rs2Inventory.get(keyword.toArray(String[]::new));
        if (potion != null) {
            Rs2Inventory.interact(potion, "Drink");
        }
    }

    void logOnceToChat(String message) {
        if (!message.equals(lastChatMessage)) {
            Microbot.log(message);
            lastChatMessage = message;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        BOT_STATUS = State.BANKING;
        isRunning = false;
        travelStep = TravelStep.GNOME_STRONGHOLD;
        bankingStep = BankingStep.BANK;
        currentTarget = null;
        killCount = 0;
        lootAttempted = false;  // Reset here
        currentDefensivePrayer = null;
        currentOffensivePrayer = null;
        currentOverheadIcon = null;
        rangeGear = null;
        magicGear = null;
        meleeGear = null;
        disableAllPrayers();
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        logOnceToChat("Shutting down Demonic Gorilla script");
    }

    public enum State {BANKING, TRAVEL_TO_GORILLAS, FIGHTING}

    public enum TravelStep {GNOME_STRONGHOLD, TRAVEL_TO_OPENING, CRASH_SITE, IN_CAVE, AT_GORILLAS}

    private enum BankingStep {BANK, LOAD_INVENTORY}

    public enum ArmorEquiped {MELEE, RANGED, MAGIC}
}
