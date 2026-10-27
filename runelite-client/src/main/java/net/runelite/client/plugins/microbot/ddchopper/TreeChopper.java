package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Finds the nearest tree at the spot, clicks it, and keeps the pacing, blacklist and bird-nest pickup around that. */
final class TreeChopper
{
	private static final int NEST_SEARCH_RADIUS = 8;
	/** Animation window that counts as "still chopping". */
	private static final int CHOPPING_ANIMATION_MS = 1800;
	private static final int ACTION_TIMEOUT_MS = 3_000;
	private static final int NEST_PICKUP_TIMEOUT_MS = 4_000;
	private static final int BLACKLIST_MS = 60_000;
	/** Distance from the spot centre beyond which we walk back to it. */
	private static final int WALK_BACK_DISTANCE = 6;
	private static final int WALK_ARRIVAL_DISTANCE = 4;
	/** How far from the player a tree outside the spot rectangle may be and still be chopped. */
	private static final int NEARBY_TREE_DISTANCE = 20;

	private final HumanPacing pacing;
	private final Consumer<String> status;
	/** Trees that refused our click recently, with the time the ban ends. */
	private final Map<WorldPoint, Long> blacklist = new HashMap<>();
	private final ClickTracker<WorldPoint> clicks = new ClickTracker<>();
	private boolean wasBusy;

	TreeChopper(HumanPacing pacing, Consumer<String> status)
	{
		this.pacing = pacing;
		this.status = status;
	}

	boolean pickUpNest()
	{
		Rs2TileItemModel nest = Microbot.getRs2TileItemCache().query().withName("Bird nest")
			.nearestOnClientThread(NEST_SEARCH_RADIUS);
		if (nest == null)
		{
			return false;
		}
		status.accept("Picking up bird nest");
		if (nest.pickup())
		{
			sleepUntil(() -> !Rs2Player.isMoving(), NEST_PICKUP_TIMEOUT_MS);
			return true;
		}
		return false;
	}

	/**
	 * One chop step. The spot is resolved lazily so a still-busy player does not advance the spot rotation.
	 */
	void chop(TreeType tree, Supplier<Spot> activeSpot)
	{
		if (Rs2Player.isAnimating(CHOPPING_ANIMATION_MS) || Rs2Player.isMoving())
		{
			status.accept("Chopping " + tree.getObjectName());
			wasBusy = true;
			return;
		}
		Spot spot = activeSpot.get();
		WorldPoint me = Rs2Player.getWorldLocation();
		long now = System.currentTimeMillis();
		blacklist.values().removeIf(until -> until < now);

		List<Rs2TileObjectModel> found = Microbot.getRs2TileObjectCache().query()
			.withName(tree.getObjectName()).toListOnClientThread();
		Rs2TileObjectModel best = nearest(found, me, spot, false);
		if (best == null)
		{
			if (me == null || !spot.contains(me) && spot.center().distanceTo2D(me) > WALK_BACK_DISTANCE)
			{
				status.accept("Walking to " + spot.getLabel());
				Rs2Walker.walkTo(spot.center(), WALK_ARRIVAL_DISTANCE);
				return;
			}
			// The rectangle may be slightly off: take any matching tree close to us.
			best = nearest(found, me, spot, true);
			if (best == null)
			{
				status.accept("Waiting for " + tree.getObjectName() + " to respawn");
				return;
			}
		}
		status.accept("Chopping " + tree.getObjectName());
		WorldPoint at = best.getWorldLocation();
		if (wasBusy)
		{
			reactBeforeNextTree();
			wasBusy = false;
		}
		boolean started = best.click("Chop down")
			&& sleepUntil(() -> Rs2Player.isAnimating() || Rs2Player.isMoving(), ACTION_TIMEOUT_MS);
		if (clicks.record(at, started))
		{
			blacklist.put(at, now + BLACKLIST_MS);
		}
		if (started)
		{
			Rs2Antiban.actionCooldown();
			Rs2Antiban.takeMicroBreakByChance();
		}
	}

	/** Waits a human reaction delay (and now and then an idle pause) after a tree fell or chopping stopped. */
	private void reactBeforeNextTree()
	{
		if (pacing == null || !pacing.isEnabled())
		{
			return;
		}
		long idle = pacing.idlePauseMs(System.currentTimeMillis(), !Rs2Inventory.isFull());
		if (idle > 0)
		{
			status.accept("Idle pause");
			Microbot.log("DD Chopper: idle pause " + idle / 1000 + " s");
			sleep((int) idle);
		}
		status.accept("Reacting");
		sleep((int) pacing.reactionDelayMs());
	}

	private Rs2TileObjectModel nearest(List<Rs2TileObjectModel> trees, WorldPoint me, Spot spot, boolean anywhereNearby)
	{
		Rs2TileObjectModel best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Rs2TileObjectModel tree : trees)
		{
			WorldPoint at = tree.getWorldLocation();
			if (blacklist.containsKey(at))
			{
				continue;
			}
			boolean eligible = anywhereNearby
				? me != null && at.distanceTo2D(me) <= NEARBY_TREE_DISTANCE
				: spot.contains(at);
			if (!eligible)
			{
				continue;
			}
			int distance = me == null ? 0 : at.distanceTo2D(me);
			if (distance < bestDistance)
			{
				bestDistance = distance;
				best = tree;
			}
		}
		return best;
	}
}
