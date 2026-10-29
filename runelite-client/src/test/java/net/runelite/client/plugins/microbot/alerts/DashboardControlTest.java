package net.runelite.client.plugins.microbot.alerts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DashboardControlTest
{
	@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "Fake Fisher", isExternal = true)
	static class FakeScript extends Plugin
	{
	}

	@PluginDescriptor(name = "Break Handler")
	static class BuiltInPlugin extends Plugin
	{
	}

	private static final String TOKEN = "token";
	private static final String COMMAND = "6f1c7a8e-1a2b-4c3d-9e8f-0123456789ab";
	private static final String SCRIPT_ID = FakeScript.class.getName();

	private final FakeScript script = new FakeScript();
	private final BuiltInPlugin builtIn = new BuiltInPlugin();
	private final List<Plugin> started = new ArrayList<>();
	private MicrobotApi api;
	private MicrobotConfig config;
	private PluginManager pluginManager;
	private Client client;
	private Consumer<Plugin> starter;
	private DashboardControl control;

	@Before
	public void setUp()
	{
		api = mock(MicrobotApi.class);
		config = mock(MicrobotConfig.class);
		when(config.allowDashboardControl()).thenReturn(true);
		pluginManager = mock(PluginManager.class);
		when(pluginManager.getPlugins()).thenReturn(List.of(builtIn, script));
		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		ClientThread clientThread = mock(ClientThread.class);
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(clientThread).invoke(any(Runnable.class));
		starter = plugin ->
		{
			started.add(plugin);
			when(pluginManager.isPluginActive(plugin)).thenReturn(true);
		};
		control = new DashboardControl(api, config, pluginManager, client, clientThread, Runnable::run, plugin -> starter.accept(plugin));
	}

	@Test
	public void stopScriptsLogoutAndStartScriptAreHandled()
	{
		assertTrue(DashboardControl.handles("stop_scripts"));
		assertTrue(DashboardControl.handles("logout"));
		assertTrue(DashboardControl.handles("start_script"));
		assertFalse(DashboardControl.handles("screenshot"));
		assertFalse(DashboardControl.handles(null));
	}

	@Test
	public void commandsAreRefusedWhenControlIsTurnedOff()
	{
		when(config.allowDashboardControl()).thenReturn(false);

		control.run(TOKEN, COMMAND, "stop_scripts", null);

		verify(api).reportCommandResult(TOKEN, COMMAND, false, "disabled");
		verify(pluginManager, never()).getPlugins();
	}

	@Test
	public void startScriptIsRefusedWhenControlIsTurnedOff()
	{
		when(config.allowDashboardControl()).thenReturn(false);

		control.run(TOKEN, COMMAND, "start_script", SCRIPT_ID);

		verify(api).reportCommandResult(TOKEN, COMMAND, false, "disabled");
		verify(pluginManager, never()).getPlugins();
		assertTrue(started.isEmpty());
	}

	@Test
	public void unknownCommandsAreReportedAsUnsupported()
	{
		control.run(TOKEN, COMMAND, "self_destruct", SCRIPT_ID);

		verify(api).reportCommandResult(TOKEN, COMMAND, false, "unsupported");
		assertTrue(started.isEmpty());
	}

	@Test
	public void startScriptStartsTheInstalledScriptAndTellsThePlayer()
	{
		control.run(TOKEN, COMMAND, "start_script", SCRIPT_ID);

		assertEquals(List.of(script), started);
		verify(api).reportCommandResult(TOKEN, COMMAND, true, null);
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", "The Microbot dashboard started Fake Fisher.", null);
	}

	@Test
	public void startNoticeIsOnlyShownWhenLoggedIn()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

		control.run(TOKEN, COMMAND, "start_script", SCRIPT_ID);

		verify(api).reportCommandResult(TOKEN, COMMAND, true, null);
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());
	}

	@Test
	public void startScriptWithAnUnknownIdStartsNothing()
	{
		control.run(TOKEN, COMMAND, "start_script", "com.example.NotInstalledPlugin");
		control.run(TOKEN, COMMAND, "start_script", null);

		verify(api, times(2)).reportCommandResult(TOKEN, COMMAND, false, "unknown-script");
		assertTrue(started.isEmpty());
	}

	@Test
	public void startScriptOnlyStartsScriptsNotBuiltInPlugins()
	{
		control.run(TOKEN, COMMAND, "start_script", BuiltInPlugin.class.getName());

		verify(api).reportCommandResult(TOKEN, COMMAND, false, "unknown-script");
		assertTrue(started.isEmpty());
	}

	@Test
	public void startScriptThatIsAlreadyRunningIsRefused()
	{
		when(pluginManager.isPluginActive(script)).thenReturn(true);

		control.run(TOKEN, COMMAND, "start_script", SCRIPT_ID);

		verify(api).reportCommandResult(TOKEN, COMMAND, false, "already-running");
		assertTrue(started.isEmpty());
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());
	}

	@Test
	public void startScriptThatDoesNotComeUpIsReportedAsFailed()
	{
		starter = started::add;

		control.run(TOKEN, COMMAND, "start_script", SCRIPT_ID);

		assertEquals(List.of(script), started);
		verify(api).reportCommandResult(TOKEN, COMMAND, false, "failed");
		verify(client, never()).addChatMessage(any(), anyString(), anyString(), any());
	}

	@Test
	public void startScriptThatThrowsIsReportedAsFailed()
	{
		starter = plugin ->
		{
			throw new IllegalStateException("boom");
		};

		control.run(TOKEN, COMMAND, "start_script", SCRIPT_ID);

		verify(api).reportCommandResult(TOKEN, COMMAND, false, "failed");
	}

	@Test
	public void stopScriptsStillStopsAndTellsThePlayerWithoutStartingAnything()
	{
		control.run(TOKEN, COMMAND, "stop_scripts", SCRIPT_ID);

		verify(api).reportCommandResult(TOKEN, COMMAND, true, null);
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", DashboardControl.STOP_NOTICE, null);
		assertTrue(started.isEmpty());
	}

	@Test
	public void logoutStillStopsScriptsAndLogsOutWithoutStartingAnything()
	{
		EventBus bus = mock(EventBus.class);
		try (var player = mockStatic(Rs2Player.class);
			var microbot = mockStatic(Microbot.class))
		{
			player.when(Rs2Player::logoutWithoutAutoLogin).thenReturn(Rs2Player.TerminalLogoutState.LOGGED_OUT);
			microbot.when(Microbot::getEventBus).thenReturn(bus);

			control.run(TOKEN, COMMAND, "logout", SCRIPT_ID);
		}

		verify(api).reportCommandResult(TOKEN, COMMAND, true, null);
		verify(bus).post(any(AutoLoginSuppressionRequest.class));
		verify(client).addChatMessage(ChatMessageType.GAMEMESSAGE, "", DashboardControl.LOGOUT_NOTICE, null);
		assertTrue(started.isEmpty());
	}

	@Test
	public void failedLogoutIsReported()
	{
		try (var player = mockStatic(Rs2Player.class))
		{
			player.when(Rs2Player::logoutWithoutAutoLogin).thenReturn(Rs2Player.TerminalLogoutState.INPUT_BLOCKED);

			control.run(TOKEN, COMMAND, "logout", null);
		}

		verify(api).reportCommandResult(TOKEN, COMMAND, false, "failed");
		assertTrue(started.isEmpty());
	}
}
