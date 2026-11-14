package com.microbot.hwid;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Makes the platform hooks known before the first login. The JVM loads a game class only when it is
 * first needed, which for the code that reads the signers can be while the login packet is being
 * built, too late to stop that login. As soon as the first game class loads, this finds where the game
 * classes come from and loads, without initialising them, every one {@link PlatformMetadataTransformer}
 * could act on. Each load goes through the transformer as usual, so by the time
 * {@link HwidAgent#PLATFORM_SCANNED_PROPERTY} is set, {@link HwidAgent#PLATFORM_HOOK_PROPERTY} and
 * {@link HwidAgent#PLATFORM_MISSED_PROPERTY} say what a login would send.
 */
final class PlatformScan
{
	private static final Logger log = Logger.getLogger(PlatformScan.class.getName());

	/** Set to {@code false} to leave class loading as the JVM does it. */
	static final String SCAN_PROPERTY = "microbot.hwid.platform.scan";

	private static final AtomicBoolean started = new AtomicBoolean();

	private PlatformScan()
	{
	}

	/** Called for every default-package class the transformer sees; only the first call does anything. */
	static void startOnce(ClassLoader loader, String gameClass)
	{
		if (loader == null || "false".equalsIgnoreCase(System.getProperty(SCAN_PROPERTY, "true").trim())
			|| !started.compareAndSet(false, true))
		{
			return;
		}
		Thread thread = new Thread(() -> run(loader, gameClass), "hwid-platform-scan");
		thread.setDaemon(true);
		thread.start();
	}

	/** Never throws. The scanned property is only set when every candidate was loaded. */
	static void run(ClassLoader loader, String gameClass)
	{
		try
		{
			URL resource = loader.getResource(gameClass + ".class");
			if (resource == null)
			{
				throw new IOException("game classes not found through their class loader");
			}
			for (String name : candidates(resource, gameClass))
			{
				Class.forName(name, false, loader);
			}
			System.setProperty(HwidAgent.PLATFORM_SCANNED_PROPERTY, "true");
		}
		catch (Throwable t)
		{
			log.info("Platform metadata scan did not finish: " + t);
		}
	}

	/** Default-package classes next to {@code gameClass} that the platform transformer could act on. */
	static List<String> candidates(URL resource, String gameClass) throws IOException
	{
		List<String> names = new ArrayList<>();
		URLConnection connection = resource.openConnection();
		if (connection instanceof JarURLConnection)
		{
			JarURLConnection jarConnection = (JarURLConnection) connection;
			jarConnection.setUseCaches(false);
			try (JarFile jar = jarConnection.getJarFile())
			{
				for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements();)
				{
					JarEntry entry = entries.nextElement();
					String name = entry.getName();
					if (entry.isDirectory() || name.indexOf('/') >= 0 || !name.endsWith(".class"))
					{
						continue;
					}
					try (InputStream in = jar.getInputStream(entry))
					{
						addIfCandidate(names, name, in.readAllBytes());
					}
				}
			}
			return names;
		}
		if (!"file".equals(resource.getProtocol()))
		{
			throw new IOException("unsupported game class location: " + resource.getProtocol());
		}
		File directory;
		try
		{
			directory = new File(resource.toURI()).getParentFile();
		}
		catch (java.net.URISyntaxException e)
		{
			throw new IOException(e);
		}
		File[] files = directory.listFiles((dir, name) -> name.endsWith(".class"));
		if (files == null)
		{
			throw new IOException("could not list " + directory);
		}
		for (File file : files)
		{
			addIfCandidate(names, file.getName(), Files.readAllBytes(file.toPath()));
		}
		return names;
	}

	private static void addIfCandidate(List<String> names, String fileName, byte[] bytes)
	{
		if (PlatformMetadataTransformer.isCandidate(bytes))
		{
			names.add(fileName.substring(0, fileName.length() - ".class".length()));
		}
	}

	static void resetForTesting()
	{
		started.set(false);
		System.clearProperty(HwidAgent.PLATFORM_SCANNED_PROPERTY);
	}
}
