package shortestpath;

import net.runelite.api.Client;
import static net.runelite.api.Constants.CHUNK_SIZE;
import static net.runelite.api.Perspective.LOCAL_COORD_BITS;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

/**
 * Utility functions for packing, unpacking, and transforming {@link WorldPoint}
 * coordinates as compact {@code int} values.
 * <p>
 * A packed world point encodes {@code x}, {@code y}, and {@code plane} into a
 * single 32-bit integer using:
 *
 * <pre>
 * bits 0..14   : x (15 bits)
 * bits 15..29  : y (15 bits)
 * bits 30..31  : plane (2 bits)
 * </pre>
 * <p>
 * This representation allows efficient storage and hashing of coordinates
 * within the pathfinding data structures.
 */
public class WorldPointUtil
{
	public static final int CHEBYSHEV_DISTANCE_METRIC = 1;
	public static final int EUCLIDEAN_SQUARED_DISTANCE_METRIC = 1414213562;
	public static final int MANHATTAN_DISTANCE_METRIC = 2;
	public static final int UNDEFINED = -1;

	/**
	 * Packs a {@link WorldPoint} into a compact {@code int} encoding.
	 *
	 * @param point world point (may be {@code null}).
	 * @return packed integer value, or {@link #UNDEFINED} if {@code point} is
	 * {@code null}.
	 */
	public static int packWorldPoint(WorldPoint point)
	{
		if (point == null)
		{
			return -1;
		}
		return packWorldPoint(point.getX(), point.getY(), point.getPlane());
	}

	/**
	 * Packs the provided coordinate triple into a single {@code int}.
	 * First 15 bits are {@code x}, next 15 bits are {@code y}, final 2 bits are the
	 * plane.
	 * Values are masked into range; overflow bits are discarded.
	 *
	 * @param x     world x (0..32767 effectively supported).
	 * @param y     world y (0..32767 effectively supported).
	 * @param plane plane (0..3).
	 * @return packed integer representation.
	 */
	public static int packWorldPoint(int x, int y, int plane)
	{
		return (x & 0x7FFF) | ((y & 0x7FFF) << 15) | ((plane & 0x3) << 30);
	}

	/**
	 * Unpacks a packed world point into a new {@link WorldPoint} instance.
	 *
	 * @param packedPoint packed coordinate.
	 * @return decoded {@link WorldPoint}.
	 */
	public static WorldPoint unpackWorldPoint(int packedPoint)
	{
		final int x = unpackWorldX(packedPoint);
		final int y = unpackWorldY(packedPoint);
		final int plane = unpackWorldPlane(packedPoint);
		return new WorldPoint(x, y, plane);
	}

	/**
	 * Extracts the x component from a packed world point.
	 *
	 * @param packedPoint packed coordinate.
	 * @return x value.
	 */
	public static int unpackWorldX(int packedPoint)
	{
		return packedPoint & 0x7FFF;
	}

	/**
	 * Extracts the y component from a packed world point.
	 *
	 * @param packedPoint packed coordinate.
	 * @return y value.
	 */
	public static int unpackWorldY(int packedPoint)
	{
		return (packedPoint >> 15) & 0x7FFF;
	}

	/**
	 * Extracts the plane component from a packed world point.
	 *
	 * @param packedPoint packed coordinate.
	 * @return plane value (0..3).
	 */
	public static int unpackWorldPlane(int packedPoint)
	{
		return (packedPoint >> 30) & 0x3;
	}

	/**
	 * Offsets a packed world point by {@code (dx, dy)} on the same plane.
	 *
	 * @param packedPoint base packed point.
	 * @param dx          delta x to add.
	 * @param dy          delta y to add.
	 * @return packed point after applying deltas.
	 */
	public static int dxdy(int packedPoint, int dx, int dy)
	{
		int x = unpackWorldX(packedPoint);
		int y = unpackWorldY(packedPoint);
		int z = unpackWorldPlane(packedPoint);
		return packWorldPoint(x + dx, y + dy, z);
	}

	/**
	 * Computes the distance between two packed points using Chebyshev metric
	 * (diagonal = 1).
	 */
	public static int distanceBetween(int previousPacked, int currentPacked)
	{
		return distanceBetween(previousPacked, currentPacked, 1);
	}

	/**
	 * Computes the 2D distance (ignoring plane) between two packed points using
	 * Chebyshev metric (diagonal = 1).
	 */
	public static int distanceBetween2D(int previousPacked, int currentPacked)
	{
		return distanceBetween2D(previousPacked, currentPacked, 1);
	}

