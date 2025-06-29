# Defining Conditions in the Scheduler UI

This guide provides detailed instructions on how to use the condition configuration panels in the Plugin Scheduler UI to define start and stop conditions for your plugins.

## Understanding the Condition Panel

The condition configuration panel is the heart of the scheduler's power, allowing you to create sophisticated logic that determines when plugins start and stop. Think of it as programming your character's behavior without writing code.

### Panel Components

The panel consists of four main sections:

1. **Condition Category Dropdown**: Select the type of condition you want to create
   - Time: For scheduling based on real-world time
   - Skill: For conditions based on your character's skills and XP
   - Resource: For inventory, item collection, and processing conditions
   - Location: For position-based conditions in the game world
   - Varbit: For game state variables and collection log entries

2. **Condition Type Selection**: Each category has several specific condition types
   - Shows only relevant condition types based on your selected category
   - Provides tooltips explaining each condition type's purpose

3. **Parameter Configuration**: Customize the condition with specific values
   - Different condition types have different parameter forms
   - Includes helpful controls like time pickers, dropdown menus, and numeric inputs

4. **Logical Structure Tree**: Visual representation of your condition structure
   - Shows how conditions are combined with AND/OR logic
   - Allows selecting, grouping, and organizing conditions
   - Displays satisfaction status with visual indicators

![Condition Panel Overview](../img/condition-panel-overview.png)

### Creating Your First Condition

1. Select the relevant tab (Start Conditions or Stop Conditions)
2. Choose a condition category from the dropdown
3. Select a specific condition type
4. Configure the parameters
5. Click "Add" to add it to your condition structure
6. The condition appears in the tree view, where you can organize it

## Condition Categories and Types

### Time Conditions

Time conditions are among the most commonly used and versatile conditions in the scheduler. They allow you to control when plugins start and stop based on real-world time factors.

#### Available Time Condition Types

| Condition Type | Description | Real-World Examples |
|----------------|-------------|-------------|
| **Time Duration** | Runs for a specific amount of time | "Run for 3 hours then stop" (good as a stop condition) |
| **Time Window** | Runs during specific hours each day | "Only run between 8am-10pm when I'm at my computer" |
| **Not In Time Window** | Runs outside specific hours | "Don't run during work hours (9am-5pm)" |
| **Specific Time** | Runs at an exact date/time | "Run exactly at 8:00pm on Tuesdays" |
| **Day of Week** | Runs on specific days | "Run on weekends only" |
| **Interval** | Runs at regular time intervals | "Run every 2 hours" or "Take a 15-minute break every hour" |

#### Practical Examples

**Example 1: Setting Up a Time Window for Evening Play**

This is perfect for limiting your botting to specific hours:

1. Select "Time" from the Category dropdown
2. Select "Time Window" from the Type dropdown
3. Set the start time to 6:00 PM (when you get home)
4. Set the end time to 11:00 PM (before bed)
5. Optional: Enable randomization for more human-like timing
6. Click "Add" to add the condition

Now your plugins will only run during your evening hours and automatically stop at night.

**Example 2: Creating a Playtime Limit**

To limit how long a plugin runs (great for stop conditions):

1. Select "Time" from the Category dropdown
2. Select "Time Duration" from the Type dropdown
3. Set the duration to your desired play time (e.g., 2 hours)
4. Enable randomization for natural variation (e.g., 1h45m to 2h15m)
5. Click "Add" to add the condition

This ensures your plugin won't run for too long, preventing detection and burnout.

**Example 3: Setting Up Weekend-Only Gaming**

For those who only want to bot on weekends:

1. Select "Time" from the Category dropdown
2. Select "Day of Week" from the Type dropdown
3. Check only "Saturday" and "Sunday" in the day selector
4. Click "Add" to add the condition

Combine this with other time conditions for even more control, like "Run on weekends between 10am and 8pm".

### Skill Conditions

Skill conditions allow you to create automation based on your character's skills and experience progress. They're perfect for training goals and level-based activities.

#### Available Skill Condition Types

| Condition Type | Description | Strategic Uses |
|----------------|-------------|---------------|
| Skill Level | Sets absolute or relative level targets | Stop when reaching a milestone level |
| Skill XP Goal | Sets XP targets rather than levels | Get precise control over training sessions |
| Skill Level Required | Sets minimum requirements | Safeguard activities that require certain levels |

#### Practical Skill Condition Examples

**Example 1: Setting a Level-Based Training Goal**

To create a condition that stops a plugin when you reach a specific level:

