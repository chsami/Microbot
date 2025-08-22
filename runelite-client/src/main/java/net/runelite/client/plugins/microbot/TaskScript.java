package net.runelite.client.plugins.microbot;


import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TaskScript extends Script {

    private List<Task> nodes;

    public TaskScript() {
    }

    public void addNodes(List<Task> tasks) {
        nodes = tasks;
    }

    public void shutdown() {
        super.shutdown();
        nodes = java.util.Collections.emptyList();
    }

    public boolean run() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isDone()) {
            return false; // already running
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
            Rs2AntibanSettings.naturalMouse = true;
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;

            try {
                final List<Task> snapshot = this.nodes;
                if (snapshot == null || snapshot.isEmpty()) {
                    return;
                }
                for (Task node : nodes) {
                    if (node.accept()) {
                        sleep(node.execute());
                        break;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                Microbot.logStackTrace(this.getClass().getSimpleName(), ie);
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }


}
