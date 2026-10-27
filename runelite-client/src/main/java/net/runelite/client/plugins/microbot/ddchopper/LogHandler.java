package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static net.runelite.client.plugins.microbot.ddchopper.ScriptTimings.ACTION_TIMEOUT_MS;
import static net.runelite.client.plugins.microbot.ddchopper.ScriptTimings.ANIMATION_WINDOW_MS;
import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** What to do with a full inventory: burn the logs, drop them one by one, or bank them. */
final class LogHandler
{
	private static final Predicate<Rs2ItemModel> IS_LOGS =
		item -> item.getName() != null && item.getName().toLowerCase().endsWith("logs");
	/** Upper bound on drop clicks per tick (a full inventory is 28 slots). */
	private static final int MAX_DROPS_PER_TICK = 30;
	private static final int LIGHT_FINISH_TIMEOUT_MS = 12_000;
	private static final int LIGHT_START_ANIMATION_MS = 1200;

	private final DDChopperConfig config;
	private final HumanPacing pacing;
	private final Consumer<String> status;
	private final BooleanSupplier scriptRunning;

	LogHandler(DDChopperConfig config, HumanPacing pacing, Consumer<String> status, BooleanSupplier scriptRunning)
	{
		this.config = config;
		this.pacing = pacing;
		this.status = status;
		this.scriptRunning = scriptRunning;
	}

	void handleFull()
	{
		Rs2ItemModel logItem = Rs2Inventory.get(IS_LOGS);
		ResourceHandling.Action action = ResourceHandling.decide(config.mode(), Rs2Inventory.hasItem("Tinderbox"),
			Rs2Player.getRealSkillLevel(Skill.FIREMAKING), logItem == null ? null : logItem.getName());
		switch (action)
		{
			case BURN:
				burnLogs();
				break;
			case DROP:
				status.accept(config.mode() == ChopMode.BURN ? "Dropping logs (cannot burn)" : "Dropping logs");
				dropLogs();
				break;
			default:
				bankLogs();
				break;
		}
	}

	/** Lights one fire: tinderbox on a log, then waits for the lighting animation to end. */
	private void burnLogs()
	{
		status.accept("Burning logs");
		if (!Rs2Inventory.combine("Tinderbox", "logs"))
		{
			sleep(300, 600);
			return;
		}
		sleepUntil(() -> Rs2Player.isAnimating(LIGHT_START_ANIMATION_MS), ACTION_TIMEOUT_MS);
		sleepUntil(() -> !Rs2Player.isAnimating(ANIMATION_WINDOW_MS) && !Rs2Player.isMoving(), LIGHT_FINISH_TIMEOUT_MS);
	}

	/** Drops the logs one by one with a varying pace. */
	private void dropLogs()
	{
		if (pacing == null || !pacing.isEnabled())
		{
			Rs2Inventory.dropAll(IS_LOGS);
			return;
		}
		for (int i = 0; i < MAX_DROPS_PER_TICK && Rs2Inventory.items(IS_LOGS).findAny().isPresent() && scriptRunning.getAsBoolean(); i++)
		{
			if (!Rs2Inventory.drop(IS_LOGS))
			{
				break;
			}
			sleep((int) pacing.dropDelayMs());
		}
	}

	private void bankLogs()
	{
		status.accept("Banking logs");
		if (BankTrip.walkToAndOpen())
		{
			Rs2Bank.depositAllExcept(Axe.itemIds());
			sleepUntil(() -> !Rs2Inventory.isFull(), ACTION_TIMEOUT_MS);
			Rs2Bank.closeBank();
		}
	}
}
