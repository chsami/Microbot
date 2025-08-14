package net.runelite.client.plugins.microbot.bga.autofishing;

import lombok.Data;

@Data
public class AutoFishingFlags {
    
    private boolean hasRequiredGear = false;
    private boolean hasSpecWeapon = false;
    private boolean specWeaponEquipped = false;
    
    private boolean atFishingLocation = false;
    private boolean atBankLocation = false;
    private boolean atDepositLocation = false;
    
    private boolean inventoryReady = false;
    private boolean inventoryFull = false;
    private boolean hasRoomForFish = false;
    
    private boolean bankingEnabled = false;
    private boolean preferDepositBox = true;
    
    private boolean shouldUseSpec = false;
    private boolean specOnCooldown = false;
    private boolean hasFullSpecEnergy = false;
    
    private boolean inErrorState = false;
    private int retryAttempts = 0;
    private long lastErrorTime = 0;
    
    private long stateStartTime = 0;
    private long lastActionTime = 0;
    
    public void reset() {
        hasRequiredGear = false;
        hasSpecWeapon = false;
        specWeaponEquipped = false;
        atFishingLocation = false;
        atBankLocation = false;
        atDepositLocation = false;
        inventoryReady = false;
        inventoryFull = false;
        hasRoomForFish = false;
        shouldUseSpec = false;
        specOnCooldown = false;
        hasFullSpecEnergy = false;
        inErrorState = false;
        retryAttempts = 0;
        lastErrorTime = 0;
        stateStartTime = System.currentTimeMillis();
        lastActionTime = System.currentTimeMillis();
    }
    
    public void markStateStart() {
        stateStartTime = System.currentTimeMillis();
    }
    
    public void markAction() {
        lastActionTime = System.currentTimeMillis();
    }
    
    public long getTimeInState() {
        return System.currentTimeMillis() - stateStartTime;
    }
    
    public long getTimeSinceLastAction() {
        return System.currentTimeMillis() - lastActionTime;
    }
    
    public void incrementRetry() {
        retryAttempts++;
        lastErrorTime = System.currentTimeMillis();
    }
    
    public void resetRetries() {
        retryAttempts = 0;
        inErrorState = false;
        lastErrorTime = 0;
    }
    
    public boolean maxRetriesExceeded() {
        return retryAttempts >= 3;
    }
    
    public boolean canRetry() {
        long backoffTime = (long) Math.pow(2, retryAttempts) * 1000;
        return System.currentTimeMillis() - lastErrorTime > backoffTime;
    }
}