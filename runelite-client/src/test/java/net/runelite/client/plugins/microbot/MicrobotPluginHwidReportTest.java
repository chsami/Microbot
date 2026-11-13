package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.plugins.microbot.HwidSpoofCheck.Problem;
import net.runelite.client.util.Text;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MicrobotPluginHwidReportTest
{
	private Client client;
	private ClientThread clientThread;
	private Object previousClient;
	private Object previousClientThread;
	private Logger pluginLogger;
	private Level previousLevel;
	private GameChatAppender appender;
	private ChatMessageManager chatMessageManager;
	private MicrobotPlugin plugin;

	@Before
	public void setUp() throws Exception
	{
		Assume.assumeFalse(Microbot.isDebug());
		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		clientThread = mock(ClientThread.class);
		previousClient = swapStatic("client", client);
		previousClientThread = swapStatic("clientThread", clientThread);

		GameChatAppender.updateConfiguration(true, Level.WARN, true);
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		pluginLogger = context.getLogger(MicrobotPlugin.class);
		previousLevel = pluginLogger.getLevel();
		pluginLogger.setLevel(Level.DEBUG);
		appender = new GameChatAppender();
		appender.setContext(context);
		appender.start();
		pluginLogger.addAppender(appender);

		chatMessageManager = mock(ChatMessageManager.class);
		plugin = new MicrobotPlugin();
		Field field = MicrobotPlugin.class.getDeclaredField("chatMessageManager");
		field.setAccessible(true);
		field.set(plugin, chatMessageManager);
	}

	@After
	public void tearDown() throws Exception
	{
		if (pluginLogger != null)
		{
			pluginLogger.detachAppender(appender);
			pluginLogger.setLevel(previousLevel);
			appender.stop();
		}
		if (client != null)
		{
			swapStatic("client", previousClient);
			swapStatic("clientThread", previousClientThread);
		}
	}

	@Test
	public void settingOffWithTheLauncherOnNeverReachesChat() throws Exception
	{
		report(Problem.SETTING_OFF_WITH_LAUNCHER_ON);

		verify(clientThread, never()).invoke(any(Runnable.class));
		verify(chatMessageManager, never()).queue(any());
	}

	@Test
	public void everyOtherProblemShowsExactlyOneChatLineWithoutTheLaunchFlags() throws Exception
	{
		for (Problem problem : Problem.values())
		{
			if (problem == Problem.SETTING_OFF_WITH_LAUNCHER_ON)
			{
				continue;
			}
			Mockito.clearInvocations(clientThread, chatMessageManager);

			report(problem);

			verify(clientThread, never()).invoke(any(Runnable.class));
			ArgumentCaptor<QueuedMessage> queued = ArgumentCaptor.forClass(QueuedMessage.class);
			verify(chatMessageManager, times(1)).queue(queued.capture());
			String line = queued.getValue().getRuneLiteFormattedMessage();
			assertTrue(problem.name(), line.contains(Text.escapeJagex(problem.chatMessage().get())));
			assertFalse(problem.name(), line.contains("-javaagent") || line.contains("-D") || line.contains("microbot.hwid"));
		}
	}

	@Test
	public void theDetailLineOnlyReachesChatWhenThePlayerLowersTheChatLogLevelToInfo() throws Exception
	{
		GameChatAppender.updateConfiguration(true, Level.INFO, true);
		try
		{
			report(Problem.SETTING_OFF_WITH_LAUNCHER_ON);
			verify(clientThread, times(1)).invoke(any(Runnable.class));
		}
		finally
		{
			GameChatAppender.updateConfiguration(true, Level.WARN, true);
		}
	}

	@Test
	public void aBlockedLoginGoesBackToTheLoginScreenAndTellsThePlayerWhy() throws Exception
	{
		for (HwidSpoofCheck.LoginBlock block : HwidSpoofCheck.LoginBlock.values())
		{
			Mockito.clearInvocations(client, clientThread);
			when(client.getGameState()).thenReturn(GameState.LOGGING_IN);
			java.util.List<HwidSpoofCheck.LoginBlock> shown = new java.util.ArrayList<>();
			Field notice = MicrobotPlugin.class.getDeclaredField("loginBlockNotice");
			notice.setAccessible(true);
			notice.set(plugin, (java.util.function.BiConsumer<HwidSpoofCheck.LoginBlock, Runnable>) (shownBlock, bypass) -> shown.add(shownBlock));

			Method method = MicrobotPlugin.class.getDeclaredMethod("blockLogin", HwidSpoofCheck.LoginBlock.class);
			method.setAccessible(true);
			method.invoke(plugin, block);

			ArgumentCaptor<Runnable> later = ArgumentCaptor.forClass(Runnable.class);
			verify(clientThread, times(1)).invokeLater(later.capture());
			verify(client, never()).setGameState(any());
			later.getValue().run();
			verify(client, times(1)).setGameState(GameState.LOGIN_SCREEN);
			assertTrue(block.name(), shown.equals(java.util.List.of(block)));

			String message = block.message();
			assertTrue(block.name(), message.contains("this login was stopped before anything was sent"));
			assertTrue(block.name(), message.contains("Why: ") && message.contains("What to do: "));
			assertTrue(block.name(), message.contains("Are you sure you want to log in without it?"));
			assertTrue(block.name(), message.contains("your real device ID and client details are sent"));
			assertFalse(block.name(), message.contains("-javaagent") || message.contains("-D") || message.contains("microbot.hwid"));
		}
	}

	@Test
	public void aLoginThatAlreadyLeftTheLoggingInStateIsNotTouched() throws Exception
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		Field notice = MicrobotPlugin.class.getDeclaredField("loginBlockNotice");
		notice.setAccessible(true);
		notice.set(plugin, (java.util.function.BiConsumer<HwidSpoofCheck.LoginBlock, Runnable>) (shownBlock, bypass) -> { });
		Method method = MicrobotPlugin.class.getDeclaredMethod("blockLogin", HwidSpoofCheck.LoginBlock.class);
		method.setAccessible(true);
		method.invoke(plugin, HwidSpoofCheck.LoginBlock.SIGNER_CHANGED);

		ArgumentCaptor<Runnable> later = ArgumentCaptor.forClass(Runnable.class);
		verify(clientThread).invokeLater(later.capture());
		later.getValue().run();
		verify(client, never()).setGameState(any());
	}

	@Test
	public void choosingToLogInWithoutSpoofingSwitchesItOffForTheSessionAndStopsBlocking() throws Exception
	{
		String previous = System.getProperty("microbot.hwid.spoof");
		try
		{
			MicrobotConfig config = mock(MicrobotConfig.class);
			when(config.spoofDeviceId()).thenReturn(true);
			Field configField = MicrobotPlugin.class.getDeclaredField("microbotConfig");
			configField.setAccessible(true);
			configField.set(plugin, config);
			Method active = MicrobotPlugin.class.getDeclaredMethod("hwidSpoofActive");
			active.setAccessible(true);
			assertTrue((Boolean) active.invoke(plugin));

			when(client.getGameState()).thenReturn(GameState.LOGGING_IN);
			Runnable[] offered = new Runnable[1];
			Field notice = MicrobotPlugin.class.getDeclaredField("loginBlockNotice");
			notice.setAccessible(true);
			notice.set(plugin, (java.util.function.BiConsumer<HwidSpoofCheck.LoginBlock, Runnable>) (shownBlock, bypass) -> offered[0] = bypass);
			Method method = MicrobotPlugin.class.getDeclaredMethod("blockLogin", HwidSpoofCheck.LoginBlock.class);
			method.setAccessible(true);
			method.invoke(plugin, HwidSpoofCheck.LoginBlock.SIGNER_CHANGED);

			// Nothing changes until the player answers.
			assertTrue((Boolean) active.invoke(plugin));
			offered[0].run();
			assertFalse((Boolean) active.invoke(plugin));
			assertTrue("false".equals(System.getProperty("microbot.hwid.spoof")));
		}
		finally
		{
			if (previous == null)
			{
				System.clearProperty("microbot.hwid.spoof");
			}
			else
			{
				System.setProperty("microbot.hwid.spoof", previous);
			}
		}
	}

	private void report(Problem problem) throws Exception
	{
		Method method = MicrobotPlugin.class.getDeclaredMethod("reportHwidSpoofProblem", Problem.class);
		method.setAccessible(true);
		method.invoke(plugin, problem);
	}

	private static Object swapStatic(String name, Object value) throws Exception
	{
		Field field = Microbot.class.getDeclaredField(name);
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, value);
		return previous;
	}
}
