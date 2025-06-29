# Scheduler Plugin

## Overview

The `SchedulerPlugin` class is the central orchestrator of the Plugin Scheduler system. It manages the automated execution of plugins based on configurable conditions and schedules, providing a comprehensive framework for automating RuneLite plugins in a controlled, prioritized, and natural-appearing manner.

## Component Relationships

The scheduler system consists of three main components that work together:

| Component | Role | Responsibility |
|-----------|------|----------------|
| `SchedulerPlugin` | **Orchestrator** | Coordinates the overall scheduling process, state transitions, and integration with other systems |
| `PluginScheduleEntry` | **Data Model** | Holds configuration and execution state for each scheduled plugin |
| `SchedulablePlugin` | **Interface** | Implemented by RuneLite plugins to define start/stop conditions and handle events |

### Component Interaction Flow

```ascii
┌────────────────┐       registers       ┌──────────────────┐
│                │◄──────────────────────┤                  │
│                │                       │                  │
│  SchedulerPlugin  schedules/evaluates  │ PluginScheduleEntry │
│                │─────────────────────▶│                  │
│                │                       │                  │
└────────┬───────┘                       └────────┬─────────┘
         │                                        │
         │ manages                                │ references
         │                                        │
         ▼                                        ▼
┌────────────────┐                      ┌──────────────────┐
│                │                      │                  │
│ Regular RuneLite│  implements         │ SchedulablePlugin │
│    Plugin      │◄─────────────────────┤     (API)        │
│                │                      │                  │
└────────────────┘                      └──────────────────┘
```

## Configuration Options

The SchedulerPlugin offers extensive configuration options organized into four main sections:

### Control Settings

Controls the core behavior of the scheduler:

| Setting | Description | Default |
|---------|-------------|---------|
| Soft Stop Retry (seconds) | Time between attempts to gracefully stop a plugin | 60 seconds |
| Enable Hard Stop | When enabled, forcibly stops plugins if they don't respond to soft stop | Disabled |
| Hard Stop Timeout (seconds) | Time to wait before forcing a hard stop | 0 seconds |
| Manual Start Threshold (minutes) | Minimum time until next scheduled plugin before manual start is allowed | 1 minute |
| Prioritize Non-Default Plugins | Stop default plugins when non-default plugins are due soon | Enabled |
| Non-Default Plugin Look-Ahead (minutes) | Time window to check for upcoming non-default plugins | 1 minute |
| Notifications On | Enable notifications for scheduler events | Disabled |

### Conditions Settings

Controls how the scheduler enforces conditions:

| Setting | Description | Default |
|---------|-------------|---------|
| Enforce Stop Conditions | Prompt before running plugins without time-based stop conditions | Enabled |
| Dialog Timeout (seconds) | Time before the 'No Stop Conditions' dialog auto-closes | 30 seconds |
| Config Timeout (seconds) | Time to wait for user to add stop conditions before canceling | 60 seconds |

### Log-In Settings

Controls login behavior of scheduled plugins:

| Setting | Description | Default |
|---------|-------------|---------|
| Enable Auto Log In | Enable auto-login before starting a plugin | Disabled |
| Auto Log In World | World to log into (0 for random) | 0 |
| World Type | Type of world to log into (0: F2P, 1: P2P, 2: Any) | 2 (Any) |
| Auto Log Out on Stop | Automatically log out when stopping the scheduler | Disabled |

### Break Settings

Controls break behavior between plugin executions:

| Setting | Description | Default |
|---------|-------------|---------|
| BreakHandler on Start | Automatically enable BreakHandler when starting a plugin | Enabled |
| Break During Wait | Take breaks when waiting for the next scheduled plugin | Enabled |
| Min Break Duration (minutes) | Minimum duration of breaks between schedules | 2 minutes |
| Max Break Duration (minutes) | Maximum duration of breaks between schedules | 2 minutes |
| Log Out During A Break | Automatically log out during breaks | Disabled |
| Use Play Schedule | Enable play schedule to control when scheduler is active | Disabled |
| Play Schedule | Select pre-defined play schedule pattern | Medium Day |

## User Interface

The SchedulerPlugin provides a comprehensive user interface for managing scheduled plugins through several key components:

### Main Scheduler Window

The `SchedulerWindow` is the primary interface for managing the plugin scheduler. It contains:

- **Schedule Tab**: Displays a table of all scheduled plugins and their status
- **Start Conditions Tab**: Configure when plugins should start running
- **Stop Conditions Tab**: Configure when running plugins should stop
- **Information Panel**: Shows real-time scheduler status and statistics

### Schedule Table Panel

The `ScheduleTablePanel` displays all scheduled plugins with the following information:

- Plugin name
- Schedule type
- Next run time
- Start/Stop conditions
- Priority
- Enabled status
- Run count

