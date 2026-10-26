package net.runelite.client.plugins.microbot.ddchopper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PanelLogicTest {
	@Test
	public void dropAndBankIgnoreFiremaking() {
		assertEquals(ResourceHandling.Action.DROP, ResourceHandling.decide(ChopMode.POWER_CHOP, true, 99, "Logs"));
		assertEquals(ResourceHandling.Action.BANK, ResourceHandling.decide(ChopMode.BANK, false, 1, "Yew logs"));
	}

	@Test
	public void burnNeedsTinderboxAndLevel() {
		assertEquals(ResourceHandling.Action.BURN, ResourceHandling.decide(ChopMode.BURN, true, 15, "Oak logs"));
		assertEquals(ResourceHandling.Action.DROP, ResourceHandling.decide(ChopMode.BURN, true, 14, "Oak logs"));
		assertEquals(ResourceHandling.Action.DROP, ResourceHandling.decide(ChopMode.BURN, false, 99, "Logs"));
		assertEquals(ResourceHandling.Action.DROP, ResourceHandling.decide(ChopMode.BURN, true, 99, "Bird nest"));
		assertEquals(ResourceHandling.Action.DROP, ResourceHandling.decide(ChopMode.BURN, true, 99, null));
	}

	@Test
	public void firemakingLevels() {
		assertEquals(1, ResourceHandling.firemakingLevel("logs"));
		assertEquals(60, ResourceHandling.firemakingLevel("Yew logs"));
		assertEquals(90, ResourceHandling.firemakingLevel("Redwood logs"));
		assertEquals(-1, ResourceHandling.firemakingLevel("Oak plank"));
	}

	@Test
	public void everyModeHasAHintAndShortLabel() {
		for (ChopMode m : ChopMode.values()) {
			assertTrue(ResourceHandling.hint(m).toLowerCase().startsWith(m.toString().toLowerCase()));
			assertTrue(m.toString().length() <= 12);
		}
	}

	@Test
	public void runtimeStatusLines() {
		assertEquals(RuntimeStatus.State.STOPPED, RuntimeStatus.state(false, false, true));
		assertEquals(RuntimeStatus.State.ON_BREAK, RuntimeStatus.state(true, true, true));
		assertEquals(RuntimeStatus.State.PAUSED, RuntimeStatus.state(true, true, false));
		assertEquals(RuntimeStatus.State.RUNNING, RuntimeStatus.state(true, false, false));
		assertEquals("RUNNING", RuntimeStatus.describe(RuntimeStatus.State.RUNNING, ""));
		assertEquals("ON BREAK", RuntimeStatus.describe(RuntimeStatus.State.ON_BREAK, null));
		assertEquals("STOPPED: No axe equipped/in inventory",
			RuntimeStatus.describe(RuntimeStatus.State.STOPPED, "No axe equipped/in inventory"));
		assertEquals("35 -> 60", RuntimeStatus.levelLine(GoalType.TARGET_LEVEL, 35, 60));
		assertEquals("35", RuntimeStatus.levelLine(GoalType.RUN_TIME, 35, 60));
	}

	@Test
	public void forestryWinsOverCrowdAvoid() {
		assertEquals(CrowdRule.Mode.SEEK_BUSY, CrowdRule.resolve(true, true, true));
		assertEquals(CrowdRule.Mode.SEEK_BUSY, CrowdRule.resolve(true, true, false));
		assertEquals(CrowdRule.Mode.AVOID_CROWD, CrowdRule.resolve(false, true, true));
		assertEquals(CrowdRule.Mode.AVOID_CROWD, CrowdRule.resolve(true, false, true));
		assertEquals(CrowdRule.Mode.OFF, CrowdRule.resolve(false, false, false));
		assertEquals(CrowdRule.Mode.OFF, CrowdRule.resolve(true, false, false));
		assertTrue(CrowdRule.conflict(true, true, true));
		assertFalse(CrowdRule.conflict(false, true, true));
		assertFalse(CrowdRule.conflictNote(true, true, true).isEmpty());
		assertTrue(CrowdRule.conflictNote(true, true, false).isEmpty());
	}

	@Test
	public void seekBusyHopsOnlyWhenTooEmpty() {
		HopGate g = HopGate.seekBusy(8, 6);
		assertEquals(HopGate.Decision.STAY, g.decide(8, 0));
		assertEquals(HopGate.Decision.STAY, g.decide(20, 1_000));
		assertEquals(HopGate.Decision.WAIT, g.decide(2, 2_000));
		assertEquals(HopGate.Decision.HOP, g.decide(2, 2_000 + HopGate.GRACE_MILLIS));
	}

	@Test
	public void seekBusyUsesTheSameHourlyCap() {
		HopGate g = HopGate.seekBusy(8, 2);
		long t = 0;
		for (int i = 0; i < 2; i++) {
			g.decide(0, t);
			t += HopGate.GRACE_MILLIS;
			assertEquals(HopGate.Decision.HOP, g.decide(0, t));
			g.recordHop(t);
			t += HopGate.MIN_GAP_MILLIS + 1;
		}
		g.decide(0, t);
		t += HopGate.GRACE_MILLIS;
		assertEquals(HopGate.Decision.CAPPED, g.decide(0, t));
	}

	@Test
	public void avoidCrowdBehaviourUnchanged() {
		HopGate g = new HopGate(3, 6);
		assertEquals(HopGate.Decision.STAY, g.decide(3, 0));
		assertEquals(HopGate.Decision.WAIT, g.decide(4, 1_000));
		assertEquals(HopGate.Decision.HOP, g.decide(4, 1_000 + HopGate.GRACE_MILLIS));
	}
}
