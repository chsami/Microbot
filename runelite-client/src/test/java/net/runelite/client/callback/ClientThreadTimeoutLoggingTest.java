package net.runelite.client.callback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ClientThreadTimeoutLoggingTest
{
	private ClientThread clientThread;
	private Logger logger;
	private ListAppender<ILoggingEvent> appender;
	private volatile Thread gameThread;

	@Before
	public void setUp() throws Exception
	{
		Client client = mock(Client.class);
		when(client.isClientThread()).thenAnswer(i -> Thread.currentThread() == gameThread);
		clientThread = new ClientThread();
		Field field = ClientThread.class.getDeclaredField("client");
		field.setAccessible(true);
		field.set(clientThread, client);
		clientThread.clientThreadTimeoutMillis = 20;

		logger = (Logger) LoggerFactory.getLogger(ClientThread.class);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@After
	public void tearDown()
	{
		logger.detachAppender(appender);
	}

	@Test
	public void repeatedTimeoutsLogOneWarningWithoutStackAndNoError()
	{
		for (int i = 0; i < 5; i++)
		{
			Optional<Integer> result = clientThread.runOnClientThreadOptional(() -> 1);
			assertFalse(result.isPresent());
		}

		assertEquals(0, count(Level.ERROR));
		assertEquals(1, count(Level.WARN));
		ILoggingEvent warning = appender.list.stream().filter(e -> e.getLevel() == Level.WARN).findFirst().get();
		assertNull(warning.getThrowableProxy());
	}

	@Test
	public void taskFailureStillLogsError() throws Exception
	{
		clientThread.clientThreadTimeoutMillis = 10000;
		gameThread = new Thread(() ->
		{
			while (!Thread.currentThread().isInterrupted())
			{
				clientThread.invoke();
				Thread.onSpinWait();
			}
		});
		gameThread.start();
		try
		{
			Optional<Integer> result = clientThread.runOnClientThreadOptional(() ->
			{
				throw new IllegalStateException("boom");
			});
			assertFalse(result.isPresent());
		}
		finally
		{
			gameThread.interrupt();
			gameThread.join(1000);
		}

		assertEquals(1, count(Level.ERROR));
		assertEquals(0, count(Level.WARN));
	}

	private long count(Level level)
	{
		return appender.list.stream().filter(e -> e.getLevel() == level).collect(Collectors.counting());
	}
}
