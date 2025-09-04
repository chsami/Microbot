package net.runelite.client.plugins.microbot.pluginscheduler.event;

/**
 * Enum representing the granular success states for plugin execution events.
 * This provides more detailed reporting of execution outcomes beyond simple success/failure.
 */
public enum ExecutionResult {
    /**
     * Plugin execution completed successfully without any issues.
     * The plugin accomplished its intended task and is ready for normal scheduling.
     */
    SUCCESS("Success", "Plugin completed successfully", true, false),
    
    /**
     * Plugin encountered a recoverable issue but can potentially run again.
     * Examples: temporary resource unavailability, network timeouts, minor game state issues.
     * The plugin schedule entry remains enabled but the failure is tracked.
     */
    SOFT_FAILURE("Soft Failure", "Plugin failed but can retry", false, true),
    
    /**
     * Plugin encountered a critical failure that prevents future execution.
     * Examples: invalid configuration, missing dependencies, critical errors.
     * The plugin schedule entry should be disabled after this result.
     */
    HARD_FAILURE("Hard Failure", "Plugin failed critically", false, false);
    
    private final String displayName;
    private final String description;
    private final boolean isSuccess;
    private final boolean canRetry;
    
    ExecutionResult(String displayName, String description, boolean isSuccess, boolean canRetry) {
        this.displayName = displayName;
        this.description = description;
        this.isSuccess = isSuccess;
        this.canRetry = canRetry;
    }
    
    /**
     * @return Human-readable display name for this result
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * @return Detailed description of what this result means
     */
    public String getDescription() {
        return description;
    }
    
    /**
     * @return true if this represents a successful execution
     */
    public boolean isSuccess() {
        return isSuccess;
    }
    
    /**
     * @return true if the plugin can be retried after this result
     */
    public boolean canRetry() {
        return canRetry;
    }
    
    /**
     * @return true if this represents any kind of failure (soft or hard)
     */
    public boolean isFailure() {
        return !isSuccess;
    }
    
    /**
     * @return true if this is a soft failure that allows retries
     */
    public boolean isSoftFailure() {
        return this == SOFT_FAILURE;
    }
    
    /**
     * @return true if this is a hard failure that prevents retries
     */
    public boolean isHardFailure() {
        return this == HARD_FAILURE;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}