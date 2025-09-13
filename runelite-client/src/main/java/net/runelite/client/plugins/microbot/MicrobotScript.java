package net.runelite.client.plugins.microbot;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MicrobotScript extends  Script{
    @Override
    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, java.util.concurrent.TimeUnit.MILLISECONDS);
        return true;
    }
}
