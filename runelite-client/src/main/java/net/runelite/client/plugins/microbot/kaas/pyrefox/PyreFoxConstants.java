package net.runelite.client.plugins.microbot.kaas.pyrefox;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

public class PyreFoxConstants
{
	public static final WorldPoint PYRE_FOX_CENTER_POINT = new WorldPoint(1617, 2999, 0);
	public static WorldPoint TRAP_OBJECT_POINT = null;
	public static volatile int GATHER_LOGS_AT_AMOUNT = 0;
	public static volatile int GATHER_LOGS_AMOUNT = 0;

	// 19215, 19217, 50726
	public static final int GAMEOBJECT_ROCK_NO_TRAP = 19215;
	public static final int GAMEOBJECT_ROCK_TRAP = 19217;
	public static final int GAMEOBJECT_ROCK_FOX_CAUGHT = 50726;


	/**
	 * Sets the gather logs at amount to something new,
	 * prevents always getting logs at 0 logs or w/e.
	 */
	public static void rerollGatherAmounts()
	{
		Microbot.log("Rerolling gathering amounts...");

		int emptySlots = Rs2Inventory.getEmptySlots();
		// Calculate the maximum amount to gather based on available space
		int maxGatherAmount = Math.max(emptySlots, 1); // Minimum gather amount should always be 1

		// Set GATHER_LOGS_AMOUNT between 1 and the calculated max (capped at 13)
		GATHER_LOGS_AMOUNT = Rs2Random.between(1, Math.min(maxGatherAmount, 13));
		// Set GATHER_LOGS_AT_AMOUNT to be always strictly less than GATHER_LOGS_AMOUNT and at max. 3.
		GATHER_LOGS_AT_AMOUNT = Rs2Random.between(0, Math.max(GATHER_LOGS_AMOUNT - 1, 3));

		Microbot.log("Will chop until " + GATHER_LOGS_AMOUNT + " logs, starting new chopping at " + GATHER_LOGS_AT_AMOUNT + " logs");
	}
}
