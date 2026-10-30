package net.runelite.client.plugins.microbot.util.coords;

import net.runelite.api.Constants;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Opt-in conversions between the two coordinate spaces of an instanced region. Nothing in the general entity models
 * uses them: it is for callers that deliberately compare an object or NPC with a template-space point (the quest
 * helper's step points and zones, the player's position).
 *
 * <p>Inside an instance the player lives in <b>template</b> space (the map the instance was copied from), while
 * {@code TileObject#getWorldLocation()} and {@code Actor#getWorldLocation()} report the <b>instance</b> tile, the raw
 * scene tile, which can be thousands of tiles away. Outside an instance both spaces are the same and every method
 * returns its input unchanged. Several scene chunks may be copies of one template chunk, and chunks may be rotated, so
 * an instance tile has exactly one template tile but a template tile may have several instance tiles.
 *
 * <p>The methods only read client state, with no client-thread hop; call them from the client thread, or accept that a
 * scene change at that very moment can give a stale answer (then the input comes back unchanged).
 */
public final class InstanceCoordinates
{
	private static final int CHUNK = Constants.CHUNK_SIZE;
	private static final int SCENE_CHUNKS = Constants.SCENE_SIZE / CHUNK;

	private InstanceCoordinates()
	{
	}

	/** Whether the world view is an instanced region (a template copy, not the real map). */
	public static boolean isInstance(@Nullable WorldView wv)
	{
		return wv != null && wv.getScene() != null && wv.getScene().isInstance();
	}

	/** Whether the world view is the top-level scene and an instanced region. */
	public static boolean isTopLevelInstance(@Nullable WorldView wv)
	{
		return wv != null && wv.isTopLevel() && isInstance(wv);
	}

	/**
	 * Pure: the template tile of an instance tile, from the scene's chunk table; null when the tile is outside the
	 * scene or its chunk is not part of the instance (the table holds -1 there).
	 */
	@Nullable
	public static WorldPoint templateOf(@Nullable int[][][] chunks, int baseX, int baseY, WorldPoint instancePoint)
	{
		if (chunks == null || instancePoint == null)
		{
			return null;
		}
		int sceneX = instancePoint.getX() - baseX;
		int sceneY = instancePoint.getY() - baseY;
		int plane = instancePoint.getPlane();
		int chunkX = sceneX / CHUNK;
		int chunkY = sceneY / CHUNK;
		if (sceneX < 0 || sceneY < 0 || chunkX >= SCENE_CHUNKS || chunkY >= SCENE_CHUNKS
			|| plane < 0 || plane >= chunks.length || chunkX >= chunks[plane].length || chunkY >= chunks[plane][chunkX].length)
		{
			return null;
		}
		int templateChunk = chunks[plane][chunkX][chunkY];
		if (templateChunk == -1)
		{
			return null;
		}
		int rotation = templateChunk >> 1 & 0x3;
		int templateChunkY = (templateChunk >> 3 & 0x7FF) * CHUNK;
		int templateChunkX = (templateChunk >> 14 & 0x3FF) * CHUNK;
		int templateChunkPlane = templateChunk >> 24 & 0x3;
		int x = templateChunkX + (sceneX & (CHUNK - 1));
		int y = templateChunkY + (sceneY & (CHUNK - 1));
		return rotate(x, y, templateChunkPlane, 4 - rotation);
	}

	private static WorldPoint rotate(int px, int py, int plane, int rotation)
	{
		int chunkX = px & ~(CHUNK - 1);
		int chunkY = py & ~(CHUNK - 1);
		int x = px & (CHUNK - 1);
		int y = py & (CHUNK - 1);
		switch (rotation & 3)
		{
			case 1:
				return new WorldPoint(chunkX + y, chunkY + (CHUNK - 1 - x), plane);
			case 2:
				return new WorldPoint(chunkX + (CHUNK - 1 - x), chunkY + (CHUNK - 1 - y), plane);
			case 3:
				return new WorldPoint(chunkX + (CHUNK - 1 - y), chunkY + x, plane);
			default:
				return new WorldPoint(px, py, plane);
		}
	}

	/** Instance tile to the template tile it was copied from; unchanged outside the top-level instance or when unknown. */
	public static WorldPoint toTemplate(@Nullable WorldView wv, WorldPoint instancePoint)
	{
		if (instancePoint == null || !isTopLevelInstance(wv))
		{
			return instancePoint;
		}
		WorldPoint template = templateOf(wv.getInstanceTemplateChunks(), wv.getBaseX(), wv.getBaseY(), instancePoint);
		return template != null ? template : instancePoint;
	}

	/**
	 * An area given by its south-west tile and size, in template space: both corners are converted and the box taken
	 * round them, because a rotated chunk swaps the area's axes. Null outside an instance or when a corner cannot be
	 * converted, so the caller keeps the original area.
	 */
	@Nullable
	public static WorldArea toTemplateArea(@Nullable WorldView wv, WorldPoint southWest, int width, int height)
	{
		if (southWest == null || !isTopLevelInstance(wv))
		{
			return null;
		}
		int[][][] chunks = wv.getInstanceTemplateChunks();
		WorldPoint a = templateOf(chunks, wv.getBaseX(), wv.getBaseY(), southWest);
		WorldPoint b = templateOf(chunks, wv.getBaseX(), wv.getBaseY(),
			new WorldPoint(southWest.getX() + Math.max(width, 1) - 1, southWest.getY() + Math.max(height, 1) - 1, southWest.getPlane()));
		if (a == null || b == null)
		{
			return null;
		}
		int minX = Math.min(a.getX(), b.getX());
		int minY = Math.min(a.getY(), b.getY());
		return new WorldArea(minX, minY, Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, a.getPlane());
	}

	/** Every instance tile that is a copy of the template tile (empty outside an instance or when none is loaded). */
	public static Collection<WorldPoint> toInstanceCopies(@Nullable WorldView wv, WorldPoint templatePoint)
	{
		if (templatePoint == null || !isTopLevelInstance(wv))
		{
			return java.util.Collections.emptyList();
		}
		return WorldPoint.toLocalInstance(wv, templatePoint);
	}

	/**
	 * Scene-local point of a template tile; when the template has several copies in the scene the one nearest
	 * {@code near} (a scene-local point such as the player's) is taken. Null when no copy is in the scene.
	 */
	@Nullable
	public static LocalPoint toLocalNear(@Nullable WorldView wv, WorldPoint templatePoint, @Nullable LocalPoint near)
	{
		// copies on the scene's own floor first; another floor only when there is none
		LocalPoint onFloor = nearest(wv, templatePoint, near, true);
		return onFloor != null ? onFloor : nearest(wv, templatePoint, near, false);
	}

	@Nullable
	private static LocalPoint nearest(WorldView wv, WorldPoint templatePoint, @Nullable LocalPoint near, boolean ownFloorOnly)
	{
		LocalPoint best = null;
		long bestDistance = Long.MAX_VALUE;
		for (WorldPoint copy : toInstanceCopies(wv, templatePoint))
		{
			if (ownFloorOnly && copy.getPlane() != wv.getPlane())
			{
				continue;
			}
			int sceneX = copy.getX() - wv.getBaseX();
			int sceneY = copy.getY() - wv.getBaseY();
			if (sceneX < 0 || sceneY < 0 || sceneX >= wv.getSizeX() || sceneY >= wv.getSizeY())
			{
				continue;
			}
			LocalPoint local = LocalPoint.fromScene(sceneX, sceneY, wv);
			long distance = near == null ? 0
				: Math.max(Math.abs(local.getSceneX() - near.getSceneX()), Math.abs(local.getSceneY() - near.getSceneY()));
			if (best == null || distance < bestDistance)
			{
				best = local;
				bestDistance = distance;
			}
		}
		return best;
	}
}
