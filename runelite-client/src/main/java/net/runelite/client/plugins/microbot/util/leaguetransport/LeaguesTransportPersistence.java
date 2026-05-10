package net.runelite.client.plugins.microbot.util.leaguetransport;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.gameval.VarbitID;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Share-file persistence for Leagues blacklist, region landings, and calibration consent flags.
 */
@Slf4j
final class LeaguesTransportPersistence
{
	private LeaguesTransportPersistence()
	{
	}

	static final String PERSIST_GROUP = net.runelite.client.plugins.microbot.MicrobotConfig.configGroup;
	static final String KEY_BLOCKED_DESTS = "leaguesBlockedDestinations";
	static final String KEY_BLOCKED_DEST_REGIONS = "leaguesBlockedDestinationRegions";
	static final String KEY_BLOCKED_DEST_METHODS = "leaguesBlockedDestinationMethods";
	static final String KEY_REGION_LANDINGS = "leaguesAreaTeleportLandings";
	static final String KEY_CALIBRATION_CONSENT = "leaguesCalibrationConsent";
	static final String KEY_PROFILE_PURGE_MARKER = "leaguesProfilePersistencePurged";

	private static final Map<Integer, String> CATALOG_VERSION_BY_MAJOR = new HashMap<>();
	private static final Object PATH_SNAPSHOT_LOCK = new Object();
	private static final Object SHARE_FILE_IO_LOCK = new Object();
	private static volatile String cachedCatalogVersion;
	private static volatile Path cachedVersionDir;
	private static volatile Path cachedShareFile;

	static
	{
		CATALOG_VERSION_BY_MAJOR.put(6, "6.0.0");
	}

