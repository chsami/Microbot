package net.runelite.client.plugins.microbot.diagnostics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ScriptErrorReporterTest
{
	@PluginDescriptor(name = "Fake")
	static class FakePlugin extends Plugin
	{
	}

	private final LoggerContext context = new LoggerContext();
	private MicrobotApi api;
	private ClientSessionManager sessions;
	private ScriptErrorReporter reporter;

	@Before
	public void setUp()
	{
		api = mock(MicrobotApi.class);
		sessions = mock(ClientSessionManager.class);
		when(sessions.getMicrobotSessionId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
		MicrobotPluginManager pluginManager = mock(MicrobotPluginManager.class);
		when(pluginManager.getInstalledPlugins()).thenReturn(List.of(new FakePlugin()));
		when(pluginManager.getInstalledPluginVersion("FakePlugin")).thenReturn(Optional.of("1.2.3"));
		reporter = reporter(pluginManager, false);
	}

	private ScriptErrorReporter reporter(MicrobotPluginManager pluginManager, boolean disableTelemetry)
	{
		ScriptErrorReporter reporter = new ScriptErrorReporter(api, pluginManager, mock(ScheduledExecutorService.class), sessions, disableTelemetry);
		reporter.setContext(context);
		reporter.start();
		return reporter;
	}

	@After
	public void tearDown()
	{
		System.clearProperty("microbot.disableTelemetry");
	}

	private void log(Level level, String message, Throwable ex)
	{
		reporter.doAppend(new LoggingEvent("x", context.getLogger("some.Script"), level, message, ex, null));
	}

	private static RuntimeException boom()
	{
		return new IllegalStateException("bank closed at /home/alice/.runelite");
	}

	@Test
	public void groupsRepeatedErrorsAndAttributesPlugin()
	{
		for (int i = 0; i < 3; i++)
		{
			log(Level.ERROR, "loop failed", boom());
		}
		log(Level.ERROR, "loop failed", new NullPointerException());
		log(Level.WARN, "just a warning", boom());
		assertEquals(2, reporter.pendingCount());

		reporter.flush();
		ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
		verify(api).submitErrors(captor.capture(), any());
		JsonObject first = captor.getValue().getAsJsonArray("errors").get(0).getAsJsonObject();
		assertEquals(3, first.get("count").getAsInt());
		assertEquals("java.lang.IllegalStateException", first.get("exception").getAsString());
		assertEquals("bank closed at [path]", first.get("exceptionMessage").getAsString());
		assertEquals("FakePlugin", first.get("plugin").getAsString());
		assertEquals("1.2.3", first.get("pluginVersion").getAsString());
		assertEquals(0, reporter.pendingCount());
	}

	@Test
	public void sanitisesErrorsWithoutException()
	{
		log(Level.ERROR, "Failed to load /home/alice/.runelite/x.json for alice@example.com", null);
		reporter.flush();
		ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
		verify(api).submitErrors(captor.capture(), any());
		String payload = captor.getValue().toString();
		assertFalse(payload, payload.contains("alice"));
	}

	@Test
	public void groupsJdkThrownErrorsByCallerFrames()
	{
		for (int i = 0; i < 2; i++)
		{
			log(Level.ERROR, "loop", i == 0 ? indexError() : indexErrorElsewhere());
		}
		assertEquals(2, reporter.pendingCount());
	}

	private static RuntimeException indexError()
	{
		try
		{
			new java.util.ArrayList<>().get(1);
			return null;
		}
		catch (IndexOutOfBoundsException e)
		{
			return e;
		}
	}

	private static RuntimeException indexErrorElsewhere()
	{
		try
		{
			new java.util.ArrayList<>().get(2);
			return null;
		}
		catch (IndexOutOfBoundsException e)
		{
			return e;
		}
	}

	@Test
	public void keepsErrorsUntilServerSessionExists()
	{
		when(sessions.getMicrobotSessionId()).thenReturn(null);
		log(Level.ERROR, "loop failed", boom());
		reporter.flush();
		verify(api, never()).submitErrors(any(), any());
		assertEquals(1, reporter.pendingCount());

		when(sessions.getMicrobotSessionId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000002"));
		reporter.flush();
		ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
		verify(api).submitErrors(captor.capture(), any());
		assertEquals("00000000-0000-0000-0000-000000000002", captor.getValue().get("sessionId").getAsString());
		assertEquals(0, reporter.pendingCount());
	}

	@Test
	public void scrubsRememberedPlayerNameAfterLogout()
	{
		reporter.rememberPlayerName("Zezima\u00A0Two");
		reporter.rememberPlayerName(null);
		log(Level.ERROR, "Zezima Two could not bank", new IllegalStateException("Zezima\u00A0Two is dead"));
		reporter.flush();
		ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
		verify(api).submitErrors(captor.capture(), any());
		String payload = captor.getValue().toString();
		assertFalse(payload, payload.contains("Zezima"));
	}

	@Test
	public void requeuesRejectedBatch()
	{
		RuntimeException ex = boom();
		log(Level.ERROR, "loop failed", ex);
		reporter.flush();
		ArgumentCaptor<Runnable> onRejected = ArgumentCaptor.forClass(Runnable.class);
		verify(api).submitErrors(any(), onRejected.capture());
		assertEquals(0, reporter.pendingCount());

		log(Level.ERROR, "loop failed", ex);
		onRejected.getValue().run();
		assertEquals(1, reporter.pendingCount());

		reporter.flush();
		ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
		verify(api, org.mockito.Mockito.times(2)).submitErrors(captor.capture(), any());
		assertEquals(2, captor.getValue().getAsJsonArray("errors").get(0).getAsJsonObject().get("count").getAsInt());
	}

	@Test
	public void ignoresInterruptedWaits()
	{
		log(Level.ERROR, "AttackNpcScript", new RuntimeException("Interrupted waiting for client thread", new InterruptedException()));
		log(Level.ERROR, "Error displaying message {}:", new InterruptedException());
		assertEquals(0, reporter.pendingCount());
	}

	@Test
	public void groupsMessagesThatOnlyDifferInNumbers()
	{
		for (int i = 0; i < 50; i++)
		{
			log(Level.ERROR, "failed attempt " + i, null);
		}
		assertEquals(1, reporter.pendingCount());
	}

	@Test
	public void capsDistinctErrors()
	{
		for (int i = 0; i < ScriptErrorReporter.MAX_FINGERPRINTS + 10; i++)
		{
			log(Level.ERROR, "failed " + (char) ('a' + i % 26) + (char) ('a' + i / 26), null);
		}
		assertEquals(ScriptErrorReporter.MAX_FINGERPRINTS, reporter.pendingCount());
	}

	@Test
	public void sendsNothingWhenTelemetryDisabled()
	{
		System.setProperty("microbot.disableTelemetry", "true");
		log(Level.ERROR, "loop failed", boom());
		reporter.flush();
		assertEquals(0, reporter.pendingCount());
		verify(api, never()).submitErrors(any(), any());
	}

	@Test
	public void sendsNothingWithDisableTelemetryFlag()
	{
		reporter = reporter(mock(MicrobotPluginManager.class), true);
		log(Level.ERROR, "loop failed", boom());
		reporter.flush();
		assertEquals(0, reporter.pendingCount());
		verify(api, never()).submitErrors(any(), any());
	}
}