	/**
	 * Computes distance between two packed points with selectable distance metric.
	 *
	 * @param previousPacked first packed point.
	 * @param currentPacked  second packed point.
	 * @param diagonal       {@code 1} for Chebyshev (max), {@code 2} for Manhattan
	 *                       (sum). Otherwise returns Euclidean squared distance.
	 * @return distance or {@code Integer.MAX_VALUE} if plane differs.
	 */
	public static int distanceBetween(int previousPacked, int currentPacked, int diagonal)
	{
		final int previousX = WorldPointUtil.unpackWorldX(previousPacked);
		final int previousY = WorldPointUtil.unpackWorldY(previousPacked);
		final int previousZ = WorldPointUtil.unpackWorldPlane(previousPacked);
		final int currentX = WorldPointUtil.unpackWorldX(currentPacked);
		final int currentY = WorldPointUtil.unpackWorldY(currentPacked);
		final int currentZ = WorldPointUtil.unpackWorldPlane(currentPacked);
		return distanceBetween(previousX, previousY, previousZ,
			currentX, currentY, currentZ, diagonal);
	}

	/**
	 * Computes 2D distance (ignoring plane) between two packed points with
	 * selectable metric.
	 *
	 * @see #distanceBetween(int, int, int, int, int, int, int)
	 */
	public static int distanceBetween2D(int previousPacked, int currentPacked, int diagonal)
	{
		final int previousX = WorldPointUtil.unpackWorldX(previousPacked);
		final int previousY = WorldPointUtil.unpackWorldY(previousPacked);
		final int currentX = WorldPointUtil.unpackWorldX(currentPacked);
		final int currentY = WorldPointUtil.unpackWorldY(currentPacked);
		return distanceBetween2D(previousX, previousY, currentX, currentY, diagonal);
	}

	/**
	 * Computes distance between two coordinates with selectable metric; returns
	 * {@code Integer.MAX_VALUE} if planes differ.
	 *
	 * @param previousX x of first point.
	 * @param previousY y of first point.
	 * @param previousZ plane of first point.
	 * @param currentX  x of second point.
	 * @param currentY  y of second point.
	 * @param currentZ  plane of second point.
	 * @param diagonal  metric selector ({@code 1}=Chebyshev, {@code 2}=Manhattan,
	 *                  otherwise Euclidean squared distance).
	 * @return distance or {@code Integer.MAX_VALUE} if planes differ.
	 */
	public static int distanceBetween(
		int previousX,
		int previousY,
		int previousZ,
		int currentX,
		int currentY,
		int currentZ,
		int diagonal)
	{
		final int dz = previousZ - currentZ;

		if (dz != 0)
		{
			return Integer.MAX_VALUE;
		}

		return distanceBetween2D(previousX, previousY, currentX, currentY, diagonal);
	}

	/**
	 * Computes a 2D distance using either Chebyshev, Manhattan or Euclidean squared
	 * distance metric.
	 *
	 * @param previousX x of first point.
	 * @param previousY y of first point.
	 * @param currentX  x of second point.
	 * @param currentY  y of second point.
	 * @param diagonal  metric selector ({@code 1}=Chebyshev, {@code 2}=Manhattan,
	 *                  otherwise Euclidean squared distance).
	 * @return distance.
	 */
	public static int distanceBetween2D(int previousX, int previousY,
		int currentX, int currentY, int diagonal)
	{
		final int dx = previousX - currentX;
		final int dy = previousY - currentY;

		if (diagonal == CHEBYSHEV_DISTANCE_METRIC)
		{
			return Math.max(Math.abs(dx), Math.abs(dy));
		}
		else if (diagonal == MANHATTAN_DISTANCE_METRIC)
		{
			return Math.abs(dx) + Math.abs(dy);
		}

		return dx * dx + dy * dy;
	}

	/**
	 * Convenience overload using Chebyshev distance between two
	 * {@link WorldPoint}s.
	 */
	public static int distanceBetween(WorldPoint previous, WorldPoint current)
	{
		return distanceBetween(previous, current, 1);
	}

	/**
	 * Distance between two {@link WorldPoint}s with selectable metric.
	 *
	 * @see #distanceBetween(int, int, int, int, int, int, int)
	 */
	public static int distanceBetween(WorldPoint previous, WorldPoint current, int diagonal)
	{
		return distanceBetween(previous.getX(), previous.getY(), previous.getPlane(),
			current.getX(), current.getY(), current.getPlane(), diagonal);
	}

