package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import java.util.concurrent.TimeUnit;

public class AutoHerbiboarScript extends Script {
    public boolean run(AutoHerbiboarConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
            } catch (Exception ex) {
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override
    public void shutdown() {
        super.shutdown();
    }
}
