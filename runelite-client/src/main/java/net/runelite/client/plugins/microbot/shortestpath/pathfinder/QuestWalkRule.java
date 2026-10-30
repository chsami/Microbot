package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

/**
 * Two switches, both off by default.
 *
 * <p><b>On-foot restriction</b>: ships, charters, quetzals, ferries, spirit trees, fairy rings, gliders, carpets,
 * balloons, minecarts, canoes, mushtrees and teleports are blocked for every search. The quest script
 * ({@link #setActive}) and the DD Chopper plugin ({@link #setOnFootOnly}) each ask for it independently.
 *
 * <p><b>Crossing mode</b> ({@link #setCrossingMode}): only the quest script sets it, and it only takes effect
 * for a pathfinder whose targets are the target of a walk the quest script's own thread has in flight
 * ({@link #beginQuestWalk}). A search that ends with the target proven unreachable
 * ({@link PathTerminationReason#SEARCH_EXHAUSTED}, not a cutoff, not a sealed target) is repeated in widening
 * {@link Stage}s, and the first stage that reaches the target is announced:
 * <ol>
 *   <li>{@link Stage#ON_FOOT}: nothing restricted.</li>
 *   <li>{@link Stage#OWNED_JEWELLERY}: only the teleports of jewellery the player owns (Ring of wealth, Amulet of
 *   glory, Games necklace, Ring of dueling, and the Skills necklace for East Ardougne targets). Besides being a
 *   fallback, this stage is also tried when a trip on foot is long: see {@link #jewelleryBeatsWalking}.</li>
 *   <li>{@link Stage#NAMED_ROUTES}: only the routes the user prefers (the Veos ferry from Port Sarim; the Skills
 *   necklace to the Fishing Guild, for targets around East Ardougne only).</li>
 *   <li>{@link Stage#CROSSINGS}: any boat, ship, charter, canoe, quetzal or long surface ride (Primio, the travel
 *   cart, Waydar, the Fremennik boat).</li>
 *   <li>{@link Stage#TELEPORTS}: everything else, the last resort.</li>
 * </ol>
 * While crossing mode is in effect the pathfinder config also keeps the transports of every stage loaded
 * whatever the user's global ShortestPath toggles say ({@link #forcesToggle}); the stage decides what a search may
 * use. The teleport-stage toggles it forces include teleport spells and minigame teleports.
 */
public final class QuestWalkRule
{
	/** How far a search may widen, in order. */
	public enum Stage
	{
		ON_FOOT,
		OWNED_JEWELLERY,
		NAMED_ROUTES,
		CROSSINGS,
		TELEPORTS;

		/** The next wider stage, or {@code null} when nothing is left to try. */
		public Stage next()
		{
			Stage[] all = values();
			return ordinal() + 1 < all.length ? all[ordinal() + 1] : null;
		}
	}

	private static volatile boolean questActive;
	private static volatile boolean onFootOnly;
	private static volatile boolean crossingMode;
	private static final CopyOnWriteArrayList<WorldPoint> QUEST_WALK_TARGETS = new CopyOnWriteArrayList<>();
	private static final ThreadLocal<Deque<WorldPoint>> OWN_WALKS = ThreadLocal.withInitial(ArrayDeque::new);
	private static final ThreadLocal<Boolean> QUEST_THREAD = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static volatile WorldPoint lastQuestWalkTarget;
	private static final ThreadLocal<Stage> STAGE = ThreadLocal.withInitial(() -> Stage.ON_FOOT);
	private static final ThreadLocal<Boolean> ARDOUGNE_TARGET = ThreadLocal.withInitial(() -> Boolean.FALSE);
	private static final UnreachableBackoff UNREACHABLE = new UnreachableBackoff();
	private static volatile String lastAnnounced = "";

	/** Boats, ships and the like: they carry the player to another landmass. */
	private static final Set<TransportType> CROSSING = EnumSet.of(
		TransportType.BOAT, TransportType.CANOE, TransportType.CHARTER_SHIP, TransportType.SHIP,
		TransportType.QUETZAL);

