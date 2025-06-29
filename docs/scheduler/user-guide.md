# Scheduler Plugin User Guide

## Introduction

The Plugin Scheduler is a sophisticated system that allows you to automatically schedule and manage plugins based on various conditions. This guide focuses on how to use the scheduler's user interface to set up plugin scheduling plans, including defining start and stop conditions, understanding plugin priorities, and configuring plugin behavior.

## The Scheduler Interface

The scheduler interface is divided into several main components:

1. **Schedule Table**: Displays all scheduled plugins with their current status, configurations, and next run times
2. **Schedule Form**: Allows you to add new scheduled plugins
3. **Properties Panel**: Lets you modify settings for existing scheduled plugins
4. **Start Conditions Panel**: Configures when a plugin should start running
5. **Stop Conditions Panel**: Configures when a plugin should stop running

![Scheduler Window Overview](../img/scheduler-overview.png)

## Adding a New Plugin Schedule

To create a new plugin schedule:

1. Navigate to the "New Schedule" tab in the scheduler window
2. Select a plugin from the dropdown menu
3. Choose a scheduling method from the "Time Condition" dropdown:
   - **Run Default**: Makes the plugin a default option (priority 0)
   - **Run at Specific Time**: Runs the plugin once at a specific date/time
   - **Run at Interval**: Runs the plugin at regular intervals
   - **Run in Time Window**: Runs the plugin during specific hours of the day
   - **Run on Day of Week**: Runs the plugin on specific days of the week
4. Configure the time condition options based on your selection
5. Set additional options:
   - **Allow Random Scheduling**: Lets the scheduler randomly select this plugin when multiple are due
   - **Requires time-based stop condition**: Requires you to add a time condition to stop the plugin
   - **Allow continue**: Allows the plugin to resume automatically after being interrupted, maintaining progress
   - **Priority**: Sets the plugin's priority level (0 = default plugin)
6. Click "Add Schedule" to create the schedule

## Understanding Default vs. Non-Default Plugins

### Default Plugins

Default plugins serve as your "background" or "fallback" activities that run when nothing else is scheduled. Think of them as what your character does during downtime.

**Characteristics:**
- Always have **priority 0** (automatically enforced by the scheduler)
- Run only when no other plugins are scheduled or eligible to run
- Typically use a very short interval check (often 1 second) to quickly start when needed
- Are displayed with a teal background color and ⭐ star icon in the schedule table

**To mark a plugin as default:**
- Check the "Set as default plugin" checkbox in the schedule form, or
- Set its priority to 0 (the system will recognize it as a default plugin)

**Example use cases:**
- AFK training activities (e.g., NMZ, splashing)
- Simple repetitive tasks that should run when nothing more important is active
- "Maintenance" plugins that handle routine tasks during downtime

### Non-Default Plugins

Non-default plugins are your primary scheduled activities that run according to specific conditions.

**Characteristics:**
- Have a priority of 1 or higher (lower number = higher priority)
- Run according to their specific start conditions
- Will always take precedence over default plugins when their conditions are met
- Can interrupt default plugins (unless the default plugin has "Allow Continue" enabled)

**Example use cases:**
- Your main skilling activities (fishing, mining, etc.)
- Time-specific activities (e.g., farm runs, daily challenges)
- Condition-based activities (e.g., "Bank items when inventory is full")

## Defining Start and Stop Conditions

The power of the Plugin Scheduler comes from its ability to create sophisticated start and stop conditions for your plugins. This allows for highly automated and intelligent plugin scheduling.

For detailed instructions on creating and configuring various condition types (Time, Skill, Resource, Location), see the [Defining Conditions guide](defining-conditions.md).

### Start Conditions

Start conditions determine when a plugin is eligible to begin running. A plugin will start when all its start conditions are met (or any, depending on your logical configuration).

**Basic Configuration:**

1. Select your plugin in the schedule table
2. Navigate to the "Start Conditions" tab
3. Click "Add New Condition"
4. Select a condition category (Time, Skill, Resource, Location, etc.)
5. Choose a specific condition type within that category
6. Configure the parameters for your chosen condition
7. Click "Add" to add the condition to your logical structure

**Example Start Conditions:**

- **Time-based:** "Start at 8:00 PM every day"
- **Resource-based:** "Start when inventory is empty"
- **Location-based:** "Start when player is at the Grand Exchange"
- **Skill-based:** "Start when Mining reaches level 70"

### Stop Conditions

Stop conditions determine when a running plugin should stop. They're evaluated continuously while the plugin is running.

**Basic Configuration:**

1. Select your plugin in the schedule table
2. Navigate to the "Stop Conditions" tab
3. Click "Add New Condition"
4. Select a condition category
5. Choose a specific condition type
6. Configure the parameters
7. Click "Add" to add the condition

**Example Stop Conditions:**

- **Time-based:** "Stop after 2 hours of running"
- **Resource-based:** "Stop when inventory is full" or "Stop after collecting 1000 items"
- **Location-based:** "Stop when reaching Lumbridge"
- **Skill-based:** "Stop when gaining 50,000 XP in Fishing"

