package net.runelite.client.plugins.microbot.inventorysetups;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class InventorySetupUtilitiesTest
{
	@Test
	public void plainAmountIsParsed()
	{
		assertEquals(250, InventorySetupUtilities.parseTextInputAmount("250"));
	}

	@Test
	public void suffixesMultiplyTheAmount()
	{
		assertEquals(5_000, InventorySetupUtilities.parseTextInputAmount("5k"));
		assertEquals(5_000, InventorySetupUtilities.parseTextInputAmount("5K"));
		assertEquals(2_000_000, InventorySetupUtilities.parseTextInputAmount("2m"));
		assertEquals(1_000_000_000, InventorySetupUtilities.parseTextInputAmount("1b"));
	}

	@Test
	public void amountIsCappedAtMaxInt()
	{
		assertEquals(Integer.MAX_VALUE, InventorySetupUtilities.parseTextInputAmount("3b"));
		assertEquals(Integer.MAX_VALUE, InventorySetupUtilities.parseTextInputAmount("9999999999"));
		assertEquals(Integer.MAX_VALUE, InventorySetupUtilities.parseTextInputAmount("12345678901"));
	}

	@Test
	public void amountIsAtLeastOne()
	{
		assertEquals(1, InventorySetupUtilities.parseTextInputAmount("0"));
		assertEquals(1, InventorySetupUtilities.parseTextInputAmount("0k"));
	}

	@Test
	public void freeNameIsKept()
	{
		assertEquals("Vorkath", InventorySetupUtilities.findNewName("Vorkath", Collections.emptySet()));
	}

	@Test
	public void duplicateNamesGetTheNextFreeNumber()
	{
		Set<String> taken = new HashSet<>(Arrays.asList("Vorkath", "Vorkath1"));

		assertEquals("Vorkath2", InventorySetupUtilities.findNewName("Vorkath", taken));
	}

	@Test
	public void emptyNameGetsANumber()
	{
		assertEquals("1", InventorySetupUtilities.findNewName("", Collections.emptySet()));
	}

	@Test
	public void longNamesAreCutToTheLimitEvenWithANumber()
	{
		String fifty = "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghij";
		String sixty = fifty + "0123456789";

		assertEquals(fifty, InventorySetupUtilities.findNewName(sixty, Collections.emptySet()));
		String renamed = InventorySetupUtilities.findNewName(sixty, Collections.singleton(fifty));
		assertEquals(MInventorySetupsPlugin.MAX_SETUP_NAME_LENGTH, renamed.length());
		assertEquals(fifty.substring(0, 49) + "1", renamed);
	}
}
