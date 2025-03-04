package net.runelite.client.plugins.microbot.gboc.utils;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.microbot.Microbot;

@Getter
@Setter
public class ActionTask {
    private String name;
    private Runnable action;
    private boolean isRunning;

    public ActionTask() {
        this.reset();
    }

    public void reset() {
        this.name = "Idle";
        this.action = () -> {
        };
        this.isRunning = false;
    }

    public void set(String name, Runnable action) {
        this.name = name;
        this.action = action;
    }

    public void run() {
        Microbot.getClientThread().runOnSeperateThread(() -> {
            isRunning = true;
            action.run();
            reset();
            return true;
        });
    }
}
