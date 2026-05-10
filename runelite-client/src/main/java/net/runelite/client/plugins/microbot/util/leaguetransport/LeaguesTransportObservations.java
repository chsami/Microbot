package net.runelite.client.plugins.microbot.util.leaguetransport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JSONL observations and catalog file I/O for Leagues transports.
 */
@Slf4j
final class LeaguesTransportObservations
{
	private LeaguesTransportObservations()
	{
	}

	private static Path observationsFile()
	{
		return LeaguesTransportPersistence.leaguesVersionDir().resolve("leagues-transport-observations.jsonl");
	}

	private static final int CATALOG_SCHEMA_VERSION = 2;
	private static final long LEAGUES_OBS_JSONL_MAX_BYTES = 5L * 1024 * 1024;
	private static final int LEAGUES_OBS_JSONL_ROTATION_SLOTS = 3;

	private static Path catalogFile()
	{
		return LeaguesTransportPersistence.leaguesVersionDir().resolve("leagues-transport-catalog.jsonl");
	}

	private static Path lockCatalogueFile()
	{
		return LeaguesTransportPersistence.leaguesVersionDir().resolve("leagues-lock-catalogue.jsonl");
	}

	private static final Set<String> LOCK_CATALOGUE_KEYS_SEEN = ConcurrentHashMap.newKeySet();
	/** Serializes bootstrap + dedupe rollback; append body runs outside this lock. */
	private static final Object LOCK_CATALOGUE_BOOTSTRAP_LOCK = new Object();
	private static volatile boolean lockCatalogueKeysBootstrapped = false;
	/** When leagues season / catalog version changes, drop in-memory keys so the next append rescans the new file. */
	private static volatile String lockCatalogueKeysBootstrappedForCatalogVersion = null;
	private static final int LEAGUES_LOCK_CATALOGUE_BOOTSTRAP_MAX_LINES = 100_000;

	private static Path catalogDir()
	{
		return LeaguesTransportPersistence.leaguesVersionDir().resolve("leagues-transport-catalog.d");
	}

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private static final Map<String, CatalogFileSnapshot> CATALOG_FILE_PARSE_CACHE = new ConcurrentHashMap<>();
	private static final AtomicBoolean LOGGED_OLD_CATALOG_SCHEMA = new AtomicBoolean(false);

	private static final class CatalogFileSnapshot
	{
		private final long mtimeMs;
		private final List<CatalogParsedRow> rows;

		private CatalogFileSnapshot(long mtimeMs, List<CatalogParsedRow> rows)
		{
			this.mtimeMs = mtimeMs;
			this.rows = rows;
		}
	}

	private static final class CatalogParsedRow
	{
		private final LeaguesRegion required;
		private final String dedupeKey;
		private final Transport transport;

		private CatalogParsedRow(LeaguesRegion required, String dedupeKey, Transport transport)
		{
			this.required = required;
			this.dedupeKey = dedupeKey;
			this.transport = transport;
		}
	}

