/*
 * Copyright (c) 2025, Microbot
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.microbot.util.walker.puzzles;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayDeque;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Solves the Draynor Manor (Ernest the Chicken) basement lever puzzle during a web-walk so a
 * plain {@code Rs2Walker.walkTo(basementTile)} / "Start path" routes to any basement room.
 *
 * <p>The 9 puzzle doors are registered as varbit-conditional transports (shortestpath
 * transports.tsv), so once the levers unlock a door the stock walker crosses + opens it. This
 * solver supplies the missing half: it sets the levers. Given the target tile's room, it does a
 * BFS over (lever-combo x room) — actions are "pull a lever in the current room" and "cross an
 * unlocked door" — to find the lever-pull sequence, then walks to each lever and pulls it. The
 * walker (via the transports) handles the door crossings.
 *
 * <p>Hook: call {@link #solveIfNeeded(WorldPoint)} at the top of the walk loop. It is a no-op
 * unless the target and the player are both inside the basement.
 */
@Slf4j
public final class DraynorBasementSolver
{
	private DraynorBasementSolver() {}

	private static volatile boolean active = false;

	// Room indices.
	private static final int ENTRANCE = 0, CD = 1, SS = 2, NS = 3, SW = 4, EF = 5, OIL = 6;

	// Room bounding boxes: {x1, y1, x2, y2} (plane 0), indexed by room.
	private static final int[][] ROOM_BOX = {
		{3100, 9745, 3118, 9757}, // ENTRANCE
		{3105, 9758, 3112, 9767}, // C/D
		{3100, 9758, 3104, 9762}, // S-small
		{3100, 9763, 3104, 9767}, // N-small
		{3096, 9758, 3099, 9762}, // SW-small
		{3096, 9763, 3099, 9767}, // E/F
		{3090, 9753, 3099, 9757}, // Oil
	};

	// Levers A..F (bit index = position): varbit, object id, room, world tile.
	private static final int[] LEVER_VARBIT = {
		VarbitID.ERNESTLEVER_A, VarbitID.ERNESTLEVER_B, VarbitID.ERNESTLEVER_C,
		VarbitID.ERNESTLEVER_D, VarbitID.ERNESTLEVER_E, VarbitID.ERNESTLEVER_F};
	private static final int[] LEVER_OBJ = {
		ObjectID.LEVERA, ObjectID.LEVERB, ObjectID.LEVERC,
		ObjectID.LEVERD, ObjectID.LEVERE, ObjectID.LEVERF};
	private static final int[] LEVER_ROOM = {ENTRANCE, ENTRANCE, CD, CD, EF, EF};
	private static final WorldPoint[] LEVER_TILE = {
		new WorldPoint(3108, 9745, 0), new WorldPoint(3118, 9752, 0), new WorldPoint(3112, 9760, 0),
		new WorldPoint(3108, 9767, 0), new WorldPoint(3097, 9767, 0), new WorldPoint(3096, 9765, 0)};

	// A walkable interior tile per room (for executing a door crossing), indexed by room.
	private static final WorldPoint[] ROOM_TILE = {
		new WorldPoint(3115, 9752, 0), new WorldPoint(3110, 9762, 0), new WorldPoint(3102, 9760, 0),
		new WorldPoint(3102, 9765, 0), new WorldPoint(3098, 9760, 0), new WorldPoint(3097, 9766, 0),
		new WorldPoint(3093, 9755, 0)};

	// Doors: {roomA, roomB, mask, requiredValue}. unlocked == (combo & mask) == requiredValue.
	// A combo bit is set when that lever is DOWN (varbit == 1). Bits: A=0 B=1 C=2 D=3 E=4 F=5.
	private static final int[][] DOORS = {
		{ENTRANCE, CD, 0b100111, 0b000011}, // d1 (obj 144): A=D,B=D,C=U,F=U
		{CD, SS, 0b110101, 0b000001},        // d2 (139): A=D,C=U,E=U,F=U
		{ENTRANCE, SS, 0b011000, 0b001000},  // d3 (145): D=D,E=U
		{SS, SW, 0b011000, 0b001000},        // d4 (140): D=D,E=U
		{EF, SW, 0b101010, 0b001000},        // d5 (143): B=U,D=D,F=U
		{EF, NS, 0b101010, 0b101000},        // d6 (138): B=U,D=D,F=D
		{CD, NS, 0b110001, 0b110000},        // d7 (137): A=U,E=D,F=D
		{SS, NS, 0b110000, 0b100000},        // d8 (142): E=U,F=D
		{ENTRANCE, OIL, 0b101100, 0b101100}, // d9 (141): C=D,D=D,F=D
	};

	private static final int LEVER_PULL_TIMEOUT_MS = 4000;

	public static boolean isBasementTarget(WorldPoint p)
	{
		return p != null && p.getPlane() == 0
			&& p.getX() >= 3088 && p.getX() <= 3120
			&& p.getY() >= 9744 && p.getY() <= 9768;
	}

	private static int roomOf(WorldPoint p)
	{
		if (p == null)
		{
			return -1;
		}
		for (int r = 0; r < ROOM_BOX.length; r++)
		{
			int[] b = ROOM_BOX[r];
			if (p.getX() >= b[0] && p.getX() <= b[2] && p.getY() >= b[1] && p.getY() <= b[3])
			{
				return r;
			}
		}
		return -1;
	}

