package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Pins that the STATIC collision map still routes into the Clock Tower ground floor. */
public class ClockTowerEdgeProbeTest {
	@Test
	public void staticMapRoutesIntoTower() {
		CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
		int sx = 2570, sy = 3234, tx = 2571, ty = 3240, plane = 0;

		Map<Long, Long> prev = new HashMap<>();
		ArrayDeque<long[]> queue = new ArrayDeque<>();
		queue.add(new long[]{sx, sy});
		prev.put(key(sx, sy), key(sx, sy));
		int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

		while (!queue.isEmpty()) {
			long[] cur = queue.poll();
			int x = (int) cur[0], y = (int) cur[1];
			if (x == tx && y == ty) break;
			for (int[] d : dirs) {
				int nx = x + d[0], ny = y + d[1];
				if (Math.abs(nx - sx) > 25 || Math.abs(ny - sy) > 25) continue;
				if (prev.containsKey(key(nx, ny))) continue;
				if (!map.canStep(x, y, plane, d[0], d[1])) continue;
				prev.put(key(nx, ny), key(x, y));
				queue.add(new long[]{nx, ny});
			}
		}

		assertTrue("static collision map must reach the Clock Tower ground floor from "
				+ sx + "," + sy + " — a regression here silently reroutes every walk into the tower",
				prev.containsKey(key(tx, ty)));

		// Walk the predecessor chain back to the start: it must terminate, stay inside the probe
		// window, and every step must be one the collision map actually permits.
		long k = key(tx, ty);
		int steps = 0;
		while (k != key(sx, sy)) {
			Long p = prev.get(k);
			assertNotNull("predecessor chain broke at (" + (k >> 16) + "," + (k & 0xffff) + ")", p);
			int px = (int) (p >> 16), py = (int) (p & 0xffff);
			int cx = (int) (k >> 16), cy = (int) (k & 0xffff);
			assertTrue("route step (" + px + "," + py + ") -> (" + cx + "," + cy + ") is not walkable",
					map.canStep(px, py, plane, cx - px, cy - py));
			k = p;
			assertTrue("predecessor chain did not terminate within the probe window", ++steps <= 200);
		}
		assertTrue("route into the tower must take at least one step", steps > 0);
	}

	private static long key(long x, long y) {
		return (x << 16) | y;
	}
}
