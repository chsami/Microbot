package net.runelite.client.plugins.microbot.util.dialogues;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2DialoguePendingOptionListTest
{
	@Test
	public void freshOptionsAreNotPending()
	{
		assertFalse(Rs2Dialogue.isPendingOptionList(Arrays.asList("What seems to be the problem?", "Bye.")));
	}

	@Test
	public void questHelperHighlightedOptionsAreNotPending()
	{
		assertFalse(Rs2Dialogue.isPendingOptionList(Arrays.asList("[1] Yes.", "No.")));
	}

	@Test
	public void pressedOptionPlaceholderIsPending()
	{
		assertTrue(Rs2Dialogue.isPendingOptionList(Arrays.asList("Please wait...", "Bye.")));
	}

	@Test
	public void placeholderWithColourTagsIsPending()
	{
		assertTrue(Rs2Dialogue.isPendingOptionList(Arrays.asList("Yes.", "<col=ffffff>Please wait...</col>")));
	}

	@Test
	public void emptyOrMissingListIsPending()
	{
		assertTrue(Rs2Dialogue.isPendingOptionList(Collections.emptyList()));
		assertTrue(Rs2Dialogue.isPendingOptionList(null));
	}

	@Test
	public void nullOptionTextIsIgnored()
	{
		assertFalse(Rs2Dialogue.isPendingOptionList(Arrays.asList(null, "Bye.")));
	}
}
