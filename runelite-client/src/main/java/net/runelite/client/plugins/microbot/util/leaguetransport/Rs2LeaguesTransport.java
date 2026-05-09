package net.runelite.client.plugins.microbot.util.leaguetransport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.WorldType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.PrimitiveIntHashMap;
import net.runelite.client.plugins.microbot.util.logging.Rs2LogRateLimit;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

/**
 * Leagues transport glue.
 *
 * Owns:
 * - **Leagues Area** UI teleport + landing calibration
 * - **Blocked-destination cache** (chat "haven't unlocked access..." -> blacklist)
 * - **Catalog/observations** JSONL (shareable)
 *
 * <p>Locked-region chat logging (this class + {@link net.runelite.client.plugins.microbot.MicrobotPlugin} reroute summary):
 * rate-limited summaries; limits chat log flood — full rules in {@link #recordBlockedDestinationFromChat}.
 *
 * Entry points:
 * - {@link #leaguesContext()} for pathfinder gating/injection
 * - {@link #injectLeaguesTransports(PathfinderConfig, LeaguesContext, java.util.Set, java.util.Map, PrimitiveIntHashMap, java.util.Map)} for pathfinder refresh
 * - {@link #tryHandleLeaguesAreaTransport(Transport)} / {@link #tryHandleLeaguesAreaTransportResult(Transport)} for walker seasonal execution
 *
 * <p><b>Threading:</b> Do not call {@link #leaguesTeleport} (or {@link #tryHandleLeaguesAreaTransport}) from the RuneLite
 * client thread — they synchronize with client-thread UI and would stall the client. {@link #leaguesTeleport} fails fast with
 * {@link LeaguesTeleportFailureReason#INVOKED_ON_CLIENT_THREAD} instead of hanging only when a live {@link Client} is available;
 * a null client returns {@link LeaguesTeleportFailureReason#CLIENT_UNAVAILABLE}. Safe from worker/script threads spawned by the
 * walker (blocking those callers until teleport settles). Calibration runs {@link #leaguesTeleport} on a daemon thread.
 */
@Slf4j
public final class Rs2LeaguesTransport
{
	private static final Pattern LEAGUES_AREA_PREFIX = Pattern.compile("(?i)leagues\\s*area:\\s*");
	/** Defensive bound before sanitize+regex on locked-region chat messages. */
	public static final int LEAGUES_LOCK_CHAT_MAX_NORMALIZE_CHARS = 4096;
	public static final int LEAGUES_LOCK_CHAT_TRUNC_WARN_INTERVAL = 200;
	/** After {@link net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer#sanitizeForParsing(String)} (lowercased, tags/entities removed). */
	private static final Pattern LEAGUES_LOCKED_REGION_CHAT = Pattern.compile(
			"(?:"
					+ "haven[''\\u2019\\u2018\\u02BC\\u2032`]*t\\s+unlocked\\s+access\\s+to\\s+the"
					+ "|don[''\\u2019\\u2018\\u02BC\\u2032`]*t\\s+have\\s+access\\s+to\\s+the"
					+ "|do\\s+not\\s+have\\s+access\\s+to\\s+the"
					+ ")\\s+(.+)\\s+area(?:\\s*\\([^)]+\\))?(?:\\s+\\p{Alnum}+)*\\s*[\\p{Punct}]*$");
	// Private by design: callers should go through captureLockedRegionFrom* helpers (pattern may change with Jagex copy).
	// Constants above must match MicrobotPlugin#clipLeaguesLockChatRawForMatch cap + warn cadence; plugin owns warn counter state.
	/** Caps {@link #lastTransportAttempt} method label — displayInfo from merged transport defs can be huge or hostile. */
	private static final int TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX = 256;

	// Bind later chat message to last attempted transport.
	// Must be atomic to avoid region-lock messages attributing to wrong attempt.
	public static final class TransportAttemptSnapshot
	{
		private final Integer packedDest;
		private final String method;
		private final long tsMs;

		private TransportAttemptSnapshot(Integer packedDest, String method, long tsMs)
		{
			this.packedDest = packedDest;
			this.method = method;
			this.tsMs = tsMs;
		}

		public Integer getPackedDest()
		{
			return packedDest;
		}

		public String getMethod()
		{
			return method;
		}

		public long getTsMs()
		{
			return tsMs;
		}
	}

	/**
	 * Newest seasonal transport click (head of the recent-attempt ring when non-empty).
	 * Chat attribution uses {@link #findTransportAttemptForLockedRegionChat} over a short ring so Leagues Area + MoA back-to-back
	 * clicks do not always lose the row that matches locked-region copy.
	 */
	private static volatile TransportAttemptSnapshot lastTransportAttempt = null;

	private static final int RECENT_TRANSPORT_ATTEMPTS_MAX = 4;
	private static final Object RECENT_TRANSPORT_ATTEMPTS_LOCK = new Object();
	private static final TransportAttemptSnapshot[] RECENT_TRANSPORT_ATTEMPTS = new TransportAttemptSnapshot[RECENT_TRANSPORT_ATTEMPTS_MAX];
	private static int recentTransportAttemptsCount = 0;

	/** Previous {@link LeaguesContext#unlockedRegions} seen in {@link #injectLeaguesTransports} — detects new unlocks for blacklist prune. */
	private static volatile EnumSet<LeaguesRegion> lastInjectedUnlockedForBlacklistPrune = null;
	/**
	 * Distinct parse-miss keys for locked-region chat that failed {@link #parseRegionName}.
	 * Keys are capped normalized strings plus a hex {@link String#hashCode()} suffix when truncated so 160-char prefixes
	 * alone cannot collapse distinct misses ({@link String#hashCode()} is not injective — rare false merge under the cap).
	 */
	private static final Set<String> LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES = ConcurrentHashMap.newKeySet();
	/** While {@link #LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES} is full: first sighting per {@code missKey} bumps after-cap stats; repeats skip (we cannot store new samples). */
	private static final Set<String> LEAGUES_PARSE_MISS_AT_CAP_SEEN = ConcurrentHashMap.newKeySet();
	/** Best-effort count (racy): debug-only dedupe can exceed cap by a small amount under contention. */
	private static final AtomicInteger LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT = new AtomicInteger(0);
	/** Bounds {@link #LEAGUES_PARSE_MISS_AT_CAP_SEEN} so long sessions with novel region strings cannot grow memory without limit. */
	private static final int LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX = 512;
	private static final Object LEAGUES_PARSE_MISS_SAMPLES_LOCK = new Object();
	private static final Object LEAGUES_PARSE_MISS_AT_CAP_LOCK = new Object();
	private static final int LEAGUES_PARSE_MISS_DISTINCT_LOG_CAP = 64;
	/** After cap, counts logged-after-cap messages for periodic visibility ({@link #LEAGUES_PARSE_MISS_AFTER_CAP_LOG_INTERVAL}). */
	private static final AtomicInteger LEAGUES_PARSE_MISS_AFTER_CAP_COUNT = new AtomicInteger(0);
	private static final int LEAGUES_PARSE_MISS_AFTER_CAP_LOG_INTERVAL = 50;
	private static final AtomicInteger LEAGUES_PARSE_MISS_INFO = new AtomicInteger(0);
	private static final int LEAGUES_PARSE_MISS_INFO_INTERVAL = 25;
	private static final AtomicInteger LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG = new AtomicInteger(0);
	/** Same cadence as {@link #LEAGUES_PARSE_MISS_INFO_INTERVAL} / other Leagues summary lines — change together if tuning spam. */
	private static final int LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG_INTERVAL = 25;
	/** One debug line per JVM if a seasonal row has null {@link Transport#getType()} — indicates bad merged transport data. */
	private static final AtomicBoolean LOGGED_NULL_SEASONAL_TRANSPORT_TYPE = new AtomicBoolean(false);
	/** One debug line per JVM if TELEPORT_ROW widget name drifts after click (layout / shield mismatch). */
	private static final AtomicBoolean LOGGED_TELEPORT_ROW_NAME_MISMATCH = new AtomicBoolean(false);
	/** Rate-limit for {@link #recordBlockedDestinationFromChat} success {@code log.info} (no per-message DEBUG; see method Javadoc). */
	private static final AtomicInteger LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO = new AtomicInteger(0);
	private static final int LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO_INTERVAL = 25;
	private static final long LEAGUES_REROUTE_DEDUPE_WINDOW_MS = 10_000L;
	private static final Object LEAGUES_REROUTE_LOCK = new Object();
	private static volatile long lastLeaguesRerouteMs = 0L;
	private static volatile int lastLeaguesReroutePackedDest = Integer.MIN_VALUE;
	private static volatile String lastLeaguesRerouteRegion = "";

	public static Integer getLastTransportAttemptPackedDest()
	{
		TransportAttemptSnapshot s = lastTransportAttempt;
		return s != null ? s.packedDest : null;
	}

	public static String getLastTransportAttemptMethod()
	{
		TransportAttemptSnapshot s = lastTransportAttempt;
		return s != null ? s.method : null;
	}

	/**
	 * Prefix on {@link TransportAttemptSnapshot#getMethod()} for Leagues Area seasonal rows
	 * (see {@link #buildTransportAttemptMethodLabel} + injected displayInfo {@code Leagues Area: …}).
	 */
	private static final String LEAGUES_AREA_ATTEMPT_PREFIX =
			TransportType.SEASONAL_TRANSPORT + ":Leagues Area:";

	/**
	 * Whether a Leagues Area teleport was attempted recently but {@link #isTeleportInProgress()} is not
	 * yet true (calibration daemon, UI delay, pre-click stall). Used by {@link Rs2Walker} stall logic.
	 *
	 * @param maxAgeMs non-negative upper bound on attempt age; larger windows catch slower UI paths
	 */
	public static boolean isLeaguesAreaTeleportPending(long maxAgeMs)
	{
		if (maxAgeMs < 0)
		{
			return false;
		}
		TransportAttemptSnapshot s = lastTransportAttempt;
		if (s == null)
		{
			return false;
		}
		String m = s.getMethod();
		if (m == null || !m.startsWith(LEAGUES_AREA_ATTEMPT_PREFIX))
		{
			return false;
		}
		return System.currentTimeMillis() - s.getTsMs() <= maxAgeMs;
	}

	public static TransportAttemptSnapshot getLastTransportAttemptSnapshot()
	{
		return lastTransportAttempt;
	}

	public static void clearLastTransportAttempt()
	{
		lastTransportAttempt = null;
		synchronized (RECENT_TRANSPORT_ATTEMPTS_LOCK)
		{
			for (int i = 0; i < RECENT_TRANSPORT_ATTEMPTS_MAX; i++)
			{
				RECENT_TRANSPORT_ATTEMPTS[i] = null;
			}
			recentTransportAttemptsCount = 0;
		}
	}

	private static void pushRecentTransportAttempt(TransportAttemptSnapshot snap)
	{
		if (snap == null)
		{
			return;
		}
		lastTransportAttempt = snap;
		synchronized (RECENT_TRANSPORT_ATTEMPTS_LOCK)
		{
			for (int i = Math.min(RECENT_TRANSPORT_ATTEMPTS_MAX - 1, recentTransportAttemptsCount); i > 0; i--)
			{
				RECENT_TRANSPORT_ATTEMPTS[i] = RECENT_TRANSPORT_ATTEMPTS[i - 1];
			}
			RECENT_TRANSPORT_ATTEMPTS[0] = snap;
			if (recentTransportAttemptsCount < RECENT_TRANSPORT_ATTEMPTS_MAX)
			{
				recentTransportAttemptsCount++;
			}
		}
	}

