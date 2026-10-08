package net.runelite.client.plugins.microbot.externalplugins;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MicrobotPluginManagerHealthTest {
    @PluginDescriptor(name = "Health Fixture", isExternal = true, version = "1.0.0", minClientVersion = "0.0.1")
    public static class HealthFixturePlugin extends Plugin {
    }

    @PluginDescriptor(name = "Jar Disabled Fixture", isExternal = true, version = "1.0.0", minClientVersion = "0.0.1", disable = true)
    public static class JarDisabledFixturePlugin extends Plugin {
    }

    @PluginDescriptor(name = "Future Fixture", isExternal = true, version = "1.0.0", minClientVersion = "999.0.0")
    public static class FutureClientFixturePlugin extends Plugin {
    }

    @PluginDescriptor(name = "Injector Failure Fixture", isExternal = true, version = "1.0.0", minClientVersion = "0.0.1")
    public static class InjectorFailureFixturePlugin extends Plugin {
    }

    private MicrobotPluginManager manager;
    private PluginManager pluginManager;
    private ConfigManager configManager;
    private EventBus eventBus;

    @Before
    public void setUp() throws Exception {
        pluginManager = mock(PluginManager.class);
        configManager = mock(ConfigManager.class);
        eventBus = mock(EventBus.class);
        when(pluginManager.getPlugins()).thenReturn(Collections.emptyList());

        Constructor<MicrobotPluginManager> constructor = MicrobotPluginManager.class.getDeclaredConstructor(
                OkHttpClient.class, MicrobotPluginClient.class, EventBus.class, ScheduledExecutorService.class,
                PluginManager.class, Gson.class, ConfigManager.class, MicrobotApi.class);
        constructor.setAccessible(true);
        manager = constructor.newInstance(new OkHttpClient(), mock(MicrobotPluginClient.class), eventBus,
                mock(ScheduledExecutorService.class), pluginManager, new Gson(), configManager, mock(MicrobotApi.class));
    }

    private MicrobotPluginManifest addManifest(String internalName, String version, boolean disable, String healthJson) throws Exception {
        String json = "{\"internalName\":\"" + internalName + "\",\"name\":\"" + internalName + "\",\"version\":\"" + version + "\","
                + "\"minClientVersion\":\"0.0.1\",\"disable\":" + disable
                + (healthJson == null ? "" : ",\"health\":" + healthJson) + "}";
        MicrobotPluginManifest manifest = new Gson().fromJson(json, MicrobotPluginManifest.class);
        manifestMap().put(internalName, manifest);
        return manifest;
    }

    @SuppressWarnings("unchecked")
    private Map<String, MicrobotPluginManifest> manifestMap() throws Exception {
        Field field = MicrobotPluginManager.class.getDeclaredField("manifestMap");
        field.setAccessible(true);
        return (Map<String, MicrobotPluginManifest>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private List<Plugin> loadPlugins(Class<?>... classes) throws Exception {
        Method method = MicrobotPluginManager.class.getDeclaredMethod("loadPlugins", List.class, BiConsumer.class);
        method.setAccessible(true);
        return (List<Plugin>) method.invoke(manager, Arrays.asList(classes), null);
    }

    private static PluginDescriptor descriptor(Class<?> clazz) {
        return clazz.getAnnotation(PluginDescriptor.class);
    }

    @Test
    public void confirmedBrokenVersionIsSkippedAtLoadAndRecorded() throws Exception {
        addManifest("HealthFixturePlugin", "1.0.0", false,
                "{\"status\":\"broken\",\"reason\":\"Stops after banking\",\"affectedVersions\":[\"1.0.0\"]}");

        List<Plugin> loaded = loadPlugins(HealthFixturePlugin.class);

        assertTrue(loaded.isEmpty());
        assertEquals("version 1.0.0 is confirmed broken upstream: Stops after banking",
                manager.getBlockedPlugins().get("HealthFixturePlugin"));
    }

    @Test
    public void unaffectedOrUnreportedVersionIsNotBlocked() throws Exception {
        addManifest("HealthFixturePlugin", "1.1.0", false, "{\"status\":\"broken\",\"reason\":\"x\",\"affectedVersions\":[\"1.1.0\"]}");
        assertNull(manager.getLoadBlockReason("HealthFixturePlugin", descriptor(HealthFixturePlugin.class)));

        addManifest("HealthFixturePlugin", "1.0.0", false, "{\"status\":\"unverified\",\"reason\":\"x\"}");
        assertNull(manager.getLoadBlockReason("HealthFixturePlugin", descriptor(HealthFixturePlugin.class)));

        manifestMap().clear();
        assertNull(manager.getLoadBlockReason("HealthFixturePlugin", descriptor(HealthFixturePlugin.class)));
    }

    @Test
    public void hubDisableAloneBlocksInstalledJar() throws Exception {
        addManifest("HealthFixturePlugin", "2.0.0", true, null);
        assertEquals("disabled upstream", manager.getLoadBlockReason("HealthFixturePlugin", descriptor(HealthFixturePlugin.class)));

        addManifest("HealthFixturePlugin", "2.0.0", true, "{\"status\":\"broken\",\"reason\":\"Retired\",\"affectedVersions\":[\"*\"]}");
        assertTrue(loadPlugins(HealthFixturePlugin.class).isEmpty());
        assertEquals("disabled upstream: Retired", manager.getBlockedPlugins().get("HealthFixturePlugin"));
    }

    @Test
    public void jarDisabledAndNewerClientSkipsAreRecorded() throws Exception {
        assertTrue(loadPlugins(JarDisabledFixturePlugin.class, FutureClientFixturePlugin.class).isEmpty());

        Map<String, String> blocked = manager.getBlockedPlugins();
        assertEquals("disabled upstream", blocked.get("JarDisabledFixturePlugin"));
        assertTrue(blocked.get("FutureClientFixturePlugin").startsWith("requires client 999.0.0 or newer"));
    }

    @Test
    public void blockedPluginsAreNotifiedOncePerReason() throws Exception {
        addManifest("HealthFixturePlugin", "1.0.0", false, "{\"status\":\"broken\",\"reason\":\"a\",\"affectedVersions\":[\"1.0.0\"]}");
        loadPlugins(HealthFixturePlugin.class, JarDisabledFixturePlugin.class);

        assertEquals(2, manager.takeBlockedPluginsToNotify().size());
        assertTrue(manager.takeBlockedPluginsToNotify().isEmpty());

        addManifest("HealthFixturePlugin", "1.0.0", false, "{\"status\":\"broken\",\"reason\":\"b\",\"affectedVersions\":[\"1.0.0\"]}");
        loadPlugins(HealthFixturePlugin.class);
        assertEquals(Collections.singleton("HealthFixturePlugin"), manager.takeBlockedPluginsToNotify().keySet());
    }

    @Test
    public void updatePromptSuppressedWhenLatestVersionIsBlocked() throws Exception {
        HealthFixturePlugin plugin = new HealthFixturePlugin();

        addManifest("HealthFixturePlugin", "1.1.0", false, null);
        assertTrue(manager.getOutdatedPluginUpdate(plugin).isPresent());

        addManifest("HealthFixturePlugin", "1.1.0", false, "{\"status\":\"broken\",\"reason\":\"x\",\"affectedVersions\":[\"1.1.0\"]}");
        assertFalse(manager.getOutdatedPluginUpdate(plugin).isPresent());

        addManifest("HealthFixturePlugin", "1.1.0", true, null);
        assertFalse(manager.getOutdatedPluginUpdate(plugin).isPresent());
    }

    @Test
    public void disabledPluginCanBeRemoved() throws Exception {
        MicrobotPluginManifest manifest = addManifest("HealthFixturePlugin", "2.0.0", true, null);
        loadPlugins(HealthFixturePlugin.class);
        assertTrue(manager.getBlockedPlugins().containsKey("HealthFixturePlugin"));

        manager.remove(manifest);

        verify(configManager).unsetConfiguration(eq("microbotPluginVersions"), eq("plugin.HealthFixturePlugin"));
        verify(eventBus).post(any(ExternalPluginsChanged.class));
        assertFalse(manager.getBlockedPlugins().containsKey("HealthFixturePlugin"));
    }

    @Test
    public void removeLeavesSameNamedPluginFromAnotherClassLoaderRunning() throws Exception {
        HealthFixturePlugin corePlugin = new HealthFixturePlugin();
        when(pluginManager.getPlugins()).thenReturn(Collections.singletonList(corePlugin));
        MicrobotPluginManifest manifest = addManifest("HealthFixturePlugin", "2.0.0", true, null);

        manager.remove(manifest);

        verify(pluginManager, never()).stopPlugin(corePlugin);
        verify(pluginManager, never()).remove(corePlugin);
        verify(configManager).unsetConfiguration(eq("microbotPluginVersions"), eq("plugin.HealthFixturePlugin"));
    }

    @Test
    public void pluginWithoutInjectorIsNotRegistered() throws Exception {
        assertTrue(loadPlugins(InjectorFailureFixturePlugin.class).isEmpty());

        verify(pluginManager, never()).addPlugin(any(Plugin.class));
    }
}