### Building Complex Logical Structures

You can combine multiple conditions using logical operators to create sophisticated rules:

1. **AND Logic (ALL conditions must be met):**
   - Click on the root condition node in the tree view
   - Click "Convert to AND"
   - All child conditions must be satisfied for the overall condition to be met

2. **OR Logic (ANY condition can be met):**
   - Click on the root condition node
   - Click "Convert to OR" (default)
   - Any child condition being satisfied will satisfy the overall condition

3. **Nesting AND/OR Groups:**
   - Select multiple conditions in the tree
   - Click "Group AND" or "Group OR"
   - This creates a sub-group with its own logical relationship

4. **NOT Logic:**
   - Select a condition
   - Click "Negate"
   - The condition's result will be inverted

**Example Complex Condition:**
"Start the plugin when (it's between 8 PM and midnight) AND (player is in the Mining Guild OR at Al Kharid mines) AND (inventory is empty)"

## The "Allow Continue" Setting Explained

The "Allow Continue" option determines what happens when a plugin is interrupted by a higher-priority plugin and later has a chance to resume:

- **When enabled**: The interrupted plugin will continue running immediately after the higher-priority plugin finishes, without resetting its start and stop conditions. This preserves all progress made toward stop conditions and doesn't require start conditions to be re-evaluated.
- **When disabled**: The interrupted plugin will not automatically resume after being interrupted. It will need to be triggered again from scratch, with all start and stop conditions reset.

**Example Scenarios:**

1. **Allow Continue = ON**
   - You're running a Woodcutting plugin (priority 3)
   - A Banking plugin (priority 2) interrupts it
   - After banking is done, the Woodcutting plugin automatically resumes with all progress intact
   - Perfect for plugins that should pick up where they left off after being interrupted

2. **Allow Continue = OFF**
   - You're running a Fishing plugin (priority 3) 
   - A Cooking plugin (priority 2) interrupts it
   - After cooking is done, the Fishing plugin does not automatically resume
   - Best for tasks that should fully restart from the beginning when interrupted

## What Happens When a Plugin Has No Stop Conditions

When a schedulable plugin has no plugin-based stop conditions:

1. The plugin will run indefinitely until:
   - You manually stop it through the UI
   - A higher-priority plugin interrupts it (after which it may resume if "Allow Continue" is enabled)
   - The plugin's internal logic determines it should stop

2. The scheduler UI will display a warning icon next to plugins without stop conditions, reminding you that they may run indefinitely.

3. If "Requires time-based stop condition" is checked in the settings, the scheduler will display a prompt when you try to add a schedule without stop conditions.

**Best Practices:**

- **Always include at least one stop condition** for every plugin schedule
- **Add a time-based safety condition** (e.g., "stop after 2 hours") even if you have other stop conditions
- **Use LockCondition for critical operations** to prevent interruption during important tasks
- **Combine multiple stop conditions** for more sophisticated control, such as:
  - "Stop after collecting 1000 items OR after 3 hours" (whichever comes first)
  - "Stop when inventory is full AND we're in a safe area"

**Safety Mechanism:**
The scheduler includes a built-in watchdog that monitors plugins for signs they might be stuck. If a plugin runs for an extended period without showing progress, the system can detect this and provide warnings.

## Working with the Schedule Table

The schedule table displays all configured plugins and their current status:

- **Plugin**: The name of the plugin (⭐ indicates default plugins, ▶ indicates currently running)
- **Schedule**: When the plugin is scheduled to run
- **Next Run**: When the plugin will run next
- **Start Conditions**: Summary of conditions that trigger the plugin to start
- **Stop Conditions**: Summary of conditions that will stop the plugin
- **Priority**: The plugin's priority level (0 = default)
- **Enabled**: Whether the plugin is enabled in the scheduler
- **Runs**: How many times the plugin has been executed

### Row Colors and Indicators

- **Purple background**: Currently running plugin
- **Amber background**: Next plugin scheduled to run
- **Teal background**: Default plugin
- **Green indicators**: Conditions are satisfied
- **Red indicators**: Conditions are not satisfied
- **Strikethrough text**: Plugin is disabled

## Managing Existing Schedules

To modify an existing schedule:

1. Click on it in the schedule table
2. Use the "Plugin Properties" tab to adjust:
   - Enabled status
   - Default plugin setting
   - Priority
   - Random scheduling
   - Time-based stop condition requirement
   - Allow continue setting
3. Use the "Start Conditions" and "Stop Conditions" tabs to modify conditions
4. Click "Save Changes" to apply your modifications

## Tips and Best Practices

1. **Set appropriate priorities**:
   - Higher priority plugins (higher numbers) run before lower priority ones
   - Use priorities to establish a clear hierarchy of tasks

2. **Use time windows effectively**:
   - Consider using time windows to run different plugins at different times of day
   - Time windows can help avoid detection by making your play patterns more natural

