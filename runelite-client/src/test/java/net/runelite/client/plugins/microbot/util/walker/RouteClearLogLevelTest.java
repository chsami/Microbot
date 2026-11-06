package net.runelite.client.plugins.microbot.util.walker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteClearLogLevelTest
{
	private Logger logger;
	private Level previousLevel;
	private ListAppender<ILoggingEvent> appender;

	@Before
	public void setUp()
	{
		logger = (Logger) LoggerFactory.getLogger(WebWalkLog.class);
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
	public void reasonlessClearStaysBelowWarn()
	{
		Rs2WalkerDoors.logRouteClear(null);
		Rs2WalkerDoors.logRouteClear("  ");

		assertEquals(2, appender.list.size());
		for (ILoggingEvent event : appender.list)
		{
			assertFalse(event.getLevel().isGreaterOrEqual(Level.WARN));
			assertTrue(event.getFormattedMessage().startsWith("[WebWalk] clear | reason=<missing>"));
		}
	}

	@Test
	public void reasonedClearLogsAtInfo()
	{
		Rs2WalkerDoors.logRouteClear("plugin:stop");

		assertEquals(1, appender.list.size());
		assertEquals(Level.INFO, appender.list.get(0).getLevel());
		assertEquals("[WebWalk] clear | plugin:stop", appender.list.get(0).getFormattedMessage());
	}
}
