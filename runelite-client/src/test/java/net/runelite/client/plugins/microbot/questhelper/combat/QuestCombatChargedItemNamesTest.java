package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuestCombatChargedItemNamesTest
{
	private static final class Item
	{
		final int id;
		final String name;

		Item(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}

	@Test
	public void stripsChargeAndDose()
	{
		assertEquals("ring of wealth", ChargedItemNames.baseName("Ring of wealth (4)"));
		assertEquals("prayer potion", ChargedItemNames.baseName("Prayer potion(3)"));
		assertEquals("amulet of glory", ChargedItemNames.baseName("Amulet of glory (t4)"));
		assertEquals("ring of wealth", ChargedItemNames.baseName("Ring of wealth"));
		assertEquals("", ChargedItemNames.baseName(null));
	}

	@Test
	public void sameFamilyIgnoresCharge()
	{
		assertTrue(ChargedItemNames.sameFamily("Ring of wealth (4)", "Ring of wealth (2)"));
		assertTrue(ChargedItemNames.sameFamily("Ring of wealth (4)", "Ring of wealth"));
		assertFalse(ChargedItemNames.sameFamily("Ring of wealth (4)", "Ring of dueling(8)"));
	}

	@Test
	public void picksExactThenHighestChargeThenBase()
	{
		Item w4 = new Item(11980, "Ring of wealth (4)");
		Item w2 = new Item(11984, "Ring of wealth (2)");
		Item w5 = new Item(11982, "Ring of wealth (5)");
		Item base = new Item(2572, "Ring of wealth");
		List<Item> all = Arrays.asList(w2, base, w5, w4);
		assertEquals(w4, ChargedItemNames.pick(all, i -> i.id, i -> i.name, 11980, "Ring of wealth (4)"));
		assertEquals(w5, ChargedItemNames.pick(Arrays.asList(w2, base, w5), i -> i.id, i -> i.name, 11980, "Ring of wealth (4)"));
		assertEquals(base, ChargedItemNames.pick(Collections.singletonList(base), i -> i.id, i -> i.name, 11980, "Ring of wealth (4)"));
		assertNull(ChargedItemNames.pick(Collections.singletonList(new Item(1, "Ring of dueling(8)")),
			i -> i.id, i -> i.name, 11980, "Ring of wealth (4)"));
	}
}
