package net.runelite.client.plugins.microbot.util.antiban;

import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlayStyle;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.ui.overlay.components.PanelComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class Rs2AntibanActionCooldownTest {

	@Before
	public void setUp() {
		Rs2Antiban.resetAntibanSettings(true);
		Rs2AntibanSettings.usePlayStyle = true;
		Rs2AntibanSettings.actionCooldownChance = 1.0;
		Rs2Antiban.setTIMEOUT(0);
		Rs2Antiban.cooldownPausedScripts = false;
	}

	@After
	public void tearDown() {
		Rs2Antiban.resetAntibanSettings(true);
		Rs2Antiban.setTIMEOUT(0);
		Rs2Antiban.cooldownPausedScripts = false;
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
	public void actionCooldownIsSkippedWhenAntibanDisabled() {
		Rs2AntibanSettings.antibanEnabled = false;

		Rs2Antiban.actionCooldown();

		assertFalse(Rs2AntibanSettings.actionCooldownActive);
		assertEquals(0, Rs2Antiban.getTIMEOUT());
	}

	@Test
	public void cooldownStillCountsDownAfterAntibanDisabled() {
		Rs2Antiban.actionCooldown();
		assertTrue(Rs2AntibanSettings.actionCooldownActive);
		Rs2AntibanSettings.antibanEnabled = false;

		AntibanPlugin plugin = new AntibanPlugin();
		int ticks = Rs2Antiban.getTIMEOUT();
		for (int i = 0; i <= ticks; i++) {
			plugin.onGameTick(new GameTick());
		}

		assertEquals(0, Rs2Antiban.getTIMEOUT());
		assertFalse(Rs2AntibanSettings.actionCooldownActive);
	}

	@Test
	public void universalCooldownPauseIsReleasedWhenNoBreakHandlerConsumesMicroBreak() {
		Rs2AntibanSettings.universalAntiban = true;
		Rs2AntibanSettings.takeMicroBreaks = true;
		AntibanPlugin plugin = new AntibanPlugin();

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
			Microbot.pauseAllScripts.set(false);

			for (int round = 0; round < 3; round++) {
				Rs2Antiban.actionCooldown();
				Rs2AntibanSettings.microBreakActive = true;
				assertTrue(Microbot.pauseAllScripts.get());

				plugin.handleMicroBreakIntegration();
				assertFalse(Rs2AntibanSettings.microBreakActive);
				assertTrue(Microbot.pauseAllScripts.get());

				int ticks = Rs2Antiban.getTIMEOUT();
				for (int i = 0; i < ticks; i++) {
					plugin.handleMicroBreakIntegration();
					AntibanPlugin.performActionBreak();
					assertTrue(Microbot.pauseAllScripts.get());
				}

				plugin.handleMicroBreakIntegration();
				AntibanPlugin.performActionBreak();
				assertFalse(Rs2AntibanSettings.actionCooldownActive);
				assertFalse(Microbot.pauseAllScripts.get());
			}

			microbot.verify(() -> Microbot.showMessage(anyString()), times(1));
		} finally {
			Microbot.pauseAllScripts.set(false);
		}
	}

	@Test
	public void universalCooldownLeavesScriptsRunningWhileInCombat() {
		Rs2AntibanSettings.universalAntiban = true;

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			 MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class)) {
			Microbot.pauseAllScripts.set(false);
			microbot.when(Microbot::isLoggedIn).thenReturn(true);
			combat.when(Rs2Combat::inCombat).thenReturn(true);
			Rs2Antiban.setActivity(Activity.GENERAL_SLAYER);

			Rs2Antiban.actionCooldown();
			assertTrue(Rs2AntibanSettings.actionCooldownActive);
			assertFalse(Microbot.pauseAllScripts.get());

			int ticks = Rs2Antiban.getTIMEOUT();
			assertTrue(ticks >= 2);
			for (int i = 0; i < 50; i++) {
				AntibanPlugin.performActionBreak();
				assertFalse(Microbot.pauseAllScripts.get());
			}
			assertEquals(ticks, Rs2Antiban.getTIMEOUT());

			combat.when(Rs2Combat::inCombat).thenReturn(false);
			AntibanPlugin.performActionBreak();
			assertTrue(Microbot.pauseAllScripts.get());
			assertEquals(ticks - 1, Rs2Antiban.getTIMEOUT());

			combat.when(Rs2Combat::inCombat).thenReturn(true);
			AntibanPlugin.performActionBreak();
			assertFalse(Microbot.pauseAllScripts.get());
			assertEquals(ticks - 1, Rs2Antiban.getTIMEOUT());

			combat.when(Rs2Combat::inCombat).thenReturn(false);
			for (int i = 0; i < ticks - 1; i++) {
				AntibanPlugin.performActionBreak();
				assertTrue(Microbot.pauseAllScripts.get());
			}
			AntibanPlugin.performActionBreak();
			assertFalse(Rs2AntibanSettings.actionCooldownActive);
			assertFalse(Microbot.pauseAllScripts.get());
		} finally {
			Microbot.pauseAllScripts.set(false);
		}
	}

	@Test
	public void universalCooldownStartedOutOfCombatPausesScripts() {
		Rs2AntibanSettings.universalAntiban = true;

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			 MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class)) {
			Microbot.pauseAllScripts.set(false);
			microbot.when(Microbot::isLoggedIn).thenReturn(true);
			combat.when(Rs2Combat::inCombat).thenReturn(false);
			Rs2Antiban.setActivity(Activity.GENERAL_SLAYER);

			Rs2Antiban.actionCooldown();

			assertTrue(Rs2AntibanSettings.actionCooldownActive);
			assertTrue(Microbot.pauseAllScripts.get());
		} finally {
			Microbot.pauseAllScripts.set(false);
		}
	}

	@Test
	public void externalPauseSurvivesPendingUniversalCooldownInCombat() {
		Rs2AntibanSettings.universalAntiban = true;

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			 MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class)) {
			Microbot.pauseAllScripts.set(false);
			microbot.when(Microbot::isLoggedIn).thenReturn(true);
			combat.when(Rs2Combat::inCombat).thenReturn(true);
			Rs2Antiban.setActivity(Activity.GENERAL_SLAYER);

			Rs2Antiban.actionCooldown();
			assertTrue(Rs2AntibanSettings.actionCooldownActive);
			assertFalse(Microbot.pauseAllScripts.get());

			Microbot.pauseAllScripts.set(true);
			for (int i = 0; i < 10; i++) {
				AntibanPlugin.performActionBreak();
				assertTrue(Microbot.pauseAllScripts.get());
			}

			combat.when(Rs2Combat::inCombat).thenReturn(false);
			int ticks = Rs2Antiban.getTIMEOUT();
			for (int i = 0; i <= ticks; i++) {
				AntibanPlugin.performActionBreak();
				assertTrue(Microbot.pauseAllScripts.get());
			}
			assertFalse(Rs2AntibanSettings.actionCooldownActive);
			assertTrue(Microbot.pauseAllScripts.get());
		} finally {
			Microbot.pauseAllScripts.set(false);
		}
	}

	@Test
	public void cooldownStartedOutOfCombatIsReleasedWhenActivityTurnsToCombat() {
		Rs2AntibanSettings.universalAntiban = true;

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class);
			 MockedStatic<Rs2Combat> combat = mockStatic(Rs2Combat.class)) {
			Microbot.pauseAllScripts.set(false);
			microbot.when(Microbot::isLoggedIn).thenReturn(true);
			combat.when(Rs2Combat::inCombat).thenReturn(true);
			Rs2Antiban.setActivity(Activity.GENERAL_SMITHING);

			Rs2Antiban.actionCooldown();
			assertTrue(Rs2AntibanSettings.actionCooldownActive);
			assertTrue(Microbot.pauseAllScripts.get());
			int ticks = Rs2Antiban.getTIMEOUT();

			Rs2Antiban.setActivity(Activity.GENERAL_SLAYER);
			for (int i = 0; i < 50; i++) {
				AntibanPlugin.performActionBreak();
				assertFalse(Microbot.pauseAllScripts.get());
			}
			assertEquals(ticks, Rs2Antiban.getTIMEOUT());
			assertTrue(Rs2AntibanSettings.actionCooldownActive);
		} finally {
			Microbot.pauseAllScripts.set(false);
		}
	}

	@Test
	public void activityChangeDoesNotRestartRunningUniversalCooldown() {
		Rs2AntibanSettings.universalAntiban = true;
		Rs2AntibanSettings.dynamicActivity = true;
		AntibanPlugin plugin = new AntibanPlugin();

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
			Microbot.pauseAllScripts.set(false);
			plugin.onStatChanged(new StatChanged(Skill.ATTACK, 100, 1, 1));
			plugin.onStatChanged(new StatChanged(Skill.SLAYER, 100, 1, 1));
			plugin.onStatChanged(new StatChanged(Skill.ATTACK, 200, 1, 1));
			assertTrue(Rs2AntibanSettings.actionCooldownActive);
			assertSame(Activity.GENERAL_COMBAT, Rs2Antiban.getActivity());

			Rs2Antiban.setTIMEOUT(1);
			plugin.onStatChanged(new StatChanged(Skill.SLAYER, 200, 1, 1));

			assertSame(Activity.GENERAL_SLAYER, Rs2Antiban.getActivity());
			assertEquals(1, Rs2Antiban.getTIMEOUT());
		} finally {
			Microbot.pauseAllScripts.set(false);
		}
	}

	@Test
	public void staleMicroBreakIsCancelledOnEveryTickWithoutBreakHandler() {
		Rs2AntibanSettings.takeMicroBreaks = true;
		AntibanPlugin plugin = new AntibanPlugin();

		try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
			plugin.handleMicroBreakIntegration();

			Rs2AntibanSettings.microBreakActive = true;
			Microbot.pauseAllScripts.set(true);
			plugin.handleMicroBreakIntegration();

			assertFalse(Rs2AntibanSettings.microBreakActive);
			assertFalse(Microbot.pauseAllScripts.get());
		} finally {
			Microbot.pauseAllScripts.set(false);
		}
	}

	@Test
	public void overlayRendersWithoutActivity() {
		Rs2Antiban.actionCooldown();
		PanelComponent panel = new PanelComponent();

		Rs2Antiban.renderAntibanOverlayComponents(panel);

		assertFalse(panel.getChildren().isEmpty());
	}
}
