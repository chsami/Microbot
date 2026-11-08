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
