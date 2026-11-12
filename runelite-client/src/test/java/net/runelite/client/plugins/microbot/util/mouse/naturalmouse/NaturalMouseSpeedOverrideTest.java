package net.runelite.client.plugins.microbot.util.mouse.naturalmouse;

import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.api.SpeedManager;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.support.DefaultSpeedManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class NaturalMouseSpeedOverrideTest
{
	private boolean previousSimulateFatigue;
	private boolean previousSimulateMistakes;
	private boolean previousDynamicIntensity;
	private int previousTicksSinceLogin;
	private ActivityIntensity previousIntensity;

	@Before
	public void before()
	{
		previousSimulateFatigue = Rs2AntibanSettings.simulateFatigue;
		previousSimulateMistakes = Rs2AntibanSettings.simulateMistakes;
		previousDynamicIntensity = Rs2AntibanSettings.dynamicIntensity;
		previousTicksSinceLogin = AntibanPlugin.ticksSinceLogin;
		previousIntensity = Rs2Antiban.getActivityIntensity();

		Rs2AntibanSettings.simulateFatigue = true;
		Rs2AntibanSettings.simulateMistakes = false;
		AntibanPlugin.ticksSinceLogin = 0;
		Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
	}

	@After
	public void after()
	{
		Rs2AntibanSettings.simulateFatigue = previousSimulateFatigue;
		Rs2AntibanSettings.simulateMistakes = previousSimulateMistakes;
		AntibanPlugin.ticksSinceLogin = previousTicksSinceLogin;
		Rs2Antiban.setActivityIntensity(previousIntensity);
		Rs2AntibanSettings.dynamicIntensity = previousDynamicIntensity;
	}

	@Test
	public void aPluginSpeedOverrideLastsWhileTheSettingsStayTheSame()
	{
		NaturalMouse mouse = new NaturalMouse();
		SpeedManager built = mouse.getFactory().getSpeedManager();
		assertSame(built, mouse.getFactory().getSpeedManager());

		SpeedManager fast = new DefaultSpeedManager();
		mouse.nature.setSpeedManager(fast);
		AntibanPlugin.ticksSinceLogin = 150;
		assertSame(fast, mouse.getFactory().getSpeedManager());
	}

	@Test
	public void aSettingsChangeRebuildsOverAPluginSpeedOverride()
	{
		NaturalMouse mouse = new NaturalMouse();
		mouse.getFactory();
		SpeedManager fast = new DefaultSpeedManager();
		mouse.nature.setSpeedManager(fast);

		Rs2AntibanSettings.simulateMistakes = true;
		assertNotSame(fast, mouse.getFactory().getSpeedManager());
	}
}
