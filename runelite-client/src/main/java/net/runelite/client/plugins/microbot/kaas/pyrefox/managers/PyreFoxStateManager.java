package net.runelite.client.plugins.microbot.kaas.pyrefox.managers;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kaas.pyrefox.PyreFoxConfig;
import net.runelite.client.plugins.microbot.kaas.pyrefox.PyreFoxConstants;
import net.runelite.client.plugins.microbot.kaas.pyrefox.PyreFoxPlugin;
import net.runelite.client.plugins.microbot.kaas.pyrefox.enums.PyreFoxState;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.kaas.pyrefox.enums.PyreFoxState.*;

@Slf4j
public class PyreFoxStateManager extends Script
{
	private PyreFoxConfig _config;
	private PyreFoxState _lastState;
	boolean isFirstRun = true;

	public boolean run(PyreFoxConfig config)
	{
		if (_config == null)
			_config = config;
		_lastState = INITIALIZE;

		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
			try {
				if (!Microbot.isLoggedIn()) return;

				var currentState = _getCurrentState();

				// Skip the reroll check on the very first run
				if (!isFirstRun && _lastState == CHOPPING_TREES && currentState != CHOPPING_TREES) {
					_log("Finished chopping trees, rerolling gather amounts");
					PyreFoxConstants.rerollGatherAmounts();
				}

				if (PyreFoxPlugin.currentState != currentState)
					_log("Switching state to " + currentState);

				PyreFoxPlugin.currentState = currentState;

				if (_lastState != currentState)
					_lastState = currentState;

				// First run is now complete
				if (isFirstRun)
					isFirstRun = false;

				_handleDroppingItems();

			} catch (Exception ex) {
				Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
			}
		}, 0, 600, TimeUnit.MILLISECONDS);

		return true;
	}

	private void _handleDroppingItems()
	{
		if (Rs2Inventory.hasItem(ItemID.FOX_FUR))
		{
			Rs2Inventory.drop(ItemID.FOX_FUR);
			sleep(200, 400);
		}
		if (Rs2Inventory.hasItem(ItemID.BONES))
		{
			Rs2Inventory.drop(ItemID.BONES);
			sleep(200, 400);
		}
	}

	/**
	 * Decides the current state.
	 */
	private PyreFoxState _getCurrentState()
	{
//		Microbot.log(Rs2Player.distanceTo(PyreFoxConstants.PYRE_FOX_CENTER_POINT) + "");

		// Run energy checks.
		if (!Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() > 10)
			Rs2Player.toggleRunEnergy(true);


		// Some initial checks before we can run our script.
		if (!PyreFoxPlugin.hasInitialized)
			return INITIALIZE;

		if (Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) <= _config.runToBankHP())
			return LOW_HITPOINTS;


		// For cutting trees we want to make sure that we have reached the randomized GATHER_LOGS_AT_AMOUNT.
		// We should also be within a radius of 60 of our anchor point, since being to far out means we won't have trees nearby,
		// causing exceptions to be thrown. when we do enter the CHOPPING_TREES state, we enter a while loop
		// which exits when reaching our log goal, or if our hitpoints drop below our configured min. HP.
		var trapPoint = PyreFoxConstants.TRAP_OBJECT_POINT;
		var trap = trapPoint != null ? Rs2GameObject.getGameObject(PyreFoxConstants.TRAP_OBJECT_POINT) : null;
		boolean trapCaughtFox = (trap != null && trap.getId() == PyreFoxConstants.GAMEOBJECT_ROCK_FOX_CAUGHT);
		boolean surpassedLogCutThreshold = Rs2Inventory.count("logs") <= PyreFoxConstants.GATHER_LOGS_AT_AMOUNT;
		if ((!trapCaughtFox || trap == null) && surpassedLogCutThreshold && Rs2Player.distanceTo(PyreFoxConstants.PYRE_FOX_CENTER_POINT) < 60)
			return CHOPPING_TREES;


		// Handles banking.
		boolean shouldBank = (_config.ForceBank() || Rs2Inventory.getEmptySlots() <= 2);
		if (shouldBank && !Rs2Bank.isOpen())
			return WALK_TO_BANK;

		if (!shouldBank && Rs2Player.distanceTo(PyreFoxConstants.PYRE_FOX_CENTER_POINT) > 5)
			return WALK_TO_PYREFOX;

		if (shouldBank && Rs2Bank.isOpen())
			return BANKING;


		// Default state; exterminate, let them haters perpetrate!
		return CATCHING;
	}

	/**
	 * Logging extension which considers verbose logging toggle.
	 */
	private void _log(String message)
	{
		if (_config.EnableVerboseLogging())
			Microbot.log(message);
	}

	public void shutdown() {
		super.shutdown();
	}
}