	/** Other travel networks; only allowed in the teleport stage. */
	private static final Set<TransportType> TRAVEL = EnumSet.of(
		TransportType.FAIRY_RING, TransportType.GNOME_GLIDER, TransportType.MINECART, TransportType.POH,
		TransportType.SPIRIT_TREE, TransportType.MAGIC_CARPET, TransportType.HOT_AIR_BALLOON,
		TransportType.MAGIC_MUSHTREE);

	/** Config keys of the toggles for {@link #CROSSING} and {@link #TRAVEL} transports and teleport spells. */
	private static final Set<String> FORCED_TOGGLES = Set.of(
		"useBoats", "useCanoes", "useCharterShips", "useShips", "useQuetzals",
		"useFairyRings", "useGnomeGliders", "useMinecarts", "usePoh", "useSpiritTrees", "useMagicCarpets",
		"useHotAirBalloons", "useMagicMushtrees");

	/**
	 * NPC and travel-cart rides longer than this between two surface tiles carry the player across the map
	 * (Primio is 1600 tiles, Waydar 893, the travel cart 270, the Fremennik boat 335); the local guides and
	 * ladders are all under 200.
	 */
	static final int LONG_RIDE_TILES = 250;

	/**
	 * How long the on-foot search of a quest walk may go without getting closer before it counts as stuck. The usual
	 * few seconds are not enough to flood a whole landmass on a loaded client, and a cut-off search proves nothing.
	 */
	public static final long EXTENDED_CUTOFF_MS = 15_000L;

	/** How long the walker waits for any pathfinder before giving up on a walk. */
	public static final long PATHFINDER_TIMEOUT_MS = 10_000L;

	/**
	 * How long the walker waits for a quest walk's pathfinder: the usual cutoff, the longer on-foot look and the
	 * three widened searches must fit, or the walker gives up on the walk before the search can say anything.
	 */
	public static final long QUEST_PATHFINDER_TIMEOUT_MS = 40_000L;

	/** The walker's patience for the pathfinder of the walk to {@code target}. */
	public static long pathfinderTimeoutMs(WorldPoint target)
	{
		boolean questSearch = target != null && crossingEnabledFor(Set.of(WorldPointUtil.packWorldPoint(target)));
		return questSearch ? QUEST_PATHFINDER_TIMEOUT_MS : PATHFINDER_TIMEOUT_MS;
	}
	private static final int SURFACE_MAX_X = 4000;
	private static final int SURFACE_MAX_Y = 4200;

	/** Rough bounds of East Ardougne, its Fishing Guild approach and the roads between. */
	private static final int ARDOUGNE_MIN_X = 2430;
	private static final int ARDOUGNE_MAX_X = 2700;
	private static final int ARDOUGNE_MIN_Y = 3200;
	private static final int ARDOUGNE_MAX_Y = 3420;

	/** A trip on foot costing more than this many ticks is worth comparing with an owned jewellery teleport. */
	static final long LONG_WALK_COST = 150;
	/** The jewellery route has to cost less than this share of the walk to be taken instead of it. */
	static final double JEWELLERY_COST_SHARE = 0.6;

	/** Teleport jewellery that quest walking uses once the player owns it (the planner only lists owned items). */
	private static final String[] OWNED_JEWELLERY = {
		"ring of wealth", "amulet of glory", "games necklace", "ring of dueling"};

	/** Jewellery destinations quest walking never teleports to: the Wilderness and a boss lair. */
	private static final String[] EXCLUDED_JEWELLERY_DESTINATIONS = {
		"ring of dueling: ferox enclave", "games necklace: corporeal beast"};

	private static final Set<String> PORT_SARIM_SEAMEN = Set.of("captain tobias", "seaman lorris", "seaman thresnor");

	private static final String VEOS = "veos";
	private static final String CABIN_BOY_HERBERT = "cabin boy herbert";
	private static final String SKILLS_NECKLACE_FISHING_GUILD = "skills necklace: fishing guild";

	private QuestWalkRule()
	{
	}

