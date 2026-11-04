/*
 * Copyright (c) 2016-2017, Adam <Adam@sigterm.info>
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
package net.runelite.client.plugins;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.graph.Graph;
import com.google.common.graph.GraphBuilder;
import com.google.common.graph.Graphs;
import com.google.common.graph.MutableGraph;
import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import com.google.inject.Module;
import com.google.inject.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.plugins.microbot.AlwaysOnPlugins;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.externalplugins.PluginJarClassLoader;
import net.runelite.client.plugins.microbot.recovery.StartupRecovery;
import net.runelite.client.task.Scheduler;
import net.runelite.client.ui.SplashScreen;
import net.runelite.client.util.GameEventManager;
import net.runelite.client.util.ReflectUtil;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Singleton
@Slf4j
public class PluginManager {
    /**
     * Base package where the core plugins are
     */
    private static final String PLUGIN_PACKAGE = "net.runelite.client.plugins";
    private static final File SIDELOADED_PLUGINS = new File(RuneLite.RUNELITE_DIR, "sideloaded-plugins");

    private final boolean safeMode;
    private final EventBus eventBus;
    private final Scheduler scheduler;
    private final ConfigManager configManager;
    private final Provider<GameEventManager> sceneTileManager;
    private final PluginModuleFactory pluginModuleFactory;
    private final List<Plugin> plugins = new CopyOnWriteArrayList<>();
    private final Set<Plugin> unloadingPlugins = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Plugin equality is final and identity-based; keep failures even after activePlugins removes it.
    private final Map<Plugin, Throwable> shutdownFailures = new ConcurrentHashMap<>();
    @Getter
    private final List<Plugin> activePlugins = new CopyOnWriteArrayList<>();

    public void addPlugin(Plugin plugin) {
        plugins.add(plugin);
    }

    @Inject
    @VisibleForTesting
    PluginManager(
            @Named("safeMode") final boolean safeMode,
            final EventBus eventBus,
            final Scheduler scheduler,
            final ConfigManager configManager,
            final Provider<GameEventManager> sceneTileManager,
            final PluginModuleFactory pluginModuleFactory) {
        this.safeMode = safeMode;
        this.eventBus = eventBus;
        this.scheduler = scheduler;
        this.configManager = configManager;
        this.sceneTileManager = sceneTileManager;
        this.pluginModuleFactory = pluginModuleFactory;
    }

    @Subscribe
    public void onProfileChanged(ProfileChanged profileChanged) {
        refreshPlugins();
    }

    private void refreshPlugins() {
        loadDefaultPluginConfiguration(null);
        SwingUtilities.invokeLater(() ->
        {
            for (Plugin plugin : getPlugins()) {
                try {
                    if (isPluginEnabled(plugin) != activePlugins.contains(plugin)) {
                        if (activePlugins.contains(plugin)) {
                            stopPlugin(plugin);
                        } else {
                            startPlugin(plugin);
                        }
                    }
                } catch (PluginInstantiationException e) {
                    log.error("Error during starting/stopping plugin {}", plugin.getClass().getSimpleName(), e);
                }
            }
        });
    }

    public Config getPluginConfigProxy(Plugin plugin) {
        try {
            final Injector injector = plugin.getInjector();

            for (Key<?> key : injector.getBindings().keySet()) {
                Class<?> type = key.getTypeLiteral().getRawType();
                if (Config.class.isAssignableFrom(type)) {
                    return (Config) injector.getInstance(key);
                }
            }
        } catch (ThreadDeath e) {
            throw e;
        } catch (Throwable e) {
            log.error("Unable to get plugin config", e);
        }
        return null;
    }

    public List<Config> getPluginConfigProxies(Collection<Plugin> plugins) {
        List<Injector> injectors = new ArrayList<>();
        if (plugins == null) {
            injectors.add(Microbot.getInjector());
            plugins = getPlugins();
        }
        plugins.forEach(pl -> injectors.add(pl.getInjector()));

        List<Config> list = new ArrayList<>();
        for (Injector injector : injectors) {
            for (Key<?> key : injector.getBindings().keySet()) {
                Class<?> type = key.getTypeLiteral().getRawType();
                if (Config.class.isAssignableFrom(type)) {
                    Config config = (Config) injector.getInstance(key);
                    list.add(config);
                }
            }
        }

        return list;
    }

    public void loadDefaultPluginConfiguration(Collection<Plugin> plugins) {
        try {
            for (Config config : getPluginConfigProxies(plugins)) {
                configManager.setDefaultConfiguration(config, false);
            }
        } catch (ThreadDeath e) {
            throw e;
        } catch (Throwable ex) {
            log.error("Unable to reset plugin configuration", ex);
        }
    }

    public void startPlugins() {
        List<Plugin> scannedPlugins = new ArrayList<>(plugins);
        scannedPlugins.sort(Comparator.comparingInt(p ->
        {
            final PluginDescriptor pluginDescriptor = p.getClass().getAnnotation(PluginDescriptor.class);
            if (pluginDescriptor == null) {
                return Integer.MAX_VALUE;
            }
            return pluginDescriptor.priority() ? 0 : 1;
        }));
        int loaded = 0;
        for (Plugin plugin : scannedPlugins) {
            try {
                Runnable runnable = () -> {
                    try {
                            startPlugin(plugin);
                        } catch (PluginInstantiationException ex) {
                            log.error("Unable to start plugin {}", plugin.getClass().getSimpleName(), ex);
                        }
                };
                if (SwingUtilities.isEventDispatchThread()) {
                    runnable.run();
                }else{
                    SwingUtilities.invokeAndWait(() ->
                    {
                    runnable.run();

                    });
                }
            } catch (InterruptedException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }

            loaded++;
            SplashScreen.stage(.80, 1, null, "Starting plugins", loaded, scannedPlugins.size(), false);
        }

		for (Plugin plugin : plugins) {
			try {
				ReflectUtil.queueInjectorAnnotationCacheInvalidation(plugin.injector);
			} catch(Exception e) {
				log.error("Unable to start plugin {}", plugin.getClass().getSimpleName(), e);
				activePlugins.remove(plugin);
				remove(plugin);
				setPluginEnabled(plugin, false);
			}
		}
	}

    /**
     * Loads core RuneLite plugins, excluding any Microbot-related plugins.
     * This method filters out plugins from the microbot package hierarchy.
     */
    public void loadCoreRunelitePlugins() throws IOException, PluginInstantiationException {
        SplashScreen.stage(.59, null, "Loading core RuneLite plugins");
        ClassPath classPath = ClassPath.from(getClass().getClassLoader());

        List<Class<?>> plugins = classPath.getTopLevelClassesRecursive(PLUGIN_PACKAGE).stream()
                .map(ClassInfo::load)
                .filter(clazz -> !isMicrobotRelatedClass(clazz))
                .collect(Collectors.toList());

        loadPlugins(plugins, (loaded, total) ->
                SplashScreen.stage(.60, .70, null, "Loading core RuneLite plugins", loaded, total, false));
    }

    /**
     * Determines if a class is related to Microbot and should be excluded from core RuneLite plugin loading.
     *
     * @param clazz the class to check
     * @return true if the class is Microbot-related and should be filtered out
     */
    private static boolean isMicrobotRelatedClass(Class<?> clazz) {
        if (clazz == null || clazz.getPackage() == null) {
            return false;
        }

        String packageName = clazz.getPackage().getName();

        return packageName.startsWith(PLUGIN_PACKAGE + ".microbot");
    }

    public void loadCorePlugins() throws IOException, PluginInstantiationException {
        SplashScreen.stage(.59, null, "Loading plugins");
        ClassPath classPath = ClassPath.from(getClass().getClassLoader());

        List<Class<?>> plugins = classPath.getTopLevelClassesRecursive(PLUGIN_PACKAGE).stream()
                .map(ClassInfo::load)
                .collect(Collectors.toList());

        loadPlugins(plugins, (loaded, total) ->
                SplashScreen.stage(.60, .70, null, "Loading plugins", loaded, total, false));
    }

    public void loadSideLoadPlugins() {
        File[] files = SIDELOADED_PLUGINS.listFiles();
        if (files == null) {
            return;
        }

        for (File f : files) {
            if (f.getName().endsWith(".jar")) {
                log.info("Side-loading plugin {}", f);

                try {
                    ClassLoader classLoader = new PluginClassLoader(f, getClass().getClassLoader());

                    List<Class<?>> plugins = ClassPath.from(classLoader)
                            .getAllClasses()
                            .stream()
                            .map(ClassInfo::load)
                            .collect(Collectors.toList());

                    loadPlugins(plugins, null);
                } catch (PluginInstantiationException | IOException ex) {
                    log.error("error sideloading plugin", ex);
                }
            }
        }
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

			if (safeMode && !pluginDescriptor.loadInSafeMode())
			{
				log.debug("Disabling {} due to safe mode", clazz);
				if (!StartupRecovery.mode().isTemporary())
				{
					// also disable the plugin from autostarting later
					configManager.setConfiguration(RuneLiteConfig.GROUP_NAME,
						getPluginConfigurationKey(clazz),
						false);
				}
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
                plugin = instantiate(this.plugins, (Class<Plugin>) pluginClazz);
                newPlugins.add(plugin);
                add(plugin);
            } catch (PluginInstantiationException ex) {
                log.error("Error instantiating plugin {}!", pluginClazz, ex);
            }

            loaded++;
            if (onPluginLoaded != null) {
                onPluginLoaded.accept(loaded, sortedPlugins.size());
            }
        }

        return newPlugins;
    }

    public boolean startPlugin(Plugin plugin) throws PluginInstantiationException {
        // plugins always start in the EDT
        assert SwingUtilities.isEventDispatchThread();

        if (unloadingPlugins.contains(plugin)
                || activePlugins.contains(plugin) || !isPluginEnabled(plugin)) {
            return false;
        }

        List<Plugin> conflicts = conflictsForPlugin(plugin);
        for (Plugin conflict : conflicts) {
            if (isPluginEnabled(conflict)) {
                setPluginEnabled(conflict, false);
            }
            if (activePlugins.contains(conflict)) {
                stopPlugin(conflict);
            }
        }

        activePlugins.add(plugin);

        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        boolean external = descriptor != null && (descriptor.isExternal()
                || plugin.getClass().getClassLoader() instanceof PluginJarClassLoader);
        String pluginName = plugin.getClass().getSimpleName();
        if (external) {
            StartupRecovery.pluginStarting(pluginName,
                getPluginConfigurationKey(plugin.getClass()));
        }

        try {
            plugin.startUp();
            shutdownFailures.remove(plugin);

            log.debug("Plugin {} is now running", plugin.getClass().getSimpleName());
            if (sceneTileManager != null) {
                final GameEventManager gameEventManager = this.sceneTileManager.get();
                if (gameEventManager != null) {
                    gameEventManager.simulateGameEvents(plugin);
                }
            }

            eventBus.register(plugin);
            scheduler.register(plugin);
            eventBus.post(new PluginChanged(plugin, true));
            if (external) {
                StartupRecovery.pluginStarted(pluginName, descriptor.version());
            }
        } catch (ThreadDeath e) {
            throw e;
        } catch (Throwable ex) {
            if (external) {
                StartupRecovery.pluginFailed(pluginName, descriptor.version(), "start", ex);
            }
            // stop the plugin and fire the change event to update the plugin list panel
            try {
                stopPlugin(plugin);
            } catch (Throwable ex2) {
                log.error("unable to stop plugin", ex2);
            }
            throw new PluginInstantiationException(ex);
        }

        return true;
    }

    public boolean stopPlugin(Plugin plugin) throws PluginInstantiationException {
        // plugins always stop in the EDT
        assert SwingUtilities.isEventDispatchThread();

        if (!activePlugins.remove(plugin) && !shutdownFailures.containsKey(plugin)) {
            return false;
        }

        StartupRecovery.pluginStopped(plugin.getClass().getSimpleName());
        scheduler.unregister(plugin);
        eventBus.unregister(plugin);
        try {
            plugin.shutDown();
            shutdownFailures.remove(plugin);

            log.debug("Plugin {} is now stopped", plugin.getClass().getSimpleName());
            eventBus.post(new PluginChanged(plugin, false));
        } catch (Exception ex) {
            shutdownFailures.put(plugin, ex);
            throw new PluginInstantiationException(ex);
        } catch (Error ex) {
            shutdownFailures.put(plugin, ex);
            throw ex;
        }

        return true;
    }

    /** A failed shutdown remains visible even though the plugin is no longer marked active. */
    public Throwable getShutdownFailure(Plugin plugin) {
        return shutdownFailures.get(plugin);
    }

    public boolean isActive(Plugin plugin) {
        return activePlugins.contains(plugin);
    }

    public void setPluginValue(String groupName, String keyName, Object value) {
        configManager.setConfiguration(groupName, keyName, value);
    }

    public void setPluginEnabled(Plugin plugin, boolean enabled) {
        if (!enabled && isPluginAlwaysOn(plugin)) {
            return;
        }

        configManager.setConfiguration(RuneLiteConfig.GROUP_NAME, getPluginConfigurationKey(plugin.getClass()), String.valueOf(enabled));

        if (enabled) {
            List<Plugin> conflicts = conflictsForPlugin(plugin);
            for (Plugin conflict : conflicts) {
                if (isPluginEnabled(conflict)) {
                    setPluginEnabled(conflict, false);
                }
            }
        }
    }

    /**
     * Test if a plugin is enabled, which causes the client to attempt to start it on boot
     *
     * @param plugin
     * @return
     */
    public boolean isPluginEnabled(Plugin plugin) {
        final PluginDescriptor pluginDescriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
        if (isPluginAlwaysOn(plugin))
            return true;

        final String keyName = getPluginConfigurationKey(plugin.getClass());
        String value = configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, keyName);
        String legacyKey = legacyPluginConfigurationKey(plugin.getClass());
        if (value == null && !keyName.equals(legacyKey)
                && plugins.stream().noneMatch(other -> other.getClass() != plugin.getClass()
                    && legacyPluginConfigurationKey(other.getClass()).equals(legacyKey))) {
            // Migrate only when the old key has one unambiguous owner. A built-in plugin must
            // never enable an unrelated external plugin with the same simple class name.
            value = configManager.getConfiguration(RuneLiteConfig.GROUP_NAME, legacyKey);
            if (value != null) {
                configManager.setConfiguration(RuneLiteConfig.GROUP_NAME, keyName, value);
            }
        }
        return value != null ? Boolean.parseBoolean(value) : pluginDescriptor.enabledByDefault();
    }

    /** Enable/disable identity for a plugin; external default names are isolated from core names. */
    public static String getPluginConfigurationKey(Class<?> type) {
        PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
        if (Strings.isNullOrEmpty(descriptor.configName()) && type.getClassLoader() instanceof PluginJarClassLoader) {
            return "external." + type.getName().toLowerCase(Locale.ROOT);
        }
        return legacyPluginConfigurationKey(type);
    }

    private static String legacyPluginConfigurationKey(Class<?> type) {
        PluginDescriptor descriptor = type.getAnnotation(PluginDescriptor.class);
        return (Strings.isNullOrEmpty(descriptor.configName()) ? type.getSimpleName() : descriptor.configName()).toLowerCase(Locale.ROOT);
    }

    public boolean isPluginAlwaysOn(Plugin plugin) {
        return AlwaysOnPlugins.isLocked(plugin.getClass().getAnnotation(PluginDescriptor.class));
    }

    /**
     * Test if a plugin is on, eg. enabled and also was started successfully
     *
     * @param plugin
     * @return
     */
    public boolean isPluginActive(Plugin plugin) {
        return activePlugins.contains(plugin);
    }

    private Plugin instantiate(List<Plugin> scannedPlugins, Class<Plugin> clazz) throws PluginInstantiationException
    {
        PluginDependency[] pluginDependencies = clazz.getAnnotationsByType(PluginDependency.class);
        List<Module> modules = new ArrayList<>();
        for (PluginDependency pluginDependency : pluginDependencies)
        {
            Optional<Plugin> dependency = scannedPlugins.stream().filter(p -> p.getClass() == pluginDependency.value()).findFirst();
            if (!dependency.isPresent())
            {
                throw new PluginInstantiationException("Unmet dependency for " + clazz.getSimpleName() + ": " + pluginDependency.value().getSimpleName());
            }

            modules.add(pluginModuleFactory.dependencyModule(dependency.get()));
        }

        Plugin plugin;
        try
        {
            plugin = clazz.getDeclaredConstructor().newInstance();
        }
        catch (ThreadDeath e)
        {
            throw e;
        }
        catch (Throwable ex)
        {
            throw new PluginInstantiationException(ex);
        }

        try
        {
            Injector parent = Microbot.getInjector();

            modules.add(pluginModuleFactory.new PluginModule(plugin));
            Injector pluginInjector = parent.createChildInjector(modules);
            plugin.injector = pluginInjector;
        }
        catch (Throwable ex)
        {
            throw new PluginInstantiationException(ex);
        }

        log.debug("Loaded plugin {}", clazz.getSimpleName());
        return plugin;
    }

    public void add(Plugin plugin) {
        plugins.add(plugin);
    }

    public void remove(Plugin plugin) {
        plugins.remove(plugin);
        unloadingPlugins.remove(plugin);
        shutdownFailures.remove(plugin);
    }

    /** Prepare an external instance without publishing it in the live plugin list. */
    @SuppressWarnings("unchecked")
    public Plugin preparePlugin(Class<? extends Plugin> type, Collection<Plugin> dependencies)
            throws PluginInstantiationException {
        return instantiate(new ArrayList<>(dependencies), (Class<Plugin>) type);
    }

    /** Prevent profile changes and UI toggles from restarting a plugin during permanent disposal. */
    public void setPluginUnloading(Plugin plugin, boolean unloading) {
        if (unloading) {
            unloadingPlugins.add(plugin);
        } else {
            unloadingPlugins.remove(plugin);
        }
    }

    public Collection<Plugin> getPlugins() {
        return plugins;
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

    public List<Plugin> conflictsForPlugin(Plugin plugin) {
        Set<String> conflicts;
        {
            PluginDescriptor desc = plugin.getClass().getAnnotation(PluginDescriptor.class);
            conflicts = new HashSet<>(Arrays.asList(desc.conflicts()));
            conflicts.add(desc.name());
        }

        return plugins.stream()
                .filter(p ->
                {
                    if (p == plugin) {
                        return false;
                    }

                    PluginDescriptor desc = p.getClass().getAnnotation(PluginDescriptor.class);
                    if (conflicts.contains(desc.name())) {
                        return true;
                    }

                    for (String conflict : desc.conflicts()) {
                        if (conflicts.contains(conflict)) {
                            return true;
                        }
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }
}