	private static boolean isLeaguesActive()
	{
		return Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE) > 0;
	}

	static void maybeRotateLeaguesObservationsJsonl(Path mainFile)
	{
		try
		{
			if (!Files.exists(mainFile))
			{
				return;
			}
			long sz = Files.size(mainFile);
			if (sz < LEAGUES_OBS_JSONL_MAX_BYTES)
			{
				return;
			}
			Path dir = mainFile.getParent();
			if (dir == null)
			{
				return;
			}
			String base = mainFile.getFileName().toString();
			int lastSlot = LEAGUES_OBS_JSONL_ROTATION_SLOTS - 1;
			if (lastSlot >= 1)
			{
				Path oldest = dir.resolve(base + "." + lastSlot);
				if (Files.exists(oldest))
				{
					Files.delete(oldest);
				}
			}
			for (int slot = lastSlot - 1; slot >= 1; slot--)
			{
				Path from = dir.resolve(base + "." + slot);
				Path to = dir.resolve(base + "." + (slot + 1));
				if (Files.exists(from))
				{
					Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
				}
			}
			Path first = dir.resolve(base + ".1");
			Files.move(mainFile, first, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (IOException e)
		{
			log.debug("[Leagues] observation JSONL rotate failed: {}", e.getMessage());
		}
	}

	static void appendTransportObservationInternal(String phase, Transport transport, Boolean success, String detail)
	{
		if (phase == null || transport == null)
		{
			return;
		}
		if (transport.getType() != TransportType.SEASONAL_TRANSPORT)
		{
			return;
		}
		if (success == null && !"attempt".equals(phase))
		{
			throw new IllegalArgumentException("appendTransportObservation: outcome required unless phase=attempt");
		}

		final Map<Integer, LeaguesRegion> blockedDestRegionsSnapshot =
				LeaguesTransportPersistence.getBlacklistedDestinationRegionsSnapshot();

		try
		{
			Path file = observationsFile();
			Files.createDirectories(file.getParent());
			maybeRotateLeaguesObservationsJsonl(file);

			JsonObject obj = new JsonObject();
			obj.addProperty("kind", "transport-observation");
			obj.addProperty("phase", phase);
			obj.addProperty("tsMs", System.currentTimeMillis());
			obj.addProperty("catalogVersion", LeaguesTransportPersistence.leaguesCatalogVersion());
			obj.addProperty("leaguesActive", isLeaguesActive());
			if (success != null)
			{
				obj.addProperty("success", success);
			}
			if (detail != null && !detail.isEmpty())
			{
				obj.addProperty("detail", detail);
			}

			TransportType type = transport.getType();
			obj.addProperty("transportType", type != null ? type.name() : "");
			obj.addProperty("displayInfo", transport.getDisplayInfo() != null ? transport.getDisplayInfo() : "");
			obj.addProperty("action", transport.getAction() != null ? transport.getAction() : "");
			obj.addProperty("name", transport.getName() != null ? transport.getName() : "");
			obj.addProperty("objectId", transport.getObjectId());
			obj.addProperty("members", transport.isMembers());

			WorldPoint origin = transport.getOrigin();
			WorldPoint dest = transport.getDestination();
			if (origin != null)
			{
				JsonObject o = new JsonObject();
				o.addProperty("x", origin.getX());
				o.addProperty("y", origin.getY());
				o.addProperty("p", origin.getPlane());
				obj.add("origin", o);
			}
			if (dest != null)
			{
				JsonObject d = new JsonObject();
				d.addProperty("x", dest.getX());
				d.addProperty("y", dest.getY());
				d.addProperty("p", dest.getPlane());
				obj.add("destination", d);

				int packed = WorldPointUtil.packWorldPoint(dest);
				obj.addProperty("destPacked", packed);

				LeaguesRegion learned = blockedDestRegionsSnapshot.get(packed);
				if (learned != null)
				{
					obj.addProperty("learnedRegion", learned.name());
				}
			}

			try (Writer w = new OutputStreamWriter(Files.newOutputStream(
					file,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND), StandardCharsets.UTF_8))
			{
				w.write(GSON.toJson(obj));
				w.write("\n");
			}
		}
		catch (Exception e)
		{
			log.debug("[Leagues] observation append failed: {}", e.getMessage());
		}
	}

	static void appendCatalogTransport(LeaguesRegion requiredRegion, Transport transport, String note)
	{
		if (requiredRegion == null || transport == null || transport.getDestination() == null)
		{
			return;
		}

		try
		{
			Path file = catalogFile();
			Files.createDirectories(file.getParent());

			JsonObject obj = new JsonObject();
			obj.addProperty("kind", "catalog-transport");
			obj.addProperty("catalogVersion", LeaguesTransportPersistence.leaguesCatalogVersion());
			obj.addProperty("schema", CATALOG_SCHEMA_VERSION);
			obj.addProperty("tsMs", System.currentTimeMillis());
			obj.addProperty("requiredRegion", requiredRegion.name());
			obj.addProperty("transportType", transport.getType() != null ? transport.getType().name() : "");
			obj.addProperty("displayInfo", transport.getDisplayInfo() != null ? transport.getDisplayInfo() : "");
			obj.addProperty("action", transport.getAction() != null ? transport.getAction() : "");
			obj.addProperty("name", transport.getName() != null ? transport.getName() : "");
			obj.addProperty("objectId", transport.getObjectId());
			obj.addProperty("members", transport.isMembers());
			if (note != null && !note.isEmpty())
			{
				obj.addProperty("note", note);
			}

			WorldPoint origin = transport.getOrigin();
			WorldPoint dest = transport.getDestination();
			if (origin != null)
			{
				JsonObject o = new JsonObject();
				o.addProperty("x", origin.getX());
				o.addProperty("y", origin.getY());
				o.addProperty("p", origin.getPlane());
				obj.add("origin", o);
			}
			JsonObject d = new JsonObject();
			d.addProperty("x", dest.getX());
			d.addProperty("y", dest.getY());
			d.addProperty("p", dest.getPlane());
			obj.add("destination", d);

			try (Writer w = new OutputStreamWriter(Files.newOutputStream(
					file,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND), StandardCharsets.UTF_8))
			{
				w.write(GSON.toJson(obj));
				w.write("\n");
			}
		}
		catch (Exception e)
		{
			log.debug("[Leagues] catalog append failed: {}", e.getMessage());
		}
	}

	private static void bootstrapLockCatalogueKeysFromDiskIfNeeded()
	{
		String catalogVersion = LeaguesTransportPersistence.leaguesCatalogVersion();
		if (lockCatalogueKeysBootstrapped
				&& lockCatalogueKeysBootstrappedForCatalogVersion != null
				&& !catalogVersion.equals(lockCatalogueKeysBootstrappedForCatalogVersion))
		{
			synchronized (LOCK_CATALOGUE_BOOTSTRAP_LOCK)
			{
				if (lockCatalogueKeysBootstrapped
						&& lockCatalogueKeysBootstrappedForCatalogVersion != null
						&& !catalogVersion.equals(lockCatalogueKeysBootstrappedForCatalogVersion))
				{
					LOCK_CATALOGUE_KEYS_SEEN.clear();
					lockCatalogueKeysBootstrapped = false;
					lockCatalogueKeysBootstrappedForCatalogVersion = null;
				}
			}
		}
		if (lockCatalogueKeysBootstrapped)
		{
			return;
		}
		synchronized (LOCK_CATALOGUE_BOOTSTRAP_LOCK)
		{
			if (lockCatalogueKeysBootstrapped)
			{
				return;
			}
			Path file = lockCatalogueFile();
			if (Files.exists(file))
			{
				try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8))
				{
					String line;
					int lineNum = 0;
					while ((line = br.readLine()) != null)
					{
						lineNum++;
						if (lineNum > LEAGUES_LOCK_CATALOGUE_BOOTSTRAP_MAX_LINES)
						{
							log.warn("[Leagues] lock-catalogue bootstrap stopped after {} lines (max={}); dedupe may miss older keys",
									lineNum, LEAGUES_LOCK_CATALOGUE_BOOTSTRAP_MAX_LINES);
							break;
						}
						line = line.trim();
						if (line.isEmpty())
						{
							continue;
						}
						JsonObject obj;
						try
						{
							obj = GSON.fromJson(line, JsonObject.class);
						}
						catch (Exception ignored)
						{
							continue;
						}
						if (obj != null
								&& obj.has("kind")
								&& "lock-catalogue".equals(obj.get("kind").getAsString())
								&& obj.has("k"))
						{
							try
							{
								LOCK_CATALOGUE_KEYS_SEEN.add(obj.get("k").getAsString());
							}
							catch (Exception ignored)
							{
							}
						}
					}
				}
				catch (IOException e)
				{
					log.debug("[Leagues] lock-catalogue bootstrap read failed: {}", e.getMessage());
				}
			}
			lockCatalogueKeysBootstrapped = true;
			lockCatalogueKeysBootstrappedForCatalogVersion = catalogVersion;
		}
	}

	/**
	 * Append one {@code kind=lock-catalogue} JSONL row; skips when dedupe key {@code k} already exists on disk or in-memory set.
	 */
	static void appendLockCatalogueEntry(LeaguesRegion region, int packedDest, String methodRaw)
	{
		if (region == null)
		{
			return;
		}
		bootstrapLockCatalogueKeysFromDiskIfNeeded();
		String normalizedMethod = LeaguesTransportLockCatalogue.normalizeLockCatalogueMethod(methodRaw);
		String k = LeaguesTransportLockCatalogue.buildDedupeKey(packedDest, normalizedMethod);
		synchronized (LOCK_CATALOGUE_BOOTSTRAP_LOCK)
		{
			if (!LOCK_CATALOGUE_KEYS_SEEN.add(k))
			{
				return;
			}
		}

		try
		{
			Path file = lockCatalogueFile();
			Files.createDirectories(file.getParent());

			JsonObject obj = new JsonObject();
			obj.addProperty("kind", "lock-catalogue");
			obj.addProperty("catalogVersion", LeaguesTransportPersistence.leaguesCatalogVersion());
			obj.addProperty("r", region.name());
			obj.addProperty("d", packedDest);
			obj.addProperty("t", System.currentTimeMillis());
			obj.addProperty("m", normalizedMethod);
			obj.addProperty("k", k);

			try (Writer w = new OutputStreamWriter(Files.newOutputStream(
					file,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.APPEND), StandardCharsets.UTF_8))
			{
				w.write(GSON.toJson(obj));
				w.write("\n");
			}
		}
		catch (Exception e)
		{
			synchronized (LOCK_CATALOGUE_BOOTSTRAP_LOCK)
			{
				LOCK_CATALOGUE_KEYS_SEEN.remove(k);
			}
			log.debug("[Leagues] lock-catalogue append failed: {}", e.getMessage());
		}
	}

	static List<Transport> loadCatalogTransports(Set<LeaguesRegion> unlockedRegions)
	{
		if (unlockedRegions == null || unlockedRegions.isEmpty())
		{
			return Collections.emptyList();
		}

		List<Transport> out = new ArrayList<>();
		Set<String> seenKeys = new HashSet<>();

		List<Path> sources = new ArrayList<>();
		Path file = catalogFile();
		Path dir = catalogDir();
		sources.add(file);

		try
		{
			if (Files.isDirectory(dir))
			{
				try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.jsonl"))
				{
					for (Path p : ds)
					{
						sources.add(p);
					}
				}
			}
		}
		catch (Exception ignored)
		{
		}

		for (Path source : sources)
		{
			if (source == null || !Files.exists(source))
			{
				continue;
			}
			for (CatalogParsedRow row : loadCatalogFileRowsCached(source))
			{
				if (!unlockedRegions.contains(row.required))
				{
					continue;
				}
				if (!seenKeys.add(row.dedupeKey))
				{
					continue;
				}
				out.add(row.transport);
			}
		}

		return out;
	}

	private static List<CatalogParsedRow> loadCatalogFileRowsCached(Path source)
	{
		if (source == null || !Files.exists(source))
		{
			return Collections.emptyList();
		}
		try
		{
			long mtime = Files.getLastModifiedTime(source).toMillis();
			String cacheKey = source.toAbsolutePath().normalize().toString();
			CatalogFileSnapshot snap = CATALOG_FILE_PARSE_CACHE.get(cacheKey);
			if (snap != null && snap.mtimeMs == mtime)
			{
				return snap.rows;
			}
			List<CatalogParsedRow> rows = parseCatalogFileRows(source);
			CATALOG_FILE_PARSE_CACHE.put(cacheKey, new CatalogFileSnapshot(mtime, rows));
			return rows;
		}
		catch (IOException e)
		{
			return Collections.emptyList();
		}
	}

	private static List<CatalogParsedRow> parseCatalogFileRows(Path source)
	{
		List<CatalogParsedRow> out = new ArrayList<>();
		try (BufferedReader br = Files.newBufferedReader(source, StandardCharsets.UTF_8))
		{
			String line;
			while ((line = br.readLine()) != null)
			{
				line = line.trim();
				if (line.isEmpty())
				{
					continue;
				}

				JsonObject obj;
				try
				{
					obj = GSON.fromJson(line, JsonObject.class);
				}
				catch (Exception ignored)
				{
					continue;
				}

				if (obj == null || !obj.has("kind") || !"catalog-transport".equals(obj.get("kind").getAsString()))
				{
					continue;
				}

				int schema = 0;
				try
				{
					schema = obj.has("schema") ? obj.get("schema").getAsInt() : 0;
				}
				catch (Exception ignored)
				{
					schema = 0;
				}
				if (schema != CATALOG_SCHEMA_VERSION)
				{
					if (LOGGED_OLD_CATALOG_SCHEMA.compareAndSet(false, true))
					{
						log.info("[Leagues] ignoring old catalog schema (expected {}, got {})", CATALOG_SCHEMA_VERSION, schema);
					}
					continue;
				}

				String req = obj.has("requiredRegion") ? obj.get("requiredRegion").getAsString() : "";
				LeaguesRegion required;
				try
				{
					required = req != null && !req.isEmpty() ? LeaguesRegion.valueOf(req) : null;
				}
				catch (Exception e)
				{
					required = null;
				}
				if (required == null)
				{
					continue;
				}

				String typeRaw = obj.has("transportType") ? obj.get("transportType").getAsString() : "";
				TransportType type;
				try
				{
					type = typeRaw != null && !typeRaw.isEmpty() ? TransportType.valueOf(typeRaw) : null;
				}
				catch (Exception e)
				{
					type = null;
				}
				if (type == null)
				{
					continue;
				}

				WorldPoint dest = parsePoint(
						obj.has("destination") && obj.get("destination").isJsonObject()
								? obj.getAsJsonObject("destination")
								: null);
				if (dest == null)
				{
					continue;
				}
				WorldPoint origin = parsePoint(
						obj.has("origin") && obj.get("origin").isJsonObject()
								? obj.getAsJsonObject("origin")
								: null);

				String displayInfo = obj.has("displayInfo") ? obj.get("displayInfo").getAsString() : "";
				boolean members = obj.has("members") && obj.get("members").getAsBoolean();
				String action = obj.has("action") ? obj.get("action").getAsString() : "";
				String name = obj.has("name") ? obj.get("name").getAsString() : "";
				int objectId = obj.has("objectId") ? obj.get("objectId").getAsInt() : -1;

				String key = required.name() + "|" + type.name() + "|" +
						(origin != null ? WorldPointUtil.packWorldPoint(origin) : 0) + "|" +
						WorldPointUtil.packWorldPoint(dest) + "|" +
						displayInfo + "|" + action + "|" + objectId;

				Transport t;
				if (origin == null)
				{
					t = new Transport(dest, displayInfo, type, members, 31, (Set<Set<Integer>>) null);
				}
				else if (objectId > 0 && action != null && !action.isEmpty())
				{
					t = new Transport(origin, dest, displayInfo, type, members, action, name, objectId);
				}
				else
				{
					t = new Transport(origin, dest, displayInfo, type, members, 1);
				}

				out.add(new CatalogParsedRow(required, key, t));
			}
		}
		catch (Exception ignored)
		{
		}
		return out;
	}

	private static final int PARSE_POINT_COORD_MAX_EXCLUSIVE = 16384;
	private static final int PARSE_POINT_PLANE_MAX_INCLUSIVE = 3;

	private static WorldPoint parsePoint(JsonObject obj)
	{
		if (obj == null)
		{
			return null;
		}
		try
		{
			int x = obj.get("x").getAsInt();
			int y = obj.get("y").getAsInt();
			int p = obj.get("p").getAsInt();
			if (x < 0 || x >= PARSE_POINT_COORD_MAX_EXCLUSIVE
					|| y < 0 || y >= PARSE_POINT_COORD_MAX_EXCLUSIVE
					|| p < 0 || p > PARSE_POINT_PLANE_MAX_INCLUSIVE)
			{
				log.warn("[Leagues] catalog parsePoint out of bounds x={} y={} p={}", x, y, p);
				return null;
			}
			return new WorldPoint(x, y, p);
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
