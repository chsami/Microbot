package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.ui.overlay.components.PanelComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class Rs2AntibanActionCooldownTest {

	@Before
	public void setUp() {
		Rs2Antiban.resetAntibanSettings(true);
		Rs2AntibanSettings.usePlayStyle = true;
		Rs2AntibanSettings.actionCooldownChance = 1.0;
		Rs2Antiban.setTIMEOUT(0);
	}

	@After
	public void tearDown() {
		Rs2Antiban.resetAntibanSettings(true);
		Rs2Antiban.setTIMEOUT(0);
	}

	@Test
	public void actionCooldownWithoutActivityDefaultsToIntensityPlayStyle() {
		Rs2Antiban.actionCooldown();

		PlayStyle expected = ActivityIntensity.EXTREME.getPlayStyle();
		assertSame(expected, Rs2Antiban.getPlayStyle());
		assertEquals(expected.getPrimaryTickInterval(), Rs2Antiban.getTIMEOUT());
		assertTrue(Rs2AntibanSettings.actionCooldownActive);
	}

	@Test
	public void actionCooldownWithoutActivityUsesRandomPlayStyleWhenRandomIntervalsEnabled() {
		Rs2AntibanSettings.randomIntervals = true;
		Rs2AntibanSettings.behavioralVariability = true;
		Rs2AntibanSettings.nonLinearIntervals = true;

		Rs2Antiban.actionCooldown();

		assertSame(PlayStyle.RANDOM, Rs2Antiban.getPlayStyle());
		assertTrue(Rs2Antiban.getTIMEOUT() >= 1);
		assertTrue(Rs2AntibanSettings.actionCooldownActive);
	}

	@Test
	public void actionCooldownKeepsExistingPlayStyle() {
		PlayStyle.CAUTIOUS.resetPlayStyle();
		Rs2Antiban.setPlayStyle(PlayStyle.CAUTIOUS);

		Rs2Antiban.actionCooldown();

		assertSame(PlayStyle.CAUTIOUS, Rs2Antiban.getPlayStyle());
		assertEquals(PlayStyle.CAUTIOUS.getPrimaryTickInterval(), Rs2Antiban.getTIMEOUT());
	}

	@Test
	public void actionCooldownWithoutActivityCountsDownAndEnds() {
		Rs2Antiban.actionCooldown();
		assertNull(Rs2Antiban.getCategory());
		assertTrue(Rs2AntibanSettings.actionCooldownActive);

		int ticks = Rs2Antiban.getTIMEOUT();
		for (int i = 0; i < ticks; i++) {
			AntibanPlugin.performActionBreak();
		}
		assertEquals(0, Rs2Antiban.getTIMEOUT());
		assertTrue(Rs2AntibanSettings.actionCooldownActive);

		AntibanPlugin.performActionBreak();
		assertFalse(Rs2AntibanSettings.actionCooldownActive);
	}

	@Test
	public void overlayRendersWithoutActivity() {
		Rs2Antiban.actionCooldown();
		PanelComponent panel = new PanelComponent();

		Rs2Antiban.renderAntibanOverlayComponents(panel);

		assertFalse(panel.getChildren().isEmpty());
	}
}
