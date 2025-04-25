package net.runelite.client.plugins.microbot.pluginscheduler;

import java.awt.Color;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents the various states the scheduler can be in
 */
public enum SchedulerState {
    UNINITIALIZED("Uninitialized", "Plugin is not yet initialized", new Color(150, 150, 150)),
    INITIALIZING("Initializing", "Plugin is initializing", new Color(255, 165, 0)),
    READY("Ready", "Ready to run scheduled plugins", new Color(0, 150, 255)),
    SCHEDULING("SCHEDULING", "Scheduler is running and monitoring", new Color(76, 175, 80)),
    STARTING_PLUGIN("Starting Plugin", "Starting a scheduled plugin", new Color(200, 230, 0)),
    RUNNING_PLUGIN("Running Plugin", "Scheduled plugin is running", new Color(0, 200, 83)),
    WAITING_FOR_LOGIN("Waiting for Login", "Waiting for user to log in", new Color(255, 215, 0)),
    HARD_STOPPING_PLUGIN("Hard Stopping Plugin", "Stopping the current plugin", new Color(255, 120, 0)),
    SOFT_STOPPING_PLUGIN("Soft Stopping Plugin", "Stopping the current plugin", new Color(255, 120, 0)),
    HOLD("Stopped", "Scheduler was manually stopped", new Color(244, 67, 54)),
    ERROR("Error", "Scheduler encountered an error", new Color(255, 0, 0)),
    SHORT_BREAK("Short Break", "Taking a short break until next plugin", new Color(100, 149, 237)),
    WAITING_FOR_SCHEDULE("Next Schedule Soon", "Waiting for upcoming scheduled plugin", new Color(147, 112, 219)),
    WAITING_FOR_STOP_CONDITION("Waiting For Stop Condition", "Waiting For Stop Condition", new Color(255, 140, 0)),
    LOGIN("Login", "Try To Login", new Color(255, 215, 0));
    private final String displayName;
    private final String description;
    private final Color color;
    @Setter
    @Getter
    private String stateInformation = "";

    SchedulerState(String displayName, String description, Color color) {
        this.displayName = displayName;
        this.description = description;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Color getColor() {
        return color;
    }
    public boolean isSchedulerActive() {
        return this != SchedulerState.UNINITIALIZED &&
        this != SchedulerState.INITIALIZING &&
        this != SchedulerState.ERROR &&
        this != SchedulerState.HOLD &&
        this != SchedulerState.READY;
    }

    /**
     * Determines if the scheduler is actively running a plugin or about to run one
     */
    public boolean isActivelyRunning() {
        return isSchedulerActive() && 
               (this == SchedulerState.RUNNING_PLUGIN
               );
    }
    public boolean isAboutStarting() {
        return this == SchedulerState.STARTING_PLUGIN || this== SchedulerState.WAITING_FOR_STOP_CONDITION ||
               this == SchedulerState.WAITING_FOR_LOGIN;
    }

    /**
     * Determines if the scheduler is in a waiting state between scheduling a plugin
     */
    public boolean isWaiting() {
        return isSchedulerActive() &&
               (this == SchedulerState.SCHEDULING ||
               this == SchedulerState.WAITING_FOR_SCHEDULE ||
               this == SchedulerState.SHORT_BREAK);
    }

    public boolean isInitializing() {
        return this == SchedulerState.INITIALIZING || this == SchedulerState.UNINITIALIZED;
    }
    public boolean isStopping() {
        return this == SchedulerState.SOFT_STOPPING_PLUGIN ||
        this == SchedulerState.HARD_STOPPING_PLUGIN;
    }
}