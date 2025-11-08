# CODE ISSUE EXAMPLES - TICK-BASED EXECUTION

## ISSUE #1: Karambwan Delay Wrong (2 ticks vs 3 ticks)

### Current (WRONG):
```java
// File: ConsumableAction.java, Lines 47-81

public enum ConsumableType {
    FOOD(3),              // regular food: 3 tick delay ✓
    CRYSTAL_FOOD(2),      // gauntlet crystal food: 2 tick delay ✓
    POTION(3),            // potions: 3 tick delay ✓
    COMBO_FOOD(3),        // combo food (e.g., karambwan): 3 ticks  ✗ WRONG!
    UNKNOWN(3);           // default to 3 ticks
    
    private final int delayTicks;
    
    public static ConsumableType fromItemId(int itemId) {
        // gauntlet crystal food (perfected)
        if (itemId == 23981 || itemId == 23982 || itemId == 23983 || itemId == 23984) {
            return CRYSTAL_FOOD;  // 2 ticks ✓
        }
        
        // karambwan (combo food)
        if (itemId == 3144) {
            return COMBO_FOOD;  // ✗ Should be 2, not 3!
        }
        
        // default to regular food or potion (3 ticks)
        return FOOD;
    }
}
```

### Fix:
```java
public enum ConsumableType {
    FOOD(3),              // regular food: 3 tick delay
    CRYSTAL_FOOD(2),      // gauntlet crystal food: 2 tick delay
    POTION(3),            // potions: 3 tick delay
    COMBO_FOOD(2),        // ← CHANGE: Karambwan is 2 ticks, not 3
    UNKNOWN(3);           // default to 3 ticks
    
    // ... rest unchanged ...
}
```

### Impact:
- Karambwan eats queue next attack **1 tick too late**
- Each karambwan eat in Gauntlet = 1 tick DPS loss
- Multiple eats per fight = cumulative DPS loss

---

## ISSUE #2: Actions Lost When Skipped Due to Tick Loss

### Current (WRONG):
```java
// File: Rs2PvMCombat.java, Lines 132-161

private void executeAction(PvMAction action) {
    // check tick loss state before executing
    TickLossState tickLoss = combatHandler != null
        ? combatHandler.getTickLossState()
        : TickLossState.NONE;

    if (action.requiresNoTickLoss() && tickLoss != TickLossState.NONE) {
        log.debug("Skipping action {} due to tick loss: {}", 
                  action.getDescription(), tickLoss);
        recordActionExecution(action, false, "Tick loss: " + tickLoss);
        return;  // ✗ ACTION LOST FOREVER!
    }

    // execute action
    long startTime = System.currentTimeMillis();
    boolean success = false;
    String result = "Unknown";

    try {
        success = action.execute();
        result = success ? "Success" : "Failed";
    } catch (Exception e) {
        log.error("Action {} threw exception: {}", 
                  action.getDescription(), e.getMessage());
        result = "Exception: " + e.getMessage();
    }

    long executionTime = System.currentTimeMillis() - startTime;
    recordActionExecution(action, success, result);

    log.debug("Executed action: {} - {} ({}ms)", 
              action.getDescription(), result, executionTime);
}
```

### Fix:
```java
private void executeAction(PvMAction action) {
    // check tick loss state before executing
    TickLossState tickLoss = combatHandler != null
        ? combatHandler.getTickLossState()
        : TickLossState.NONE;

    if (action.requiresNoTickLoss() && tickLoss != TickLossState.NONE) {
        log.debug("Deferring action {} due to tick loss: {}", 
                  action.getDescription(), tickLoss);
        
        // ✓ RE-QUEUE FOR NEXT TICK INSTEAD OF LOSING IT
        queueActionRelative(action, 1);
        recordActionExecution(action, false, "Deferred: Tick loss: " + tickLoss);
        return;
    }

    // execute action
    long startTime = System.currentTimeMillis();
    boolean success = false;
    String result = "Unknown";

    try {
        success = action.execute();
        result = success ? "Success" : "Failed";
    } catch (Exception e) {
        log.error("Action {} threw exception: {}", 
                  action.getDescription(), e.getMessage());
        result = "Exception: " + e.getMessage();
    }

    long executionTime = System.currentTimeMillis() - startTime;
    recordActionExecution(action, success, result);

    log.debug("Executed action: {} - {} ({}ms)", 
              action.getDescription(), result, executionTime);
}
```

