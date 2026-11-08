package net.runelite.client.plugins;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginFailures;
import net.runelite.client.plugins.microbot.ui.MicrobotPluginUsage;
import net.runelite.client.task.Scheduler;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

public class PluginManagerLifecycleTest {
    @Test
    public void loadingContinuesAfterPluginConfigurationFailures() throws Exception {
        Injector previous = RuneLite.getInjector();
        RuneLite.setInjector(Guice.createInjector());
        try {
            PluginManager manager = manager();
            List<Plugin> loaded = manager.loadPlugins(Arrays.asList(
                    ConfigurationFailurePlugin.class,
                    LinkageFailurePlugin.class,
                    LifecyclePlugin.class), null);

            assertEquals(1, loaded.size());
            assertTrue(loaded.get(0) instanceof LifecyclePlugin);
        } finally {
            RuneLite.setInjector(previous);
        }
    }

    @Test
    public void startsByStartPluginsAtLaunchAreNotUseButLaterStartsAre() throws Exception {
        List<Plugin> used = new java.util.ArrayList<>();
        MicrobotPluginUsage.setStartRecorder(used::add);
        try {
            PluginManager manager = manager();
            LifecyclePlugin plugin = new LifecyclePlugin();
            manager.add(plugin);

            manager.startPlugins();
            assertEquals(1, plugin.starts);
            assertTrue("Launch auto-starts do not count", used.isEmpty());

            onEdt(() -> manager.stopPlugin(plugin));
            onEdt(() -> manager.startPlugin(plugin));
            assertEquals(java.util.Collections.singletonList(plugin), used);

            onEdt(() -> manager.stopPlugin(plugin));
            onEdt(() -> manager.startPlugin(plugin, false));
            assertEquals("A start the caller says is not use does not count", 1, used.size());
        } finally {
            MicrobotPluginUsage.setStartRecorder(p -> { });
        }
    }

    @Test
    public void profileChangeRestartsAreNotUse() throws Exception {
        List<Plugin> used = new java.util.ArrayList<>();
        MicrobotPluginUsage.setStartRecorder(used::add);
        try {
            PluginManager manager = manager();
            // Past launch, where starts count as use.
            manager.startPlugins();
            LifecyclePlugin plugin = new LifecyclePlugin();
            manager.add(plugin);

            manager.onProfileChanged(null);
            onEdt(() -> null);

            assertTrue(manager.isActive(plugin));
            assertTrue(used.isEmpty());
        } finally {
            MicrobotPluginUsage.setStartRecorder(p -> { });
        }
    }

    @Test
    public void aThrowingUsageRecorderLeavesThePluginStartedAndNotFailed() throws Exception {
        MicrobotPluginUsage.setStartRecorder(p -> {
            throw new IllegalStateException("usage broke");
        });
        try {
            PluginManager manager = manager();
            // Past launch, where starts count as use.
            manager.startPlugins();
            LifecyclePlugin plugin = new LifecyclePlugin();
            manager.add(plugin);

            assertTrue(onEdt(() -> manager.startPlugin(plugin)));
            assertTrue(manager.isActive(plugin));
            assertNull(MicrobotPluginFailures.failure(plugin));
        } finally {
            MicrobotPluginUsage.setStartRecorder(p -> { });
        }
    }

    @Test
    public void aStartThatFailsAfterStartUpIsNotUse() throws Exception {
        List<Plugin> used = new java.util.ArrayList<>();
        MicrobotPluginUsage.setStartRecorder(used::add);
        try {
            EventBus eventBus = mock(EventBus.class);
            doThrow(new IllegalStateException("bad subscriber")).when(eventBus).register(any());
            PluginManager manager = new PluginManager(false, eventBus, mock(Scheduler.class), mock(ConfigManager.class),
                null, new PluginModuleFactory());
            manager.startPlugins();
            LifecyclePlugin plugin = new LifecyclePlugin();
            manager.add(plugin);

            onEdt(() -> {
                try {
                    manager.startPlugin(plugin);
                    fail("Start should fail");
                } catch (PluginInstantiationException expected) {
                    // Recorded as a failure, not as use.
                }
                return null;
            });

            assertTrue(used.isEmpty());
        } finally {
            MicrobotPluginUsage.setStartRecorder(p -> { });
        }
    }

