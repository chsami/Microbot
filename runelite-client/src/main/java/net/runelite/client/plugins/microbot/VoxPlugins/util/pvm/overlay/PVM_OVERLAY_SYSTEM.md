# PvM Overlay System

## Overview

Comprehensive overlay system for the PvM combat utility that visualizes tracked data with minimal screen clutter. Provides configurable real-time indicators for projectiles, hazards, prayers, and combat actions.

## Architecture

### Components

1. **Rs2PvMOverlayConfig** - Configuration interface for all overlay settings
2. **Rs2PvMSceneOverlay** - In-world scene overlay for projectiles and hazards
3. **Rs2PvMInfoOverlay** - Information panel for combat stats and actions

### Design Philosophy

- **Minimal Clutter**: Only show essential information
- **Configurable Display**: Every visual element can be toggled
- **Color-Coded**: Intuitive color system for quick recognition
- **Performance-Optimized**: Efficient rendering with lazy evaluation

## Configuration Options

### General Settings

- **Enable overlay**: Master toggle for entire overlay system
- **Show info panel**: Toggle information panel display

### Projectile Tracking

```java
projectileStyle:
  - TARGET_TILE: Highlight where projectile will land (default)
  - CURRENT_POSITION: Show real-time projectile position
  - FULL_PATH: Display trajectory from current position to target
```

**Colors:**
- AOE Projectiles (ground-targeted): Orange (255, 100, 0)
- Player-Targeted Projectiles: Purple (200, 0, 255)

**Display Options:**
- Show projectile ID and impact time
- Adjustable outline width (1-5 pixels)

### Hazard Tracking

**Features:**
- Highlight dangerous tiles (hazards)
- Show safe tiles for dodging
- Configurable colors and outline width

**Colors:**
- Hazard Tiles: Red (255, 0, 0, 120)
- Safe Tiles: Green (0, 255, 0, 100)

### Prayer Tracking

**Features:**
- Show suggested prayer based on incoming projectiles
- Highlight currently active protection prayer
- Adjustable text size (12-24pt)

**Color System:**
- Suggested (Active): Green
- Suggested (Inactive): Orange  
- Currently Active: Cyan

### Action Tracking

**Features:**
- Show queued actions for current tick
- Display recent action history
- Configurable history size (3-20 entries)

**Priority Colors:**
- Prayer (1): Red
- Dodge (2): Orange
- Weapon Switch (3): Yellow
- Attack (4): Green
- Consume (5): Cyan

### Tick Loss Tracking

**Features:**
- Display current tick loss state
- Optional visual warning when tick loss detected

## Scene Overlay

### Projectile Rendering

#### Target Tile Mode (Default)
Renders tile outline where projectile will impact:
```java
- Polygon outline on target tile
- Color based on projectile type (AOE vs player-targeted)
- Optional info text showing ID and impact time
```

#### Current Position Mode
Shows real-time projectile location:
```java
- Circle marker at current projectile position
- Filled center with semi-transparent color
- Reference target tile for context
```

#### Full Path Mode
Displays complete trajectory:
```java
- Dashed line from current position to target
- Current position marker
- Target tile highlight
```

### Hazard Rendering

**Dangerous Tiles:**
- Polygon outline with fill color
- Configurable outline width
- Semi-transparent to avoid obscuring game view

**Safe Tiles (Optional):**
- 3x3 area around player
- Subtle green highlight
- No outline for minimal visual noise

## Info Panel Overlay

### Layout

```
┌─────────────────────┐
│   PvM Combat        │ ← Title (Cyan)
├─────────────────────┤
│ Projectiles:   2    │ ← Tracking Stats
│ Hazards:       3    │
├─────────────────────┤
│ Suggested:  Mage    │ ← Prayer Suggestion
│ Active:     Range   │
├─────────────────────┤
│ Tick Loss:  NONE    │ ← Tick Loss State
├─────────────────────┤
│ Queued:      2      │ ← Action Queue
│   1. Dodge          │
│   2. Attack         │
├─────────────────────┤
│ History:            │ ← Action History
│   Dodge         ✓   │
│   Prayer Switch ✓   │
│   Attack        ✗   │
└─────────────────────┘
```

### Position

- Top-left corner of screen
- Above game widgets
- Minimal footprint

## Integration

### Adding to Plugin