### Impact:
- Prevents loss of critical actions (e.g., attacks)
- Actions retry next tick automatically
- No DPS loss from skipped actions

---

## ISSUE #3: Dual Execution Layers (Race Condition)

### Current (PROBLEMATIC):
```java
// File: MicroGauntletScript.java, Lines 74-98

public boolean run(MicroGauntletConfig config) {
    this.config = config;
    this.pvmCombat = Rs2PvMEventManager.getInstance().getPvmCombat();

    // ✗ SCHEDULED EXECUTOR THREAD
    mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
        if (!super.run()) return;
        if (BreakHandlerScript.isBreakActive()) return;

        try {
            // check if in boss fight
            if (!Rs2GauntletUtil.isBossFightActive()) {
                if (fightActive) {
                    stopTracking();
                }
                return;
            }

            // start tracking when fight begins
            if (!fightActive) {
                startTracking();
            }

            // EXECUTE COMBAT LOOP (on separate thread!)
            executeCombatLoop();

        } catch (Exception ex) {
            log.error("gauntlet script error: {}", ex.getMessage(), ex);
        }
    }, 0, 600, TimeUnit.MILLISECONDS);  // ← Fixed 600ms interval, not synced with GameTick

    return true;
}
```

### Problem:
```
THREAD A (ScheduledExecutor)        THREAD B (Game/RuneLite)
────────────────────────            ────────────────────────────
t=100ms:
  executeCombatLoop()
    queueAction(attack, tick=101)   
    [Updates actionQueue]

                                    t=150ms:
                                      GameTick fires
                                      executeQueuedActions()
                                        Look for tick=150 actions
                                        (Not found!)

                                    t=750ms:
                                      GameTick fires
                                      executeQueuedActions()
                                        Look for tick=101 actions
                                        Found! ← 600ms+ delay
```

### Fix:
```java
// File: MicroGauntletScript.java

public boolean run(MicroGauntletConfig config) {
    this.config = config;
    this.pvmCombat = Rs2PvMEventManager.getInstance().getPvmCombat();
    
    // ✓ USE EVENT MANAGER TO SUBSCRIBE TO GAMETICK
    Rs2PvMEventManager pvmEventManager = Rs2PvMEventManager.getInstance();
    pvmEventManager.start();
    
    // Register this script as GameTick listener
    // (Need to add this capability to Rs2PvMEventManager)
    pvmEventManager.registerScriptListener(this);
    
    return true;
}

// ✓ SUBSCRIBE TO GAMETICK EVENT (synced with game)
@Subscribe
public void onGameTick(GameTick event) {
    if (!super.run()) return;
    if (BreakHandlerScript.isBreakActive()) return;

    try {
        // check if in boss fight
        if (!Rs2GauntletUtil.isBossFightActive()) {
            if (fightActive) {
                stopTracking();
            }
            return;
        }

        // start tracking when fight begins
        if (!fightActive) {
            startTracking();
        }

        // EXECUTE COMBAT LOOP (on game thread, synced with tick)
        executeCombatLoop();

    } catch (Exception ex) {
        log.error("gauntlet script error: {}", ex.getMessage(), ex);
    }
}

@Override
public void shutdown() {
    // Unsubscribe from events
    Rs2PvMEventManager.getInstance().unregisterScriptListener(this);
    stopTracking();
    super.shutdown();
}
```

### Benefits:
- ✓ Perfect synchronization with game ticks
- ✓ No race conditions
- ✓ Actions execute at exact tick boundary
- ✓ Simpler code

---

