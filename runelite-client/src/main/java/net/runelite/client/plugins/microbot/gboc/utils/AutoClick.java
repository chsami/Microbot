package net.runelite.client.plugins.microbot.gboc.utils;

import com.google.inject.Singleton;

import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.TimeUnit;


import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.gboc.GbocPlugin;

@Slf4j
@Singleton
public class AutoClick extends Script {
    @Setter
    private boolean ready = false;

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || (Objects.equals(GbocPlugin.currentAction.getName(), "Idle") || !ready)) return;
            var client = Microbot.getClient();
            var canvas = client.getCanvas();
            MouseEvent event = new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 1, 1, 1, false, MouseEvent.BUTTON1);
            event.setSource("gboc");
            canvas.dispatchEvent(event);
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
}