package net.runelite.client.plugins.microbot.externalplugins;

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;

/** Serializes all external artifact mutations. File IO and disposal never run on the client/EDT. */
@Singleton
@Slf4j
public class LivePluginManager {
    private static final String CONFIG_GROUP = "microbotPluginManager";
    private static final String PLUGIN_DIRECTORY_KEY = "pluginDirectory";
    private final PluginManager plugins;
    private final ConfigManager config;
    private final EventBus events;
    private final boolean safeMode;
    @Getter
    private volatile Path pluginDirectory;
    private volatile Path cacheDirectory;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "microbot-plugin-lifecycle");
        thread.setDaemon(true);
        return thread;
    });
    // Accessed only by worker. Snapshots contain no loader/plugin references.
    private final Map<String, Installation> installed = new LinkedHashMap<>();
    private final Map<String, List<Installation>> quarantine = new HashMap<>();
    private volatile List<PluginEntry> snapshot = Collections.emptyList();
    private volatile boolean watching;
    private volatile boolean closed;
    @Getter
    private volatile String watchStatus = "";
    private ScheduledFuture<?> watcher;
    private Map<Path, String> observed = Collections.emptyMap();
    private final Map<Path, String> skipped = new HashMap<>();
    private volatile LoadPolicy loadPolicy;

    interface LoadPolicy {
        String blockReason(String internalName, PluginDescriptor descriptor);

        void blocked(String internalName, String version, String phase, String reason);

        void loaded(String internalName);
    }

    void setLoadPolicy(LoadPolicy loadPolicy) {
        this.loadPolicy = loadPolicy;
    }

    @Inject
    LivePluginManager(PluginManager plugins, ConfigManager config, EventBus events,
            @Named("safeMode") boolean safeMode) {
        this(plugins, config, events, safeMode, RuneLite.RUNELITE_DIR.toPath().resolve("microbot-plugins"));
    }

    LivePluginManager(PluginManager plugins, ConfigManager config, EventBus events,
            boolean safeMode, Path directory) {
        this.plugins = plugins;
        this.config = config;
        this.events = events;
        this.safeMode = safeMode;
        // ConfigManager has no active profile during Guice construction. Start with
        // the launcher default and apply the saved directory in loadAtStartup().
        this.pluginDirectory = directory.toAbsolutePath().normalize();
        this.cacheDirectory = pluginDirectory.resolve(".runtime");
    }

    private void applyConfiguredDirectory() {
        Path configured = configuredDirectory(config, pluginDirectory);
        if (!configured.equals(pluginDirectory)) {
            pluginDirectory = configured;
            cacheDirectory = configured.resolve(".runtime");
        }
    }

    private static Path configuredDirectory(ConfigManager config, Path fallback) {
        String configured;
        try {
            configured = config.getConfiguration(CONFIG_GROUP, PLUGIN_DIRECTORY_KEY);
        } catch (RuntimeException ex) {
            // The config profile is not loaded yet during early startup.
            log.debug("Could not read the configured plugin directory yet", ex);
            return fallback.toAbsolutePath().normalize();
        }
        if (configured != null && !configured.trim().isEmpty()) {
            try {
                return java.nio.file.Paths.get(configured).toAbsolutePath().normalize();
            } catch (InvalidPathException ignored) {
                // Keep the default folder if a previously saved path is no longer valid.
            }
        }
        return fallback.toAbsolutePath().normalize();
    }

    public List<PluginEntry> getInstalledPlugins() {
        // isRunning is sampled from the current registry without retaining plugin objects in UI models.
        Set<String> active = new HashSet<>();
        for (Plugin plugin : plugins.getActivePlugins()) {
            active.add(plugin.getClass().getName());
        }
        List<PluginEntry> result = new ArrayList<>();
        for (PluginEntry entry : snapshot) {
            result.add(new PluginEntry(entry.id, entry.name, entry.version, entry.sourcePath,
                    entry.classNames.stream().anyMatch(active::contains), entry.status, entry.classNames, entry.hubId));
        }
        return Collections.unmodifiableList(result);
    }

    public CompletableFuture<OperationResult> install(Path source) {
        return install(source, null);
    }

    /** expectedId protects the Update-selected action from importing an unrelated plugin. */
    public CompletableFuture<OperationResult> install(Path source, String expectedId) {
        return submit(() -> installNow(source, expectedId, null, null, false, false));
    }

    CompletableFuture<OperationResult> installHub(Path source, String hubId, String expectedHash) {
        return submit(() -> installNow(source, null, hubId, expectedHash, false, false));
    }

    public CompletableFuture<OperationResult> reload(String id) {
        return submit(() -> {
            Installation current = require(id);
            return installNow(current.source, id, current.hubId, current.expectedHash, true, false);
        });
    }

    public CompletableFuture<OperationResult> remove(String id) {
        return submit(() -> removeNow(require(id), true));
    }

    CompletableFuture<OperationResult> removeHub(String hubId) {
        return submit(() -> {
            Installation current = installed.values().stream()
                    .filter(i -> hubId.equals(i.hubId))
                    .findFirst().orElse(null);
            if (current == null) {
                return removeUnloadedHubFile(hubId);
            }
            return removeNow(current, true);
        });
    }

    private OperationResult removeUnloadedHubFile(String hubId) throws Exception {
        if (!hubId.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException("Plugin is not installed.");
        }
        Path file = pluginDirectory.resolve(hubId + ".jar");
        checkManagedPath(file);
        if (!Files.exists(file) || installed.values().stream().anyMatch(i -> i.source.equals(file))) {
            throw new IllegalArgumentException("Plugin is not installed.");
        }
        String digest = hash(file);
        Properties origin = readOrigin(file);
        boolean fromOrigin = hubId.equals(origin.getProperty("hubId")) && digest.equalsIgnoreCase(origin.getProperty("sha256", ""));
        if (!fromOrigin && !hubId.equals(recordedHubInstall(file, digest))) {
            throw new IllegalArgumentException(file.getFileName() + " was not installed from the Plugin Hub. Remove it in Local Plugins.");
        }
        Files.deleteIfExists(file);
        skipped.remove(file);
        Files.deleteIfExists(originPath(file));
        return OperationResult.success("Removed " + hubId + ". Settings were kept.");
    }

    public CompletableFuture<OperationResult> rescan() {
        return submit(() -> {
            skipped.clear();
            OperationResult result = scan(false);
            if (result.success) {
                watchStatus = "";
            }
            return result;
        });
    }

    /** Switches the active JAR folder, keeping files in place and replacing its loaded plugins. */
    public CompletableFuture<OperationResult> changePluginDirectory(Path directory) {
        return submit(() -> changePluginDirectoryNow(directory));
    }

    private OperationResult changePluginDirectoryNow(Path directory) throws Exception {
        if (directory == null) {
            throw new IllegalArgumentException("Choose a plugin folder.");
        }
        Path next = directory.toAbsolutePath().normalize();
        Path previous = pluginDirectory;
        if (next.equals(previous)) {
            return OperationResult.success("This is already the managed plugin folder.");
        }
        if (!quarantine.isEmpty()) {
            throw new IllegalStateException("Retry plugin cleanup before changing the managed folder.");
        }

        Files.createDirectories(next);
        if (!Files.isDirectory(next) || !Files.isWritable(next)) {
            throw new IllegalArgumentException("The selected plugin folder must be a writable directory.");
        }

        boolean resumeWatching = watching;
        setWatching(false);
        try {
            unloadManagedPluginsKeepingFiles();
            pluginDirectory = next;
            cacheDirectory = next.resolve(".runtime");
            config.setConfiguration(CONFIG_GROUP, PLUGIN_DIRECTORY_KEY, next.toString());
            Files.createDirectories(cacheDirectory);

            observed = Collections.emptyMap();
            OperationResult scanned = scan(false);
            if (resumeWatching) {
                setWatching(true);
            }
            return scanned.isSuccess()
                    ? OperationResult.success("Plugin folder changed. JAR files remain in their previous folders.")
                    : OperationResult.failure("Plugin folder changed, but some JARs could not be loaded:\n" + scanned.getMessage());
        } catch (Exception | LinkageError | AssertionError failure) {
            pluginDirectory = previous;
            cacheDirectory = previous.resolve(".runtime");
            String detail = message(failure);
            try {
                config.setConfiguration(CONFIG_GROUP, PLUGIN_DIRECTORY_KEY, previous.toString());
            } catch (RuntimeException configFailure) {
                detail += "\nCould not restore the saved folder setting: " + message(configFailure);
            }
            observed = Collections.emptyMap();
            OperationResult restored = scan(false);
            if (resumeWatching) {
                setWatching(true);
            }
            if (!restored.isSuccess()) {
                detail += "\nPrevious folder recovery also reported errors:\n" + restored.getMessage();
            }
            return OperationResult.failure("Could not change the plugin folder: " + detail);
        }
    }

    private void unloadManagedPluginsKeepingFiles() throws Exception {
        List<Installation> remaining = new ArrayList<>(installed.values());
        Set<Class<?>> managedTypes = new HashSet<>();
        Set<Plugin> managedInstances = new HashSet<>();
        for (Installation installation : remaining) {
            managedTypes.addAll(installation.artifact.types);
            managedInstances.addAll(installation.instances);
        }

        for (Plugin plugin : plugins.getPlugins()) {
            if (managedInstances.contains(plugin)) {
                continue;
            }
            for (PluginDependency dependency : plugin.getClass().getAnnotationsByType(PluginDependency.class)) {
                if (managedTypes.contains(dependency.value())) {
                    throw new IllegalStateException("Remove dependent plugin " + plugin.getClass().getSimpleName()
                            + " before changing the managed folder.");
                }
            }
        }

        while (!remaining.isEmpty()) {
            Installation removable = null;
            for (Installation candidate : remaining) {
                boolean hasPendingDependent = remaining.stream()
                        .filter(other -> other != candidate)
                        .flatMap(other -> other.artifact.types.stream())
                        .anyMatch(type -> java.util.Arrays.stream(type.getAnnotationsByType(PluginDependency.class))
                                .anyMatch(dependency -> candidate.artifact.types.contains(dependency.value())));
                if (!hasPendingDependent) {
                    removable = candidate;
                    break;
                }
            }
            if (removable == null) {
                throw new IllegalStateException("Plugin dependencies contain a cycle; the managed folder was not changed.");
            }
            removeNow(removable, false);
            remaining.remove(removable);
        }
    }

    /** Called before the client's normal startPlugins pass; does not start scripts during the splash screen. */
    public void loadAtStartup() {
        if (SwingUtilities.isEventDispatchThread()
                || (Microbot.getClient() != null && Microbot.getClient().isClientThread())) {
            throw new IllegalStateException("Startup plugin scanning must run off the UI and client threads.");
        }
        applyConfiguredDirectory();
        OperationResult result = submit(() -> scan(true)).join();
        if (!result.success) {
            log.warn("Some local plugins could not be loaded: {}", result.message);
        }
    }

    public boolean isWatching() {
        return watching;
    }

    public synchronized void setWatching(boolean enabled) {
        if (closed || safeMode) {
            watching = false;
            return;
        }
        watching = enabled;
        watchStatus = "";
        if (watcher != null) {
            watcher.cancel(false);
            watcher = null;
        }
        if (enabled) {
            // This is a filesystem debounce, never a sleep waiting for game state. Two equal
            // observations avoid importing a JAR while a compiler/copy is still writing it.
            watcher = worker.scheduleWithFixedDelay(this::watch, 1, 1, TimeUnit.SECONDS);
        }
    }

    public synchronized CompletableFuture<OperationResult> shutdown() {
        setWatching(false);
        CompletableFuture<OperationResult> result = submit(() -> {
            List<String> errors = new ArrayList<>();
            Set<Installation> remaining = new HashSet<>(installed.values());
            quarantine.values().forEach(remaining::addAll);
            for (Installation current : remaining) {
                try {
                    retire(current);
                    release(current.artifact);
                } catch (Exception | LinkageError | AssertionError failure) {
                    errors.add(message(failure));
                }
            }
            return errors.isEmpty() ? OperationResult.success("Plugins stopped.")
                    : OperationResult.failure(String.join("; ", errors));
        });
        closed = true;
        result.whenComplete((ignored, failure) -> worker.shutdown());
        return result;
    }

    private synchronized CompletableFuture<OperationResult> submit(Callable<OperationResult> action) {
        if (closed) {
            return CompletableFuture.completedFuture(OperationResult.failure("Plugin manager is shutting down."));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (safeMode) {
                    return OperationResult.failure("External plugins are unavailable in safe mode.");
                }
                Files.createDirectories(cacheDirectory);
                return action.call();
            } catch (Exception | LinkageError | AssertionError failure) {
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                return OperationResult.failure(message(failure));
            } finally {
                publish();
            }
        }, worker);
    }

    private OperationResult installNow(Path source, String expectedId, String hubId, String expectedHash,
            boolean force, boolean startup) throws Exception {
        Path incoming = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(incoming, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Select an existing plugin JAR (symbolic links are not supported).");
        }
        Path staged = Files.createTempFile(cacheDirectory, "plugin-", ".jar");
        LivePluginArtifact candidate = null;
        Installation replacement = null;
        Installation originalSource = installed.values().stream().filter(i -> i.source.equals(incoming)).findFirst().orElse(null);
        boolean retained = false;
        try {
            Files.copy(incoming, staged, StandardCopyOption.REPLACE_EXISTING);
            String digest = hash(staged);
            if (expectedHash != null && !expectedHash.isEmpty() && !digest.equalsIgnoreCase(expectedHash)) {
                throw new IllegalArgumentException("Plugin checksum does not match the expected artifact. The installed version was kept.");
            }
            candidate = LivePluginArtifact.read(staged);
            if (expectedId != null && !candidate.id.equals(expectedId)) {
                throw new IllegalArgumentException("This JAR belongs to a different plugin; use Add JAR to install it.");
            }
            if (hubId != null && candidate.types.stream().noneMatch(type -> type.getSimpleName().equals(hubId))) {
                throw new IllegalArgumentException("Downloaded JAR does not contain the selected Hub plugin.");
            }
            checkLoadPolicy(candidate);
            Installation previousInstall = hubId == null ? installed.get(candidate.id)
                    : installed.values().stream().filter(i -> hubId.equals(i.hubId)).findFirst()
                            .orElse(installed.get(candidate.id));
            if (hubId != null && previousInstall != null && previousInstall.hubId == null
                    && !previousInstall.source.equals(incoming)) {
                throw new IllegalArgumentException(displayName(candidate) + " is loaded from a JAR you added ("
                        + previousInstall.source.getFileName() + "). Remove it in Local Plugins first.");
            }
            if (previousInstall != null && hubId != null && !previousInstall.blocked && !previousInstall.pendingRestart
                    && !quarantine.containsKey(previousInstall.artifact.id)
                    && (!previousInstall.artifact.id.equals(candidate.id)
                            || !entryClassNames(previousInstall.artifact).equals(entryClassNames(candidate)))) {
                return replaceChangedHubPlugin(previousInstall, incoming, hubId, expectedHash, startup);
            }
            Installation old = previousInstall;
            if (old != null && old.pendingRestart) {
                throw new IllegalStateException("Restart Microbot to finish removing " + displayName(old.artifact)
                        + " before installing it again.");
            }
            if (old != null && incoming.getParent().equals(pluginDirectory) && !incoming.equals(old.source)) {
                throw new IllegalArgumentException("Duplicate plugin JAR in the folder. Keep only " + old.source.getFileName() + ".");
            }
            if (quarantine.containsKey(candidate.id)) {
                throw new IllegalStateException("A previous generation could not be cleaned up. Remove this plugin before retrying.");
            }
            if (old != null && old.blocked) {
                throw new IllegalStateException("Previous cleanup failed. Remove the plugin successfully before installing a replacement.");
            }
            validate(candidate, old);
            if (old != null && old.digest.equals(digest) && !force) {
                return OperationResult.success("This plugin version is already loaded.");
            }
            Path target = old != null ? old.source : incoming.getParent().equals(pluginDirectory)
                    ? incoming : pluginDirectory.resolve(candidate.types.get(0).getSimpleName() + ".jar");
            checkManagedPath(target);
            for (Installation other : installed.values()) {
                if (other != old && other.source.toString().equalsIgnoreCase(target.toString())) {
                    throw new IllegalArgumentException("Another plugin already owns this installed JAR filename.");
                }
            }
            replacement = new Installation(candidate, target, digest, hubId,
                    hubId == null ? null : digest);
            Map<String, Boolean> enabled = new HashMap<>();
            Set<String> running = new HashSet<>();
            if (old != null) {
                onEdt(() -> {
                    for (Plugin plugin : old.instances) {
                        enabled.put(plugin.getClass().getName(), plugins.isPluginEnabled(plugin));
                        if (plugins.isPluginActive(plugin)) {
                            running.add(plugin.getClass().getName());
                        }
                    }
                    return null;
                });
            }
            // Prepare dependencies/injection before interrupting the existing instance.
            prepare(replacement, old);
            if (old != null) {
                try {
                    retire(old);
                } catch (Exception | LinkageError | AssertionError failure) {
                    old.blocked = true;
                    old.status = "Cleanup failed: " + message(failure);
                    throw new IllegalStateException(old.status + ". Replacement was not started.", failure);
                }
            }
            try {
                activate(replacement, enabled, running, old != null, startup);
                replaceFile(staged, target);
                writeOrigin(replacement);
                installed.put(replacement.artifact.id, replacement);
                retained = true;
                if (old != null) {
                    release(old.artifact);
                }
                notifyLoaded(replacement.artifact);
                boolean nowRunning = replacement.instances.stream().anyMatch(plugins::isPluginActive);
                if (old != null && force && old.digest.equals(digest)) {
                    return OperationResult.success("Reloaded " + describe(replacement.artifact) + ".");
                }
                return OperationResult.success(old == null
                        ? "Installed " + describe(replacement.artifact) + (nowRunning ? "."
                                : ". Turn it on in the Microbot plugin list.")
                        : "Updated " + displayName(replacement.artifact)
                                + (version(replacement.artifact).isEmpty() ? "" : " to " + version(replacement.artifact))
                                + ". Settings were kept.");
            } catch (Exception | LinkageError | AssertionError failure) {
                try {
                    retire(replacement);
                } catch (Exception | LinkageError | AssertionError cleanupFailure) {
                    quarantine(replacement, cleanupFailure);
                    retained = true;
                    // Keep the previous bytes installed on disk, but never restart another generation.
                    if (old != null) {
                        restoreDisk(old);
                    }
                    return OperationResult.failure(replacement.status);
                }
                if (old != null) {
                    restoreDisk(old);
                    // A disposed Script cannot be restarted. Re-create the previous artifact.
                    Path rollbackFile = Files.createTempFile(cacheDirectory, "rollback-", ".jar");
                    Files.copy(old.artifact.cacheFile, rollbackFile, StandardCopyOption.REPLACE_EXISTING);
                    LivePluginArtifact previous = LivePluginArtifact.read(rollbackFile);
                    Installation rollback = new Installation(previous, old.source, old.digest, old.hubId, old.expectedHash);
                    try {
                        prepare(rollback, null);
                        activate(rollback, enabled, running, true, startup);
                        installed.put(previous.id, rollback);
                        release(old.artifact);
                        return OperationResult.failure("Update failed; previous version restored: " + message(failure));
                    } catch (Exception | LinkageError | AssertionError rollbackFailure) {
                        rollback.status = "Rollback failed; plugin left stopped: " + message(rollbackFailure);
                        try {
                            retire(rollback);
                        } catch (Exception | LinkageError | AssertionError ignored) {
                            rollback.blocked = true;
                            rollback.status = "Rollback cleanup failed; restart required.";
                        }
                        installed.put(previous.id, rollback);
                        release(old.artifact);
                        return OperationResult.failure(rollback.status);
                    }
                }
                throw failure;
            }
        } catch (Exception | LinkageError | AssertionError failure) {
            if (originalSource != null) {
                try {
                    restoreDisk(originalSource);
                } catch (Exception restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                    originalSource.status = "Could not restore installed JAR: " + message(restoreFailure);
                }
            }
            throw failure;
        } finally {
            if (!retained) {
                if (candidate != null) {
                    try {
                        if (replacement != null) {
                            retire(replacement);
                        }
                    } catch (Exception | LinkageError | AssertionError cleanupFailure) {
                        quarantine(replacement, cleanupFailure);
                        throw new IllegalStateException(replacement.status, cleanupFailure);
                    }
                    release(candidate);
                } else {
                    Files.deleteIfExists(staged);
                }
            }
        }
    }

    private OperationResult replaceChangedHubPlugin(Installation previous, Path incoming, String hubId,
            String expectedHash, boolean startup) throws Exception {
        Path oldSource = previous.source;
        String oldHubId = previous.hubId;
        String oldExpectedHash = previous.expectedHash;
        String name = displayName(previous.artifact);
        Path backup = Files.createTempFile(cacheDirectory, "previous-", ".jar");
        try {
            Files.copy(previous.artifact.cacheFile, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                removeNow(previous, false);
            } catch (Exception | LinkageError | AssertionError failure) {
                throw new IllegalStateException("Couldn't stop " + name + " to update it: " + message(failure)
                        + ". The installed version was kept; restart Microbot and try again.", failure);
            }
            OperationResult result;
            try {
                result = installNow(incoming, null, hubId, expectedHash, false, startup);
            } catch (Exception | LinkageError | AssertionError failure) {
                result = OperationResult.failure(message(failure));
            }
            if (!result.isSuccess()) {
                return OperationResult.failure("Couldn't update " + name + ": " + result.getMessage() + " "
                        + restorePrevious(oldSource, backup, oldHubId, oldExpectedHash, startup));
            }
            Installation replacement = installed.values().stream().filter(i -> hubId.equals(i.hubId)).findFirst().orElse(null);
            if (replacement != null && !replacement.source.equals(oldSource)) {
                checkManagedPath(oldSource);
                try {
                    Files.deleteIfExists(originPath(oldSource));
                    Files.deleteIfExists(oldSource);
                } catch (IOException cleanup) {
                    log.warn("Could not delete the previous plugin JAR {}: {}", oldSource, cleanup.getMessage());
                }
            }
            return replacement == null ? result : OperationResult.success("Updated " + displayName(replacement.artifact)
                    + (version(replacement.artifact).isEmpty() ? "" : " to " + version(replacement.artifact)) + ".");
        } finally {
            try {
                Files.deleteIfExists(backup);
            } catch (IOException cleanup) {
                log.debug("Could not delete the update backup {}", backup, cleanup);
            }
        }
    }

    private String restorePrevious(Path oldSource, Path backup, String oldHubId, String oldExpectedHash, boolean startup) {
        try {
            if (!Files.exists(oldSource) || !hash(oldSource).equals(hash(backup))) {
                replaceFile(backup, oldSource);
            }
            OperationResult restored = installNow(oldSource, null, oldHubId, oldExpectedHash, true, startup);
            if (restored.isSuccess()) {
                return "The previous version was restored.";
            }
            log.warn("Could not restart the previous plugin version: {}", restored.getMessage());
        } catch (Exception | LinkageError | AssertionError failure) {
            log.warn("Could not restore the previous plugin version", failure);
        }
        return "The previous version is still in the plugin folder and loads after a restart.";
    }

    private void restoreDisk(Installation current) throws Exception {
        replaceFile(current.artifact.cacheFile, current.source);
        writeOrigin(current);
    }

    private void quarantine(Installation candidate, Throwable failure) {
        candidate.blocked = true;
        candidate.status = "Cleanup failed; remove before retrying: " + message(failure);
        quarantine.computeIfAbsent(candidate.artifact.id, ignored -> new ArrayList<>()).add(candidate);
        Installation primary = installed.get(candidate.artifact.id);
        if (primary == null) {
            installed.put(candidate.artifact.id, candidate);
        } else {
            primary.blocked = true;
            primary.status = candidate.status;
        }
    }

    private void validate(LivePluginArtifact candidate, Installation old) {
        Set<String> names = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (Class<? extends Plugin> type : candidate.types) {
            names.add(type.getName());
            if (!keys.add(configKey(type))) {
                throw new IllegalArgumentException("Multiple plugins in this JAR share an enable/disable setting.");
            }
        }
        for (Plugin plugin : plugins.getPlugins()) {
            if (old != null && old.instances.contains(plugin)) {
                continue;
            }
            if (names.contains(plugin.getClass().getName())) {
                throw new IllegalArgumentException(displayName(plugin.getClass())
                        + " is already loaded, either built into this client or from another JAR."
                        + " Remove the other copy first.");
            }
            if (keys.contains(configKey(plugin.getClass()))) {
                throw new IllegalArgumentException("This plugin uses the same on/off setting as "
                        + displayName(plugin.getClass()) + " ('" + configKey(plugin.getClass())
                        + "'). Give it a different @PluginDescriptor configName.");
            }
        }
        if (old != null) {
            Set<String> previous = new HashSet<>();
            old.artifact.types.forEach(type -> previous.add(type.getName()));
            if (!previous.equals(names)) {
                throw new IllegalArgumentException("An update must keep the same plugin entry classes. Remove the old package first.");
            }
            checkDependents(old);
        }
    }

    private void prepare(Installation candidate, Installation old) throws Exception {
        List<Plugin> available = new ArrayList<>(plugins.getPlugins());
        if (old != null) {
            available.removeAll(old.instances);
        }
        List<Class<? extends Plugin>> remaining = new ArrayList<>(candidate.artifact.types);
        while (!remaining.isEmpty()) {
            boolean progressed = false;
            for (Class<? extends Plugin> type : new ArrayList<>(remaining)) {
                boolean ready = true;
                for (PluginDependency dependency : type.getAnnotationsByType(PluginDependency.class)) {
                    Plugin found = available.stream().filter(p -> p.getClass() == dependency.value()).findFirst().orElse(null);
                    if (found == null) {
                        ready = false;
                    } else if (found.getClass().getClassLoader() instanceof PluginJarClassLoader
                            && found.getClass().getClassLoader() != candidate.artifact.loader) {
                        throw new IllegalArgumentException("Dependencies on another reloadable JAR are unsupported: " + type.getSimpleName());
                    }
                }
                if (ready) {
                    Plugin instance = plugins.preparePlugin(type, available);
                    candidate.instances.add(instance);
                    available.add(instance);
                    remaining.remove(type);
                    progressed = true;
                }
            }
            if (!progressed) {
                throw new IllegalArgumentException("Missing or cyclic plugin dependency: " + remaining.get(0).getSimpleName());
            }
        }
    }

    private void activate(Installation current, Map<String, Boolean> enabled, Set<String> running,
            boolean replacement, boolean startup) throws Exception {
        onEdt(() -> {
            for (Plugin plugin : current.instances) {
                plugins.add(plugin);
            }
            plugins.loadDefaultPluginConfiguration(current.instances);
            for (Plugin plugin : current.instances) {
                if (replacement) {
                    config.setConfiguration(RuneLiteConfig.GROUP_NAME, configKey(plugin.getClass()),
                            enabled.getOrDefault(plugin.getClass().getName(), false));
                }
                // At startup, defer enable-state migration until every plugin has been registered.
                boolean shouldStart = !startup && (replacement ? running.contains(plugin.getClass().getName()) : plugins.isPluginEnabled(plugin));
                if (!startup && shouldStart) {
                    for (Plugin conflict : plugins.conflictsForPlugin(plugin)) {
                        if (!current.instances.contains(conflict) && plugins.isPluginEnabled(conflict)) {
                            throw new IllegalStateException("Turn off " + displayName(conflict.getClass())
                                    + " first; it can't run at the same time as " + displayName(plugin.getClass()) + ".");
                        }
                    }
                }
                if (!startup && shouldStart && !plugins.startPlugin(plugin)) {
                    throw new IllegalStateException("Plugin did not start: " + plugin.getClass().getSimpleName());
                }
            }
            return null;
        });
    }

    private OperationResult removeNow(Installation current, boolean deleteFile) throws Exception {
        if (current.pendingRestart) {
            return OperationResult.success(current.status, true);
        }
        checkDependents(current);
        skipped.remove(current.source);
        try {
            retire(current);
            for (Installation pending : quarantine.getOrDefault(current.artifact.id, Collections.emptyList())) {
                if (pending != current) {
                    retire(pending);
                }
                release(pending.artifact);
            }
            quarantine.remove(current.artifact.id);
            if (deleteFile) {
                checkManagedPath(current.source);
                Files.deleteIfExists(current.source);
                Files.deleteIfExists(originPath(current.source));
            }
            release(current.artifact);
            installed.remove(current.artifact.id);
            return OperationResult.success("Removed " + displayName(current.artifact) + ". Settings were kept.");
        } catch (Exception | LinkageError | AssertionError failure) {
            current.blocked = true;
            current.status = "Removal failed: " + message(failure);
            if (!deleteFile) {
                throw failure;
            }
            try {
                checkManagedPath(current.source);
                Files.deleteIfExists(current.source);
                Files.deleteIfExists(originPath(current.source));
            } catch (Exception deleteFailure) {
                failure.addSuppressed(deleteFailure);
                throw failure;
            }
            log.warn("Plugin {} could not be stopped; its file was removed and the rest happens on restart",
                    current.artifact.id, failure);
            current.pendingRestart = true;
            current.status = "Restart Microbot to finish removing " + displayName(current.artifact) + ".";
            return OperationResult.success(current.status, true);
        }
    }

    private void retire(Installation current) throws Exception {
        onEdt(() -> {
            List<Plugin> reverse = new ArrayList<>(current.instances);
            Collections.reverse(reverse);
            for (Plugin plugin : reverse) {
                plugins.setPluginUnloading(plugin, true);
            }
            for (Plugin plugin : reverse) {
                plugins.stopPlugin(plugin);
                if (plugins.getShutdownFailure(plugin) != null) {
                    throw new IllegalStateException("Plugin shutdown failed.", plugins.getShutdownFailure(plugin));
                }
            }
            return null;
        });
        PluginResources.dispose(current.artifact.loader, 5000);
        onEdt(() -> {
            current.instances.forEach(plugins::remove);
            return null;
        });
    }

    private void checkDependents(Installation current) {
        for (Plugin plugin : plugins.getPlugins()) {
            if (current.instances.contains(plugin)) {
                continue;
            }
            for (PluginDependency dependency : plugin.getClass().getAnnotationsByType(PluginDependency.class)) {
                if (current.artifact.types.contains(dependency.value())) {
                    throw new IllegalStateException("Remove dependent plugin first: " + plugin.getClass().getSimpleName());
                }
            }
        }
    }

    private OperationResult scan(boolean startup) throws Exception {
        List<String> failures = new ArrayList<>();
        List<String> notices = new ArrayList<>();
        List<Path> sources;
        try (java.util.stream.Stream<Path> files = Files.list(pluginDirectory)) {
            sources = files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .sorted().collect(java.util.stream.Collectors.toList());
        }
        skipped.keySet().retainAll(new HashSet<>(sources));
        for (Path source : sources) {
            String digest = null;
            try {
                Properties origin = readOrigin(source);
                Installation existing = installed.values().stream().filter(i -> i.source.equals(source)).findFirst().orElse(null);
                digest = hash(source);
                if (existing != null && existing.digest.equals(digest)) {
                    skipped.remove(source);
                    continue;
                }
                if (digest.equals(skipped.get(source))) {
                    continue;
                }
                String hubId = origin.getProperty("hubId");
                String expectedHash = origin.getProperty("sha256");
                if (hubId == null) {
                    String recordedHub = recordedHubInstall(source, digest);
                    if (recordedHub != null) {
                        hubId = recordedHub;
                        expectedHash = digest;
                    }
                }
                OperationResult result = installNow(source, existing == null || hubId != null ? null : existing.artifact.id,
                        hubId, expectedHash, false, startup);
                if (result.success) {
                    skipped.remove(source);
                } else {
                    skipped.put(source, digest);
                    failures.add(source.getFileName() + ": " + result.message);
                    if (startup) {
                        reportBlocked(jarName(source), "", "load", result.message);
                    }
                }
            } catch (LivePluginArtifact.BlockedPluginException blocked) {
                if (digest != null) {
                    skipped.put(source, digest);
                }
                notices.add(blockedNotice(blocked, source));
                if (startup) {
                    reportBlocked(blocked.internalName, blocked.version, "compatibility check", blocked.reason);
                }
            } catch (Exception | LinkageError | AssertionError failure) {
                if (digest != null) {
                    skipped.put(source, digest);
                }
                failures.add(source.getFileName() + ": " + message(failure));
                if (startup) {
                    reportBlocked(jarName(source), "", "load", message(failure));
                }
            }
        }
        for (Installation current : new ArrayList<>(installed.values())) {
            if (!current.pendingRestart && !Files.exists(current.source)) {
                try {
                    removeNow(current, true);
                } catch (Exception | LinkageError | AssertionError failure) {
                    failures.add(message(failure));
                }
            }
        }
        if (!failures.isEmpty()) {
            failures.addAll(notices);
            return OperationResult.failure(String.join("\n", failures));
        }
        return OperationResult.success(notices.isEmpty() ? "Plugin folder refreshed."
                : "Plugin folder refreshed.\n" + String.join("\n", notices));
    }

    private static String blockedNotice(LivePluginArtifact.BlockedPluginException blocked, Path source) {
        String label = blocked.displayName + " (" + source.getFileName() + ")";
        if (blocked.reason.startsWith("requires client")) {
            return label + " needs a newer Microbot: " + blocked.reason
                    + ". Update Microbot, or remove the JAR. It is skipped until then.";
        }
        return label + " is blocked by the Plugin Hub: " + blocked.reason
                + ". Remove it in the Plugin Hub, or install a working version there. It is skipped until then.";
    }

    private void watch() {
        if (!watching || closed) {
            return;
        }
        try {
            Map<Path, String> current = new HashMap<>();
            Files.createDirectories(cacheDirectory);
            try (java.util.stream.Stream<Path> files = Files.list(pluginDirectory)) {
                for (Path path : files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                        .collect(java.util.stream.Collectors.toList())) {
                    current.put(path, hash(path));
                }
            }
            if (current.equals(observed)) {
                Map<Path, String> expected = new HashMap<>(skipped);
                for (Installation installation : installed.values()) {
                    if (!installation.pendingRestart) {
                        expected.put(installation.source, installation.digest);
                    }
                }
                if (!current.equals(expected)) {
                    OperationResult result = scan(false);
                    if (!result.success) {
                        watching = false;
                        watchStatus = watchStopped(result.message);
                        log.warn("Plugin folder watch stopped: {}", result.message);
                    }
                    publish();
                }
            }
            observed = current;
        } catch (Exception | LinkageError | AssertionError failure) {
            watching = false;
            watchStatus = watchStopped(message(failure));
            log.warn("Plugin folder watch stopped: {}", message(failure));
            publish();
        }
    }

    private void publish() {
        List<PluginEntry> next = new ArrayList<>();
        for (Installation current : installed.values()) {
            PluginDescriptor descriptor = current.artifact.types.get(0).getAnnotation(PluginDescriptor.class);
            List<String> classNames = new ArrayList<>();
            current.artifact.types.forEach(type -> classNames.add(type.getName()));
            next.add(new PluginEntry(current.artifact.id, descriptor.name(), descriptor.version(), current.source,
                    false, current.status, Collections.unmodifiableList(classNames), current.hubId));
        }
        snapshot = Collections.unmodifiableList(next);
        SwingUtilities.invokeLater(() -> events.post(new ExternalPluginsChanged()));
    }

    private Installation require(String id) {
        Installation result = installed.get(id);
        if (result == null) {
            throw new IllegalArgumentException("Plugin is not installed.");
        }
        return result;
    }

    private void checkManagedPath(Path path) {
        if (!path.toAbsolutePath().normalize().getParent().equals(pluginDirectory) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Plugin file must be directly inside the managed plugin folder.");
        }
    }

    private Path originPath(Path source) {
        return source.resolveSibling(source.getFileName() + ".origin");
    }

    private Properties readOrigin(Path source) throws Exception {
        Properties result = new Properties();
        Path origin = originPath(source);
        if (Files.exists(origin)) {
            try (InputStream input = Files.newInputStream(origin)) {
                result.load(input);
            }
        }
        return result;
    }

    private void writeOrigin(Installation current) throws Exception {
        Path target = originPath(current.source);
        checkManagedPath(target);
        if (current.hubId == null) {
            Files.deleteIfExists(target);
            return;
        }
        Properties values = new Properties();
        values.setProperty("hubId", current.hubId);
        values.setProperty("sha256", current.expectedHash);
        Path temporary = Files.createTempFile(pluginDirectory, ".origin-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "Installed Hub artifact");
            }
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void replaceFile(Path source, Path target) throws Exception {
        checkManagedPath(target);
        Path temporary = Files.createTempFile(pluginDirectory, ".install-", ".tmp");
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void move(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void release(LivePluginArtifact artifact) {
        try {
            artifact.close();
            Files.deleteIfExists(artifact.cacheFile);
        } catch (Exception | LinkageError | AssertionError failure) {
            log.debug("Deferred removal of closed plugin cache file", failure);
        }
    }

    static String hash(Path path) throws Exception {
        return com.google.common.io.Files.asByteSource(path.toFile()).hash(Hashing.sha256()).toString();
    }

    private static String configKey(Class<?> type) {
        return PluginManager.getPluginConfigurationKey(type);
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.call();
        }
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeLater(task);
        try {
            return task.get();
        } catch (java.util.concurrent.ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw failure;
        }
    }

    private static String message(Throwable failure) {
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private void checkLoadPolicy(LivePluginArtifact candidate) throws LivePluginArtifact.BlockedPluginException {
        LoadPolicy policy = loadPolicy;
        if (policy == null) {
            return;
        }
        for (Class<? extends Plugin> type : candidate.types) {
            PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
            if (!descriptor.isExternal()) {
                continue;
            }
            String reason = policy.blockReason(type.getSimpleName(), descriptor);
            if (reason != null) {
                throw new LivePluginArtifact.BlockedPluginException(type.getSimpleName(), descriptor, reason);
            }
        }
    }

    private void notifyLoaded(LivePluginArtifact artifact) {
        LoadPolicy policy = loadPolicy;
        if (policy == null) {
            return;
        }
        for (Class<? extends Plugin> type : artifact.types) {
            try {
                policy.loaded(type.getSimpleName());
            } catch (RuntimeException failure) {
                log.debug("Could not clear the load block for {}", type.getSimpleName(), failure);
            }
        }
    }

    private void reportBlocked(String internalName, String version, String phase, String reason) {
        LoadPolicy policy = loadPolicy;
        if (policy == null) {
            return;
        }
        try {
            policy.blocked(internalName, version, phase, reason);
        } catch (RuntimeException failure) {
            log.debug("Could not report the blocked plugin {}", internalName, failure);
        }
    }

    private String recordedHubInstall(Path source, String digest) {
        String name = jarName(source);
        if (!name.matches("[A-Za-z0-9_.-]+")) {
            return null;
        }
        String record;
        try {
            record = config.getConfiguration("microbotPluginVersions", "plugin." + name);
        } catch (RuntimeException failure) {
            return null;
        }
        if (record == null) {
            return null;
        }
        String[] parts = record.split(":", 2);
        return parts.length == 2 && parts[1].equalsIgnoreCase(digest) ? name : null;
    }

    private static String jarName(Path source) {
        String name = source.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".jar") ? name.substring(0, name.length() - 4) : name;
    }

    private static Set<String> entryClassNames(LivePluginArtifact artifact) {
        Set<String> names = new HashSet<>();
        artifact.types.forEach(type -> names.add(type.getName()));
        return names;
    }

    private static String displayName(Class<?> type) {
        PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
        String name = descriptor == null ? "" : net.runelite.client.util.Text.removeTags(descriptor.name()).trim();
        return name.isEmpty() ? type.getSimpleName() : name;
    }

    private static String displayName(LivePluginArtifact artifact) {
        return displayName(artifact.types.get(0));
    }

    private static String version(LivePluginArtifact artifact) {
        PluginDescriptor descriptor = artifact.types.get(0).getAnnotation(PluginDescriptor.class);
        return descriptor == null ? "" : descriptor.version();
    }

    private static String describe(LivePluginArtifact artifact) {
        String version = version(artifact);
        return displayName(artifact) + (version == null || version.isEmpty() ? "" : " " + version);
    }

    private static String watchStopped(String detail) {
        return "Folder watch stopped: " + detail + "\nFix the JAR, then tick 'Watch folder for changes' again.";
    }

    private static final class Installation {
        final LivePluginArtifact artifact;
        final Path source;
        final String digest;
        final String hubId;
        final String expectedHash;
        final List<Plugin> instances = new ArrayList<>();
        String status = "Ready";
        boolean blocked;
        boolean pendingRestart;

        Installation(LivePluginArtifact artifact, Path source, String digest, String hubId, String expectedHash) {
            this.artifact = artifact;
            this.source = source;
            this.digest = digest;
            this.hubId = hubId;
            this.expectedHash = expectedHash;
        }
    }

    @Getter
    public static final class PluginEntry {
        private final String id;
        private final String name;
        private final String version;
        private final Path sourcePath;
        private final boolean running;
        private final String status;
        private final List<String> classNames;
        private final String hubId;

        private PluginEntry(String id, String name, String version, Path sourcePath, boolean running,
                String status, List<String> classNames, String hubId) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.sourcePath = sourcePath;
            this.running = running;
            this.status = status;
            this.classNames = classNames;
            this.hubId = hubId;
        }
    }

    @Getter
    public static final class OperationResult {
        private final boolean success;
        private final String message;
        private final boolean needsRestart;

        private OperationResult(boolean success, String message, boolean needsRestart) {
            this.success = success;
            this.message = message;
            this.needsRestart = needsRestart;
        }

        public static OperationResult success(String message) {
            return new OperationResult(true, message, false);
        }

        public static OperationResult success(String message, boolean needsRestart) {
            return new OperationResult(true, message, needsRestart);
        }

        public static OperationResult failure(String message) {
            return new OperationResult(false, message, false);
        }
    }
}