	private static int readCombo()
	{
		int combo = 0;
		for (int i = 0; i < 6; i++)
		{
			if (Microbot.getVarbitValue(LEVER_VARBIT[i]) == 1)
			{
				combo |= (1 << i);
			}
		}
		return combo;
	}

	/**
	 * BFS over (combo, room); returns the FIRST action on a shortest path to targetRoom, as
	 * {@code {0, leverIndex}} (pull that lever — we're in its room) or {@code {1, roomIndex}}
	 * (cross into that adjacent room). Null if already there / unreachable. Re-running this after
	 * every executed step (rather than committing to a full plan) is what keeps it correct when a
	 * lever pull re-locks doors.
	 */
	private static int[] firstAction(int startCombo, int startRoom, int targetRoom)
	{
		if (startRoom < 0 || targetRoom < 0 || startRoom == targetRoom)
		{
			return null;
		}
		final int N = 64 * 7;
		int[][] first = new int[N][];
		boolean[] seen = new boolean[N];
		int start = startCombo * 7 + startRoom;
		seen[start] = true;
		ArrayDeque<Integer> q = new ArrayDeque<>();
		q.add(start);
		while (!q.isEmpty())
		{
			int s = q.poll();
			int combo = s / 7, room = s % 7;
			if (room == targetRoom)
			{
				return first[s];
			}
			for (int li = 0; li < 6; li++)
			{
				if (LEVER_ROOM[li] == room)
				{
					int ns = (combo ^ (1 << li)) * 7 + room;
					if (!seen[ns])
					{
						seen[ns] = true;
						first[ns] = (s == start) ? new int[]{0, li} : first[s];
						q.add(ns);
					}
				}
			}
			for (int[] d : DOORS)
			{
				if ((room == d[0] || room == d[1]) && (combo & d[2]) == d[3])
				{
					int other = room == d[0] ? d[1] : d[0];
					int ns = combo * 7 + other;
					if (!seen[ns])
					{
						seen[ns] = true;
						first[ns] = (s == start) ? new int[]{1, other} : first[s];
						q.add(ns);
					}
				}
			}
		}
		return null;
	}

	/**
	 * If {@code target} is inside the Draynor basement and the player is too, set the levers so the
	 * target's room is reachable. Blocking; safe to call every walk iteration (re-entrant guard).
	 */
	public static void solveIfNeeded(WorldPoint target)
	{
		if (active || !isBasementTarget(target) || !Microbot.isLoggedIn())
		{
			return;
		}
		WorldPoint me = Rs2Player.getWorldLocation();
		if (!isBasementTarget(me))
		{
			return; // not in the basement yet; let the walk descend first
		}
		int targetRoom = roomOf(target);
		if (targetRoom < 0 || roomOf(me) == targetRoom)
		{
			return;
		}
		active = true;
		try
		{
			log.info("[DraynorBasement] solving toward room {}", targetRoom);
			// Re-plan after every step: read the current room + lever combo, take the first action
			// on a shortest path, execute it. A fixed up-front plan strands the player because a
			// lever pull can re-lock the doors around it; re-planning each step stays correct.
			for (int step = 0; step < 80; step++)
			{
				int room = roomOf(Rs2Player.getWorldLocation());
				if (room < 0 || room == targetRoom)
				{
					return;
				}
				int[] act = firstAction(readCombo(), room, targetRoom);
				if (act == null)
				{
					log.warn("[DraynorBasement] no path from room {} to room {}", room, targetRoom);
					return;
				}
				if (act[0] == 0)
				{
					// pull a lever in the current room; bail if the walk, click, or toggle fails so
					// we don't re-plan the same (unchanged) action until the step cap.
					int li = act[1];
					if (!Rs2Walker.walkTo(LEVER_TILE[li], 1))
					{
						log.warn("[DraynorBasement] could not reach lever {}", (char) ('A' + li));
						return;
					}
					final int vb = LEVER_VARBIT[li];
					final int before = Microbot.getVarbitValue(vb);
					if (!Rs2GameObject.interact(LEVER_OBJ[li], "Pull")
						|| !sleepUntil(() -> Microbot.getVarbitValue(vb) != before, LEVER_PULL_TIMEOUT_MS))
					{
						log.warn("[DraynorBasement] lever {} pull did not register", (char) ('A' + li));
						return;
					}
				}
				else
				{
					// cross into the next room; the unlocked door's transport opens + crosses it.
					// Retry: the route can momentarily read UNREACHABLE before the transport set
					// re-evaluates the varbits from the previous pull.
					final int next = act[1];
					boolean crossed = false;
					for (int attempt = 0; attempt < 3 && !crossed; attempt++)
					{
						Rs2Walker.walkTo(ROOM_TILE[next], 1);
						crossed = roomOf(Rs2Player.getWorldLocation()) == next
							|| sleepUntil(() -> roomOf(Rs2Player.getWorldLocation()) == next, 700);
					}
					if (!crossed)
					{
						log.warn("[DraynorBasement] could not cross into room {}; deferring to walker", next);
						return;
					}
				}
				sleepUntil(() -> !Rs2Player.isMoving(), 400); // let movement + transport state settle
			}
		}
		finally
		{
			active = false;
		}
	}
}