Special visual indicators help identify:
- Currently running plugin (purple highlight)
- Next scheduled plugin (amber highlight)
- Plugins with met/unmet conditions (green/red indicators)

### Schedule Form Panel

The `ScheduleFormPanel` allows users to add, edit, and remove scheduled plugins. It provides:

- Plugin selection dropdown
- Time condition configuration
- Priority setting
- Randomization options
- Default plugin status toggle
- Allow continue after interruption option

When editing an existing plugin, additional statistics are shown:
- Total runs
- Last run time
- Last run duration 
- Last stop reason

### Condition Configuration

The condition configuration interface allows users to create complex logical conditions for starting and stopping plugins:

- **Time Conditions**: Specific times, intervals, time windows, days of week
- **Game State Conditions**: Player status, inventory contents, skill levels
- **Logical Operators**: AND, OR, NOT for combining conditions
- **Lock Conditions**: Prevent plugins from stopping during critical operations

## Plugin Schedule Entry Model

The `PluginScheduleEntry` class is the core data model representing a scheduled plugin:

### Key Properties

| Property | Description |
|----------|-------------|
| `name` | Name of the plugin to schedule |
| `enabled` | Whether this schedule entry is active |
| `allowRandomScheduling` | Whether this plugin can be scheduled randomly |
| `isDefault` | Whether this is a default plugin (lower priority) |
| `priority` | Numeric priority (higher values = higher priority) |
| `allowContinue` | Whether to resume after interruption |
| `startConditions` | Logical conditions that determine when plugin starts |
| `stopConditions` | Logical conditions that determine when plugin stops |

### Statistics Tracking

Each entry tracks comprehensive statistics:
- Run count
- Last run time
- Last run duration
- Last stop reason
- Success/failure status

### Stop Reason Types

The system tracks why plugins stop:
- `NONE`: Not stopped yet
- `MANUAL_STOP`: User manually stopped the plugin
- `PLUGIN_FINISHED`: Plugin completed its task normally
- `ERROR`: Error occurred while running
- `SCHEDULED_STOP`: Stop conditions were met
- `INTERRUPTED`: Externally interrupted
- `HARD_STOP`: Forcibly stopped after timeout

## Adding New Schedule Entries

New schedule entries can be added through the `ScheduleFormPanel`:

1. **Select Plugin**: Choose a plugin from the dropdown menu
2. **Set Priority**: Adjust the priority spinner (higher values = higher priority)
3. **Configure Time Conditions**: Choose from:
   - Run Default: Use plugin's built-in schedule
   - Run at Specific Time: Run at a particular time of day
   - Run at Interval: Run at regular intervals
   - Run in Time Window: Run during specific hours
   - Run on Day of Week: Run on particular days
4. **Optional Settings**:
   - Random Scheduling: Allow the scheduler to choose this plugin randomly
   - Default Plugin: Set as a default (lower priority) plugin
   - Time-based Stop: Configure automatic stop after running for some time
   - Allow Continue: Resume after interruption
5. **Click Add**: Add the plugin to the schedule

## State Management and Execution Flow

The SchedulerPlugin implements a sophisticated state machine that manages the entire plugin scheduling life cycle through the `SchedulerState` enum.

### State Categories and Relationships

The scheduler's states can be organized into four functional categories:

#### 1. Initialization States
- **UNINITIALIZED**: Initial state before the plugin is ready
- **INITIALIZING**: Loading required dependencies and preparing to run
- **READY**: Fully initialized and waiting for user activation

#### 2. Active Scheduling States
- **SCHEDULING**: Actively monitoring schedules
- **STARTING_PLUGIN**: Beginning execution of a scheduled plugin
- **RUNNING_PLUGIN**: Plugin is currently executing
- **SOFT_STOPPING_PLUGIN**: Gracefully requesting a plugin to stop
- **HARD_STOPPING_PLUGIN**: Forcefully stopping a plugin

#### 3. Waiting States
- **WAITING_FOR_LOGIN**: Waiting for user login before starting a plugin
- **LOGIN**: Currently in the process of logging in
- **WAITING_FOR_STOP_CONDITION**: Waiting for user to configure stop conditions
- **WAITING_FOR_SCHEDULE**: Waiting for the next scheduled plugin
- **BREAK**: Taking a configured break between plugin executions
- **PLAYSCHEDULE_BREAK**: Taking a break based on play schedule settings

#### 4. Control States
- **HOLD**: Scheduler manually paused by user
- **ERROR**: An error occurred that prevents normal operation

### State Transitions Diagram

