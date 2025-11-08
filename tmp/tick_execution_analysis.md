# TICK-BASED EXECUTION MODEL ANALYSIS - GAUNTLET PLUGIN

## EXECUTIVE SUMMARY

The implementation uses a **hybrid two-layer execution model**:
1. **Game Tick Layer** - Synchronized with OSRS 600ms ticks (via `GameTick` event)
2. **Script Polling Layer** - Separate 600ms scheduled executor (ScheduledExecutorService)

This creates potential **race conditions and timing conflicts** between the two layers.

---

## 1. ACTION QUEUE ARCHITECTURE

### Rs2PvMCombat.java - Action Queue System
**File**: `/runelite-client/src/main/java/net/runelite/client/plugins/microbot/VoxPlugins/util/pvm/Rs2PvMCombat.java`

#### Queue Structure (Lines 42-46):
```java
// action queue (tick -> list of actions)
private final Map<Integer, List<PvMAction>> actionQueue = new ConcurrentHashMap<>();

// action history for debugging
private final Deque<ExecutedAction> actionHistory = new ConcurrentLinkedDeque<>();
```

#### Queue Methods:
- **`queueAction(PvMAction action, int targetTick)`** (Lines 62-65)
  - Queues action for absolute tick number
  - Uses `computeIfAbsent` for thread safety
  - No tick validation - can queue past ticks

- **`queueAction(PvMAction action)`** (Lines 70-73)
  - Queues for next tick (`currentTick + 1`)
  - Single-line default implementation

- **`queueActionRelative(PvMAction action, int tickOffset)`** (Lines 79-85)
  - Queues at `currentTick + tickOffset`
  - Used for timing-sensitive actions (e.g., +3 ticks for eating)

- **`queueActionSequence(...)`** (Lines 92-105)
  - Queues multiple actions with different tick offsets
  - Validates size match between actions and offsets

#### Key Combos (Lines 225-281):
```java
// Weapon switch: Switch tick 0, attack tick 1
queueActionSequence([switch, attack], [0, 1])

// Consume + Attack: Eat tick 0, attack tick 3 (3-tick delay)
queueActionSequence([consume, attack], [0, 3])

// Combo eat: Both same tick
queueActionSequence([eat, karambwan], [0, 0])

// Woox walk: Move on (weaponSpeed - 1) tick
List<Integer> offsets = Arrays.asList(0, moveOffset)
```

### Execution Method (Lines 111-127):
```java
public void executeQueuedActions() {
    int currentTick = Microbot.getClient().getTickCount();
    List<PvMAction> actions = actionQueue.remove(currentTick);  // FETCH FOR CURRENT TICK
    
    if (actions == null || actions.isEmpty()) {
        return;
    }
    
    // Sort by priority (lower = higher)
    actions.sort(Comparator.comparingInt(PvMAction::getPriority));
    
    for (PvMAction action : actions) {
        executeAction(action);  // Execute immediately
    }
}
```

**CRITICAL ISSUE**: `executeQueuedActions()` is called from **TWO different execution contexts**.

---

## 2. EXECUTION CONTEXT ANALYSIS

### Context 1: Game Tick Event (Primary - Synchronous)
**File**: `Rs2PvMEventManager.java` Lines 298-340

```java
@Subscribe
public void onGameTick(GameTick event) {
    if (!isActive.get()) return;
    
    long startTime = System.currentTimeMillis();
    
    // 1. PRAYER FLICKING (immediate, tick-perfect)
    if (prayerHandler != null) {
        prayerHandler.processFlicks();  // 0ms, on game thread
    }
    
    // 2. AUTOMATIC ACTIONS
    if (autoActionManager != null) {
        autoActionManager.processAutoActions();  // queues actions
    }
    
    // 3. EXECUTE QUEUED ACTIONS ← CRITICAL
    if (pvmCombat != null) {
        pvmCombat.executeQueuedActions();  // Executes at tick boundary
    }
    
    // 4. TRACKER CLEANUP
    if (projectileTracker != null) projectileTracker.onGameTick();
    // ... more trackers
    
    long executionTime = System.currentTimeMillis() - startTime;
    
    // ⚠️ WARNING IF > 600MS
    if (executionTime > 600) {
        log.warn("GameTick processing took {}ms (exceeded 600ms tick limit!)", executionTime);
    }
}
```

**Characteristics**:
- Fired by RuneLite's GameTick event (every 600ms)
- Runs on **game thread** (synchronous)
- Guaranteed at tick boundary
- If execution > 600ms, will block next tick