	/** Quest script only: switches its on-foot restriction; off also ends crossing mode and forgets the back-off. */
	public static void setActive(boolean on)
	{
		questActive = on;
		if (!on)
		{
			crossingMode = false;
			UNREACHABLE.reset();
			lastAnnounced = "";
			lastQuestWalkTarget = null;
			pendingFare = 0;
			fareTargets = Set.of();
		}
	}

	private static volatile int pendingFare;
	private static volatile Set<Integer> fareTargets = Set.of();

	/**
	 * Coins the route of the quest walk in progress pays in fares (Port Sarim to Karamja costs 30, a toll gate 10); 0 when
	 * none, or when the fare was worked out for another walk target.
	 */
	public static int pendingFare()
	{
		WorldPoint walking = lastQuestWalkTarget;
		return walking != null && fareTargets.contains(WorldPointUtil.packWorldPoint(walking)) ? pendingFare : 0;
	}

	/** Sets the fares of the route just planned to these targets, 0 included, so an old fare never lingers. */
	static void setFare(Set<Integer> targets, int coins)
	{
		fareTargets = Set.copyOf(targets);
		pendingFare = coins;
	}

	/** The fare is paid or no longer needed. */
	public static void clearFare()
	{
		pendingFare = 0;
	}

	/** Whether the quest script is running its automation right now (on, not paused). */
	public static boolean isQuestAutomationActive()
	{
		return questActive;
	}

	/** Whether the calling thread is the quest script's loop thread and the quest automation is running. */
	public static boolean isQuestThread()
	{
		return questActive && QUEST_THREAD.get();
	}

	/** Other plugins: keep the walker on foot while they run. Never touches the quest script's state. */
	public static void setOnFootOnly(boolean on)
	{
		onFootOnly = on;
	}

	/** Whether any on-foot restriction is requested. */
	public static boolean isActive()
	{
		return questActive || onFootOnly;
	}

	/** Quest script only: whether walks of the quest thread may cross water and teleport when nothing else reaches. */
	public static void setCrossingMode(boolean on)
	{
		crossingMode = on && questActive;
	}

	/** Marks the calling thread as the quest script's loop thread. */
	public static void markQuestThread(boolean on)
	{
		QUEST_THREAD.set(on);
	}

	/**
	 * Called when a walk starts. Returns true when it is a quest walk (the caller must pass the result to
	 * {@link #endQuestWalk}); records the target for the status and the unreachable back-off.
	 */
	public static boolean beginQuestWalk(WorldPoint target)
	{
		if (!QUEST_THREAD.get())
		{
			return false;
		}
		OWN_WALKS.get().push(target);
		QUEST_WALK_TARGETS.add(target);
		lastQuestWalkTarget = target;
		return true;
	}

	public static void endQuestWalk(boolean wasQuestWalk)
	{
		if (wasQuestWalk)
		{
			QUEST_WALK_TARGETS.remove(OWN_WALKS.get().pop());
		}
	}

	/** Whether a walk started by the quest script is running right now. */
	public static boolean isQuestWalkInFlight()
	{
		return !QUEST_WALK_TARGETS.isEmpty();
	}

	/** Whether the forced toggles apply right now: crossing mode, during a quest walk. */
	public static boolean crossingEnabled()
	{
		return questActive && crossingMode && isQuestWalkInFlight();
	}

	/**
	 * Whether a pathfinder for {@code packedTargets} may widen: crossing is on and it is searching for the target of
	 * a quest walk in flight, not for some other plugin's or the Agent Server's target.
	 */
	public static boolean crossingEnabledFor(Set<Integer> packedTargets)
	{
		if (!crossingEnabled())
		{
			return false;
		}
		for (WorldPoint walking : QUEST_WALK_TARGETS)
		{
			if (packedTargets.contains(WorldPointUtil.packWorldPoint(walking)))
			{
				return true;
			}
		}
		return false;
	}