	private static int leaguesMajor()
	{
		int v = Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE);
		return v > 0 ? v : 0;
	}

	private static void ensurePathSnapshot()
	{
		if (cachedShareFile != null)
		{
			return;
		}
		synchronized (PATH_SNAPSHOT_LOCK)
		{
			if (cachedShareFile != null)
			{
				return;
			}
			int major = leaguesMajor();
			String curated = CATALOG_VERSION_BY_MAJOR.get(major);
			String version = curated != null ? curated : major + ".0.0";
			Path dir = Path.of(
					System.getProperty("user.home"),
					".runelite",
					"microbot",
					"leagues-transport",
					"v" + version);
			cachedCatalogVersion = version;
			cachedVersionDir = dir;
			cachedShareFile = dir.resolve("leagues-transport-cache.properties");
		}
	}

	static String leaguesCatalogVersion()
	{
		ensurePathSnapshot();
		return cachedCatalogVersion;
	}

	static Path leaguesVersionDir()
	{
		ensurePathSnapshot();
		return cachedVersionDir;
	}

	private static Path shareFile()
	{
		ensurePathSnapshot();
		return cachedShareFile;
	}

	private static final Set<Integer> PERSIST_BLOCKED_DESTS = ConcurrentHashMap.newKeySet();
	private static final Map<Integer, LeaguesRegion> PERSIST_BLOCKED_DEST_REGIONS = new ConcurrentHashMap<>();
	private static final Map<Integer, String> PERSIST_BLOCKED_DEST_METHODS = new ConcurrentHashMap<>();
	private static volatile boolean persistLoaded = false;
	private static final Map<LeaguesRegion, WorldPoint> PERSIST_REGION_LANDINGS = new ConcurrentHashMap<>();

	private static final AtomicBoolean CALIBRATION_CONSENT_ALLOWED = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_CONSENT_DENIED = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_CONSENT_PROMPT_QUEUED = new AtomicBoolean(false);
	private static final AtomicLong CALIBRATION_CONSENT_RETRY_AFTER_MS = new AtomicLong(0L);

	private static final AtomicBoolean PROFILE_KEYS_PURGED = new AtomicBoolean(false);

	static void ensureLoaded()
	{
		if (persistLoaded)
		{
			return;
		}
		synchronized (LeaguesTransportPersistence.class)
		{
			if (persistLoaded)
			{
				return;
			}
			loadFromShareFile();
			maybePurgeLegacyProfileKeys();
			persistLoaded = true;
		}
	}

	static boolean isCalibrationConsentAllowed()
	{
		return CALIBRATION_CONSENT_ALLOWED.get();
	}

	static boolean isCalibrationConsentDenied()
	{
		return CALIBRATION_CONSENT_DENIED.get();
	}

	static boolean compareAndSetCalibrationConsentPromptQueued(boolean expect, boolean update)
	{
		return CALIBRATION_CONSENT_PROMPT_QUEUED.compareAndSet(expect, update);
	}

	static void setCalibrationConsentRetryAfterMs(long ms)
	{
		CALIBRATION_CONSENT_RETRY_AFTER_MS.set(ms);
	}

	static long getCalibrationConsentRetryAfterMs()
	{
		return CALIBRATION_CONSENT_RETRY_AFTER_MS.get();
	}

	static void setCalibrationConsentAllowed(boolean allowed)
	{
		CALIBRATION_CONSENT_ALLOWED.set(allowed);
	}

	static void setCalibrationConsentDenied(boolean denied)
	{
		CALIBRATION_CONSENT_DENIED.set(denied);
	}

	static void setCalibrationConsentPromptQueued(boolean queued)
	{
		CALIBRATION_CONSENT_PROMPT_QUEUED.set(queued);
	}

	static void resetCalibrationConsentPromptStateOnLogout()
	{
		CALIBRATION_CONSENT_PROMPT_QUEUED.set(false);
		CALIBRATION_CONSENT_RETRY_AFTER_MS.set(0L);
	}

	static boolean hasRegionLanding(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		ensureLoaded();
		return PERSIST_REGION_LANDINGS.containsKey(region);
	}

	static Map<LeaguesRegion, WorldPoint> copyRegionLandingsSnapshot()
	{
		ensureLoaded();
		return new HashMap<>(PERSIST_REGION_LANDINGS);
	}

	static boolean isDestinationBlacklisted(int packedWorldPoint)
	{
		ensureLoaded();
		return PERSIST_BLOCKED_DESTS.contains(packedWorldPoint);
	}

	static void invalidateBlacklistFor(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		ensureLoaded();
		ArrayList<Integer> drop = new ArrayList<>();
		for (Map.Entry<Integer, LeaguesRegion> e : PERSIST_BLOCKED_DEST_REGIONS.entrySet())
		{
			if (region.equals(e.getValue()))
			{
				drop.add(e.getKey());
			}
		}
		if (drop.isEmpty())
		{
			return;
		}
		for (Integer packed : drop)
		{
			PERSIST_BLOCKED_DEST_REGIONS.remove(packed);
			PERSIST_BLOCKED_DESTS.remove(packed);
			PERSIST_BLOCKED_DEST_METHODS.remove(packed);
		}
		flush();
	}

	static Map<Integer, LeaguesRegion> getBlacklistedDestinationRegionsSnapshot()
	{
		ensureLoaded();
		return Collections.unmodifiableMap(new HashMap<>(PERSIST_BLOCKED_DEST_REGIONS));
	}

	static void persistBlacklistDestination(int packedWorldPoint, LeaguesRegion region, String method)
	{
		if (packedWorldPoint == 0)
		{
			return;
		}
		ensureLoaded();
		PERSIST_BLOCKED_DESTS.add(packedWorldPoint);
		if (region != null)
		{
			PERSIST_BLOCKED_DEST_REGIONS.put(packedWorldPoint, region);
		}
		if (method != null && !method.isEmpty())
		{
			PERSIST_BLOCKED_DEST_METHODS.put(packedWorldPoint, method);
		}
		flush();
	}

	static Optional<WorldPoint> getCachedRegionLanding(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		ensureLoaded();
		WorldPoint landing = PERSIST_REGION_LANDINGS.get(region);
		if (landing == null)
		{
			return Optional.empty();
		}
		return Optional.of(landing);
	}

	static void persistRegionLanding(LeaguesRegion region, WorldPoint landing)
	{
		Objects.requireNonNull(region, "region");
		if (landing == null)
		{
			return;
		}
		ensureLoaded();
		PERSIST_REGION_LANDINGS.put(region, landing);
		flush();
	}

	static void flush()
	{
		if (!maybePurgeLegacyProfileKeys())
		{
			writeShareFile();
		}
	}

	private static boolean maybePurgeLegacyProfileKeys()
	{
		if (PROFILE_KEYS_PURGED.get())
		{
			return false;
		}
		try
		{
			if (isShareFileMarkerSet(KEY_PROFILE_PURGE_MARKER))
			{
				PROFILE_KEYS_PURGED.set(true);
				return false;
			}
		}
		catch (Exception e)
		{
			log.debug("[Leagues] purge marker probe failed: {}", e.toString());
		}

		ConfigManager cm = Microbot.getConfigManager();
		if (cm == null)
		{
			return false;
		}
		if (!PROFILE_KEYS_PURGED.compareAndSet(false, true))
		{
			return false;
		}

		try
		{
			cm.unsetConfiguration(PERSIST_GROUP, KEY_BLOCKED_DESTS);
			cm.unsetConfiguration(PERSIST_GROUP, KEY_BLOCKED_DEST_REGIONS);
			cm.unsetConfiguration(PERSIST_GROUP, KEY_BLOCKED_DEST_METHODS);
			cm.unsetConfiguration(PERSIST_GROUP, KEY_REGION_LANDINGS);
			writeShareFile();
			return true;
		}
		catch (Exception e)
		{
			PROFILE_KEYS_PURGED.set(false);
			log.debug("[Leagues] legacy profile purge failed (will retry): {}", e.toString());
			return false;
		}
	}

	private static boolean isShareFileMarkerSet(String markerKey)
	{
		if (markerKey == null || markerKey.isEmpty())
		{
			return false;
		}
		try
		{
			Path file = shareFile();
			if (!Files.exists(file))
			{
				return false;
			}
			try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				String rawLine;
				while ((rawLine = br.readLine()) != null)
				{
					String line = rawLine.trim();
					if (line.isEmpty() || line.startsWith("#"))
					{
						continue;
					}
					int eq = line.indexOf('=');
					if (eq <= 0)
					{
						continue;
					}
					String key = line.substring(0, eq).trim();
					if (!markerKey.equals(key))
					{
						continue;
					}
					String val = line.substring(eq + 1).trim();
					return "true".equalsIgnoreCase(val) || "1".equals(val);
				}
			}
		}
		catch (Exception e)
		{
			log.debug("[Leagues] share marker read failed key={} type={} msg={}",
					markerKey, e.getClass().getName(), e.getMessage());
		}
		return false;
	}

	private static void loadFromShareFile()
	{
		try
		{
			Path file = shareFile();
			if (!Files.exists(file))
			{
				return;
			}
			try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8))
			{
				String rawLine;
				while ((rawLine = br.readLine()) != null)
				{
					String line = rawLine != null ? rawLine.trim() : "";
					if (line.isEmpty() || line.startsWith("#"))
					{
						continue;
					}
					int eq = line.indexOf('=');
					if (eq <= 0 || eq >= line.length() - 1)
					{
						log.debug("[Leagues] share file malformed line (no '=' value)");
						continue;
					}
					String key = line.substring(0, eq).trim();
					String val = line.substring(eq + 1).trim();
					if (key.equals(KEY_BLOCKED_DESTS))
					{
						loadCsvInts(val, PERSIST_BLOCKED_DESTS);
					}
					else if (key.equals(KEY_BLOCKED_DEST_REGIONS))
					{
						loadDestRegionMap(val, PERSIST_BLOCKED_DEST_REGIONS);
					}
					else if (key.equals(KEY_BLOCKED_DEST_METHODS))
					{
						loadDestStringMap(val, PERSIST_BLOCKED_DEST_METHODS);
					}
					else if (key.equals(KEY_REGION_LANDINGS))
					{
						loadRegionLandings(val, PERSIST_REGION_LANDINGS);
					}
					else if (key.equals(KEY_CALIBRATION_CONSENT))
					{
						if ("allowed".equalsIgnoreCase(val))
						{
							CALIBRATION_CONSENT_ALLOWED.set(true);
							CALIBRATION_CONSENT_DENIED.set(false);
						}
						else if ("denied".equalsIgnoreCase(val))
						{
							CALIBRATION_CONSENT_DENIED.set(true);
							CALIBRATION_CONSENT_ALLOWED.set(false);
						}
						else
						{
							CALIBRATION_CONSENT_ALLOWED.set(false);
							CALIBRATION_CONSENT_DENIED.set(false);
						}
					}
					else
					{
						log.debug("[Leagues] share file ignored key={}", key);
					}
				}
			}
		}
		catch (IOException e)
		{
			log.debug("[Leagues] share file read failed path={}: {}", shareFile(), e.getMessage());
		}
	}

	private static void writeShareFile()
	{
		synchronized (SHARE_FILE_IO_LOCK)
		{
			Path tmp = null;
			try
			{
				Path file = shareFile();
				Files.createDirectories(file.getParent());
				String consentLine = "";
				if (CALIBRATION_CONSENT_ALLOWED.get() || CALIBRATION_CONSENT_DENIED.get())
				{
					consentLine = KEY_CALIBRATION_CONSENT + "="
							+ (CALIBRATION_CONSENT_ALLOWED.get() ? "allowed" : "denied")
							+ "\n";
				}
				String content = ""
						+ "# Microbot Leagues transport cache (shareable)\n"
						+ "# Copy between machines/profiles to share learned data.\n"
						+ "# catalogVersion=" + leaguesCatalogVersion() + "\n"
						+ KEY_PROFILE_PURGE_MARKER + "=true\n"
						+ KEY_BLOCKED_DESTS + "=" + joinCsvInts(PERSIST_BLOCKED_DESTS) + "\n"
						+ KEY_BLOCKED_DEST_REGIONS + "=" + joinDestRegionMap(PERSIST_BLOCKED_DEST_REGIONS) + "\n"
						+ KEY_BLOCKED_DEST_METHODS + "=" + joinDestStringMap(PERSIST_BLOCKED_DEST_METHODS) + "\n"
						+ KEY_REGION_LANDINGS + "=" + joinRegionLandings(PERSIST_REGION_LANDINGS) + "\n"
						+ consentLine;

				tmp = Files.createTempFile(file.getParent(), file.getFileName().toString() + ".", ".tmp");
				Files.writeString(tmp, content, StandardCharsets.UTF_8);
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (Exception e)
			{
				if (tmp != null)
				{
					try
					{
						Files.deleteIfExists(tmp);
					}
					catch (Exception ignored)
					{
					}
				}
				log.debug("[Leagues] share file write failed path={} type={} msg={}",
						shareFile(), e.getClass().getName(), e.getMessage());
			}
		}
	}

	private static void loadCsvInts(String raw, Set<Integer> out)
	{
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		String[] parts = raw.split(",");
		for (int i = 0; i < parts.length; i++)
		{
			String p = parts[i].trim();
			if (p.isEmpty())
			{
				continue;
			}
			try
			{
				out.add(Integer.parseInt(p));
			}
			catch (NumberFormatException ignored)
			{
			}
		}
	}

	private static String joinCsvInts(Set<Integer> values)
	{
		if (values.isEmpty())
		{
			return "";
		}
		StringBuilder sb = new StringBuilder(values.size() * 6);
		boolean first = true;
		for (Integer v : values)
		{
			if (v == null)
			{
				continue;
			}
			if (!first)
			{
				sb.append(',');
			}
			first = false;
			sb.append(v);
		}
		return sb.toString();
	}

	private static void loadDestRegionMap(String raw, Map<Integer, LeaguesRegion> out)
	{
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		String[] entries = raw.split(";");
		for (int i = 0; i < entries.length; i++)
		{
			String e = entries[i].trim();
			if (e.isEmpty())
			{
				continue;
			}
			int eq = e.indexOf('=');
			if (eq <= 0 || eq >= e.length() - 1)
			{
				continue;
			}
			try
			{
				int packed = Integer.parseInt(e.substring(0, eq).trim());
				String regionName = e.substring(eq + 1).trim();
				LeaguesRegion region = LeaguesRegion.valueOf(regionName);
				out.put(packed, region);
			}
			catch (Exception ignored)
			{
			}
		}
	}

	private static String joinDestRegionMap(Map<Integer, LeaguesRegion> map)
	{
		if (map.isEmpty())
		{
			return "";
		}
		StringBuilder sb = new StringBuilder(map.size() * 10);
		boolean first = true;
		for (Map.Entry<Integer, LeaguesRegion> e : map.entrySet())
		{
			if (e.getKey() == null || e.getValue() == null)
			{
				continue;
			}
			if (!first)
			{
				sb.append(';');
			}
			first = false;
			sb.append(e.getKey()).append('=').append(e.getValue().name());
		}
		return sb.toString();
	}

	private static void loadDestStringMap(String raw, Map<Integer, String> out)
	{
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		String[] entries = raw.split(";");
		for (int i = 0; i < entries.length; i++)
		{
			String e = entries[i].trim();
			if (e.isEmpty())
			{
				continue;
			}
			int eq = e.indexOf('=');
			if (eq <= 0 || eq >= e.length() - 1)
			{
				continue;
			}
			try
			{
				int packed = Integer.parseInt(e.substring(0, eq).trim());
				String val = e.substring(eq + 1).trim();
				out.put(packed, val);
			}
			catch (Exception ignored)
			{
			}
		}
	}

	private static String joinDestStringMap(Map<Integer, String> map)
	{
		if (map.isEmpty())
		{
			return "";
		}
		StringBuilder sb = new StringBuilder(map.size() * 12);
		boolean first = true;
		for (Map.Entry<Integer, String> e : map.entrySet())
		{
			Integer k = e.getKey();
			String v = e.getValue();
			if (k == null || v == null || v.isEmpty())
			{
				continue;
			}
			String safe = v.replace(";", " ").replace("=", " ").trim();
			if (safe.isEmpty())
			{
				continue;
			}
			if (!first)
			{
				sb.append(';');
			}
			first = false;
			sb.append(k).append('=').append(safe);
		}
		return sb.toString();
	}

	private static void loadRegionLandings(String raw, Map<LeaguesRegion, WorldPoint> out)
	{
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		String[] entries = raw.split(";");
		for (int i = 0; i < entries.length; i++)
		{
			String e = entries[i].trim();
			if (e.isEmpty())
			{
				continue;
			}
			int eq = e.indexOf('=');
			if (eq <= 0 || eq >= e.length() - 1)
			{
				continue;
			}
			try
			{
				LeaguesRegion r = LeaguesRegion.valueOf(e.substring(0, eq).trim());
				String[] parts = e.substring(eq + 1).trim().split("\\s+");
				if (parts.length != 3)
				{
					continue;
				}
				int x = Integer.parseInt(parts[0]);
				int y = Integer.parseInt(parts[1]);
				int p = Integer.parseInt(parts[2]);
				out.put(r, new WorldPoint(x, y, p));
			}
			catch (Exception ignored)
			{
			}
		}
	}

	private static String joinRegionLandings(Map<LeaguesRegion, WorldPoint> map)
	{
		if (map.isEmpty())
		{
			return "";
		}
		StringBuilder sb = new StringBuilder(map.size() * 18);
		boolean first = true;
		for (Map.Entry<LeaguesRegion, WorldPoint> e : map.entrySet())
		{
			LeaguesRegion r = e.getKey();
			WorldPoint wp = e.getValue();
			if (r == null || wp == null)
			{
				continue;
			}
			if (!first)
			{
				sb.append(';');
			}
			first = false;
			sb.append(r.name()).append('=')
					.append(wp.getX()).append(' ')
					.append(wp.getY()).append(' ')
					.append(wp.getPlane());
		}
		return sb.toString();
	}
}
