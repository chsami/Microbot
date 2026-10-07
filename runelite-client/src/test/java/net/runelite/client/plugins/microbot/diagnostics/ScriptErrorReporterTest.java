package net.runelite.client.plugins.microbot.diagnostics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
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
	private ScriptErrorReporter reporter;

	@Before
	public void setUp()
	{
		api = mock(MicrobotApi.class);
		MicrobotPluginManager pluginManager = mock(MicrobotPluginManager.class);
		when(pluginManager.getInstalledPlugins()).thenReturn(List.of(new FakePlugin()));
		when(pluginManager.getInstalledPluginVersion("FakePlugin")).thenReturn(Optional.of("1.2.3"));
		reporter = reporter(pluginManager, false);
	}

	private ScriptErrorReporter reporter(MicrobotPluginManager pluginManager, boolean disableTelemetry)
	{
		ScriptErrorReporter reporter = new ScriptErrorReporter(api, pluginManager, mock(ScheduledExecutorService.class), disableTelemetry);
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
		verify(api).submitErrors(captor.capture());
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
		verify(api).submitErrors(captor.capture());
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
	public void capsDistinctErrors()
	{
		for (int i = 0; i < ScriptErrorReporter.MAX_FINGERPRINTS + 10; i++)
		{
			log(Level.ERROR, "failed " + i, null);
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
		verify(api, never()).submitErrors(any());
	}

	@Test
	public void sendsNothingWithDisableTelemetryFlag()
	{
		reporter = reporter(mock(MicrobotPluginManager.class), true);
		log(Level.ERROR, "loop failed", boom());
		reporter.flush();
		assertEquals(0, reporter.pendingCount());
		verify(api, never()).submitErrors(any());
	}
}
