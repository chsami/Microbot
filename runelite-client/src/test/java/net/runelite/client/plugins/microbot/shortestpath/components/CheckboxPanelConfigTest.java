package net.runelite.client.plugins.microbot.shortestpath.components;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.lang.reflect.Field;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin.CONFIG_GROUP;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CheckboxPanelConfigTest
{
	private static final String FAILURE_PREFIX = "Failed to poh checkbox config";

	private Logger logger;
	private ListAppender<ILoggingEvent> appender;
	private Level previousLevel;
	private Object previousConfigManager;
	private ConfigManager configManager;

	@Before
	public void setUp() throws Exception
	{
		logger = (Logger) LoggerFactory.getLogger(Microbot.class);
		previousLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
		appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);

		previousConfigManager = configManagerField().get(null);
		configManager = mock(ConfigManager.class);
		configManagerField().set(null, configManager);
	}

	@After
	public void tearDown() throws Exception
	{
		configManagerField().set(null, previousConfigManager);
		logger.detachAppender(appender);
		logger.setLevel(previousLevel);
	}

	@Test
	public void unsetKeysLoadAsUncheckedWithoutLogging()
	{
		CheckboxPanel panel = new CheckboxPanel();

		assertFalse(panel.fairyRingCb.isSelected());
		assertFalse(panel.spiritTreeCb.isSelected());
		assertEquals(0, failureLogCount());
	}

	@Test
	public void storedValuesAreLoaded()
	{
		when(configManager.<Boolean>getConfiguration(CONFIG_GROUP, "fairyRing", Boolean.class)).thenReturn(Boolean.TRUE);
		when(configManager.<Boolean>getConfiguration(CONFIG_GROUP, "spiritTree", Boolean.class)).thenReturn(Boolean.FALSE);

		CheckboxPanel panel = new CheckboxPanel();

		assertTrue(panel.fairyRingCb.isSelected());
		assertFalse(panel.spiritTreeCb.isSelected());
		assertEquals(0, failureLogCount());
	}

	@Test
	public void missingConfigManagerIsStillLogged() throws Exception
	{
		configManagerField().set(null, null);

		CheckboxPanel panel = new CheckboxPanel();

		assertFalse(panel.fairyRingCb.isSelected());
		assertEquals(7, failureLogCount());
	}

	private long failureLogCount()
	{
		return appender.list.stream()
			.filter(event -> event.getFormattedMessage().startsWith(FAILURE_PREFIX))
			.count();
	}

	private static Field configManagerField() throws Exception
	{
		Field field = Microbot.class.getDeclaredField("configManager");
		field.setAccessible(true);
		return field;
	}
}
