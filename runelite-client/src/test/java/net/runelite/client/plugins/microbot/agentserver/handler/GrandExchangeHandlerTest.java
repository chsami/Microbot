package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.api.Client;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GrandExchangeHandlerTest {

	private MockedStatic<Microbot> microbot;
	private MockedStatic<Rs2GrandExchange> grandExchange;
	private MockedStatic<Rs2Inventory> inventory;
	private ByteArrayOutputStream responseBody;

	@Before
	public void setUp() {
		Client client = mock(Client.class);
		when(client.getGrandExchangeOffers()).thenReturn(new GrandExchangeOffer[0]);
		microbot = Mockito.mockStatic(Microbot.class);
		microbot.when(Microbot::getClient).thenReturn(client);
		grandExchange = Mockito.mockStatic(Rs2GrandExchange.class);
		inventory = Mockito.mockStatic(Rs2Inventory.class);
		responseBody = new ByteArrayOutputStream();
	}

	@After
	public void tearDown() {
		inventory.close();
		grandExchange.close();
		microbot.close();
	}

	@Test
	public void buyAwayFromTheExchangeIsRefusedWithoutWalking() throws IOException {
		HttpExchange exchange = post("/ge/buy", "{\"name\":\"Gold bar\",\"quantity\":10,\"price\":150}");

		new GrandExchangeHandler(new Gson()).handleRequest(exchange);

		verify(exchange).sendResponseHeaders(eq(409), anyLong());
		assertTrue(responseBody.toString(StandardCharsets.UTF_8).contains("POST /walk"));
		grandExchange.verify(() -> Rs2GrandExchange.processOffer(any()), never());
	}

	@Test
	public void buyAtTheOpenExchangePlacesTheOffer() throws IOException {
		grandExchange.when(Rs2GrandExchange::isOpen).thenReturn(true);
		grandExchange.when(() -> Rs2GrandExchange.processOffer(any())).thenReturn(true);
		HttpExchange exchange = post("/ge/buy", "{\"name\":\"Gold bar\",\"quantity\":10,\"price\":150}");

		new GrandExchangeHandler(new Gson()).handleRequest(exchange);

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		grandExchange.verify(() -> Rs2GrandExchange.processOffer(any()));
	}

	@Test
	public void collectWithAFullInventoryIsRefusedInsteadOfBanking() throws IOException {
		grandExchange.when(Rs2GrandExchange::isOpen).thenReturn(true);
		inventory.when(Rs2Inventory::isFull).thenReturn(true);
		HttpExchange exchange = post("/ge/collect", "{\"toBank\":true}");

		new GrandExchangeHandler(new Gson()).handleRequest(exchange);

		verify(exchange).sendResponseHeaders(eq(409), anyLong());
		grandExchange.verify(() -> Rs2GrandExchange.collectAll(anyBoolean()), never());
	}

	@Test
	public void abortWithAFullInventoryIsRefused() throws IOException {
		grandExchange.when(Rs2GrandExchange::isOpen).thenReturn(true);
		inventory.when(Rs2Inventory::isFull).thenReturn(true);
		HttpExchange exchange = post("/ge/abort", "{\"name\":\"Gold bar\"}");

		new GrandExchangeHandler(new Gson()).handleRequest(exchange);

		verify(exchange).sendResponseHeaders(eq(409), anyLong());
		grandExchange.verify(() -> Rs2GrandExchange.abortOffer(any(), anyBoolean()), never());
	}

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

	private HttpExchange post(String path, String json) {
		HttpExchange exchange = mock(HttpExchange.class);
		when(exchange.getRequestMethod()).thenReturn("POST");
		when(exchange.getRequestURI()).thenReturn(URI.create(path));
		when(exchange.getRequestHeaders()).thenReturn(new Headers());
		when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getResponseBody()).thenReturn(responseBody);
		return exchange;
	}
}
