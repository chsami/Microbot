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

import com.fasterxml.jackson.databind.annotation.NoClass;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.google.common.io.Files;
import com.google.common.reflect.ClassPath;
import com.google.gson.Gson;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Module;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.microbot.Microbot;
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
import javax.swing.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URLClassLoader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotPluginManager {
    private static final File PLUGIN_DIR = new File(RuneLite.RUNELITE_DIR, "microbot-plugins");

    private final OkHttpClient okHttpClient;
    private final MicrobotPluginClient microbotPluginClient;
    private final EventBus eventBus;
    private final ScheduledExecutorService executor;
    private final PluginManager pluginManager;
    private final Gson gson;
    private final ConfigManager configManager;

    private final Map<String, URLClassLoader> loaders = new ConcurrentHashMap<>();

    @Inject
    @Named("safeMode")
    private boolean safeMode;

    private final Map<String, MicrobotPluginManifest> manifestMap = new ConcurrentHashMap<>();

    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private volatile boolean profileRefreshInProgress = false;
    private static final String PLUGIN_PACKAGE = "net.runelite.client.plugins.microbot";

    @Inject
    private MicrobotPluginManager(
            OkHttpClient okHttpClient,
            MicrobotPluginClient microbotPluginClient,
            EventBus eventBus,
            ScheduledExecutorService executor,
            PluginManager pluginManager,
            Gson gson,
            ConfigManager configManager
    ) {
        this.okHttpClient = okHttpClient;
        this.microbotPluginClient = microbotPluginClient;
        this.eventBus = eventBus;
        this.executor = executor;
        this.pluginManager = pluginManager;
        this.gson = gson;
        this.configManager = configManager;

        PLUGIN_DIR.mkdirs();
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
            for (MicrobotPluginManifest m : manifests) {
                next.put(m.getInternalName(), m);
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

    /**
     * Gets the File object for the plugin JAR file corresponding to the given internal name.
     *
     * @param internalName the internal name of the plugin
     * @return the File object representing the plugin JAR
     */
    private File getPluginJarFile(String internalName) {
        return new File(PLUGIN_DIR, internalName + ".jar");
    }

    /**
     * Gets the plugin manifest for a given plugin instance.
     *
     * @param internalName
     * @return
     */
    private byte[] getPluginJarByteArray(String internalName) {
        return new File(PLUGIN_DIR, internalName + ".jar").getPath().getBytes();
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
                .build();
    }

    /**
     * Verifies that the SHA-256 hash of a locally installed plugin matches the
     * authoritative hash from the manifest map.
     * <p>
     * This ensures the integrity of the plugin and detects tampering or corruption.
     *
     * @param internalName the internal name of the plugin to verify (must not be null or empty)
     * @return {@code true} if the plugin exists in both the local and authoritative manifests
     * and the SHA-256 hashes match, {@code false} otherwise
     * @throws IllegalArgumentException if {@code internalName} is null or empty
     */
    private boolean verifyHash(String internalName) {
        if (internalName == null || internalName.isEmpty()) {
            throw new IllegalArgumentException("Internal name is null/empty");
        }

        MicrobotPluginManifest authoritativeManifest = manifestMap.get(internalName);

        if (authoritativeManifest == null) {
            return false;
        }
        String localHash = calculateHash(internalName);
        String authoritativeHash = authoritativeManifest.getSha256();

        if (localHash.isEmpty() || authoritativeHash == null || authoritativeHash.isEmpty()) {
            return false;
        }

        var result = localHash.equals(authoritativeHash);

        if (!result) {
            log.warn("Hash mismatch for plugin {}: local={}, authoritative={}",
                    internalName, localHash, authoritativeHash);
        }
        return result;
    }

    /**
     * Calculates the SHA-256 hash of the given JAR data.
     *
     * @param interalName
     * @return
     */
    private String calculateHash(String interalName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            File file = getPluginJarFile(interalName);
            try (InputStream inputStream = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Error calculating plugin hash", e);
            return "";
        }
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
     * Loads a single plugin from the sideload folder if not already loaded.
     */
    private void loadSideLoadPlugin(String internalName) {
        File pluginFile = getPluginJarFile(internalName);
        if (!pluginFile.exists()) {
            log.debug("Plugin file {} does not exist", pluginFile);
            return;
        }
        Set<String> loadedInternalNames = pluginManager.getPlugins().stream()
                .filter(p -> p.getClass().isAnnotationPresent(PluginDescriptor.class))
                .filter(p -> p.getClass().getAnnotation(PluginDescriptor.class).isExternal())
                .map(p -> p.getClass().getSimpleName())
                .collect(Collectors.toSet());
        if (loadedInternalNames.contains(internalName)) {
            return;
        }
        try {
            if (!verifyHash(internalName)) {
                log.warn("Plugin hash verification failed for: {}", internalName);
            }
            List<Class<?>> plugins = new ArrayList<>();
            MicrobotPluginClassLoader classLoader = new MicrobotPluginClassLoader(pluginFile, getClass().getClassLoader());
            loaders.put(internalName, classLoader);
            for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {
                try {
                    Class<?> clazz = classLoader.loadClass(classInfo.getName());
                    plugins.add(clazz);
                } catch (ClassNotFoundException e) {
                    log.trace("Class not found during sideloading: {}", classInfo.getName(), e);
                } catch(Throwable t) {
                    log.error("Incompatible plugin found: " + internalName);
                }
            }
            loadPlugins(plugins, null);
        } catch (PluginInstantiationException | IOException e) {
            log.trace("Error loading side-loaded plugin!", e);
        }
    }

    public void loadSideLoadPlugins() {
        if (safeMode) {
            log.warn("Safe mode is enabled, skipping loading of sideloaded plugins.");
            return;
        }
        File[] files = createSideloadingFolder();
        if (files == null) {
            return;
        }
        Set<String> loadedInternalNames = pluginManager.getPlugins().stream()
                .filter(p -> p.getClass().isAnnotationPresent(PluginDescriptor.class))
                .filter(p -> p.getClass().getAnnotation(PluginDescriptor.class).isExternal())
                .map(p -> p.getClass().getSimpleName())
                .collect(Collectors.toSet());
        for (File f : files) {
            if (!f.getName().endsWith(".jar")) {
                continue;
            }
            String internalName = f.getName().replace(".jar", "");
            if (loadedInternalNames.contains(internalName)) {
                continue;
            }
            try {
                loadSideLoadPlugin(internalName);
            } catch (Exception exception) {
                System.out.println("Error loading side-loaded plugin: " + internalName);
            }
        }
        eventBus.post(new ExternalPluginsChanged());
    }

    /**
     * Topologically sort a graph. Uses Kahn's algorithm.
     *
     * @param graph - A directed graph
     * @param <T>   - The type of the item contained in the nodes of the graph
     * @return - A topologically sorted list corresponding to graph.
     * <p>
     * Multiple invocations with the same arguments may return lists that are not equal.
     */
    @VisibleForTesting
    static <T> List<T> topologicalSort(Graph<T> graph) {
        MutableGraph<T> graphCopy = Graphs.copyOf(graph);
        List<T> l = new ArrayList<>();
        Set<T> s = graphCopy.nodes().stream()
                .filter(node -> graphCopy.inDegree(node) == 0)
                .collect(Collectors.toSet());
        while (!s.isEmpty()) {
            Iterator<T> it = s.iterator();
            T n = it.next();
            it.remove();

            l.add(n);

            for (T m : new HashSet<>(graphCopy.successors(n))) {
                graphCopy.removeEdge(n, m);
                if (graphCopy.inDegree(m) == 0) {
                    s.add(m);
                }
            }
        }
        if (!graphCopy.edges().isEmpty()) {
            throw new RuntimeException("Graph has at least one cycle");
        }
        return l;
    }

    private List<Plugin> loadPlugins(List<Class<?>> plugins, BiConsumer<Integer, Integer> onPluginLoaded) throws PluginInstantiationException {
        MutableGraph<Class<? extends Plugin>> graph = GraphBuilder
                .directed()
                .build();

        Set<Class<?>> alreadyLoaded = pluginManager.getPlugins().stream()
                .map(Object::getClass)
                .collect(Collectors.toSet());

        for (Class<?> clazz : plugins) {
            if (alreadyLoaded.contains(clazz)) {
                log.debug("Plugin {} is already loaded, skipping duplicate.", clazz.getSimpleName());
                continue;
            }
            PluginDescriptor pluginDescriptor = clazz.getAnnotation(PluginDescriptor.class);

            if (pluginDescriptor == null) {
                if (clazz.getSuperclass() == Plugin.class) {
                    log.error("Class {} is a plugin, but has no plugin descriptor", clazz);
                }
                continue;
            }

            if (clazz.getSuperclass() != Plugin.class) {
                log.error("Class {} has plugin descriptor, but is not a plugin", clazz);
                continue;
            }

            if (pluginDescriptor.isExternal() && !Rs2UiHelper.isClientVersionCompatible(pluginDescriptor.minClientVersion())) {
                log.error("Plugin {} requires client version {} or higher, but current version is {}. Skipping plugin loading.",
                        clazz.getSimpleName(), pluginDescriptor.minClientVersion(), RuneLiteProperties.getMicrobotVersion());
                continue;
            }

            if (pluginDescriptor.disable()) {
                log.error("Plugin {} has been disabled upstream", clazz.getSimpleName());
                continue;
            }

            graph.addNode((Class<Plugin>) clazz);
        }

        for (Class<? extends Plugin> pluginClazz : graph.nodes()) {
            PluginDependency[] pluginDependencies = pluginClazz.getAnnotationsByType(PluginDependency.class);

            for (PluginDependency pluginDependency : pluginDependencies) {
                if (graph.nodes().contains(pluginDependency.value())) {
                    graph.putEdge(pluginDependency.value(), pluginClazz);
                }
            }
        }

        if (Graphs.hasCycle(graph)) {
            throw new PluginInstantiationException("Plugin dependency graph contains a cycle!");
        }

        List<Class<? extends Plugin>> sortedPlugins = topologicalSort(graph);

        int loaded = 0;
        List<Plugin> newPlugins = new ArrayList<>();
        for (Class<? extends Plugin> pluginClazz : sortedPlugins) {
            Plugin plugin;
            try {
                plugin = instantiate(pluginManager.getPlugins(), (Class<Plugin>) pluginClazz);
                log.info("Plugin loaded {}", plugin.getClass().getSimpleName());
                newPlugins.add(plugin);
                pluginManager.addPlugin(plugin);
                loaded++;
            } catch (PluginInstantiationException ex) {
                log.error("Error instantiating plugin!", ex);
            }

            if (onPluginLoaded != null) {
                onPluginLoaded.accept(loaded, sortedPlugins.size());
            }
        }

        return newPlugins;
    }

    private Plugin instantiate(Collection<Plugin> scannedPlugins, Class<Plugin> clazz) throws PluginInstantiationException {
        PluginDependency[] pluginDependencies = clazz.getAnnotationsByType(PluginDependency.class);
        List<Plugin> deps = new ArrayList<>();
        for (PluginDependency pluginDependency : pluginDependencies) {
            Optional<Plugin> dependency = scannedPlugins.stream().filter(p -> p.getClass() == pluginDependency.value()).findFirst();
            if (!dependency.isPresent()) {
                throw new PluginInstantiationException("Unmet dependency for " + clazz.getSimpleName() + ": " + pluginDependency.value().getSimpleName());
            }
            deps.add(dependency.get());
        }

        Plugin plugin;
        try {
            plugin = clazz.getDeclaredConstructor().newInstance();
        } catch (ThreadDeath e) {
            throw e;
        } catch (Throwable ex) {
            throw new PluginInstantiationException(ex);
        }

        try {
            Injector parent = Microbot.getInjector();

            if (deps.size() > 1) {
                List<com.google.inject.Module> modules = new ArrayList<>(deps.size());
                for (Plugin p : deps) {
                    com.google.inject.Module module = (Binder binder) ->
                    {
                        binder.bind((Class<Plugin>) p.getClass()).toInstance(p);
                        binder.install(p);
                    };
                    modules.add(module);
                }

                parent = parent.createChildInjector(modules);
            } else if (!deps.isEmpty()) {
                parent = deps.get(0).getInjector();
            }

            Module pluginModule = (Binder binder) ->
            {
                binder.bind(clazz).toInstance(plugin);
                binder.install(plugin);
            };
            Injector pluginInjector = parent.createChildInjector(pluginModule);
            System.out.println(pluginInjector.getClass().getSimpleName());
            plugin.setInjector(pluginInjector);
        } catch (com.google.common.util.concurrent.ExecutionError e) {
            // Guice/Guava wraps NoClassDefFoundError here
            Throwable cause = e.getCause();
            if (cause instanceof NoClassDefFoundError) {
                log.error("Missing class while loading plugin {}: {}", clazz.getSimpleName(), cause.toString());
            } else {
                log.error("Error while loading plugin {}: {}", clazz.getSimpleName(), e.toString(), e);
            }

            File jar = getPluginJarFile(plugin.getClass().getSimpleName());
            if (jar != null) {
                jar.delete();
            }
        } catch (Exception ex) {
            log.error("Incompatible plugin found: " + clazz.getSimpleName());
            File jar = getPluginJarFile(plugin.getClass().getSimpleName());
            jar.delete();
        }

        log.debug("Loaded plugin {}", clazz.getSimpleName());
        return plugin;
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
        log.info("Client shutdown detected, stopping all Microbot plugins");
        shutdown();
    }

    /**
     * Refreshes plugins
     */
    private void refresh() {
        if (safeMode) {
            log.warn("Safe mode is enabled, skipping loading of sideloaded plugins.");
            return;
        }

        if (isShuttingDown.get()) {
            return;
        }

        synchronized (this) {
            if (profileRefreshInProgress) {
                return;
            }
            profileRefreshInProgress = true;
        }

        try {
            log.debug("Starting plugin refresh");

            List<Plugin> installedPlugins = getInstalledPlugins();

            List<Plugin> disabledPlugins = installedPlugins.stream()
                    .filter(plugin -> {
                        MicrobotPluginManifest upstreamManifest = manifestMap.get(plugin.getClass().getSimpleName());
                        return upstreamManifest != null && upstreamManifest.isDisable();
                    })
                    .collect(Collectors.toList());

            if (!disabledPlugins.isEmpty()) {
                log.warn("Found {} disabled plugin(s) that have been disabled upstream:", disabledPlugins.size());
                for (Plugin disabledPlugin : disabledPlugins) {
                    log.warn("  - Plugin '{}' has been disabled upstream and will be removed from your installed plugins",
                            disabledPlugin.getClass().getSimpleName());
                }

                List<Plugin> enabledPlugins = installedPlugins.stream()
                        .filter(plugin -> {
                            MicrobotPluginManifest upstreamManifest = manifestMap.get(plugin.getClass().getSimpleName());
                            return upstreamManifest == null || !upstreamManifest.isDisable();
                        })
                        .collect(Collectors.toList());

                installedPlugins = enabledPlugins;

                log.info("Automatically removed {} disabled plugin(s) from your installed plugins list", disabledPlugins.size());
            }

            Set<String> installedNames = installedPlugins.stream()
                    .map(plugin -> plugin.getClass().getSimpleName())
                    .collect(Collectors.toSet());

            List<Plugin> allLoadedPlugins = new ArrayList<>(pluginManager.getPlugins());

            List<Plugin> loadedExternalPlugins = allLoadedPlugins.stream()
                    .filter(plugin -> {
                        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                        if (descriptor == null || !descriptor.isExternal()) {
                            return false;
                        }
                        String packageName = plugin.getClass().getPackage().getName();
                        return packageName.contains("microbot");
                    })
                    .collect(Collectors.toList());

            Set<String> loadedByInternalName = loadedExternalPlugins.stream()
                    .map(this::getPluginManifest) // your helper
                    .filter(Objects::nonNull)
                    .map(MicrobotPluginManifest::getInternalName)
                    .collect(Collectors.toSet());

            log.info("Profile refresh - Installed plugins: {}, Currently loaded Microbot plugins: {}",
                    installedNames, loadedByInternalName);

            log.debug("All loaded plugins ({}):", allLoadedPlugins.size());
            for (Plugin plugin : allLoadedPlugins) {
                PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                boolean isExternal = descriptor != null && descriptor.isExternal();
                MicrobotPluginManifest manifest = getPluginManifest(plugin);
                log.debug("  - {} (external: {}, has manifest: {})",
                        plugin.getClass().getSimpleName(), isExternal, manifest != null);
            }

            // TODO: current code will redownload all plugins, because manifestMap is a manifest of all the plugins on the hub
            // we need to filter it down to only the ones the user has installed
            // what i think we need to do:
            // create a filtered down list of manifestmap that contains only  the installed plugins
            // add your "install" plugin to this list, then use this list here
            var userManifestMap = manifestMap.entrySet().stream()
                    .filter(e -> installedNames.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


            Set<String> needsDownload = userManifestMap.keySet().stream()
                    .filter(microbotPluginManifest -> !getPluginJarFile(microbotPluginManifest).exists())
                    .collect(Collectors.toSet());

            Set<String> needsRedownload = userManifestMap.keySet().stream()
                    .filter(pluginName -> {
                        File pluginFile = getPluginJarFile(pluginName);
                        if (!pluginFile.exists()) {
                            return false;
                        }
                        if (!verifyHash(pluginName)) {
                            log.info("Hash verification failed for plugin: {}. Marking for redownload.", pluginName);
                            if (pluginFile.delete()) {
                                log.info("Deleted outdated plugin file: {}", pluginFile.getClass().getSimpleName());
                            } else {
                                log.warn("Failed to delete outdated plugin file: {}", pluginFile.getAbsolutePath());
                            }
                            return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toSet());

            needsDownload.addAll(needsRedownload);

            Set<String> needsReload = new HashSet<>(needsDownload);

            Set<File> keepFiles = userManifestMap.keySet().stream()
                    .map(this::getPluginJarFile)
                    .filter(File::exists)
                    .collect(Collectors.toSet());

            Instant now = Instant.now();
            Instant keepAfter = now.minus(3, ChronoUnit.DAYS);

            Optional.ofNullable(PLUGIN_DIR.listFiles((dir, name) -> name.endsWith(".jar"))).stream()
                    .flatMap(Arrays::stream)
                    .filter(file -> !keepFiles.contains(file) && file.lastModified() < keepAfter.toEpochMilli())
                    .forEach(file -> {
                        log.info("Cleaning up old plugin file (>3 days): {}", file.getName());
                        if (!file.delete()) {
                            log.warn("Failed to delete old plugin file: {}", file.getAbsolutePath());
                        }
                    });

            for (String pluginName : needsDownload) {
                log.info("Downloading missing plugin: {}", pluginName);
                downloadPlugin(pluginName);
            }

            Set<String> installedPluginNames = userManifestMap.values().stream()
                    .map(MicrobotPluginManifest::getInternalName)
                    .collect(Collectors.toSet());

            Set<MicrobotPluginManifest> toAdd = userManifestMap.values().stream()
                    .filter(m -> needsReload.contains(m.getInternalName())
                            || !loadedByInternalName.contains(m.getInternalName()))
                    .collect(Collectors.toSet());

            List<Plugin> toRemove = loadedExternalPlugins.stream()
                    .filter(p -> {
                        MicrobotPluginManifest m = getPluginManifest(p);
                        if (m == null) return true; // unknown → remove
                        String name = m.getInternalName();
                        return !installedPluginNames.contains(name) || needsReload.contains(name);
                    })
                    .collect(Collectors.toList());

            log.info("Plugin refresh - Will add: {} plugins, Will remove: {} plugins",
                    toAdd.stream().map(MicrobotPluginManifest::getInternalName).collect(Collectors.toSet()),
                    toRemove.stream().map(p -> p.getClass().getSimpleName()).collect(Collectors.toSet()));

            for (Plugin plugin : toRemove) {
                String simple = plugin.getClass().getSimpleName();
                log.info("Stopping plugin \"{}\"", simple);
                try {
                    SwingUtilities.invokeAndWait(() -> stopPlugin(plugin));
                } catch (InterruptedException | InvocationTargetException e) {
                    log.warn("Failed to stop plugin {}", simple, e);
                }
            }


            for (MicrobotPluginManifest manifest : toAdd) {
                String pluginName = manifest.getInternalName();
                File pluginFile = getPluginJarFile(pluginName);
                if (!pluginFile.exists()) {
                    log.warn("Plugin file missing for {}, skipping load", pluginName);
                    continue;
                }

                log.info("Loading plugin \"{}\"", pluginName);
                List<Plugin> newPlugins = null;
                MicrobotPluginClassLoader classLoader = null;
                try {
                    if (!verifyHash(pluginName)) {
                        log.warn("Plugin hash verification failed for: {}. The installed version may be outdated or from a different source.", pluginName);
                        continue;
                    }

                    List<Class<?>> pluginClasses = new ArrayList<>();
                    classLoader = new MicrobotPluginClassLoader(pluginFile, getClass().getClassLoader());

                    for (ClassPath.ClassInfo classInfo : ClassPath.from(classLoader).getAllClasses()) {
                        try {
                            Class<?> clazz = classLoader.loadClass(classInfo.getName());
                            pluginClasses.add(clazz);
                        } catch (ClassNotFoundException e) {
                            log.trace("Class not found during plugin loading: {}", classInfo.getName(), e);
                        } catch(Throwable t) {
                            log.error("Incompatible plugin found: " + pluginName);
                        }
                    }

                    newPlugins = loadPlugins(pluginClasses, null);

                    boolean startup = SplashScreen.isOpen();
                    if (!startup && !newPlugins.isEmpty()) {
                        pluginManager.loadDefaultPluginConfiguration(newPlugins);
                        final List<Plugin> pluginsToStart = newPlugins;
                        SwingUtilities.invokeAndWait(() -> {
                            try {
                                for (Plugin p : pluginsToStart) {
                                    pluginManager.startPlugin(p);
                                }
                            } catch (PluginInstantiationException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                    log.info("Successfully loaded plugin: {}", pluginName);
                } catch (ThreadDeath e) {
                    throw e;
                } catch (Throwable e) {
                    log.warn("Unable to load or start plugin \"{}\"", pluginName, e);
                }
            }

            if (!toAdd.isEmpty() || !toRemove.isEmpty()) {
                eventBus.post(new ExternalPluginsChanged());
            }
            log.info("Completed plugin refresh - Added: {}, Removed: {}", toAdd.size(), toRemove.size());
        } catch (Exception e) {
            log.error("Error during plugin refresh", e);
        } finally {
            profileRefreshInProgress = false;
        }
    }

    /**
     * Downloads a plugin JAR file from the remote server.
     *
     * @param internalName the internal name of the plugin to download
     * @return true if the plugin was successfully downloaded, false otherwise
     */
    private boolean downloadPlugin(String internalName) {
        MicrobotPluginManifest manifest = manifestMap.get(internalName);
        if (manifest == null) {
            log.error("Cannot download plugin {}: manifest not found", internalName);
            return false;
        }

        try {
            File pluginFile = getPluginJarFile(internalName);

            HttpUrl jarUrl = microbotPluginClient.getJarURL(manifest);
            if (jarUrl == null || !jarUrl.isHttps()) {
                log.error("Invalid JAR URL for plugin {}", internalName);
                return false;
            }

            OkHttpClient clientWithoutProxy = noProxy(okHttpClient);
            Request request = new Request.Builder()
                    .url(jarUrl)
                    .build();

            try (Response response = clientWithoutProxy.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Failed to download plugin {}: HTTP {}", internalName, response.code());
                    return false;
                }

                byte[] jarData = response.body().bytes();

                Files.write(jarData, pluginFile);
                log.info("Plugin {} downloaded to {}", internalName, pluginFile.getAbsolutePath());
                return true;
            }

        } catch (Exception e) {
            log.error("Failed to download plugin {}", internalName, e);

            File pluginFile = getPluginJarFile(internalName);
            if (pluginFile.exists() && !pluginFile.delete()) {
                log.warn("Failed to delete corrupted plugin file: {}", pluginFile.getAbsolutePath());
            }
            return false;
        }
    }

    /**
     * Installs a plugin and triggers UI refresh.
     *
     * @param manifest the manifest of the plugin to install
     */
    public void installPlugin(MicrobotPluginManifest manifest) {
        executor.submit(() -> install(manifest));
    }

    /**
     * Removes a plugin and triggers UI refresh.
     *
     * @param manifest the manifest of the plugin to remove
     */
    public void removePlugin(MicrobotPluginManifest manifest) {
        executor.submit(() -> remove(manifest));
    }

    /**
     * Installs a plugin by adding it to the installed plugins list in config.
     *
     * @param manifest the manifest of the plugin to install
     */
    public void install(MicrobotPluginManifest manifest) {
        if (manifest == null || !manifestMap.containsValue(manifest)) {
            log.error("Can't install plugin: unable to identify manifest");
            return;
        }

        final String internalName = manifest.getInternalName();
        if (internalName == null || internalName.isEmpty()) {
            log.error("Cannot install plugin: internal name is null or empty");
            return;
        }

        if (manifest.isDisable()) {
            log.warn("Cannot install plugin '{}' ({}): This plugin has been disabled upstream by the developers. " +
                            "This usually means the plugin is no longer functional, has security issues, or has been deprecated.",
                    manifest.getDisplayName(), internalName);
            return;
        }

        var result = downloadPlugin(internalName);
        if (result) {
            //verifiy hash inside loadSidePlugin doesn't work
            loadSideLoadPlugin(internalName);
        }

        log.info("Added plugin {} to installed list", manifest.getDisplayName());
        eventBus.post(new ExternalPluginsChanged());
    }

    /**
     * Removes a plugin by removing it from the installed plugins list in config.
     *
     * @param manifest the manifest of the plugin to remove
     */
    public void remove(MicrobotPluginManifest manifest) {
        if (manifest == null || !manifestMap.containsValue(manifest)) {
            log.error("Can't install plugin: unable to identify manifest");
            return;
        }

        final String internalName = manifest.getInternalName();
        if (internalName == null || internalName.isEmpty()) {
            log.error("Cannot install plugin: internal name is null or empty");
            return;
        }

        if (manifest.isDisable()) {
            log.warn("Cannot install plugin '{}' ({}): This plugin has been disabled upstream by the developers. " +
                            "This usually means the plugin is no longer functional, has security issues, or has been deprecated.",
                    manifest.getDisplayName(), internalName);
            return;
        }

        var pluginToRemove = pluginManager.getPlugins().stream().filter(x -> x.getClass().getSimpleName().equalsIgnoreCase(internalName)).findFirst();
        if (pluginToRemove.isPresent()) {
            URLClassLoader cl = loaders.remove(internalName);
            if (cl == null) return;

            var plugin = pluginToRemove.get();
            try {
                SwingUtilities.invokeAndWait(() ->
                {
                    try {
                        pluginManager.stopPlugin(plugin);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }

            pluginManager.remove(plugin);

            try {
                cl.close();
            } catch (Exception ignored) {
            }
            File jar = getPluginJarFile(internalName);
            jar.delete();
        } else {
            log.warn("Plugin to remove not found in plugin manager: {}", internalName);
        }

        log.info("Added plugin {} to installed list", manifest.getDisplayName());
        eventBus.post(new ExternalPluginsChanged());
    }

    /**
     * Updates a plugin by redownloading and reloading it.
     *
     * @param manifest
     */
    public void update(MicrobotPluginManifest manifest) {
        // remove and download the new one
        remove(manifest);
        install(manifest);
    }

    /**
     * @return
     * @apiNote
     */
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
     * Submits a plugin refresh task to the executor.
     * This will reload plugins based on the current profile's installed plugins list.
     */
    public void updatePlugin(MicrobotPluginManifest manifest) {
        executor.submit(() -> {
            update(manifest);
        });
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

    /**
     * Gracefully stops a plugin
     */
    private void stopPlugin(Plugin plugin) {
        String pluginName = plugin.getClass().getSimpleName();

        try {
            if (pluginManager.isPluginActive(plugin)) {
                if (SwingUtilities.isEventDispatchThread()) {
                    pluginManager.stopPlugin(plugin);
                    return;
                } else  {
                    SwingUtilities.invokeAndWait(() -> {
                        try {
                            pluginManager.stopPlugin(plugin);
                        } catch (PluginInstantiationException e) {
                            log.warn("Error stopping plugin {}: {}", pluginName, e.getMessage());
                        }
                    });
                }
            }
            pluginManager.remove(plugin);
        } catch (Exception e) {
            log.warn("Error during plugin stop for {}: {}", pluginName, e.getMessage());
        }
    }

    /**
     * Gracefully shuts down the plugin manager and performs final cleanup.
     */
    private void shutdown() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            return;
        }

        log.info("Shutting down MicrobotPluginManager");

        try {
            List<Plugin> externalPlugins = pluginManager.getPlugins().stream()
                    .filter(plugin -> {
                        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                        return descriptor != null && descriptor.isExternal();
                    })
                    .collect(Collectors.toList());

            for (Plugin plugin : externalPlugins) {
                stopPlugin(plugin);
            }

            log.info("MicrobotPluginManager shutdown complete");
        } catch (Exception e) {
            log.error("Error during MicrobotPluginManager shutdown", e);
        }
    }
}
