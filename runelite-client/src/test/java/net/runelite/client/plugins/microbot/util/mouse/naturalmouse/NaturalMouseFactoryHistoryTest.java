package net.runelite.client.plugins.microbot.util.mouse.naturalmouse;

import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.OvershootManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.MouseMotionNature;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.FlowTemplates;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.fail;

/**
 * Every template build writes into the one nature a NaturalMouse owns, so a template that leaves a
 * setting alone would inherit it from whichever template was built before. The motion handed out for
 * an intensity must not depend on that history: it must equal what a fresh NaturalMouse hands out.
 */
@RunWith(Parameterized.class)
public class NaturalMouseFactoryHistoryTest
{
	// Every branch of NaturalMouse.getFactory(), including the null intensity that falls through.
	private static final List<ActivityIntensity> SERVED = Arrays.asList(
		ActivityIntensity.VERY_LOW,
		ActivityIntensity.LOW,
		ActivityIntensity.MODERATE,
		ActivityIntensity.HIGH,
		ActivityIntensity.EXTREME,
		null);

	// Spans 0 to past every template's minimum overshoot distance (3, or the default 10), and a far one.
	private static final double[] OVERSHOOT_DISTANCES = {
		0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 5, 6, 7, 8, 9, 9.5, 10, 10.5, 11, 12, 15, 20, 50, 500};

	// Spans below and above every template's minimum overshoot movement time (40, 100, 130, 250 ms).
	private static final long[] MOVEMENT_TIMES_MS = {0, 1, 20, 50, 72, 100, 150, 200, 250, 300, 450, 600, 1000};

	private static final int[] OVERSHOOTS_REMAINING = {1, 2, 3, 4};

	// Overshoot offsets are random, so their scale is read from the largest of many draws. With the
	// default divider a draw reaches the largest offset with probability 1/50 per axis, so 4000 draws
	// on two axes all miss it with probability (49/50)^8000, about 1e-70.
	private static final int AMOUNT_DRAWS = 4000;
	private static final double AMOUNT_TARGET_DISTANCE = 2000;

	// Passed identically to both managers so only their settings can make the results differ.
	private static final Flow FLOW = new Flow(FlowTemplates.constantSpeed());
	private static final long FLOW_TIME_MS = 500;

	private final ActivityIntensity target;
	private final ActivityIntensity predecessor;

	private boolean previousSimulateFatigue;
	private boolean previousSimulateMistakes;
	private boolean previousDynamicIntensity;
	private int previousTicksSinceLogin;
	private double previousNoiseAmplitude;
	private ActivityIntensity previousIntensity;

	public NaturalMouseFactoryHistoryTest(String targetLabel, String predecessorLabel,
		ActivityIntensity target, ActivityIntensity predecessor)
	{
		this.target = target;
		this.predecessor = predecessor;
	}

	@Parameterized.Parameters(name = "{0} after {1}")
	public static List<Object[]> pairs()
	{
		List<Object[]> pairs = new ArrayList<>();
		for (ActivityIntensity target : SERVED)
		{
			for (ActivityIntensity predecessor : SERVED)
			{
				if (target != predecessor)
				{
					pairs.add(new Object[]{label(target), label(predecessor), target, predecessor});
				}
			}
		}
		return pairs;
	}

	@Before
	public void before()
	{
		previousSimulateFatigue = Rs2AntibanSettings.simulateFatigue;
		previousSimulateMistakes = Rs2AntibanSettings.simulateMistakes;
		previousDynamicIntensity = Rs2AntibanSettings.dynamicIntensity;
		previousTicksSinceLogin = AntibanPlugin.ticksSinceLogin;
		previousNoiseAmplitude = Rs2Antiban.mouseFatigue.noiseAmplitude;
		previousIntensity = Rs2Antiban.getActivityIntensity();

		// Mistakes on, or every template has zero overshoots and the distance threshold never shows.
		Rs2AntibanSettings.simulateMistakes = true;
		Rs2AntibanSettings.simulateFatigue = false;
		AntibanPlugin.ticksSinceLogin = 0;
		Rs2Antiban.mouseFatigue.noiseAmplitude = 0;
	}

	@After
	public void after()
	{
		Rs2AntibanSettings.simulateFatigue = previousSimulateFatigue;
		Rs2AntibanSettings.simulateMistakes = previousSimulateMistakes;
		AntibanPlugin.ticksSinceLogin = previousTicksSinceLogin;
		Rs2Antiban.mouseFatigue.noiseAmplitude = previousNoiseAmplitude;
		Rs2Antiban.setActivityIntensity(previousIntensity);
		// setActivityIntensity also turns dynamicIntensity off.
		Rs2AntibanSettings.dynamicIntensity = previousDynamicIntensity;
	}