1. Select "Skill" from the Category dropdown
2. Select "Skill Level" from the Type dropdown
3. Choose your skill from the dropdown (e.g., "Fishing")
4. Select either:
   - Absolute level: Specific level to reach (e.g., 70)
   - Relative level: Levels to gain from current (e.g., +5 levels)
5. Optional: Enable randomization for the target level to vary behavior
6. Click "Add" to add the condition

This creates a natural stopping point for your training session, perfect for goal-oriented skilling.

**Example 2: Setting an XP-Based Training Session**

For more precise control over training duration:

1. Select "Skill" from the Category dropdown
2. Select "Skill XP Goal" from the Type dropdown
3. Choose your skill from the dropdown
4. Enter your XP target (e.g., 50,000 XP)
5. Select whether this is absolute or relative to current XP
6. Click "Add" to add the condition

XP-based conditions give you finer control than level-based ones, especially at higher levels where levels take longer to achieve.

**Example 3: Level Requirement Safety Check**

To ensure you have the required level before attempting an activity:

1. Select "Skill" from the Category dropdown
2. Select "Skill Level Required" from the Type dropdown
3. Choose the required skill (e.g., "Agility")
4. Set the minimum level needed (e.g., 60 for Seers Village course)
5. Click "Add" to add the condition

This prevents your plugin from starting activities you don't have the levels for, avoiding failures and wasted time.

**Pro Tip:** Combine skill conditions with time conditions to create balanced training sessions. For example: "Train Woodcutting until level 70 OR until 2 hours have passed."

### Resource Conditions

Resource conditions are essential for inventory management and gathering activities. They let you create rules based on items, resources, and inventory state.

#### Available Resource Condition Types

| Condition Type | Description | Common Use Cases |
|----------------|-------------|-----------------|
| Item Collection | Tracks collected items over time | Stop after gathering 1000 feathers |
| Process Items | Tracks items processed/created | Stop after smithing 500 cannonballs |
| Gather Resources | Tracks gathered raw resources | Stop after mining 200 iron ore |
| Inventory Item Count | Checks current inventory state | Start when inventory is empty, stop when full |
| Bank Item Count | Checks items in bank | Stop when bank has 10,000 vials |
| Loot Item | Checks for specific drops | Keep killing until specific drop obtained |
| Item Required | Checks for required items | Only start if you have antipoison |

#### Practical Resource Condition Examples

**Example 1: Setting Up an Efficient Woodcutting Session**

To create a stop condition that triggers when you've cut 500 logs or your inventory is full:

1. Select "Resource" from the Category dropdown
2. Select "Gather Resources" from the Type dropdown
3. Enter "Yew logs" in the item name field
4. Set the target amount to 500
5. Click "Add" to add this condition
6. Add another condition for inventory:
   - Select "Resource" category again
   - Select "Inventory Item Count" type
   - Enter "28" for full inventory
   - Set the comparison to "greater than or equal to"
   - Click "Add"
7. Ensure these are in an OR relationship in the condition tree

Now your plugin will stop when either you've gathered 500 logs OR your inventory becomes full.

**Example 2: Setting Up a Profitable Crafting Session**

For a plugin that runs until you've crafted a specific number of items:

1. Select "Resource" from the Category dropdown
2. Select "Process Items" from the Type dropdown
3. Enter "Gold bracelet" in the item name field
4. Set the target amount to 100
5. Enable "Track profits" if you want to monitor profitability
6. Click "Add" to add the condition

This will track how many items you've crafted and automatically stop once you reach your goal.

**Example 3: Required Items Safety Check**

To ensure you have the necessary items before starting a dangerous activity:

1. Select "Resource" from the Category dropdown
2. Select "Item Required" from the Type dropdown
3. Enter "Super antipoison" in the item name field
4. Set the minimum amount to 2
5. Click "Add" to add this condition
6. Add more required items as needed using AND logic

This creates a safety check that prevents your plugin from starting unless you have all the required items.

### Location Conditions

Location conditions allow you to create rules based on where your character is in the game world. These are essential for region-specific activities and location-based task switching.

#### Available Location Condition Types

| Condition Type | Description | Strategic Uses |
|----------------|-------------|----------------|
| Position | Based on exact coordinates with radius | Create specific action spots |
| Area | Based on a rectangular area | Define larger working zones |
| Region | Based on game region IDs | Control activities by game area |
| Distance | Based on distance from a point | Create proximity-based triggers |
| Not In Area | Inverted area condition | Create exclusion zones |

#### Practical Location Condition Examples

**Example 1: Setting Up a Mining Location**

To create a condition that only runs your plugin when you're at a specific mining site:

