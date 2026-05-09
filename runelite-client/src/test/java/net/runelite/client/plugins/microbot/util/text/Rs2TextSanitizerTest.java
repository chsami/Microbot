package net.runelite.client.plugins.microbot.util.text;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class Rs2TextSanitizerTest
{
	@Test
	public void stripsTagsAndDecodesEntities()
	{
		String raw = "<col=ff0000>Zeah</col>&nbsp;(&#39;Test&#39;)&#160;and&#x2019;more";
		String s = Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(raw).replace('\u00A0', ' ');
		assertEquals("Zeah ('Test') and'more", s);
	}

	@Test
	public void dropsDanglingLtAndTrims()
	{
		String raw = "  <col=00ff00>Kourend<  ";
		assertEquals("Kourend", Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(raw));
	}

	@Test
	public void handlesNumericCodePoints()
	{
		String raw = "Morytania&#8217;s&nbsp;End";
		assertEquals("Morytania's End", Rs2TextSanitizer.sanitizeLeaguesLockedRegionName(raw));
	}

	@Test
	public void parsesItemNameSuffix()
	{
		Optional<Rs2TextSanitizer.ItemNameWithSuffix> superAttack = Rs2TextSanitizer.parseItemNameSuffix("Super attack (4)");
		assertEquals("Super attack", superAttack.get().getBaseName());
		assertEquals(4, superAttack.get().getSuffix().getAsInt());
		Optional<Rs2TextSanitizer.ItemNameWithSuffix> oakLogs = Rs2TextSanitizer.parseItemNameSuffix("Oak logs");
		assertEquals("Oak logs", oakLogs.get().getBaseName());
		assertFalse(oakLogs.get().getSuffix().isPresent());
		Optional<Rs2TextSanitizer.ItemNameWithSuffix> prayerPot = Rs2TextSanitizer.parseItemNameSuffix("<col=ff0>Prayer potion (1)</col>");
		assertEquals("Prayer potion", prayerPot.get().getBaseName());
		assertEquals(1, prayerPot.get().getSuffix().getAsInt());
	}

	@Test
	public void sanitizeWidgetMultilineTextRemovesTagsAndBr()
	{
		String raw = "<col=ff0>Hello</col><br>World&nbsp;!";
		assertEquals("Hello World !", Rs2TextSanitizer.sanitizeWidgetMultilineText(raw).replaceAll("\\s+", " ").trim());
	}
}

