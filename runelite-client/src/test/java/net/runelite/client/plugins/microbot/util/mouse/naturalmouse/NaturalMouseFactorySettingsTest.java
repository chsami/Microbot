package net.runelite.client.plugins.microbot.util.mouse.naturalmouse;

import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.MouseMotionFactory;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.Flow;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.util.Pair;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

/**
 * The motion templates read simulateFatigue, simulateMistakes and ticksSinceLogin while they build,
 * so the factory NaturalMouse hands out must follow those settings, not only the activity intensity.
 */
public class NaturalMouseFactorySettingsTest
{
	// Template constants, from FactoryTemplates.
	static final int NORMAL_GAMER_INITIAL_BASE = 150;
	private static final int NORMAL_GAMER_MAX_BASE = 200;
	private static final int NORMAL_GAMER_OVERSHOOTS = 4;
	static final int FAST_GAMER_INITIAL_BASE = 120;
	private static final int FAST_GAMER_MAX_BASE = 170;
	private static final int FAST_GAMER_OVERSHOOTS = 3;
	static final int SUPER_FAST_GAMER_INITIAL_BASE = 90;
	private static final int SUPER_FAST_GAMER_OVERSHOOTS = 2;
	static final int AVERAGE_USER_BASE = 400;
	private static final int AVERAGE_USER_OVERSHOOTS = 4;

	// Far above every template's minimum overshoot distance, so the count is the template's own.
	private static final double DISTANCE = 500;

	// A stale base is caught only when a sample lands outside the expected range. The narrowest gap
	// below (120 against 150) puts a sample outside with probability 1/4, so 1000 samples miss it
	// with probability (3/4)^1000, about 1e-125.
	private static final int SAMPLES = 1000;

	// DefaultSpeedManager treats buckets this close to zero as pauses.
	private static final double ZERO_BUCKET = 10e-6;

	private boolean previousSimulateFatigue;
	private boolean previousSimulateMistakes;
	private boolean previousDynamicIntensity;
	private int previousTicksSinceLogin;
	private double previousNoiseAmplitude;
	private ActivityIntensity previousIntensity;