	/**
	 * Picks freshest attempt at or below {@code maxAgeMs} that matches locked-region chat {@code regionCaptured}
	 * (same capture string as {@link #captureLockedRegionFromChatRaw}).
	 */
	public static Optional<TransportAttemptSnapshot> findTransportAttemptForLockedRegionChat(
			String regionCaptured, long nowMs, long maxAgeMs)
	{
		if (regionCaptured == null || maxAgeMs < 0L)
		{
			return Optional.empty();
		}
		synchronized (RECENT_TRANSPORT_ATTEMPTS_LOCK)
		{
			for (int i = 0; i < recentTransportAttemptsCount; i++)
			{
				TransportAttemptSnapshot s = RECENT_TRANSPORT_ATTEMPTS[i];
				if (s == null)
				{
					continue;
				}
				long ageMs = nowMs - s.getTsMs();
				if (ageMs > maxAgeMs || ageMs < 0L)
				{
					continue;
				}
				if (attemptSnapshotMatchesLockedChatRegion(s, regionCaptured))
				{
					return Optional.of(s);
				}
			}
		}
		return Optional.empty();
	}

	private static boolean attemptSnapshotMatchesLockedChatRegion(TransportAttemptSnapshot snap, String regionCaptured)
	{
		if (snap == null)
		{
			return false;
		}
		String norm = normalizeRegionNameForLockedChat(regionCaptured);
		if (norm.isEmpty())
		{
			return false;
		}
		LeaguesRegion lrChat = parseRegionNameNormalized(norm);
		String method = snap.getMethod();
		if (method == null)
		{
			return false;
		}
		Optional<LeaguesRegion> lrAttempt = parseLeaguesAreaRegionFromAttemptMethod(method);
		if (lrChat != null && lrAttempt.isPresent())
		{
			return lrChat == lrAttempt.get();
		}
		// MoA / non-Leagues-Area labels: substring on normalized chat token only when enum match is not decisive.
		return method.toLowerCase(Locale.ROOT).contains(norm);
	}

	private static Optional<LeaguesRegion> parseLeaguesAreaRegionFromAttemptMethod(String method)
	{
		if (method == null)
		{
			return Optional.empty();
		}
		String low = method.toLowerCase(Locale.ROOT);
		String key = "leagues area:";
		int i = low.indexOf(key);
		if (i < 0)
		{
			return Optional.empty();
		}
		String rest = method.substring(i + key.length()).trim();
		int pipe = rest.indexOf('|');
		if (pipe >= 0)
		{
			rest = rest.substring(0, pipe);
		}
		rest = Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(rest.trim());
		if (rest.isEmpty())
		{
			return Optional.empty();
		}
		return Optional.ofNullable(parseRegionNameNormalized(rest));
	}

	/**
	 * Updates last transport attempt snapshot for Leagues locked-region chat correlation.
	 * <p>Record all transport attempts while Leagues active so any blocked-region message can be attributed to the most recent
	 * attempted destination (teleports, boats, trees, rings, etc.). This intentionally favors coverage over precision; stale-age
	 * gating in {@link net.runelite.client.plugins.microbot.MicrobotPlugin} prevents long-gap misattribution.
	 * This snapshot lets {@link net.runelite.client.plugins.microbot.MicrobotPlugin} attribute
	 * "haven't unlocked access to X area" to the most recent attempted destination, then blacklist that packed tile for that region.
	 * <p>Chat pairing is best-effort: see {@link #lastTransportAttempt} and the locked-region gamemessage path in
	 * {@link net.runelite.client.plugins.microbot.MicrobotPlugin}. Attempts older than
	 * {@link net.runelite.client.plugins.microbot.MicrobotPlugin#LEAGUES_LOCK_CHAT_MAX_ATTEMPT_AGE_MS} are not attributed.
	 */
	public static void recordTransportAttempt(Transport transport)
	{
		recordTransportAttempt(transport, null);
	}

	/**
	 * @param attemptHandler short tag for locked-region chat logs, e.g. {@code LeaguesArea} or {@code MoA}; appended as
	 *                       {@code |handler=} on the method label when non-null.
	 */
	public static void recordTransportAttempt(Transport transport, String attemptHandler)
	{
		if (transport == null || transport.getDestination() == null)
		{
			return;
		}
		// Only useful while Leagues logic active; avoid churn outside leagues.
		if (!isLeaguesActive())
		{
			return;
		}

		if (transport.getType() == null)
		{
			if (log.isDebugEnabled() && Rs2LogRateLimit.once(LOGGED_NULL_SEASONAL_TRANSPORT_TYPE))
			{
				String di = transport.getDisplayInfo();
				String sample = di == null ? "" : (di.length() > TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX
						? di.substring(0, TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX) + "…"
						: di);
				Integer packedDest = WorldPointUtil.packWorldPoint(transport.getDestination());
				log.debug("recordTransportAttempt: transport.getType() null; check merged transport / TSV. destPacked={} dest={} displayInfoSample='{}'",
						packedDest, transport.getDestination(), sample);
			}
			Integer packed = WorldPointUtil.packWorldPoint(transport.getDestination());
			pushRecentTransportAttempt(new TransportAttemptSnapshot(packed,
					withAttemptHandlerSuffix(buildNullTypeAttemptMethodLabel(transport), attemptHandler),
					System.currentTimeMillis()));
			// Preserve prior behavior: keep a single JSONL attempt line for unexpected/null-typed seasonal rows.
			appendTransportAttemptObservation(transport, "");
			return;
		}

		TransportType type = transport.getType();
		Integer packed = WorldPointUtil.packWorldPoint(transport.getDestination());
		String method = withAttemptHandlerSuffix(buildTransportAttemptMethodLabel(transport), attemptHandler);
		pushRecentTransportAttempt(new TransportAttemptSnapshot(packed, method, System.currentTimeMillis()));

		// Avoid logging JSONL for every attempt; seasonal rows are the primary diagnostic target.
		if (type == TransportType.SEASONAL_TRANSPORT)
		{
			appendTransportAttemptObservation(transport, "");
		}
	}

	private static String withAttemptHandlerSuffix(String method, String attemptHandler)
	{
		if (attemptHandler == null || attemptHandler.isEmpty())
		{
			return method;
		}
		return method + "|handler=" + attemptHandler;
	}

	/**
	 * Same as {@link #tryHandleLeaguesAreaTransportResult(Transport)} but only reports success.
	 *
	 * @apiNote Call from worker/script threads only (same contract as {@link #leaguesTeleport}). Many {@code false} outcomes
	 *          are {@link Optional#empty()} in {@link #tryHandleLeaguesAreaTransportResult} (prefix/region/active) —
	 *          {@link Microbot#status} untouched. For {@link LeaguesTeleportResult} without a second {@link #leaguesTeleport}
	 *          call (duplicate UI work), use {@link #tryHandleLeaguesAreaTransportResult}.
	 */
	public static boolean tryHandleLeaguesAreaTransport(Transport transport)
	{
		return tryHandleLeaguesAreaTransportResult(transport).map(LeaguesTeleportResult::isSuccess).orElse(false);
	}

	/**
	 * Attempts Leagues Area UI teleport for a transport row whose displayInfo matches the Leagues Area prefix.
	 *
	 * @return {@link Optional#empty()} if this transport is not a Leagues Area row or prerequisites fail before
	 *         {@link #leaguesTeleport}; otherwise {@link Optional#of} the exact {@link LeaguesTeleportResult} from that call
	 *         (success or failure). Avoids re-invoking {@link #leaguesTeleport} for diagnostics.
	 * @apiNote Same threading rules as {@link #leaguesTeleport} — not on the RuneLite client thread.
	 */
	public static Optional<LeaguesTeleportResult> tryHandleLeaguesAreaTransportResult(Transport transport)
	{
		if (transport == null || transport.getDisplayInfo() == null)
		{
			return Optional.empty();
		}
		if (!isLeaguesActive())
		{
			return Optional.empty();
		}

		String displayInfo = transport.getDisplayInfo();
		String displayInfoForPrefix = Rs2TextSanitizer.normalizeAsciiColons(displayInfo);
		Matcher areaPrefix = LEAGUES_AREA_PREFIX.matcher(displayInfoForPrefix);
		if (!areaPrefix.lookingAt())
		{
			return Optional.empty();
		}

		// UI menu label after prefix — sanitize to handle any unexpected tags/entities/Unicode.
		String regionRaw = areaPrefix.replaceFirst("").trim();
		String sanitizedRegion = net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(regionRaw);
		LeaguesRegion region = parseRegionName(sanitizedRegion);
		if (region == null)
		{
			if (log.isDebugEnabled())
			{
				log.debug("Leagues Area transport: parseRegionName miss after sanitize; rawLabel='{}' sanitized='{}'",
						regionRaw, sanitizedRegion);
			}
			return Optional.empty();
		}

		recordTransportAttempt(transport, "LeaguesArea");
		LeaguesTeleportResult res = leaguesTeleport(region, DEFAULT_TIMEOUT_MS);
		if (!res.isSuccess() && log.isDebugEnabled())
		{
			log.debug("Leagues Area transport failed: region={} reason={} message={}",
					region, res.getFailureReason(), res.getMessage());
		}

		if (res.isSuccess())
		{
			WorldPoint after = Rs2Player.getWorldLocation();
			if (after != null)
			{
				persistRegionLanding(region, after);
			}
			clearLastTransportAttempt();
		}
		return Optional.of(res);
	}

	private static String buildNullTypeAttemptMethodLabel(Transport transport)
	{
		String di = transport.getDisplayInfo();
		if (di == null || di.isEmpty())
		{
			return "UNKNOWN:";
		}
		if (di.length() <= TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX)
		{
			return "UNKNOWN:" + di;
		}
		return "UNKNOWN:" + di.substring(0, TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX) + "…h"
				+ Integer.toHexString(di.hashCode());
	}

	private static String buildTransportAttemptMethodLabel(Transport transport)
	{
		String prefix = transport.getType() + ":";
		String di = transport.getDisplayInfo();
		if (di == null || di.isEmpty())
		{
			return prefix;
		}
		if (di.length() <= TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX)
		{
			return prefix + di;
		}
		// Suffix uses String#hashCode only — stable across runs for same displayInfo text (JSONL correlation).
		return prefix + di.substring(0, TRANSPORT_ATTEMPT_DISPLAY_INFO_MAX) + "…h"
				+ Integer.toHexString(di.hashCode());
	}

	public static final class LeaguesContext
	{
		private final boolean active;
		private final EnumSet<LeaguesRegion> unlockedRegions;

		private LeaguesContext(boolean active, EnumSet<LeaguesRegion> unlockedRegions)
		{
			this.active = active;
			this.unlockedRegions = unlockedRegions != null ? unlockedRegions : EnumSet.noneOf(LeaguesRegion.class);
		}

		public boolean isActive()
		{
			return active;
		}

		public EnumSet<LeaguesRegion> getUnlockedRegions()
		{
			return unlockedRegions;
		}
	}

	private static final LeaguesContext INACTIVE_CONTEXT =
			new LeaguesContext(false, EnumSet.noneOf(LeaguesRegion.class));

	public static LeaguesContext leaguesContext()
	{
		if (!isLeaguesActive())
		{
			return INACTIVE_CONTEXT;
		}
		return new LeaguesContext(true, unlockedRegions());
	}

	public static boolean isTransportAllowed(LeaguesContext ctx, Transport transport)
	{
		if (transport == null)
		{
			return false;
		}
		if (ctx == null || !ctx.active)
		{
			return true;
		}
		WorldPoint dest = transport.getDestination();
		if (dest == null)
		{
			// Seasonal rows without a destination cannot be gated by Leagues blacklist; drop them when Leagues active.
			return transport.getType() != TransportType.SEASONAL_TRANSPORT;
		}
		int packed = WorldPointUtil.packWorldPoint(dest);
		if (isDestinationBlacklisted(packed))
		{
			return false;
		}
		LeaguesRegion learned = getBlacklistedDestinationRegionsSnapshot().get(packed);
		return learned == null || ctx.unlockedRegions.contains(learned);
	}

