package net.runelite.client.plugins.microbot.agentserver.handler;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class GrandExchangeHandlerTest {

	@Test(expected = AgentHandler.BadRequestException.class)
	public void fractionalPriceIsRejected() {
		GrandExchangeHandler.requirePositiveWholeNumber(Map.of("price", 1.9), "price");
	}

	@Test(expected = AgentHandler.BadRequestException.class)
	public void zeroIsRejected() {
		GrandExchangeHandler.requirePositiveWholeNumber(Map.of("quantity", 0.0), "quantity");
	}

	@Test(expected = AgentHandler.BadRequestException.class)
	public void missingValueIsRejected() {
		GrandExchangeHandler.requirePositiveWholeNumber(Map.of(), "quantity");
	}

	@Test
	public void wholeNumbersFromJsonOrTextAreAccepted() {
		assertEquals(72, GrandExchangeHandler.requirePositiveWholeNumber(Map.of("price", 72.0), "price"));
		assertEquals(505, GrandExchangeHandler.requirePositiveWholeNumber(Map.of("quantity", "505"), "quantity"));
	}
}
