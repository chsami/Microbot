package net.runelite.client.plugins.microbot.breakhandler;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pluginscheduler.util.SchedulerPluginUtil;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.ui.ClientUI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * BreakHandlerScript manages automated break scheduling and execution for botting activities.
 * This script handles timing between breaks, break duration management, play schedule enforcement,
 * and integration with micro-break systems and plugin lock conditions.
 * 
 * The script operates on a scheduled executor that runs every second to:
 * - Monitor break timers and trigger breaks when appropriate
 * - Handle user-initiated break toggles
 * - Enforce play schedule restrictions
 * - Manage break state transitions and cleanup
 * - Update UI elements like window titles during breaks
 * 
 * Key Features:
 * - Configurable break intervals and durations
 * - Play schedule enforcement with automatic logout
 * - Integration with micro-break antiban system
 * - Lock state management to prevent breaks during critical operations
 * - World switching capabilities after breaks
 * - Statistics tracking for break frequency
 * 
 * @version 1.0.0
 */

@Slf4j
public class BreakHandlerScript extends Script {
    public static String version = "1.0.0";
    
    // Constants for better maintainability
    private static final int SCHEDULER_INTERVAL_MS = 1000;
    private static final int MINUTES_TO_SECONDS = 60;

    public static int breakIn = -1;
    public static int breakDuration = -1;
    public static Duration setBreakDurationTime = Duration.ZERO;
    public static int totalBreaks = 0;
    
    
    public static AtomicBoolean lockState = new AtomicBoolean(false);
    public static void setLockState(boolean state) {
        boolean currentState = BreakHandlerScript.lockState.get();
        if (currentState != state) {
            log.info("\n\t-Setting lock state to: " + state + "\n\t-previous state: " + currentState);
            BreakHandlerScript.lockState.set(state);
        }
    }
    private String title = "";
    private BreakHandlerConfig config;

    public static boolean isBreakActive() {
        return breakDuration >= 0 ;
    }
    public static boolean isMicroBreakActive() {
        return Rs2AntibanSettings.takeMicroBreaks && Rs2AntibanSettings.microBreakActive;
    }

    public static String formatDuration(Duration duration, String header) {
        return String.format(header + " %s", formatDuration(duration));
    }

