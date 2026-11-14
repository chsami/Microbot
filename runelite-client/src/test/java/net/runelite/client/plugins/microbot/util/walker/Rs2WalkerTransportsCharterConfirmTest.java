package net.runelite.client.plugins.microbot.util.walker;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class Rs2WalkerTransportsCharterConfirmTest
{
	@Test
	public void plainYesIsPicked()
	{
		assertEquals(1, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("Yes", "No")));
	}

	@Test
	public void longerAffirmativeWordingIsPicked()
	{
		assertEquals(1, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("Yes, pay 480 coins.", "No")));
		assertEquals(1, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("Okay.", "Choose again.", "No.")));
		assertEquals(1, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("Ok", "No")));
	}

	@Test
	public void affirmativeIsPickedWhereverItSits()
	{
		assertEquals(2, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("No.", "Yes.")));
		assertEquals(3, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("Pay 200 coins", "No", "Okay")));
	}

	@Test
	public void colourTagsAndCaseAreIgnored()
	{
		assertEquals(2, Rs2WalkerTransports.charterConfirmOptionIndex(
			Arrays.asList("<col=ff0000>No</col>", "<col=00ff00>YES</col>")));
	}

	@Test
	public void firstNonRefusalIsPickedWhenNothingIsAffirmative()
	{
		assertEquals(1, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("Pay 200 coins", "No")));
		assertEquals(3, Rs2WalkerTransports.charterConfirmOptionIndex(
			Arrays.asList("No.", "Choose again.", "Sail to Musa Point", "Cancel")));
	}

	@Test
	public void refusalsOnlyOrNoOptionsPickNothing()
	{
		assertEquals(-1, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("No", "Choose again.", "Cancel")));
		assertEquals(-1, Rs2WalkerTransports.charterConfirmOptionIndex(Arrays.asList("", null)));
		assertEquals(-1, Rs2WalkerTransports.charterConfirmOptionIndex(Collections.emptyList()));
		assertEquals(-1, Rs2WalkerTransports.charterConfirmOptionIndex(null));
	}
}