	private static final String PERSIST_GROUP = net.runelite.client.plugins.microbot.MicrobotConfig.configGroup;
	private static final String KEY_BLOCKED_DESTS = "leaguesBlockedDestinations";
	private static final String KEY_BLOCKED_DEST_REGIONS = "leaguesBlockedDestinationRegions";
	private static final String KEY_BLOCKED_DEST_METHODS = "leaguesBlockedDestinationMethods";
	private static final String KEY_REGION_LANDINGS = "leaguesAreaTeleportLandings";
	private static final String KEY_CALIBRATION_CONSENT = "leaguesCalibrationConsent";
	private static final String KEY_PROFILE_PURGE_MARKER = "leaguesProfilePersistencePurged";

	/**
	 * Per-league (major) catalog version mapping.
	 * Major is {@link VarbitID#LEAGUE_TYPE}. Default = {@code major.0.0} when not explicitly curated.
	 *
	 * <p>Minor/patch bumps are manual (edit this map) but the major automatically tracks the current League type.
	 * Old version directories/files are intentionally left on disk (never deleted); they are simply not used.
	 */
	private static final Map<Integer, String> CATALOG_VERSION_BY_MAJOR = new HashMap<>();
	static
	{
		CATALOG_VERSION_BY_MAJOR.put(6, "6.0.0");
	}