```java
@Inject
private OverlayManager overlayManager;

@Inject
private Rs2PvMSceneOverlay sceneOverlay;

@Inject
private Rs2PvMInfoOverlay infoOverlay;

@Override
protected void startUp() {
    overlayManager.add(sceneOverlay);
    overlayManager.add(infoOverlay);
}

@Override
protected void shutDown() {
    overlayManager.remove(sceneOverlay);
    overlayManager.remove(infoOverlay);
}
```

### Configuration Binding

The overlays automatically read from Rs2PvMOverlayConfig. No manual binding required - Guice handles dependency injection.

## Usage Examples

### Example 1: Gauntlet Hunllef

```java
// register projectile prayers
prayerHandler.registerProjectilePrayer(1707, Rs2PrayerEnum.PROTECT_MAGIC);  // mage attack
prayerHandler.registerProjectilePrayer(1711, Rs2PrayerEnum.PROTECT_RANGE);  // range attack

// configure overlay
config.showProjectiles(true);
config.projectileStyle(ProjectileStyle.TARGET_TILE);
config.showHazards(true);  // for tornadoes
config.showPrayerSuggestion(true);
```

**Overlay Display:**
- Orange outlines on tornado tiles (AOE hazards)
- Purple outlines on player-targeted projectile impacts
- Prayer suggestion updates 2 ticks before impact
- Safe tiles highlighted when dodging required

### Example 2: Colosseum

```java
// configure for multi-threat scenario
config.showProjectiles(true);
config.projectileStyle(ProjectileStyle.CURRENT_POSITION);  // fast-moving projectiles
config.showActionQueue(true);  // show queued dodges/prayers
config.showTickLoss(true);  // critical for timing

// enable safe tile display
config.showSafeTiles(true);
```

**Overlay Display:**
- Real-time projectile positions with trajectory lines
- Action queue showing next 3 queued actions
- Tick loss warning if detected
- Green highlights on nearby safe tiles

### Example 3: Inferno

```java
// minimal overlay for complex scenarios
config.showProjectiles(true);
config.projectileStyle(ProjectileStyle.TARGET_TILE);
config.showProjectileInfo(false);  // reduce clutter
config.showPrayerSuggestion(true);
config.showActionQueue(false);  // manual control
```

**Overlay Display:**
- Simple tile outlines for prayer flick timing
- Prayer suggestion without action history
- Minimal info panel for focus

## Performance Considerations

### Optimization Strategies

1. **Lazy Rendering**: Only render enabled features
2. **Cache Iteration**: Use Rs2Cache.values() efficiently
3. **Early Returns**: Skip rendering if tracking disabled
4. **Polygon Reuse**: OverlayUtil handles canvas polygon caching

### Render Order

```
1. Hazards (background layer)
2. Safe tiles (mid layer)
3. Projectiles (foreground layer)
```

This ensures projectiles are always visible over hazards.

## Troubleshooting

### Projectiles Not Showing

**Check:**
1. Is projectile registered in ProjectileRegistry?
2. Is config.showProjectiles() enabled?
3. Is projectile expired (past impact time)?

### Prayer Suggestion Wrong

**Check:**
1. Is projectile registered with correct prayer in Rs2PrayerHandler?
2. Is projectile player-targeted (not AOE)?
3. Is projectile within tick buffer (default 2 ticks)?

### Hazards Not Highlighted

**Check:**
1. Is hazard registered in Rs2HazardTracker?
2. Is config.showHazards() enabled?
3. Is hazard expired (past duration)?

## Future Enhancements

### Planned Features

1. **Customizable Colors**: Per-projectile color configuration
2. **Animation Overlay**: Show NPC attack animations
3. **Path Validation**: Highlight blocked vs clear paths
4. **Weapon Cooldown**: Display attack timer overlay
5. **Consumable Timers**: Show food/potion cooldowns

### API Extensions

```java
// proposed API for future features
public interface Rs2PvMOverlayExtension {
    void registerCustomRenderer(String id, OverlayRenderer renderer);
    void registerColorOverride(int projectileId, Color color);
    void registerTextOverride(int projectileId, Function<ProjectileData, String> formatter);
}
```

## Credits

Based on overlay patterns from:
- The Gauntlet plugin (projectile/hazard rendering)
- QoL plugin (scene overlay architecture)
- DevTools plugin (positioning and text rendering)

Implements position tracking from PROJECTILE_POSITION_TRACKING_GUIDE.md
