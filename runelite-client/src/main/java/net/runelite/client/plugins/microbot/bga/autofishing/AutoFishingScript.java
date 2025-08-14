package net.runelite.client.plugins.microbot.bga.autofishing;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.AutoFishingState;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.Fish;
import net.runelite.client.plugins.microbot.bga.autofishing.enums.HarpoonType;
import net.runelite.client.plugins.microbot.bga.autofishing.managers.EquipmentManager;
import net.runelite.client.plugins.microbot.bga.autofishing.managers.InventoryManager;
import net.runelite.client.plugins.microbot.bga.autofishing.managers.LocationManager;
import net.runelite.client.plugins.microbot.bga.autofishing.managers.SpecialAttackManager;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;


public class AutoFishingScript extends Script {

    public static String version = "1.0.0";
    
    @Inject
    private EquipmentManager equipmentManager;
    
    @Inject
    private LocationManager locationManager;
    
    @Inject
    private InventoryManager inventoryManager;
    
    @Inject
    private SpecialAttackManager specialAttackManager;


    @Getter
    private AutoFishingState currentState;
    private AutoFishingFlags flags;
    private AutoFishingConfig config;
    private Fish selectedFish;

    @Getter
    private HarpoonType selectedHarpoon;
    private String fishAction = "";

    public boolean run(AutoFishingConfig config) {
        this.config = config;
        this.selectedFish = config.fish();
        this.selectedHarpoon = config.harpoonSpec();
        this.flags = new AutoFishingFlags();
        this.currentState = AutoFishingState.INITIALIZING;
        
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;

                executeStateMachine();
                
            } catch (Exception ex) {
                handleError(ex);
            }
        }, 0, getDynamicLoopInterval(), TimeUnit.MILLISECONDS);
        
        return true;
    }

    private void executeStateMachine() {
        updateFlags();
        
        AutoFishingState nextState = executeCurrentState();
        
        if (nextState != currentState) {
            transitionToState(nextState);
        }
    }

    private AutoFishingState executeCurrentState() {
        switch (currentState) {
            case INITIALIZING:
                return handleInitializing();
            case CHECKING_GEAR:
                return handleCheckingGear();
            case GETTING_GEAR:
                return handleGettingGear();
            case TRAVELING:
                return handleTraveling();
            case FISHING:
                return handleFishing();
            case MANAGING_SPEC:
                return handleManagingSpec();
            case INVENTORY_FULL:
                return handleInventoryFull();
            case DEPOSITING:
                return handleDepositing();
            case RETURNING:
                return handleReturning();
            case ERROR_RECOVERY:
                return handleErrorRecovery();
            default:
                return AutoFishingState.ERROR_RECOVERY;
        }
    }

    private AutoFishingState handleInitializing() {
        Microbot.status = "Initializing...";
        
        locationManager.initializeFishingLocation();
        
        flags.setBankingEnabled(config.useBank());
        flags.setPreferDepositBox(true);
        
        flags.reset();
        flags.markStateStart();

        return AutoFishingState.CHECKING_GEAR;
    }

    private AutoFishingState handleCheckingGear() {
        Microbot.status = "Checking equipment...";
        
        boolean hasGear = equipmentManager.validateFishingGear(selectedFish, selectedHarpoon);
        flags.setHasRequiredGear(hasGear);
        
        if (!hasGear) {
            return AutoFishingState.GETTING_GEAR;
        }
        
        if (!locationManager.isAtFishingLocation()) {
            return AutoFishingState.TRAVELING;
        }
        
        return AutoFishingState.FISHING;
    }

    private AutoFishingState handleGettingGear() {
        Microbot.status = "Getting equipment from bank...";
        
        if (!Rs2Bank.isOpen()) {
            if (!locationManager.walkToBankAndOpen()) {
                flags.incrementRetry();
                return AutoFishingState.ERROR_RECOVERY;
            }
            return currentState;
        }
        
        if (!equipmentManager.withdrawGear(selectedFish, selectedHarpoon)) {
            flags.incrementRetry();
            return AutoFishingState.ERROR_RECOVERY;
        }
        
        equipmentManager.equipTools(selectedHarpoon);
        
        Rs2Bank.closeBank();
        
        flags.resetRetries();
        return AutoFishingState.CHECKING_GEAR;
    }

    private AutoFishingState handleTraveling() {
        Microbot.status = "Traveling to fishing location...";
        
        if (Rs2Player.isMoving()) {
            return currentState;
        }
        
        if (locationManager.isAtFishingLocation()) {
            flags.setAtFishingLocation(true);
            return AutoFishingState.FISHING;
        }
        
        WorldPoint fishingSpot = locationManager.findOptimalFishingSpot(selectedFish);
        if (fishingSpot == null) {
            flags.incrementRetry();
            return AutoFishingState.ERROR_RECOVERY;
        }
        
        if (!locationManager.walkToLocation(fishingSpot)) {
            flags.incrementRetry();
            return AutoFishingState.ERROR_RECOVERY;
        }
        
        return currentState;
    }

    private AutoFishingState handleFishing() {
        Microbot.status = "Fishing " + selectedFish.getName() + "...";

        if (inventoryManager.isInventoryFull()) {
            return AutoFishingState.INVENTORY_FULL;
        }
        
        if (Rs2Player.isAnimating()) {
            Microbot.status = "Currently fishing...";
            Rs2Player.waitForAnimation();
            return currentState;
        }

        Rs2NpcModel fishingSpot = getFishingSpot(selectedFish);
        if (fishingSpot == null) {
            sleepUntil(() -> getFishingSpot(selectedFish) != null, 2000);
            return currentState;
        }
        
        if (fishAction.isEmpty()) {
            fishAction = Rs2Npc.getAvailableAction(fishingSpot, selectedFish.getActions());
            if (fishAction.isEmpty()) {
                flags.incrementRetry();
                return AutoFishingState.ERROR_RECOVERY;
            }
        }
        
        if (!Rs2Camera.isTileOnScreen(fishingSpot.getLocalLocation())) {
            validateInteractable(fishingSpot);
        }
        
        if (selectedFish.equals(Fish.KARAMBWAN)) {
            handleKarambwanLogic();
        }
        
        if (Rs2Npc.interact(fishingSpot, fishAction)) {
            flags.markAction();
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            
            Rs2Player.waitForAnimation();
        }
        
        return currentState;
    }

    private AutoFishingState handleManagingSpec() {
        Microbot.status = "Activating special attack...";
        
        if (specialAttackManager.activateSpecialAttack(selectedHarpoon)) {
            sleep(1000);
        }
        
        return AutoFishingState.FISHING;
    }

    private AutoFishingState handleInventoryFull() {
        Microbot.status = "Inventory full, managing items...";
        
        if (flags.isBankingEnabled()) {
            return AutoFishingState.DEPOSITING;
        } else {
            if (inventoryManager.dropFish()) {
                return AutoFishingState.FISHING;
            } else {
                flags.incrementRetry();
                return AutoFishingState.ERROR_RECOVERY;
            }
        }
    }

    private AutoFishingState handleDepositing() {
        Microbot.status = "Depositing items...";
        
        if (flags.isPreferDepositBox() && locationManager.isDepositBoxNearby()) {
            return handleDepositBoxDeposit();
        } else {
            return handleBankDeposit();
        }
    }

    private AutoFishingState handleDepositBoxDeposit() {
        if (!Rs2DepositBox.isOpen()) {
            if (!locationManager.walkToDepositBoxAndOpen()) {
                return handleBankDeposit();
            }
            return currentState;
        }
        
        boolean success = true;
        success &= inventoryManager.depositFish();
        success &= inventoryManager.depositTreasures(config);
        
        Rs2DepositBox.closeDepositBox();
        
        if (success && inventoryManager.validateDepositSuccess()) {
            return AutoFishingState.RETURNING;
        } else {
            flags.incrementRetry();
            return AutoFishingState.ERROR_RECOVERY;
        }
    }

    private AutoFishingState handleBankDeposit() {
        if (!Rs2Bank.isOpen()) {
            if (!locationManager.walkToBankAndOpen()) {
                flags.incrementRetry();
                return AutoFishingState.ERROR_RECOVERY;
            }
            return currentState;
        }
        
        boolean success = true;
        success &= inventoryManager.depositFish();
        success &= inventoryManager.depositTreasures(config);
        
        Rs2Bank.closeBank();
        
        if (success && inventoryManager.validateDepositSuccess()) {
            return AutoFishingState.RETURNING;
        } else {
            flags.incrementRetry();
            return AutoFishingState.ERROR_RECOVERY;
        }
    }

    private AutoFishingState handleReturning() {
        Microbot.status = "Returning to fishing location...";
        
        if (Rs2Player.isMoving()) {
            return currentState;
        }
        
        if (locationManager.isAtFishingLocation()) {
            flags.setAtFishingLocation(true);
            return AutoFishingState.FISHING;
        }
        
        if (!locationManager.returnToFishingLocation()) {
            flags.incrementRetry();
            return AutoFishingState.ERROR_RECOVERY;
        }
        
        return currentState;
    }

    private AutoFishingState handleErrorRecovery() {
        Microbot.status = "Recovering from error...";
        
        if (flags.maxRetriesExceeded()) {
            shutdown();
            return currentState;
        }
        
        if (!flags.canRetry()) {
            return currentState;
        }
        

        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }
        
        fishAction = "";
        
        flags.resetRetries();
        return AutoFishingState.CHECKING_GEAR;
    }

    private void updateFlags() {
        flags.setAtFishingLocation(locationManager.isAtFishingLocation());
        flags.setInventoryFull(inventoryManager.isInventoryFull());
        flags.setHasRoomForFish(inventoryManager.hasInventorySpace());
        flags.setSpecWeaponEquipped(specialAttackManager.hasSpecWeaponEquipped(selectedHarpoon));
        flags.setHasFullSpecEnergy(specialAttackManager.hasFullSpecEnergy());
        flags.setShouldUseSpec(specialAttackManager.shouldActivateSpec(selectedHarpoon));

    }

    private void transitionToState(AutoFishingState newState) {
        currentState = newState;
        flags.markStateStart();
    }

    private void handleKarambwanLogic() {
        if (Rs2Inventory.hasItem(ItemID.TBWT_RAW_KARAMBWANJI)) {
            if (Rs2Inventory.hasItem(ItemID.TBWT_KARAMBWAN_VESSEL)) {
                Rs2Inventory.waitForInventoryChanges(() -> 
                    Rs2Inventory.combineClosest(ItemID.TBWT_RAW_KARAMBWANJI, ItemID.TBWT_KARAMBWAN_VESSEL), 
                    600, 5000);
            }
        }
    }

    private Rs2NpcModel getFishingSpot(Fish fish) {
        return Arrays.stream(fish.getFishingSpot())
                .mapToObj(Rs2Npc::getNpc)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void handleError(Exception ex) {
        flags.setInErrorState(true);
        flags.incrementRetry();
        currentState = AutoFishingState.ERROR_RECOVERY;
    }

    private long getDynamicLoopInterval() {
        switch (currentState) {
            case FISHING:
                return 600;
            case TRAVELING:
            case RETURNING:
                return 1000;
            case GETTING_GEAR:
            case DEPOSITING:
                return 800;
            case ERROR_RECOVERY:
                return 2000;
            default:
                return 1000;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        
        if (Rs2Bank.isOpen()) {
            Rs2Bank.closeBank();
        }
        if (Rs2DepositBox.isOpen()) {
            Rs2DepositBox.closeDepositBox();
        }

    }
}