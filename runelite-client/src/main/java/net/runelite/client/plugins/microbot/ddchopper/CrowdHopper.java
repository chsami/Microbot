package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.http.api.worlds.WorldType;

import java.util.Set;
import java.util.function.Consumer;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Hops worlds when too many other players are at the spot, or seeks a busier world for Forestry events. */
final class CrowdHopper
{
	/** The player is only counted as "at the spot" within this distance of its centre. */
	private static final int SPOT_PROXIMITY = 25;
	private static final int HOP_TIMEOUT_MS = 20_000;
	private static final int WORLD_PICK_ATTEMPTS = 8;
	private static final int BUSY_WORLD_CANDIDATES = 30;

	private final DDChopperConfig config;
	private final Consumer<String> status;
	private HopGate gate;
	private CrowdRule.Mode mode = CrowdRule.Mode.OFF;
	private int threshold = -1;

	CrowdHopper(DDChopperConfig config, Consumer<String> status)
	{
		this.config = config;
		this.status = status;
		refresh();
	}

	boolean isActive()
	{
		return mode != CrowdRule.Mode.OFF;
	}

	/** Re-reads the crowd options so panel changes apply live; rebuilds the hop gate when the rule changes. */
	void refresh()
	{
		CrowdRule.Mode newMode = CrowdRule.resolve(config.forestryEnabled(), config.forestryBusyWorld(), config.hopWhenCrowded());
		int newThreshold = newMode == CrowdRule.Mode.SEEK_BUSY ? config.forestryMinPlayers() : config.maxOthers();
		if (newMode != mode || newThreshold != threshold || gate == null)
		{
			mode = newMode;
			threshold = newThreshold;
			gate = newMode == CrowdRule.Mode.SEEK_BUSY
				? HopGate.seekBusy(newThreshold, config.maxHopsPerHour())
				: new HopGate(newThreshold, config.maxHopsPerHour());
		}
	}

	/** Returns true when this tick was spent on a hop. */
	boolean checkAndHop(Spot spot)
	{
		WorldPoint me = Rs2Player.getWorldLocation();
		if (me == null || spot.center().distanceTo2D(me) > SPOT_PROXIMITY)
		{
			return false;
		}
		int others = countOthers();
		long now = System.currentTimeMillis();
		HopGate.Decision decision = gate.decide(others, now);
		if (decision == HopGate.Decision.CAPPED)
		{
			status.accept("Hop limit reached (" + others + " at spot)");
			return false;
		}
		if (decision != HopGate.Decision.HOP)
		{
			return false;
		}
		int world = pickWorld(spot);
		if (world <= 0)
		{
			Microbot.log("DD Chopper: " + others + " others at the spot but no suitable world found");
			gate.recordHop(now);
			return false;
		}
		Microbot.log("DD Chopper: " + others + " other players at the spot, hopping to "
			+ (mode == CrowdRule.Mode.SEEK_BUSY ? "busier " : "") + "world " + world);
		status.accept("Hopping to " + world);
		gate.recordHop(now);
		if (Microbot.hopToWorld(world))
		{
			sleepUntil(() -> !Microbot.isHopping() && Microbot.isLoggedIn(), HOP_TIMEOUT_MS);
		}
		return true;
	}

	private int countOthers()
	{
		String myName = Microbot.getClientThread().runOnClientThreadOptional(
			() -> Microbot.getClient().getLocalPlayer() != null ? Microbot.getClient().getLocalPlayer().getName() : null)
			.orElse(null);
		return (int) Microbot.getRs2PlayerCache().query().within(config.crowdRadius()).toListOnClientThread()
			.stream().filter(p -> p != null && (myName == null || !myName.equals(p.getName()))).count();
	}

	private int pickWorld(Spot spot)
	{
		return mode == CrowdRule.Mode.SEEK_BUSY ? pickBusyWorld(spot) : pickRandomWorld(spot);
	}

	/** Highest-population world that is accessible and safe (Rs2WorldUtil filters PvP, high risk, total level, deadman, seasonal), members-aware. */
	private int pickBusyWorld(Spot spot)
	{
		Set<Integer> avoid = HopGate.parseWorlds(config.avoidWorlds());
		int current = Microbot.getClient().getWorld();
		var result = Microbot.getWorldService().getWorlds();
		for (int id : Rs2WorldUtil.getAccessibleWorldsByPopulation(BUSY_WORLD_CANDIDATES))
		{
			if (id == current || avoid.contains(id))
			{
				continue;
			}
			var world = result == null ? null : result.findWorld(id);
			if (world != null && spot.isMembers() && !world.getTypes().contains(WorldType.MEMBERS))
			{
				continue;
			}
			return id;
		}
		return -1;
	}

	private int pickRandomWorld(Spot spot)
	{
		Set<Integer> avoid = HopGate.parseWorlds(config.avoidWorlds());
		int current = Microbot.getClient().getWorld();
		for (int i = 0; i < WORLD_PICK_ATTEMPTS; i++)
		{
			// Rs2WorldUtil skips PvP, high risk, total level, deadman, seasonal and other restricted worlds, and
			// F2P-only accounts only get F2P worlds.
			int world = Rs2WorldUtil.getRandomAccessibleWorld(true, true, spot.isMembers());
			if (world > 0 && world != current && !avoid.contains(world))
			{
				return world;
			}
		}
		return -1;
	}
}
