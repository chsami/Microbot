# Gauntlet Plugin - Tick-Based Execution Model Analysis

## Overview

Comprehensive analysis of the tick-based execution model for the Gauntlet plugin, covering action queue architecture, eating delays, and attack timing issues.

## Key Findings

### Critical Issues Identified

1. **Dual Execution Layers** (HIGH SEVERITY)
   - ScheduledExecutor (600ms) not synchronized with GameTick events
   - Causes race condition and timing jitter
   - Actions can execute 600ms+ after queued

2. **Karambwan Delay Wrong** (MEDIUM SEVERITY)
   - Implementation: 3 ticks
   - OSRS Reality: 2 ticks
   - Impact: 1 tick DPS loss per karambwan eat

3. **Actions Lost on Tick Loss** (MEDIUM SEVERITY)
   - Skipped actions not re-queued
   - Can lose critical attacks
   - No retry mechanism

4. **Eating Blocks Game Thread** (MEDIUM SEVERITY)
   - sleepUntil() blocks for up to 1200ms
   - Prevents prayer flicking during eat
   - Unreliable HP detection

### Impact Assessment

- **DPS Loss**: ~0.5-1% from timing issues + karambwan bug
- **Responsiveness**: Eating blocks game thread for up to 1200ms
- **Reliability**: Actions can be lost due to tick loss
- **Synchronization**: No guaranteed tick-perfect execution

## Documentation Structure

### 1. ANALYSIS_SUMMARY.txt (This File)
Quick reference guide covering all key findings and recommendations.

### 2. tick_execution_analysis.md
**Comprehensive analysis with code references**
- 10 detailed sections
- 800+ lines of analysis
- Exact line number citations
- Complete architecture breakdown
- 9 specific recommendations with rationale

**Covers:**
- Action queue architecture
- Dual execution layer detailed timeline
- OSRS food mechanics vs implementation
- Attack timing analysis
- Tick loss detection mechanism
- Priority system behavior
- Eating implementation issues
- Tick loss state detection
- Summary tables
- Food delay reference

### 3. execution_flow_diagrams.txt
**ASCII flow diagrams for visual understanding**
- 8 detailed timing diagrams
- Shows race condition timing
- Action queue lifecycle visualization
- Karambwan bug impact diagram
- Tick loss state machine
- Priority execution order
- Script polling vs GameTick misalignment
- sleepUntil() blocking timeline
- Recommended architecture

### 4. code_issue_examples.md
**Code-level issue analysis with before/after examples**
- 5 detailed issues with code
- Exact file names and line numbers
- Before/after code comparisons
- Impact analysis for each fix
- Summary table of all issues

**Issues Covered:**
1. Karambwan delay (3→2 ticks)
2. Actions lost when skipped
3. Dual execution layers race condition
4. Eating blocks with sleepUntil()
5. Eating priority too low

## Quick Reference: The Issues

### Issue #1: Karambwan Delay
```
File: ConsumableAction.java:51, 74-75
Current: COMBO_FOOD(3)
Fix: COMBO_FOOD(2)
Impact: HIGH
```

### Issue #2: Action Skipping
```
File: Rs2PvMCombat.java:138-142
Current: return (action lost)
Fix: queueActionRelative(action, 1)
Impact: HIGH
```

### Issue #3: Dual Execution Layers
```
File: MicroGauntletScript.java:74-98
Current: ScheduledExecutor (separate thread)
Fix: @Subscribe onGameTick(GameTick event)
Impact: CRITICAL
```

### Issue #4: Eating Blocks Thread
```
File: MicroGauntletScript.java:220, 248
Current: sleepUntil(..., 1200)
Fix: Remove sleep, queue async follow-up
Impact: HIGH
```

### Issue #5: Eating Priority
```
File: MicroGauntletScript.java:225-226, 253-254
Current: Priority.CONSUMABLE (5)
Fix: Priority.DODGE (2) or WEAPON_SWITCH (3)
Impact: MEDIUM
```

## Action Queue Architecture Summary

