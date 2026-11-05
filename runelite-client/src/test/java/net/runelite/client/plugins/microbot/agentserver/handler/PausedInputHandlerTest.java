package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.ScriptPauseController;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

public class PausedInputHandlerTest
{
	private ByteArrayOutputStream responseBody;

	@Before
	public void setUp() throws Exception
	{
		AgentHandler.setTokenSupplier(null);
		ScriptPauseController.setPaused(false).get(2, java.util.concurrent.TimeUnit.SECONDS);
		responseBody = new ByteArrayOutputStream();
	}

	@After
	public void tearDown() throws Exception
	{
		ScriptPauseController.setPaused(false).get(2, java.util.concurrent.TimeUnit.SECONDS);
		AgentHandler.setTokenSupplier(null);
	}

	@Test
	public void widgetInvokeReportsPauseInsteadOfClaimingItWasInvoked() throws Exception
	{
		ScriptPauseController.setPaused(true).get(2, java.util.concurrent.TimeUnit.SECONDS);
		HttpExchange exchange = exchange("/widgets/invoke", "{\"groupId\":1,\"childId\":2}");

		new WidgetInvokeHandler(new Gson()).handle(exchange);

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertTrue(response().contains("\"invoked\":false"));
		assertTrue(response().contains("\"reason\":\"Scripts are paused\""));
	}

	@Test
	public void keyboardHandlerReportsPauseWithoutEmittingKeys() throws Exception
	{
		ScriptPauseController.setPaused(true).get(2, java.util.concurrent.TimeUnit.SECONDS);
		HttpExchange exchange = exchange("/keyboard", "{\"text\":\"hello\"}");
		try (MockedStatic<Rs2Keyboard> keyboard = mockStatic(Rs2Keyboard.class))
		{
			new KeyboardHandler(new Gson()).handle(exchange);

			keyboard.verify(() -> Rs2Keyboard.typeString("hello"), never());
		}

		verify(exchange).sendResponseHeaders(eq(200), anyLong());
		assertTrue(response().contains("\"success\":false"));
		assertTrue(response().contains("\"reason\":\"Scripts are paused\""));
	}

	private HttpExchange exchange(String path, String body)
	{
		HttpExchange exchange = mock(HttpExchange.class);
		Headers requestHeaders = new Headers();
		requestHeaders.add("Host", "127.0.0.1:8081");
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getRequestURI()).thenReturn(URI.create(path));
		when(exchange.getRequestMethod()).thenReturn("POST");
		when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		when(exchange.getResponseBody()).thenReturn(responseBody);
		return exchange;
	}

	private String response()
	{
		return responseBody.toString(StandardCharsets.UTF_8);
	}
}