1. Navigate to the desired mining location in-game
2. Select "Location" from the Category dropdown
3. Select "Area" from the Type dropdown
4. Click "Capture Current Area" to use your current position
5. Adjust the area size using the sliders (make it large enough to cover the entire mining area)
6. Click "Add" to add the condition

This ensures your mining plugin only runs when you're actually at the mining site, preventing it from activating in inappropriate locations.

**Example 2: Creating a Safe Zone**

To create a condition that stops a plugin when you enter a dangerous area:

1. Select "Location" from the Category dropdown
2. Select "Not In Area" from the Type dropdown
3. Define the area you consider safe (e.g., a bank or city)
4. Click "Add" to add the condition

This works as a safety measure - the plugin will only run when you're in the defined safe area and automatically stop if you leave it.

**Example 3: Bank-Proximity Condition**

For creating a condition that activates when you're near a bank:

1. Stand near your preferred bank
2. Select "Location" from the Category dropdown
3. Select "Position" from the Type dropdown
4. Click "Use Current Position"
5. Set an appropriate radius (e.g., 15 tiles to cover the bank area)
6. Click "Add" to add the condition

This is perfect for banking plugins that should only activate when you're actually near a bank, preventing premature attempts to bank while still gathering resources.

**Pro Tip:** Combine location conditions with resource conditions for complete automation cycles. For example: "Stop mining when inventory is full OR player is no longer in the mining area."

### NPC Conditions

NPC conditions allow you to create rules based on interactions with non-player characters in the game. They're particularly useful for combat activities, boss fights, and NPC-dependent tasks.

#### Available NPC Condition Types

| Condition Type | Description | Strategic Uses | Notes |
|----------------|-------------|----------------|-------|
| NPC Kill Count | Tracks how many of a specific NPC you've killed | Stop after killing 100 goblins | May have tracking issues in multi-combat areas |

> **Note**: Currently, only the NPC Kill Count condition is fully implemented. Future versions may include NPC Presence and NPC Interaction conditions.

#### Practical NPC Condition Examples

**Example 1: Basic Kill Count Goal**

For a plugin that runs until you've killed a certain number of NPCs:

1. Select "NPC" from the Category dropdown
2. Select "Kill Count" from the Type dropdown
3. Enter the NPC name (e.g., "Goblin")
4. Set the target count (e.g., 50)
5. Click "Add" to add the condition

The plugin will track NPC interactions and count kills until the target is reached.

**Example 2: Multi-NPC Kill Goals with Logical Conditions**

For more complex hunting goals that involve multiple NPC types:

1. Create a kill count condition for the first NPC type
2. Create a kill count condition for the second NPC type
3. Use logical operators to combine them:
   - AND: Must kill both target counts (e.g., "Kill 50 goblins AND 30 rats")
   - OR: Killing either target count is sufficient (e.g., "Kill 50 goblins OR 30 rats")

**Advanced Features:**

- **Name Pattern Matching**: Use regular expressions to match similar NPCs (e.g., "Goblin.*" matches all goblin variants)
- **Randomized Goals**: Set min/max ranges for more varied play patterns
- **Progress Tracking**: View detailed statistics including kill rate and completion percentage
- **Interaction Detection**: Accurately tracks which NPCs you're engaged with

### Varbit Conditions

Varbit conditions relate to game state variables and track internal game values, allowing you to create rules based on quests, minigames, collection logs, and other game state information.

#### Available Varbit Condition Types

| Condition Type | Description | Use Case | 
|----------------|-------------|----------|
| Collection Log - Bosses | Based on boss collection log entries | "Stop after collecting all GWD items" |
| Collection Log - Minigames | Based on minigame collection log entries | "Run until Tempoross log is complete" |
| Custom Varbit | Track a specific varbit or varp ID | "Wait until quest state changes" |

#### Advanced Varbit Features

- **Relative or Absolute Values**: Compare against absolute values or relative changes from the starting state
- **Comparison Operators**: Use equals, not equals, greater than, less than, etc.
- **Randomization**: Set min/max target ranges for more varied behavior

#### Working with Varbit Conditions

Varbit conditions provide powerful ways to track game progress but require some technical understanding:

1. **Finding Varbit IDs**: Use developer tools to identify the relevant varbit ID for your condition
2. **Understanding Value Ranges**: Most varbits use values 0-1 for off/on states, but some track counts or progress
3. **Test Thoroughly**: Always test your varbit conditions before relying on them for automation

## Creating Complex Logical Conditions

The true power of the scheduler comes from creating sophisticated logic by combining multiple conditions. Think of this as building "if-then" statements that determine when your plugins run.

### Understanding Logical Operators

Before we dive in, let's understand the basic logical operators:

- **AND**: All conditions must be true (like saying "I'll only go fishing IF I have bait AND I have a fishing rod")
- **OR**: Any condition can be true (like saying "I'll stop fishing IF my inventory is full OR it's been 2 hours")
- **NOT**: Inverts a condition (like saying "Run the plugin when I'm NOT in the Wilderness")

### Using AND Logic (All Conditions)

Use AND logic when you want a plugin to start/stop only when ALL specified conditions are met:

1. Add your first condition (e.g., "Time Window: 6PM-10PM")
2. In the condition tree, select the root node
3. Click "Convert to AND" (the icon changes to show AND logic)
4. Add your second condition (e.g., "Location: Mining Guild")
5. Add any additional conditions

**Real-World Example: Safe Mining Training**
```
AND
├── TimeWindow(6:00 PM to 10:00 PM)
├── Location(Mining Guild)
└── InventoryItemCount(Pickaxe) >= 1
```
This setup ensures your plugin only runs in the evening, when you're in the Mining Guild, and have a pickaxe.

### Using OR Logic (Any Conditions)

Use OR logic when you want a plugin to start/stop when ANY of the specified conditions are met:

1. Add your first condition (e.g., "Inventory Full")
2. In the condition tree, ensure it's set to OR (this is the default)
3. Add your second condition (e.g., "Time Duration: 2 hours")
4. Add any additional conditions

**Real-World Example: Smart Fishing Stop Condition**
```
OR
├── InventoryItemCount(Raw fish) >= 28
├── TimeDuration(2 hours)
└── PlayerHealth < 20%
```
This will stop your fishing plugin when your inventory fills OR you've been fishing for 2 hours OR your health gets dangerously low.

### Using NOT Logic (Negate Conditions)

Use NOT logic when you want to invert a condition's result:

1. Select the condition in the tree
2. Click the "Negate" button (usually shown as a "!" icon)
3. The condition will be inverted and shown with "NOT" in the description

**Real-World Example: Avoid Dangerous Times**

```text
NOT(TimeWindow(2:00 AM to 6:00 AM))
```

This creates a condition that's true except during the specified hours, helping you avoid playing during suspicious times.

### Creating Nested Conditions

For more complex logic, you can create nested condition groups that combine AND and OR logic:

1. Add several conditions
2. Select a subset of those conditions in the tree
3. Click "Group AND" or "Group OR" to create a sub-group
4. The sub-group will now behave as a single condition within the parent group

**Real-World Example: Advanced Skilling Strategy**

```text
AND
├── OR
│   ├── Location(Varrock Mine)
│   └── Location(Al Kharid Mine)
└── AND
    ├── InventoryItemCount(Total) < 28
    └── NOT(NearbyPlayer > 5)
```

This complex condition translates to: "Run the plugin when I'm at either mining location AND my inventory isn't full AND there aren't too many players nearby."

### Visual Indicators in the Logical Tree

The condition tree provides visual feedback about your logical structure:

- **Connecting lines**: Show the relationship between conditions
- **Icons**: Indicate AND/OR/NOT relationships
- **Checkmarks/X marks**: Show which conditions are currently satisfied
- **Highlighting**: Indicates which condition is selected for editing

Remember that well-designed logical structures can create extremely sophisticated automation patterns without requiring any actual coding!

## Understanding One-Time vs. Recurring Conditions

When configuring conditions, it's important to understand which conditions trigger just once and which can trigger repeatedly. This affects how your plugins will behave over extended periods.

### One-Time Conditions

One-time conditions are triggers that once satisfied, will stay satisfied forever. They're perfect for milestone events and permanent changes.

**Examples of one-time conditions:**

- **Specific Time condition**: Triggers once exactly at 8:00 PM on June 15
- **Skill Level condition**: Triggers once when Woodcutting reaches level 70  
- **Item Collection condition**: Triggers once after collecting 1000 feathers
- **Collection Log condition**: Triggers once after completing a boss collection

**How to identify one-time conditions:**

- Look for the "One-time" label in the schedule table
- These conditions use absolute values rather than ranges or states
- They typically represent achievement of a specific goal

**Strategic use:**
One-time conditions are excellent for progression-based automation. For example, you might set up a series of plugins that activate as you reach certain milestones, automatically moving your character from one training method to the next as you level up.

### Recurring Conditions

Recurring conditions can trigger multiple times as their state changes between satisfied and unsatisfied.

**Examples of recurring conditions:**

- **Time Window condition**: Triggers daily during set hours (e.g., 6PM-10PM)  
- **Interval condition**: Triggers repeatedly at set intervals (e.g., every 2 hours)  
- **Inventory Item Count**: Can trigger repeatedly as inventory fills and empties  
- **Location condition**: Triggers each time you enter or leave an area