```ascii
┌───────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                         INITIALIZATION STATES                                     │
├───────────────┐                 ┌───────────────┐                 ┌───────────────┐               │
│ UNINITIALIZED │────────────────▶│ INITIALIZING  │────────────────▶│    READY      │               │
└───────────────┘                 └───────────────┘                 └───────┬───────┘               │
                                                                            │                       │
┌──────────────────────────────────────────────────────────────────┐        │                       │
│                      WAITING STATES                              │        │                       │
│  ┌────────────────┐        ┌────────────┐       ┌────────────┐   │        │                       │
│  │WAITING_FOR_    │◀──────▶│   LOGIN    │──────▶│WAITING_FOR_│   │        │                       │
│  │   LOGIN        │        └────────────┘       │   STOP     │   │        │                       │
│  └─────────┬──────┘                             │ CONDITION  │   │        │                       │
│            │                                    └─────┬──────┘   │        │                       │
│  ┌─────────▼──────┐         ┌────────────┐            │          │        │                       │
│  │WAITING_FOR_    │         │   BREAK    │◀───────────┘          │        │                       │
│  │  SCHEDULE      │◀────────┤            │                       │        │                       │
│  └─────────┬──────┘         └─────┬──────┘                       │        │                       │
│            │                      │                              │        │                       │
│  ┌─────────▼──────┐         ┌─────▼──────┐                       │        │                       │
│  │PLAYSCHEDULE_   │         │ SCHEDULING │◀──────────────────────┼────────┘                       │
│  │   BREAK        │◀────────┤            │                       │                               │
│  └───────────────┬┘         └─────┬──────┘                       │                               │
└───────────────────────────────────┘                              │                               │
                                                                   │                               │
┌───────────────────────────────────────────────────────┐          │                               │
│                ACTIVE SCHEDULING STATES               │          │                               │
│                         ┌────────────────┐            │          │                               │
│                         │ STARTING_PLUGIN│◀───────────┼──────────┘                               │
│                         └────────┬───────┘            │                                          │
│                                  │                    │                                          │
│                         ┌────────▼───────┐            │         ┌────────────────────────────────┐
│                         │ RUNNING_PLUGIN │            │         │      CONTROL STATES            │
│                         └────────┬───────┘            │         │  ┌────────────┐  ┌──────────┐  │
│                                  │                    │         │  │    HOLD    │  │  ERROR   │  │
│                         ┌────────▼───────┐            │         │  └────────────┘  └──────────┘  │
│                         │SOFT_STOPPING_  │            │         └────────────────────────────────┘
│                         │   PLUGIN       │            │
│                         └────────┬───────┘            │
│                                  │                    │
│                         ┌────────▼───────┐            │
│                         │HARD_STOPPING_  │            │
│                         │   PLUGIN       │            │
│                         └────────────────┘            │
└───────────────────────────────────────────────────────┘
```

### State Descriptions and Transition Logic

| State | Description | Entry Conditions | Exit Conditions | Helper Methods |
|-------|-------------|------------------|-----------------|----------------|
| UNINITIALIZED | Default state when plugin is loaded but not ready | Initial state | Transitions to INITIALIZING when plugin starts | isInitializing() |
| INITIALIZING | Loading required plugins and setting up | From UNINITIALIZED on startup | Transitions to READY when dependencies are loaded | isInitializing() |
| READY | Ready but not actively scheduling | After successful initialization | Transitions to SCHEDULING when user activates scheduler | !isSchedulerActive() |
| SCHEDULING | Actively monitoring for plugins to run | From READY when activated, or after breaks/waiting | To WAITING states or STARTING_PLUGIN | isSchedulerActive(), isWaiting() |
| STARTING_PLUGIN | In process of starting a plugin | When conditions are met to start a plugin | To RUNNING_PLUGIN when started successfully | isAboutStarting() |
| RUNNING_PLUGIN | A scheduled plugin is currently running | After plugin successfully starts | To SOFT_STOPPING_PLUGIN when stop conditions met | isActivelyRunning() |
| WAITING_FOR_LOGIN | Plugin needs login before running | When plugin requires login but user is not logged in | To LOGIN when login process begins | isAboutStarting() |
| LOGIN | Currently logging in | From WAITING_FOR_LOGIN | To STARTING_PLUGIN after successful login | N/A |
| WAITING_FOR_STOP_CONDITION | Awaiting user to add stop conditions | When plugin has no stop conditions | To STARTING_PLUGIN when conditions added | isAboutStarting() |
| SOFT_STOPPING_PLUGIN | Requesting plugin to stop gracefully | When stop conditions are met | To HARD_STOPPING_PLUGIN on timeout or successful stop | isStopping() |
| HARD_STOPPING_PLUGIN | Forcing plugin to stop | After soft stop timeout or when hard stop requested | To SCHEDULING or ERROR | isStopping() |
| BREAK | Taking scheduled break between plugins | After plugin completes and break is needed | To SCHEDULING when break duration completes | isWaiting(), isBreaking() |
| PLAYSCHEDULE_BREAK | Break due to play schedule settings | When outside allowed play hours | To SCHEDULING when entering allowed play hours | isWaiting(), isBreaking() |
| WAITING_FOR_SCHEDULE | Waiting for next scheduled plugin | When no plugins are ready to run currently | To SCHEDULING when schedule time approaches | isWaiting() |
| ERROR | Error occurred during scheduling | On exception or plugin error | After error is acknowledged | N/A |
| HOLD | Scheduler manually paused | User requested pause | When user resumes scheduling | N/A |