    /**
     * Formats a duration into HH:MM:SS format
     * @param duration the duration to format
     * @return formatted time string
     */
    public static String formatDuration(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return "00:00:00";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public boolean run(BreakHandlerConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = false;
        title = ClientUI.getFrame().getTitle();
        initializeNextBreakTimer();        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                processBreakHandlerLogic();
            } catch (Exception ex) {
                Microbot.log("BreakHandler error: " + ex.getMessage());
            }
        }, 0, SCHEDULER_INTERVAL_MS, TimeUnit.MILLISECONDS);
        
        return true;
    }

    /**
     * Main break handler logic - processes all break-related operations
     */
    private void processBreakHandlerLogic() {
        // Handle immediate user requests first
        if (handleConfigToggles()) {
            return;
        }
        
        // Handle play schedule logic
        if (handlePlaySchedule()) {
            return;
        }
        
        // Update timers and UI
        updateBreakTimers();
        updateWindowTitle();
        
        // Handle break state transitions
        if (shouldEndBreak()) {
            log.debug(" Break ended naturally - " +
                    "\nshouldStartBreak: " + shouldStartBreak() + 
                    "\nshouldEndBreak: " + shouldEndBreak() + 
                 "\nbreakDuration: " + breakDuration + 
                 "\nbreakIn: " + breakIn + 
                 "\nisLockState: " + isLockState() + 
                 "\npauseAllScripts: " + Microbot.pauseAllScripts.get() + 
                 "\nPluginPauseEvent.isPaused: " + PluginPauseEvent.isPaused());
            stopBreak();
            return;
        }
        
        if (shouldStartBreak()) {
            log.debug(" Break start naturally - " +
                    "\nshouldStartBreak: " + shouldStartBreak() + 
                    "\nshouldEndBreak: " + shouldEndBreak() + 
                 "\nbreakDuration: " + breakDuration + 
                 "\nbreakIn: " + breakIn + 
                 "\nisLockState: " + isLockState() + 
                 "\npauseAllScripts: " + Microbot.pauseAllScripts.get() + 
                 "\nPluginPauseEvent.isPaused: " + PluginPauseEvent.isPaused());
            startBreak();
        }
    }

    /**
     * Handles immediate config toggle requests (breakNow, breakEndNow)
     * @return true if a toggle was processed and execution should return
     */
    private boolean handleConfigToggles() {
        if (config.breakNow() && !Microbot.pauseAllScripts.get() && !isLockState() && !PluginPauseEvent.isPaused()) {
            Microbot.log("Break start triggered via config toggle");
            startBreak();
            return true;
        }

        if (config.breakEndNow()) {
            Microbot.log("Break ended triggered via config toggle");
            stopBreak();
            return true;
        }
        
        return false;
    }

    /**
     * Handles play schedule logic for outside schedule hours
     * @return true if schedule logic was processed and execution should return
     */
    private boolean handlePlaySchedule() {
        if (config.playSchedule().isOutsideSchedule() && config.usePlaySchedule() && !isLockState()) {
            Duration untilNextSchedule = config.playSchedule().timeUntilNextSchedule();
            breakIn = -1;
            breakDuration = (int) untilNextSchedule.toSeconds();
            setBreakDurationTime = Duration.ofSeconds(breakDuration);
            return true;
        }
        return false;
    }

    /**
     * Updates break-related timers and duration calculations
     */
    private void updateBreakTimers() {
        // Count down to next break
        if (breakIn >= 0 && breakDuration <= 0) {
            if (!(Rs2AntibanSettings.takeMicroBreaks && config.onlyMicroBreaks())) {
                if(Microbot.isLoggedIn()) {
                    breakIn--;
                }
            }
        }

        // Count down active break
        if (breakDuration >= 0) {
            breakDuration--;            
        }        
    }

    /**
     * Updates window title based on current break state
     */
    private void updateWindowTitle() {
        if (breakDuration > 0) {
            String formattedTime = formatDuration(Duration.ofSeconds(breakDuration));
            
            if (Rs2AntibanSettings.takeMicroBreaks && Rs2AntibanSettings.microBreakActive) {
                ClientUI.getFrame().setTitle("Micro break duration: " + formattedTime);
            } else if (config.playSchedule().isOutsideSchedule() && config.usePlaySchedule()) {
                ClientUI.getFrame().setTitle("Next schedule in: " + formattedTime);
            } else {
                ClientUI.getFrame().setTitle("Break duration: " + formattedTime);
            }
        }
    }

    /**
     * Determines if a break should naturally end
     */
    private boolean shouldEndBreak() {
        return ((breakDuration <= 0 && Microbot.pauseAllScripts.get() && PluginPauseEvent.isPaused()) );
               //!(Rs2AntibanSettings.universalAntiban && Rs2AntibanSettings.actionCooldownActive);
    }

    /**
     * Determines if a break should start
     */
    private boolean shouldStartBreak() {
        boolean normalBreakTime = breakIn <= 0 && !Microbot.pauseAllScripts.get() && !isLockState() && !PluginPauseEvent.isPaused();
        boolean microBreakTime = Rs2AntibanSettings.microBreakActive && !Microbot.pauseAllScripts.get() && !isLockState();
        
        return normalBreakTime || microBreakTime;
    }

    /**
     * Initializes the timer for the next break
     */
    private void initializeNextBreakTimer() {
        breakIn = Rs2Random.between(
            config.timeUntilBreakStart() * MINUTES_TO_SECONDS, 
            config.timeUntilBreakEnd() * MINUTES_TO_SECONDS
        );
        resetConfigToggles();
    }

    /**
     * Starts a break with appropriate duration and logout handling
     */
    private void startBreak() {
        Microbot.log("Starting break - breakNow: " + config.breakNow() + 
                    ", microBreak: " + Rs2AntibanSettings.microBreakActive+", playSchedule: " + config.usePlaySchedule() );

        // Pause all scripts
        Microbot.pauseAllScripts.compareAndSet(false, true);
        PluginPauseEvent.setPaused(true);
        // Handle micro break case
        if (Rs2AntibanSettings.microBreakActive) {
            return;
        }
        
        // Handle play schedule logout
        if (isOutsidePlaySchedule()) {
            Rs2Player.logout();
            return;
        }

        // Set break duration and handle logout
        setBreakDuration();
        if (config.logoutAfterBreak()) {
            Rs2Player.logout();
        }
    }

    /**
     * Stops the current break and resumes normal operation
     */
    private void stopBreak() {
     
        Microbot.log("Stopping break - duration: " + breakDuration + 
                    ", microBreak: " + Rs2AntibanSettings.microBreakActive);

        // Resume scripts and reset state
        resumeFromBreak();

        // Handle world switching
        handleWorldSwitching();
        
        // Update statistics and UI
        updateBreakStatistics();
        resetWindowTitle();
        
        // Clean up break state
        cleanupBreakState();
        
        // Reset config toggles
        resetConfigToggles();
    }

    /**
     * Checks if currently outside play schedule hours
     */
    private boolean isOutsidePlaySchedule() {
        return config.playSchedule().isOutsideSchedule() && config.usePlaySchedule();
    }

    /**
     * Sets the break duration based on configuration
     */
    private void setBreakDuration() {
        breakDuration = Rs2Random.between(
            config.breakDurationStart() * MINUTES_TO_SECONDS, 
            config.breakDurationEnd() * MINUTES_TO_SECONDS
        );
        setBreakDurationTime = Duration.ofSeconds(breakDuration);
    }

    /**
     * Resumes scripts and resets break timing
     */
    private void resumeFromBreak() {
        Microbot.pauseAllScripts.compareAndSet(true, false);
        PluginPauseEvent.setPaused(false);
        breakDuration = -1;
        setBreakDurationTime = Duration.ZERO;        
        if (breakIn <= 0) {
            initializeNextBreakTimer();
        }
        log.info("\n\tResuming scripts after break. \n\t\tcurrent duration: " + breakDuration+ "\n\t\tnext break in: " + breakIn);
    }

    /**
     * Handles world switching based on configuration
     */
    private void handleWorldSwitching() {
        if(!Microbot.isLoggedIn()){
            if (config.useRandomWorld()) {
                new Login(Login.getRandomWorld(Login.activeProfile.isMember()));
            } else {
                new Login();
            }
        }
    }

    /**
     * Updates break statistics
     */
    private void updateBreakStatistics() {
        totalBreaks++;
    }

    /**
     * Resets window title to original
     */
    private void resetWindowTitle() {
        ClientUI.getFrame().setTitle(title);
    }

    /**
     * Cleans up break-related state variables
     */
    private void cleanupBreakState() {
        if (Rs2AntibanSettings.takeMicroBreaks) {
            Rs2AntibanSettings.microBreakActive = false;
        }
    }

    /**
     * Resets config toggles to false after processing
     */
    private void resetConfigToggles() {
        if (config.breakNow()) {
            Microbot.getConfigManager().setConfiguration(
                BreakHandlerConfig.configGroup, "breakNow", false
            );
        }
        if (config.breakEndNow()) {
            Microbot.getConfigManager().setConfiguration(
                BreakHandlerConfig.configGroup, "breakEndNow", false
            );
        }
    }

    @Override
    public void shutdown() {
        resetBreakState();
        super.shutdown();
    }

    /**
     * Resets the break handler state
     */
    public void reset() {
        resetBreakState();
    }

    /**
     * Centralized method to reset all break-related state
     */
    private void resetBreakState() {
        if(isBreakActive()){
            PluginPauseEvent.setPaused(false);
            Microbot.pauseAllScripts.compareAndSet(true, false);
        }
        BreakHandlerScript.breakIn = -1;
        BreakHandlerScript.breakDuration = -1;
        BreakHandlerScript.setBreakDurationTime = Duration.ZERO;
        BreakHandlerScript.totalBreaks = 0;
        BreakHandlerScript.lockState.set(false);
  
        resetWindowTitle();
    }

    /**
     * Checks if the break handler is currently in a locked state.
     * This includes both the manual lock state and any locked conditions from schedulable plugins.
     * 
     * @return true if locked, false otherwise
     */
    public static boolean isLockState() {
        boolean hasLockedSchedulablePlugins = SchedulerPluginUtil.hasLockedSchedulablePlugins();
        return lockState.get();
    }

}
