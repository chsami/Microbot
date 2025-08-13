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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Module;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.SplashScreen;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class MicrobotPluginManager
{
    private static final File PLUGIN_DIR = new File(RuneLite.RUNELITE_DIR, "microbot-plugins");
    private static final File PLUGIN_LIST = new File(PLUGIN_DIR, "plugins.json");

    private final OkHttpClient okHttpClient;
    private final MicrobotPluginClient microbotPluginClient;
    private final EventBus eventBus;
    private final ScheduledExecutorService executor;
    private final PluginManager pluginManager;
    private final Gson gson;

    @Inject
    private MicrobotPluginManager(
        OkHttpClient okHttpClient,
        MicrobotPluginClient microbotPluginClient,
        EventBus eventBus,
        ScheduledExecutorService executor,
        PluginManager pluginManager,
        Gson gson)
    {
        this.okHttpClient = okHttpClient;
        this.microbotPluginClient = microbotPluginClient;
        this.eventBus = eventBus;
        this.executor = executor;
        this.pluginManager = pluginManager;
        this.gson = gson;

        PLUGIN_DIR.mkdirs();

        if (!PLUGIN_LIST.exists())
        {
            try
            {
                PLUGIN_LIST.createNewFile();
                Files.asCharSink(PLUGIN_LIST, StandardCharsets.UTF_8).write("[]");
            }
            catch (IOException e)
            {
                log.error("Unable to create Microbot plugin list", e);
            }
        }
    }

    public List<String> getInstalledPlugins()
    {
        List<String> plugins = new ArrayList<>();
        try (FileReader reader = new FileReader(PLUGIN_LIST))
        {
            plugins = gson.fromJson(reader, new TypeToken<List<String>>(){}.getType());
            if (plugins == null)
            {
                plugins = new ArrayList<>();
            }
        }
        catch (IOException | com.google.gson.JsonSyntaxException e)
        {
            log.error("Error reading Microbot plugin list", e);
        }
        return plugins;
    }

    public void saveInstalledPlugins(List<String> plugins)
    {
        try
        {
            Files.asCharSink(PLUGIN_LIST, StandardCharsets.UTF_8).write(gson.toJson(plugins));
        }
        catch (IOException e)
        {
            log.error("Error writing Microbot plugin list", e);
        }
    }

    private File getPluginJarFile(String internalName)
    {
        return new File(PLUGIN_DIR, internalName + ".jar");
    }

    public void install(MicrobotPluginManifest manifest)
    {
        executor.execute(() -> {
            try
            {
                HttpUrl url = microbotPluginClient.getJarURL(manifest);
                if (url == null)
                {

                    log.error("Invalid URL for plugin: {}", manifest.getInternalName());
                    return;
                }

                Request request = new Request.Builder()
                    .url(url)
                    .build();

                try (Response response = okHttpClient.newCall(request).execute())
                {
                    if (!response.isSuccessful())
                    {
                        log.error("Error downloading plugin: {}, code: {}", manifest.getInternalName(), response.code());
                        return;
                    }

                    byte[] jarData = response.body().bytes();

                    // Verify the SHA-256 hash
                    if (!verifyHash(jarData, manifest.getSha256()))
                    {
                        log.error("Plugin hash verification failed for: {}", manifest.getInternalName());
                        return;
                    }

                    // Save the jar file
                    File pluginFile = getPluginJarFile(manifest.getInternalName());
                    Files.write(jarData, pluginFile);

                    List<String> plugins = getInstalledPlugins();
                    if (!plugins.contains(manifest.getInternalName()))
                    {
                        plugins.add(manifest.getInternalName());
                        saveInstalledPlugins(plugins);
                    }

                    loadSideLoadPlugins();

                }
            }
            catch (IOException e)
            {
                log.error("Error installing plugin: {}", manifest.getInternalName(), e);
            }
        });
    }

    public void remove(String internalName)
    {
        executor.execute(() -> {
            // Remove the jar file
            File pluginFile = getPluginJarFile(internalName);
            if (pluginFile.exists() && !pluginFile.delete())
            {
                log.warn("Could not delete plugin file: {}", pluginFile);
            }

            pluginManager.remove(pluginManager.getPlugins().stream()
                    .filter(x ->
                            x.getClass().getSimpleName().equals(internalName)
                                    && x.getClass().getAnnotation(PluginDescriptor.class).isExternal())
                    .findFirst()
                    .orElse(null));

            // Update installed plugins list
            List<String> plugins = getInstalledPlugins();
            if (plugins.contains(internalName))
            {
                plugins.remove(internalName);
                saveInstalledPlugins(plugins);
            }

            // Notify for plugin change
            eventBus.post(new ExternalPluginsChanged());
        });
    }

    private boolean verifyHash(byte[] jarData, String expectedHash)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jarData);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash)
            {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            return hexString.toString().equals(expectedHash);
        }
        catch (NoSuchAlgorithmException e)
        {
            log.error("Error verifying plugin hash", e);
            return false;
        }
    }
    public static File[] createSideloadingFolder() {
        final File MICROBOT_PLUGINS = new File(RuneLite.RUNELITE_DIR, "microbot-plugins");
        if (!java.nio.file.Files.exists(MICROBOT_PLUGINS.toPath())) {
            try {
                java.nio.file.Files.createDirectories(MICROBOT_PLUGINS.toPath());
                System.out.println("Directory for sideloading was created successfully.");
                return MICROBOT_PLUGINS.listFiles();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return MICROBOT_PLUGINS.listFiles();
    }

    /**
     * Load plugins from the sideloading folder, matching the provided jar names.
     */
    public void loadSideLoadPlugins() {
        File[] files = createSideloadingFolder();
        if (files == null)
        {
            return;
        }

        for (File f : files)
        {
            var installedPlugins = getInstalledPlugins();

            var match = installedPlugins.stream()
                    .filter(x -> x.equals(f.getName().replace(".jar", "")))
                    .findFirst();

            if (!match.isPresent())
            {
                continue; // Skip if the plugin is not in the installed list
            }

            if (f.getName().endsWith(".jar"))
            {
                log.info("Side-loading plugin " + f.getName());

                try
                {
                    byte[] fileBytes = Files.toByteArray(f);

                    List<Class<?>> plugins = new ArrayList<>();

                    MicrobotPluginClassLoader classLoader = new MicrobotPluginClassLoader(fileBytes, getClass().getClassLoader());

                    // Assuming you know the class names you want to load
                    Set<String> classNamesToLoad = classLoader.getLoadedClassNames();

                    for (String className : classNamesToLoad) {
                        try {
                            Class<?> clazz = classLoader.loadClass(className);
                            plugins.add(clazz);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }

                    loadPlugins(plugins, null);

                    eventBus.post(new ExternalPluginsChanged());

                }
                catch (PluginInstantiationException | IOException ex)
                {
                    System.out.println("error sideloading plugin " + ex);
                }
            }
        }
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

    public List<Plugin> loadPlugins(List<Class<?>> plugins, BiConsumer<Integer, Integer> onPluginLoaded) throws PluginInstantiationException {
        MutableGraph<Class<? extends Plugin>> graph = GraphBuilder
                .directed()
                .build();

        for (Class<?> clazz : plugins) {
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

            graph.addNode((Class<Plugin>) clazz);
        }

        // Build plugin graph
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
                log.info("Microbot pluginManager loaded " + plugin.getName());
                newPlugins.add(plugin);
                pluginManager.addPlugin(plugin);
            } catch (PluginInstantiationException ex) {
                log.error("Error instantiating plugin!", ex);
            }

            loaded++;
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
                    // Create a module for each dependency
                    com.google.inject.Module module = (Binder binder) ->
                    {
                        binder.bind((Class<Plugin>) p.getClass()).toInstance(p);
                        binder.install(p);
                    };
                    modules.add(module);
                }

                // Create a parent injector containing all of the dependencies
                parent = parent.createChildInjector(modules);
            } else if (!deps.isEmpty()) {
                // With only one dependency we can simply use its injector
                parent = deps.get(0).getInjector();
            }

            // Create injector for the module
            Module pluginModule = (Binder binder) ->
            {
                // Since the plugin itself is a module, it won't bind itself, so we'll bind it here
                binder.bind(clazz).toInstance(plugin);
                binder.install(plugin);
            };
            Injector pluginInjector = parent.createChildInjector(pluginModule);
            plugin.setInjector(pluginInjector);
        } catch (CreationException ex) {
            throw new PluginInstantiationException(ex);
        }

        log.debug("Loaded plugin {}", clazz.getSimpleName());
        return plugin;
    }

    public void loadCorePlugins(List<Class<?>> plugins) throws IOException, PluginInstantiationException
    {
        SplashScreen.stage(.59, null, "Loading plugins");

        loadPlugins(plugins, (loaded, total) ->
                SplashScreen.stage(.60, .70, null, "Loading plugins", loaded, total, false));
    }
}