### State Helper Methods

The `SchedulerState` enum provides helper methods to classify states into functional groups, making it easier to check the scheduler's current status:

```java
// Returns true if the scheduler is active (not in initialization, hold, or error states)
public boolean isSchedulerActive() {
    return this != SchedulerState.UNINITIALIZED &&
           this != SchedulerState.INITIALIZING &&
           this != SchedulerState.ERROR &&
           this != SchedulerState.HOLD &&
           this != SchedulerState.READY;
}

// Returns true if a plugin is currently running
public boolean isActivelyRunning() {
    return isSchedulerActive() && 
           (this == SchedulerState.RUNNING_PLUGIN);
}

// Returns true if scheduler is about to start a plugin
public boolean isAboutStarting() {
    return this == SchedulerState.STARTING_PLUGIN || 
           this == SchedulerState.WAITING_FOR_STOP_CONDITION ||
           this == SchedulerState.WAITING_FOR_LOGIN;
}

// Returns true if scheduler is waiting between plugin executions
public boolean isWaiting() {
    return isSchedulerActive() &&
           (this == SchedulerState.SCHEDULING ||
            this == SchedulerState.WAITING_FOR_SCHEDULE ||
            this == SchedulerState.BREAK || 
            this == SchedulerState.PLAYSCHEDULE_BREAK);
}

// Returns true if scheduler is in a break state
public boolean isBreaking() {
    return (this == SchedulerState.BREAK || 
            this == SchedulerState.PLAYSCHEDULE_BREAK);
}

// Returns true if scheduler is stopping a plugin
public boolean isStopping() {
    return this == SchedulerState.SOFT_STOPPING_PLUGIN ||
           this == SchedulerState.HARD_STOPPING_PLUGIN;
}

// Returns true if scheduler is initializing
public boolean isInitializing() {
    return this == SchedulerState.INITIALIZING || 
           this == SchedulerState.UNINITIALIZED;
}
```

## Class Structure

```java
@Slf4j
@PluginDescriptor(
    name = PluginDescriptor.Mocrosoft + PluginDescriptor.VOX + "Plugin Scheduler",
    description = "Schedule plugins at your will",
    tags = {"microbot", "schedule", "automation"},
    enabledByDefault = false,
    priority = false
)
public class SchedulerPlugin extends Plugin {
    // Dependencies, state variables, configuration
    // Methods for plugin lifecycle management and scheduling
}
```

## Key Features

### Plugin Lifecycle Management

The `SchedulerPlugin` manages the complete lifecycle of scheduled plugins:

- **Registration**: Allows plugins to be registered with the scheduler through `PluginScheduleEntry` objects
- **Activation**: Starts plugins when their start conditions are met, respecting priority and scheduling properties
- **Monitoring**: Tracks running plugins and continuously evaluates their stop conditions
- **Deactivation**: Implements both soft and hard stop mechanisms when stop conditions are met
- **Persistence**: Saves and loads scheduled plugin configurations across client sessions

### Advanced Scheduling Algorithm

The scheduler implements a sophisticated algorithm to determine which plugins to run and prioritize:

```java
/**
 * Schedules the next plugin based on priority and timing rules
 */
private void scheduleNextPlugin() {
    // Check if a non-default plugin is coming up soon
    boolean prioritizeNonDefaultPlugins = config.prioritizeNonDefaultPlugins();
    int nonDefaultPluginLookAheadMinutes = config.nonDefaultPluginLookAheadMinutes();
    
    if (prioritizeNonDefaultPlugins) {
        // Look for any upcoming non-default plugin within the configured time window
        PluginScheduleEntry upcomingNonDefault = getNextScheduledPlugin(false, 
                                                        Duration.ofMinutes(nonDefaultPluginLookAheadMinutes))
            .filter(plugin -> !plugin.isDefault())
            .orElse(null);
            
        // If we found an upcoming non-default plugin, check if it's already due to run
        if (upcomingNonDefault != null && !upcomingNonDefault.isDueToRun()) {
            // Get the next plugin that's due to run now
            Optional<PluginScheduleEntry> nextDuePlugin = getNextScheduledPlugin(true, null);
            
            // If the next due plugin is a default plugin, don't start it
            // Instead, wait for the non-default plugin
            if (nextDuePlugin.isPresent() && nextDuePlugin.get().isDefault()) {
                log.info("Not starting default plugin '{}' because non-default plugin '{}' is scheduled within {} minutes",
                    nextDuePlugin.get().getCleanName(),
                    upcomingNonDefault.getCleanName(),
                    nonDefaultPluginLookAheadMinutes);
                return;
            }
        }
    }
    
    // Get the next plugin that's due to run
    Optional<PluginScheduleEntry> selected = getNextScheduledPlugin(true, null);
    if (selected.isEmpty()) {
        return;
    }
    
    // If we're on a break, interrupt it
    if (isOnBreak()) {
        log.info("Interrupting active break to start scheduled plugin: {}", selected.get().getCleanName());
        interruptBreak();
    }
   
    // Start the selected plugin
    log.info("Starting scheduled plugin: {}", selected.get().getCleanName());
    startPluginScheduleEntry(selected.get());
}
```