### Context 2: Script Scheduler (Secondary - Asynchronous)
**File**: `MicroGauntletScript.java` Lines 74-98

```java
mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
    if (!super.run()) return;
    if (BreakHandlerScript.isBreakActive()) return;
    
    try {
        // Check if in boss fight
        if (!Rs2GauntletUtil.isBossFightActive()) {
            if (fightActive) {
                stopTracking();
            }
            return;
        }
        
        // Start tracking when fight begins
        if (!fightActive) {
            startTracking();
        }
        
        // EXECUTE COMBAT LOOP ← Calls queueAction()
        executeCombatLoop();
        
    } catch (Exception ex) {
        log.error("gauntlet script error: {}", ex.getMessage(), ex);
    }
}, 0, 600, TimeUnit.MILLISECONDS);  // ← 600ms interval
```

**Characteristics**:
- Uses `ScheduledExecutorService` with **600ms fixed delay**
- Runs on **separate executor thread** (asynchronous)
- NOT synchronized with game tick events
- Can fire **before, after, or during** GameTick processing

### executeCombatLoop() (Lines 145-180):
```java
private void executeCombatLoop() {
    // ... find Hunllef ...
    
    // PRIORITY 1: Auto prayer
    pvmCombat.handleAutoPrayer();  // May skip
    
    // PRIORITY 2: Hazard dodging
    pvmCombat.handleAutoDodgeInRange(hunllef.getWorldLocation(), 6);
    
    // PRIORITY 3: Eating
    handleEating();  // ← QUEUES ACTION
    
    // PRIORITY 4: Weapon switching
    handleWeaponSwitching(hunllef);  // ← QUEUES ACTION
    
    // PRIORITY 5: Attacking
    handleAttacking(hunllef);  // ← QUEUES ACTION
}
```

Each of these calls `pvmCombat.queueAction()` or `pvmCombat.queueActionRelative()`.

---

## 3. EATING DELAYS - OSRS MECHANICS vs IMPLEMENTATION

### OSRS Food Mechanics:
| Food Type | Delay | Notes |
|-----------|-------|-------|
| Normal food (Gauntlet) | 3 ticks | Cooked paddlefish, trout |
| Karambwan | 2 ticks | Can combo eat (same tick) |
| Crystal food | 2 ticks | Gauntlet only |
| Super restore | 3 ticks | Restores prayer points |

### Current Implementation:

**File**: `ConsumableAction.java` Lines 47-81

```java
public enum ConsumableType {
    FOOD(3),              // regular food: 3 tick delay
    CRYSTAL_FOOD(2),      // gauntlet crystal food: 2 tick delay
    POTION(3),            // potions: 3 tick delay
    COMBO_FOOD(3),        // combo food (e.g., karambwan): 3 ticks  ← ⚠️ WRONG!
    UNKNOWN(3);           // default to 3 ticks
    
    private final int delayTicks;
    
    public static ConsumableType fromItemId(int itemId) {
        // gauntlet crystal food (perfected)
        if (itemId == 23981 || itemId == 23982 || itemId == 23983 || itemId == 23984) {
            return CRYSTAL_FOOD;  // 2 ticks ✓
        }
        
        // karambwan (combo food)
        if (itemId == 3144) {
            return COMBO_FOOD;  // 3 ticks ✗ SHOULD BE 2
        }
        
        // default to regular food or potion (3 ticks)
        return FOOD;
    }
}
```

**ISSUE #1 - Karambwan Delay**:
- Implementation: `COMBO_FOOD(3)` - 3 tick delay
- OSRS Reality: Karambwan is **2 ticks**, NOT 3
- Impact: Queues attacks 1 tick too late

**File**: `Rs2ConsumableHandler.java` Lines 112-120

```java
public boolean isLosingTickFromConsumable() {
    if (lastConsumableAction == null) {
        return false;
    }
    
    // Check if within delay window
    long tickDelayMs = lastConsumableAction.getDelayTicks() * 600L;  // ← USES DelayTicks
    return lastConsumableAction.getAgeMs() <= tickDelayMs;
}
```

Time-based tracking: `delayTicks * 600ms`

**Problem**: Relies on milliseconds, not game ticks
- Assumes perfect 600ms ticks
- Real game ticks can vary: 550-650ms
- No tick-count based validation

### Gauntlet Eating Implementation:

**File**: `MicroGauntletScript.java` Lines 209-269

