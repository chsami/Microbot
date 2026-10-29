package net.runelite.client.plugins.microbot.util.antiban;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import org.junit.After;
import org.junit.Test;

public class HumanMouseSpeedTest
{
	@After
	public void reset()
	{
		Rs2AntibanSettings.reset();
	}

	@Test
	public void theHumanSpeedAppliesOnlyWhenNothingNeedsTheFastMotion()
	{
		assertTrue(HumanMouseSpeed.applies(true, ActivityIntensity.LOW, false, false));
		assertTrue(HumanMouseSpeed.applies(true, null, false, false));
		assertFalse("switched off", HumanMouseSpeed.applies(false, ActivityIntensity.LOW, false, false));
		assertFalse("eating, prayer, a spam-click step", HumanMouseSpeed.applies(true, ActivityIntensity.LOW, false, true));
		assertFalse("a fight", HumanMouseSpeed.applies(true, ActivityIntensity.LOW, true, false));
		assertFalse(HumanMouseSpeed.applies(true, ActivityIntensity.HIGH, false, false));
		assertFalse(HumanMouseSpeed.applies(true, ActivityIntensity.EXTREME, false, false));
	}

	@Test
	public void timeCriticalStretchesNestAndEnd()
	{
		assertFalse(HumanMouseSpeed.isTimeCritical());
		HumanMouseSpeed.enterTimeCritical();
		HumanMouseSpeed.enterTimeCritical();
		HumanMouseSpeed.exitTimeCritical();
		assertTrue(HumanMouseSpeed.isTimeCritical());
		HumanMouseSpeed.exitTimeCritical();
		assertFalse(HumanMouseSpeed.isTimeCritical());
	}

	@Test
	public void aSavedZeroPercentChanceIsHonouredAsAZeroToZeroRange() throws Exception
	{
		Class<?> persistent = Class.forName(Rs2AntibanSettings.class.getName() + "$PersistentSettings");
		Constructor<?> constructor = persistent.getDeclaredConstructor();
		constructor.setAccessible(true);
		Object saved = constructor.newInstance();
		Field chance = persistent.getDeclaredField("moveMouseRandomlyChance");
		chance.setAccessible(true);
		chance.set(saved, 0.0);
		Method apply = Rs2AntibanSettings.class.getDeclaredMethod("apply", persistent);
		apply.setAccessible(true);
		apply.invoke(null, saved);
		assertEquals(0.0, Rs2AntibanSettings.moveMouseRandomlyChance, 0.0);
		assertEquals("the lower end follows the saved upper end", 0.0, Rs2AntibanSettings.moveMouseRandomlyChanceMin, 0.0);
		assertEquals(0.0, Rs2AntibanSettings.currentMoveMouseRandomlyChance(), 0.0);
	}
}