The plugin selection algorithm incorporates several factors and methods:

1. **Priority**: Higher priority plugins are always considered first
2. **Default Status**: Non-default plugins can be prioritized over default plugins
3. **Time Window Forecasting**: The scheduler looks ahead for upcoming non-default plugins
4. **Run Count Balance**: For randomizable plugins, uses weighted selection based on run counts

```java
/**
 * Selects a plugin using weighted random selection.
 * Plugins with lower run counts have higher probability of being selected.
 */
private PluginScheduleEntry selectPluginWeighted(List<PluginScheduleEntry> plugins) {
    // Return the only plugin if there's just one
    if (plugins.size() == 1) {
        return plugins.get(0);
    }

    // Calculate weights - plugins with lower run counts get higher weights
    // Find the maximum run count
    int maxRuns = plugins.stream()
            .mapToInt(PluginScheduleEntry::getRunCount)
            .max()
            .orElse(0);

    // Add 1 to avoid division by zero and ensure all plugins have some chance
    maxRuns = maxRuns + 1;

    // Calculate weights
    double[] weights = new double[plugins.size()];
    double totalWeight = 0;

    for (int i = 0; i < plugins.size(); i++) {
        weights[i] = maxRuns - plugins.get(i).getRunCount() + 1;
        totalWeight += weights[i];
    }

    // Select based on weighted probability
    double randomValue = Math.random() * totalWeight;
    double weightSum = 0;
    for (int i = 0; i < plugins.size(); i++) {
        weightSum += weights[i];
        if (randomValue < weightSum) {
            return plugins.get(i);
        }
    }
    
    // Fallback
    return plugins.get(0);
}
```

### State-Based Decision Making

The scheduler uses the state machine to make intelligent decisions about plugin execution, with different behavior based on the current state:

#### 1. State-Dependent UI Updates
The UI reflects the current state with appropriate colors and messages:
- Active states (RUNNING_PLUGIN) show green indicators
- Warning states (STOPPING_PLUGIN) show orange indicators
- Error states show red indicators
- Break states show blue indicators

#### 2. State-Based Priority Handling
The scheduler prioritizes actions differently based on the current state:
- During SCHEDULING, it evaluates which plugin should run next
- During BREAK states, it calculates appropriate break durations
- During WAITING states, it monitors for conditions to transition

#### 3. State Transition Guards
Transitions between states have guards that ensure proper flow:
- Cannot transition directly from ERROR to RUNNING_PLUGIN
- HARD_STOPPING_PLUGIN only follows SOFT_STOPPING_PLUGIN
- STARTING_PLUGIN must precede RUNNING_PLUGIN

This state-based design creates a robust system that can handle complex scheduling scenarios while maintaining proper execution flow.

### Seamless Integration with Core Systems

The scheduler deeply integrates with other Microbot systems to create a cohesive automation experience:

#### Break Handler Integration

The scheduler integrates with the BreakHandler plugin in several ways:

```java
/**
 * Starts a short break until the next plugin is scheduled to run
 */
private boolean startBreakBetweenSchedules(boolean logout, 
    int minBreakDurationMinutes, int maxBreakDurationMinutes) {
    if (!isBreakHandlerEnabled()) {
        return false;
    }
    if (BreakHandlerScript.isLockState())
        BreakHandlerScript.setLockState(false);
    
    // Check if we're outside play schedule
    if (config.usePlaySchedule() && config.playSchedule().isOutsideSchedule()) {
        Duration untilNextSchedule = config.playSchedule().timeUntilNextSchedule();
        log.info("Outside play schedule. Next schedule in: {}", formatDuration(untilNextSchedule));
        
        // Configure a break until the next play schedule time
        BreakHandlerScript.breakDuration = (int) untilNextSchedule.getSeconds();
        this.currentBreakDuration = untilNextSchedule;
        BreakHandlerScript.breakIn = 0;
        
        // Store the original logout setting before changing it
        savedBreakHandlerLogoutSetting = Microbot.getConfigManager().getConfiguration(
            BreakHandlerConfig.configGroup, "Logout", Boolean.class);
        
        // Set the new logout setting
        Microbot.getConfigManager().setConfiguration(BreakHandlerConfig.configGroup, "Logout", true);
        
        // Wait for break to become active
        sleepUntil(() -> BreakHandlerScript.isBreakActive(), 1000);
        
        setState(SchedulerState.PLAYSCHEDULE_BREAK);
        return true;
    }
    
    // Configure duration for regular break
    // Determine break duration and start break handler
    
    return true;
}
```

