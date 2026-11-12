package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AgentHandlerClientDisconnectTest {

	private static final String LOCALIZED_ABORT = "De software op uw hostcomputer heeft een verbinding verbroken";

	private static final class PingHandler extends AgentHandler {
		PingHandler() {
			super(new Gson());
		}

		@Override
		public String getPath() {
			return "/ping";
		}

		@Override
		protected void handleRequest(HttpExchange exchange) throws IOException {
			sendJson(exchange, 200, Collections.singletonMap("ok", true));
		}
	}

	@After
	public void tearDown() {
		AgentHandler.setTokenSupplier(null);
	}

	@Test
	public void bodyWriteFailureIsTreatedAsDisconnectWithoutErrorResponse() throws Exception {
		AgentHandler.setTokenSupplier(null);
		HttpExchange exchange = mock(HttpExchange.class);
		Headers requestHeaders = new Headers();
		requestHeaders.add("Host", "127.0.0.1:8081");
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getRequestURI()).thenReturn(URI.create("/ping"));
		when(exchange.getResponseBody()).thenReturn(new OutputStream() {
			@Override
			public void write(int b) throws IOException {
				throw new IOException(LOCALIZED_ABORT);
			}

			@Override
			public void write(byte[] b, int off, int len) throws IOException {
				throw new IOException(LOCALIZED_ABORT);
			}
		});

		new PingHandler().handle(exchange);

		verify(exchange, times(1)).sendResponseHeaders(eq(200), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(500), anyLong());
	}

	@Test
	public void closedStreamOnResponseHeadersIsTreatedAsDisconnectWithoutErrorResponse() throws Exception {
		AgentHandler.setTokenSupplier(null);
		HttpExchange exchange = mock(HttpExchange.class);
		Headers requestHeaders = new Headers();
		requestHeaders.add("Host", "127.0.0.1:8081");
		when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
		when(exchange.getResponseHeaders()).thenReturn(new Headers());
		when(exchange.getRequestURI()).thenReturn(URI.create("/ping"));
		doThrow(new IOException("stream is closed")).when(exchange).sendResponseHeaders(anyInt(), anyLong());

		new PingHandler().handle(exchange);

		verify(exchange, times(1)).sendResponseHeaders(eq(200), anyLong());
		verify(exchange, never()).sendResponseHeaders(eq(500), anyLong());
		verify(exchange, never()).getResponseBody();
	}

	@Test
	public void recognisesClosedResponseStream() {
		assertTrue(AgentHandler.isClientDisconnect(new IOException("stream is closed")));
	}

	@Test
	public void recognisesWindowsDisconnectMessages() {
		assertTrue(AgentHandler.isClientDisconnect(new IOException("An established connection was aborted by the software in your host machine")));
		assertTrue(AgentHandler.isClientDisconnect(new IOException("An existing connection was forcibly closed by the remote host")));
		assertTrue(AgentHandler.isClientDisconnect(new AgentHandler.ClientDisconnectException(new IOException(LOCALIZED_ABORT))));
		assertTrue(AgentHandler.isClientDisconnect(new RuntimeException(new AgentHandler.ClientDisconnectException(new IOException(LOCALIZED_ABORT)))));
	}

	@Test
	public void unrelatedIoFailureIsNotADisconnect() {
		assertFalse(AgentHandler.isClientDisconnect(new IOException(LOCALIZED_ABORT)));
		assertFalse(AgentHandler.isClientDisconnect(new IOException("No space left on device")));
		assertFalse(AgentHandler.isClientDisconnect(new IllegalStateException("broken pipe")));
	}
}