```java
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
                    sleepUntil(() -> Rs2Player.getSkillCurrent(Skill.HITPOINTS) > currentHp, 1200);
                    return true;
                }
                
                @Override
                public int getPriority() {
                    return Rs2PvMCombat.Priority.CONSUMABLE;  // Priority 5
                }
                
                @Override
                public boolean requiresNoTickLoss() {
                    return false;  // Emergency doesn't wait
                }
            });
        }
    }
    // Normal eat at 60% HP
    else if (currentHp < maxHp * 0.6) {
        if (Rs2Inventory.hasItem("Cooked paddlefish")) {
            pvmCombat.queueActionRelative(new Rs2PvMCombat.PvMAction() {
                // ... same logic ...
                @Override
                public boolean requiresNoTickLoss() {
                    return true;  // ← Wait for safe tick
                }
            }, 1);  // Queue for +1 tick
        }
    }
}
```

**Issues**:
1. Emergency eat uses `queueAction()` (next tick) - no delay timing
2. Normal eat uses `queueActionRelative(..., 1)` - assumes 1 tick safe margin
3. No follow-up attack scheduling - relies on next loop iteration
4. `sleepUntil` is blocking - delays action execution

---

## 4. ATTACK TIMING

### Attack Frequency:

**File**: `MicroGauntletScript.java` Lines 338-364

```java
private void handleAttacking(NPC hunllef) {
    // Only queue attack if not already in combat
    if (!Rs2Combat.inCombat()) {
        pvmCombat.queueAction(new Rs2PvMCombat.PvMAction() {
            @Override
            public boolean execute() {
                Rs2Combat.attack(hunllef);
                return true;
            }
            
            @Override
            public int getPriority() {
                return Rs2PvMCombat.Priority.ATTACK;  // Priority 4
            }
            
            @Override
            public boolean requiresNoTickLoss() {
                return true;  // tick-perfect attacks for max DPS
            }
        });
    }
}
```

**Attack Scheduling**:
- Script loop runs every **600ms** (ScheduledExecutorService)
- Calls `queueAction()` if not in combat
- Gets queued for `currentTick + 1`
- GameTick event executes it on next tick

**Script Loop Frequency**:
```
Line 74-98 of MicroGauntletScript.java:
scheduleWithFixedDelay(() -> {
    // ... executeCombatLoop() ...
}, 0, 600, TimeUnit.MILLISECONDS)
```

| Execution | Frequency | Delay |
|-----------|-----------|-------|
| Script loop (executeCombatLoop) | 600ms fixed delay | ~0-100ms from GameTick |
| GameTick event (executeQueuedActions) | ~600ms (varies) | Tied to game tick boundary |
| Attack cycles | ~4-6 ticks (2.4-3.6s) | Based on weapon speed |

**Hunllef Attack Pattern**:
- 4 attacks (Range → Mage → Range → Mage)
- Each attack: ~3-4 ticks apart
- Player should attack every 4+ ticks (based on weapon)

---

## 5. TICK-BASED EXECUTOR ISSUES

### Problem 1: Two Execution Layers (Race Condition)

```
Timeline of race condition:

TIME    LAYER                   ACTION
────────────────────────────────────────────────────────────
t=0ms   ScheduledExecutor       executeCombatLoop() starts
t=5ms   ScheduledExecutor       queueAction(attack, tick=100)
        Script thread           ↓ Queue: tick 100 → [attack]

t=150ms RuneLite GameTick       onGameTick() fires
        Game thread             executeQueuedActions() for tick=99
                               (attack not queued yet for 99)

t=200ms ScheduledExecutor       executeCombatLoop() starts AGAIN
t=205ms ScheduledExecutor       queueAction(attack, tick=101)
        Script thread           ↓ Updates same queue
                               (10 checks might conflict here)

t=350ms RuneLite GameTick       onGameTick() fires
        Game thread             executeQueuedActions() for tick=100
                               ✓ Finds attack action!
```

**Issue**: 
- Script queues actions on **600ms cycle** (not tick-synchronized)
- GameTick executes actions **at tick boundaries** (600ms cycle but different phase)
- If script and tick are 100ms offset, action queues run 100ms apart
- Creates "jitter" in action timing

### Problem 2: No Tick-Perfect Synchronization

**File**: `Rs2CombatHandler.java` Lines 111-120

```java
public boolean isWeaponOnCooldown() {
    if (lastAttackTick < 0) {
        return false;
    }
    
    int currentTick = Microbot.getClient().getTickCount();
    int ticksSinceAttack = currentTick - lastAttackTick;
    
    return ticksSinceAttack < weaponAttackSpeed;  // ← Uses game tick count
}
```

