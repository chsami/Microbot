package net.runelite.client.plugins.microbot.externalplugins.fixture;

import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.TimeUnit;

/** Loaded through a child loader: a script whose loop runs, or dies on its first tick. */
public class LoopScriptFixture extends Script {
    public void startLoop(boolean crash) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (crash) {
                throw new IllegalStateException("script loop crashed");
            }
        }, 0, 20, TimeUnit.MILLISECONDS);
    }
}