	/** Pure decision: widen only when the on-foot search proved the target unreachable. */
	public static boolean shouldWiden(boolean crossingEnabled, PathTerminationReason onFootResult, boolean sealedTarget)
	{
		return crossingEnabled && !sealedTarget && onFootResult == PathTerminationReason.SEARCH_EXHAUSTED;
	}

	/** Pure decision: a cut-off on-foot search proves nothing, so it is repeated with {@link #EXTENDED_CUTOFF_MS}. */
	public static boolean shouldRetryOnFootLonger(boolean crossingEnabled, PathTerminationReason onFootResult,
		boolean sealedTarget)
	{
		return crossingEnabled && !sealedTarget && onFootResult == PathTerminationReason.CUTOFF_REACHED;
	}

	static Stage stage()
	{
		return STAGE.get();
	}

	/** Runs {@code body} with the search widened to {@code stage} on this thread. */
	static void runAt(Stage stage, boolean ardougneTarget, Runnable body)
	{
		STAGE.set(stage);
		ARDOUGNE_TARGET.set(ardougneTarget);
		try
		{
			body.run();
		}
		finally
		{
			STAGE.set(Stage.ON_FOOT);
			ARDOUGNE_TARGET.set(Boolean.FALSE);
		}
	}

	/** Whether the pathfinder config must keep the transports behind this toggle even when the user switched it off. */
	public static boolean forcesToggle(String configKey)
	{
		return FORCED_TOGGLES.contains(configKey) && crossingEnabled();
	}

	/** Whether teleport items must be loaded although the user's policy is {@code NONE}. */
	public static boolean forcesTeleportItems()
	{
		return crossingEnabled();
	}

	public static boolean isRestrictedType(TransportType type, WorldPoint origin)
	{
		return CROSSING.contains(type) || TRAVEL.contains(type) || TransportType.isTeleport(type, origin);
	}

	/** Whether quest walking keeps this transport off until the search is widened. */
	public static boolean isRestricted(Transport transport)
	{
		return isRestrictedType(transport.getType(), transport.getOrigin()) || isLongRide(transport);
	}

	/**
	 * A crossing of the CROSSINGS stage: boats, ships, canoes, quetzals and long rides. Charter ships are left to the
	 * last resort: they cost hundreds of coins where the regular ship costs thirty.
	 */
	static boolean isCrossing(Transport transport)
	{
		return (CROSSING.contains(transport.getType()) && transport.getType() != TransportType.CHARTER_SHIP)
			|| isLongRide(transport);
	}

	private static boolean isLongRide(Transport transport)
	{
		WorldPoint from = transport.getOrigin();
		WorldPoint to = transport.getDestination();
		return (transport.getType() == TransportType.NPC || transport.getType() == TransportType.TRANSPORT)
			&& from != null && to != null && onSurface(from) && onSurface(to)
			&& from.distanceTo2D(to) > LONG_RIDE_TILES;
	}

	private static boolean onSurface(WorldPoint point)
	{
		return point.getX() < SURFACE_MAX_X && point.getY() < SURFACE_MAX_Y;
	}