**Problem**: Game tick count is absolute (never resets), but:
- `lastAttackTick` updated in `executeAction()` (might be off by 1 tick)
- `currentTick` fetched via `Microbot.getClient().getTickCount()`
- If action executes **mid-tick**, tick count might not have incremented yet

### Problem 3: Eating Blocks Action Execution

**File**: `MicroGauntletScript.java` Lines 245-250

```java
@Override
public boolean execute() {
    Rs2Inventory.interact("Cooked paddlefish", "Eat");
    sleepUntil(() -> Rs2Player.getSkillCurrent(Skill.HITPOINTS) > currentHp, 1200);
    return true;  // ← Blocks for up to 1200ms!
}
```

**Timeline**:
```
t=0ms      execute() called
t=5ms      interact() sent to RS client
t=50-100ms HP change detected, sleepUntil returns
t=1200ms   MAX wait - execute() finally returns!

← During this 1200ms, the GameTick event might fire again
  And try to execute other actions while eating action still blocking
```

This is **NOT truly non-blocking** - it sleeps the game thread during execution.

---

## 6. PRIORITY SYSTEM

### Action Priorities (Lines 423-429):

```java
public static class Priority {
    public static final int PRAYER = 1;         // survival - highest priority
    public static final int DODGE = 2;          // avoid damage
    public static final int WEAPON_SWITCH = 3;  // prepare attack
    public static final int ATTACK = 4;         // deal damage
    public static final int CONSUME = 5;        // healing/buffs - lowest priority
}
```

### Priority Execution (Lines 121-127):

```java
// Sort by priority (lower = higher)
actions.sort(Comparator.comparingInt(PvMAction::getPriority));

for (PvMAction action : actions) {
    executeAction(action);  // Execute in priority order
}
```

**Gauntlet Script Priorities**:
1. Auto prayer (`pvmCombat.handleAutoPrayer()`) - immediate
2. Hazard dodging (`handleAutoDodgeInRange()`) - immediate
3. Eating (`handleEating()`) - queues as CONSUME (priority 5)
4. Weapon switch (`handleWeaponSwitching()`) - queues as WEAPON_SWITCH (priority 3)
5. Attacking (`handleAttacking()`) - queues as ATTACK (priority 4)

**Issue**: Eating has **lowest priority** - but should be high priority if HP critical!

---

## 7. TICK LOSS DETECTION

### Implementation (Rs2CombatHandler.java Lines 63-89):

```java
public TickLossState getTickLossState() {
    // Check if just ate/drank (highest priority)
    if (consumableHandler != null && consumableHandler.isLosingTickFromConsumable()) {
        return TickLossState.LOSING;  // ← Used to skip tick-perfect actions
    }
    
    // Check if weapon switching (1-tick delay)
    if (weaponSwitchHandler != null && weaponSwitchHandler.isSwitchingWeapon()) {
        return TickLossState.POTENTIAL;
    }
    
    // Check if player is moving
    if (Rs2Player.isMoving()) {
        return TickLossState.POTENTIAL;
    }
    
    // Check if player is animating (excluding combat animations)
    if (Rs2Player.isAnimating()) {
        int animationId = Microbot.getClient().getLocalPlayer().getAnimation();
        // Combat animations are typically 400-500 range, allow those
        if (animationId != -1 && (animationId < 390 || animationId > 530)) {
            return TickLossState.POTENTIAL;
        }
    }
    
    return TickLossState.NONE;
}
```

**How it's used** (executeAction, Lines 138-142):

```java
if (action.requiresNoTickLoss() && tickLoss != TickLossState.NONE) {
    log.debug("Skipping action {} due to tick loss: {}", action.getDescription(), tickLoss);
    recordActionExecution(action, false, "Tick loss: " + tickLoss);
    return;  // ← SKIPS ACTION ENTIRELY
}
```

**Issue**: If action is skipped, it's **lost forever** - not re-queued for next tick!

---

## 8. KEY FINDINGS & CRITICAL ISSUES

### Issue #1: No Game Thread Synchronization
**Severity**: HIGH
- Script loop runs on separate ScheduledExecutor thread
- GameTick event runs on game thread
- No synchronization between the two
- Potential race condition in action queue access

**Code**: 
- `MicroGauntletScript.java` line 74 (ScheduledExecutor)
- `Rs2PvMEventManager.java` line 299 (GameTick)

### Issue #2: Karambwan Delay Wrong
**Severity**: MEDIUM
- Implementation: 3 ticks
- OSRS Reality: 2 ticks
- Causes 1-tick delay in attack timing after eating karambwan

