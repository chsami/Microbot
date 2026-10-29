package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AntibanActionCooldownBusyHoldTest {

	@Before
	public void setUp() {
		Rs2Antiban.resetAntibanSettings(true);
		Rs2AntibanSettings.universalAntiban = true;
		Rs2AntibanSettings.actionCooldownActive = true;
		Rs2Antiban.setTIMEOUT(4);
		AntibanPlugin.busyHoldTicks = 0;
		Microbot.pauseAllScripts.set(true);
	}

	@After
	public void tearDown() {
		Microbot.pauseAllScripts.set(false);
		Rs2Antiban.resetAntibanSettings(true);
		Rs2Antiban.setTIMEOUT(0);
		AntibanPlugin.busyHoldTicks = 0;
	}

	@Test
	public void busyPlayerHoldsCountdownOnlyForBoundedTicks() {
		for (int i = 0; i < 10; i++)
			AntibanPlugin.tickActionCooldown(true);
		assertEquals(4, Rs2Antiban.getTIMEOUT());

		AntibanPlugin.tickActionCooldown(true);
		assertEquals(3, Rs2Antiban.getTIMEOUT());
	}

	@Test
	public void continuouslyBusyCooldownAlwaysEndsAndUnpauses() {
		for (int i = 0; i < 10 + 4 + 1; i++)
			AntibanPlugin.tickActionCooldown(true);

		assertFalse(Rs2AntibanSettings.actionCooldownActive);
		assertFalse(Microbot.pauseAllScripts.get());
		assertEquals(0, AntibanPlugin.busyHoldTicks);
	}

	@Test
	public void idlePlayerCountsDownImmediately() {
		AntibanPlugin.tickActionCooldown(false);
		assertEquals(3, Rs2Antiban.getTIMEOUT());
		assertTrue(Rs2AntibanSettings.actionCooldownActive);
	}
}
