package net.runelite.client.plugins.microbot.ddchopper;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;

/** The config panel truncates the label column when names or dropdown options are wide. */
public class ConfigLabelsTest {
	@Test
	public void itemNamesAreShortAndSectioned() {
		for (Method m : DDChopperConfig.class.getDeclaredMethods()) {
			ConfigItem item = m.getAnnotation(ConfigItem.class);
			if (item == null) continue;
			assertTrue(item.name() + " too long", item.name().length() <= 20);
			assertTrue(item.name() + " has no section", !item.section().isEmpty());
		}
	}

	@Test
	public void dropdownOptionsAreShort() {
		for (Spot s : Spot.values()) assertTrue(s.toString(), s.toString().length() <= 22);
		for (TreeChoice c : TreeChoice.values()) assertTrue(c.toString(), c.toString().length() <= 12);
		for (AxeChoice c : AxeChoice.values()) assertTrue(c.toString(), c.toString().length() <= 12);
		for (GoalType c : GoalType.values()) assertTrue(c.toString(), c.toString().length() <= 14);
		for (ChopMode c : ChopMode.values()) assertTrue(c.toString(), c.toString().length() <= 12);
	}
}
