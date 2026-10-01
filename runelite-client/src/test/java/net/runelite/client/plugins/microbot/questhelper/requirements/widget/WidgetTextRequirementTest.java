package net.runelite.client.plugins.microbot.questhelper.requirements.widget;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class WidgetTextRequirementTest
{
	private Client client;
	private Widget widget;

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		widget = mock(Widget.class);
		when(client.getWidget(1, 2)).thenReturn(widget);
		when(widget.getText()).thenReturn("<macro=quest_reward>");
		when(client.macroExpand("<macro=quest_reward>")).thenReturn("<str>As a reward he now lets me use his high quality range");
	}

	@Test
	public void expandsMacroAndPreservesFormattingTags()
	{
		assertTrue(new WidgetTextRequirement(1, 2, "<str>As a reward").check(client));
	}

	@Test
	public void rejectsNonMatchingExpandedText()
	{
		assertFalse(new WidgetTextRequirement(1, 2, "not the quest reward").check(client));
	}

	@Test
	public void ignoresHiddenWidgetWithoutExpandingText()
	{
		when(widget.isHidden()).thenReturn(true);
		assertFalse(new WidgetTextRequirement(1, 2, "As a reward").check(client));
		verify(client, never()).macroExpand(anyString());
	}

	@Test
	public void missingWidgetDoesNotMatch()
	{
		when(client.getWidget(1, 2)).thenReturn(null);
		assertFalse(new WidgetTextRequirement(1, 2, "As a reward").check(client));
	}

	@Test
	public void expandsChildWidgetText()
	{
		Widget child = mock(Widget.class);
		when(widget.getText()).thenReturn("");
		when(client.macroExpand("")).thenReturn("");
		when(widget.getStaticChildren()).thenReturn(new Widget[]{child});
		when(child.getNestedChildren()).thenReturn(new Widget[0]);
		when(child.getText()).thenReturn("<macro=quest_reward>");
		assertTrue(new WidgetTextRequirement(1, 2, true, "<str>As a reward").check(client));
	}
}
