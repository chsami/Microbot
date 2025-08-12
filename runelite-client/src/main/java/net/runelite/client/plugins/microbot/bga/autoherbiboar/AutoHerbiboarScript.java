package net.runelite.client.plugins.microbot.bga.autoherbiboar;

import lombok.Setter;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.herbiboars.HerbiboarPlugin;
import java.util.concurrent.TimeUnit;

@Setter
public class AutoHerbiboarScript extends Script {
    private HerbiboarPlugin herbiboarPlugin;

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
