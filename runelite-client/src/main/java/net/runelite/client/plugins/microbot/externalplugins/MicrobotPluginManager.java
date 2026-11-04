/*
 * Copyright (c) 2023 Microbot
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.externalplugins;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.reflect.ClassPath;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.recovery.StartupRecovery;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.ui.SplashScreen;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.Proxy;
import java.net.ProxySelector;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotPluginManager {
    private static final File PLUGIN_DIR = new File(RuneLite.RUNELITE_DIR, "microbot-plugins");
    private static final String INSTALLED_VERSION_GROUP = "microbotPluginVersions";
    private static final String INSTALLED_VERSION_KEY_PREFIX = "plugin.";
    private static final String UPDATE_NOTIFICATION_GROUP = "microbotPluginUpdateNotifications";
    private static final String UPDATE_NOTIFICATION_KEY_PREFIX = "plugin.";

    private final OkHttpClient okHttpClient;
    private final MicrobotPluginClient microbotPluginClient;
    private final EventBus eventBus;
    private final PluginManager pluginManager;
    private final ConfigManager configManager;
    private final MicrobotApi microbotApi;

    private final LivePluginManager livePlugins;
    private final ExecutorService downloads = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "microbot-plugin-downloads");
        thread.setDaemon(true);
        return thread;
    });

    @Inject
    @Named("safeMode")
    private boolean safeMode;

    private final Map<String, MicrobotPluginManifest> manifestMap = new ConcurrentHashMap<>();
    private final Map<String, String> blockedPlugins = new ConcurrentHashMap<>();
    private final Map<String, String> notifiedBlockedPlugins = new HashMap<>();

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private volatile boolean localPluginsLoaded;
    private static final String PLUGIN_PACKAGE = "net.runelite.client.plugins.microbot";

    @Inject
    MicrobotPluginManager(
            OkHttpClient okHttpClient,
            MicrobotPluginClient microbotPluginClient,
            EventBus eventBus,
            PluginManager pluginManager,
            ConfigManager configManager,
            MicrobotApi microbotApi,
            LivePluginManager livePlugins
    ) {
        this.okHttpClient = okHttpClient;
        this.microbotPluginClient = microbotPluginClient;
        this.eventBus = eventBus;
        this.pluginManager = pluginManager;
        this.configManager = configManager;
        this.microbotApi = microbotApi;
        this.livePlugins = livePlugins;
        livePlugins.setLoadPolicy(new LivePluginManager.LoadPolicy() {
            @Override
            public String blockReason(String internalName, PluginDescriptor descriptor) {
                return getLoadBlockReason(internalName, descriptor);
            }

            @Override
            public void blocked(String internalName, String version, String phase, String reason) {
                blockedPlugins.put(internalName, reason);
                StartupRecovery.pluginFailed(internalName, version, phase, reason);
            }

            @Override
            public void loaded(String internalName) {
                blockedPlugins.remove(internalName);
            }
        });
    }

    /**
     * Initializes the MicrobotPluginManager
     */
    public void init() {
        loadManifest();
    }

    /**
     * Loads the plugin manifest list from the remote server and updates the local manifest map.
     * If the manifest has changed, posts an ExternalPluginsChanged event.
     */
    private void loadManifest() {
        try {
            List<MicrobotPluginManifest> manifests = microbotPluginClient.downloadManifest();
            Map<String, MicrobotPluginManifest> next = new HashMap<>(manifests.size());

            com.google.gson.JsonArray allReleases = null;
            try {
                allReleases = microbotPluginClient.fetchAllReleases();
                log.debug("Fetched {} releases from GitHub", allReleases.size());
            } catch (IOException ex) {
                log.warn("Failed to fetch GitHub releases: {}", ex.getMessage());
                log.debug("Releases fetch error", ex);
            }

            for (MicrobotPluginManifest m : manifests) {
                next.put(m.getInternalName(), m);
                if (allReleases != null) {
                    try {
                        List<String> versions = microbotPluginClient.parseVersionsFromReleases(m, allReleases);
                        m.setAvailableVersions(versions);
                    } catch (IOException ex) {
                        log.warn("Failed to parse available versions for {}: {}", m.getInternalName(), ex.getMessage());
                        log.debug("Version parse error", ex);
                    }
                }
            }
            boolean changed = !next.keySet().equals(manifestMap.keySet())
                    || next.entrySet().stream().anyMatch(e -> {
                MicrobotPluginManifest cur = manifestMap.get(e.getKey());
                return cur == null || !Objects.equals(cur.getSha256(), e.getValue().getSha256());
            });
            if (changed) {
                manifestMap.clear();
                manifestMap.putAll(next);
                log.info("Loaded {} plugin manifests.", manifestMap.size());
                eventBus.post(new ExternalPluginsChanged());
            } else {
                log.debug("Plugin manifests unchanged ({} entries), skipping event.", manifestMap.size());
            }
        } catch (Exception e) {
            log.error("Failed to fetch plugin manifests", e);
        }
    }

    public Map<String, MicrobotPluginManifest> getManifestMap() {
        return Collections.unmodifiableMap(manifestMap);
    }

    public File getPluginDirectory() {
        return livePlugins.getPluginDirectory().toFile();
    }

    public Optional<MicrobotPluginManifest> findManifest(String name) {
        return manifestMap.values().stream()
                .filter(manifest -> name.equalsIgnoreCase(manifest.getInternalName())
                        || name.equalsIgnoreCase(manifest.getDisplayName()))
                .findFirst();
    }

    public boolean isLoaded(String internalName) {
        return getInstalledPlugins().stream()
                .anyMatch(plugin -> plugin.getClass().getSimpleName().equalsIgnoreCase(internalName));
    }

    public boolean isLoadedFromHub(String internalName) {
        if (Strings.isNullOrEmpty(internalName)) {
            return false;
        }
        Optional<LivePluginManager.PluginEntry> live = liveEntry(internalName);
        return live.isPresent() ? live.get().getHubId() != null : hasInstalledVersionRecord(internalName);
    }

    public boolean isHubInstalled(String internalName) {
        if (Strings.isNullOrEmpty(internalName)) {
            return false;
        }
        Optional<LivePluginManager.PluginEntry> live = liveEntry(internalName);
        return live.isPresent() ? live.get().getHubId() != null : hasInstalledVersionRecord(internalName);
    }

    private Optional<LivePluginManager.PluginEntry> liveEntry(String internalName) {
        List<LivePluginManager.PluginEntry> entries = livePlugins.getInstalledPlugins();
        Optional<LivePluginManager.PluginEntry> byHub = entries.stream()
                .filter(entry -> internalName.equalsIgnoreCase(entry.getHubId()))
                .findFirst();
        if (byHub.isPresent()) {
            return byHub;
        }
        return entries.stream()
                .filter(entry -> entry.getClassNames().stream().anyMatch(name -> internalName.equalsIgnoreCase(simpleName(name))))
                .findFirst();
    }

    private boolean hasInstalledVersionRecord(String internalName) {
        return !Strings.isNullOrEmpty(configManager.getConfiguration(INSTALLED_VERSION_GROUP,
                INSTALLED_VERSION_KEY_PREFIX + internalName));
    }

    private static String simpleName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /**
     * Creates an OkHttpClient instance that does not use any proxy settings.
     *
     * @param base
     * @return
     */
    private static OkHttpClient noProxy(OkHttpClient base) {
        return base.newBuilder()
                .proxy(Proxy.NO_PROXY)
                .proxySelector(ProxySelector.of(null))
                .followSslRedirects(false)
                .build();
    }

    public static File[] createSideloadingFolder() {
        try {
            Files.createParentDirs(PLUGIN_DIR);

            if (!PLUGIN_DIR.exists() && PLUGIN_DIR.mkdir()) {
                log.debug("Directory for sideloading was created successfully.");
            }
        } catch (IOException e) {
            log.trace("Error creating directory for microbot-plugins!", e);
        }

        return PLUGIN_DIR.listFiles();
    }

    /**
     * Loads managed local plugins before the normal client startup pass.
     */
    public void loadSideLoadPlugins() {
        livePlugins.loadAtStartup();
        localPluginsLoaded = true;
    }

    private List<Plugin> loadPlugins(List<Class<?>> types, BiConsumer<Integer, Integer> onPluginLoaded)
            throws PluginInstantiationException {
        List<Class<?>> accepted = new ArrayList<>();
        for (Class<?> type : types) {
            PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
            if (descriptor == null || pluginManager.getPlugins().stream()
                    .anyMatch(plugin -> plugin.getClass() == type)) {
                continue;
            }

            if (descriptor.isExternal()) {
                String blockReason = getLoadBlockReason(type.getSimpleName(), descriptor);
                if (blockReason != null) {
                    blockedPlugins.put(type.getSimpleName(), blockReason);
                    StartupRecovery.pluginFailed(type.getSimpleName(), descriptor.version(),
                            "compatibility check", blockReason);
                    continue;
                }
                blockedPlugins.remove(type.getSimpleName());
            } else if (descriptor.disable()) {
                StartupRecovery.pluginFailed(type.getSimpleName(), descriptor.version(),
                        "compatibility check", "disabled upstream");
                continue;
            }
            accepted.add(type);
        }
        return pluginManager.loadPlugins(accepted, onPluginLoaded);
    }
    /**
     * Determines if a class is a Microbot-related Plugin that should be loaded.
     * This includes plugins from utility packages, UI components, and specific Microbot systems.
     *
     * @param clazz the class to check
     * @return true if the class should be included in Microbot plugin loading
     */
    private static boolean isMicrobotRelatedPlugin(Class<?> clazz) {
        if (clazz == null || clazz.getPackage() == null) {
            return false;
        }

        if (!Plugin.class.isAssignableFrom(clazz) || clazz == Plugin.class) {
            return false;
        }

        PluginDescriptor descriptor = clazz.getAnnotation(PluginDescriptor.class);
        if (descriptor == null) {
            return false;
        }

        String pkg = clazz.getPackage().getName();

        if (pkg.startsWith(PLUGIN_PACKAGE)) {
            return pkg.equals(PLUGIN_PACKAGE)
                    || pkg.contains(".ui")
                    || pkg.contains(".util")
                    || pkg.contains(".shortestpath")
                    || pkg.contains(".questhelper")
                    || pkg.contains("inventorysetups")
                    || pkg.contains("breakhandler");
        }

        return false;
    }

    /**
     * Scans the classpath for Microbot-related Plugin classes and returns them.
     * This includes plugin classes from utility packages, UI components, and specific Microbot systems.
     *
     * @return list of Microbot-related Plugin classes found on the classpath
     */
    private List<Class<?>> scanForMicrobotPlugins() {
        List<Class<?>> microbotPlugins = new ArrayList<>();

        try {
            ClassPath classPath = ClassPath.from(getClass().getClassLoader());

            for (ClassPath.ClassInfo classInfo : classPath.getAllClasses()) {
                if (!classInfo.getPackageName().startsWith(PLUGIN_PACKAGE)) {
                    continue;
                }

                try {
                    Class<?> clazz = classInfo.load();
                    if (isMicrobotRelatedPlugin(clazz)) {
                        microbotPlugins.add(clazz);
                        log.debug("Found Microbot plugin class: {}", clazz.getName());
                    }
                } catch (Throwable e) {
                    log.trace("Could not load class during Microbot scan: {}", classInfo.getName(), e);
                }
            }

            log.info("Found {} additional Microbot plugin classes during classpath scan", microbotPlugins.size());
        } catch (IOException e) {
            log.error("Failed to scan classpath for Microbot plugin classes", e);
        }

        return microbotPlugins;
    }

    public void loadCorePlugins(List<Class<?>> plugins) throws PluginInstantiationException {
        SplashScreen.stage(.59, null, "Loading plugins");
        List<Class<?>> combinedPlugins = new ArrayList<>(plugins);

        List<Class<?>> additionalMicrobotPlugins = scanForMicrobotPlugins();

        Set<Class<?>> existingPlugins = new HashSet<>(plugins);

        List<Class<?>> newMicrobotPlugins = additionalMicrobotPlugins.stream()
                .filter(clazz -> !existingPlugins.contains(clazz))
                .collect(Collectors.toList());

        combinedPlugins.addAll(newMicrobotPlugins);

        log.info("Loading core plugins: {} passed in + {} core Microbot plugins = {} total",
                plugins.size(), newMicrobotPlugins.size(), combinedPlugins.size());

        if (!combinedPlugins.isEmpty()) {
            loadPlugins(combinedPlugins, (loaded, total) ->
                    SplashScreen.stage(.60, .70, null, "Loading Microbot plugins", loaded, total, false));
        }
    }

    @Subscribe
    public void onClientShutdown(ClientShutdown shutdown) {
        if (isShuttingDown.compareAndSet(false, true)) {
            downloads.shutdown();
            shutdown.waitFor(livePlugins.shutdown().whenComplete((result, failure) -> StartupRecovery.cleanExit()));
        }
    }

    public CompletableFuture<LivePluginManager.OperationResult> installPlugin(
            MicrobotPluginManifest manifest, @Nullable String versionOverride) {
        if (isShuttingDown.get()) {
            return installationUnavailable();
        }
        if (manifest == null || Strings.isNullOrEmpty(manifest.getInternalName()) || manifest.isDisable()) {
            return CompletableFuture.completedFuture(
                    LivePluginManager.OperationResult.failure("This Hub plugin is unavailable."));
        }
        if (safeMode) {
            return CompletableFuture.completedFuture(
                    LivePluginManager.OperationResult.failure("External plugins are unavailable in safe mode."));
        }
        String version = Strings.isNullOrEmpty(versionOverride) ? manifest.getVersion() : versionOverride;
        MicrobotPluginHealth health = getPluginHealth(manifest, version);
        if (health.isBlocking()) {
            return CompletableFuture.completedFuture(LivePluginManager.OperationResult.failure(
                    "This plugin version is unavailable: " + health.getLabel()
                            + (health.getReason() == null ? "" : " (" + health.getReason() + ")")));
        }
        try {
            return CompletableFuture.supplyAsync(() -> downloadAndInstall(manifest, version), downloads);
        } catch (RejectedExecutionException rejected) {
            // Shutdown can win after the flag check but before executor submission.
            return installationUnavailable();
        }
    }

    private CompletableFuture<LivePluginManager.OperationResult> installationUnavailable() {
        return CompletableFuture.completedFuture(
                LivePluginManager.OperationResult.failure("Plugin manager is shutting down."));
    }

    private LivePluginManager.OperationResult downloadAndInstall(MicrobotPluginManifest manifest, String version) {
        Path temporary = null;
        try {
            if (isShuttingDown.get()) {
                return LivePluginManager.OperationResult.failure("Plugin manager is shutting down.");
            }
            if (Strings.isNullOrEmpty(version) || (manifest.getMinClientVersion() != null
                    && !Rs2UiHelper.isClientVersionCompatible(manifest.getMinClientVersion()))) {
                return LivePluginManager.OperationResult.failure("The selected plugin version is incompatible with this client.");
            }
            HttpUrl jarUrl = microbotPluginClient.getJarURL(manifest, version);
            if (jarUrl == null || !jarUrl.isHttps()) {
                return LivePluginManager.OperationResult.failure("Couldn't download " + hubName(manifest)
                        + ": the Plugin Hub link is not a secure HTTPS link. Report this in Discord.");
            }
            temporary = java.nio.file.Files.createTempFile("microbot-plugin-", ".jar");
            Request request = new Request.Builder().url(jarUrl).build();
            try (Response response = noProxy(okHttpClient).newCall(request).execute()) {
                if (response.code() >= 400 && response.code() < 500) {
                    return LivePluginManager.OperationResult.failure("Couldn't download " + hubName(manifest) + " "
                            + version + ": this version is no longer on the Plugin Hub. Pick another version.");
                }
                if (!response.isSuccessful() || response.body() == null || !response.request().url().isHttps()) {
                    return LivePluginManager.OperationResult.failure("Couldn't download " + hubName(manifest)
                            + " from the Plugin Hub (HTTP " + response.code() + "). Check your connection and try again.");
                }
                try (InputStream input = response.body().byteStream()) {
                    java.nio.file.Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            String expectedHash = version.equals(manifest.getVersion()) ? manifest.getSha256() : null;
            String actualHash = LivePluginManager.hash(temporary);
            if (!Strings.isNullOrEmpty(expectedHash) && !actualHash.equalsIgnoreCase(expectedHash)) {
                return LivePluginManager.OperationResult.failure("The downloaded file for " + hubName(manifest)
                        + " didn't match the Plugin Hub's checksum. Try again later; if it keeps happening, report it in Discord.");
            }
            LivePluginManager.OperationResult result = livePlugins.installHub(temporary,
                    manifest.getInternalName(), expectedHash).get();
            if (result.isSuccess()) {
                rememberInstalledPluginVersion(manifest.getInternalName(), version, actualHash);
                try {
                    sendPluginInstallTelemetry(manifest, version);
                } catch (RuntimeException failure) {
                    // Telemetry is best effort and must not turn an installed plugin into a failure.
                    log.debug("Could not record plugin installation telemetry");
                }
            }
            return result;
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (failure instanceof IOException) {
                return LivePluginManager.OperationResult.failure("Couldn't reach the Plugin Hub to download "
                        + hubName(manifest) + ". Check your connection and try again.");
            }
            return LivePluginManager.OperationResult.failure("Couldn't " + (isHubInstalled(manifest.getInternalName()) ? "update " : "install ")
                    + hubName(manifest) + ": "
                    + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        } finally {
            if (temporary != null) {
                try {
                    java.nio.file.Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    log.debug("Could not remove temporary plugin download");
                }
            }
        }
    }
    public CompletableFuture<LivePluginManager.OperationResult> removePlugin(MicrobotPluginManifest manifest) {
        if (manifest == null || Strings.isNullOrEmpty(manifest.getInternalName())) {
            return CompletableFuture.completedFuture(
                    LivePluginManager.OperationResult.failure("Select an installed plugin."));
        }
        String internalName = manifest.getInternalName();
        return livePlugins.removeHub(internalName).thenApply(result -> {
            if (result.isSuccess()) {
                clearInstalledPluginVersion(internalName);
                synchronized (this) {
                    blockedPlugins.remove(internalName);
                    notifiedBlockedPlugins.remove(internalName);
                }
                // The lifecycle publishes before this continuation clears Hub metadata/health.
                SwingUtilities.invokeLater(() -> eventBus.post(new ExternalPluginsChanged()));
            }
            return result;
        });
    }
    private static String hubName(MicrobotPluginManifest manifest) {
        return Strings.isNullOrEmpty(manifest.getDisplayName()) ? manifest.getInternalName() : manifest.getDisplayName();
    }

    private void sendPluginInstallTelemetry(MicrobotPluginManifest manifest, @Nullable String versionOverride)
    {
        if (manifest == null) {
            return;
        }

        String version = Strings.isNullOrEmpty(versionOverride) ? manifest.getVersion() : versionOverride;
        microbotApi.increasePluginInstall(manifest.getInternalName(), manifest.getDisplayName(), version);
    }

    /** Returns external plugins currently registered with the client. */
    public List<Plugin> getInstalledPlugins() {
        Predicate<Plugin> isExternalPluginPredicate = plugin ->
                plugin.getClass().getAnnotation(PluginDescriptor.class).isExternal();

        List<Plugin> loadedPlugins = pluginManager.getPlugins()
                .stream()
                .filter(plugin -> !plugin.getClass().getAnnotation(PluginDescriptor.class).hidden())
                .filter(isExternalPluginPredicate)
                .collect(Collectors.toList());

        return loadedPlugins;
    }

    /**
     * Downloads and replaces a Hub artifact through the serialized live lifecycle.
     */
    public CompletableFuture<LivePluginManager.OperationResult> updatePlugin(
            MicrobotPluginManifest manifest, @Nullable String versionOverride) {
        return installPlugin(manifest, versionOverride);
    }

    public MicrobotPluginHealth getPluginHealth(MicrobotPluginManifest manifest, @Nullable String version) {
        return MicrobotPluginHealth.evaluate(manifest, version,
                Rs2UiHelper.isClientVersionCompatible(manifest.getMinClientVersion()));
    }

    public Map<String, String> getBlockedPlugins() {
        return Collections.unmodifiableMap(blockedPlugins);
    }

    public synchronized Map<String, String> takeBlockedPluginsToNotify() {
        Map<String, String> pending = new java.util.TreeMap<>();
        blockedPlugins.forEach((name, reason) -> {
            if (!reason.equals(notifiedBlockedPlugins.get(name))) {
                pending.put(name, reason);
            }
        });
        notifiedBlockedPlugins.putAll(pending);
        return pending;
    }

    @Nullable
    String getLoadBlockReason(String internalName, PluginDescriptor descriptor) {
        if (!Rs2UiHelper.isClientVersionCompatible(descriptor.minClientVersion())) {
            return "requires client " + descriptor.minClientVersion() + " or newer (current "
                    + RuneLiteProperties.getMicrobotVersion() + ")";
        }
        if (descriptor.disable()) {
            return "disabled upstream";
        }

        MicrobotPluginManifest manifest = manifestMap.get(internalName);
        if (manifest == null) {
            return null;
        }
        MicrobotPluginHealth health = getPluginHealth(manifest, descriptor.version());
        String reason = health.getReason();
        if (manifest.isDisable()) {
            return "disabled upstream" + (reason == null ? "" : ": " + reason);
        }
        if (!Strings.isNullOrEmpty(descriptor.version())
                && (health.getState() == MicrobotPluginHealth.State.BROKEN || health.isAffected(descriptor.version()))) {
            return "version " + descriptor.version() + " is confirmed broken upstream"
                    + (reason == null ? "" : ": " + reason);
        }
        return null;
    }
    /**
     * Gets the manifest for a given plugin, this pulls from the global manifest map.
     *
     * @param plugin the plugin to get the manifest for
     * @return the manifest for the plugin, or null if not found or not an external plugin
     */
    @Nullable
    private MicrobotPluginManifest getPluginManifest(Plugin plugin) {
        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (descriptor == null || !descriptor.isExternal()) {
            return null;
        }

        String internalName = plugin.getClass().getSimpleName();

        return manifestMap.get(internalName);
    }

    /** Prefers a live local artifact's version over metadata from an earlier Hub installation. */
    public Optional<String> getInstalledPluginVersion(String internalName) {
        if (Strings.isNullOrEmpty(internalName)) {
            return Optional.empty();
        }
        Optional<LivePluginManager.PluginEntry> live = livePlugins.getInstalledPlugins().stream()
                .filter(entry -> internalName.equals(entry.getHubId()) || entry.getClassNames().stream()
                        .anyMatch(name -> internalName.equals(name.substring(name.lastIndexOf('.') + 1))))
                .findFirst();
        if (live.isPresent() && live.get().getHubId() == null) {
            return Optional.ofNullable(live.get().getVersion()).filter(version -> !version.isEmpty());
        }
        if (!live.isPresent() && localPluginsLoaded) {
            // A local removal must not resurrect stale Hub metadata in the installed-version UI.
            return Optional.empty();
        }
        String key = INSTALLED_VERSION_KEY_PREFIX + internalName;
        String value = configManager.getConfiguration(INSTALLED_VERSION_GROUP, key);
        if (Strings.isNullOrEmpty(value)) {
            return Optional.empty();
        }

        String[] parts = value.split(":", 2);
        if (parts.length < 2 || Strings.isNullOrEmpty(parts[0]) || Strings.isNullOrEmpty(parts[1])) {
            return Optional.empty();
        }

        return Optional.of(parts[0]);
    }

    public Optional<OutdatedPluginUpdate> getOutdatedPluginUpdate(Plugin plugin) {
        MicrobotPluginManifest manifest = getPluginManifest(plugin);
        if (manifest == null) {
            return Optional.empty();
        }

        String internalName = manifest.getInternalName();
        String latestVersion = manifest.getVersion();
        String installedVersion = getInstalledPluginVersion(internalName)
                .orElseGet(() -> {
                    PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                    return descriptor == null ? null : descriptor.version();
                });

        if (Strings.isNullOrEmpty(internalName)
                || Strings.isNullOrEmpty(installedVersion)
                || Strings.isNullOrEmpty(latestVersion)
                || latestVersion.equals(installedVersion)
                || getPluginHealth(manifest, latestVersion).isBlocking()) {
            return Optional.empty();
        }

        String notificationKey = UPDATE_NOTIFICATION_KEY_PREFIX + internalName;
        String notifiedVersion = configManager.getConfiguration(UPDATE_NOTIFICATION_GROUP, notificationKey);
        if (latestVersion.equals(notifiedVersion)) {
            return Optional.empty();
        }

        return Optional.of(new OutdatedPluginUpdate(
                internalName,
                Strings.isNullOrEmpty(manifest.getDisplayName()) ? internalName : manifest.getDisplayName(),
                installedVersion,
                latestVersion
        ));
    }

    public void rememberOutdatedPluginUpdateNotification(OutdatedPluginUpdate outdatedPluginUpdate) {
        if (outdatedPluginUpdate == null || Strings.isNullOrEmpty(outdatedPluginUpdate.getInternalName())
                || Strings.isNullOrEmpty(outdatedPluginUpdate.getLatestVersion())) {
            return;
        }

        configManager.setConfiguration(
                UPDATE_NOTIFICATION_GROUP,
                UPDATE_NOTIFICATION_KEY_PREFIX + outdatedPluginUpdate.getInternalName(),
                outdatedPluginUpdate.getLatestVersion()
        );
    }

    private void rememberInstalledPluginVersion(String internalName, String version, String sha256) {
        if (Strings.isNullOrEmpty(internalName) || Strings.isNullOrEmpty(version) || Strings.isNullOrEmpty(sha256)) {
            return;
        }

        configManager.setConfiguration(
                INSTALLED_VERSION_GROUP,
                INSTALLED_VERSION_KEY_PREFIX + internalName,
                version + ":" + sha256
        );
    }

    private void clearInstalledPluginVersion(String internalName) {
        if (Strings.isNullOrEmpty(internalName)) {
            return;
        }
        configManager.unsetConfiguration(INSTALLED_VERSION_GROUP, INSTALLED_VERSION_KEY_PREFIX + internalName);
    }

    public static final class OutdatedPluginUpdate {
        private final String internalName;
        private final String displayName;
        private final String installedVersion;
        private final String latestVersion;

        private OutdatedPluginUpdate(String internalName, String displayName, String installedVersion, String latestVersion) {
            this.internalName = internalName;
            this.displayName = displayName;
            this.installedVersion = installedVersion;
            this.latestVersion = latestVersion;
        }

        public String getInternalName() {
            return internalName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getInstalledVersion() {
            return installedVersion;
        }

        public String getLatestVersion() {
            return latestVersion;
        }
    }
}