	@Test(timeout = 30_000)
	public void theMotionForAnIntensityDoesNotDependOnTheIntensityBeforeIt()
	{
		NaturalMouse used = new NaturalMouse();
		Rs2Antiban.setActivityIntensity(predecessor);
		used.getFactory();
		Rs2Antiban.setActivityIntensity(target);
		MouseMotionFactory afterHistory = used.getFactory();

		MouseMotionFactory fresh = new NaturalMouse().getFactory();

		// Every differing setting is listed, so one run shows all a fix has to cover.
		List<String> differences = new ArrayList<>();
		OvershootManager freshOvershoots = fresh.getOvershootManager();
		OvershootManager historyOvershoots = afterHistory.getOvershootManager();

		Series overshootCounts = new Series("overshoot count by target distance, which starts at the minimum distance for overshoots");
		for (double distance : OVERSHOOT_DISTANCES)
		{
			overshootCounts.probe("at " + distance + " px",
				freshOvershoots.getOvershoots(FLOW, FLOW_TIME_MS, distance),
				historyOvershoots.getOvershoots(FLOW, FLOW_TIME_MS, distance));
		}
		overshootCounts.report(differences);

		Series nextTimes = new Series("next movement time (ms) after an overshoot, which floors at the minimum overshoot movement time");
		for (long movementMs : MOVEMENT_TIMES_MS)
		{
			for (int remaining : OVERSHOOTS_REMAINING)
			{
				nextTimes.probe("after a " + movementMs + " ms movement with " + remaining + " overshoots left",
					freshOvershoots.deriveNextMouseMovementTimeMs(movementMs, remaining),
					historyOvershoots.deriveNextMouseMovementTimeMs(movementMs, remaining));
			}
		}
		nextTimes.report(differences);

		compare(differences, "largest overshoot offset (px) over " + AMOUNT_DRAWS + " draws toward a "
				+ (int) AMOUNT_TARGET_DISTANCE + " px target",
			largestOvershootOffset(freshOvershoots), largestOvershootOffset(historyOvershoots));

		MouseMotionNature freshNature = fresh.getNature();
		MouseMotionNature historyNature = afterHistory.getNature();
		compare(differences, "nature reaction time base ms",
			freshNature.getReactionTimeBaseMs(), historyNature.getReactionTimeBaseMs());
		compare(differences, "nature reaction time variation ms",
			freshNature.getReactionTimeVariationMs(), historyNature.getReactionTimeVariationMs());
		compare(differences, "nature time to steps divider",
			freshNature.getTimeToStepsDivider(), historyNature.getTimeToStepsDivider());
		compare(differences, "nature minimum steps",
			freshNature.getMinSteps(), historyNature.getMinSteps());
		compare(differences, "nature effect fade steps",
			freshNature.getEffectFadeSteps(), historyNature.getEffectFadeSteps());

		// The base time can only be read from samples, so it is checked against the template value,
		// which a fresh NaturalMouse must also show.
		long base = templateBase(target);
		NaturalMouseFactorySettingsTest.assertBaseTime(label(target) + " motion of a fresh NaturalMouse", fresh, base);
		try
		{
			NaturalMouseFactorySettingsTest.assertBaseTime("base movement time", afterHistory, base);
		}
		catch (AssertionError e)
		{
			differences.add(e.getMessage());
		}

		if (!differences.isEmpty())
		{
			fail(label(target) + " motion built after " + label(predecessor) + " on the same NaturalMouse differs from "
				+ "a fresh NaturalMouse's " + label(target) + " motion in " + differences.size() + " settings:\n  "
				+ String.join("\n  ", differences));
		}
	}

	private static void compare(List<String> differences, String setting, Object fresh, Object afterHistory)
	{
		if (!fresh.equals(afterHistory))
		{
			differences.add(setting + ": fresh " + fresh + ", after history " + afterHistory);
		}
	}

	/** One setting read through many inputs, reported as one line so a threshold shift stays readable. */
	private static final class Series
	{
		private final String setting;
		private int probes;
		private int differing;
		private String firstDifference;

		Series(String setting)
		{
			this.setting = setting;
		}

		void probe(String input, Object fresh, Object afterHistory)
		{
			probes++;
			if (!fresh.equals(afterHistory))
			{
				differing++;
				if (firstDifference == null)
				{
					firstDifference = input + ": fresh " + fresh + ", after history " + afterHistory;
				}
			}
		}

		void report(List<String> differences)
		{
			if (differing > 0)
			{
				differences.add(setting + ": first " + firstDifference + " (" + differing + " of " + probes
					+ " inputs differ)");
			}
		}
	}

	private static int largestOvershootOffset(OvershootManager manager)
	{
		int largest = 0;
		for (int i = 0; i < AMOUNT_DRAWS; i++)
		{
			Point offset = manager.getOvershootAmount(AMOUNT_TARGET_DISTANCE, 0, FLOW_TIME_MS, 1);
			largest = Math.max(largest, Math.max(Math.abs(offset.x), Math.abs(offset.y)));
		}
		return largest;
	}

	// From FactoryTemplates, with fatigue off.
	private static long templateBase(ActivityIntensity intensity)
	{
		if (intensity == ActivityIntensity.VERY_LOW)
		{
			return NaturalMouseFactorySettingsTest.AVERAGE_USER_BASE;
		}
		if (intensity == ActivityIntensity.LOW)
		{
			return NaturalMouseFactorySettingsTest.NORMAL_GAMER_INITIAL_BASE;
		}
		if (intensity == ActivityIntensity.MODERATE || intensity == ActivityIntensity.HIGH)
		{
			return NaturalMouseFactorySettingsTest.FAST_GAMER_INITIAL_BASE;
		}
		return NaturalMouseFactorySettingsTest.SUPER_FAST_GAMER_INITIAL_BASE;
	}

	private static String label(ActivityIntensity intensity)
	{
		return intensity == null ? "no intensity" : intensity.name();
	}
}
