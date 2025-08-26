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
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Binder;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
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
import javax.swing.*;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, MicrobotPluginManifest> manifestMap = new ConcurrentHashMap<>();

	public Map<String, MicrobotPluginManifest> getManifestMap() {
		return Collections.unmodifiableMap(manifestMap);
	}

	private void loadManifest()
	{
		try
		{
			List<MicrobotPluginManifest> manifests = microbotPluginClient.downloadManifest();
			Map<String, MicrobotPluginManifest> next = new HashMap<>(manifests.size());
			for (MicrobotPluginManifest m : manifests)
			{
				next.put(m.getInternalName(), m);
			}
			boolean changed = !next.keySet().equals(manifestMap.keySet())
				|| next.entrySet().stream().anyMatch(e -> {
				MicrobotPluginManifest cur = manifestMap.get(e.getKey());
				return cur == null || !Objects.equals(cur.getSha256(), e.getValue().getSha256());
			});
			if (changed)
			{
				manifestMap.clear();
				manifestMap.putAll(next);
				log.info("Loaded {} plugin manifests.", manifestMap.size());
				eventBus.post(new ExternalPluginsChanged());
			}
			else
			{
				log.debug("Plugin manifests unchanged ({} entries), skipping event.", manifestMap.size());
			}
		}
		catch (Exception e)
		{
			log.error("Failed to fetch plugin manifests", e);
		}
	}

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

		loadManifest();
		executor.scheduleWithFixedDelay(this::loadManifest, 10, 10, TimeUnit.MINUTES);
	}

	public List<String> getInstalledPlugins()
	{
		List<String> plugins = new ArrayList<>();
		try (FileReader reader = new FileReader(PLUGIN_LIST))
		{
			plugins = gson.fromJson(reader, new TypeToken<List<String>>() {}.getType());
			if (plugins == null)
			{
				plugins = new ArrayList<>();
			}
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.error("Error reading Microbot plugin list", e);
			// Auto-heal corrupt file to reduce repeated failures
			try
			{
				Files.asCharSink(PLUGIN_LIST, StandardCharsets.UTF_8).write("[]");
			}
			catch (IOException ioEx)
			{
				log.warn("Failed to auto-heal plugins.json", ioEx);
			}
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
			// Check if plugin is disabled
			if (manifest.isDisable())
			{
				log.error("Plugin {} is disabled and cannot be installed.", manifest.getInternalName());
				return;
			}

			// Check version compatibility before installing
			if (!isClientVersionCompatible(manifest.getMinClientVersion()))
			{
				log.error("Plugin {} requires client version {} or higher, but current version is {}. Installation aborted.",
					manifest.getInternalName(), manifest.getMinClientVersion(), RuneLiteProperties.getMicrobotVersion());
				return;
			}

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

					manifestMap.put(manifest.getInternalName(), manifest);
					// Save the jar file
					File pluginFile = getPluginJarFile(manifest.getInternalName());
					Files.write(jarData, pluginFile);
					List<String> plugins = getInstalledPlugins();
					if (!plugins.contains(manifest.getInternalName()))
					{
						plugins.add(manifest.getInternalName());
						saveInstalledPlugins(plugins);
					}
					loadSideLoadPlugin(manifest.getInternalName());
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
            List<Plugin> pluginsToRemove = pluginManager.getPlugins().stream()
                    .filter(plugin -> {
                        PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
                        if (descriptor == null) {
                            return false;
                        }

                        boolean isExternal = descriptor.isExternal();
                        String className = plugin.getClass().getSimpleName();
                        String descriptorName = descriptor.name();

                        boolean nameMatches = className.equals(internalName) ||
                                            descriptorName.equals(internalName) ||
                                            className.toLowerCase().equals(internalName.toLowerCase()) ||
                                            descriptorName.toLowerCase().equals(internalName.toLowerCase());

                        return isExternal && nameMatches;
                    })
                    .collect(Collectors.toList());

            for (Plugin plugin : pluginsToRemove) {
                if (pluginManager.isPluginEnabled(plugin)) {
                    try {
                        pluginManager.setPluginEnabled(plugin, false);

                        if (pluginManager.isPluginActive(plugin)) {
                            SwingUtilities.invokeLater(() -> {
                                try {
                                    pluginManager.stopPlugin(plugin);
                                } catch (PluginInstantiationException e) {
                                    log.warn("Error stopping plugin {}: {}", plugin.getClass().getSimpleName(), e.getMessage());
                                }
                            });
                        }
                    } catch (Exception e) {
                        log.warn("Error stopping plugin {}: {}", plugin.getClass().getSimpleName(), e.getMessage());
                    }
                }

                pluginManager.remove(plugin);
            }

            File pluginFile = getPluginJarFile(internalName);
            if (pluginFile.exists()) {
                pluginFile.delete();
            }

            List<String> plugins = getInstalledPlugins();
            if (plugins.contains(internalName))
            {
                plugins.remove(internalName);
                saveInstalledPlugins(plugins);
            }

            eventBus.post(new ExternalPluginsChanged());
        });
    }

    private boolean verifyHash(byte[] jarData, String expectedHash)
    {
        if ((expectedHash == null || expectedHash.isEmpty()) || (jarData == null || jarData.length == 0))
        {
            throw new IllegalArgumentException("Hash or jar data is null/empty");
        }

        String computedHash = calculateSHA256Hash(jarData);
        return computedHash.equals(expectedHash);
    }

    /**
     * Calculate SHA-256 hash for byte array data and return as hex string
     */
    private String calculateSHA256Hash(byte[] data)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            int offset = 0;
            int bufferSize = 8192;

            while (offset < data.length)
            {
                int bytesToProcess = Math.min(bufferSize, data.length - offset);
                digest.update(data, offset, bytesToProcess);
                offset += bytesToProcess;
            }

            byte[] hash = digest.digest();

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
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException e)
        {
            log.trace("Error computing SHA-256 hash", e);
			throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public static File[] createSideloadingFolder() {
        final File MICROBOT_PLUGINS = new File(RuneLite.RUNELITE_DIR, "microbot-plugins");
        if (!java.nio.file.Files.exists(MICROBOT_PLUGINS.toPath())) {
            try {
                java.nio.file.Files.createDirectories(MICROBOT_PLUGINS.toPath());
                log.debug("Directory for sideloading was created successfully.");
                return MICROBOT_PLUGINS.listFiles();
            } catch (IOException e) {
                log.trace("Error creating directory for sideloading!", e);
            }
        }
        return MICROBOT_PLUGINS.listFiles();
    }

	public void syncPlugins()
	{
		List<String> installed = getInstalledPlugins();
		Map<String, Plugin> loadedByInternalName = pluginManager.getPlugins().stream()
			.filter(p -> p.getClass().isAnnotationPresent(PluginDescriptor.class))
			.filter(p -> {
				PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
				return d != null && d.isExternal();
			})
			.collect(Collectors.toMap(
				p -> p.getClass().getAnnotation(PluginDescriptor.class).name(),
				p -> p,
				(a, b) -> a
			));

		// Remove plugins that are loaded but not installed
		for (Map.Entry<String, Plugin> entry : loadedByInternalName.entrySet())
		{
			if (!installed.contains(entry.getKey()))
			{
				remove(entry.getKey());
			}
		}
	}

    /**
     * Loads a single plugin from the sideload folder if not already loaded.
     */
	private void loadSideLoadPlugin(String internalName)
	{
		File pluginFile = getPluginJarFile(internalName);
		if (!pluginFile.exists())
		{
			log.debug("Plugin file {} does not exist", pluginFile);
			return;
		}
		List<String> installedPlugins = getInstalledPlugins();
		if (!installedPlugins.contains(internalName))
		{
			return; // Not installed
		}
		Set<String> loadedInternalNames = pluginManager.getPlugins().stream()
			.filter(p -> p.getClass().isAnnotationPresent(PluginDescriptor.class))
			.filter(p -> p.getClass().getAnnotation(PluginDescriptor.class).isExternal())
			.map(p -> p.getClass().getAnnotation(PluginDescriptor.class).name())
			.collect(Collectors.toSet());
		if (loadedInternalNames.contains(internalName))
		{
			return; // Already loaded
		}
		MicrobotPluginManifest manifest = manifestMap.get(internalName);
		if (manifest == null)
		{
			log.warn("No manifest found for plugin {}. Skipping hash validation and load.", internalName);
			return;
		}
		try
		{
			byte[] fileBytes = Files.toByteArray(pluginFile);
			// Validate hash before loading
			if (!verifyHash(fileBytes, manifest.getSha256()))
			{
				log.error("Hash mismatch for plugin {}. Skipping load.", internalName);
				pluginFile.delete();
				List<String> plugins = getInstalledPlugins();
				plugins.remove(internalName);
				saveInstalledPlugins(plugins);
				eventBus.post(new ExternalPluginsChanged());
				return;
			}
			List<Class<?>> plugins = new ArrayList<>();
			MicrobotPluginClassLoader classLoader = new MicrobotPluginClassLoader(getClass().getClassLoader(), pluginFile.getName(), fileBytes);
			Set<String> classNamesToLoad = classLoader.getLoadedClassNames();
			for (String className : classNamesToLoad)
			{
				try
				{
					Class<?> clazz = classLoader.loadClass(className);
					plugins.add(clazz);
				}
				catch (ClassNotFoundException e)
				{
					log.trace("Class not found during sideloading: {}", className, e);
				}
			}
			loadPlugins(plugins, null);
			eventBus.post(new ExternalPluginsChanged());
		}
		catch (PluginInstantiationException | IOException e)
		{
			log.trace("Error loading side-loaded plugin!", e);
		}
	}

	public void loadSideLoadPlugins()
	{
		syncPlugins();
		File[] files = createSideloadingFolder();
		if (files == null)
		{
			return;
		}
		List<String> installedPlugins = getInstalledPlugins();
		Set<String> loadedInternalNames = pluginManager.getPlugins().stream()
			.filter(p -> p.getClass().isAnnotationPresent(PluginDescriptor.class))
			.filter(p -> p.getClass().getAnnotation(PluginDescriptor.class).isExternal())
			.map(p -> p.getClass().getAnnotation(PluginDescriptor.class).name())
			.collect(Collectors.toSet());
		for (File f : files)
		{
			if (!f.getName().endsWith(".jar"))
			{
				continue;
			}
			String internalName = f.getName().replace(".jar", "");
			if (!installedPlugins.contains(internalName))
			{
				continue; // Skip if not in installed list
			}
			if (loadedInternalNames.contains(internalName))
			{
				continue; // Already loaded
			}
			loadSideLoadPlugin(internalName);
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

	public List<Plugin> loadPlugins(List<Class<?>> plugins, BiConsumer<Integer, Integer> onPluginLoaded) throws PluginInstantiationException
	{
		MutableGraph<Class<? extends Plugin>> graph = GraphBuilder
			.directed()
			.build();

		Set<Class<?>> alreadyLoaded = pluginManager.getPlugins().stream()
			.map(Object::getClass)
			.collect(Collectors.toSet());

		for (Class<?> clazz : plugins)
		{
			if (alreadyLoaded.contains(clazz))
			{
				log.debug("Plugin {} is already loaded, skipping duplicate.", clazz.getSimpleName());
				continue;
			}
			PluginDescriptor pluginDescriptor = clazz.getAnnotation(PluginDescriptor.class);

			if (pluginDescriptor == null)
			{
				if (clazz.getSuperclass() == Plugin.class)
				{
					log.error("Class {} is a plugin, but has no plugin descriptor", clazz);
				}
				continue;
			}

			if (clazz.getSuperclass() != Plugin.class)
			{
				log.error("Class {} has plugin descriptor, but is not a plugin", clazz);
				continue;
			}

			// Check version compatibility for external plugins
			if (pluginDescriptor.isExternal() && !isClientVersionCompatible(pluginDescriptor.minClientVersion()))
			{
				log.error("Plugin {} requires client version {} or higher, but current version is {}. Skipping plugin loading.",
					clazz.getSimpleName(), pluginDescriptor.minClientVersion(), RuneLiteProperties.getMicrobotVersion());
				continue;
			}

			// Check if the plugin is disabled
			if (pluginDescriptor.disable())
			{
				log.error("Plugin {} has been disabled upstream", clazz.getSimpleName());
				continue;
			}

			graph.addNode((Class<Plugin>) clazz);
		}

		// Build plugin graph
		for (Class<? extends Plugin> pluginClazz : graph.nodes())
		{
			PluginDependency[] pluginDependencies = pluginClazz.getAnnotationsByType(PluginDependency.class);

			for (PluginDependency pluginDependency : pluginDependencies)
			{
				if (graph.nodes().contains(pluginDependency.value()))
				{
					graph.putEdge(pluginDependency.value(), pluginClazz);
				}
			}
		}

		if (Graphs.hasCycle(graph))
		{
			throw new PluginInstantiationException("Plugin dependency graph contains a cycle!");
		}

		List<Class<? extends Plugin>> sortedPlugins = topologicalSort(graph);

		int loaded = 0;
		List<Plugin> newPlugins = new ArrayList<>();
		for (Class<? extends Plugin> pluginClazz : sortedPlugins)
		{
			Plugin plugin;
			try
			{
				plugin = instantiate(pluginManager.getPlugins(), (Class<Plugin>) pluginClazz);
				log.info("Plugin loaded {}", plugin.getClass().getSimpleName());
				newPlugins.add(plugin);
				pluginManager.addPlugin(plugin);
				loaded++;
			}
			catch (PluginInstantiationException ex)
			{
				log.error("Error instantiating plugin!", ex);
			}

			if (onPluginLoaded != null)
			{
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

    /**
     * Check if the current client version is compatible with the required minimum version
     */
    public boolean isClientVersionCompatible(String minClientVersion) {
        if (minClientVersion == null || minClientVersion.isEmpty()) {
            return true;
        }

        String currentVersion = RuneLiteProperties.getMicrobotVersion();
        if (currentVersion == null) {
            log.warn("Unable to determine current Microbot version");
            return false;
        }

        return compareVersions(currentVersion, minClientVersion) >= 0;
    }

    /**
     * Compare two version strings using semantic versioning with support for 4-part versions
     * Supports formats like: 1.9.7, 1.9.7.1, 1.9.8, 1.9.8.1
     * @param version1 The first version to compare
     * @param version2 The second version to compare
     * @return -1 if version1 < version2, 0 if equal, 1 if version1 > version2
     */
    @VisibleForTesting
    static int compareVersions(String version1, String version2) {
        if (version1 == null && version2 == null) return 0;
        if (version1 == null) return -1;
        if (version2 == null) return 1;

        // Split versions by dots and handle up to 4 parts (major.minor.patch.build)
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");

        int maxLength = Math.max(v1Parts.length, v2Parts.length);

        for (int i = 0; i < maxLength; i++) {
            int v1Part = i < v1Parts.length ? parseVersionPart(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? parseVersionPart(v2Parts[i]) : 0;

            if (v1Part < v2Part) return -1;
            if (v1Part > v2Part) return 1;
        }

        return 0;
    }

    /**
     * Parse a version part, extracting only the numeric portion
     */
    private static int parseVersionPart(String part) {
        if (part == null || part.isEmpty()) return 0;

        StringBuilder numericPart = new StringBuilder();
        for (char c : part.toCharArray()) {
            if (!Character.isDigit(c)) break;
			numericPart.append(c);
        }

        try {
            return numericPart.length() > 0 ? Integer.parseInt(numericPart.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void loadCorePlugins(List<Class<?>> plugins) throws IOException, PluginInstantiationException
    {
        SplashScreen.stage(.59, null, "Loading plugins");

        loadPlugins(plugins, (loaded, total) ->
                SplashScreen.stage(.60, .70, null, "Loading plugins", loaded, total, false));
    }
}
