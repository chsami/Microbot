# Auto World Hopper Plugin

An automated world hopping plugin for RuneLite that switches worlds based on configurable triggers.

## Features

### World Selection
- **World Type Filter**: Choose between Free worlds, Members worlds, or both
- **Avoid PvP Worlds**: Skip dangerous PvP and high-risk worlds
- **Avoid Skill Total Worlds**: Skip worlds with skill total requirements

### Hop Triggers

#### 1. Player Detection
- Automatically hop when too many players are detected nearby
- Configurable detection radius (5-50 tiles)
- Set maximum player count (0 to disable)

#### 2. Time-based Hopping  
- Hop worlds after a specified time interval
- Configurable interval from 1-60 minutes
- Shows countdown timer in overlay

#### 3. Chat Detection
- Hop when someone speaks in public chat
- Option to ignore friends when they speak
- Instant hopping when triggered

### Advanced Settings
- **Hop Cooldown**: Minimum time between hops to prevent spam (1-10 seconds)
- **Random Delay**: Add randomization before hopping (0-30 seconds)
- **Notifications**: Show chat messages when hopping worlds
- **Debug Mode**: Display additional information in overlay
- **Skip Worlds**: Comma-separated list of world IDs to never hop to (e.g. "301, 302, 303")

## Configuration

### World Settings
- **Enable Auto World Hopper**: Master on/off switch
- **World Type**: Free/Members/Both filter
- **Avoid PvP Worlds**: Skip dangerous worlds  
- **Avoid Skill Total Worlds**: Skip restricted worlds

### Hop Triggers
- **Enable Player Detection**: Monitor nearby players
- **Max Players Nearby**: Threshold for player detection (0 = disabled)
- **Detection Radius**: Area to scan for players
- **Enable Time-based Hopping**: Hop after set intervals
- **Hop Interval**: Minutes between automatic hops
- **Enable Chat Detection**: Monitor public chat
- **Ignore Friends**: Don't hop when friends speak

### Advanced
- **Hop Cooldown**: Minimum seconds between hops
- **Random Delay**: Maximum random delay before hopping
- **Show Notifications**: Display hop messages in chat
- **Debug Mode**: Show detailed overlay information
- **Skip Worlds**: World IDs to never hop to (comma separated)

## Usage

1. Enable the plugin in the RuneLite plugin panel
2. Configure your desired world type and triggers
3. The plugin will automatically hop worlds based on your settings
4. Monitor status through the overlay (top-left corner)
5. Adjust settings as needed during gameplay

## Overlay Information

The overlay shows:
- Current status (Running/Paused/Stopped)
- Current world number  
- Time until next automatic hop (if enabled)
- Current player count vs threshold (if enabled)
- Debug information (if debug mode enabled)

## Safety Features

- Cooldown system prevents rapid world hopping
- Random delays reduce detection patterns
- Respects world capacity limits
- Avoids problematic world types by default

## Notes

- Plugin respects RuneLite's world hopping limitations
- Works while logged in or on login screen
- Automatically pauses when logged out
- Resets timers when logging back in

## Version History

### v1.0.0
- Initial release
- Player detection hopping
- Time-based hopping  
- Chat detection hopping
- World type filtering
- Safety features and cooldowns