3. **Combine condition types**:
   - Mix time, resource, and location conditions for sophisticated automation
   - Example: Run a mining plugin only when inventory is empty AND at certain times of day

4. **Plan for interruptions**:
   - Enable "Allow Continue" for tasks that should automatically resume after being interrupted
   - Create robust stop conditions that handle unexpected situations

5. **Monitor run statistics**:
   - Use the statistics in the Properties panel to track plugin performance
   - Look for unusually short runs that might indicate problems

## Common Scenarios and Real-World Examples

Here are some practical examples of how to set up the scheduler for common gameplay situations. Use these as templates for your own scheduling plans.

### Scenario 1: AFK Training with Default Plugin

**Goal**: Train a skill passively when you're not doing anything else

**Solution**:
1. Add your AFK training plugin (e.g., NMZ, splashing) with "Run Default" time condition
2. Ensure "Set as default plugin" is checked (priority will be set to 0)
3. Add a safety stop condition like "Stop after 5 hours" to prevent running indefinitely
4. Consider adding "Stop when XP gained reaches 100,000" as an additional stop condition

**Example Setup:**
- Plugin: AFKCombatPlugin
- Priority: 0 (Default)
- Start Conditions: None (runs as default)
- Stop Conditions: OR(TimeCondition(5 hours), SkillXPCondition(100,000 XP))

### Scenario 2: Resource Gathering and Processing Cycle

**Goal**: Alternate between gathering resources and processing them (e.g., fishing and cooking)

**Solution**:
1. Create a schedule for fishing plugin:
   - Priority: 2
   - Start Condition: Inventory is empty OR time since last run > 15 minutes
   - Stop Condition: Inventory is full (28 items)

2. Create a schedule for cooking plugin:
   - Priority: 1 (higher priority than fishing)
   - Start Condition: Inventory has raw fish
   - Stop Condition: No raw fish in inventory
   
This creates a natural cycle: When inventory fills with fish, the cooking plugin (higher priority) takes over. When cooking is done, the fishing plugin starts again.

### Scenario 3: Time-Limited Daily Activities

**Goal**: Run specific plugins at certain times of day, like farm runs every few hours

**Solution**:
1. Create a schedule with "Run in Time Window" or "Run at Interval"
   - For farm runs: "Run at Interval" of 80 minutes
   - For daily tasks: "Run in Time Window" (e.g., 6:00 PM to 7:00 PM)
   
2. Add specific stop conditions:
   - Time-based: "Stop after 15 minutes" (prevents running too long)
   - Completion-based: "Stop when all farming patches are harvested"
   
3. Enable "Allow Continue" if this activity should be able to resume after interruptions

**Example Setup:**
- Plugin: FarmRunPlugin
- Priority: 1
- Start Conditions: TimeCondition(Interval: 80 minutes)
- Stop Conditions: OR(TimeCondition(15 minutes), CompletionCondition(all patches harvested))

### Scenario 4: Location-Based Task Switching

**Goal**: Run different plugins based on player location

**Solution**:
1. Create a mining plugin:
   - Priority: 2
   - Start Condition: In mining area AND inventory not full
   - Stop Condition: Inventory full OR not in mining area

2. Create a banking plugin:
   - Priority: 1 (higher priority)
   - Start Condition: Inventory full AND near bank
   - Stop Condition: Inventory empty

This creates a smart workflow where your character will mine until inventory is full, then prioritize banking when near a bank.

### Scenario 5: Skill Goal Achievement

**Goal**: Train a skill until reaching a specific level, then switch to another activity

**Solution**:
1. Create a woodcutting plugin:
   - Priority: 2
   - Start Condition: Woodcutting level < 70
   - Stop Condition: Woodcutting level reaches 70

2. Create a fishing plugin:
   - Priority: 2
   - Start Condition: Woodcutting level >= 70 AND Fishing level < 70
   - Stop Condition: Fishing level reaches 70

This creates a progression plan where the scheduler automatically moves from one skill goal to the next.

## Disclaimers

> **Note**: The examples and scenarios provided in this guide are illustrative only. Not all plugins mentioned in these examples may be schedulable at this time. Please check the Scheduler GUI to see which plugins are currently available for scheduling.

> **Feedback Welcome**: The Plugin Scheduler and all its features are currently in an experimental state. User feedback is greatly appreciated to help improve functionality and documentation.

## Conclusion

The Scheduler Plugin provides a powerful way to automate and coordinate your botting activities. By understanding how to properly configure start and stop conditions, priorities, and other settings, you can create sophisticated schedules that maximize efficiency while maintaining a natural play pattern.

For detailed instructions on creating specific condition types, including Time, Skill, Resource, and Location conditions, see the [Defining Conditions guide](defining-conditions.md). This companion guide provides examples, configuration steps, and advanced techniques for creating powerful condition logic.

For implementation details about the API and extending the condition system, see the [API documentation for conditions](api/conditions/).