	/** Whether any target lies around East Ardougne, where the Skills necklace route is wanted. */
	public static boolean isArdougneArea(Set<Integer> packedTargets)
	{
		for (int packed : packedTargets)
		{
			WorldPoint point = WorldPointUtil.unpackWorldPoint(packed);
			if (point.getX() >= ARDOUGNE_MIN_X && point.getX() <= ARDOUGNE_MAX_X
				&& point.getY() >= ARDOUGNE_MIN_Y && point.getY() <= ARDOUGNE_MAX_Y)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The user's named routes: the Veos ferry between Port Sarim and Zeah, and, for Ardougne targets only, the
	 * Skills necklace to the Fishing Guild.
	 */
	static boolean isNamedRoute(Transport transport, boolean ardougneTarget)
	{
		if (transport.getType() == TransportType.SHIP && transport.getName() != null)
		{
			String npc = transport.getName().toLowerCase(Locale.ROOT);
			if (npc.equals(VEOS) || npc.equals(CABIN_BOY_HERBERT))
			{
				return true;
			}
			// the 30 coin ship between Port Sarim and Musa Point (Karamja)
			String where = transport.getDisplayInfo() == null ? "" : transport.getDisplayInfo().toLowerCase(Locale.ROOT);
			return PORT_SARIM_SEAMEN.contains(npc) && (where.equals("musa point") || where.equals("port sarim"));
		}
		return ardougneTarget && isSkillsNecklaceToFishingGuild(transport);
	}

	private static boolean isSkillsNecklaceToFishingGuild(Transport transport)
	{
		return transport.getType() == TransportType.TELEPORTATION_ITEM && transport.getDisplayInfo() != null
			&& transport.getDisplayInfo().toLowerCase(Locale.ROOT).equals(SKILLS_NECKLACE_FISHING_GUILD);
	}

	/** A teleport of the Ring of wealth, Amulet of glory, Games necklace or Ring of dueling. */
	static boolean isOwnedJewellery(Transport transport)
	{
		if (transport.getType() != TransportType.TELEPORTATION_ITEM || transport.getDisplayInfo() == null)
		{
			return false;
		}
		String info = transport.getDisplayInfo().toLowerCase(Locale.ROOT);
		for (String excluded : EXCLUDED_JEWELLERY_DESTINATIONS)
		{
			if (info.equals(excluded))
			{
				return false;
			}
		}
		for (String jewellery : OWNED_JEWELLERY)
		{
			if (info.startsWith(jewellery))
			{
				return true;
			}
		}
		return false;
	}

	/** An owned-jewellery teleport that quest walking may use: the listed jewellery, or the Skills necklace route. */
	static boolean isJewelleryTeleport(Transport transport)
	{
		return isOwnedJewellery(transport) || isSkillsNecklaceToFishingGuild(transport);
	}

	private static volatile boolean longTravelHeld;

	/** Charter ships (hundreds of coins, a bank trip) are never part of a quest walk unless this is switched on. */
	private static volatile boolean allowCharterShips;

	/** Set while a transport struck out this walk; the last-resort teleport stage waits for the transport's retry. */
	private static volatile boolean transportBlockLearned;

	/** Largest fare a quest walk fetches coins for; ordinary ships and gates, never a charter. */
	static final int MAX_FETCHED_FARE = 200;

	public static void setAllowCharterShips(boolean allow)
	{
		allowCharterShips = allow;
	}

	public static void setTransportBlockLearned(boolean learned)
	{
		transportBlockLearned = learned;
	}

	/** Pure: the last-resort stage never replaces a transport that was only refused this walk. */
	static boolean stageSkipped(Stage stage, boolean transportBlocked)
	{
		return stage == Stage.TELEPORTS && transportBlocked;
	}

	public static boolean skipsStage(Stage stage)
	{
		return stageSkipped(stage, transportBlockLearned);
	}

	/** Pure: whether a charter ship may be used by the last-resort stage. */
	static boolean charterPermitted(boolean allowed)
	{
		return allowed;
	}

	/** Pure: whether a quest walk may go to the bank for coins to pay this fare. */
	public static boolean fareFetchAllowed(int fare)
	{
		return fare <= MAX_FETCHED_FARE;
	}
	/** How long after a fight a quest walk still does not teleport or travel far. */
	public static final long COMBAT_HOLD_MS = 15_000;

	/** Pure decision: no long-distance travel or teleport while fighting, or just after a fight. */
	public static boolean shouldHoldLongTravel(boolean inCombat, long lastCombatAtMs, long nowMs)
	{
		return inCombat || (lastCombatAtMs > 0 && nowMs - lastCombatAtMs < COMBAT_HOLD_MS);
	}

	/** Set by the quest loop every tick; while true the walker plans neither owned-jewellery nor teleport routes. */
	public static void setLongTravelHeld(boolean held)
	{
		longTravelHeld = held;
	}

	public static boolean isLongTravelHeld()
	{
		return longTravelHeld;
	}

	/** Pure decision: a walk this long is compared with the owned-jewellery route. */
	public static boolean jewelleryWorthTrying(long onFootCost)
	{
		return onFootCost > LONG_WALK_COST;
	}

	/** Pure decision: the jewellery route replaces the walk only when it is substantially cheaper. */
	public static boolean jewelleryBeatsWalking(long onFootCost, long jewelleryCost)
	{
		return jewelleryWorthTrying(onFootCost) && jewelleryCost >= 0 && jewelleryCost < onFootCost * JEWELLERY_COST_SHARE;
	}

	/** Whether a search at {@code stage} may use {@code transport}. */
	public static boolean permits(Stage stage, Transport transport, boolean ardougneTarget)
	{
		if (!isRestricted(transport))
		{
			return true;
		}
		switch (stage)
		{
			case OWNED_JEWELLERY:
				return isOwnedJewellery(transport) || (ardougneTarget && isSkillsNecklaceToFishingGuild(transport));
			case NAMED_ROUTES:
				return isNamedRoute(transport, ardougneTarget);
			case CROSSINGS:
				return isCrossing(transport);
			case TELEPORTS:
				// The last resort never newly enables tablets, scrolls or spell and minigame teleports: of the teleports
				// only the owned jewellery's. When nothing else reaches, the walk gives up and says so.
				return transport.getType() != TransportType.TELEPORTATION_SPELL
					&& transport.getType() != TransportType.TELEPORTATION_MINIGAME
					&& (transport.getType() != TransportType.CHARTER_SHIP || charterPermitted(allowCharterShips))
					&& (transport.getType() != TransportType.TELEPORTATION_ITEM
						|| isOwnedJewellery(transport) || isSkillsNecklaceToFishingGuild(transport));
			default:
				return false;
		}
	}

	/** Whether the search must skip this transport right now. */
	public static boolean blocks(Transport transport)
	{
		return isActive() && !permits(STAGE.get(), transport, ARDOUGNE_TARGET.get());
	}

	/** True the first time this stage is announced for these targets, so a step is logged once. */
	static boolean firstAnnouncement(Stage stage, Set<?> targets)
	{
		String key = stage + "@" + targets;
		if (key.equals(lastAnnounced))
		{
			return false;
		}
		lastAnnounced = key;
		return true;
	}

	/** The log line for a route found at a widened stage; {@code used} lists the restricted transports it takes. */
	public static String describe(Stage stage, String target, Set<String> used)
	{
		if (stage == Stage.OWNED_JEWELLERY)
		{
			return "Quest walk: teleporting via " + used + " toward " + target;
		}
		if (stage == Stage.TELEPORTS)
		{
			return "Quest walk: " + target + " not reachable on foot or by crossing; teleporting via " + used;
		}
		return "Quest walk: " + target + " not reachable on foot; crossing via " + used;
	}

	/** Records that no stage reached {@code target} from {@code playerAt}. Returns true the first time. */
	public static boolean reportUnreachable(WorldPoint target, WorldPoint playerAt)
	{
		return UNREACHABLE.record(target, playerAt, System.currentTimeMillis());
	}

	/** Whether a quest walk to {@code target} should be skipped for now because it just proved unreachable. */
	public static boolean isBackedOff(WorldPoint target, WorldPoint playerAt)
	{
		return questActive && QUEST_THREAD.get() && UNREACHABLE.isBackedOff(target, playerAt, System.currentTimeMillis());
	}

	/** A quest walk reached its target: forget any earlier failure for it. */
	public static void walkArrived(WorldPoint target)
	{
		UNREACHABLE.clear(target);
		pendingFare = 0;
	}

	/** The step target currently given up on, or {@code null} (also when the quest has moved on to another step). */
	public static WorldPoint unreachableTarget(WorldPoint playerAt)
	{
		if (!questActive)
		{
			return null;
		}
		WorldPoint given = UNREACHABLE.target(playerAt, System.currentTimeMillis());
		return given != null && given.equals(lastQuestWalkTarget) ? given : null;
	}

	public static long unreachableRetryInMs()
	{
		return UNREACHABLE.remainingMs(System.currentTimeMillis());
	}
}