## ISSUE #4: Eating Uses Blocking sleepUntil()

### Current (PROBLEMATIC):
```java
// File: MicroGauntletScript.java, Lines 209-269

private void handleEating() {
    int currentHp = Rs2Player.getSkillCurrent(Skill.HITPOINTS);
    int maxHp = Rs2Player.getSkillBase(Skill.HITPOINTS);

    // Emergency eat at 40% HP
    if (currentHp < maxHp * 0.4) {
        if (Rs2Inventory.hasItem("Cooked paddlefish")) {
            pvmCombat.queueAction(new Rs2PvMCombat.PvMAction() {
                @Override
                public boolean execute() {
                    Rs2Inventory.interact("Cooked paddlefish", "Eat");
                    // ✗ BLOCKS FOR UP TO 1200ms!
                    sleepUntil(() -> Rs2Player.getSkillCurrent(Skill.HITPOINTS) > currentHp, 1200);
                    return true;
                }

                @Override
                public int getPriority() {
                    return Rs2PvMCombat.Priority.CONSUMABLE;
                }

                @Override
                public String getDescription() {
                    return "Emergency eat (HP: " + currentHp + "/" + maxHp + ")";
                }

                @Override
                public boolean requiresNoTickLoss() {
                    return false;
                }
            });
        }
    }
    // Normal eat at 60% HP
    else if (currentHp < maxHp * 0.6) {
        if (Rs2Inventory.hasItem("Cooked paddlefish")) {
            pvmCombat.queueActionRelative(new Rs2PvMCombat.PvMAction() {
                @Override
                public boolean execute() {
                    Rs2Inventory.interact("Cooked paddlefish", "Eat");
                    // ✗ SAME BLOCKING ISSUE
                    sleepUntil(() -> Rs2Player.getSkillCurrent(Skill.HITPOINTS) > currentHp, 1200);
                    return true;
                }
                // ... rest unchanged ...
            }, 1);
        }
    }
}
```

### Problems:
1. **Blocks game thread** - Prevents other GameTick handlers from running
2. **Unreliable HP detection** - Network lag, animation delays
3. **Can't execute parallel actions** - Prayer flicking, dodging blocked
4. **Timing issues** - HP check happens multiple times, wastes CPU

### Fix:
```java
// File: MicroGauntletScript.java

private void handleEating() {
    int currentHp = Rs2Player.getSkillCurrent(Skill.HITPOINTS);
    int maxHp = Rs2Player.getSkillBase(Skill.HITPOINTS);

    // Emergency eat at 40% HP
    if (currentHp < maxHp * 0.4) {
        if (Rs2Inventory.hasItem("Cooked paddlefish")) {
            pvmCombat.queueAction(new Rs2PvMCombat.PvMAction() {
                @Override
                public boolean execute() {
                    // ✓ NO BLOCKING - Just interact
                    Rs2Inventory.interact("Cooked paddlefish", "Eat");
                    return true;
                }

                @Override
                public int getPriority() {
                    // ✓ HIGHER PRIORITY - Emergency should be handled first
                    return Rs2PvMCombat.Priority.DODGE;  // Priority 2, not CONSUME (5)
                }

                @Override
                public String getDescription() {
                    return "Emergency eat (HP: " + currentHp + "/" + maxHp + ")";
                }

                @Override
                public boolean requiresNoTickLoss() {
                    return false;
                }
            });
            
            // ✓ QUEUE FOLLOW-UP ATTACK AUTOMATICALLY
            // Paddlefish has 3-tick delay, so attack at +4 ticks
            queueActionRelative(createAttackAction(currentHunllef), 4);
        }
    }
    
    // Normal eat at 60% HP
    else if (currentHp < maxHp * 0.6) {
        if (Rs2Inventory.hasItem("Cooked paddlefish")) {
            pvmCombat.queueActionRelative(new Rs2PvMCombat.PvMAction() {
                @Override
                public boolean execute() {
                    // ✓ NO BLOCKING - Just interact
                    Rs2Inventory.interact("Cooked paddlefish", "Eat");
                    return true;
                }

                @Override
                public int getPriority() {
                    // ✓ HIGHER PRIORITY than before
                    return Rs2PvMCombat.Priority.WEAPON_SWITCH;  // Priority 3
                }

                @Override
                public String getDescription() {
                    return "Eat food (HP: " + currentHp + "/" + maxHp + ")";
                }

                @Override
                public boolean requiresNoTickLoss() {
                    return true;
                }
            }, 1);  // Queue for next tick
            
            // ✓ QUEUE FOLLOW-UP ATTACK AUTOMATICALLY
            // Paddlefish has 3-tick delay, so attack at +4 ticks relative to now
            queueActionRelative(createAttackAction(currentHunllef), 4);
        }
    }
}

// Helper method to create attack action
private Rs2PvMCombat.PvMAction createAttackAction(NPC hunllef) {
    return new Rs2PvMCombat.PvMAction() {
        @Override
        public boolean execute() {
            return Rs2Combat.attack(hunllef);
        }

        @Override
        public int getPriority() {
            return Rs2PvMCombat.Priority.ATTACK;
        }

        @Override
        public String getDescription() {
            return "Attack Hunllef (queued after food)";
        }

        @Override
        public boolean requiresNoTickLoss() {
            return true;
        }
    };
}
```

