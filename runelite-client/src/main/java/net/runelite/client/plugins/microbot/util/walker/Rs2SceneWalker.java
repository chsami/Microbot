package net.runelite.client.plugins.microbot.util.walker;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.CollisionData;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.InstanceCoordinates;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.scene.SceneNavigator;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Quest automation only: walks inside an instanced region (a quest cave) on the live collision flags of the loaded
 * scene, because the global pathfinder has no map for instances. Used by {@code Rs2Walker} when the pathfinder reports
 * the target not walkable or without a path; in every other situation it does nothing and returns null, so the
 * existing walker logic is unchanged.
 *
 * <p>When no walking path exists it may use ONE known passage, an object on an explicit allowlist offering
 * "Climb-over" (the Sourhog cave blockage), once per quest step, after verifying that the player really changed side.
 *
 * <p>Must be called from a script thread, never the client thread.
 */
@Slf4j
public final class Rs2SceneWalker
{
	/** The only passage action this walker uses. */
	static final String PASSAGE_ACTION = "Climb-over";
	/** Object ids that may be crossed, with the exact confirmation prompt each one asks (answered "Yes"). */
	private static final Map<Integer, String> PASSAGES = Map.of(40331, "Climb over the blockage?");
	private static final int HOP_TILES = 12;
	private static final long BUDGET_MS = 30_000;
	private static final int MAX_CLICK_ATTEMPTS = 3;

	/** Passages crossed during the current quest step (object id and tile), so none is used twice. */
	private static final Set<String> crossed = ConcurrentHashMap.newKeySet();
	private static final Map<String, Integer> attempts = new ConcurrentHashMap<>();
	private static final Map<String, Integer> approachStalls = new ConcurrentHashMap<>();
	private static final int MAX_APPROACH_STALLS = 3;
	private static volatile String stepKey = "";

	private Rs2SceneWalker()
	{
	}

	/** The quest script tells which step it is on; a new step forgets the passages used for the last one. */
	public static void onQuestStep(String key)
	{
		String next = key == null ? "" : key;
		if (!Objects.equals(stepKey, next))
		{
			stepKey = next;
			crossed.clear();
			attempts.clear();
			approachStalls.clear();
		}
	}

	private static final class Snapshot
	{
		int[][] flags;
		int playerX;
		int playerY;
		int targetX = -1;
		int targetY = -1;
		int baseX;
		int baseY;
		int plane;
	}

	/**
	 * Walks to within {@code distance} of {@code target} inside the quest's instance.
	 *
	 * @param cancelled the walker's own cancel test for this walk, checked before every hop
	 * @return ARRIVED, MOVING (budget used up) or EXIT (cancelled); null when this walker does not apply or cannot
	 * help, in which case the caller carries on with its own logic
	 */
	public static WalkerState tryWalk(WorldPoint target, int distance, BooleanSupplier cancelled)
	{
		if (!QuestWalkRule.isQuestThread())
		{
			return null;
		}
		Snapshot first = snapshot(target);
		if (first == null)
		{
			return null;
		}
		long deadline = System.currentTimeMillis() + BUDGET_MS;
		int stalled = 0;
		while (System.currentTimeMillis() < deadline)
		{
			if (shouldStop(cancelled))
			{
				return WalkerState.EXIT;
			}
			Snapshot now = snapshot(target);
			if (now == null)
			{
				return null;
			}
			boolean[][] goal = SceneNavigator.goalTiles(now.flags, now.targetX, now.targetY, Math.max(distance, 0));
			if (!hasGoal(goal))
			{
				goal = SceneNavigator.goalTiles(now.flags, now.targetX, now.targetY, Math.max(distance, 1));
			}
			if (goal[now.playerX][now.playerY])
			{
				return WalkerState.ARRIVED;
			}
			java.util.List<int[]> path = SceneNavigator.path(now.flags, now.playerX, now.playerY, goal);
			if (path != null)
			{
				int before = now.playerX * SceneNavigator.SIZE + now.playerY;
				walkAlong(now, path, cancelled);
				Snapshot after = snapshot(target);
				stalled = after != null && after.playerX * SceneNavigator.SIZE + after.playerY == before ? stalled + 1 : 0;
				if (stalled >= 3)
				{
					log.warn("[SceneWalker] no progress towards {}", target);
					return null;
				}
				continue;
			}
			if (!crossPassage(now, goal, cancelled))
			{
				return null;
			}
		}
		return WalkerState.MOVING;
	}

