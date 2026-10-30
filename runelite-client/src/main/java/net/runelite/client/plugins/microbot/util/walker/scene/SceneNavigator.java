package net.runelite.client.plugins.microbot.util.walker.scene;

import net.runelite.api.CollisionDataFlag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pure path search over one floor of the loaded scene's collision flags ({@code flags[sceneX][sceneY]}). It knows
 * nothing about the client, so it can be tested with hand-made grids. The global pathfinder has no collision data for
 * instanced regions (a quest cave is a copy of map squares it never saw); the live flags are the only truth there.
 */
public final class SceneNavigator
{
	public static final int SIZE = 104;
	/** Tiles an obstacle may be used from: its own tile and the ring around it. */
	private static final int RING = 1;
	private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

	private SceneNavigator()
	{
	}

	/** An object the player can pass by using it (Climb-over, Squeeze-through, ...), as the scene tiles it covers. */
	public static final class Obstacle
	{
		public final Object ref;
		public final int minX;
		public final int minY;
		public final int maxX;
		public final int maxY;

		public Obstacle(Object ref, int minX, int minY, int maxX, int maxY)
		{
			this.ref = ref;
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
		}
	}

	/** An obstacle that joins the player's side to the goal's side, with a tile to stand on each side of it. */
	public static final class Crossing
	{
		public final Obstacle obstacle;
		/** Tile on the player's side next to the obstacle. */
		public final int[] near;
		/** Tile on the far side, from which the goal is reachable. */
		public final int[] far;

		Crossing(Obstacle obstacle, int[] near, int[] far)
		{
			this.obstacle = obstacle;
			this.near = near;
			this.far = far;
		}
	}

	private static boolean inBounds(int x, int y)
	{
		return x >= 0 && y >= 0 && x < SIZE && y < SIZE;
	}

	/** Whether a player can stand on the tile. */
	public static boolean standable(int[][] flags, int x, int y)
	{
		return inBounds(x, y) && (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
	}

	/** Whether one step of (dx, dy) from (x, y) is allowed (diagonals need both sides open, as in the game). */
	public static boolean canStep(int[][] flags, int x, int y, int dx, int dy)
	{
		int nx = x + dx;
		int ny = y + dy;
		if (!standable(flags, x, y) || !standable(flags, nx, ny))
		{
			return false;
		}
		if (dx != 0 && dy != 0)
		{
			return canStep(flags, x, y, dx, 0) && canStep(flags, x, y, 0, dy)
				&& canStep(flags, x + dx, y, 0, dy) && canStep(flags, x, y + dy, dx, 0);
		}
		int out = dx == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST : dx == -1 ? CollisionDataFlag.BLOCK_MOVEMENT_WEST
			: dy == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_NORTH : CollisionDataFlag.BLOCK_MOVEMENT_SOUTH;
		int in = dx == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_WEST : dx == -1 ? CollisionDataFlag.BLOCK_MOVEMENT_EAST
			: dy == 1 ? CollisionDataFlag.BLOCK_MOVEMENT_SOUTH : CollisionDataFlag.BLOCK_MOVEMENT_NORTH;
		return (flags[x][y] & out) == 0 && (flags[nx][ny] & in) == 0;
	}

	/** Every tile reachable on foot from any of the start tiles. */
	public static boolean[][] reachable(int[][] flags, List<int[]> starts)
	{
		boolean[][] seen = new boolean[SIZE][SIZE];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		for (int[] start : starts)
		{
			if (inBounds(start[0], start[1]) && !seen[start[0]][start[1]])
			{
				seen[start[0]][start[1]] = true;
				queue.add(start);
			}
		}
		while (!queue.isEmpty())
		{
			int[] at = queue.poll();
			for (int[] step : STEPS)
			{
				int nx = at[0] + step[0];
				int ny = at[1] + step[1];
				if (inBounds(nx, ny) && !seen[nx][ny] && canStep(flags, at[0], at[1], step[0], step[1]))
				{
					seen[nx][ny] = true;
					queue.add(new int[]{nx, ny});
				}
			}
		}
		return seen;
	}

	public static boolean[][] reachable(int[][] flags, int x, int y)
	{
		return reachable(flags, Collections.singletonList(new int[]{x, y}));
	}

	/**
	 * The shortest walk from (sx, sy) to the nearest goal tile, as the tiles to step on (the start excluded), empty when
	 * already standing on a goal, or null when no goal can be walked to.
	 */
	public static List<int[]> path(int[][] flags, int sx, int sy, boolean[][] goal)
	{
		if (!inBounds(sx, sy))
		{
			return null;
		}
		if (goal[sx][sy])
		{
			return new ArrayList<>();
		}
		int[][] from = new int[SIZE][SIZE];
		for (int[] row : from)
		{
			Arrays.fill(row, -1);
		}
		from[sx][sy] = sx * SIZE + sy;
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[]{sx, sy});
		while (!queue.isEmpty())
		{
			int[] at = queue.poll();
			for (int[] step : STEPS)
			{
				int nx = at[0] + step[0];
				int ny = at[1] + step[1];
				if (!inBounds(nx, ny) || from[nx][ny] != -1 || !canStep(flags, at[0], at[1], step[0], step[1]))
				{
					continue;
				}
				from[nx][ny] = at[0] * SIZE + at[1];
				if (goal[nx][ny])
				{
					List<int[]> out = new ArrayList<>();
					int cx = nx;
					int cy = ny;
					while (cx != sx || cy != sy)
					{
						out.add(0, new int[]{cx, cy});
						int prev = from[cx][cy];
						cx = prev / SIZE;
						cy = prev % SIZE;
					}
					return out;
				}
				queue.add(new int[]{nx, ny});
			}
		}
		return null;
	}