**How they work:**
These conditions can switch between active and inactive states multiple times. For example, a Time Window condition for 6PM-10PM will be:

- Inactive from midnight until 6PM
- Active from 6PM to 10PM
- Inactive from 10PM to midnight
- Active again at 6PM the next day

**Strategic use:**
Use recurring conditions to create cyclical behavior patterns. For example, combine an inventory condition with a location condition to create a gathering cycle: gather resources until inventory is full, bank when near a bank, repeat when inventory is empty.

## Indicators and Visualization

The condition panel provides visual feedback on condition status:

- **Green checkmark (✓)**: Condition is currently satisfied
- **Red X (✗)**: Condition is not currently satisfied
- **Lightning bolt (⚡)**: Condition is currently relevant to the plugin's state

The condition tree visualizes the logical structure of your conditions, allowing you to see at a glance how they're organized.

## Tips for Effective Condition Configuration

1. **Start simple**: Begin with just one or two conditions and add complexity gradually
2. **Test thoroughly**: Always test your conditions with controlled parameters first
3. **Use the condition tree**: View the logical structure to ensure it matches your intent
4. **Monitor condition status**: Watch the indicators to see if conditions are behaving as expected
5. **Combine time with other conditions**: For example, "Run for 2 hours OR until inventory is full"
6. **Use nested logical groups**: For complex scenarios like "Run when (at location A OR location B) AND (have antipoison)"

## Common Issues and Solutions

### Issue: Plugin never starts

- **Possible cause**: Start conditions are too restrictive
- **Solution**: Check if all required conditions are being met, or switch from AND to OR logic

### Issue: Plugin never stops

- **Possible cause**: Stop conditions are never met or are incorrectly configured
- **Solution**: Add a time-based fallback stop condition

### Issue: Plugin starts or stops at unexpected times

- **Possible cause**: Logical structure (AND/OR) is not what you intended
- **Solution**: Review the condition tree and restructure as needed

### Issue: Condition status indicators don't match expectations

- **Possible cause**: The condition parameters don't match the current game state
- **Solution**: Verify the current game state and adjust the condition parameters

## Advanced Condition Techniques

### Using Lock Conditions

Lock conditions prevent a plugin from stopping during critical operations:

1. Add your regular stop conditions
2. Add a LockCondition for critical operations
3. In your plugin code, activate the lock when needed
4. The plugin won't stop while the lock is active, even if stop conditions are met

### Time Randomization

For more human-like behavior:

1. When creating time conditions, use the randomization options
2. For intervals, set a min/max range instead of exact times
3. For time windows, consider adding random variations to start/end times

### Progressive Conditions

Create conditions that change as the plugin runs:

1. Use the plugin's `onStopConditionCheck()` method
2. Modify conditions based on progress
3. This allows for dynamic behavior adaptation

## Condition Reliability Disclaimer

> **Important**: Not all conditions have been thoroughly tested in all scenarios. The following list indicates the confidence level in each condition type's reliability:
> 
> ### High Confidence (Thoroughly Tested)
> - **Time Conditions**: Well-tested and reliable, though some features users want may be missing (feedback appreciated)
> - **Skill Conditions**: Thoroughly tested and reliable
> - **Location Conditions**: Well-tested in most common areas
> - **Logical Conditions**: Thoroughly tested (AND, OR, NOT operators)
> - **Lock Conditions**: Thoroughly tested
> - **Item Collection Conditions** (from Resource Conditions): Well-tested for most common items
>
> ### Medium Confidence (Tested but May Have Edge Cases)
> - **Varbit Conditions**: Tested for collection log entries and common game variables
> - **Bank Item Conditions**: May have edge cases with certain items
> - **Process Item Conditions**: May have tracking issues with certain processing methods
>
> ### Lower Confidence (Newer Implementations)
> - **NPC Conditions**: Implementation is still being refined, particularly for multi-combat areas
> - **NPC Kill Count Conditions**: May not track kills accurately in crowded areas or specific circumstances
>
> If you encounter any issues with condition reliability, please report them with specific details to help improve the system. For critical tasks, we recommend using the high confidence conditions or adding fallback conditions (such as time limits).

## Conclusion

Mastering the condition configuration system is key to effectively using the Plugin Scheduler. With proper condition setup, you can create sophisticated automation plans that respond intelligently to game states, time factors, and resource availability.

For more information on how conditions fit into the overall Scheduler workflow, including default plugins, priorities, and the "Allow Continue" setting, see the [Plugin Scheduler User Guide](user-guide.md).

For implementation details about specific condition types, see the [API documentation for conditions](api/conditions/).