	/**
	 * Distance from a packed point to a {@link WorldArea} using Chebyshev metric,
	 * respecting plane.
	 * Returns {@code Integer.MAX_VALUE} if the plane differs.
	 */
	public static int distanceToArea(int packedPoint, WorldArea area)
	{
		final int plane = unpackWorldPlane(packedPoint);
		if (area.getPlane() != plane)
		{
			return Integer.MAX_VALUE;
		}
		return distanceToArea2D(packedPoint, area);
	}

	/**
	 * 2D distance (Chebyshev) from a packed point to a {@link WorldArea} ignoring
	 * plane, equivalent to
	 * {@link WorldArea#distanceTo(WorldPoint)} semantics in 2D.
	 */
	public static int distanceToArea2D(int packedPoint, WorldArea area)
	{
		final int y = unpackWorldY(packedPoint);
		final int x = unpackWorldX(packedPoint);
		final int areaMaxX = area.getX() + area.getWidth() - 1;
		final int areaMaxY = area.getY() + area.getHeight() - 1;
		final int dx = Math.max(Math.max(area.getX() - x, 0), x - areaMaxX);
		final int dy = Math.max(Math.max(area.getY() - y, 0), y - areaMaxY);

		return Math.max(dx, dy);
	}

	private static int rotate(int originalX, int originalY, int z, int rotation)
	{
		int chunkX = originalX & -CHUNK_SIZE;
		int chunkY = originalY & -CHUNK_SIZE;
		int x = originalX & (CHUNK_SIZE - 1);
		int y = originalY & (CHUNK_SIZE - 1);
		switch (rotation)
		{
			case 1:
				return packWorldPoint(chunkX + y, chunkY + (CHUNK_SIZE - 1 - x), z);
			case 2:
				return packWorldPoint(chunkX + (CHUNK_SIZE - 1 - x), chunkY + (CHUNK_SIZE - 1 - y), z);
			case 3:
				return packWorldPoint(chunkX + (CHUNK_SIZE - 1 - y), chunkY + x, z);
		}
		return packWorldPoint(originalX, originalY, z);
	}

	private static int unpackChunkRotation(int chunkData)
	{
		return chunkData >> 1 & 0x3;
	}

	private static int unpackChunkTemplateY(int chunkData)
	{
		return (chunkData >> 3 & 0x7FF) * CHUNK_SIZE;
	}

	private static int unpackChunkTemplateX(int chunkData)
	{
		return (chunkData >> 14 & 0x3FF) * CHUNK_SIZE;
	}

	private static int unpackChunkTemplatePlane(int chunkData)
	{
		return chunkData >> 24 & 0x3;
	}

	public static int fromLocalInstance(Client client, Player localPlayer)
	{
		WorldView worldView = localPlayer.getWorldView();
		int worldViewId = worldView.getId();
		boolean isOnBoat = worldViewId != WorldView.TOPLEVEL;
		if (isOnBoat)
		{
			WorldEntity worldEntity = client.getTopLevelWorldView().worldEntities().byIndex(worldViewId);
			return fromLocalInstance(client, worldEntity.getLocalLocation());
		}
		return fromLocalInstance(client, localPlayer.getLocalLocation());
	}

	/**
	 * Converts an instanced {@link LocalPoint} to its corresponding packed world
	 * point coordinate, resolving the
	 * underlying template chunk mapping and rotation.
	 *
	 * @param client     RuneLite client.
	 * @param localPoint local scene coordinate.
	 * @return packed world point.
	 */
	public static int fromLocalInstance(Client client, LocalPoint localPoint)
	{
		WorldView worldView = client.getWorldView(localPoint.getWorldView());
		int plane = worldView.getPlane();

		if (!worldView.isInstance())
		{
			return packWorldPoint(
				(localPoint.getX() >> LOCAL_COORD_BITS) + worldView.getBaseX(),
				(localPoint.getY() >> LOCAL_COORD_BITS) + worldView.getBaseY(),
				plane);
		}

		int[][][] instanceTemplateChunks = worldView.getInstanceTemplateChunks();

		// get position in the scene
		int sceneX = localPoint.getSceneX();
		int sceneY = localPoint.getSceneY();

		// get chunk from scene
		int chunkX = sceneX / CHUNK_SIZE;
		int chunkY = sceneY / CHUNK_SIZE;

		// get the template chunk for the chunk
		int templateChunk = instanceTemplateChunks[plane][chunkX][chunkY];

		int rotation = unpackChunkRotation(templateChunk);
		int templateChunkY = unpackChunkTemplateY(templateChunk);
		int templateChunkX = unpackChunkTemplateX(templateChunk);
		int templateChunkPlane = unpackChunkTemplatePlane(templateChunk);

		// calculate world point of the template
		int x = templateChunkX + (sceneX & (CHUNK_SIZE - 1));
		int y = templateChunkY + (sceneY & (CHUNK_SIZE - 1));

		// create and rotate point back to 0, to match with template
		return rotate(x, y, templateChunkPlane, 4 - rotation);
	}

