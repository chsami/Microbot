package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.Microbot;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.QuestWalkRule;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.api.EquipmentInventorySlot;

import java.util.ArrayList;
import java.util.List;
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

	/**
	 * Stage 4: teleport out with worn jewellery (Amulet of glory, Ring of wealth). Owned tablets are used earlier, by
	 * {@link #teleportTowardFarGoal}; never a spell. Returns false when there is nothing to teleport with and the
	 * watchdog goes on to give up.
	 */
	static boolean teleport(WorldPoint stepRegion)
	{
		Rs2Walker.clearWalkingRoute("quest-watchdog:stage4-teleport");
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
		return false;
	}

	/**
	 * Early way out of a walk that is going nowhere far from its goal (the walker cannot leave a room): uses an owned
	 * teleport tablet or worn jewellery that lands clearly nearer the goal. Only owned items, never a spell, and the use is
	 * logged. Returns false when nothing owned helps.
	 */
	static boolean teleportTowardFarGoal(WorldPoint goal)
	{
		WorldPoint at = Rs2Player.getWorldLocation();
		if (QuestWalkRule.isLongTravelHeld() || !QuestStuckTeleports.goalIsFar(at, goal))
		{
			return false; // not in a fight, nor just after one
		}
		List<String> inventory = new ArrayList<>();
		Rs2Inventory.items().forEach(item -> inventory.add(item.getName()));
		List<String> worn = new ArrayList<>();
		for (EquipmentInventorySlot slot : new EquipmentInventorySlot[]{EquipmentInventorySlot.AMULET, EquipmentInventorySlot.RING})
		{
			var item = Rs2Equipment.get(slot);
			if (item != null)
			{
				worn.add(item.getName());
			}
		}
		QuestStuckTeleports.Option option = QuestStuckTeleports.choose(at, goal, inventory, worn);
		if (option == null)
		{
			return false;
		}
		log.warn("[QuestWatchdog] stuck at {} with the goal {} far away: using the owned {} ({})", at, goal, option.item,
			option.action);
		Rs2Walker.clearWalkingRoute("quest-watchdog:teleport-toward-goal");
		if (option.worn)
		{
			EquipmentInventorySlot slot = option.item.startsWith("amulet") ? EquipmentInventorySlot.AMULET : EquipmentInventorySlot.RING;
			return Rs2Equipment.interact(slot, option.action);
		}
		return Rs2Inventory.interact(option.item, option.action);
	}
}