	private static boolean shouldStop(BooleanSupplier cancelled)
	{
		return Thread.currentThread().isInterrupted() || Microbot.pauseAllScripts.get() || cancelled.getAsBoolean();
	}

	private static boolean hasGoal(boolean[][] goal)
	{
		for (boolean[] column : goal)
		{
			for (boolean tile : column)
			{
				if (tile)
				{
					return true;
				}
			}
		}
		return false;
	}

	/** One click towards a tile of the path (the furthest that is on screen), then wait to get there or stop. */
	private static void walkAlong(Snapshot now, java.util.List<int[]> path, BooleanSupplier cancelled)
	{
		int furthest = Math.min(path.size(), HOP_TILES);
		int[] hop = path.get(furthest - 1);
		boolean clicked = false;
		Rs2Walker.requestAutoRun();
		for (int index = furthest; index >= 1 && !clicked; index -= 4)
		{
			int[] candidate = path.get(index - 1);
			// the raw scene tile is the local point: nothing here is a template coordinate
			LocalPoint local = Microbot.getClientThread().invoke(() ->
				LocalPoint.fromScene(candidate[0], candidate[1], Microbot.getClient().getTopLevelWorldView()));
			if (local != null && Rs2Camera.isTileOnScreen(local))
			{
				Rs2Walker.walkFastLocal(local);
				hop = candidate;
				clicked = true;
			}
		}
		if (!clicked)
		{
			// off screen: the minimap click, which takes the template tile of the hop
			WorldPoint template = Microbot.getClientThread().invoke(() ->
				InstanceCoordinates.toTemplate(Microbot.getClient().getTopLevelWorldView(),
					new WorldPoint(now.baseX + path.get(furthest - 1)[0], now.baseY + path.get(furthest - 1)[1], now.plane)));
			clicked = template != null && Rs2Walker.walkFastCanvasWithAutoRun(template);
		}
		if (!clicked)
		{
			sleep(600);
			return;
		}
		final int[] arrive = hop;
		sleepUntil(Rs2Player::isMoving, 1500);
		sleepUntil(() ->
		{
			if (shouldStop(cancelled))
			{
				return true;
			}
			Snapshot s = snapshot(null);
			return s != null && (Math.max(Math.abs(s.playerX - arrive[0]), Math.abs(s.playerY - arrive[1])) <= 1
				|| !Rs2Player.isMoving());
		}, 8000);
	}

