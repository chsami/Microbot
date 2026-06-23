package net.runelite.client.plugins.microbot.util.camera;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NpcTracker {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1); // Single-threaded scheduler
    private ScheduledFuture<?> trackingTask; // Future to manage the scheduled task

    // The original method to track the actor
    private static void trackNpc(int npcId) {
        if (!Microbot.isLoggedIn()) {
            return;
        }
        Actor actor = Rs2Npc.getNpc(npcId); // Get the actor
        if (actor == null) {
            return; // Actor not found, do nothing
        }
        int yaw = Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(actor));
        Microbot.getClient().setCameraYawTarget(Rs2Camera.toClientAngleUnits(yaw));
    }

    /**
     * Method to start tracking the NPC
     *
     * @param npcId the ID of the NPC to track
     */
    public void startTracking(int npcId) {
        if (trackingTask != null && !trackingTask.isCancelled()) {
            Microbot.log("Already tracking an NPC");
            return; // Already tracking, do nothing
        }

        // Schedule the trackActor method to run every 50 milliseconds
        trackingTask = scheduler.scheduleAtFixedRate(() -> trackNpc(npcId), 0, 200, TimeUnit.MILLISECONDS);
        Microbot.log("Started tracking NPC with ID: " + npcId);
    }

    /**
     * Method to stop tracking the NPC
     */
    public void stopTracking() {
        if (trackingTask != null) {
            trackingTask.cancel(true); // Cancel the scheduled task
            trackingTask = null;
            Microbot.log("Stopped tracking NPC");
        }
    }

    /**
     * Method to check if a NPC is being tracked
     *
     * @return true if a NPC is being tracked, false otherwise
     */
    public boolean isTracking() {
        return trackingTask != null;
    }
}
