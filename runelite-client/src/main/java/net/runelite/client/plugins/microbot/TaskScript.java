package net.runelite.client.plugins.microbot;


import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.Config;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TaskScript extends Script {

    private static List<Task> nodes;
    private static Config config;

    public TaskScript(Config pluginConfig) {
        config = pluginConfig;
    }

    public TaskScript() {
    }

    public void addNodes(List<Task> tasks) {
        nodes = tasks;
    }

    public void shutdown() {
        nodes = null;
        super.shutdown();
    }

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
            Rs2AntibanSettings.naturalMouse = true;
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;

            try {
                for (Task node : nodes) {
                    if (node.accept()) {
                        sleep(node.execute());
                        break;
                    }
                }
            } catch (Exception ex) {
                System.out.println("Error was thrown");
                System.out.println(ex.getMessage());
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }


}