#### Auto Login Integration

The scheduler manages the login process through a dedicated monitoring thread:

```java
/**
 * Starts a thread to monitor login state and process login when needed
 */
private void startLoginMonitoringThread() {
    if (loginMonitor != null && loginMonitor.isAlive()) {
        return;
    }
    
    setState(SchedulerState.WAITING_FOR_LOGIN);
    
    loginMonitor = new Thread(() -> {
        try {
            // Don't continue if auto login is disabled
            if (!config.autoLogIn()) {
                log.info("Auto login is disabled");
                setState(SchedulerState.SCHEDULING);
                return;
            }
            
            // Wait a moment for the game client to be ready
            sleep(1000);
            
            // Set state to indicate login attempt is in progress
            setState(SchedulerState.LOGIN);
            
            // Determine world selection logic
            int worldNumber = config.autoLogInWorld();
            int worldType = config.worldType();
            
            // Attempt login with configured settings
            AutoLoginPlugin autoLogin = injector.getInstance(AutoLoginPlugin.class);
            autoLogin.requestLogin();
            
            // Wait for login to complete
            boolean loggedIn = sleepUntil(() -> Login.isLoggedIn(), 60000);
            
            if (loggedIn) {
                setState(SchedulerState.SCHEDULING);
            } else {
                setState(SchedulerState.ERROR);
            }
        } catch (Exception e) {
            log.error("Error in login monitor thread", e);
            setState(SchedulerState.ERROR);
        }
    });
    
    loginMonitor.setName("Login-Monitor");
    loginMonitor.setDaemon(true);
    loginMonitor.start();
}

### Two-Tiered Plugin Stop Mechanism

The scheduler implements a sophisticated stop mechanism with both soft and hard stop options:

```java
/**
 * Initiates a forced stop process for the current plugin.
 * Used when a plugin needs to be stopped immediately or when soft stop fails.
 * 
 * @param successful Whether the plugin completed successfully before stopping
 */
public void forceStopCurrentPluginScheduleEntry(boolean successful) {
    if (currentPlugin != null && currentPlugin.isRunning()) {
        log.info("Force Stopping current plugin: " + currentPlugin.getCleanName());
        if (currentState == SchedulerState.RUNNING_PLUGIN) {
            setState(SchedulerState.HARD_STOPPING_PLUGIN);
        }
        currentPlugin.stop(successful, StopReason.HARD_STOP, "Plugin was forcibly stopped by user request");
        // Wait a short time to see if the plugin stops immediately
        if (currentPlugin != null) {
            if (!currentPlugin.isRunning()) {
                log.info("Plugin stopped successfully: " + currentPlugin.getCleanName());
            } else {
                SwingUtilities.invokeLater(() -> {
                    forceStopCurrentPluginScheduleEntry(successful);
                });
                log.info("Failed to hard stop plugin: " + currentPlugin.getCleanName());
            }
        }
    }
    updatePanels();
}
```

The actual stop monitoring logic is implemented in the `PluginScheduleEntry` class:

```java
/**
 * Starts a monitor thread that oversees the plugin stopping process
 * 
 * @param successfulRun Whether the plugin run was successful
 */
private void startStopMonitoringThread(boolean successfulRun) {
    // Don't start a new thread if one is already running
    if (isMonitoringStop) {
        return;
    }
    
    isMonitoringStop = true;
    
    stopMonitorThread = new Thread(() -> {
        log.info("Stop monitoring thread started for plugin '" + name + "'");
        
        try {
            // Keep checking until the stop completes or is abandoned
            while (stopInitiated && isMonitoringStop) {
                // Check if plugin has stopped running
                if (!isRunning()) {
                    // Plugin has stopped successfully
                    if (scheduleEntryConfigManager != null) {
                        scheduleEntryConfigManager.setScheduleMode(false);
                    }
                    
                    // Update conditions for next run
                    if (successfulRun) {
                        resetStartConditions();                            
                    } else {
                        setEnabled(false);
                    }
                    
                    // Reset stop state
                    stopInitiated = false;
                    hasStarted = false;
                    break;
                }
                
                Thread.sleep(300); // Check every 300ms
            }
        } catch (InterruptedException e) {
            // Thread was interrupted, just exit
        } finally {
            isMonitoringStop = false;
        }
    });
    
    stopMonitorThread.setName("StopMonitor-" + name);
    stopMonitorThread.setDaemon(true); // Use daemon thread to not prevent JVM exit
    stopMonitorThread.start();
}
```

This approach allows plugins to clean up resources and save state during a soft stop, while ensuring they eventually stop even if unresponsive.

### Comprehensive UI and User Experience

The scheduler provides an intuitive interface for managing scheduled plugins:

```java
@Inject
private ClientToolbar clientToolbar;

