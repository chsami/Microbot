## LockCondition Usage in Combat Plugins

Combat and bossing plugins often require careful management of the LockCondition to prevent interruption during critical combat sequences. This section provides examples and best practices for using LockCondition in combat scenarios.

### Example: Boss Fight Lock Management

For a boss fight plugin, you would typically want to lock the plugin when the fight begins and unlock it when the fight ends, similar to how the GotrPlugin manages lock state:

```java
public class BossPlugin extends Plugin implements SchedulablePlugin {
    
    private LockCondition lockCondition;
    private LogicalCondition stopCondition = null;
    
    @Override
    public LogicalCondition getStopCondition() {
        if (this.stopCondition == null) {
            this.stopCondition = createStopCondition();
        }
        return this.stopCondition;
    }
    
    private LogicalCondition createStopCondition() {
        // Create a lock condition specifically for boss combat
        this.lockCondition = new LockCondition("Locked during boss fight");
        
        // Create stop conditions
        OrCondition stopTriggers = new OrCondition();
        
        // Add various stop conditions (resource depletion, time limit, etc.)
        stopTriggers.addCondition(new InventoryItemCountCondition("Prayer potion", 0, true));
        // NOTE: HealthPercentCondition is not yet implemented - this is a placeholder
        // stopTriggers.addCondition(new HealthPercentCondition(15)); 
        
        // Use SingleTriggerTimeCondition for a time limit (60 minutes from now)
        stopTriggers.addCondition(SingleTriggerTimeCondition.afterDelay(60 * 60)); // 60 minutes in seconds
        
        // Combine the stop triggers with the lock condition using AND logic
        // This ensures the plugin won't stop if locked, even if other conditions are met
        AndCondition andCondition = new AndCondition();
        andCondition.addCondition(stopTriggers);
        andCondition.addCondition(lockCondition);
        
        return andCondition;
    }
    
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }
        
        String message = chatMessage.getMessage();
        
        // Lock the plugin when the boss fight begins
        if (message.contains("You've entered the boss arena.") || 
            message.contains("The boss has appeared!")) {
            if (lockCondition != null) {
                lockCondition.lock();
                log.debug("Boss fight started - locked plugin");
            }
        }
        // Unlock the plugin when the boss fight ends
        else if (message.contains("Congratulations, you've defeated the boss!") || 
                 message.contains("You have been defeated.")) {
            if (lockCondition != null) {
                lockCondition.unlock();
                log.debug("Boss fight ended - unlocked plugin");
            }
        }
    }
    
    @Subscribe
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this) {
            log.info("Scheduler requesting plugin shutdown");
            
            // Setup a scheduled task to check if it's safe to exit
            ScheduledExecutorService exitExecutor = Executors.newSingleThreadScheduledExecutor();
            exitExecutor.scheduleWithFixedDelay(() -> {
                try {
                    // Check if we're in a critical phase (boss fight)
                    if (lockCondition != null && lockCondition.isLocked()) {
                        log.info("Cannot exit during boss fight - waiting for fight to end");
                        return; // Try again later
                    }
                    
                    // Safe to exit, perform cleanup
                    log.info("Safe to exit - performing cleanup");
                    leaveBossArea(); // Method to safely teleport away or exit
                    
                    // Stop the plugin
                    Microbot.getClientThread().invokeLater(() -> {
                        Microbot.stopPlugin(this);
                        return true;
                    });
                    
                    // Shutdown the executor
                    exitExecutor.shutdown();
                } catch (Exception ex) {
                    log.error("Error during safe exit", ex);
                    // Force stop in case of error
                    Microbot.getClientThread().invokeLater(() -> {
                        Microbot.stopPlugin(this);
                        return true;
                    });
                    
                    // Shutdown the executor
                    exitExecutor.shutdown();
                }
            }, 0, 2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
    
    /**
     * Safely leaves the boss area, e.g., via teleport or exit portal
     */
    private void leaveBossArea() {
        // Implementation to safely leave the boss area
        // This might involve clicking an exit portal, using a teleport item, etc.
    }
}
```

### Combat-Specific Lock Patterns

When developing combat plugins, consider these lock patterns:

1. **Phase-Based Locking**: Lock during specific boss phases that shouldn't be interrupted

```java
@Subscribe
public void onNpcChanged(NpcChanged event) {
    NPC npc = event.getNpc();
    
    // Detect phase change on the boss
    if (npc.getId() == BOSS_ID && npc.getAnimation() == PHASE_CHANGE_ANIM) {
        // Lock during the special phase
        lockCondition.lock();
        
        // Schedule unlock after the phase should be complete
        ScheduledExecutorService phaseExecutor = Executors.newSingleThreadScheduledExecutor();
        phaseExecutor.schedule(() -> {
            lockCondition.unlock();
            phaseExecutor.shutdown();
        }, 30, java.util.concurrent.TimeUnit.SECONDS);
    }
}
```

