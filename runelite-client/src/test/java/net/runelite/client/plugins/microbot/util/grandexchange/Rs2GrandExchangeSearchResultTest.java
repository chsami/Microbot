package net.runelite.client.plugins.microbot.util.grandexchange;

import net.runelite.api.widgets.Widget;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2GrandExchangeSearchResultTest {
	private static Widget widget(String text) {
		Widget widget = mock(Widget.class);
		when(widget.getText()).thenReturn(text);
		return widget;
	}

	@Test
	public void returnsNullForNullChildren() {
		assertNull(Rs2GrandExchange.findSearchResult(null, "Coal", true));
	}

	@Test
	public void returnsNullForEmptyChildren() {
		assertNull(Rs2GrandExchange.findSearchResult(new Widget[0], "Coal", true));
	}

	@Test
	public void returnsNullWhenMatchIsFirstChild() {
		Widget[] children = {widget("Coal"), widget("")};
		assertNull(Rs2GrandExchange.findSearchResult(children, "Coal", true));
	}

	@Test
	public void skipsNullChildrenAndNullText() {
		Widget clickable = widget("");
		Widget[] children = {null, widget(null), clickable, widget("<col=ff9040>Coal</col>")};
		Pair<Widget, Integer> result = Rs2GrandExchange.findSearchResult(children, "coal", true);
		assertSame(clickable, result.getLeft());
		assertEquals(Integer.valueOf(2), result.getRight());
	}

	@Test
	public void returnsNullWhenPrecedingChildIsNull() {
		Widget[] children = {widget(""), null, widget("Coal")};
		assertNull(Rs2GrandExchange.findSearchResult(children, "Coal", true));
	}

	@Test
	public void skipsUnusableMatchForLaterUsableMatch() {
		Widget clickable = widget("");
		Widget[] children = {widget("Coal"), clickable, widget("Coal")};
		Pair<Widget, Integer> result = Rs2GrandExchange.findSearchResult(children, "Coal", true);
		assertSame(clickable, result.getLeft());
		assertEquals(Integer.valueOf(1), result.getRight());
	}

	@Test
	public void partialMatchUsesContainment() {
		Widget clickable = widget("");
		Widget[] children = {widget("Iron ore"), clickable, widget("Coal bag")};
		Pair<Widget, Integer> result = Rs2GrandExchange.findSearchResult(children, "coal", false);
		assertSame(clickable, result.getLeft());
		assertEquals(Integer.valueOf(1), result.getRight());
	}

	@Test
	public void exactMatchIgnoresPartial() {
		Widget[] children = {widget(""), widget("Coal bag")};
		assertNull(Rs2GrandExchange.findSearchResult(children, "Coal", true));
	}
}