	/**
	 * No walking path: uses an allowlisted passage that joins the player's side to the target's, once per quest step.
	 *
	 * @return true when the walk should be re-planned (walked next to the passage, or crossed it and verified)
	 */
	private static boolean crossPassage(Snapshot now, boolean[][] goal, BooleanSupplier cancelled)
	{
		java.util.List<Rs2TileObjectModel> candidates = Microbot.getRs2TileObjectCache().query()
			.where(o -> PASSAGES.containsKey(o.getId()) && o.hasAction(PASSAGE_ACTION))
			.toListOnClientThread();
		java.util.List<SceneNavigator.Obstacle> obstacles = new ArrayList<>();
		for (Rs2TileObjectModel candidate : candidates)
		{
			WorldPoint at = candidate.getWorldLocation();
			if (at.getPlane() != now.plane || crossed.contains(passageKey(candidate)))
			{
				continue;
			}
			int minX = at.getX() - now.baseX;
			int minY = at.getY() - now.baseY;
			obstacles.add(new SceneNavigator.Obstacle(candidate, minX, minY,
				minX + Math.max(candidate.sizeX(), 1) - 1, minY + Math.max(candidate.sizeY(), 1) - 1));
		}
		SceneNavigator.Crossing crossing = SceneNavigator.findCrossing(now.flags, now.playerX, now.playerY, goal, obstacles);
		if (crossing == null)
		{
			return false;
		}
		Rs2TileObjectModel object = (Rs2TileObjectModel) crossing.obstacle.ref;
		String key = passageKey(object);
		boolean[][] nearGoal = new boolean[SceneNavigator.SIZE][SceneNavigator.SIZE];
		nearGoal[crossing.near[0]][crossing.near[1]] = true;
		java.util.List<int[]> toPassage = SceneNavigator.path(now.flags, now.playerX, now.playerY, nearGoal);
		if (toPassage == null)
		{
			return false;
		}
		if (!toPassage.isEmpty())
		{
			walkAlong(now, toPassage, cancelled);
			// an approach that gets nowhere is given up after three tries
			Snapshot moved = snapshot(null);
			boolean progressed = moved == null || moved.playerX != now.playerX || moved.playerY != now.playerY;
			if (progressed)
			{
				approachStalls.remove(key);
			}
			else if (approachStalls.merge(key, 1, Integer::sum) >= MAX_APPROACH_STALLS)
			{
				log.warn("[SceneWalker] cannot get next to the passage");
				return false;
			}
			return true;
		}
		if (attempts.merge(key, 1, Integer::sum) > MAX_CLICK_ATTEMPTS || shouldStop(cancelled))
		{
			return false;
		}
		Microbot.status = "Scene walker: " + PASSAGE_ACTION;
		log.info("[SceneWalker] using {} (id {})", PASSAGE_ACTION, object.getId());
		if (!object.click(PASSAGE_ACTION))
		{
			return false;
		}
		answerPassageDialogue(PASSAGES.get(object.getId()), cancelled);
		sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 4000);
		Snapshot after = snapshot(null);
		if (after == null)
		{
			return false;
		}
		// crossed only when the tile it was used from can no longer be walked to from where the player stands now
		boolean changedSide = !SceneNavigator.reachable(after.flags, after.playerX, after.playerY)[crossing.near[0]][crossing.near[1]];
		if (!changedSide)
		{
			log.warn("[SceneWalker] used {} but the player did not change side", PASSAGE_ACTION);
			return false;
		}
		crossed.add(key);
		return true;
	}

	private static String passageKey(Rs2TileObjectModel object)
	{
		WorldPoint at = object.getWorldLocation();
		return object.getId() + "@" + at.getX() + "," + at.getY();
	}

	/**
	 * Continues the passage's lines and answers its confirmation "Yes" only when the prompt is exactly the one expected
	 * for that object; any other question is left alone.
	 */
	private static void answerPassageDialogue(String expectedPrompt, BooleanSupplier cancelled)
	{
		long until = System.currentTimeMillis() + 6000;
		boolean seen = false;
		while (System.currentTimeMillis() < until && !shouldStop(cancelled))
		{
			if (Rs2Dialogue.isInDialogue())
			{
				seen = true;
				if (Rs2Dialogue.hasContinue())
				{
					Rs2Dialogue.clickContinue();
				}
				else if (Rs2Dialogue.hasDialogueOptionTitle(expectedPrompt, true))
				{
					Rs2Dialogue.clickOption("Yes", false);
				}
				else
				{
					return;
				}
				sleep(300);
			}
			else if (seen || Rs2Player.isMoving() || Rs2Player.isAnimating())
			{
				return;
			}
			else
			{
				sleep(200);
			}
		}
	}

	/** Reads the scene on the client thread; null unless the player is in a top-level instance (and holds the target). */
	private static Snapshot snapshot(WorldPoint target)
	{
		return Microbot.getClientThread().invoke(() ->
		{
			WorldView wv = Microbot.getClient().getTopLevelWorldView();
			if (!InstanceCoordinates.isTopLevelInstance(wv) || Microbot.getClient().getLocalPlayer() == null)
			{
				return null;
			}
			CollisionData[] maps = wv.getCollisionMaps();
			int plane = wv.getPlane();
			LocalPoint player = Microbot.getClient().getLocalPlayer().getLocalLocation();
			if (maps == null || plane >= maps.length || maps[plane] == null || player == null)
			{
				return null;
			}
			Snapshot s = new Snapshot();
			int[][] live = maps[plane].getFlags();
			s.flags = new int[live.length][];
			for (int x = 0; x < live.length; x++)
			{
				s.flags[x] = live[x].clone();
			}
			s.playerX = player.getSceneX();
			s.playerY = player.getSceneY();
			s.baseX = wv.getBaseX();
			s.baseY = wv.getBaseY();
			s.plane = plane;
			if (target != null)
			{
				// the target is a template tile: its plane is the template's, not the scene's
				LocalPoint local = InstanceCoordinates.toLocalNear(wv, target, player);
				if (local == null)
				{
					return null;
				}
				s.targetX = local.getSceneX();
				s.targetY = local.getSceneY();
			}
			return s;
		});
	}
}