2. **Special Attack Locking**: Lock during special attack execution

```java
private void executeSpecialAttack() {
    try {
        // Lock before starting the special attack sequence
        lockCondition.lock();
        
        // Perform special attack actions
        Rs2Combat.toggleSpecialAttack(true);
        Rs2Equipment.equipItem("Dragon dagger(p++)", "Wield");
        Rs2Npc.interact(targetNpc, "Attack");
        
        // Wait for animation to complete
        Global.sleepUntil(() -> Rs2Player.getAnimationId() == -1, 3000);
        
        // Switch back to main weapon
        Rs2Equipment.equipItem("Abyssal whip", "Wield");
    } finally {
        // Always unlock when the sequence is complete
        lockCondition.unlock();
    }
}
```

3. **Prayer Flicking Lock**: Lock during prayer flicking sequences

```java
private void startPrayerFlicking() {
    // Lock while the prayer flicking routine is active
    lockCondition.lock();
    
    ScheduledExecutorService flickingExecutor = Executors.newSingleThreadScheduledExecutor();
    prayerFlickingFuture = flickingExecutor.scheduleAtFixedRate(() -> {
        try {
            // Detect if we should stop flicking
            // Note: HealthPercentCondition is not implemented yet, this is just an example
            // This would need to be replaced with actual health checking logic
            if (/* Rs2Player.getHealthPercent() < 25 || */ !inCombat()) {
                stopPrayerFlicking();
                return;
            }
            
            // Toggle the appropriate prayers based on boss attacks
            toggleProtectionPrayers();
        } catch (Exception e) {
            log.error("Error in prayer flicking", e);
            stopPrayerFlicking();
        }
    }, 0, 600, java.util.concurrent.TimeUnit.MILLISECONDS);
}

private void stopPrayerFlicking() {
    if (prayerFlickingFuture != null) {
        prayerFlickingFuture.cancel(false);
        prayerFlickingFuture = null;
    }
    
    // Turn off prayers
    Rs2Prayer.toggle(Prayer.PROTECT_FROM_MAGIC, false);
    Rs2Prayer.toggle(Prayer.PROTECT_FROM_MISSILES, false);
    Rs2Prayer.toggle(Prayer.PROTECT_FROM_MELEE, false);
    
    // Unlock after prayer flicking stops
    lockCondition.unlock();
}
```

### Best Practices for Combat Lock Management

1. **Always use try-finally blocks** when manually locking to ensure the lock is released even if exceptions occur:

```java
try {
    lockCondition.lock();
    // Critical combat sequence
} finally {
    lockCondition.unlock();
}
```

2. **Keep lock scopes narrow** - only lock during truly critical operations:

```java
// BAD: Locking for the entire method
public void doBossFight() {
    lockCondition.lock();
    setupGear();
    walkToBoss();
    fightBoss();  // Only this part is truly critical
    collectLoot();
    lockCondition.unlock();
}

// GOOD: Locking only the critical part
public void doBossFight() {
    setupGear();
    walkToBoss();
    
    try {
        lockCondition.lock();
        fightBoss();  // Only lock during the actual fight
    } finally {
        lockCondition.unlock();
    }
    
    collectLoot();
}
```

3. **Use meaningful lock reason messages** to aid debugging:

```java
// BAD
this.lockCondition = new LockCondition();

// GOOD
this.lockCondition = new LockCondition("Locked during Zulrah's blue phase");
```

4. **Consider timeout mechanisms** for locks that might get stuck:

```java
// Set a maximum lock duration for safety
final long MAX_LOCK_DURATION = 120_000; // 2 minutes
long lockStartTime = System.currentTimeMillis();

try {
    lockCondition.lock();
    
    while (bossStillFighting()) {
        // Combat logic...
        
        // Safety timeout check
        if (System.currentTimeMillis() - lockStartTime > MAX_LOCK_DURATION) {
            log.warn("Lock timeout exceeded - forcing unlock");
            break;
        }
        
        Global.sleep(100);
    }
} finally {
    lockCondition.unlock();
}
```

5. **Take advantage of LockCondition constructor options**:

```java
// Create a locked condition from the start
this.lockCondition = new LockCondition("Locked during combat sequence", true);

// Later when it's safe to unlock
this.lockCondition.unlock();
```

By following these patterns, your combat plugins will safely integrate with the scheduler system while ensuring critical combat sequences are never interrupted at dangerous moments.
