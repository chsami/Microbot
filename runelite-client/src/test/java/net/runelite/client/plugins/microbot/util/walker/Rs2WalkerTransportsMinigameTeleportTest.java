package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.widgets.Widget;
import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WalkerTransportsMinigameTeleportTest
{
	@Test
	public void missingSelectedWidgetDoesNotMatch()
	{
		assertFalse(Rs2WalkerTransports.widgetTextMatches(null, "barbarian assault"));
	}

	@Test
	public void nullWidgetTextDoesNotMatch()
	{
		assertFalse(Rs2WalkerTransports.widgetTextMatches(widgetWithText(null), "barbarian assault"));
	}

	@Test
	public void matchingTextIgnoresCase()
	{
		assertTrue(Rs2WalkerTransports.widgetTextMatches(widgetWithText("Barbarian Assault"), "barbarian assault"));
	}

	@Test
	public void differentTextDoesNotMatch()
	{
		assertFalse(Rs2WalkerTransports.widgetTextMatches(widgetWithText("Castle Wars"), "barbarian assault"));
	}

	@Test
	public void selectClicksInsideTheRowNotTheRestingPointer()
	{
		Rectangle list = new Rectangle(100, 200, 150, 120);
		assertEquals(new Rectangle(100, 230, 150, 15),
			Rs2WalkerTransports.visibleRowArea(new Rectangle(100, 230, 150, 15), list));
		// Partly scrolled off the bottom: only the visible part is clickable.
		assertEquals(new Rectangle(100, 310, 150, 10),
			Rs2WalkerTransports.visibleRowArea(new Rectangle(100, 310, 150, 15), list));
	}

	@Test
	public void rowScrolledOutOfViewFallsBackToTheList()
	{
		Rectangle list = new Rectangle(100, 200, 150, 120);
		assertEquals(list, Rs2WalkerTransports.visibleRowArea(new Rectangle(100, 400, 150, 15), list));
		assertEquals(list, Rs2WalkerTransports.visibleRowArea(null, list));
	}

	@Test
	public void noVisibleListMeansNoClick()
	{
		assertNull(Rs2WalkerTransports.visibleRowArea(new Rectangle(100, 230, 150, 15), null));
		assertNull(Rs2WalkerTransports.visibleRowArea(new Rectangle(100, 230, 150, 15), new Rectangle()));
	}

	private static Widget widgetWithText(String text)
	{
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn(text);
		return widget;
	}
}
