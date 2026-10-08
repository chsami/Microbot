package net.runelite.client.plugins.microbot;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertEquals;

public class MicrobotLogInterruptTest
{
	private Logger logger;
	private ListAppender<ILoggingEvent> appender;
	private Level previousLevel;

	@Before
	public void setUp()
	{
		logger = (Logger) LoggerFactory.getLogger(Microbot.class);
		previousLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
	}

	@After
	public void tearDown()
	{
		logger.detachAppender(appender);
		logger.setLevel(previousLevel);
	}

	@Test
	public void interruptedClientThreadWaitIsNotLoggedAsError()
	{
		Microbot.logStackTrace("AttackNpcScript", new RuntimeException("Interrupted waiting for client thread", new InterruptedException()));

		assertEquals(1, appender.list.size());
		assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
	}

	@Test
	public void otherFailuresStillLogError()
	{
		Microbot.logStackTrace("AttackNpcScript", new IllegalStateException("boom"));

		assertEquals(1, appender.list.size());
		assertEquals(Level.ERROR, appender.list.get(0).getLevel());
	}
}
