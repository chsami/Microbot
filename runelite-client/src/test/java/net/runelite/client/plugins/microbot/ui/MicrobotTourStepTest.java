package net.runelite.client.plugins.microbot.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class MicrobotTourStepTest
{
	@Test
	public void everyStepHasCompactPlainText()
	{
		for (MicrobotTourStep step : MicrobotTourStep.values())
		{
			assertFalse(step.name(), step.getTitle().isBlank());
			assertFalse(step.name(), step.getBody().isBlank());
			assertTrue(step.name() + " is " + step.getBody().length(), step.getBody().length() <= 260);
			assertFalse(step.name(), step.getBody().matches(".*[<>&].*"));
			assertFalse(step.name(), step.getTitle().matches(".*[<>&].*"));
			assertEquals(step.getBody(), MicrobotTourCallout.escape(step.getBody()));
		}
	}

	@Test
	public void onlyCloudIsNew()
	{
		for (MicrobotTourStep step : MicrobotTourStep.values())
		{
			assertEquals(step.name(), step == MicrobotTourStep.MICROBOT_CLOUD, step.isNew());
		}
	}

	@Test
	public void onlyWelcomeHasNoTarget()
	{
		for (MicrobotTourStep step : MicrobotTourStep.values())
		{
			assertEquals(step.name(), step != MicrobotTourStep.WELCOME, step.isRequiresTarget());
		}
	}

	@Test
	public void helpIsLastAndMentionsReplay()
	{
		MicrobotTourStep[] steps = MicrobotTourStep.values();
		assertEquals(MicrobotTourStep.WELCOME, steps[0]);
		assertEquals(MicrobotTourStep.HELP, steps[steps.length - 1]);
		assertTrue(MicrobotTourStep.HELP.getBody().contains("replay this tour"));
	}

	@Test
	public void reportProblemComesRightBeforeHelp()
	{
		MicrobotTourStep[] steps = MicrobotTourStep.values();
		assertEquals(MicrobotTourStep.REPORT_PROBLEM, steps[steps.length - 2]);
		assertTrue(MicrobotTourStep.REPORT_PROBLEM.getBody().contains("Report a problem"));
		assertTrue(MicrobotTourStep.REPORT_PROBLEM.getBody().contains("My reports"));
	}

	@Test
	public void escapeHandlesHtmlCharacters()
	{
		assertEquals("a &lt;b&gt; &amp; c", MicrobotTourCallout.escape("a <b> & c"));
	}
}