private NavigationButton navButton;
private SchedulerPanel panel;
private SchedulerWindow schedulerWindow;

/**
 * Initializes the scheduler UI components and navigation
 */
private void initializeUI() {
    panel = new SchedulerPanel(this);
    
    final BufferedImage icon = ImageUtil.loadImageResource(SchedulerPlugin.class, "calendar-icon.png");
    navButton = NavigationButton.builder()
        .tooltip("Plugin Scheduler")
        .priority(10)
        .icon(icon)
        .panel(panel)
        .build();
        
    clientToolbar.addNavigation(navButton);
    
    // Initialize the main scheduler window
    schedulerWindow = new SchedulerWindow(this);
}
```

The UI allows users to:

- Add, edit, and remove scheduled plugins
- Configure start and stop conditions through an intuitive condition builder
- View detailed plugin execution history and statistics
- Manually control plugin execution with start, stop, and pause options

## Plugin Selection and Scheduling Algorithm

The scheduler implements a sophisticated multi-factor algorithm to determine which plugins to run and when.

### Plugin Selection Process

1. **Scheduling Cycle**:
   - The scheduler runs a periodic check approximately every second via `checkSchedule()`
   - During each cycle, it evaluates if the current plugin should be stopped with `checkCurrentPlugin()`
   - If no plugin is running, it selects the next plugin to run with `scheduleNextPlugin()`

2. **Selection Algorithm**:
   ```text
   START
     Check if any non-default plugins are scheduled soon (within lookup window)
     If a non-default plugin is upcoming and current plugin is default:
       Don't start any default plugin, wait for the non-default plugin
     Get next plugin that's due to run via getNextScheduledPlugin()
     If on a break, interrupt it to start the selected plugin
     Start the selected plugin with startPluginScheduleEntry()
   END
   ```

3. **Selection Factors** (in order of precedence):
   - **Plugin Priority**: Higher priority plugins are always evaluated first
   - **Plugin Type**: Non-default plugins take precedence over default plugins
   - **Start Conditions**: Plugins with met start conditions are selected first
   - **Randomization**: For equal priority plugins, weighted random selection
   - **Run Balance**: Plugins run less frequently get higher weighting

### Condition Evaluation Model

The scheduler uses a sophisticated condition evaluation model with different rules for start vs. stop:

```text
For Starting a Plugin:
Plugin Start Conditions AND User Start Conditions must ALL be true