### Benefits:
- ✓ No blocking - Game thread stays responsive
- ✓ Higher priority eating - Executes before attacks
- ✓ Auto-queues follow-up attack - No timing delays
- ✓ Simpler logic - Let game loop handle delays
- ✓ Better reliability - No waiting for HP updates

---

## ISSUE #5: Eating Priority Too Low

### Current (WRONG):
```java
// File: MicroGauntletScript.java, Lines 253-254

@Override
public int getPriority() {
    return Rs2PvMCombat.Priority.CONSUMABLE;  // Priority 5 - LOWEST!
}
```

### Problem:
```
Priority order (lower = higher):
1. PRAYER
2. DODGE
3. WEAPON_SWITCH
4. ATTACK
5. CONSUMABLE  ← EATING IS LOWEST PRIORITY!

If all these are queued in same tick:
1. Prayer flick
2. Dodge
3. Switch weapon
4. Attack
5. EAT  ← Happens last!

Timeline:
t=0ms   Prayer flick (ok)
t=5ms   Dodge (ok)
t=10ms  Weapon switch (ok)
t=15ms  Attack (ok)
t=20ms  Eat  ← DELAYED! Player might already be damaged
```

### Fix:
```java
// For EMERGENCY eating (critical HP)
@Override
public int getPriority() {
    return Rs2PvMCombat.Priority.DODGE;  // Priority 2
}

// For NORMAL eating (routine healing)
@Override
public int getPriority() {
    return Rs2PvMCombat.Priority.WEAPON_SWITCH;  // Priority 3
}
```

This ensures eating happens early, before attacks are attempted.

---

## SUMMARY OF ISSUES & FIXES

| Issue | File | Lines | Severity | Fix |
|-------|------|-------|----------|-----|
| Karambwan delay 3 (should be 2) | ConsumableAction.java | 51, 74-75 | MEDIUM | Change `COMBO_FOOD(3)` to `COMBO_FOOD(2)` |
| Actions lost when skipped | Rs2PvMCombat.java | 138-142 | MEDIUM | Re-queue with `queueActionRelative(action, 1)` |
| Dual execution layers | MicroGauntletScript.java | 74-98 | HIGH | Replace ScheduledExecutor with GameTick subscription |
| Eating blocks with sleepUntil() | MicroGauntletScript.java | 220, 248 | MEDIUM | Remove sleepUntil(), queue async follow-up attack |
| Eating priority too low | MicroGauntletScript.java | 225-226, 253-254 | LOW | Use Priority.DODGE (2) or WEAPON_SWITCH (3) instead of CONSUMABLE (5) |

