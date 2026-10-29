package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PathJitterTest
{
	private static final int N = 60;

	/** 8-connected Dijkstra on an open grid with a wall (gap at y=30) using jittered costs; returns the path. */
	private static List<int[]> route(int seed, int level)
	{
		int[][] dist = new int[N][N];
		int[][] prev = new int[N][N];
		for (int[] r : dist) Arrays.fill(r, Integer.MAX_VALUE);
		PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[2] - b[2]);
		dist[2][2] = 0;
		pq.add(new int[]{2, 2, 0});
		while (!pq.isEmpty())
		{
			int[] c = pq.poll();
			if (c[2] > dist[c[0]][c[1]]) continue;
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++)
				{
					int x = c[0] + dx, y = c[1] + dy;
					if ((dx == 0 && dy == 0) || x < 0 || y < 0 || x >= N || y >= N) continue;
					if (x == 30 && y != 30) continue; // wall
					boolean target = x == N - 3 && y == N - 3;
					int cost = c[2] + 1 + (target ? 0 : PathJitter.penalty(x * 10000 + y, seed, level));
					if (cost < dist[x][y])
					{
						dist[x][y] = cost;
						prev[x][y] = c[0] * 1000 + c[1];
						pq.add(new int[]{x, y, cost});
					}
				}
		}
		List<int[]> path = new ArrayList<>();
		int x = N - 3, y = N - 3;
		while (!(x == 2 && y == 2))
		{
			path.add(new int[]{x, y});
			int p = prev[x][y];
			x = p / 1000;
			y = p % 1000;
		}
		path.add(new int[]{2, 2});
		return path;
	}

	@Test
	public void routesDifferAcrossSeedsAndStayNearOptimal()
	{
		int base = route(0, 0).size();
		for (int level = 1; level <= 3; level++)
		{
			Set<String> distinct = new HashSet<>();
			for (int seed = 1; seed <= 40; seed++)
			{
				List<int[]> p = route(seed * 7919, level);
				StringBuilder sb = new StringBuilder();
				for (int[] t : p) sb.append(t[0]).append(',').append(t[1]).append(';');
				distinct.add(sb.toString());
				for (int[] t : p)
				{
					assertTrue("never through the wall", !(t[0] == 30 && t[1] != 30));
				}
				assertTrue("level " + level + " seed " + seed + " len " + p.size() + " base " + base,
					p.size() <= base * 1.2);
			}
			assertTrue("level " + level + " produced " + distinct.size() + " distinct routes", distinct.size() > 3);
		}
	}

	@Test
	public void penaltyIsDeterministicNonNegativeAndOffWhenDisabled()
	{
		for (int t = 0; t < 1000; t++)
		{
			assertEquals(PathJitter.penalty(t, 5, 2), PathJitter.penalty(t, 5, 2));
			assertTrue(PathJitter.penalty(t, 5, 3) >= 0 && PathJitter.penalty(t, 5, 3) <= 3);
			assertEquals(0, PathJitter.penalty(t, 5, 0));
		}
	}

	@Test
	public void sameWalkKeepsSeedNewWalkChangesIt()
	{
		PathJitter.configure(true, 2);
		try
		{
			PathJitter.beginWalk("a", 1000);
			int[] first = sample();
			PathJitter.beginWalk("a", 2000);
			assertTrue(Arrays.equals(first, sample()));
			PathJitter.beginWalk("a", 1000 + 200_000);
			assertTrue(!Arrays.equals(first, sample()));
		}
		finally
		{
			PathJitter.configure(false, 2);
		}
	}

	@Test
	public void wallCrossingsAreTheStepsTheShippedMapDoesNotAllow()
	{
		CollisionMap map = new CollisionMap(SplitFlagMap.fromResources());
		// Ardougne timber house, plane 1: a plain wall between these two tiles
		assertEquals(1, PathJitter.wallCrossings(java.util.Arrays.asList(
			new net.runelite.api.coords.WorldPoint(2617, 3294, 1),
			new net.runelite.api.coords.WorldPoint(2617, 3295, 1)), map).size());
		// open ground
		assertEquals(0, PathJitter.wallCrossings(java.util.Arrays.asList(
			new net.runelite.api.coords.WorldPoint(2617, 3290, 0),
			new net.runelite.api.coords.WorldPoint(2618, 3290, 0)), map).size());
		// the same edge walked the other way is the same crossing
		assertEquals(PathJitter.wallCrossings(java.util.Arrays.asList(
				new net.runelite.api.coords.WorldPoint(2617, 3294, 1),
				new net.runelite.api.coords.WorldPoint(2617, 3295, 1)), map),
			PathJitter.wallCrossings(java.util.Arrays.asList(
				new net.runelite.api.coords.WorldPoint(2617, 3295, 1),
				new net.runelite.api.coords.WorldPoint(2617, 3294, 1)), map));
	}

	@Test
	public void boundHistoryAndSuspension()
	{
		assertTrue(PathJitter.withinBound(110, 100));
		assertTrue(!PathJitter.withinBound(111, 100));
		assertEquals(0, PathJitter.routeLength(new ArrayList<>()));
		java.util.List<net.runelite.api.coords.WorldPoint> p = new ArrayList<>();
		p.add(new net.runelite.api.coords.WorldPoint(3200, 3200, 0));
		p.add(new net.runelite.api.coords.WorldPoint(3201, 3201, 0));
		p.add(new net.runelite.api.coords.WorldPoint(3300, 3300, 0)); // teleport jump is free
		p.add(new net.runelite.api.coords.WorldPoint(3300, 3302, 0));
		assertEquals(3, PathJitter.routeLength(p));

		Object key = "step-history";
		int h = PathJitter.routeHash(p);
		assertTrue(!PathJitter.seenRoute(key, h));
		PathJitter.recordRoute(key, h);
		assertTrue(PathJitter.seenRoute(key, h));
		for (int i = 1; i <= 3; i++) PathJitter.recordRoute(key, h + i);
		assertTrue("history is short", !PathJitter.seenRoute(key, h));

		PathJitter.configure(true, 3);
		try
		{
			boolean nonZero = false;
			for (int i = 0; i < 200; i++)
			{
				int tp = PathJitter.transportPenalty(i * 977);
				int gp = PathJitter.targetPenalty(i * 977);
				assertTrue(tp >= 0 && tp <= 4 && gp >= 0 && gp <= 7);
				nonZero |= tp > 0;
			}
			assertTrue(nonZero);
			PathJitter.withoutJitter(() ->
			{
				assertTrue(!PathJitter.isActive());
				assertEquals(0, PathJitter.transportPenalty(5));
				assertEquals(0, PathJitter.targetPenalty(5));
			});
			assertTrue(PathJitter.isActive());
		}
		finally
		{
			PathJitter.configure(false, 2);
		}
	}

	private static int[] sample()
	{
		int[] s = new int[400];
		for (int i = 0; i < s.length; i++) s[i] = PathJitter.penalty(i * 31);
		return s;
	}
}
