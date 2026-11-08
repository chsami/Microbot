package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.Plugin;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class MicrobotPluginFailuresTest
{
    public static class FixturePlugin extends Plugin
    {
    }

    @After
    public void tearDown()
    {
        MicrobotPluginFailures.setListener(() -> { });
    }

    @Test
    public void failureKeepsTheExceptionUntilAStartSucceeds()
    {
        Plugin plugin = new FixturePlugin();
        IllegalStateException failure = new IllegalStateException("Missing config");

        MicrobotPluginFailures.failed(plugin, failure);
        assertSame(failure, MicrobotPluginFailures.failure(plugin));

        MicrobotPluginFailures.started(plugin);
        assertNull(MicrobotPluginFailures.failure(plugin));
    }

    @Test
    public void aRemovedPluginsFailureIsDroppedSoItCanBeUnloaded()
    {
        Plugin plugin = new FixturePlugin();
        MicrobotPluginFailures.failed(plugin, new IllegalStateException("Missing config"));

        MicrobotPluginFailures.removed(plugin);

        assertNull(MicrobotPluginFailures.failure(plugin));
    }

    @Test
    public void describeNamesTheTypeAndTheMessage()
    {
        assertEquals("IllegalStateException: Missing config",
            MicrobotPluginFailures.describe(new IllegalStateException("Missing config")));
        assertEquals("NullPointerException", MicrobotPluginFailures.describe(new NullPointerException()));
    }

    @Test
    public void failuresBelongToTheExactInstance()
    {
        Plugin failed = new FixturePlugin();

        MicrobotPluginFailures.failed(failed, new IllegalStateException("x"));

        assertNull(MicrobotPluginFailures.failure(new FixturePlugin()));
    }

    @Test
    public void listenerHearsFailuresAndClears()
    {
        AtomicInteger calls = new AtomicInteger();
        MicrobotPluginFailures.setListener(calls::incrementAndGet);
        Plugin plugin = new FixturePlugin();

        MicrobotPluginFailures.started(plugin);
        assertEquals("Nothing to clear, nothing to redraw", 0, calls.get());

        MicrobotPluginFailures.failed(plugin, new IllegalStateException("x"));
        MicrobotPluginFailures.started(plugin);
        assertEquals(2, calls.get());
    }

    @Test
    public void aThrowingListenerDoesNotReachTheCaller()
    {
        MicrobotPluginFailures.setListener(() -> {
            throw new IllegalStateException("panel broke");
        });
        Plugin plugin = new FixturePlugin();

        MicrobotPluginFailures.failed(plugin, new IllegalStateException("x"));
        MicrobotPluginFailures.started(plugin);

        assertNull(MicrobotPluginFailures.failure(plugin));
    }
}