### Queue Structure
- Thread-safe: `Map<Integer, List<PvMAction>>`
- Key: Absolute game tick number
- Value: List of actions to execute

### Queue Methods
- `queueAction(action, targetTick)` - Specific tick
- `queueAction(action)` - Next tick (currentTick + 1)
- `queueActionRelative(action, offset)` - Relative timing
- `queueActionSequence(actions, offsets)` - Multi-action sequences

### Execution Flow
1. Called from `Rs2PvMEventManager.onGameTick()`
2. Fetch actions for current tick from queue
3. Sort by priority (1=highest)
4. Execute each action in priority order

## OSRS Food Delay Reference

| Item | ID | Delay | Type |
|------|----|----|------|
| Cooked paddlefish | 23,544 | 3 ticks | Food |
| Crystal food | 23,981-24 | 2 ticks | Food |
| Karambwan | 3,144 | 2 ticks | Combo |
| Super restore | 3,025 | 3 ticks | Potion |
| Prayer potion | 139 | 3 ticks | Potion |
| Antidote++ | 5,952 | 3 ticks | Potion |

## Recommended Fixes (Priority Order)

### CRITICAL (Do First)
1. Replace ScheduledExecutor with GameTick subscription
2. Re-queue skipped actions
3. Remove sleepUntil() blocking

### HIGH (Do Next)
1. Correct Karambwan delay (3→2 ticks)
2. Increase eating priority

### MEDIUM (Future)
1. Add tick-count validation
2. Implement attack duplicate prevention
3. Add timing telemetry

## How to Use This Analysis

1. **For Quick Overview**: Read ANALYSIS_SUMMARY.txt (this file)
2. **For Code Fixes**: Read code_issue_examples.md
3. **For Architecture Understanding**: Read tick_execution_analysis.md
4. **For Visual Understanding**: Read execution_flow_diagrams.txt

## Key Files Analyzed

- `Rs2PvMCombat.java` - Action queue system
- `Rs2PvMEventManager.java` - GameTick event handler
- `MicroGauntletScript.java` - Main script loop
- `Rs2CombatHandler.java` - Combat timing and tick loss
- `Rs2ConsumableHandler.java` - Food consumption tracking
- `ConsumableAction.java` - Food delay definitions

## Key Code Locations

| Component | File | Lines |
|-----------|------|-------|
| Action Queue | Rs2PvMCombat.java | 42-127 |
| GameTick Handler | Rs2PvMEventManager.java | 298-340 |
| Script Loop | MicroGauntletScript.java | 74-98 |
| Eating Logic | MicroGauntletScript.java | 209-269 |
| Tick Loss Check | Rs2CombatHandler.java | 63-89 |
| Food Delays | ConsumableAction.java | 47-81 |
| Consumable Tracking | Rs2ConsumableHandler.java | 112-120 |

## Testing Recommendations

1. **Timing Accuracy Test**
   - Log action queue times
   - Compare with GameTick execution times
   - Measure execution latency

2. **Eating Delay Test**
   - Verify food delays with game tick count
   - Compare actual vs expected attack timing
   - Test karambwan combo eating

3. **Stress Test**
   - Queue multiple actions per tick
   - Verify priority ordering
   - Check for race conditions

4. **Blocking Test**
   - Monitor game thread during eating
   - Verify no tick skips
   - Measure execution time per tick

## Summary

The Gauntlet plugin's tick-based execution model has several timing issues:

1. **Architectural**: Dual execution layers (Script + GameTick) not synchronized
2. **Mechanical**: Karambwan delay hardcoded wrong (3 vs 2 ticks)
3. **Logical**: Skipped actions lost instead of re-queued
4. **Performance**: Eating blocks entire game thread

The recommended fixes address all these issues and improve reliability, responsiveness, and DPS consistency.

---

**Analysis Date**: 2025-11-08
**Branch**: PVM_utilty_Gauntlet
**Commit**: 192a287cb1

For detailed technical analysis, see the accompanying documentation files.
