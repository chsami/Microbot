package net.runelite.client.plugins.microbot.externalplugins;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager.OperationResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.swing.SwingUtilities;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises real artifact discovery, classloading, file replacement and rollback without a game client. */
public class LivePluginManagerTest {
    private static final String ENTRY_CLASS = "livefixture.FixturePlugin";
    private static final String PLUGIN_ID = "fixture-plugin";
    private static final String CONFIG_KEY = "fixtureplugin";

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private final List<Plugin> registered = new CopyOnWriteArrayList<>();
    private final List<Plugin> active = new CopyOnWriteArrayList<>();
    private final List<Plugin> prepared = new CopyOnWriteArrayList<>();
    private final Map<String, Boolean> enabled = new ConcurrentHashMap<>();
    private final Map<Plugin, Exception> shutdownFailures = new ConcurrentHashMap<>();
    private PluginManager plugins;
    private ConfigManager config;
    private EventBus events;
    private LivePluginManager manager;
    private Path directory;

    @Before
    public void setUp() throws Exception {
        plugins = mock(PluginManager.class);
        config = mock(ConfigManager.class);
        events = mock(EventBus.class);
        directory = temporary.newFolder("plugins").toPath();
        when(plugins.getPlugins()).thenReturn(registered);
        when(plugins.getActivePlugins()).thenReturn(active);
        when(plugins.getShutdownFailure(any(Plugin.class))).thenAnswer(call -> shutdownFailures.get(call.getArgument(0)));
        when(plugins.isPluginActive(any(Plugin.class))).thenAnswer(call -> active.contains(call.getArgument(0)));
        when(plugins.isPluginEnabled(any(Plugin.class))).thenAnswer(call -> {
            Plugin plugin = call.getArgument(0);
            return enabled.getOrDefault(configKey(plugin), plugin.getClass().getAnnotation(PluginDescriptor.class).enabledByDefault());
        });
        when(plugins.preparePlugin(any(), anyCollection())).thenAnswer(call -> {
            assertFalse("Plugin construction must not block the EDT", SwingUtilities.isEventDispatchThread());
            Class<? extends Plugin> type = call.getArgument(0);
            assertNotSame("Core workers must not inherit a plugin context loader",
                    type.getClassLoader(), Thread.currentThread().getContextClassLoader());
            Plugin plugin = type.getDeclaredConstructor().newInstance();
            prepared.add(plugin);
            return plugin;
        });
        doAnswer(call -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            registered.add(call.getArgument(0));
            return null;
        }).when(plugins).add(any(Plugin.class));
        doAnswer(call -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            Plugin plugin = call.getArgument(0);
            assertFalse("A running plugin must be stopped before removal", active.contains(plugin));
            registered.remove(plugin);
            return null;
        }).when(plugins).remove(any(Plugin.class));
        doAnswer(call -> {
            enabled.put(call.getArgument(1), call.getArgument(2));
            return null;
        }).when(config).setConfiguration(anyString(), anyString(), any(Boolean.class));
        when(plugins.startPlugin(any(Plugin.class))).thenAnswer(call -> {
            assertTrue("Plugin startup must run on EDT", SwingUtilities.isEventDispatchThread());
            Plugin plugin = call.getArgument(0);
            assertNotSame("EDT callbacks must retain the shared client context loader",
                    plugin.getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
            if (active.contains(plugin) || !plugins.isPluginEnabled(plugin)) {
                return false;
            }
            active.add(plugin);
            try {
                invokeLifecycle(plugin, "startUp");
            } catch (PluginInstantiationException failure) {
                // Match PluginManager's failed-start cleanup contract.
                active.remove(plugin);
                try {
                    invokeLifecycle(plugin, "shutDown");
                } catch (Exception shutdownFailure) {
                    shutdownFailures.put(plugin, shutdownFailure);
                }
                throw failure;
            }
            return true;
        });
        when(plugins.stopPlugin(any(Plugin.class))).thenAnswer(call -> {
            assertTrue("Plugin shutdown must run on EDT", SwingUtilities.isEventDispatchThread());
            Plugin plugin = call.getArgument(0);
            assertNotSame("EDT callbacks must retain the shared client context loader",
                    plugin.getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());
            if (!active.remove(plugin)) {
                return false;
            }
            try {
                invokeLifecycle(plugin, "shutDown");
            } catch (Exception shutdownFailure) {
                shutdownFailures.put(plugin, shutdownFailure);
                throw shutdownFailure;
            }
            return true;
        });
        manager = newManager(false);
    }

    @After
    public void tearDown() throws Exception {
        if (manager != null) {
            await(manager.shutdown());
        }
        // Failure fixtures have no real worker/resource left alive. Release their deliberately
        // retained loaders after checking the manager correctly refused an unsafe rollback.
        for (Plugin plugin : prepared) {
            ((URLClassLoader) plugin.getClass().getClassLoader()).close();
        }
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    public void addUpdateAndRemoveUseFreshCodeAndReleaseOwnedResources() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        Path v2 = fixture("v2.jar", "2.0", false);
        success(manager.install(v1));
        Plugin first = onlyPlugin();
        assertEquals("1.0", resource(first));
        assertEquals(1, counter(first, "starts"));
        assertTrue(manager.getInstalledPlugins().get(0).isRunning());
        assertSame(Plugin.class, first.getClass().getSuperclass());

        AtomicInteger closed = new AtomicInteger();
        AutoCloseable owned = PluginResources.registerCloseable(first.getClass().getClassLoader(), () -> {
            assertEquals("Shutdown must precede disposal", 1, counter(first, "stops"));
            closed.incrementAndGet();
        });
        ExecutorService executor = PluginResources.registerExecutor(first.getClass().getClassLoader(), Executors.newSingleThreadExecutor());
        executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

        Path managed = manager.getInstalledPlugins().get(0).getSourcePath();
        // On Windows this move fails if the loader still holds the source JAR open.
        Path moved = directory.resolve("source-unlocked.tmp");
        Files.move(managed, moved);
        Files.move(moved, managed);
        success(manager.install(v2, PLUGIN_ID));
        Plugin second = onlyPlugin();
        assertNotSame(first.getClass(), second.getClass());
        assertNotSame(first.getClass().getClassLoader(), second.getClass().getClassLoader());
        assertEquals("2.0", resource(second));
        assertEquals("2.0", manager.getInstalledPlugins().get(0).getVersion());
        assertEquals(1, counter(first, "stops"));
        assertEquals(1, closed.get());
        assertTrue(executor.isTerminated());
        assertNotNull(owned); // Retain the weakly registered resource until disposal has completed.
        assertArrayEquals(Files.readAllBytes(v2), Files.readAllBytes(managed));

        success(manager.remove(PLUGIN_ID));
        assertEquals(1, counter(second, "stops"));
        assertTrue(registered.isEmpty());
        assertTrue(active.isEmpty());
        assertTrue(manager.getInstalledPlugins().isEmpty());
        assertFalse(Files.exists(managed));
        assertTrue("Importing must not delete the original user-selected JAR", Files.exists(v1));
        assertTrue(Files.exists(v2));
        Files.copy(v1, managed); // The installed filename is immediately reusable.
        Files.delete(managed);
    }

    @Test
    public void changingManagedFolderUnloadsPluginsKeepsJarsAndPersistsTheSelection() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        success(manager.install(v1));
        Plugin original = onlyPlugin();
        Path oldJar = directory.resolve("FixturePlugin.jar");
        assertTrue(Files.exists(oldJar));

        Path nextDirectory = temporary.newFolder("plugins-next").toPath();
        Files.copy(fixture("v2.jar", "2.0", false), nextDirectory.resolve("FixturePlugin.jar"));
        success(manager.changePluginDirectory(nextDirectory));

        Plugin replacement = onlyPlugin();
        assertNotSame(original, replacement);
        assertEquals("2.0", resource(replacement));
        assertFalse(active.contains(original));
        assertTrue(active.contains(replacement));
        assertTrue("Switching folders must leave the old installed JAR in place", Files.exists(oldJar));
        assertEquals(nextDirectory.toAbsolutePath().normalize(), manager.getPluginDirectory());
        verify(config).setConfiguration("microbotPluginManager", "pluginDirectory",
                nextDirectory.toAbsolutePath().normalize().toString());

        await(manager.shutdown());
        directory = temporary.newFolder("fallback").toPath();
        when(config.getConfiguration("microbotPluginManager", "pluginDirectory"))
                .thenReturn(nextDirectory.toString());
        manager = newManager(false);
        manager.loadAtStartup();
        assertEquals(nextDirectory.toAbsolutePath().normalize(), manager.getPluginDirectory());
    }

    @Test
    public void manifestDisabledHubPluginIsNotLoadedAtStartupAndIsReported() throws Exception {
        Files.copy(fixture("v1.jar", "1.0", false), directory.resolve("FixturePlugin.jar"));
        MicrobotPluginManager hub = new MicrobotPluginManager(mock(okhttp3.OkHttpClient.class),
                mock(MicrobotPluginClient.class), events, plugins, config,
                mock(net.runelite.client.plugins.microbot.MicrobotApi.class), manager);
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName("FixturePlugin");
        manifest.setDisplayName("Live fixture");
        manifest.setVersion("1.0");
        manifest.setDisable(true);
        java.lang.reflect.Field manifests = MicrobotPluginManager.class.getDeclaredField("manifestMap");
        manifests.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, MicrobotPluginManifest> map = (Map<String, MicrobotPluginManifest>) manifests.get(hub);
        map.put("FixturePlugin", manifest);

        manager.loadAtStartup();

        assertTrue(registered.isEmpty());
        assertTrue(manager.getInstalledPlugins().isEmpty());
        assertTrue(Files.exists(directory.resolve("FixturePlugin.jar")));
        String reason = hub.getBlockedPlugins().get("FixturePlugin");
        assertNotNull(reason);
        assertTrue(reason, reason.startsWith("disabled upstream"));
        verify(plugins, never()).preparePlugin(any(), anyCollection());
    }

    @Test
    public void blockedHubJarDoesNotStopFolderWatchingAndLoadsOnceUnblocked() throws Exception {
        Files.copy(fixture("v1.jar", "1.0", false), directory.resolve("FixturePlugin.jar"));
        MicrobotPluginManager hub = new MicrobotPluginManager(mock(okhttp3.OkHttpClient.class),
                mock(MicrobotPluginClient.class), events, plugins, config,
                mock(net.runelite.client.plugins.microbot.MicrobotApi.class), manager);
        MicrobotPluginManifest manifest = new MicrobotPluginManifest();
        manifest.setInternalName("FixturePlugin");
        manifest.setDisplayName("Live fixture");
        manifest.setVersion("1.0");
        manifest.setDisable(true);
        java.lang.reflect.Field manifests = MicrobotPluginManager.class.getDeclaredField("manifestMap");
        manifests.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, MicrobotPluginManifest> map = (Map<String, MicrobotPluginManifest>) manifests.get(hub);
        map.put("FixturePlugin", manifest);

        OperationResult first = await(manager.rescan());
        assertTrue(first.getMessage(), first.isSuccess());
        assertTrue(first.getMessage(), first.getMessage().contains("Live fixture (FixturePlugin.jar) is blocked by the Plugin Hub"));
        manager.setWatching(true);
        Thread.sleep(3500);
        assertTrue(manager.isWatching());
        assertEquals("", manager.getWatchStatus());
        assertTrue(registered.isEmpty());
        assertTrue(Files.exists(directory.resolve("FixturePlugin.jar")));
        manager.setWatching(false);

        manifest.setDisable(false);
        OperationResult unblocked = await(manager.rescan());
        assertTrue(unblocked.getMessage(), unblocked.isSuccess());
        assertEquals(1, registered.size());
    }

    @Test
    public void hubUpdateNeverOverwritesAJarThePlayerAdded() throws Exception {
        Path local = directory.resolve("my-dev-build.jar");
        Files.copy(fixture("v1.jar", "1.0", false), local);
        success(manager.rescan());
        Plugin plugin = onlyPlugin();
        assertNull(manager.getInstalledPlugins().get(0).getHubId());
        byte[] before = Files.readAllBytes(local);
        Path v2 = fixture("v2.jar", "2.0", false);

        OperationResult result = await(manager.installHub(v2, "FixturePlugin", LivePluginManager.hash(v2)));

        assertFalse(result.getMessage(), result.isSuccess());
        assertTrue(result.getMessage(), result.getMessage().contains("Remove it in Local Plugins first"));
        assertArrayEquals(before, Files.readAllBytes(local));
        assertFalse(Files.exists(directory.resolve("my-dev-build.jar.origin")));
        assertSame(plugin, onlyPlugin());
        assertTrue(active.contains(plugin));
    }

    @Test
    public void hubUpdateThatMovesTheEntryClassAndFailsToStartRestoresThePreviousVersion() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        success(manager.installHub(v1, "FixturePlugin", LivePluginManager.hash(v1)));
        Plugin original = onlyPlugin();
        Path installedJar = manager.getInstalledPlugins().get(0).getSourcePath();
        Path moved = fixture("moved.jar", "moved-plugin", "livefixture.moved.FixturePlugin", CONFIG_KEY, "2.0", true);

        OperationResult failure = await(manager.installHub(moved, "FixturePlugin", LivePluginManager.hash(moved)));

        assertFalse(failure.isSuccess());
        assertTrue(failure.getMessage(), failure.getMessage().contains("The previous version was restored."));
        Plugin restored = onlyPlugin();
        assertNotSame(original, restored);
        assertEquals(ENTRY_CLASS, restored.getClass().getName());
        assertEquals("1.0", resource(restored));
        assertTrue(active.contains(restored));
        assertArrayEquals(Files.readAllBytes(v1), Files.readAllBytes(installedJar));
        assertEquals("FixturePlugin", manager.getInstalledPlugins().get(0).getHubId());
    }

    @Test
    public void hubUpdateThatMovesTheEntryClassReplacesThePreviousVersion() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        success(manager.installHub(v1, "FixturePlugin", LivePluginManager.hash(v1)));
        Plugin original = onlyPlugin();
        Path installedJar = manager.getInstalledPlugins().get(0).getSourcePath();
        Path moved = fixture("moved.jar", "moved-plugin", "livefixture.moved.FixturePlugin", CONFIG_KEY, "2.0", false);

        OperationResult result = await(manager.installHub(moved, "FixturePlugin", LivePluginManager.hash(moved)));

        assertTrue(result.getMessage(), result.isSuccess());
        Plugin replacement = onlyPlugin();
        assertEquals("livefixture.moved.FixturePlugin", replacement.getClass().getName());
        assertEquals(1, counter(original, "stops"));
        assertEquals(1, manager.getInstalledPlugins().size());
        assertEquals(installedJar, manager.getInstalledPlugins().get(0).getSourcePath());
        assertArrayEquals(Files.readAllBytes(moved), Files.readAllBytes(installedJar));
    }

    @Test
    public void hubRemoveNeverDeletesALocalJarThatReplacedAHubPlugin() throws Exception {
        Path local = directory.resolve("FixturePlugin.jar");
        Files.copy(fixture("v1.jar", "1.0", false), local);
        when(config.getConfiguration("microbotPluginVersions", "plugin.FixturePlugin"))
                .thenReturn("0.9:0000000000000000000000000000000000000000000000000000000000000000");
        success(manager.rescan());
        Plugin plugin = onlyPlugin();
        assertNull(manager.getInstalledPlugins().get(0).getHubId());

        OperationResult result = await(manager.removeHub("FixturePlugin"));

        assertFalse(result.getMessage(), result.isSuccess());
        assertTrue(Files.exists(local));
        assertSame(plugin, onlyPlugin());
        assertTrue(active.contains(plugin));
    }

    @Test
    public void failedReplacementRestoresRunningPreviousVersionAndInstalledBytes() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        Path bad = fixture("bad.jar", "2.0", true);
        success(manager.install(v1));
        Plugin first = onlyPlugin();
        Path managed = manager.getInstalledPlugins().get(0).getSourcePath();

        OperationResult failure = await(manager.install(bad, PLUGIN_ID));
        assertFalse(failure.getMessage(), failure.isSuccess());
        assertTrue(failure.getMessage(), failure.getMessage().contains("previous version restored"));
        Plugin restored = onlyPlugin();
        assertNotSame("Rollback must reconstruct disposed objects", first, restored);
        assertNotSame(first.getClass().getClassLoader(), restored.getClass().getClassLoader());
        assertEquals("1.0", resource(restored));
        assertTrue(active.contains(restored));
        assertEquals(1, counter(first, "stops"));
        assertEquals(1, counter(prepared.get(1), "stops"));
        assertArrayEquals(Files.readAllBytes(v1), Files.readAllBytes(managed));
        assertEquals("1.0", manager.getInstalledPlugins().get(0).getVersion());
    }

    @Test
    public void disabledPluginStaysDisabledAcrossReplacement() throws Exception {
        enabled.put(CONFIG_KEY, false);
        success(manager.install(fixture("v1.jar", "1.0", false)));
        Plugin first = onlyPlugin();
        assertEquals(0, counter(first, "starts"));
        success(manager.install(fixture("v2.jar", "2.0", false), PLUGIN_ID));
        assertEquals("2.0", resource(onlyPlugin()));
        assertEquals(0, counter(onlyPlugin(), "starts"));
        assertTrue(active.isEmpty());
        assertEquals(Boolean.FALSE, enabled.get(CONFIG_KEY));
        assertFalse(manager.getInstalledPlugins().get(0).isRunning());
    }

    @Test
    public void checksumMismatchNeverStopsOrOverwritesInstalledPlugin() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        success(manager.installHub(v1, "FixturePlugin", LivePluginManager.hash(v1)));
        Plugin original = onlyPlugin();
        Path managed = manager.getInstalledPlugins().get(0).getSourcePath();
        OperationResult failure = await(manager.installHub(fixture("v2.jar", "2.0", false), "FixturePlugin", LivePluginManager.hash(v1)));
        assertFalse(failure.isSuccess());
        assertTrue(failure.getMessage(), failure.getMessage().contains("checksum"));
        assertSame(original, onlyPlugin());
        assertTrue(active.contains(original));
        assertEquals(0, counter(original, "stops"));
        assertArrayEquals(Files.readAllBytes(v1), Files.readAllBytes(managed));
    }

    @Test
    public void duplicatePluginClassOrConfigIdentityIsRejected() throws Exception {
        success(manager.install(fixture("v1.jar", "1.0", false)));
        Plugin original = onlyPlugin();
        OperationResult duplicateClass = await(manager.install(fixture("duplicate.jar", "another-id", ENTRY_CLASS, CONFIG_KEY, "2.0", false)));
        assertFalse(duplicateClass.isSuccess());
        OperationResult duplicateConfig = await(manager.install(fixture("conflict.jar", "other-id", "livefixture.OtherPlugin", CONFIG_KEY, "2.0", false)));
        assertFalse(duplicateConfig.isSuccess());
        assertSame(original, onlyPlugin());
        assertEquals(1, prepared.size());
        assertEquals(0, counter(original, "stops"));
    }

    @Test
    public void externalPluginCanShareSimpleNameWithBuiltInOverlay() throws Exception {
        Plugin overlay = new BuiltInOverlayPlugin();
        registered.add(overlay);
        active.add(overlay);
        enabled.put(configKey(overlay), true);
        String entry = "livefixture.BuiltInOverlayPlugin";
        success(manager.install(fixture("overlay-v1.jar", "overlay-plugin", entry, "", "1.0", false)));
        Plugin script = prepared.get(0);
        assertNotEquals(configKey(overlay), configKey(script));
        assertTrue(active.contains(overlay));
        assertTrue(active.contains(script));

        enabled.put(configKey(script), false);
        SwingUtilities.invokeAndWait(() -> {
            try {
                plugins.stopPlugin(script);
            } catch (PluginInstantiationException failure) {
                throw new AssertionError(failure);
            }
        });
        success(manager.install(fixture("overlay-v2.jar", "overlay-plugin", entry, "", "2.0", false), "overlay-plugin"));
        Plugin replacement = prepared.get(1);
        assertEquals("2.0", resource(replacement));
        assertFalse(active.contains(replacement));
        assertTrue(active.contains(overlay));
        assertEquals(Boolean.TRUE, enabled.get(configKey(overlay)));
        assertEquals(Boolean.FALSE, enabled.get(configKey(replacement)));

        success(manager.remove("overlay-plugin"));
        assertEquals(1, registered.size());
        assertSame(overlay, registered.get(0));
        assertTrue(active.contains(overlay));
    }

    @PluginDescriptor(name = "Built-in overlay")
    private static class BuiltInOverlayPlugin extends Plugin {
    }

    @Test
    public void updateSelectedRejectsUnrelatedArtifactBeforeConstruction() throws Exception {
        success(manager.install(fixture("v1.jar", "1.0", false)));
        Plugin original = onlyPlugin();
        Path other = fixture("other.jar", "other-id", "livefixture.OtherPlugin", "other", "1.0", false);
        OperationResult failure = await(manager.install(other, PLUGIN_ID));
        assertFalse(failure.isSuccess());
        assertTrue(failure.getMessage(), failure.getMessage().contains("different plugin"));
        assertSame(original, onlyPlugin());
        assertEquals(1, prepared.size());
    }

    @Test
    public void safeModeBlocksInstallStartupScanAndFolderWatching() throws Exception {
        success(manager.shutdown());
        manager = newManager(true);
        Path source = fixture("v1.jar", "1.0", false);
        Files.copy(source, directory.resolve("FixturePlugin.jar"));
        assertFalse(await(manager.install(source)).isSuccess());
        assertFalse(await(manager.rescan()).isSuccess());
        manager.loadAtStartup();
        manager.setWatching(true);
        assertFalse(manager.isWatching());
        assertTrue(registered.isEmpty());
        assertTrue(manager.getInstalledPlugins().isEmpty());
        verify(plugins, never()).preparePlugin(any(), anyCollection());
    }

    @Test
    public void installedArtifactsReloadAtStartupWithoutStartingDuringSplash() throws Exception {
        success(manager.install(fixture("v1.jar", "1.0", false)));
        Plugin original = onlyPlugin();
        success(manager.shutdown());
        assertTrue(registered.isEmpty());
        manager = newManager(false);
        clearInvocations(plugins);
        manager.loadAtStartup();
        Plugin restored = onlyPlugin();
        assertNotSame(original.getClass().getClassLoader(), restored.getClass().getClassLoader());
        assertEquals("1.0", resource(restored));
        assertEquals(0, counter(restored, "starts"));
        assertTrue(active.isEmpty());
        assertEquals(1, manager.getInstalledPlugins().size());
        verify(plugins, never()).isPluginEnabled(any(Plugin.class));
    }

    @Test
    public void rescanReconcilesAddedChangedAndDeletedSourceJar() throws Exception {
        Path source = directory.resolve("FixturePlugin.jar");
        Files.copy(fixture("v1.jar", "1.0", false), source);
        success(manager.rescan());
        Plugin first = onlyPlugin();
        Files.copy(fixture("v2.jar", "2.0", false), source, StandardCopyOption.REPLACE_EXISTING);
        success(manager.rescan());
        assertNotSame(first, onlyPlugin());
        assertEquals("2.0", resource(onlyPlugin()));
        Files.delete(source);
        success(manager.rescan());
        assertTrue(registered.isEmpty());
        assertTrue(active.isEmpty());
        assertTrue(manager.getInstalledPlugins().isEmpty());
    }

    @Test
    public void explicitReloadCreatesNewInstanceEvenWhenBytesAreUnchanged() throws Exception {
        success(manager.install(fixture("v1.jar", "1.0", false)));
        Plugin first = onlyPlugin();
        success(manager.reload(PLUGIN_ID));
        assertNotSame(first.getClass().getClassLoader(), onlyPlugin().getClass().getClassLoader());
        assertEquals("1.0", resource(onlyPlugin()));
        assertEquals(1, counter(first, "stops"));
        assertTrue(active.contains(onlyPlugin()));
    }

    @Test
    public void failedInitialStartupDoesNotLeaveInstalledFileOrRegisteredInstance() throws Exception {
        OperationResult failure = await(manager.install(fixture("bad.jar", "1.0", true)));
        assertFalse(failure.isSuccess());
        assertTrue(registered.isEmpty());
        assertTrue(active.isEmpty());
        assertTrue(manager.getInstalledPlugins().isEmpty());
        assertFalse(Files.exists(directory.resolve("FixturePlugin.jar")));
    }

    @Test
    public void invalidInPlaceOverwriteRestoresInstalledBytesWithoutStoppingCurrentPlugin() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        success(manager.install(v1));
        Plugin original = onlyPlugin();
        Path source = manager.getInstalledPlugins().get(0).getSourcePath();
        Files.write(source, "not a valid plugin ZIP".getBytes(StandardCharsets.UTF_8));

        OperationResult failure = await(manager.rescan());
        assertFalse(failure.isSuccess());
        assertSame(original, onlyPlugin());
        assertTrue(active.contains(original));
        assertEquals(0, counter(original, "stops"));
        assertArrayEquals("Invalid replacement bytes must not poison the next launch", Files.readAllBytes(v1), Files.readAllBytes(source));
    }

    @Test
    public void secondFilenameWithSameArtifactIdDoesNotReplaceTheExistingInstallation() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        success(manager.install(v1));
        Plugin original = onlyPlugin();
        Path source = manager.getInstalledPlugins().get(0).getSourcePath();
        Files.copy(fixture("v2.jar", "2.0", false), directory.resolve("ZDuplicate.jar"));

        OperationResult failure = await(manager.rescan());
        assertFalse(failure.isSuccess());
        assertSame(original, onlyPlugin());
        assertEquals("1.0", resource(original));
        assertArrayEquals(Files.readAllBytes(v1), Files.readAllBytes(source));
        assertEquals(1, prepared.size());
    }

    @Test
    public void failedReplacementStartupAndShutdownNeverRestartThePreviousGeneration() throws Exception {
        Path v1 = fixture("v1.jar", "1.0", false);
        success(manager.install(v1));
        Plugin original = onlyPlugin();
        Path source = manager.getInstalledPlugins().get(0).getSourcePath();
        Path bad = fixture("bad.jar", PLUGIN_ID, ENTRY_CLASS, CONFIG_KEY, "2.0", true, true);

        OperationResult failure = await(manager.install(bad, PLUGIN_ID));
        assertFalse(failure.isSuccess());
        assertTrue(failure.getMessage(), failure.getMessage().toLowerCase(Locale.ROOT).contains("cleanup"));
        assertEquals("Unsafe cleanup must not prepare a rollback generation", 2, prepared.size());
        assertEquals(1, counter(original, "stops"));
        assertEquals(1, counter(prepared.get(1), "starts"));
        assertEquals(1, counter(prepared.get(1), "stops"));
        assertTrue(active.isEmpty());
        assertArrayEquals("Retain known-good bytes for the next launch", Files.readAllBytes(v1), Files.readAllBytes(source));
    }

    @Test
    public void folderWatcherWaitsForStableBytesThenAddsUpdatesAndRemoves() throws Exception {
        Path source = directory.resolve("FixturePlugin.jar");
        Files.copy(fixture("v1.jar", "1.0", false), source);
        CountDownLatch added = new CountDownLatch(1);
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        doAnswer(call -> {
            List<LivePluginManager.PluginEntry> entries = manager.getInstalledPlugins();
            if (!entries.isEmpty() && entries.get(0).getVersion().equals("1.0")) {
                added.countDown();
            } else if (!entries.isEmpty() && entries.get(0).getVersion().equals("2.0")) {
                updated.countDown();
            } else if (entries.isEmpty() && updated.getCount() == 0) {
                removed.countDown();
            }
            return null;
        }).when(events).post(any(ExternalPluginsChanged.class));

        long started = System.nanoTime();
        manager.setWatching(true);
        assertTrue("Watcher did not import the stable JAR", added.await(15, TimeUnit.SECONDS));
        // The watcher samples every second and requires two equal observations; a single
        // observation or an eager immediate load would complete before this lower bound.
        assertTrue("A JAR must settle across two observations", System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(1500));
        Plugin original = onlyPlugin();
        assertEquals("1.0", resource(original));

        Files.copy(fixture("v2.jar", "2.0", false), source, StandardCopyOption.REPLACE_EXISTING);
        assertTrue("Watcher did not update the changed JAR", updated.await(15, TimeUnit.SECONDS));
        assertNotSame(original, onlyPlugin());
        assertEquals("2.0", resource(onlyPlugin()));
        assertEquals(1, counter(original, "stops"));

        Files.delete(source);
        assertTrue("Watcher did not remove the deleted JAR", removed.await(15, TimeUnit.SECONDS));
        assertTrue(registered.isEmpty());
        assertTrue(active.isEmpty());
        assertTrue(manager.isWatching());
        assertEquals("", manager.getWatchStatus());
    }

    @Test
    public void watcherFailurePublishesAnErrorEvenWhenNoPluginIsInstalled() throws Exception {
        Files.write(directory.resolve("InvalidPlugin.jar"), new byte[]{1, 2, 3});
        CountDownLatch failed = new CountDownLatch(1);
        doAnswer(call -> {
            if (!manager.isWatching() && !manager.getWatchStatus().isEmpty()) {
                failed.countDown();
            }
            return null;
        }).when(events).post(any(ExternalPluginsChanged.class));

        manager.setWatching(true);
        assertTrue("Watcher failure was not published to the UI", failed.await(15, TimeUnit.SECONDS));
        assertFalse(manager.isWatching());
        assertTrue(manager.getInstalledPlugins().isEmpty());
        assertFalse(manager.getWatchStatus().isEmpty());
        assertTrue(registered.isEmpty());
        manager.setWatching(false);
        assertEquals("", manager.getWatchStatus());
    }

    @Test
    public void repeatedUpdatesRetainOnlyTheCurrentRegistryGenerationAndCacheJar() throws Exception {
        for (int version = 1; version <= 5; version++) {
            Plugin previous = registered.isEmpty() ? null : onlyPlugin();
            success(manager.install(fixture("v" + version + ".jar", version + ".0", false)));
            Plugin current = onlyPlugin();
            assertEquals(version + ".0", resource(current));
            assertEquals(1, active.size());
            assertSame(current, active.get(0));
            assertEquals(1, manager.getInstalledPlugins().size());
            if (previous != null) {
                assertNotSame(previous.getClass().getClassLoader(), current.getClass().getClassLoader());
                assertEquals(1, counter(previous, "stops"));
                assertFalse(registered.contains(previous));
            }
            assertEquals("Retired loader JARs must be closed and released", 1, runtimeJarCount());
        }
        success(manager.remove(PLUGIN_ID));
        assertEquals(0, runtimeJarCount());
        assertTrue(registered.isEmpty());
        assertTrue(active.isEmpty());
    }

    private long runtimeJarCount() throws Exception {
        try (java.util.stream.Stream<Path> files = Files.list(directory.resolve(".runtime"))) {
            return files.filter(path -> path.toString().endsWith(".jar")).count();
        }
    }

    private LivePluginManager newManager(boolean safeMode) {
        return new LivePluginManager(plugins, config, events, safeMode, directory);
    }

    private static OperationResult await(CompletableFuture<OperationResult> operation) throws Exception {
        return operation.get(30, TimeUnit.SECONDS);
    }

    private static void success(CompletableFuture<OperationResult> operation) throws Exception {
        OperationResult result = await(operation);
        assertTrue(result.getMessage(), result.isSuccess());
    }

    private Plugin onlyPlugin() {
        assertEquals(1, registered.size());
        return registered.get(0);
    }

    private static String configKey(Plugin plugin) {
        return PluginManager.getPluginConfigurationKey(plugin.getClass());
    }

    private static void invokeLifecycle(Plugin plugin, String method) throws Exception {
        try {
            plugin.getClass().getMethod(method).invoke(plugin);
        } catch (InvocationTargetException failure) {
            throw new PluginInstantiationException(failure.getCause());
        }
    }

    private static int counter(Plugin plugin, String field) throws Exception {
        return plugin.getClass().getField(field).getInt(plugin);
    }

    private static String resource(Plugin plugin) throws Exception {
        try (InputStream input = plugin.getClass().getClassLoader().getResourceAsStream("livefixture/version.txt")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Path fixture(String filename, String version, boolean failStart) throws Exception {
        return fixture(filename, PLUGIN_ID, ENTRY_CLASS, CONFIG_KEY, version, failStart);
    }

    /** ASM keeps fixture entry classes absent from the parent loader, as real imported plugins are. */
    private Path fixture(String filename, String id, String className, String configName, String version, boolean failStart) throws Exception {
        return fixture(filename, id, className, configName, version, failStart, false);
    }

    private Path fixture(String filename, String id, String className, String configName, String version, boolean failStart, boolean failStop) throws Exception {
        String type = className.replace('.', '/');
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V11, Opcodes.ACC_PUBLIC, type, null, "net/runelite/client/plugins/Plugin", null);
        AnnotationVisitor descriptor = writer.visitAnnotation("Lnet/runelite/client/plugins/PluginDescriptor;", true);
        descriptor.visit("name", "Live fixture");
        descriptor.visit("configName", configName);
        descriptor.visit("version", version);
        descriptor.visit("isExternal", true);
        descriptor.visit("minClientVersion", "1.0.0");
        descriptor.visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "starts", "I", null, null).visitEnd();
        writer.visitField(Opcodes.ACC_PUBLIC, "stops", "I", null, null).visitEnd();
        MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "net/runelite/client/plugins/Plugin", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        lifecycle(writer, type, "startUp", "starts", failStart);
        lifecycle(writer, type, "shutDown", "stops", failStop);
        writer.visitEnd();

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Microbot-Plugin-Id", id);
        manifest.getMainAttributes().putValue("Microbot-Plugin-Class", className);
        Path jar = temporary.getRoot().toPath().resolve(filename);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry(type + ".class"));
            output.write(writer.toByteArray());
            output.closeEntry();
            output.putNextEntry(new JarEntry("livefixture/version.txt"));
            output.write(version.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private static void lifecycle(ClassWriter writer, String type, String methodName, String counter, boolean fail) {
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, methodName, "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitInsn(Opcodes.DUP);
        method.visitFieldInsn(Opcodes.GETFIELD, type, counter, "I");
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IADD);
        method.visitFieldInsn(Opcodes.PUTFIELD, type, counter, "I");
        if (fail) {
            method.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
            method.visitInsn(Opcodes.DUP);
            method.visitLdcInsn("fixture startup failed");
            method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false);
            method.visitInsn(Opcodes.ATHROW);
        } else {
            method.visitInsn(Opcodes.RETURN);
        }
        method.visitMaxs(0, 0);
        method.visitEnd();
    }
}