**Code**: 
- `ConsumableAction.java` lines 51, 74-75

### Issue #3: Actions Skip When Tick Loss (Not Re-queued)
**Severity**: MEDIUM
- If action skipped due to tick loss, it's lost forever
- No mechanism to re-queue for next safe tick
- Can cause attacks to miss entire windows

**Code**:
- `Rs2PvMCombat.java` lines 138-142

### Issue #4: Eating Uses Sleep (Blocks Execution)
**Severity**: MEDIUM
- `sleepUntil()` blocks for up to 1200ms
- Prevents other actions from executing during eat
- Can interfere with game thread tick processing

**Code**:
- `MicroGauntletScript.java` lines 248, 220

### Issue #5: Eating Priority Too Low
**Severity**: LOW
- Emergency eat (40% HP) should be higher priority
- Currently priority 5 (lowest)
- Could get executed after damage if multiple actions queued

**Code**:
- `MicroGauntletScript.java` lines 225-226, 253-254

### Issue #6: Game Tick Processing Warning at 600ms
**Severity**: LOW (informational)
- Log warns if processing takes > 600ms
- But that's exactly 1 full game tick!
- Should probably be > 300ms warning (50% of tick)

**Code**:
- `Rs2PvMEventManager.java` lines 334-339

---

## 9. DETAILED RECOMMENDATIONS

### Fix #1: Align Script Loop with Game Ticks
Replace ScheduledExecutor with GameTick subscription:

```java
// Instead of:
mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
    executeCombatLoop();
}, 0, 600, TimeUnit.MILLISECONDS);

// Do:
@Subscribe
public void onGameTick(GameTick event) {
    if (!fightActive) return;
    executeCombatLoop();
}
```

### Fix #2: Correct Karambwan Delay
```java
// Change from:
COMBO_FOOD(3),

// To:
COMBO_FOOD(2),  // Karambwan is 2 ticks
```

### Fix #3: Re-queue Skipped Actions
```java
// Change from:
if (action.requiresNoTickLoss() && tickLoss != TickLossState.NONE) {
    log.debug("Skipping action {} due to tick loss", action.getDescription());
    recordActionExecution(action, false, "Tick loss");
    return;  // ← Lost forever!
}

// To:
if (action.requiresNoTickLoss() && tickLoss != TickLossState.NONE) {
    log.debug("Deferring action {} due to tick loss", action.getDescription());
    queueActionRelative(action, 1);  // ← Re-queue for next tick
    return;
}
```

### Fix #4: Use Async Eating (Don't Sleep)
```java
// Instead of sleeping:
Rs2Inventory.interact("Cooked paddlefish", "Eat");
sleepUntil(..., 1200);  // ← Blocks

// Queue a follow-up attack:
queueActionRelative(createAttackAction(hunllef), 3);  // +3 ticks after eat
// Game loop will naturally handle the delay
```

### Fix #5: Prioritize Emergency Eating
```java
// For emergency eat, use higher priority:
@Override
public int getPriority() {
    return Rs2PvMCombat.Priority.DODGE;  // Priority 2, not CONSUME (5)
}
```

---

## 10. OSRS FOOD DELAY REFERENCE

For future expansion, correct delay values:

| Item | ID | Delay Ticks | Type |
|------|----|----|------|
| Cooked paddlefish | 23,544 | 3 | Food |
| Crystal food perfected | 23,981-24 | 2 | Food |
| Karambwan | 3,144 | 2 | Combo |
| Super restore | 3,025 | 3 | Potion |
| Prayer potion | 139 | 3 | Potion |
| Antidote++ | 5,952 | 3 | Potion |

All delays measured in game ticks (600ms each).

---

## SUMMARY TABLE

| Aspect | Current | Issues | Recommended |
|--------|---------|--------|-------------|
| **Execution Model** | Dual thread (Scheduled + GameTick) | Race condition risk | Single GameTick subscription |
| **Loop Frequency** | 600ms fixed delay | Not tick-synchronized | Sync to GameTick event |
| **Eating Delays** | `FOOD(3), COMBO_FOOD(3), CRYSTAL(2)` | Karambwan = 3 (wrong) | Change COMBO to 2 ticks |
| **Eating Method** | `sleepUntil()` blocking | Blocks game thread | Queue async follow-up |
| **Skipped Actions** | Lost forever | No retry mechanism | Re-queue for next tick |
| **Tick Loss Check** | Based on timestamps | Millisecond precision needed | Use game tick count |
| **Attack Queuing** | Every 600ms if not in combat | Can queue multiple times | Track last queue time |

