package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.Microbot;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;

import java.util.Objects;

/**
 * Game-facing half of the quest stuck watchdog: computes the progress signature and performs the
 * recovery actions for each {@link QuestStuckWatchdog.Stage}. All calls run on the watchdog's own
 * scheduler thread, never the client thread.
 */
@Slf4j
final class QuestStuckRecovery
{
	private QuestStuckRecovery()
	{
	}

	/** Hash of everything that counts as progress. */
	static long signature(QuestStep step, int questVar, boolean inDialogue)
	{
		return signature(step, questVar, inDialogue, 0L);
	}

	static long signature(QuestStep step, int questVar, boolean inDialogue, long extraProgress)
	{
		WorldPoint at = Rs2Player.getWorldLocation();
		int inv = Rs2Inventory.items().mapToInt(i -> 31 * i.getId() + i.getQuantity()).sum();
		String text = step == null ? "" : String.valueOf(step.getText());
		return Objects.hash(at, System.identityHashCode(step), text, questVar, inv, inDialogue, extraProgress);
	}

	/** Stage 1: cancel the walk and its interim route; the main loop re-plans on its next pass. */
	static void replan()
	{
		Rs2Walker.clearWalkingRoute("quest-watchdog:stage1-replan");
	}

	/** Stage 2: blacklist the edge from here toward the failed target for the session, then re-plan. */
	static void blacklist(WorldPoint failedTarget)
	{
		WorldPoint at = Rs2Player.getWorldLocation();
		if (at != null && failedTarget != null && !at.equals(failedTarget))
		{
			boolean learned = Rs2PathApi.learnBlockedEdge(at, failedTarget, "quest-watchdog:stuck");
			log.warn("[QuestWatchdog] blacklisted edge {} -> {} newly={}", at, failedTarget, learned);
		}
		Rs2Walker.clearWalkingRoute("quest-watchdog:stage2-blacklist");
	}

	/** Stage 3: climb down / open a door toward plane 0. Returns true if an interaction was issued. */
	static boolean escapePlane()
	{
		Rs2Walker.clearWalkingRoute("quest-watchdog:stage3-plane-escape");
		WorldPoint at = Rs2Player.getWorldLocation();
		if (at != null && at.getPlane() > 0)
		{
			for (String name : new String[]{"Staircase", "Ladder", "Stairs"})
			{
				if (Microbot.getRs2TileObjectCache().query().fromWorldView().withName(name).interact("Climb-down", 20))
				{
					return true;
				}
			}
		}
		for (String name : new String[]{"Door", "Gate", "Large door"})
		{
			if (Microbot.getRs2TileObjectCache().query().fromWorldView().withName(name).interact("Open", 20))
			{
				return true;
			}
		}
		return false;
	}

	/** Stage 4: use any teleport the player has, preferring one near the step's region. */
	static boolean teleport(WorldPoint stepRegion)
	{
		Rs2Walker.clearWalkingRoute("quest-watchdog:stage4-teleport");
		// {item name, destination} - inventory teleport tabs
		Object[][] tabs = {
			{"Varrock teleport", new WorldPoint(3213, 3424, 0)},
			{"Lumbridge teleport", new WorldPoint(3222, 3218, 0)},
			{"Falador teleport", new WorldPoint(2965, 3379, 0)},
			{"Camelot teleport", new WorldPoint(2757, 3478, 0)},
			{"Ardougne teleport", new WorldPoint(2662, 3307, 0)},
		};
		Object[] best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Object[] t : tabs)
		{
			if (!Rs2Inventory.hasItem((String) t[0]))
			{
				continue;
			}
			int d = stepRegion == null ? 0 : stepRegion.distanceTo2D((WorldPoint) t[1]);
			if (d < bestDist)
			{
				bestDist = d;
				best = t;
			}
		}
		if (best != null)
		{
			return Rs2Inventory.interact((String) best[0], "Break");
		}
		if (Rs2Equipment.isWearing(EquipmentInventorySlot.AMULET)
			&& Rs2Equipment.get(EquipmentInventorySlot.AMULET) != null
			&& Rs2Equipment.get(EquipmentInventorySlot.AMULET).getName().toLowerCase().contains("glory")
			&& Rs2Equipment.interact(EquipmentInventorySlot.AMULET, "Edgeville"))
		{
			return true;
		}
		if (Rs2Equipment.isWearing(EquipmentInventorySlot.RING)
			&& Rs2Equipment.get(EquipmentInventorySlot.RING) != null
			&& Rs2Equipment.get(EquipmentInventorySlot.RING).getName().toLowerCase().contains("ring of wealth")
			&& Rs2Equipment.interact(EquipmentInventorySlot.RING, "Grand Exchange"))
		{
			return true;
		}
		return Rs2Magic.cast(Rs2Spells.LUMBRIDGE_HOME_TELEPORT);
	}
}
