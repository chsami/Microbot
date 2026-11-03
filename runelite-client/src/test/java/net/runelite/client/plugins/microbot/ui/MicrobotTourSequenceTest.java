package net.runelite.client.plugins.microbot.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MicrobotTourSequenceTest
{
	private static MicrobotTourSequence all()
	{
		return new MicrobotTourSequence(Arrays.asList(MicrobotTourStep.values()), step -> true);
	}

	private static MicrobotTourSequence withoutHelp()
	{
		return new MicrobotTourSequence(Arrays.asList(MicrobotTourStep.values()), step -> step != MicrobotTourStep.HELP && step != MicrobotTourStep.REPORT_PROBLEM);
	}

	@Test
	public void walksTheStepsInOrder()
	{
		MicrobotTourSequence sequence = all();
		assertEquals(Optional.of(MicrobotTourStep.WELCOME), sequence.first());
		MicrobotTourStep[] steps = MicrobotTourStep.values();
		for (int i = 0; i < steps.length - 1; i++)
		{
			assertEquals(Optional.of(steps[i + 1]), sequence.next(steps[i]));
			assertEquals(Optional.of(steps[i]), sequence.previous(steps[i + 1]));
		}
		assertEquals(Optional.empty(), sequence.previous(MicrobotTourStep.WELCOME));
		assertEquals(Optional.empty(), sequence.next(MicrobotTourStep.HELP));
		assertEquals(11, sequence.total());
		assertEquals(1, sequence.position(MicrobotTourStep.WELCOME));
		assertEquals(10, sequence.position(MicrobotTourStep.REPORT_PROBLEM));
		assertEquals(11, sequence.position(MicrobotTourStep.HELP));
		assertTrue(sequence.isLast(MicrobotTourStep.HELP));
		assertFalse(sequence.isLast(MicrobotTourStep.MICROBOT_CLOUD));
	}

	@Test
	public void unavailableStepsAreSkippedInBothDirections()
	{
		MicrobotTourSequence sequence = new MicrobotTourSequence(Arrays.asList(MicrobotTourStep.values()),
			step -> step != MicrobotTourStep.INSTALLED);
		assertEquals(Optional.of(MicrobotTourStep.PLUGIN_HUB), sequence.next(MicrobotTourStep.COMMUNITY_PLUGINS));
		assertEquals(Optional.of(MicrobotTourStep.COMMUNITY_PLUGINS), sequence.previous(MicrobotTourStep.PLUGIN_HUB));
	}

	@Test
	public void countsOnlyAvailableSteps()
	{
		MicrobotTourSequence sequence = withoutHelp();
		assertEquals(6, sequence.position(MicrobotTourStep.PROFILES));
		assertEquals(9, sequence.total());
		assertTrue(sequence.isLast(MicrobotTourStep.MICROBOT_CLOUD));
	}

	@Test
	public void markSkippedMovesOnAndShrinksTheTotal()
	{
		MicrobotTourSequence sequence = all();
		sequence.markSkipped(MicrobotTourStep.INSTALLED);
		assertEquals(Optional.of(MicrobotTourStep.PLUGIN_HUB), sequence.next(MicrobotTourStep.COMMUNITY_PLUGINS));
		assertEquals(10, sequence.total());
		assertEquals(4, sequence.position(MicrobotTourStep.PLUGIN_HUB));
	}

	@Test
	public void skippedBugButtonStepsMakeCloudTheLastStep()
	{
		MicrobotTourSequence sequence = all();
		sequence.markSkipped(MicrobotTourStep.REPORT_PROBLEM);
		sequence.markSkipped(MicrobotTourStep.HELP);
		assertTrue(sequence.isLast(MicrobotTourStep.MICROBOT_CLOUD));
		assertEquals(9, sequence.position(MicrobotTourStep.MICROBOT_CLOUD));
		assertEquals(9, sequence.total());
	}

	@Test
	public void welcomeOnlyIsOneStepWithDone()
	{
		MicrobotTourSequence sequence = new MicrobotTourSequence(Arrays.asList(MicrobotTourStep.values()),
			step -> step == MicrobotTourStep.WELCOME);
		assertEquals(Optional.of(MicrobotTourStep.WELCOME), sequence.first());
		assertEquals(1, sequence.position(MicrobotTourStep.WELCOME));
		assertEquals(1, sequence.total());
		assertTrue(sequence.isLast(MicrobotTourStep.WELCOME));
		assertEquals(Optional.empty(), sequence.previous(MicrobotTourStep.WELCOME));
	}

	@Test
	public void emptySequenceHasNoFirstStep()
	{
		MicrobotTourSequence sequence = new MicrobotTourSequence(Collections.emptyList(), step -> true);
		assertEquals(Optional.empty(), sequence.first());
		assertEquals(0, sequence.total());
	}
}