For Stopping a Plugin:
Plugin Stop Conditions OR User Stop Conditions - ANY being true triggers stop
```

This condition model gives both plugins and users appropriate control:

- Plugins cannot start unless both their requirements and user preferences allow
- Either the plugin or user can trigger a stop when needed

## Break System and Play Schedule

The scheduler implements two complementary break systems to create natural-appearing behavior patterns.

### Core Break System

The break system controls automated breaks between plugin executions:

1. **Break Duration Calculation**:
   - Minimum break duration from config (default: 2 minutes)
   - Maximum break duration from config (default: 2 minutes, configurable up to 60)
   - The `startBreakBetweenSchedules` method handles break initialization

2. **Break Triggers**:
   - After plugin completion
   - When no plugins are running and no plugins are due to run soon
   - When outside allowed play schedule hours
   - Based on configured break frequency

3. **Break Management**:
   - **Break Initiation**: Uses `BreakHandlerScript.breakDuration` to set break length
   - **Break Interruption**: Can interrupt breaks for high-priority plugins using `interruptBreak()`
   - **Break State Tracking**: Uses dedicated states (BREAK, PLAYSCHEDULE_BREAK)

### Play Schedule System

The play schedule controls when the scheduler is allowed to run plugins:

1. **Schedule Configuration**:
   - Each day can have different allowed play hours
   - Multiple time windows can be configured per day
   - Randomization can be applied to window boundaries

2. **Schedule Behavior**:
   - Outside allowed hours: Scheduler enters PLAYSCHEDULE_BREAK state
   - Approaching end of window: Current plugin may be stopped
   - Beginning of window: Scheduler resumes normal operation
   - Window transitions: Can trigger login/logout actions

### Integration with BreakHandler Plugin

The scheduler integrates with the standalone BreakHandler plugin:

1. **Coordination Mechanism**:
   - BreakHandler signals when breaks begin/end
   - Scheduler respects BreakHandler's break state
   - Login/logout settings synchronized between systems
   - Break statistics shared for consistent behavior

2. **Break Handler Settings Management**:

   ```java
   /**
    * Saves current BreakHandler settings before modifying them
    * for scheduler operation, and restores original settings
    * when scheduler is disabled.
    */
   private void syncBreakHandlerSettings() {
       // Save original settings
       savedBreakHandlerLogoutSetting = getBreakHandlerSetting("logout");
       
       // Apply scheduler settings
       if (config.breakHandlerForceLogout()) {
           setBreakHandlerSetting("logout", true);
       }
   }
   ```

## Implementation Strategies

### Main Schedule Check Logic

The scheduler uses a main scheduling check that runs approximately every second:

```java
private void checkSchedule() {
    // Skip checking if in certain states
    if (SchedulerState.LOGIN == currentState ||
            SchedulerState.WAITING_FOR_LOGIN == currentState ||
            SchedulerState.HARD_STOPPING_PLUGIN == currentState ||
            SchedulerState.SOFT_STOPPING_PLUGIN == currentState ||
            currentState == SchedulerState.HOLD) {
        return;
    }
    
    // First, check if we need to stop the current plugin
    if (isScheduledPluginRunning()) {
        checkCurrentPlugin();
    }
    
    // If no plugin is running, check for scheduled plugins
    if (!isScheduledPluginRunning()) {
        // Check if login is needed
        PluginScheduleEntry nextPluginWith = null;
        PluginScheduleEntry nextPluginPossible = getNextScheduledPlugin(false, null).orElse(null);
        
        // Skip breaks if min break duration is 0
        int minBreakDuration = config.minBreakDuration();
        if (minBreakDuration == 0) {
            minBreakDuration = 1;
            nextPluginWith = getNextScheduledPlugin(true, null).orElse(null);
        } else {
            minBreakDuration = Math.max(1, minBreakDuration);
            // Get the next scheduled plugin within minBreakDuration
            nextPluginWith = getNextScheduledPluginWithinTime(
                    Duration.ofMinutes(minBreakDuration));
        }

        // Handle login requirements
        if (nextPluginWith == null && 
                nextPluginPossible != null && 
                !nextPluginPossible.hasOnlyTimeConditions() 
                && !isOnBreak() && !Microbot.isLoggedIn()) {                    
            startLoginMonitoringThread();
            return;                
        }

        // Determine if we should schedule next plugin or take a break
        if (nextPluginWith != null && canRunNow()) {
            scheduleNextPlugin();
        } else {
            // Take a break if nothing is running
            startBreak();
        }
    }
}
```

### Plugin Management

The actual management of plugin lifecycle is handled through:

1. **Starting Plugins**: `startPluginScheduleEntry(PluginScheduleEntry entry)`
2. **Stopping Plugins**: `forceStopCurrentPluginScheduleEntry(boolean successful)`
3. **Checking Conditions**: `checkCurrentPlugin()` evaluates stop conditions

### User Interface Functions

The scheduler provides UI controls through:

- **Force Start**: `forceStartPluginScheduleEntry(PluginScheduleEntry entry)`
- **Stop**: `forceStopCurrentPluginScheduleEntry(boolean successful)`
- **Pause/Resume**: `setSchedulerState(SchedulerState.HOLD)` and `startScheduler()`

## Integration with Other Systems

The SchedulerPlugin integrates with several other plugins and systems to provide a complete automation solution:

### BreakHandler Integration

The scheduler works closely with the BreakHandler plugin to:
- Respect global break settings
- Coordinate break times across plugins
- Share break statistics and patterns
- Manage login/logout during breaks

Configuration options allow you to control:
- Whether to use BreakHandler for scheduled plugins
- Whether to log out during breaks
- Break duration settings

### AutoLogin Integration

The scheduler integrates with the AutoLogin plugin to:
- Automatically log in before starting plugins when needed
- Handle world selection based on configuration
- Manage disconnections and reconnections
- Support play schedule login/logout requirements

### Antiban Features

The scheduler implements various antiban measures:
- Randomized break patterns
- Natural play schedule enforcement
- Varied execution timing
- Random plugin selection within priority groups

## Best Practices

For optimal use of the Plugin Scheduler:

1. **Set Clear Priorities**:
   - High priority (8-10): Critical tasks
   - Medium priority (4-7): Regular tasks
   - Low priority (1-3): Background tasks

2. **Use Appropriate Stop Conditions**:
   - Always include a time-based stop condition as a fallback
   - Use game-state conditions for more precise control
   - Test conditions thoroughly before long-term use

3. **Balance Random vs. Fixed Scheduling**:
   - Use randomization for most plugins
   - Reserve fixed schedules for time-sensitive tasks
   - Mix default and non-default plugins for natural patterns

4. **Configure Integration Features**:
   - Enable BreakHandler integration for more natural patterns
   - Use AutoLogin when running unattended
   - Set up Play Schedules that match realistic play patterns