	/**
	 * Converts a packed world point to one or more packed world points representing
	 * its locations within an instanced
	 * map (e.g., dungeons or raids). If the current top-level world is not
	 * instanced, the result contains exactly the
	 * original point.
	 *
	 * @param client      RuneLite client.
	 * @param packedPoint packed world coordinate.
	 * @return list of packed coordinates valid in the current instance.
	 */
	public static PrimitiveIntList toLocalInstance(Client client, int packedPoint)
	{
		WorldView worldView = client.getTopLevelWorldView();

		PrimitiveIntList worldPoints = new PrimitiveIntList();
		if (!worldView.isInstance())
		{
			worldPoints.add(packedPoint);
			return worldPoints;
		}

		int baseX = worldView.getBaseX();
		int baseY = worldView.getBaseY();
		int worldPointX = unpackWorldX(packedPoint);
		int worldPointY = unpackWorldY(packedPoint);
		int worldPointPlane = unpackWorldPlane(packedPoint);

		int[][][] instanceTemplateChunks = worldView.getInstanceTemplateChunks();

		// find instance chunks using the template point. there might be more than one.
		for (int z = 0; z < instanceTemplateChunks.length; z++)
		{
			for (int x = 0; x < instanceTemplateChunks[z].length; ++x)
			{
				for (int y = 0; y < instanceTemplateChunks[z][x].length; ++y)
				{
					int chunkData = instanceTemplateChunks[z][x][y];
					int rotation = unpackChunkRotation(chunkData);
					int templateChunkY = unpackChunkTemplateY(chunkData);
					int templateChunkX = unpackChunkTemplateX(chunkData);
					int plane = unpackChunkTemplatePlane(chunkData);
					if (worldPointX >= templateChunkX && worldPointX < templateChunkX + CHUNK_SIZE
						&& worldPointY >= templateChunkY && worldPointY < templateChunkY + CHUNK_SIZE
						&& plane == worldPointPlane)
					{
						worldPoints.add(rotate(
							baseX + x * CHUNK_SIZE + (worldPointX & (CHUNK_SIZE - 1)),
							baseY + y * CHUNK_SIZE + (worldPointY & (CHUNK_SIZE - 1)),
							z,
							rotation));
					}
				}
			}
		}
		return worldPoints;
	}

	private static boolean isInScene(WorldView worldView, int packedPoint)
	{
		int x = unpackWorldX(packedPoint);
		int y = unpackWorldY(packedPoint);

		int baseX = worldView.getBaseX();
		int baseY = worldView.getBaseY();

		int maxX = baseX + worldView.getSizeX();
		int maxY = baseY + worldView.getSizeY();

		return x >= baseX && x < maxX && y >= baseY && y < maxY;
	}

	/**
	 * Converts a packed world point into a {@link LocalPoint} relative to the
	 * top-level world view if it resides in
	 * the currently loaded scene and on the same plane; returns {@code null}
	 * otherwise.
	 *
	 * @param client      RuneLite client.
	 * @param packedPoint packed world point.
	 * @return {@link LocalPoint} or {@code null} if out of scene or plane.
	 */
	public static LocalPoint toLocalPoint(Client client, int packedPoint)
	{
		WorldView worldView = client.getTopLevelWorldView();

		if (worldView.getPlane() != unpackWorldPlane(packedPoint))
		{
			return null;
		}

		if (!isInScene(worldView, packedPoint))
		{
			return null;
		}

		return new LocalPoint(
			(unpackWorldX(packedPoint) - worldView.getBaseX() << LOCAL_COORD_BITS) + (1 << LOCAL_COORD_BITS - 1),
			(unpackWorldY(packedPoint) - worldView.getBaseY() << LOCAL_COORD_BITS) + (1 << LOCAL_COORD_BITS - 1),
			worldView.getId());
	}
}