	/** Standable tiles within {@code radius} (Chebyshev) of the target tile. */
	public static boolean[][] goalTiles(int[][] flags, int tx, int ty, int radius)
	{
		boolean[][] goal = new boolean[SIZE][SIZE];
		for (int x = tx - radius; x <= tx + radius; x++)
		{
			for (int y = ty - radius; y <= ty + radius; y++)
			{
				if (standable(flags, x, y))
				{
					goal[x][y] = true;
				}
			}
		}
		return goal;
	}

	private static List<int[]> ring(int[][] flags, Obstacle o)
	{
		List<int[]> tiles = new ArrayList<>();
		for (int x = o.minX - RING; x <= o.maxX + RING; x++)
		{
			for (int y = o.minY - RING; y <= o.maxY + RING; y++)
			{
				if (standable(flags, x, y))
				{
					tiles.add(new int[]{x, y});
				}
			}
		}
		return tiles;
	}

	/**
	 * The obstacle that joins the player's side to the goal's: one with a standable tile around it that the player can
	 * walk to now, and another around it from which a goal can be walked to but the player cannot reach. The one nearest
	 * the player wins. Null when none connects the two, or when a goal is reachable without any.
	 */
	public static Crossing findCrossing(int[][] flags, int px, int py, boolean[][] goal, List<Obstacle> obstacles)
	{
		boolean[][] mine = reachable(flags, px, py);
		List<int[]> goalTiles = new ArrayList<>();
		for (int x = 0; x < SIZE; x++)
		{
			for (int y = 0; y < SIZE; y++)
			{
				if (goal[x][y])
				{
					if (mine[x][y])
					{
						return null;
					}
					goalTiles.add(new int[]{x, y});
				}
			}
		}
		boolean[][] theirs = reachable(flags, goalTiles);
		Crossing best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Obstacle obstacle : obstacles)
		{
			int[] near = null;
			int[] far = null;
			int nearDistance = Integer.MAX_VALUE;
			for (int[] tile : ring(flags, obstacle))
			{
				int distance = Math.max(Math.abs(tile[0] - px), Math.abs(tile[1] - py));
				if (mine[tile[0]][tile[1]])
				{
					if (distance < nearDistance)
					{
						near = tile;
						nearDistance = distance;
					}
				}
				else if (theirs[tile[0]][tile[1]] && far == null)
				{
					far = tile;
				}
			}
			if (near != null && far != null && nearDistance < bestDistance)
			{
				best = new Crossing(obstacle, near, far);
				bestDistance = nearDistance;
			}
		}
		return best;
	}

	/** Whether the player is on the goal's side: a goal tile can be walked to from where they stand. */
	public static boolean onGoalSide(int[][] flags, int px, int py, boolean[][] goal)
	{
		return path(flags, px, py, goal) != null;
	}
}