    @Test
    public void failedStartupRetainsShutdownFailureAndStillAllowsRestart() throws Exception {
        PluginManager manager = manager();
        LifecyclePlugin plugin = new LifecyclePlugin();
        plugin.startFailure = new IllegalStateException("Startup failed");
        plugin.stopFailure = new IllegalStateException("Shutdown failed");
        manager.add(plugin);

        onEdt(() -> {
            try {
                manager.startPlugin(plugin);
                fail("Startup should fail");
            } catch (PluginInstantiationException expected) {
                assertSame(plugin.startFailure, expected.getCause());
            }
            assertFalse(manager.isActive(plugin));
            assertSame(plugin.stopFailure, manager.getShutdownFailure(plugin));
            assertEquals(1, plugin.starts);

            plugin.startFailure = null;
            plugin.stopFailure = null;
            assertTrue("A plugin whose shutdown failed must be startable again", manager.startPlugin(plugin));
            assertEquals(2, plugin.starts);
            assertNull(manager.getShutdownFailure(plugin));
            assertTrue(manager.stopPlugin(plugin));
            manager.remove(plugin);
            assertNull(manager.getShutdownFailure(plugin));
            return null;
        });
    }

    @Test
    public void startFailureIsRecordedForThePluginListAndClearedByASuccessfulStart() throws Exception {
        PluginManager manager = manager();
        LifecyclePlugin plugin = new LifecyclePlugin();
        plugin.startFailure = new IllegalStateException("Missing config");
        manager.add(plugin);

        onEdt(() -> {
            try {
                manager.startPlugin(plugin);
                fail("Startup should fail");
            } catch (PluginInstantiationException expected) {
                // Recorded below.
            }
            assertSame(plugin.startFailure, MicrobotPluginFailures.failure(plugin));

            plugin.startFailure = null;
            assertTrue(manager.startPlugin(plugin));
            assertNull(MicrobotPluginFailures.failure(plugin));
            return null;
        });
    }

    @Test
    public void shutdownLinkageErrorIsRememberedAndStillPropagates() throws Exception {
        PluginManager manager = manager();
        LifecyclePlugin plugin = new LifecyclePlugin();
        plugin.stopFailure = new NoClassDefFoundError("Missing plugin dependency");
        manager.add(plugin);

        onEdt(() -> {
            assertTrue(manager.startPlugin(plugin));
            try {
                manager.stopPlugin(plugin);
                fail("Shutdown error should propagate");
            } catch (NoClassDefFoundError expected) {
                assertSame(plugin.stopFailure, expected);
            }
            assertFalse(manager.isActive(plugin));
            assertSame(plugin.stopFailure, manager.getShutdownFailure(plugin));
            plugin.stopFailure = null;
            assertTrue(manager.stopPlugin(plugin));
            assertNull(manager.getShutdownFailure(plugin));
            assertFalse(manager.stopPlugin(plugin));
            return null;
        });
    }

    @Test
    public void shutdownFailuresAreOwnedByTheExactPluginInstance() throws Exception {
        PluginManager manager = manager();
        LifecyclePlugin first = new LifecyclePlugin();
        LifecyclePlugin second = new LifecyclePlugin();
        first.stopFailure = new IllegalStateException("First instance failed");
        manager.add(first);
        manager.add(second);

        onEdt(() -> {
            assertTrue(manager.startPlugin(first));
            try {
                manager.stopPlugin(first);
                fail("Shutdown should fail");
            } catch (PluginInstantiationException expected) {
                assertSame(first.stopFailure, expected.getCause());
            }
            assertNull(manager.getShutdownFailure(second));
            assertTrue(manager.startPlugin(second));
            assertTrue(manager.stopPlugin(second));
            assertSame(first.stopFailure, manager.getShutdownFailure(first));
            first.stopFailure = null;
            assertTrue(manager.stopPlugin(first));
            return null;
        });
    }

    private static PluginManager manager() {
        Scheduler scheduler = mock(Scheduler.class);
        return new PluginManager(false, mock(EventBus.class), scheduler, mock(ConfigManager.class),
            null, new PluginModuleFactory());
    }

    private static <T> T onEdt(Callable<T> callable) throws Exception {
        FutureTask<T> task = new FutureTask<>(callable);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    @PluginDescriptor(name = "Lifecycle test", enabledByDefault = true)
    public static class LifecyclePlugin extends Plugin {
        private RuntimeException startFailure;
        private Throwable stopFailure;
        private int starts;
        private int stops;

        @Override
        protected void startUp() {
            starts++;
            if (startFailure != null) {
                throw startFailure;
            }
        }

        @Override
        protected void shutDown() throws Exception {
            stops++;
            if (stopFailure instanceof Error) {
                throw (Error) stopFailure;
            }
            if (stopFailure instanceof Exception) {
                throw (Exception) stopFailure;
            }
        }
    }

    @PluginDescriptor(name = "Configuration failure test")
    public static class ConfigurationFailurePlugin extends Plugin {
        @Override
        public void configure(Binder binder) {
            throw new IllegalStateException("Plugin configuration failed");
        }
    }

    @PluginDescriptor(name = "Linkage failure test")
    public static class LinkageFailurePlugin extends Plugin {
        @Override
        public void configure(Binder binder) {
            throw new NoClassDefFoundError("Missing plugin dependency");
        }
    }
}
