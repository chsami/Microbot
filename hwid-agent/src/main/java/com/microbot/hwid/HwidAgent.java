package com.microbot.hwid;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Logger;

public final class HwidAgent
{
	private static final Logger log = Logger.getLogger(HwidAgent.class.getName());

	public static final String TOGGLE_PROPERTY = "microbot.hwid.spoof";
	public static final String AGENT_PROPERTY = "microbot.hwid.agent";
	/** {@link #AGENT_PROPERTY} value once the transformers are registered. */
	public static final String AGENT_LOADED = "loaded";
	/** {@link #AGENT_PROPERTY} value when the agent was attached with -Dmicrobot.hwid.spoof=false. */
	public static final String AGENT_INERT = "inert";
	public static final String DEVICE_HOOK_PROPERTY = "microbot.hwid.device.installed";
	public static final String RANDOM_DAT_CALL_PROPERTY = "microbot.hwid.randomdat.lastCall";

	private static volatile boolean deviceHookInstalled;
	private static volatile byte[] injectedRandomDat;
	private static volatile byte[] injectedSnapshot;
	private static volatile String injectedFor;
	private static File credentialsFile;
	private static boolean credentialsRead;
	private static String credentialsCharacterId;

	private HwidAgent()
	{
	}

	/**
	 * Attaching the agent is the opt-in, so a missing -Dmicrobot.hwid.spoof registers the transformers.
	 * Only an explicit -Dmicrobot.hwid.spoof=false leaves the agent inert: it registers no transformer,
	 * touches no file and only publishes {@link #AGENT_INERT}.
	 */
	public static void premain(String args, Instrumentation inst)
	{
		if (spoofExplicitlyOff())
		{
			System.setProperty(AGENT_PROPERTY, AGENT_INERT);
			return;
		}
		try
		{
			inst.addTransformer(new PlatformInfoTransformer());
			inst.addTransformer(new RandomDatTransformer());
			inst.addTransformer(new RandomDatWriteTransformer());
			System.setProperty(AGENT_PROPERTY, AGENT_LOADED);
			launcherCharacterId();
		}
		catch (Throwable t)
		{
			log.severe("hwid-agent failed to register transformer: " + t);
		}
	}

	static void markDeviceHookInstalled()
	{
		deviceHookInstalled = true;
		System.setProperty(DEVICE_HOOK_PROPERTY, "true");
	}

	public static boolean isDeviceHookInstalled()
	{
		return deviceHookInstalled;
	}

	public static String deviceId(int osType)
	{
		try
		{
			if (!spoofEnabled())
			{
				return VanillaDeviceId.getDeviceId(osType);
			}

			return DeviceIdStore.getOrCreate(currentIdentifier());
		}
		catch (Throwable t)
		{
			log.warning("hwid-agent device id fallback: " + t);
			return VanillaDeviceId.getDeviceId(osType);
		}
	}

	public static boolean spoofEnabled()
	{
		return Boolean.parseBoolean(System.getProperty(TOGGLE_PROPERTY, "true"));
	}

	static boolean spoofExplicitlyOff()
	{
		String value = System.getProperty(TOGGLE_PROPERTY);
		return value != null && "false".equalsIgnoreCase(value.trim());
	}

	public static Object randomDatFile(Object handle)
	{
		return spoofEnabled() ? null : handle;
	}

	public static synchronized byte[] randomDatField(byte[] current)
	{
		try
		{
			System.setProperty(RANDOM_DAT_CALL_PROPERTY, Long.toString(System.currentTimeMillis()));
			if (current != null && current != injectedRandomDat)
			{
				return current;
			}
			if (current != null && !Arrays.equals(current, injectedSnapshot))
			{
				RandomDatStore.put(injectedFor, current);
				injectedSnapshot = current.clone();
			}
			if (!spoofEnabled())
			{
				injectedRandomDat = null;
				injectedSnapshot = null;
				injectedFor = null;
				return null;
			}
			String identifier = currentIdentifier();
			byte[] data = RandomDatStore.getOrCreate(identifier);
			injectedFor = identifier;
			if (current != null && Arrays.equals(current, data))
			{
				return current;
			}
			byte[] injected = data.clone();
			injectedSnapshot = data.clone();
			injectedRandomDat = injected;
			return injected;
		}
		catch (Throwable t)
		{
			System.clearProperty(RANDOM_DAT_CALL_PROPERTY);
			log.warning("hwid-agent random.dat fallback: " + t);
			return current;
		}
	}

	static String currentIdentifier()
	{
		String username = vanillaUsername();
		if (username != null && !username.isBlank())
		{
			return "user/" + username.trim();
		}

		String jxCharacterId = System.getenv("JX_CHARACTER_ID");
		if (jxCharacterId == null || jxCharacterId.isBlank())
		{
			jxCharacterId = launcherCharacterId();
		}
		if (jxCharacterId != null && !jxCharacterId.isBlank())
		{
			return "jx/" + jxCharacterId.trim();
		}

		return "default";
	}

	private static synchronized String launcherCharacterId()
	{
		if (!credentialsRead)
		{
			credentialsRead = true;
			credentialsCharacterId = readCharacterId(credentialsFile != null
				? credentialsFile
				: new File(new File(System.getProperty("user.home"), ".runelite"), System.getProperty("runelite.credentials.path", "credentials.properties")));
		}
		return credentialsCharacterId;
	}

	private static String readCharacterId(File file)
	{
		if (!file.isFile())
		{
			return null;
		}
		Properties credentials = new Properties();
		try (InputStream in = Files.newInputStream(file.toPath()))
		{
			credentials.load(in);
		}
		catch (IOException e)
		{
			log.warning("hwid-agent could not read the launcher credentials: " + e);
			return null;
		}
		return credentials.getProperty("JX_CHARACTER_ID");
	}

	static synchronized void setCredentialsFileForTesting(File file)
	{
		credentialsFile = file;
		credentialsRead = false;
		credentialsCharacterId = null;
	}

	static synchronized void resetRandomDatForTesting()
	{
		injectedRandomDat = null;
		injectedSnapshot = null;
		injectedFor = null;
	}

	private static String vanillaUsername()
	{
		try
		{
			Class<?> api = Class.forName("net.runelite.api.Client", false, HwidAgent.class.getClassLoader());
			Class<?> gameclient = Class.forName("client", false, HwidAgent.class.getClassLoader());

			for (java.lang.reflect.Field field : gameclient.getDeclaredFields())
			{
				int modifiers = field.getModifiers();
				if (!java.lang.reflect.Modifier.isStatic(modifiers))
				{
					continue;
				}
				if (java.lang.reflect.Modifier.isFinal(modifiers) || field.getType().isPrimitive())
				{
					continue;
				}
				field.setAccessible(true);
				Object value = field.get(null);
				if (value == null || !api.isInstance(value))
				{
					continue;
				}
				Object username = api.getMethod("getUsername").invoke(value);
				return username instanceof String ? (String) username : null;
			}
		}
		catch (Throwable ignored)
		{
		}
		return null;
	}
}
