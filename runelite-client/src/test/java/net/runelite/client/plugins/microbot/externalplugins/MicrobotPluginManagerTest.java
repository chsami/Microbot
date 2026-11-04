package net.runelite.client.plugins.microbot.externalplugins;

import com.google.common.hash.Hashing;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager.OperationResult;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Keeps Hub download failures separate from the live lifecycle and persisted installation state. */
public class MicrobotPluginManagerTest {
    private static final byte[] ARTIFACT = "downloaded plugin bytes".getBytes(StandardCharsets.UTF_8);
    private static final String HASH = Hashing.sha256().hashBytes(ARTIFACT).toString();
    private MicrobotPluginClient client;
    private ConfigManager config;
    private MicrobotApi telemetry;
    private LivePluginManager lifecycle;
    private MicrobotPluginManifest manifest;
    private MicrobotPluginManager manager;
    private int responseCode = 200;

    @Before
    public void setUp() {
        client = mock(MicrobotPluginClient.class);
        config = mock(ConfigManager.class);
        telemetry = mock(MicrobotApi.class);
        lifecycle = mock(LivePluginManager.class);
        manifest = new MicrobotPluginManifest();
        manifest.setInternalName("FixturePlugin");
        manifest.setDisplayName("Fixture");
        manifest.setVersion("1.2.3");
        manifest.setSha256(HASH);
        when(client.getJarURL(manifest, "1.2.3"))
                .thenReturn(HttpUrl.parse("https://plugins.example.invalid/fixture.jar"));
        when(lifecycle.shutdown()).thenReturn(CompletableFuture.completedFuture(OperationResult.success("Stopped")));
        OkHttpClient http = new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(responseCode)
                .message("Fixture response")
                .body(ResponseBody.create(MediaType.parse("application/java-archive"), ARTIFACT))
                .build()).build();
        manager = new MicrobotPluginManager(http, client, mock(EventBus.class),
                mock(PluginManager.class), config, telemetry, lifecycle);
    }

    @After
    public void tearDown() {
        manager.onClientShutdown(new ClientShutdown());
    }

    @Test
    public void failedDownloadDoesNotTouchInstalledPlugin() throws Exception {
        responseCode = 503;
        OperationResult result = manager.installPlugin(manifest, null).get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        verify(lifecycle, never()).installHub(any(Path.class), anyString(), any());
        verifyNoInteractions(config, telemetry);
    }

    @Test
    public void checksumMismatchDoesNotEnterLifecycle() throws Exception {
        manifest.setSha256("0000000000000000000000000000000000000000000000000000000000000000");
        OperationResult result = manager.installPlugin(manifest, null).get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("checksum"));
        verify(lifecycle, never()).installHub(any(Path.class), anyString(), any());
        verifyNoInteractions(config, telemetry);
    }

    @Test
    public void insecureDownloadIsRejectedBeforeLifecycle() throws Exception {
        when(client.getJarURL(manifest, "1.2.3"))
                .thenReturn(HttpUrl.parse("http://plugins.example.invalid/fixture.jar"));

        OperationResult result = manager.installPlugin(manifest, null).get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("HTTPS"));
        verify(lifecycle, never()).installHub(any(Path.class), anyString(), any());
        verifyNoInteractions(config, telemetry);
    }

    @Test
    public void failedLifecycleDoesNotCommitMetadataOrTelemetry() throws Exception {
        when(lifecycle.installHub(any(Path.class), eq("FixturePlugin"), eq(HASH)))
                .thenReturn(CompletableFuture.completedFuture(OperationResult.failure("Previous plugin retained")));

        OperationResult result = manager.installPlugin(manifest, null).get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        verifyNoInteractions(config, telemetry);
    }

    @Test
    public void metadataCommitsOnlyAfterLifecycleCompletes() throws Exception {
        CountDownLatch called = new CountDownLatch(1);
        CompletableFuture<OperationResult> installed = new CompletableFuture<>();
        AtomicReference<Path> download = new AtomicReference<>();
        when(lifecycle.installHub(any(Path.class), eq("FixturePlugin"), eq(HASH))).thenAnswer(call -> {
            download.set(call.getArgument(0));
            called.countDown();
            return installed;
        });

        CompletableFuture<OperationResult> result = manager.installPlugin(manifest, null);
        try {
            assertTrue(called.await(5, TimeUnit.SECONDS));
            assertTrue(Files.exists(download.get()));
            assertFalse(result.isDone());
            verifyNoInteractions(config, telemetry);
        } finally {
            installed.complete(OperationResult.success("Installed"));
        }

        assertTrue(result.get(5, TimeUnit.SECONDS).isSuccess());
        verify(config).setConfiguration("microbotPluginVersions", "plugin.FixturePlugin", "1.2.3:" + HASH);
        verify(telemetry).increasePluginInstall("FixturePlugin", "Fixture", "1.2.3");
        assertFalse(Files.exists(download.get()));
    }

    @Test
    public void telemetryFailureDoesNotUndoSuccessfulInstallation() throws Exception {
        when(lifecycle.installHub(any(Path.class), eq("FixturePlugin"), eq(HASH)))
                .thenReturn(CompletableFuture.completedFuture(OperationResult.success("Installed")));
        doThrow(new IllegalStateException("Telemetry unavailable")).when(telemetry)
                .increasePluginInstall(anyString(), anyString(), anyString());

        assertTrue(manager.installPlugin(manifest, null).get(5, TimeUnit.SECONDS).isSuccess());
        verify(config).setConfiguration("microbotPluginVersions", "plugin.FixturePlugin", "1.2.3:" + HASH);
    }

    @Test
    public void submissionDuringExecutorShutdownReturnsFailureFuture() throws Exception {
        // Force the executor-rejection side of the race before the shutdown flag is observed.
        Field field = MicrobotPluginManager.class.getDeclaredField("downloads");
        field.setAccessible(true);
        ((ExecutorService) field.get(manager)).shutdown();

        OperationResult result = manager.installPlugin(manifest, null).get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("shutting down"));
        verify(lifecycle, never()).installHub(any(Path.class), anyString(), any());
        verifyNoInteractions(config, telemetry);
    }

    @Test
    public void onlyHubInstallsCountAsHubInstalled() throws Exception {
        Constructor<LivePluginManager.PluginEntry> constructor = LivePluginManager.PluginEntry.class.getDeclaredConstructor(
                String.class, String.class, String.class, Path.class, boolean.class, String.class, List.class, String.class);
        constructor.setAccessible(true);
        LivePluginManager.PluginEntry sideloaded = constructor.newInstance("private.SecretPlugin", "Secret", "1.0",
                Paths.get("SecretPlugin.jar"), true, "Ready", Collections.singletonList("private.SecretPlugin"), null);
        LivePluginManager.PluginEntry hub = constructor.newInstance("hub.AutoMinerPlugin", "Auto Miner", "2.0",
                Paths.get("AutoMinerPlugin.jar"), true, "Ready", Collections.singletonList("hub.AutoMinerPlugin"), "AutoMinerPlugin");
        when(lifecycle.getInstalledPlugins()).thenReturn(java.util.Arrays.asList(sideloaded, hub));
        when(config.getConfiguration("microbotPluginVersions", "plugin.FixturePlugin")).thenReturn("1.2.3:" + HASH);

        assertFalse(manager.isHubInstalled("SecretPlugin"));
        assertTrue(manager.isHubInstalled("AutoMinerPlugin"));
        assertTrue(manager.isHubInstalled("FixturePlugin"));
        assertTrue(manager.isLoadedFromHub("AutoMinerPlugin"));
        assertFalse(manager.isLoadedFromHub("SecretPlugin"));
    }

    @Test
    public void localJarReplacingAHubPluginIsNotHubInstalledDespiteAStaleVersionRecord() throws Exception {
        Constructor<LivePluginManager.PluginEntry> constructor = LivePluginManager.PluginEntry.class.getDeclaredConstructor(
                String.class, String.class, String.class, Path.class, boolean.class, String.class, List.class, String.class);
        constructor.setAccessible(true);
        LivePluginManager.PluginEntry local = constructor.newInstance("local.FixturePlugin", "Fixture", "9.9",
                Paths.get("FixturePlugin.jar"), true, "Ready", Collections.singletonList("local.FixturePlugin"), null);
        when(lifecycle.getInstalledPlugins()).thenReturn(Collections.singletonList(local));
        when(config.getConfiguration("microbotPluginVersions", "plugin.FixturePlugin")).thenReturn("1.2.3:" + HASH);

        assertFalse(manager.isHubInstalled("FixturePlugin"));
        assertFalse(manager.isLoadedFromHub("FixturePlugin"));
    }

    @Test
    public void localReplacementAndRemovalDoNotReportStaleHubVersion() throws Exception {
        when(config.getConfiguration("microbotPluginVersions", "plugin.FixturePlugin"))
                .thenReturn("1.2.3:" + HASH);
        Constructor<LivePluginManager.PluginEntry> constructor = LivePluginManager.PluginEntry.class.getDeclaredConstructor(
                String.class, String.class, String.class, Path.class, boolean.class, String.class, List.class, String.class);
        constructor.setAccessible(true);
        LivePluginManager.PluginEntry local = constructor.newInstance("fixture.FixturePlugin", "Fixture", "2.0.0",
                Paths.get("FixturePlugin.jar"), false, "Ready", Collections.singletonList("fixture.FixturePlugin"), null);
        when(lifecycle.getInstalledPlugins()).thenReturn(Collections.singletonList(local));
        manager.loadSideLoadPlugins();

        assertEquals("2.0.0", manager.getInstalledPluginVersion("FixturePlugin").get());

        when(lifecycle.getInstalledPlugins()).thenReturn(Collections.emptyList());
        assertFalse(manager.getInstalledPluginVersion("FixturePlugin").isPresent());
        verify(config, never()).getConfiguration("microbotPluginVersions", "plugin.FixturePlugin");
    }
}
