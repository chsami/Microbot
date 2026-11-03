package net.runelite.client.plugins.microbot.externalplugins.fixture;

import net.runelite.client.plugins.microbot.Script;

import java.util.concurrent.ScheduledExecutorService;

/** Loaded through a child loader to exercise automatic Script resource ownership. */
public class ReloadableScriptFixture extends Script {
    public ScheduledExecutorService executor() {
        return scheduledExecutorService;
    }
}