	private static int leaguesMajor()
	{
		int v = Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE);
		return v > 0 ? v : 0;
	}

	private static String leaguesCatalogVersion()
	{
		int major = leaguesMajor();
		String curated = CATALOG_VERSION_BY_MAJOR.get(major);
		return curated != null ? curated : major + ".0.0";
	}

	private static Path leaguesVersionDir()
	{
		return Path.of(
				System.getProperty("user.home"),
				".runelite",
				"microbot",
				"leagues-transport",
				"v" + leaguesCatalogVersion());
	}

	// Shareable persistence file (human mergeable) — versioned by League type (major) + manual minor/patch.
	// NOTE: file-only persistence by design (no profile ConfigManager writes/reads).
	private static Path shareFile()
	{
		return leaguesVersionDir().resolve("leagues-transport-cache.properties");
	}

	// Shareable logs for crowd-sourcing. JSONL = append-only + easy to merge. Versioned by catalog version.
	private static Path observationsFile()
	{
		return leaguesVersionDir().resolve("leagues-transport-observations.jsonl");
	}

	// Catalog schema version. Bump to invalidate old crowd-sourced files.
	private static final int CATALOG_SCHEMA_VERSION = 2;
	private static Path catalogFile()
	{
		return leaguesVersionDir().resolve("leagues-transport-catalog.jsonl");
	}

	private static Path catalogDir()
	{
		return leaguesVersionDir().resolve("leagues-transport-catalog.d");
	}

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	private static final Set<Integer> PERSIST_BLOCKED_DESTS = ConcurrentHashMap.newKeySet();
	private static final Map<Integer, LeaguesRegion> PERSIST_BLOCKED_DEST_REGIONS = new ConcurrentHashMap<>();
	private static final Map<Integer, String> PERSIST_BLOCKED_DEST_METHODS = new ConcurrentHashMap<>();
	private static volatile boolean persistLoaded = false;
	private static final Map<LeaguesRegion, WorldPoint> PERSIST_REGION_LANDINGS = new ConcurrentHashMap<>();
	private static final AtomicBoolean CALIBRATION_RUNNING = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_CANCEL_REQUESTED = new AtomicBoolean(false);
	private static final AtomicLong CALIBRATION_PROBE_MS = new AtomicLong(0L);
	private static final long CALIBRATION_PROBE_MIN_INTERVAL_MS = 5000L;
	private static final AtomicBoolean CALIBRATION_CONSENT_ALLOWED = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_CONSENT_DENIED = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_CONSENT_PROMPT_QUEUED = new AtomicBoolean(false);
	private static final AtomicLong CALIBRATION_CONSENT_RETRY_AFTER_MS = new AtomicLong(0L);
	private static final AtomicBoolean CALIBRATION_COMPLETE_PROMPT_QUEUED = new AtomicBoolean(false);
	private static final AtomicBoolean CALIBRATION_COMPLETE_PROMPT_SHOWN = new AtomicBoolean(false);
	private static final AtomicLong CALIBRATION_COMPLETE_RETRY_AFTER_MS = new AtomicLong(0L);
	private static final AtomicBoolean TELEPORT_IN_PROGRESS = new AtomicBoolean(false);
	private static final AtomicBoolean PROFILE_KEYS_PURGED = new AtomicBoolean(false);
	private static final AtomicLong WIDGET_VISIBILITY_CAP_HIT_LOG_MS = new AtomicLong(0L);
	private static final AtomicLong WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS = new AtomicLong(0L);
	private static final AtomicLong CALIBRATION_COMPLETE_DIALOG_FAIL_LOG_MS = new AtomicLong(0L);
	/** Per JSONL path: last mtime + parsed rows (unlock filter applied later in {@link #loadCatalogTransports}). */
	private static final Map<String, CatalogFileSnapshot> CATALOG_FILE_PARSE_CACHE = new ConcurrentHashMap<>();
	private static final AtomicBoolean LOGGED_OLD_CATALOG_SCHEMA = new AtomicBoolean(false);

	private static final class CatalogFileSnapshot
	{
		private final long mtimeMs;
		private final java.util.List<CatalogParsedRow> rows;

		private CatalogFileSnapshot(long mtimeMs, java.util.List<CatalogParsedRow> rows)
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

	public static boolean isTeleportInProgress()
	{
		return TELEPORT_IN_PROGRESS.get();
	}

	/**
	 * Called on each game tick once wiring is ready and the player is actually in-game, to start Leagues landing
	 * calibration ASAP.
	 * Throttled + non-blocking; actual teleports run on worker thread after user consents.
	 */
	public static void tickLeaguesCalibration()
	{
		// Called from MicrobotPlugin#onGameTick (client thread). Gate on real in-game state:
		// do not prompt while Welcome Screen "Play now" is still showing.
		Client c = Microbot.getClient();
		if (!isClientReadyForCalibration(c))
		{
			return;
		}

		if (!isLeaguesActive())
		{
			return;
		}
		long now = System.currentTimeMillis();
		long prev = CALIBRATION_PROBE_MS.get();
		if (prev != 0L && (now - prev) < CALIBRATION_PROBE_MIN_INTERVAL_MS)
		{
			return;
		}
		if (!CALIBRATION_PROBE_MS.compareAndSet(prev, now))
		{
			return;
		}

		EnumSet<LeaguesRegion> unlocked = unlockedRegions();
		if (unlocked.isEmpty())
		{
			return;
		}
		calibrateMissingLandingsAsync(unlocked);
	}

	private static boolean isClientReadyForCalibration(Client client)
	{
		if (!Microbot.isLoggedIn())
		{
			return false;
		}
		if (client == null)
		{
			return false;
		}
		// Gate on real in-game state: do not prompt while Welcome Screen "Play now" is still showing.
		boolean welcomeVisible;
		if (client.isClientThread())
		{
			Widget w = client.getWidget(InterfaceID.WelcomeScreen.PLAY);
			welcomeVisible = isWidgetEffectivelyVisible(w);
		}
		else
		{
			net.runelite.client.callback.ClientThread clientThread = Microbot.getClientThread();
			if (clientThread == null)
			{
				return false;
			}
			// `runOnClientThreadOptional` returns Optional.empty on timeout/interrupt; treat as not-ready.
			java.util.Optional<Boolean> vis = clientThread.runOnClientThreadOptional(() ->
			{
				Widget w = client.getWidget(InterfaceID.WelcomeScreen.PLAY);
				return isWidgetEffectivelyVisible(w);
			});
			if (vis.isEmpty())
			{
				// Debug once/hour: helps diagnose "no prompts" when client thread is stalled in tests/startup.
				long now = System.currentTimeMillis();
				long prev = WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS.get();
				if (prev == 0L || (now - prev) >= 3_600_000L)
				{
					if (WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS.compareAndSet(prev, now))
					{
						log.debug("[Leagues] widget visibility check timed out/empty; gating calibration as not-ready");
					}
				}
				return false;
			}
			welcomeVisible = vis.get();
		}
		if (welcomeVisible)
		{
			return false;
		}
		return client.getGameState() == net.runelite.api.GameState.LOGGED_IN && client.getLocalPlayer() != null;
	}

	private static boolean isWidgetEffectivelyVisible(Widget w)
	{
		if (w == null)
		{
			return false;
		}
		// Walk parent chain with fixed cap; detect cycles without allocating.
		Widget slow = w;
		Widget fast = w;
		final int cap = 20;
		for (int i = 0; i < cap && slow != null; i++)
		{
			Widget cur = slow;
			if (cur.isHidden())
			{
				return false;
			}
			Widget parent = cur.getParent();
			if (parent == cur)
			{
				return false;
			}

			// Floyd cycle detection on parent pointers (same chain as traversal).
			slow = parent;
			fast = fast != null ? fast.getParent() : null;
			fast = fast != null ? fast.getParent() : null;
			if (slow != null && slow == fast)
			{
				return false;
			}
		}

		// If we reached root, treat as visible.
		if (slow == null)
		{
			return true;
		}

		// Cap hit: ancestry unexpectedly deep; log once for diagnostics, but do not block calibration forever.
		if (log.isDebugEnabled())
		{
			long now = System.currentTimeMillis();
			long prev = WIDGET_VISIBILITY_CAP_HIT_LOG_MS.get();
			if (prev == 0L || (now - prev) >= 3_600_000L)
			{
				if (WIDGET_VISIBILITY_CAP_HIT_LOG_MS.compareAndSet(prev, now))
				{
					log.debug("[Leagues] widget visibility parent chain exceeded cap={}", cap);
				}
			}
		}
		// Fail-open: if widget ancestry is unexpectedly deep, treat as not-visible so calibration is not blocked forever.
		return false;
	}

	/**
	 * Called on logout/login-screen transitions to clear ephemeral UI prompt state.
	 * Persisted consent remains unchanged.
	 */
	public static void onLogout()
	{
		CALIBRATION_CANCEL_REQUESTED.set(true);
		CALIBRATION_CONSENT_PROMPT_QUEUED.set(false);
		CALIBRATION_CONSENT_RETRY_AFTER_MS.set(0L);
		CALIBRATION_COMPLETE_PROMPT_QUEUED.set(false);
		CALIBRATION_COMPLETE_PROMPT_SHOWN.set(false);
		CALIBRATION_COMPLETE_RETRY_AFTER_MS.set(0L);
		CALIBRATION_PROBE_MS.set(0L);
		TELEPORT_IN_PROGRESS.set(false);
		WIDGET_VISIBILITY_CAP_HIT_LOG_MS.set(0L);
		WIDGET_VISIBILITY_CHECK_TIMEOUT_LOG_MS.set(0L);
	}

	private static void promptCalibrationConsentIfNeeded()
	{
		ensurePersistLoaded();
		if (CALIBRATION_CONSENT_ALLOWED.get() || CALIBRATION_CONSENT_DENIED.get())
		{
			return;
		}
		long now = System.currentTimeMillis();
		long retryAfter = CALIBRATION_CONSENT_RETRY_AFTER_MS.get();
		if (retryAfter != 0L && now < retryAfter)
		{
			return;
		}
		// Prevent multiple queued runnables (fast ticks).
		if (!CALIBRATION_CONSENT_PROMPT_QUEUED.compareAndSet(false, true))
		{
			return;
		}

		SwingUtilities.invokeLater(() ->
		{
			try
			{
				// Avoid duplicate dialogs if state changed while queued.
				if (CALIBRATION_CONSENT_ALLOWED.get() || CALIBRATION_CONSENT_DENIED.get())
				{
					return;
				}
				// Dialog may have been queued before the game is actually ready (e.g. welcome screen).
				// Re-check here, and if not ready, allow a future retry.
				Client client = Microbot.getClient();
				if (!isClientReadyForCalibration(client))
				{
					CALIBRATION_CONSENT_RETRY_AFTER_MS.set(System.currentTimeMillis() + 5000L);
					return;
				}

				int res = JOptionPane.showConfirmDialog(
						null,
						"Calibrating Leagues teleports required.\n"
								+ "This will teleport your character briefly to learn landing tiles.\n\n"
								+ "Start calibration now?",
						"Microbot: Calibrate Leagues teleports",
						JOptionPane.YES_NO_OPTION,
						JOptionPane.INFORMATION_MESSAGE);

				if (res == JOptionPane.YES_OPTION)
				{
					CALIBRATION_CONSENT_ALLOWED.set(true);
					flushPersist();
				}
				else
				{
					CALIBRATION_CONSENT_DENIED.set(true);
					flushPersist();
				}
			}
			finally
			{
				CALIBRATION_CONSENT_PROMPT_QUEUED.set(false);
			}
		});
	}

	private static void promptCalibrationComplete(EnumSet<LeaguesRegion> unlockedRegions)
	{
		if (unlockedRegions == null || unlockedRegions.isEmpty())
		{
			return;
		}
		long now = System.currentTimeMillis();
		long retryAfter = CALIBRATION_COMPLETE_RETRY_AFTER_MS.get();
		if (retryAfter != 0L && now < retryAfter)
		{
			return;
		}
		if (CALIBRATION_COMPLETE_PROMPT_SHOWN.get())
		{
			return;
		}
		if (!CALIBRATION_COMPLETE_PROMPT_QUEUED.compareAndSet(false, true))
		{
			return;
		}

		ensurePersistLoaded();
		// Snapshot learned landings for display (avoid concurrent mutation while formatting).
		Map<LeaguesRegion, WorldPoint> landings = new HashMap<>(PERSIST_REGION_LANDINGS);

		int known = 0;
		int missing = 0;
		StringBuilder sb = new StringBuilder(1024);
		sb.append("Leagues teleport calibration complete.\n\n");
		sb.append("Unlocked regions: ").append(unlockedRegions.size()).append('\n');

		for (LeaguesRegion r : unlockedRegions)
		{
			WorldPoint wp = landings.get(r);
			if (wp != null)
			{
				known++;
			}
			else
			{
				missing++;
			}
		}
		sb.append("Learned landings: ").append(known).append('\n');
		sb.append("Missing landings: ").append(missing).append("\n\n");
		sb.append("Unlocked Leagues Area teleports:\n");

		// Fixed upper bound: loop only over unlocked regions.
		for (LeaguesRegion r : unlockedRegions)
		{
			WorldPoint wp = landings.get(r);
			sb.append("- ").append(r.getDisplayName());
			if (wp != null)
			{
				sb.append(" -> ").append(wp.toString());
			}
			else
			{
				sb.append(" -> (not learned)");
			}
			sb.append('\n');
		}

		// Avoid absurdly large dialogs if something goes wrong.
		final int maxChars = 8000;
		final String msg = sb.length() > maxChars ? sb.substring(0, maxChars) + "\n…(truncated)" : sb.toString();

		SwingUtilities.invokeLater(() ->
		{
			try
			{
				Client client = Microbot.getClient();
				// Re-check: state can flip between queue and display.
				if (!isClientReadyForCalibration(client))
				{
					// Backoff retries so flapping state doesn't spam re-queues.
					CALIBRATION_COMPLETE_RETRY_AFTER_MS.set(System.currentTimeMillis() + 5000L);
					return;
				}
				if (!CALIBRATION_COMPLETE_PROMPT_SHOWN.compareAndSet(false, true))
				{
					return;
				}
				try
				{
					JOptionPane.showMessageDialog(
							null,
							msg,
							"Microbot: Leagues calibration complete",
							JOptionPane.INFORMATION_MESSAGE);
				}
				catch (Exception e)
				{
					// If dialog fails, allow a future retry.
					CALIBRATION_COMPLETE_RETRY_AFTER_MS.set(System.currentTimeMillis() + 5000L);
					CALIBRATION_COMPLETE_PROMPT_SHOWN.set(false);
					// Debug once/hour: helps diagnose headless/EDT issues in tests or broken Swing envs.
					if (log.isDebugEnabled())
					{
						long nowMs = System.currentTimeMillis();
						long prev = CALIBRATION_COMPLETE_DIALOG_FAIL_LOG_MS.get();
						if (prev == 0L || (nowMs - prev) >= 3_600_000L)
						{
							if (CALIBRATION_COMPLETE_DIALOG_FAIL_LOG_MS.compareAndSet(prev, nowMs))
							{
								log.debug("[Leagues] completion dialog failed", e);
							}
						}
					}
				}
			}
			finally
			{
				CALIBRATION_COMPLETE_PROMPT_QUEUED.set(false);
			}
		});
	}

	private static final int[] LEAGUE_AREA_SELECTION_VARBITS = {
			VarbitID.LEAGUE_AREA_SELECTION_0,
			VarbitID.LEAGUE_AREA_SELECTION_1,
			VarbitID.LEAGUE_AREA_SELECTION_2,
			VarbitID.LEAGUE_AREA_SELECTION_3,
			VarbitID.LEAGUE_AREA_SELECTION_4,
			VarbitID.LEAGUE_AREA_SELECTION_5,
	};

	private static final int LEAGUE_TRANSPORT_CC_OP_IDENTIFIER = 1;
	private static final int LEAGUE_TRANSPORT_CC_OP_PARAM0 = -1;

	// Leagues area teleports can take ~20s from click to landing. Default 15s
	// causes premature timeouts and walker replans mid-teleport.
	private static final int DEFAULT_TIMEOUT_MS = 60_000;
	private static final int POLL_MS = 100;

	private Rs2LeaguesTransport()
	{
	}

	public static boolean isLeaguesContext()
	{
		return verifyLeaguesContextOrNull() == null;
	}

	/**
	 * Fast Leagues check safe off client thread. Uses varbit cache (no widget/UI).
	 */
	public static boolean isLeaguesActive()
	{
		return Microbot.getVarbitValue(VarbitID.LEAGUE_TYPE) > 0;
	}

	public static boolean isDestinationBlacklisted(int packedWorldPoint)
	{
		ensurePersistLoaded();
		return PERSIST_BLOCKED_DESTS.contains(packedWorldPoint);
	}

	/**
	 * Drops persisted blacklist rows tagged with {@code region} (player unlocked that Leagues area).
	 * Called automatically when {@link #injectLeaguesTransports} sees new unlocks; scripts may call after manual unlock.
	 * <p>Does not remove dest-only rows ({@code PERSIST_BLOCKED_DEST_REGIONS} has no entry for that packed tile); those stay until
	 * manually cleared or replaced.
	 */
	public static void invalidateBlacklistFor(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		ensurePersistLoaded();
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
		flushPersist();
	}

	public static Map<Integer, LeaguesRegion> getBlacklistedDestinationRegionsSnapshot()
	{
		ensurePersistLoaded();
		return Collections.unmodifiableMap(new HashMap<>(PERSIST_BLOCKED_DEST_REGIONS));
	}

	public static void persistBlacklistDestination(int packedWorldPoint, LeaguesRegion region, String method)
	{
		if (packedWorldPoint == 0)
		{
			return;
		}
		ensurePersistLoaded();
		PERSIST_BLOCKED_DESTS.add(packedWorldPoint);
		if (region != null)
		{
			PERSIST_BLOCKED_DEST_REGIONS.put(packedWorldPoint, region);
		}
		if (method != null && !method.isEmpty())
		{
			PERSIST_BLOCKED_DEST_METHODS.put(packedWorldPoint, method);
		}
		flushPersist();
	}

	/**
	 * Append one JSON line for an in-flight transport attempt (no {@code success} field).
	 * Only {@link TransportType#SEASONAL_TRANSPORT} rows are logged (Leagues chat / audit scope).
	 */
	public static void appendTransportAttemptObservation(Transport transport, String detail)
	{
		appendTransportObservationInternal("attempt", transport, null, detail);
	}

	/**
	 * Append one JSON line with explicit {@code success} outcome.
	 * Only {@link TransportType#SEASONAL_TRANSPORT} rows are logged (Leagues chat / audit scope).
	 *
	 * @param phase must be {@code "result"} — other phases use {@link #appendTransportAttemptObservation}.
	 */
	public static void appendTransportObservation(String phase, Transport transport, boolean success, String detail)
	{
		if (!"result".equals(phase))
		{
			throw new IllegalArgumentException("appendTransportObservation(boolean): phase must be \"result\"");
		}
		appendTransportObservationInternal(phase, transport, Boolean.valueOf(success), detail);
	}

	private static void appendTransportObservationInternal(String phase, Transport transport, Boolean success, String detail)
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

		final Map<Integer, LeaguesRegion> blockedDestRegionsSnapshot = getBlacklistedDestinationRegionsSnapshot();

		try
		{
			Path file = observationsFile();
			Files.createDirectories(file.getParent());

			JsonObject obj = new JsonObject();
			obj.addProperty("kind", "transport-observation");
			obj.addProperty("phase", phase);
			obj.addProperty("tsMs", System.currentTimeMillis());
			obj.addProperty("catalogVersion", leaguesCatalogVersion());
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

	/**
	 * Append a region-gated transport entry to the shareable catalog.
	 * Catalog entries are only used when {@code requiredRegion} is unlocked.
	 */
	public static void appendCatalogTransport(LeaguesRegion requiredRegion, Transport transport, String note)
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
			obj.addProperty("catalogVersion", leaguesCatalogVersion());
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

	/**
	 * Load region-gated transports from disk. Only returns entries whose required region is unlocked.
	 */
	public static java.util.List<Transport> loadCatalogTransports(EnumSet<LeaguesRegion> unlockedRegions)
	{
		if (unlockedRegions == null || unlockedRegions.isEmpty())
		{
			return Collections.emptyList();
		}

		java.util.List<Transport> out = new ArrayList<>();
		Set<String> seenKeys = new HashSet<>();

		java.util.List<Path> sources = new ArrayList<>();
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

	private static java.util.List<CatalogParsedRow> loadCatalogFileRowsCached(Path source)
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
			java.util.List<CatalogParsedRow> rows = parseCatalogFileRows(source);
			CATALOG_FILE_PARSE_CACHE.put(cacheKey, new CatalogFileSnapshot(mtime, rows));
			return rows;
		}
		catch (IOException e)
		{
			return Collections.emptyList();
		}
	}

	private static java.util.List<CatalogParsedRow> parseCatalogFileRows(Path source)
	{
		java.util.List<CatalogParsedRow> out = new ArrayList<>();
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

				WorldPoint dest = parsePoint(obj.has("destination") ? obj.getAsJsonObject("destination") : null);
				if (dest == null)
				{
					continue;
				}
				WorldPoint origin = parsePoint(obj.has("origin") ? obj.getAsJsonObject("origin") : null);

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

	private static WorldPoint parsePoint(JsonObject obj)
	{
		if (obj == null)
		{
			return null;
		}
		try
		{
			return new WorldPoint(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("p").getAsInt());
		}
		catch (Exception e)
		{
			return null;
		}
	}

	/**
	 * Single entry point for all Leagues transport injection into the pathfinder.
	 * Keeps Leagues-specific behavior out of core webwalker config.
	 * <p>
	 * No-op when {@code ctx.unlockedRegions} is empty (e.g. varbits not yet populated this tick). Next
	 * {@code refreshTransports} picks up new unlocks — intentional trade-off vs injecting partial catalog.
	 *
	 * @implNote Unit tests cover parsers/helpers; full injection path is exercised manually in Leagues — update if an automated integration suite is added.
	 */
	public static void injectLeaguesTransports(
			PathfinderConfig pathfinderConfig,
			LeaguesContext ctx,
			Set<Transport> usableTeleports,
			Map<WorldPoint, Set<Transport>> transports,
			PrimitiveIntHashMap<Set<Transport>> transportsPacked,
			Map<TransportType, int[]> typeStats)
	{
		if (pathfinderConfig == null || ctx == null || !ctx.active || ctx.unlockedRegions.isEmpty()
				|| usableTeleports == null || transports == null || transportsPacked == null || typeStats == null)
		{
			return;
		}

		EnumSet<LeaguesRegion> unlockedNow = EnumSet.copyOf(ctx.unlockedRegions);
		EnumSet<LeaguesRegion> prevUnlocked = lastInjectedUnlockedForBlacklistPrune;
		if (prevUnlocked != null)
		{
			for (LeaguesRegion r : unlockedNow)
			{
				if (!prevUnlocked.contains(r))
				{
					invalidateBlacklistFor(r);
				}
			}
		}
		lastInjectedUnlockedForBlacklistPrune = unlockedNow;

		// Calibration is kicked only from {@link #tickLeaguesCalibration} — avoid per-refresh probe + varbit work here (L0.4).
		injectLeaguesAreaTeleports(pathfinderConfig, ctx, ctx.unlockedRegions, usableTeleports, typeStats);
		injectLeaguesCatalogTransports(pathfinderConfig, ctx, ctx.unlockedRegions, usableTeleports, transports, transportsPacked, typeStats);
	}

	/**
	 * Keep one originless teleport per packed destination: lowest {@link Transport#getDuration} wins.
	 */
	private static boolean mergeOriginlessTeleportByBestDuration(Set<Transport> usableTeleports, Transport candidate)
	{
		if (candidate == null || candidate.getOrigin() != null || candidate.getDestination() == null)
		{
			return false;
		}
		int p = WorldPointUtil.packWorldPoint(candidate.getDestination());
		int minDur = candidate.getDuration();
		for (Transport o : usableTeleports)
		{
			if (o == null || o.getOrigin() != null || o.getDestination() == null)
			{
				continue;
			}
			if (WorldPointUtil.packWorldPoint(o.getDestination()) == p)
			{
				minDur = Math.min(minDur, o.getDuration());
			}
		}
		if (candidate.getDuration() > minDur)
		{
			return false;
		}
		usableTeleports.removeIf(o -> o != null && o.getOrigin() == null && o.getDestination() != null
				&& WorldPointUtil.packWorldPoint(o.getDestination()) == p);
		return usableTeleports.add(candidate);
	}

	private static void injectLeaguesAreaTeleports(
			PathfinderConfig pathfinderConfig,
			LeaguesContext ctx,
			EnumSet<LeaguesRegion> unlockedLeaguesRegions,
			Set<Transport> usableTeleports,
			Map<TransportType, int[]> typeStats)
	{
		int before = usableTeleports.size();
		int added = 0;

		for (LeaguesRegion region : unlockedLeaguesRegions)
		{
			Optional<WorldPoint> landingOpt = getCachedRegionLanding(region);
			if (!landingOpt.isPresent())
			{
				continue;
			}

			WorldPoint landing = landingOpt.get();
			// Originless row: origin null, destination = cached landing (see Transport(WorldPoint destination, ...)).
			Transport t = new Transport(
					landing,
					"Leagues Area: " + region.getDisplayName(),
					TransportType.SEASONAL_TRANSPORT,
					true,
					31,
					java.util.Collections.emptySet());
			if (!pathfinderConfig.isTransportUsableWithLeaguesContext(t, ctx))
			{
				continue;
			}
			if (mergeOriginlessTeleportByBestDuration(usableTeleports, t))
			{
				added++;
			}
		}

		if (added > 0)
		{
			int[] stats = typeStats.computeIfAbsent(TransportType.SEASONAL_TRANSPORT, k -> new int[]{0, 0, 0});
			stats[0] += added;
			stats[1] += added;
			log.info("[Leagues] injected {} Leagues Area teleports (originless {} -> {})",
					added, before, usableTeleports.size());
		}
	}

	private static void injectLeaguesCatalogTransports(
			PathfinderConfig pathfinderConfig,
			LeaguesContext ctx,
			EnumSet<LeaguesRegion> unlockedLeaguesRegions,
			Set<Transport> usableTeleports,
			Map<WorldPoint, Set<Transport>> transports,
			PrimitiveIntHashMap<Set<Transport>> transportsPacked,
			Map<TransportType, int[]> typeStats)
	{
		int beforeOriginless = usableTeleports.size();
		int addedOriginless = 0;
		int addedOriginBased = 0;

		java.util.List<Transport> catalog = loadCatalogTransports(unlockedLeaguesRegions);
		for (Transport t : catalog)
		{
			if (t == null || t.getDestination() == null)
			{
				continue;
			}

			if (!pathfinderConfig.isTransportUsableWithLeaguesContext(t, ctx))
			{
				continue;
			}

			TransportType tt = t.getType();
			if (t.getOrigin() == null)
			{
				if (mergeOriginlessTeleportByBestDuration(usableTeleports, t))
				{
					addedOriginless++;
					if (tt != null)
					{
						int[] stats = typeStats.computeIfAbsent(tt, k -> new int[]{0, 0, 0});
						stats[0] += 1;
						stats[1] += 1;
					}
				}
			}
			else
			{
				transports.computeIfAbsent(t.getOrigin(), k -> new HashSet<>()).add(t);

				int packedOrigin = WorldPointUtil.packWorldPoint(t.getOrigin());
				Set<Transport> packedSet = transportsPacked.get(packedOrigin);
				if (packedSet == null)
				{
					packedSet = new HashSet<>();
					transportsPacked.put(packedOrigin, packedSet);
				}
				packedSet.add(t);
				addedOriginBased++;
				if (tt != null)
				{
					int[] stats = typeStats.computeIfAbsent(tt, k -> new int[]{0, 0, 0});
					stats[0] += 1;
					stats[1] += 1;
				}
			}
		}

		if (addedOriginless + addedOriginBased > 0)
		{
			log.info("[Leagues] injected {} catalog transports (originlessAdded={} originBasedAdded={} originless {} -> {})",
					addedOriginless + addedOriginBased,
					addedOriginless,
					addedOriginBased,
					beforeOriginless,
					usableTeleports.size());
		}
	}

	/**
	 * Parse a region name from game messages / UI labels into a {@link LeaguesRegion}.
	 * Handles partial matches like "Fremennik Province" or "Kharidian Desert".
	 * Order matters: more-specific substrings (e.g. {@code kharidian}) before broad {@code desert}.
	 * <p>For raw gamemessage text, apply the same entity/tag cleanup as
	 * {@link net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer#sanitizeLeaguesLockedRegionName(String)}
	 * (Leagues locked-region chat handler) before calling. {@link #normalizeRegionNameForLockedChat} only does apostrophe + case
	 * + trim and can diverge from full chat preprocessing.
	 */
	public static LeaguesRegion parseRegionName(String regionNameRaw)
	{
		String s = normalizeRegionNameForLockedChat(regionNameRaw);
		if (s.isEmpty())
		{
			return null;
		}
		return parseRegionNameNormalized(s);
	}

	/** @param s non-empty result of {@link #normalizeRegionNameForLockedChat(String)} */
	private static LeaguesRegion parseRegionNameNormalized(String s)
	{
		if (s.contains("misthalin")) return LeaguesRegion.MISTHALIN;
		if (s.contains("kourend") || s.contains("kebos")) return LeaguesRegion.KEBOS_AND_KOUREND;
		if (s.contains("varlamore")) return LeaguesRegion.VARLAMORE;
		if (s.contains("fremennik")) return LeaguesRegion.FREMENNIK;
		if (s.contains("tirannwn")) return LeaguesRegion.TIRANNWN;
		if (s.contains("morytania")) return LeaguesRegion.MORYTANIA;
		if (s.contains("wilderness")) return LeaguesRegion.WILDERNESS;
		if (s.contains("karamja")) return LeaguesRegion.KARAMJA;
		if (s.contains("kandarin")) return LeaguesRegion.KANDARIN;
		if (s.contains("asgarnia")) return LeaguesRegion.ASGARNIA;
		if (s.contains("kharidian")) return LeaguesRegion.DESERT;
		if (s.contains("desert")) return LeaguesRegion.DESERT;

		return null;
	}

	/**
	 * Same normalization as {@link #parseRegionName(String)} input; empty when {@code null} or blank after normalize.
	 * Chat-sourced strings should already be sanitized (NFKC, entities, tags) before reaching {@link #parseRegionName} —
	 * this step is only apostrophe + {@link Locale#ROOT} lower case + trim.
	 */
	private static String normalizeRegionNameForLockedChat(String regionNameRaw)
	{
		if (regionNameRaw == null)
		{
			return "";
		}
		return regionNameRaw.replace('’', '\'').trim().toLowerCase(Locale.ROOT);
	}

	/**
	 * Captures the region text from a Leagues locked-region gamemessage.
	 * Caller owns any message length cap before passing {@code rawForMatch}.
	 */
	public static Optional<String> captureLockedRegionFromChatRaw(String rawForMatch)
	{
		return captureLockedRegionFromSanitizedLower(Rs2TextSanitizer.sanitizeForParsing(rawForMatch));
	}

	/** Same as {@link #captureLockedRegionFromChatRaw(String)} but skips sanitation when caller already has sanitized lower. */
	public static Optional<String> captureLockedRegionFromSanitizedLower(String sanitizedLower)
	{
		return Rs2TextSanitizer.captureFirstGroup(LEAGUES_LOCKED_REGION_CHAT, sanitizedLower);
	}

	/**
	 * Persist a "locked area" failure from chat for the last attempted transport.
	 * <p>High-volume chat: no per-message DEBUG while {@link #LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES} has fewer than
	 * {@link #LEAGUES_PARSE_MISS_DISTINCT_LOG_CAP} distinct keys (set size, not key string length), nor on the blocked-dest success path — rate-limited {@code log.info} only.
	 * Once the distinct-sample set is full (and {@link LeaguesRegion} still null after {@link #parseRegionNameNormalized}),
	 * rate-limited {@code log.info} covers overflow drops and after-cap skip tallies; {@code log.debug} only accompanies the
	 * overflow-drop {@code log.info} when DEBUG is enabled — after-cap-only INFO lines have no paired DEBUG.
	 */
	public static boolean recordBlockedDestinationFromChat(String regionNameRaw, Integer packedDest, String method)
	{
		if (packedDest == null)
		{
			return false;
		}
		String norm = normalizeRegionNameForLockedChat(regionNameRaw);
		LeaguesRegion region = norm.isEmpty() ? null : parseRegionNameNormalized(norm);
		if (region == null)
		{
			if (norm.isEmpty())
			{
				return false;
			}
			// L0.9: Jagex copy can fail parseRegionName while chat still matches locked-region pattern — blacklist by
			// destination only so the walker stops retrying the same teleport.
			persistBlacklistDestination(packedDest, null, method);
			clearLastTransportAttempt();
			String missKey = norm.length() > 160
					? norm.substring(0, 160) + "|h" + Integer.toHexString(norm.hashCode())
					: norm;
			boolean emitSampleLog = false;
			boolean atCapSkip = false;

			synchronized (LEAGUES_PARSE_MISS_SAMPLES_LOCK)
			{
				// Two-phase dedupe:
				// 1) `LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES` up to distinct cap (size/add consistent under this lock)
				// 2) once at cap, `LEAGUES_PARSE_MISS_AT_CAP_SEEN` bounds memory + overflow logs (guarded by `LEAGUES_PARSE_MISS_AT_CAP_LOCK`)
				int distinct = LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES.size();
				if (distinct < LEAGUES_PARSE_MISS_DISTINCT_LOG_CAP)
				{
					// Strictly cap distinct samples; concurrent callers share lock so size/add is consistent.
					if (!LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES.add(missKey))
					{
						return true;
					}
					emitSampleLog = true;
				}
				else
				{
					// Already in main sample set → duplicate chat; never hits at-cap dedupe below.
					if (LEAGUES_LOCKED_REGION_PARSE_MISS_SAMPLES.contains(missKey))
					{
						return true;
					}
					atCapSkip = true;
				}
			}

			if (atCapSkip)
			{
				synchronized (LEAGUES_PARSE_MISS_AT_CAP_LOCK)
				{
					if (LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT.get() >= LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX
							&& !LEAGUES_PARSE_MISS_AT_CAP_SEEN.contains(missKey))
					{
						boolean emitOverflowSummary = Rs2LogRateLimit.everyN(
								LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG, LEAGUES_PARSE_MISS_AT_CAP_SEEN_FULL_LOG_INTERVAL);
						if (emitOverflowSummary)
						{
							log.info("[Leagues] locked-region parse-miss: at-cap dedupe set full (max={}); dropped novel keys — extend parseRegionName or raise cap",
									LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX);
						}
						if (log.isDebugEnabled() && emitOverflowSummary)
						{
							log.debug("[Leagues] locked-region parse-miss dropped (at-cap dedupe set full); missKey prefix='{}'",
									missKey.length() > 80 ? missKey.substring(0, 80) + "…" : missKey);
						}
						return true;
					}
					if (!LEAGUES_PARSE_MISS_AT_CAP_SEEN.add(missKey))
					{
						return true;
					}
					// Strictly cap growth: lock makes count consistent with set adds.
					int prev = LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT.get();
					if (prev < LEAGUES_PARSE_MISS_AT_CAP_SEEN_MAX)
					{
						LEAGUES_PARSE_MISS_AT_CAP_SEEN_COUNT.set(prev + 1);
					}
				}
				int n = LEAGUES_PARSE_MISS_AFTER_CAP_COUNT.incrementAndGet();
				if (n == 1 || n % LEAGUES_PARSE_MISS_AFTER_CAP_LOG_INTERVAL == 0)
				{
					log.info("[Leagues] locked-region parse-miss skipped={} (distinct-sample cap {}); extend parseRegionName",
							n, LEAGUES_PARSE_MISS_DISTINCT_LOG_CAP);
				}
				return true;
			}

			if (emitSampleLog && Rs2LogRateLimit.everyN(LEAGUES_PARSE_MISS_INFO, LEAGUES_PARSE_MISS_INFO_INTERVAL))
			{
				String sample = regionNameRaw == null ? ""
						: regionNameRaw.length() > 120 ? regionNameRaw.substring(0, 120) + "…" : regionNameRaw;
				log.info("[Leagues] locked-region chat did not map to LeaguesRegion; dest-only blacklist applied. sample='{}'", sample);
			}
			return true;
		}
		if (Rs2LogRateLimit.everyN(LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO, LEAGUES_BLOCKED_DEST_FROM_CHAT_INFO_INTERVAL))
		{
			log.info("[Leagues] blocked transport destPacked={} region='{}' method='{}'",
					packedDest, regionNameRaw, method != null ? method : "");
		}
		persistBlacklistDestination(packedDest, region, method);
		// Prevent later unrelated lock messages from reusing same attempt context.
		clearLastTransportAttempt();
		return true;
	}

	/**
	 * Dedupes rapid repeat reroutes for the same locked chat attribution (same {@code region}+{@code packedDest} within
	 * {@link #LEAGUES_REROUTE_DEDUPE_WINDOW_MS}). Production {@link net.runelite.api.events.ChatMessage} delivery is
	 * effectively single-threaded per subscriber; duplicate lines still arrive as separate events (distinct timestamps).
	 * Concurrent test harnesses are unusual — reroute dedupe still limits bursts if ordering overlaps.
	 *
	 * @implNote Production caller is currently {@link net.runelite.client.plugins.microbot.MicrobotPlugin#onChatMessage}; chat path passes
	 * non-null {@code packedDest} when {@link #recordBlockedDestinationFromChat} returned true. Either argument {@code null}
	 * returns {@code true} so path restart is not skipped for defensive/unknown callers and tests.
	 */
	public static boolean shouldRecalculatePathAfterLock(String region, Integer packedDest)
	{
		if (region == null || packedDest == null)
		{
			return true;
		}
		synchronized (LEAGUES_REROUTE_LOCK)
		{
			long now = System.currentTimeMillis();
			String prevRegion = lastLeaguesRerouteRegion;
			int prevPacked = lastLeaguesReroutePackedDest;
			long prevMs = lastLeaguesRerouteMs;
			if (packedDest == prevPacked && region.equals(prevRegion) && (now - prevMs) <= LEAGUES_REROUTE_DEDUPE_WINDOW_MS)
			{
				if (log.isDebugEnabled())
				{
					log.debug("[Leagues] reroute deduped: region='{}' destPacked={} ageMs={}", region, packedDest, (now - prevMs));
				}
				return false;
			}
			lastLeaguesRerouteRegion = region;
			lastLeaguesReroutePackedDest = packedDest;
			lastLeaguesRerouteMs = now;
			return true;
		}
	}

	public static Optional<WorldPoint> getCachedRegionLanding(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		ensurePersistLoaded();
		WorldPoint landing = PERSIST_REGION_LANDINGS.get(region);
		if (landing == null)
		{
			return Optional.empty();
		}

		return Optional.of(landing);
	}

	public static void persistRegionLanding(LeaguesRegion region, WorldPoint landing)
	{
		Objects.requireNonNull(region, "region");
		if (landing == null)
		{
			return;
		}
		ensurePersistLoaded();

		PERSIST_REGION_LANDINGS.put(region, landing);
		flushPersist();
	}

	/**
	 * Opportunistic calibration: if a region is unlocked but has no cached landing tile yet,
	 * attempt to teleport there on a worker thread and persist the landing.
	 *
	 * Called from webwalker/pathfinder refresh so new unlocks during session self-heal without
	 * touching walker logic. Spawns a daemon thread that calls {@link #leaguesTeleport} — do not call
	 * {@code leaguesTeleport} from the client thread (it blocks on client-thread UI work).
	 */
	public static void calibrateMissingLandingsAsync(EnumSet<LeaguesRegion> unlockedRegions)
	{
		if (unlockedRegions == null || unlockedRegions.isEmpty())
		{
			return;
		}
		if (!isLeaguesActive())
		{
			return;
		}
		ensurePersistLoaded();

		int missingCount = 0;
		for (LeaguesRegion r : unlockedRegions)
		{
			if (!PERSIST_REGION_LANDINGS.containsKey(r))
			{
				missingCount++;
			}
		}
		if (missingCount == 0)
		{
			// If user already opted-in, still show confirmation even when nothing to calibrate.
			if (CALIBRATION_CONSENT_ALLOWED.get())
			{
				// Backoff only when client isn't ready yet (avoid delaying when ready).
				Client client = Microbot.getClient();
				if (!isClientReadyForCalibration(client))
				{
					long prev = CALIBRATION_COMPLETE_RETRY_AFTER_MS.get();
					long now = System.currentTimeMillis();
					if (prev == 0L || now >= prev)
					{
						CALIBRATION_COMPLETE_RETRY_AFTER_MS.set(now + 5000L);
					}
				}
				promptCalibrationComplete(unlockedRegions);
			}
			return;
		}

		promptCalibrationConsentIfNeeded();
		if (!CALIBRATION_CONSENT_ALLOWED.get())
		{
			return;
		}

		if (!CALIBRATION_RUNNING.compareAndSet(false, true))
		{
			return;
		}

		// Allow new calibration after logout cancellation only when we will actually spawn a worker.
		CALIBRATION_CANCEL_REQUESTED.set(false);

		final EnumSet<LeaguesRegion> unlockedSnapshot = EnumSet.copyOf(unlockedRegions);
		Thread t = new Thread(() ->
		{
			try
			{
				int ok = 0;
				int fail = 0;
				int tried = 0;

				// Fixed upper bound: at most one pass over unlocked regions.
				for (LeaguesRegion target : unlockedSnapshot)
				{
					if (CALIBRATION_CANCEL_REQUESTED.get())
					{
						dismissOpenMenusAfterCalibrationCancel();
						break;
					}
					if (target == null)
					{
						continue;
					}
					if (PERSIST_REGION_LANDINGS.containsKey(target))
					{
						continue;
					}
					tried++;

					log.info("[Leagues] calibrate landing start: {}", target);
					if (CALIBRATION_CANCEL_REQUESTED.get())
					{
						dismissOpenMenusAfterCalibrationCancel();
						break;
					}
					final WorldPoint before = Rs2Player.getWorldLocation();
					LeaguesTeleportResult res = leaguesTeleport(target);
					if (!res.isSuccess())
					{
						fail++;
						log.info("[Leagues] calibrate landing failed: {} reason={} msg='{}'",
								target, res.getFailureReason(), res.getMessage());
						continue;
					}

					// Leagues teleports may not have a stable "animating" phase; key off location change.
					boolean moved = sleepUntilTrue(() ->
					{
						WorldPoint now = Rs2Player.getWorldLocation();
						return now != null && before != null && !now.equals(before);
					}, 100, 8000);
					if (!moved)
					{
						fail++;
						continue;
					}

					WorldPoint after = Rs2Player.getWorldLocation();
					if (after != null)
					{
						ok++;
						persistRegionLanding(target, after);
						log.info("[Leagues] calibrate landing ok: {} -> {}", target, after);
					}
					else
					{
						fail++;
					}
				}

				// Confirmation prompt by request: show unlocked Leagues Area teleports.
				if (tried > 0)
				{
					promptCalibrationComplete(unlockedSnapshot);
				}
			}
			catch (Exception e)
			{
				log.debug("[Leagues] calibrate landing thread exc: type={} msg={}",
						e.getClass().getName(), e.getMessage());
			}
			finally
			{
				CALIBRATION_RUNNING.set(false);
			}
		}, "microbot-leagues-landing-calibration");
		t.setDaemon(true);
		t.start();
	}

	private static void ensurePersistLoaded()
	{
		if (persistLoaded)
		{
			return;
		}
		synchronized (Rs2LeaguesTransport.class)
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

	/**
	 * Legacy cleanup: older builds persisted Leagues cache into the active profile via {@link ConfigManager}.
	 * Unsets those keys once {@link ConfigManager} exists, then writes marker via {@link #writeShareFile()}.
	 * Safe to call from {@link #ensurePersistLoaded()} and {@link #flushPersist()}: retries when {@code cm} was null at first load.
	 *
	 * @return {@code true} if this call already persisted the share file via purge; {@code false} if caller should persist
	 */
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

	private static void flushPersist()
	{
		if (!maybePurgeLegacyProfileKeys())
		{
			writeShareFile();
		}
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

			Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (Exception e)
		{
			log.debug("[Leagues] share file write failed path={} type={} msg={}",
					shareFile(), e.getClass().getName(), e.getMessage());
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
			if (!first) sb.append(',');
			first = false;
			sb.append(v);
		}
		return sb.toString();
	}

	// format: "packed=REGION;packed=REGION"
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
			if (e.isEmpty()) continue;
			int eq = e.indexOf('=');
			if (eq <= 0 || eq >= e.length() - 1) continue;
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
			if (e.getKey() == null || e.getValue() == null) continue;
			if (!first) sb.append(';');
			first = false;
			sb.append(e.getKey()).append('=').append(e.getValue().name());
		}
		return sb.toString();
	}

	// format: "packed=method;packed=method" (method must not contain ';' or '=')
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
			if (e.isEmpty()) continue;
			int eq = e.indexOf('=');
			if (eq <= 0 || eq >= e.length() - 1) continue;
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
			if (k == null || v == null || v.isEmpty()) continue;
			String safe = v.replace(";", " ").replace("=", " ").trim();
			if (safe.isEmpty()) continue;
			if (!first) sb.append(';');
			first = false;
			sb.append(k).append('=').append(safe);
		}
		return sb.toString();
	}

	// format: "REGION=x y p;REGION=x y p"
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
			if (e.isEmpty()) continue;
			int eq = e.indexOf('=');
			if (eq <= 0 || eq >= e.length() - 1) continue;
			try
			{
				LeaguesRegion r = LeaguesRegion.valueOf(e.substring(0, eq).trim());
				String[] parts = e.substring(eq + 1).trim().split("\\s+");
				if (parts.length != 3) continue;
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
			if (r == null || wp == null) continue;
			if (!first) sb.append(';');
			first = false;
			sb.append(r.name()).append('=')
					.append(wp.getX()).append(' ')
					.append(wp.getY()).append(' ')
					.append(wp.getPlane());
		}
		return sb.toString();
	}

	public static EnumSet<LeaguesRegion> unlockedRegions()
	{
		Optional<EnumSet<LeaguesRegion>> unlockedOpt = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			if (!leaguesContextRejectOrEmptySuccess().isEmpty())
			{
				return EnumSet.noneOf(LeaguesRegion.class);
			}
			return readUnlockedRegionsFromSelectionVarbits();
		});

		return unlockedOpt.orElse(EnumSet.noneOf(LeaguesRegion.class));
	}

	public static LeaguesTeleportResult leaguesTeleport(LeaguesRegion region)
	{
		return leaguesTeleport(region, DEFAULT_TIMEOUT_MS);
	}

	/**
	 * @apiNote Do not invoke from the RuneLite client thread: the implementation waits on client-thread UI
	 *          hops and can stall the client. Use from worker/script threads (see {@link #calibrateMissingLandingsAsync}).
	 */
	public static LeaguesTeleportResult leaguesTeleport(LeaguesRegion region, int timeoutMs)
	{
		Objects.requireNonNull(region, "region");
		if (timeoutMs <= 0)
		{
			throw new IllegalArgumentException("timeoutMs must be > 0");
		}

		Client client = Microbot.getClient();
		if (client == null)
		{
			String msg = "Client not available.";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.CLIENT_UNAVAILABLE,
					msg,
					region,
					null);
		}
		if (client.isClientThread())
		{
			String msg = "leaguesTeleport must not run on the RuneLite client thread.";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.INVOKED_ON_CLIENT_THREAD,
					msg,
					region,
					null);
		}

		final long startedAtMs = System.currentTimeMillis();
		final WorldPoint before = Rs2Player.getWorldLocation();

		Optional<TeleportGateSnapshot> gateOpt = evaluateTeleportGates(region);
		if (!gateOpt.isPresent())
		{
			String msg = "Leagues teleport gates: empty client-thread gate result ("
					+ LeaguesTeleportFailureReason.CLIENT_THREAD_UNAVAILABLE.name()
					+ "; not null Client / not wrong thread).";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.CLIENT_THREAD_UNAVAILABLE,
					msg,
					region,
					null);
		}

		TeleportGateSnapshot gate = gateOpt.get();
		if (gate.contextFailureReason != null)
		{
			Microbot.status = gate.contextFailureMessage;
			return LeaguesTeleportResult.failure(
					gate.contextFailureReason,
					gate.contextFailureMessage,
					region,
					null);
		}

		if (!gate.unlockedRegions.contains(region))
		{
			String msg = "Region not unlocked: " + region.getDisplayName() + ".";
			Microbot.status = msg;
			return LeaguesTeleportResult.failure(
					LeaguesTeleportFailureReason.REGION_LOCKED,
					msg,
					region,
					gate.unlockedRegions);
		}

		boolean marked = TELEPORT_IN_PROGRESS.compareAndSet(false, true);
		try
		{
			if (!performTeleportSequence(region, timeoutMs))
			{
				String msg = "Leagues transport: UI timeout.";
				Microbot.status = msg;
				return LeaguesTeleportResult.failure(
						LeaguesTeleportFailureReason.UI_TIMEOUT,
						msg,
						region,
						gate.unlockedRegions);
			}

			// Critical: do not report success until the teleport actually completes.
			// Otherwise the walker replans mid-teleport and repeatedly interrupts the action.
			int remaining = remainingMs(startedAtMs, timeoutMs);
			final boolean arrived;
			if (remaining <= 0)
			{
				arrived = false;
			}
			else
			{
				final int rem = remaining;
				final WorldPoint bef = before;
				final LeaguesRegion reg = region;
				boolean[] box = {false};
				Rs2Walker.runWithWalkerLockReleased(() -> box[0] = waitForTeleportArrival(reg, bef, rem));
				arrived = box[0];
			}
			if (!arrived)
			{
				String msg = "Leagues transport: teleport timeout.";
				Microbot.status = msg;
				return LeaguesTeleportResult.failure(
						LeaguesTeleportFailureReason.TELEPORT_TIMEOUT,
						msg,
						region,
						gate.unlockedRegions);
			}

			return LeaguesTeleportResult.ok(region, gate.unlockedRegions);
		}
		finally
		{
			if (marked)
			{
				TELEPORT_IN_PROGRESS.set(false);
			}
		}
	}

	private static boolean waitForTeleportArrival(LeaguesRegion region, WorldPoint before, int timeoutMs)
	{
		Objects.requireNonNull(region, "region");
		final long startedAtMs = System.currentTimeMillis();
		final int teleportDistanceThreshold = 20;

		// Heuristic: if we successfully clicked teleport and we start animating,
		// wait until the world location actually changes (real teleport, not a 1-tile step).
		//
		// This avoids the walker interrupting mid-teleport while still being robust to
		// landing-tile cache drift.
		final boolean animatingStarted = sleepUntilTrue(Rs2Player::isAnimating, POLL_MS, remainingMs(startedAtMs, timeoutMs));
		final long moveWaitStartedAtMs = System.currentTimeMillis();
		return sleepUntilTrue(() ->
		{
			WorldPoint now = Rs2Player.getWorldLocation();
			if (now == null || before == null)
			{
				return false;
			}
			if (now.equals(before))
			{
				return false;
			}

			// Require "teleport-like" movement: region change OR big distance jump.
			if (now.getRegionID() != before.getRegionID())
			{
				return true;
			}
			return now.distanceTo(before) > teleportDistanceThreshold;
		}, POLL_MS, remainingMs(moveWaitStartedAtMs, animatingStarted ? remainingMs(startedAtMs, timeoutMs) : remainingMs(startedAtMs, timeoutMs)));
	}

	private static boolean performTeleportSequence(LeaguesRegion region, int timeoutMs)
	{
		final long startedAtMs = System.currentTimeMillis();

		// UI already on correct teleport row — skip Activities → Leagues → View Areas chain.
		if (isFixedTeleportRowReady(region))
		{
			return invokeTeleportToRegion(region);
		}

		if (!Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD))
		{
			invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.ACTIVITIES_GROUP, LeagueTransportWidgets.ACTIVITIES_CHILD), "Leagues", "");
			if (!sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD), POLL_MS, remainingMs(startedAtMs, timeoutMs)))
			{
				return false;
			}
		}

		if (!Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD))
		{
			invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD), "Leagues", "");
			if (!sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD), POLL_MS, remainingMs(startedAtMs, timeoutMs)))
			{
				return false;
			}
		}

		// Opens areas panel if needed (clicks View Areas only when panel not visible).
		if (!ensureAreasMenuShowsTargetRow(region, startedAtMs, timeoutMs))
		{
			return false;
		}

		return invokeTeleportToRegion(region);
	}

	/**
	 * After "View Areas", wait for list root, optionally click the areas-menu shield tab for this region,
	 * then wait until the teleport row for {@link LeaguesRegion#getTeleportListRowDynamicIndex()} exists and is visible.
	 */
	private static boolean ensureAreasMenuShowsTargetRow(LeaguesRegion region, long startedAtMs, int timeoutMs)
	{
		Objects.requireNonNull(region, "region");

		// Areas panel may take a tick to open; retry the "View Areas" click while polling.
		int viewAreasPacked = LeagueTransportWidgets.pack(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD);
		if (!Global.sleepUntil(
				() -> Rs2Widget.isWidgetVisible(LeagueTransportWidgets.AREAS_PANEL_GROUP, LeagueTransportWidgets.AREAS_PANEL_CHILD),
				() -> invokeCcOp(viewAreasPacked, "View Areas", ""),
				remainingMs(startedAtMs, timeoutMs),
				POLL_MS))
		{
			return false;
		}

		// Shield tab must be clicked before the teleport rows container is populated/visible.
		LeaguesRegion.AreasMenuShield shield = region.getAreasMenuShield();
		if (shield.isActive())
		{
			invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
		}

		int listRootPacked = areasListRootPacked(region);
		if (!Global.sleepUntil(
				() -> Rs2Widget.isWidgetVisible(areasListRootGroup(region), areasListRootChild(region)),
				() -> {
					if (shield.isActive())
					{
						invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
					}
				},
				remainingMs(startedAtMs, timeoutMs),
				POLL_MS))
		{
			return false;
		}

		// Wait until fixed row template widget updates to this region (name + action becomes available).
		return Global.sleepUntil(
				() -> isFixedTeleportRowReady(region),
				() -> {
					if (shield.isActive())
					{
						invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
					}
				},
				remainingMs(startedAtMs, timeoutMs),
				POLL_MS);
	}

	private static boolean isFixedTeleportRowReady(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		int packed = LeagueTransportWidgets.pack(LeagueTransportWidgets.TELEPORT_ROW_GROUP, LeagueTransportWidgets.TELEPORT_ROW_CHILD);
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget row = Rs2Widget.getWidget(packed);
			if (row == null || row.isHidden())
			{
				return false;
			}
			String name = row.getName();
			if (name == null || !name.equals(region.toMenuTarget()))
			{
				return false;
			}
			// Empirical: some clients expose row Actions[] as "Teleport to" while menuAction option that works is "Teleport".
			// Name match is sufficient to proceed; click will still be validated by game client.
			return true;
		}).orElse(false);
	}

	private static boolean isTargetRowVisible(LeaguesRegion region, int listContainerPackedId)
	{
		Objects.requireNonNull(region, "region");
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget listContainer = Rs2Widget.getWidget(listContainerPackedId);
			if (listContainer == null || listContainer.isHidden())
			{
				return false;
			}
			Widget row = resolveTeleportRowWidget(listContainer, region);
			return row != null && !row.isHidden();
		}).orElse(false);
	}

	/**
	 * Rows reuse the same packed interface id; target region selects {@link LeaguesRegion#getTeleportListRowDynamicIndex()}
	 * under the list container’s child arrays.
	 */
	private static Widget resolveTeleportRowWidget(Widget listContainer, LeaguesRegion region)
	{
		Objects.requireNonNull(listContainer, "listContainer");
		Objects.requireNonNull(region, "region");
		int idx = region.getTeleportListRowDynamicIndex();
		if (idx < 0)
		{
			return null;
		}
		Widget[] dynamic = listContainer.getDynamicChildren();
		if (dynamic != null && idx < dynamic.length)
		{
			Widget w = dynamic[idx];
			if (w != null)
			{
				return w;
			}
		}
		Widget[] nested = listContainer.getNestedChildren();
		if (nested != null && idx < nested.length)
		{
			return nested[idx];
		}
		Widget[] children = listContainer.getChildren();
		if (children != null && idx < children.length)
		{
			return children[idx];
		}
		return null;
	}

	private static int areasListRootGroup(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		int g = region.getAreasListRootGroup();
		return g != 0 ? g : LeagueTransportWidgets.AREAS_LIST_CONTAINER_GROUP;
	}

	private static int areasListRootChild(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		return region.getAreasListRootGroup() != 0 ? region.getAreasListRootChild() : LeagueTransportWidgets.AREAS_LIST_CONTAINER_CHILD;
	}

	/** Packed component id for list root (row search + fallback {@link MenuAction} param). */
	private static int areasListRootPacked(LeaguesRegion region)
	{
		return LeagueTransportWidgets.pack(areasListRootGroup(region), areasListRootChild(region));
	}

	private static boolean invokeTeleportToRegion(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");

		if (region.getTeleportCcOpGroup() != 0)
		{
			invokeCcOp(
					LeagueTransportWidgets.pack(region.getTeleportCcOpGroup(), region.getTeleportCcOpChild()),
					region.getTeleportCcOpOption(),
					region.toMenuTarget());
			scheduleDebugCheckTeleportRowNameMatches(region);
			return true;
		}

		// Fixed row template widget id (updates its name/actions when shield tab changes).
		invokeCcOp(
				LeagueTransportWidgets.pack(LeagueTransportWidgets.TELEPORT_ROW_GROUP, LeagueTransportWidgets.TELEPORT_ROW_CHILD),
				region.getTeleportCcOpOption(),
				region.toMenuTarget());
		scheduleDebugCheckTeleportRowNameMatches(region);
		return true;
	}

	/** Next client tick: if teleport row name does not match {@link LeaguesRegion#toMenuTarget()}, log once (DEBUG). */
	private static void scheduleDebugCheckTeleportRowNameMatches(LeaguesRegion region)
	{
		if (region == null || !log.isDebugEnabled())
		{
			return;
		}
		Microbot.getClientThread().invokeLater(() ->
		{
			int packed = LeagueTransportWidgets.pack(LeagueTransportWidgets.TELEPORT_ROW_GROUP, LeagueTransportWidgets.TELEPORT_ROW_CHILD);
			Widget row = Rs2Widget.getWidget(packed);
			if (row == null || row.isHidden())
			{
				return;
			}
			String name = row.getName();
			String expect = region.toMenuTarget();
			if (name != null && expect != null && !name.equals(expect) && LOGGED_TELEPORT_ROW_NAME_MISMATCH.compareAndSet(false, true))
			{
				log.debug("[Leagues] TELEPORT_ROW name mismatch after click: region={} expected={} actual={}", region, expect, name);
			}
		});
	}

	/** Best-effort close of open menus after calibration cancel (logout) so in-flight UI does not confuse walker. */
	private static void dismissOpenMenusAfterCalibrationCancel()
	{
		var ct = Microbot.getClientThread();
		if (ct == null)
		{
			return;
		}
		ct.invokeLater(() -> Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE));
	}

	private static int remainingMs(long startedAtMs, int timeoutMs)
	{
		long elapsed = System.currentTimeMillis() - startedAtMs;
		long remaining = timeoutMs - elapsed;
		if (remaining <= 0)
		{
			return 1;
		}
		return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
	}

	/**
	 * Non-blocking driver for advanced callers. Call {@link #tick()} from script loop until inactive.
	 */
	public static final class LeaguesTeleportDriver
	{
		private final LeaguesRegion targetRegion;
		private boolean active = true;
		private int step;
		private boolean areasMenuShieldClicked;

		private LeaguesTeleportDriver(LeaguesRegion targetRegion)
		{
			this.targetRegion = targetRegion;
		}

		public boolean isActive()
		{
			return active;
		}

		public void stop()
		{
			active = false;
		}

		public void tick()
		{
			if (!active)
			{
				return;
			}
			if (Microbot.getClient() == null)
			{
				active = false;
				return;
			}

			if (!passVisibilityGate(step))
			{
				return;
			}

			switch (step)
			{
				case 0:
					if (Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD))
					{
						step = 1;
						return;
					}
					Microbot.status = "Leagues: open activities";
					invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.ACTIVITIES_GROUP, LeagueTransportWidgets.ACTIVITIES_CHILD), "Leagues", "");
					step = 1;
					return;
				case 1:
					if (Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD))
					{
						step = 2;
						return;
					}
					Microbot.status = "Leagues: open leagues";
					invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD), "Leagues", "");
					step = 2;
					return;
				case 2:
					if (Rs2Widget.isWidgetVisible(LeagueTransportWidgets.AREAS_PANEL_GROUP, LeagueTransportWidgets.AREAS_PANEL_CHILD))
					{
						step = 3;
						return;
					}
					Microbot.status = "Leagues: open areas";
					invokeCcOp(LeagueTransportWidgets.pack(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD), "View Areas", "");
					step = 3;
					return;
				case 3:
				{
					if (isFixedTeleportRowReady(targetRegion))
					{
						Microbot.status = "Leagues: teleport " + targetRegion.getDisplayName();
						if (!invokeTeleportToRegion(targetRegion))
						{
							log.warn("LeaguesTeleportDriver: teleport invoke failed for {}", targetRegion);
						}
						areasMenuShieldClicked = false;
						active = false;
						step = 0;
						return;
					}
					LeaguesRegion.AreasMenuShield shield = targetRegion.getAreasMenuShield();
					int listRootPacked = areasListRootPacked(targetRegion);
					if (shield.isActive() && !areasMenuShieldClicked)
					{
						Microbot.status = "Leagues: areas menu shield";
						invokeCcOp(LeagueTransportWidgets.pack(shield.getGroup(), shield.getChild()), shield.getCcOpOption(), shield.getCcOpTarget());
						areasMenuShieldClicked = true;
						return;
					}
					if (!isTargetRowVisible(targetRegion, listRootPacked))
					{
						return;
					}
					Microbot.status = "Leagues: teleport " + targetRegion.getDisplayName();
					if (!invokeTeleportToRegion(targetRegion))
					{
						log.warn("LeaguesTeleportDriver: teleport invoke failed for {}", targetRegion);
					}
					areasMenuShieldClicked = false;
					active = false;
					step = 0;
					return;
				}
				default:
					log.warn("Rs2LeaguesTransport unexpected step {}", step);
					active = false;
			}
		}

		private boolean passVisibilityGate(int step)
		{
			switch (step)
			{
				case 0:
					return true;
				case 1:
					return Rs2Widget.isWidgetVisible(LeagueTransportWidgets.LEAGUES_GROUP, LeagueTransportWidgets.LEAGUES_CHILD);
				case 2:
					return Rs2Widget.isWidgetVisible(LeagueTransportWidgets.VIEW_AREAS_GROUP, LeagueTransportWidgets.VIEW_AREAS_CHILD);
				case 3:
					return Rs2Widget.isWidgetVisible(areasListRootGroup(targetRegion), areasListRootChild(targetRegion));
				default:
					return false;
			}
		}
	}

	private static final class TeleportGateSnapshot
	{
		private final LeaguesTeleportFailureReason contextFailureReason;
		private final String contextFailureMessage;
		private final EnumSet<LeaguesRegion> unlockedRegions;

		private TeleportGateSnapshot(
				LeaguesTeleportFailureReason contextFailureReason,
				String contextFailureMessage,
				EnumSet<LeaguesRegion> unlockedRegions)
		{
			this.contextFailureReason = contextFailureReason;
			this.contextFailureMessage = contextFailureMessage;
			this.unlockedRegions = unlockedRegions != null ? EnumSet.copyOf(unlockedRegions) : EnumSet.noneOf(LeaguesRegion.class);
		}
	}

	private static Optional<TeleportGateSnapshot> evaluateTeleportGates(LeaguesRegion region)
	{
		Objects.requireNonNull(region, "region");
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			String ctxReject = leaguesContextRejectOrEmptySuccess();
			if (!ctxReject.isEmpty())
			{
				return new TeleportGateSnapshot(mapContextFailureReason(ctxReject), ctxReject, null);
			}
			return new TeleportGateSnapshot(null, null, readUnlockedRegionsFromSelectionVarbits());
		});
	}

	private static EnumSet<LeaguesRegion> readUnlockedRegionsFromSelectionVarbits()
	{
		EnumSet<LeaguesRegion> unlocked = EnumSet.noneOf(LeaguesRegion.class);
		for (int vb : LEAGUE_AREA_SELECTION_VARBITS)
		{
			int areaId = Microbot.getVarbitValue(vb);
			LeaguesRegion r = byAreaIdOrNull(areaId);
			if (r != null)
			{
				unlocked.add(r);
			}
		}
		return unlocked;
	}

	private static LeaguesRegion byAreaIdOrNull(int areaId)
	{
		if (areaId <= 0)
		{
			return null;
		}
		for (LeaguesRegion r : LeaguesRegion.values())
		{
			if (r.getAreaId() == areaId)
			{
				return r;
			}
		}
		return null;
	}

	private static LeaguesTeleportFailureReason mapContextFailureReason(String message)
	{
		if ("Client not available.".equals(message))
		{
			return LeaguesTeleportFailureReason.CLIENT_UNAVAILABLE;
		}
		if ("Not on a Leagues / seasonal world.".equals(message))
		{
			return LeaguesTeleportFailureReason.NOT_SEASONAL_WORLD;
		}
		if ("League account not active.".equals(message))
		{
			return LeaguesTeleportFailureReason.LEAGUE_ACCOUNT_INACTIVE;
		}
		if ("Leagues context: client thread unavailable.".equals(message))
		{
			return LeaguesTeleportFailureReason.CLIENT_THREAD_UNAVAILABLE;
		}
		return LeaguesTeleportFailureReason.UNKNOWN;
	}

	private static String leaguesContextRejectOrEmptySuccess()
	{
		Client c = Microbot.getClient();
		if (c == null)
		{
			return "Client not available.";
		}
		EnumSet<WorldType> types = c.getWorldType();
		if (types == null || !types.contains(WorldType.SEASONAL))
		{
			return "Not on a Leagues / seasonal world.";
		}
		if (Microbot.getVarbitValue(VarbitID.LEAGUE_ACCOUNT) <= 0)
		{
			return "League account not active.";
		}
		return "";
	}

	private static String verifyLeaguesContextOrNull()
	{
		Optional<String> msgOpt = Microbot.getClientThread().runOnClientThreadOptional(Rs2LeaguesTransport::leaguesContextRejectOrEmptySuccess);
		if (!msgOpt.isPresent())
		{
			return "Leagues context: client thread unavailable.";
		}
		String msg = msgOpt.get();
		return msg.isEmpty() ? null : msg;
	}

	private static void invokeCcOp(int packedWidgetId, String option, String target)
	{
		Objects.requireNonNull(option, "option");
		String targetNonNull = target != null ? target : "";

		Rectangle bounds = Microbot.getClientThread().runOnClientThreadOptional(() ->
		{
			Widget w = Rs2Widget.getWidget(packedWidgetId);
			return w != null ? w.getBounds() : null;
		}).orElse(null);

		Rectangle clickRect = bounds != null ? bounds : new Rectangle(1, 1, 1, 1);
		Microbot.doInvoke(new NewMenuEntry()
				.option(option)
				.target(targetNonNull)
				.identifier(LEAGUE_TRANSPORT_CC_OP_IDENTIFIER)
				.opcode(MenuAction.CC_OP.getId())
				.param0(LEAGUE_TRANSPORT_CC_OP_PARAM0)
				.param1(packedWidgetId)
				.itemId(-1),
				clickRect);
	}
}

