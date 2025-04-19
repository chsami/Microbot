# Time Conditions

Time conditions are a fundamental part of the Plugin Scheduler system that allow plugins to be scheduled based on various time-related factors.

## Overview

Time conditions enable plugins to run at specific times, intervals, or within time windows. They are essential for automating plugin execution based on real-world time constraints.

## Available Time Conditions

### IntervalCondition

The `IntervalCondition` allows plugins to run at regular time intervals.

**Usage:**
```java
// Run every 30 minutes
IntervalCondition condition = new IntervalCondition(Duration.ofMinutes(30));
```

**Key features:**
- Flexible interval specification using Java's `Duration` class
- Configurable randomization to add variation to the timing
- Reset capability to restart the interval countdown

### SingleTriggerTimeCondition

The `SingleTriggerTimeCondition` triggers once at a specific date and time.

**Usage:**
```java
// Trigger at a specific date and time
ZonedDateTime triggerTime = ZonedDateTime.of(2025, 4, 18, 15, 0, 0, 0, ZoneId.systemDefault());
SingleTriggerTimeCondition condition = new SingleTriggerTimeCondition(triggerTime);
```

**Key features:**
- One-time execution at a precise moment
- Cannot trigger again after the specified time has passed
- Progress tracking toward the trigger time

### TimeWindowCondition

The `TimeWindowCondition` allows plugins to run within specific time windows on a daily basis.

**Usage:**
```java
// Run between 9 AM and 5 PM
TimeWindowCondition condition = new TimeWindowCondition(
    LocalTime.of(9, 0),  // Start time
    LocalTime.of(17, 0)  // End time
);
```

**Key features:**
- Daily recurring time windows
- Configurable start and end times
- Support for windows that span midnight

### DayOfWeekCondition

The `DayOfWeekCondition` restricts plugin execution to specific days of the week.

**Usage:**
```java
// Run only on weekends
Set<DayOfWeek> weekendDays = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
DayOfWeekCondition condition = new DayOfWeekCondition(weekendDays);
```

**Key features:**
- Selection of multiple days of the week
- Combines with other time conditions to create complex schedules

## Common Features of Time Conditions

All time conditions implement the `TimeCondition` interface, which extends the base `Condition` interface and provides additional time-specific functionality:

- `getCurrentTriggerTime()`: Returns the next time this condition will be satisfied
- `getDurationUntilNextTrigger()`: Returns the time remaining until the next trigger
- `hasTriggered()`: Indicates if a one-time condition has already triggered
- `canTriggerAgain()`: Determines if the condition can trigger again in the future

## Using Time Conditions as Start Conditions

When used as start conditions, time conditions determine when a plugin should be activated:

```java
PluginScheduleEntry entry = new PluginScheduleEntry("MyPlugin", true);
// Run every 2 hours
entry.addStartCondition(new IntervalCondition(Duration.ofHours(2)));
```

## Using Time Conditions as Stop Conditions

When used as stop conditions, time conditions control when a plugin should be deactivated:

```java
// Stop after running for 30 minutes
entry.addStopCondition(new SingleTriggerTimeCondition(
    ZonedDateTime.now().plusMinutes(30)
));
```

## Combining Time Conditions

Time conditions can be combined with logical conditions to create complex scheduling rules:

```java
// Create a logical AND condition
AndCondition timeRules = new AndCondition();

// Add time window (9 AM to 5 PM)
timeRules.addCondition(new TimeWindowCondition(
    LocalTime.of(9, 0),
    LocalTime.of(17, 0)
));

// Add day of week condition (weekdays only)
Set<DayOfWeek> weekdays = EnumSet.of(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
);
timeRules.addCondition(new DayOfWeekCondition(weekdays));

// Add the combined time rules as a start condition
entry.addStartCondition(timeRules);
```