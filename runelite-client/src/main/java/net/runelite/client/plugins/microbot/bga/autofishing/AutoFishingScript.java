package net.runelite.client.plugins.microbot.bga.autofishing;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.worldmap.FishingSpotLocation;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoFishingScript extends Script {

    public static String version = "1.0";
    
    private AutoFishingState state = AutoFishingState.INITIALIZING;
    private AutoFishingConfig config;
    private Fish selectedFish;
    private long stateStartTime = System.currentTimeMillis(); // track state timeout
    @Getter
    private HarpoonType selectedHarpoon;
    private WorldPoint fishingLocation;
    private BankLocation closestBank;
    private FishingSpotLocation selectedSpotLocation;
    private int currentLocationIndex = 0;
    private String fishAction = "";
    
    public AutoFishingState getCurrentState() {
        return state;
    }

    public boolean run(AutoFishingConfig config) {
        this.config = config;
        this.selectedFish = config.fishToCatch();
        this.selectedHarpoon = config.harpoonSpec();
        
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return;

                if (needsToBank()) {
                    state = AutoFishingState.DEPOSITING;
                } else if (state == AutoFishingState.DEPOSITING || state == AutoFishingState.RETURNING) {
                    if (isAtFishingLocation()) {
                        state = AutoFishingState.FISHING;
                    } else {
                        state = AutoFishingState.TRAVELING;
                    }
                } else if (!hasRequiredGear()) {
                    state = AutoFishingState.GETTING_GEAR;
                } else if (!isAtFishingLocation() && state != AutoFishingState.GETTING_GEAR) {
                    state = AutoFishingState.TRAVELING;
                }

                switch (state) {
                    case INITIALIZING:
                        handleInitializing();
                        break;
                    case CHECKING_GEAR:
                        handleCheckingGear();
                        break;
                    case GETTING_GEAR:
                        handleGettingGear();
                        break;
                    case TRAVELING:
                        handleTraveling();
                        break;
                    case FISHING:
                        handleFishing();
                        break;
                    case MANAGING_SPEC:
                        handleManagingSpec();
                        break;
                    case INVENTORY_FULL:
                        handleInventoryFull();
                        break;
                    case DEPOSITING:
                        handleDepositing();
                        break;
                    case RETURNING:
                        handleReturning();
                        break;
                    case ERROR_RECOVERY:
                        handleErrorRecovery();
                        break;
                }
            } catch (Exception ex) {
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        
        return true;
    }

    private boolean needsToBank() {
        if (!config.useBank()) return false;
        return Rs2Inventory.isFull();
    }

    private boolean hasRequiredGear() {
        if (!selectedFish.getMethod().getRequiredItems().isEmpty()) {
            boolean hasTool = selectedFish.getMethod().getRequiredItems().stream()
                    .anyMatch(tool -> Rs2Equipment.isWearing(tool) || Rs2Inventory.contains(tool));
            if (!hasTool) return false;
        }
        
        if (selectedHarpoon != HarpoonType.NONE) {
            return Rs2Equipment.isWearing(selectedHarpoon.getName()) || 
                   Rs2Inventory.contains(selectedHarpoon.getName());
        }
        
        return true;
    }

    private boolean isAtFishingLocation() {
        if (fishingLocation == null) return false;
        return Rs2Player.getWorldLocation().distanceTo(fishingLocation) <= 5;
    }

    private Rs2NpcModel getFishingSpot() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        return Arrays.stream(selectedFish.getFishingSpot())
                .mapToObj(Rs2Npc::getNpc)
                .filter(Objects::nonNull)
                .filter(npc -> npc.getWorldLocation().distanceTo(playerLocation) <= 10)
                .findFirst()
                .orElse(null);
    }

    private void handleInitializing() {
        Microbot.status = "Initializing...";
        state = AutoFishingState.CHECKING_GEAR;
    }

    private void handleCheckingGear() {
        Microbot.status = "Checking equipment...";
        
        if (!hasRequiredGear()) {
            state = AutoFishingState.GETTING_GEAR;
            return;
        }
        
        if (fishingLocation == null) {
            setupFishingSpotLocation();
            WorldPoint selectedLocation = getCurrentFishingLocation();
            if (selectedLocation != null) {
                fishingLocation = selectedLocation;
            } else {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                WorldPoint closestLocation = selectedFish.getClosestLocation(playerLocation);
                if (closestLocation != null) {
                    fishingLocation = closestLocation;
                } else {
                    fishingLocation = selectedFish.getClosestLocation(playerLocation);
                }
            }
        }
        
        if (!isAtFishingLocation()) {
            state = AutoFishingState.TRAVELING;
            return;
        }
        
        state = AutoFishingState.FISHING;
    }

    private void handleGettingGear() {
        Microbot.status = "Getting equipment from bank...";
        
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.walkToBankAndUseBank()) {
                state = AutoFishingState.ERROR_RECOVERY;
                return;
            }
            return;
        }
        
        // withdraw required fishing tools with validation
        if (!selectedFish.getMethod().getRequiredItems().isEmpty()) {
            for (String tool : selectedFish.getMethod().getRequiredItems()) {
                // pre-condition validation
                if (!Rs2Equipment.isWearing(tool) && !Rs2Inventory.contains(tool)) {
                    if (!Rs2Bank.hasItem(tool)) {
                        log.info("Required tool {} not found in bank", tool);
                        continue;
                    }
                    
                    // perform action
                    log.info("Withdrawing required tool: {}", tool);
                    Rs2Bank.withdrawOne(tool);
                    
                    // post-condition validation
                    boolean success = sleepUntil(() -> Rs2Inventory.contains(tool), 3000);
                    if (success) {
                        log.info("Successfully withdrew tool: {}", tool);
                    } else {
                        log.info("Failed to withdraw tool {} within timeout", tool);
                    }
                    // small delay to prevent spam clicking
                    sleepUntil(() -> true, 600);
                    break;
                }
            }
        }
        
        // withdraw harpoon if needed with validation
        if (selectedHarpoon != HarpoonType.NONE) {
            if (!Rs2Equipment.isWearing(selectedHarpoon.getName()) && !Rs2Inventory.contains(selectedHarpoon.getName())) {
                if (!Rs2Bank.hasItem(selectedHarpoon.getName())) {
                    log.info("Required harpoon {} not found in bank", selectedHarpoon.getName());
                } else {
                    // perform action
                    log.info("Withdrawing harpoon: {}", selectedHarpoon.getName());
                    Rs2Bank.withdrawOne(selectedHarpoon.getName());
                    
                    // post-condition validation
                    boolean success = sleepUntil(() -> Rs2Inventory.contains(selectedHarpoon.getName()), 3000);
                    if (success) {
                        log.info("Successfully withdrew harpoon: {}", selectedHarpoon.getName());
                    } else {
                        log.info("Failed to withdraw harpoon {} within timeout", selectedHarpoon.getName());
                    }
                    // small delay after withdrawal
                    sleepUntil(() -> true, 600);
                }
            }
        }
        
        log.info("Closing bank after gear withdrawal");
        Rs2Bank.closeBank();
        
        // equip withdrawn tools with validation
        for (String tool : selectedFish.getMethod().getRequiredItems()) {
            if (Rs2Inventory.contains(tool)) {
                // pre-condition validation
                if (Rs2Equipment.isWearing(tool)) {
                    log.info("Tool {} already equipped", tool);
                    continue;
                }
                
                // perform action
                log.info("Equipping tool: {}", tool);
                Rs2Inventory.wield(tool);
                
                // post-condition validation
                boolean success = sleepUntil(() -> Rs2Equipment.isWearing(tool), 3000);
                if (success) {
                    log.info("Successfully equipped tool: {}", tool);
                } else {
                    log.info("Failed to equip tool {} within timeout", tool);
                }
                // small delay after equipping
                sleepUntil(() -> true, 600);
            }
        }
        
        // equip harpoon if withdrawn with validation
        if (selectedHarpoon != HarpoonType.NONE && Rs2Inventory.contains(selectedHarpoon.getName())) {
            // pre-condition validation
            if (!Rs2Equipment.isWearing(selectedHarpoon.getName())) {
                // perform action
                log.info("Equipping harpoon: {}", selectedHarpoon.getName());
                Rs2Inventory.wield(selectedHarpoon.getName());
                
                // post-condition validation
                boolean success = sleepUntil(() -> Rs2Equipment.isWearing(selectedHarpoon.getName()), 3000);
                if (success) {
                    log.info("Successfully equipped harpoon: {}", selectedHarpoon.getName());
                } else {
                    log.info("Failed to equip harpoon {} within timeout", selectedHarpoon.getName());
                }
                // small delay after equipping harpoon
                sleepUntil(() -> true, 600);
            } else {
                log.info("Harpoon {} already equipped", selectedHarpoon.getName());
            }
        }
        
        log.info("Gear setup complete, rechecking gear");
        changeState(AutoFishingState.CHECKING_GEAR);
    }

    private void handleTraveling() {
        Microbot.status = "Traveling to fishing location...";
        
        if (fishingLocation == null) {
            setupFishingSpotLocation();
            WorldPoint selectedLocation = getCurrentFishingLocation();
            if (selectedLocation != null) {
                fishingLocation = selectedLocation;
            } else {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                WorldPoint closestLocation = selectedFish.getClosestLocation(playerLocation);
                if (closestLocation != null) {
                    fishingLocation = closestLocation;
                } else {
                    fishingLocation = selectedFish.getClosestLocation(playerLocation);
                }
            }
        }
        
        if (isAtFishingLocation()) {
            state = AutoFishingState.FISHING;
            return;
        }
        
        Rs2Walker.walkTo(fishingLocation);
    }

    private void handleFishing() {
        Microbot.status = "Fishing " + selectedFish.getName() + "...";
        
        if (Rs2Inventory.isFull()) {
            state = AutoFishingState.INVENTORY_FULL;
            return;
        }
        
        if (Rs2Player.isAnimating()) {
            return;
        }
        
        Rs2NpcModel fishingSpot = getFishingSpot();
        if (fishingSpot == null) {
            if (selectedSpotLocation != null && selectedSpotLocation.getLocations().length > 1) {
                cycleToNextLocation();
                if (fishingLocation != null) {
                    Rs2Walker.walkTo(fishingLocation);
                    return;
                }
            } else {
                state = AutoFishingState.TRAVELING;
                return;
            }
        }
        
        if (fishAction.isEmpty()) {
            fishAction = Rs2Npc.getAvailableAction(fishingSpot, selectedFish.getActions());
            if (fishAction.isEmpty()) {
                state = AutoFishingState.ERROR_RECOVERY;
                return;
            }
        }
        
        if (selectedFish.equals(Fish.KARAMBWAN)) {
            handleKarambwanLogic();
        }
        
        if (Rs2Npc.interact(fishingSpot, fishAction)) {
            Rs2Player.waitForXpDrop(Skill.FISHING, true);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
        }
    }

    private void handleManagingSpec() {
        Microbot.status = "Activating special attack...";
        
        if (selectedHarpoon != HarpoonType.NONE && Rs2Combat.getSpecEnergy() >= 100) {
            Rs2Combat.setSpecState(true, 1000);
            // wait for special attack to activate
            sleepUntil(() -> Rs2Combat.getSpecEnergy() < 100, 3000);
        }
        
        state = AutoFishingState.FISHING;
    }

    private void handleInventoryFull() {
        Microbot.status = "Inventory full, managing items...";
        
        if (config.useBank()) {
            WorldPoint currentLocation = Rs2Player.getWorldLocation();
            closestBank = Rs2Bank.getNearestBank(currentLocation);
            if (closestBank == null) {
                state = AutoFishingState.ERROR_RECOVERY;
                return;
            }
            state = AutoFishingState.DEPOSITING;
        } else {
            if (Rs2Inventory.dropAll(selectedFish.getRawNames().toArray(new String[0]))) {
                state = AutoFishingState.FISHING;
            } else {
                state = AutoFishingState.ERROR_RECOVERY;
            }
        }
    }

    private void handleDepositing() {
        Microbot.status = "Depositing items...";
        
        if (!Rs2Bank.isOpen()) {
            if (closestBank != null) {
                if (!Rs2Bank.walkToBankAndUseBank(closestBank)) {
                    state = AutoFishingState.ERROR_RECOVERY;
                    return;
                }
            } else {
                if (!Rs2Bank.walkToBankAndUseBank()) {
                    state = AutoFishingState.ERROR_RECOVERY;
                    return;
                }
            }
            return;
        }
        
        for (String fishName : selectedFish.getRawNames()) {
            if (Rs2Inventory.contains(fishName)) {
                Rs2Bank.depositAll(fishName);
                // wait for deposit to complete
                sleepUntil(() -> !Rs2Inventory.contains(fishName), 2000);
            }
        }
        
        Rs2Bank.closeBank();
        closestBank = null;
        state = AutoFishingState.RETURNING;
    }

    private void handleReturning() {
        Microbot.status = "Returning to fishing location...";
        
        if (fishingLocation == null) {
            state = AutoFishingState.TRAVELING;
            return;
        }
        
        if (isAtFishingLocation()) {
            state = AutoFishingState.FISHING;
            return;
        }
        
        Rs2Walker.walkTo(fishingLocation);
    }

    private void handleErrorRecovery() {
        Microbot.status = "Recovering from error...";
        
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }
        
        fishAction = "";
        // wait for any animations to complete during recovery
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 3000);
        
        changeState(AutoFishingState.CHECKING_GEAR);
    }

    private void handleKarambwanLogic() {
        if (Rs2Inventory.hasItem(ItemID.TBWT_RAW_KARAMBWANJI) && Rs2Inventory.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL)) {
            Rs2Inventory.combineClosest(ItemID.TBWT_RAW_KARAMBWANJI, ItemID.TBWT_KARAMBWAN_VESSEL);
            // wait for karambwanji combination to complete
            sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.TBWT_RAW_KARAMBWANJI) || Rs2Player.isAnimating(), 3000);
        }
    }

    private void setupFishingSpotLocation() {
        selectedSpotLocation = null;
        currentLocationIndex = 0;
        
        switch (selectedFish) {
            case SHRIMP:
                var shrimpLoc = config.shrimpLocation();
                if (shrimpLoc != AutoFishingConfig.ShrimpLocation.AUTO) {
                    selectedSpotLocation = shrimpLoc.toFishingSpotLocation();
                }
                break;
            case ANCHOVIES:
                var anchoviesLoc = config.anchoviesLocation();
                if (anchoviesLoc != AutoFishingConfig.AnchoviesLocation.AUTO) {
                    selectedSpotLocation = anchoviesLoc.toFishingSpotLocation();
                }
                break;
            case HERRING:
                var herringLoc = config.herringLocation();
                if (herringLoc != AutoFishingConfig.HerringLocation.AUTO) {
                    selectedSpotLocation = herringLoc.toFishingSpotLocation();
                }
                break;
            case LOBSTER:
                var lobsterLoc = config.lobsterLocation();
                if (lobsterLoc != AutoFishingConfig.LobsterLocation.AUTO) {
                    selectedSpotLocation = lobsterLoc.toFishingSpotLocation();
                }
                break;
            case SHARK:
                var sharkLoc = config.sharkLocation();
                if (sharkLoc != AutoFishingConfig.SharkLocation.AUTO) {
                    selectedSpotLocation = sharkLoc.toFishingSpotLocation();
                }
                break;
            case SALMON:
                var salmonLoc = config.salmonLocation();
                if (salmonLoc != AutoFishingConfig.SalmonLocation.AUTO) {
                    selectedSpotLocation = salmonLoc.toFishingSpotLocation();
                }
                break;
            case TROUT:
                var troutLoc = config.troutLocation();
                if (troutLoc != AutoFishingConfig.TroutLocation.AUTO) {
                    selectedSpotLocation = troutLoc.toFishingSpotLocation();
                }
                break;
            case MONKFISH:
                var monkfishLoc = config.monkfishLocation();
                if (monkfishLoc != AutoFishingConfig.MonkfishLocation.AUTO) {
                    selectedSpotLocation = monkfishLoc.toFishingSpotLocation();
                }
                break;
            case KARAMBWAN:
                var karambwanLoc = config.karambwanLocation();
                if (karambwanLoc != AutoFishingConfig.KarambwanLocation.AUTO) {
                    selectedSpotLocation = karambwanLoc.toFishingSpotLocation();
                }
                break;
            case KARAMBWANJI:
                var karambwanjiLoc = config.karambwanjiLocation();
                if (karambwanjiLoc != AutoFishingConfig.KarambwanjiLocation.AUTO) {
                    selectedSpotLocation = karambwanjiLoc.toFishingSpotLocation();
                }
                break;
            case LAVA_EEL:
                var lavaEelLoc = config.lavaEelLocation();
                if (lavaEelLoc != AutoFishingConfig.LavaEelLocation.AUTO) {
                    selectedSpotLocation = lavaEelLoc.toFishingSpotLocation();
                }
                break;
            case CAVE_EEL:
                var caveEelLoc = config.caveEelLocation();
                if (caveEelLoc != AutoFishingConfig.CaveEelLocation.AUTO) {
                    selectedSpotLocation = caveEelLoc.toFishingSpotLocation();
                }
                break;
            case BARBARIAN_FISH:
                var barbarianFishLoc = config.barbarianFishLocation();
                if (barbarianFishLoc != AutoFishingConfig.BarbarianFishLocation.AUTO) {
                    selectedSpotLocation = barbarianFishLoc.toFishingSpotLocation();
                }
                break;
            case ANGLERFISH:
                var anglerfishLoc = config.anglerfishLocation();
                if (anglerfishLoc != AutoFishingConfig.AnglerfishLocation.AUTO) {
                    selectedSpotLocation = anglerfishLoc.toFishingSpotLocation();
                }
                break;
        }
    }

    private WorldPoint getCurrentFishingLocation() {
        if (selectedSpotLocation != null && selectedSpotLocation.getLocations().length > 0) {
            if (currentLocationIndex >= selectedSpotLocation.getLocations().length) {
                currentLocationIndex = 0;
            }
            return selectedSpotLocation.getLocations()[currentLocationIndex];
        }
        return null;
    }

    private void cycleToNextLocation() {
        if (selectedSpotLocation != null && selectedSpotLocation.getLocations().length > 1) {
            currentLocationIndex = (currentLocationIndex + 1) % selectedSpotLocation.getLocations().length;
            fishingLocation = getCurrentFishingLocation();
        }
    }

    // helper method to change state with timeout reset
    private void changeState(AutoFishingState newState) {
        if (newState != state) {
            log.info("State change: {} -> {}", state, newState);
            state = newState;
            stateStartTime = System.currentTimeMillis();
        }
    }

    @Override
    public void shutdown() {
        log.info("Shutting down auto fishing script");
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        
        // cleanup any open interfaces
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }
        
        // reset all state variables
        closestBank = null;
        fishingLocation = null;
        selectedSpotLocation = null;
        currentLocationIndex = 0;
        fishAction = "";
        log.info("Auto fishing script shutdown complete");
    }
}