	@Before
	public void before()
	{
		previousSimulateFatigue = Rs2AntibanSettings.simulateFatigue;
		previousSimulateMistakes = Rs2AntibanSettings.simulateMistakes;
		previousDynamicIntensity = Rs2AntibanSettings.dynamicIntensity;
		previousTicksSinceLogin = AntibanPlugin.ticksSinceLogin;
		previousNoiseAmplitude = Rs2Antiban.mouseFatigue.noiseAmplitude;
		previousIntensity = Rs2Antiban.getActivityIntensity();

		Rs2AntibanSettings.simulateFatigue = false;
		Rs2AntibanSettings.simulateMistakes = false;
		AntibanPlugin.ticksSinceLogin = 0;
		// Without noise the fatigued base time is exactly MouseFatigue's formula.
		Rs2Antiban.mouseFatigue.noiseAmplitude = 0;
		Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
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
	public void fatigueRaisesTheNextBaseTimeWithTicksSinceLoginUpToTheCap()
	{
		Rs2AntibanSettings.simulateFatigue = true;
		NaturalMouse mouse = new NaturalMouse();

		assertBaseTime("LOW motion at login", mouse.getFactory(), NORMAL_GAMER_INITIAL_BASE);

		AntibanPlugin.ticksSinceLogin = 6000;
		long fatigued = fatiguedBase(NORMAL_GAMER_INITIAL_BASE, NORMAL_GAMER_MAX_BASE);
		assertEquals("fatigued LOW base after 6000 ticks (sanity check on the formula)", 180, fatigued);
		assertBaseTime("next LOW motion after ticksSinceLogin rose to 6000, intensity unchanged",
			mouse.getFactory(), fatigued);

		AntibanPlugin.ticksSinceLogin = 100_000;
		assertEquals("fatigued LOW base after 100000 ticks is the template cap",
			NORMAL_GAMER_MAX_BASE, fatiguedBase(NORMAL_GAMER_INITIAL_BASE, NORMAL_GAMER_MAX_BASE));
		assertBaseTime("next LOW motion after ticksSinceLogin rose to 100000, intensity unchanged",
			mouse.getFactory(), NORMAL_GAMER_MAX_BASE);
	}

	@Test(timeout = 30_000)
	public void fatigueRaisesTheNextBaseTimeOfTheFastGamerTemplateToo()
	{
		Rs2AntibanSettings.simulateFatigue = true;
		Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
		NaturalMouse mouse = new NaturalMouse();

		assertBaseTime("MODERATE motion at login", mouse.getFactory(), FAST_GAMER_INITIAL_BASE);

		AntibanPlugin.ticksSinceLogin = 6000;
		long fatigued = fatiguedBase(FAST_GAMER_INITIAL_BASE, FAST_GAMER_MAX_BASE);
		assertEquals("fatigued MODERATE base after 6000 ticks (sanity check on the formula)", 150, fatigued);
		assertBaseTime("next MODERATE motion after ticksSinceLogin rose to 6000, intensity unchanged",
			mouse.getFactory(), fatigued);

		AntibanPlugin.ticksSinceLogin = 100_000;
		assertBaseTime("next MODERATE motion after ticksSinceLogin rose to 100000, intensity unchanged",
			mouse.getFactory(), FAST_GAMER_MAX_BASE);
	}

	@Test(timeout = 30_000)
	public void turningMistakesOnGivesTheNextMotionTheTemplateOvershoots()
	{
		NaturalMouse mouse = new NaturalMouse();
		assertEquals("overshoots of a LOW motion with simulateMistakes off",
			0, overshoots(mouse.getFactory()));

		Rs2AntibanSettings.simulateMistakes = true;

		assertEquals("overshoots of the next LOW motion after simulateMistakes was turned on, intensity unchanged",
			NORMAL_GAMER_OVERSHOOTS, overshoots(mouse.getFactory()));
	}

	@Test(timeout = 30_000)
	public void turningMistakesOffRemovesOvershootsFromTheNextMotion()
	{
		Rs2AntibanSettings.simulateMistakes = true;
		NaturalMouse mouse = new NaturalMouse();
		assertEquals("overshoots of a LOW motion with simulateMistakes on",
			NORMAL_GAMER_OVERSHOOTS, overshoots(mouse.getFactory()));

		Rs2AntibanSettings.simulateMistakes = false;

		assertEquals("overshoots of the next LOW motion after simulateMistakes was turned off, intensity unchanged",
			0, overshoots(mouse.getFactory()));
	}

	@Test(timeout = 30_000)
	public void turningFatigueOffReturnsTheNextMotionToTheInitialBaseTime()
	{
		Rs2AntibanSettings.simulateFatigue = true;
		AntibanPlugin.ticksSinceLogin = 100_000;
		NaturalMouse mouse = new NaturalMouse();
		assertBaseTime("LOW motion with fatigue on after 100000 ticks", mouse.getFactory(), NORMAL_GAMER_MAX_BASE);

		Rs2AntibanSettings.simulateFatigue = false;

		assertBaseTime("next LOW motion after simulateFatigue was turned off, intensity unchanged",
			mouse.getFactory(), NORMAL_GAMER_INITIAL_BASE);
	}

	@Test(timeout = 30_000)
	public void changingTheIntensitySelectsTheMatchingTemplate()
	{
		Rs2AntibanSettings.simulateMistakes = true;
		NaturalMouse mouse = new NaturalMouse();

		assertTemplate("LOW", mouse, NORMAL_GAMER_INITIAL_BASE, NORMAL_GAMER_OVERSHOOTS);

		Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
		assertTemplate("EXTREME", mouse, SUPER_FAST_GAMER_INITIAL_BASE, SUPER_FAST_GAMER_OVERSHOOTS);

		Rs2Antiban.setActivityIntensity(ActivityIntensity.VERY_LOW);
		assertTemplate("VERY_LOW", mouse, AVERAGE_USER_BASE, AVERAGE_USER_OVERSHOOTS);

		Rs2Antiban.setActivityIntensity(ActivityIntensity.MODERATE);
		assertTemplate("MODERATE", mouse, FAST_GAMER_INITIAL_BASE, FAST_GAMER_OVERSHOOTS);

		Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
		assertTemplate("HIGH", mouse, FAST_GAMER_INITIAL_BASE, FAST_GAMER_OVERSHOOTS);

		Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
		assertTemplate("LOW again", mouse, NORMAL_GAMER_INITIAL_BASE, NORMAL_GAMER_OVERSHOOTS);
	}

	@Test(timeout = 30_000)
	public void repeatedCallsWithNothingChangedDescribeTheSameMotion()
	{
		Rs2AntibanSettings.simulateFatigue = true;
		Rs2AntibanSettings.simulateMistakes = true;
		AntibanPlugin.ticksSinceLogin = 6000;
		long fatigued = fatiguedBase(NORMAL_GAMER_INITIAL_BASE, NORMAL_GAMER_MAX_BASE);
		assertNotEquals("fatigue must move the base for this check to mean anything",
			NORMAL_GAMER_INITIAL_BASE, fatigued);
		NaturalMouse mouse = new NaturalMouse();

		for (int call = 1; call <= 3; call++)
		{
			MouseMotionFactory factory = mouse.getFactory();
			assertBaseTime("LOW motion on unchanged call " + call, factory, fatigued);
			assertEquals("overshoots of a LOW motion on unchanged call " + call,
				NORMAL_GAMER_OVERSHOOTS, overshoots(factory));
		}
	}

	private static void assertTemplate(String intensity, NaturalMouse mouse, long base, int overshoots)
	{
		MouseMotionFactory factory = mouse.getFactory();
		assertBaseTime("next motion after the intensity changed to " + intensity, factory, base);
		assertEquals("overshoots of the next motion after the intensity changed to " + intensity,
			overshoots, overshoots(factory));
	}

	private static long fatiguedBase(int initialBase, int maxBase)
	{
		return Rs2Antiban.mouseFatigue.calculateBaseTimeWithNoise(initialBase, maxBase);
	}

	private static int overshoots(MouseMotionFactory factory)
	{
		Pair<Flow, Long> flowWithTime = factory.getSpeedManager().getFlowWithTime(DISTANCE);
		return factory.getOvershootManager().getOvershoots(flowWithTime.x, flowWithTime.y, DISTANCE);
	}

	/**
	 * DefaultSpeedManager draws a time uniformly from [base, 2 * base - 1], then stretches it by
	 * (buckets + zero buckets) / buckets of the chosen flow so pauses do not eat into movement. So
	 * every sample from the right base lies in [base, stretched 2 * base - 1], and a sample outside
	 * that range proves the base is a different one.
	 */
	static void assertBaseTime(String motion, MouseMotionFactory factory, long base)
	{
		long smallest = Long.MAX_VALUE;
		long largest = Long.MIN_VALUE;
		String firstOutlier = null;

		for (int i = 0; i < SAMPLES; i++)
		{
			Pair<Flow, Long> flowWithTime = factory.getSpeedManager().getFlowWithTime(DISTANCE);
			double[] buckets = flowWithTime.x.getFlowCharacteristics();
			int zeroBuckets = 0;
			for (double bucket : buckets)
			{
				if (Math.abs(bucket) < ZERO_BUCKET)
				{
					zeroBuckets++;
				}
			}
			double stretch = (buckets.length + zeroBuckets) / (double) buckets.length;
			long time = flowWithTime.y;

			// The slack covers the rounding of the stretch, not any difference in base.
			long highest = (long) Math.floor((2 * base - 1) * stretch + 1e-6);
			if ((time < base || time > highest) && firstOutlier == null)
			{
				firstOutlier = time + " ms (allowed " + base + " to " + highest + ")";
			}

			long unstretched = Math.round(time / stretch);
			smallest = Math.min(smallest, unstretched);
			largest = Math.max(largest, unstretched);
		}

		if (firstOutlier != null)
		{
			fail(motion + ": expected base movement time " + base + " ms, but sampled time " + firstOutlier
				+ " is impossible for that base; without pause stretching the " + SAMPLES + " samples ranged "
				+ smallest + " to " + largest + " ms, so the motion is built from stale settings");
		}
	}
}
