package net.runelite.client.ui;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.joran.spi.ConsoleTarget;
import com.google.common.collect.Iterables;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Provider;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.input.MouseManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class ClientUITest
{
	private static final String CONFIG_GROUP = "runelite";
	private static final String SIDEBAR_TAB = "clientSidebarTab";
	private static final String SIDEBAR_CLOSED = "clientSidebarClosed";

	private ConfigManager configManager;
	private EventBus eventBus;
	private ClientUI clientUI;
	private PrintStream originalOut;
	private ConsoleAppender<ILoggingEvent> stdoutAppender;

	@Before
	public void setUp() throws Exception
	{
		originalOut = System.out;
		configManager = mock(ConfigManager.class);
		eventBus = new EventBus();
		Constructor<ClientUI> constructor = ClientUI.class.getDeclaredConstructor(RuneLiteConfig.class, MouseManager.class,
			Client.class, ConfigManager.class, Provider.class, EventBus.class, boolean.class, String.class);
		constructor.setAccessible(true);
		clientUI = constructor.newInstance(mock(RuneLiteConfig.class), null, null, configManager,
			(Provider<ClientThread>) () -> null, eventBus, false, "Microbot");
		eventBus.register(clientUI);
	}

	@After
	public void tearDown() throws Exception
	{
		System.setOut(originalOut);
		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
		ConsoleLogAppender consoleLogAppender = (ConsoleLogAppender) get("consoleLogAppender");
		if (consoleLogAppender != null)
		{
			consoleLogAppender.uninstall();
		}
		if (stdoutAppender != null)
		{
			root.detachAppender(stdoutAppender);
			stdoutAppender.stop();
			ConsoleLogAppender.bindStdoutAppenders(context, ConsoleTarget.SystemOut.getStream());
		}
	}

	@Test
	public void removingTheOpenTabStoresTheTabShownInstead() throws Exception
	{
		NavigationButton first = button("First", 1);
		NavigationButton second = button("Second", 2);
		onEdt(() ->
		{
			installSidebar();
			clientUI.addNavigation(first);
			clientUI.addNavigation(second);
			clientUI.openPanel(first, true);
			clientUI.openPanel(second, true);
			clientUI.removeNavigation(second);
		});

		verify(configManager).setConfiguration(CONFIG_GROUP, SIDEBAR_TAB, "First");
	}

	@Test
	public void removingTheOpenTabBeforeTheLastTabSelectsTheLastTab() throws Exception
	{
		NavigationButton first = button("First", 1);
		NavigationButton second = button("Second", 2);
		NavigationButton third = button("Third", 3);
		AtomicReference<JTabbedPane> sidebar = new AtomicReference<>();
		onEdt(() ->
		{
			installSidebar();
			sidebar.set((JTabbedPane) get("sidebar"));
			clientUI.addNavigation(first);
			clientUI.addNavigation(second);
			clientUI.addNavigation(third);
			clientUI.openPanel(third, true);
			clientUI.openPanel(second, true);
			clientUI.removeNavigation(second);
		});

		assertEquals(2, sidebar.get().getTabCount());
		assertEquals(1, sidebar.get().getSelectedIndex());
		assertEquals(third, get("selectedTab"));
		verify(configManager).setConfiguration(CONFIG_GROUP, SIDEBAR_TAB, "Third");
	}

	@Test
	public void removingTheOpenTabWhileClosingKeepsTheSavedTab() throws Exception
	{
		NavigationButton first = button("First", 1);
		NavigationButton second = button("Second", 2);
		onEdt(() ->
		{
			installSidebar();
			clientUI.addNavigation(first);
			clientUI.addNavigation(second);
			clientUI.openPanel(first, true);
			clientUI.openPanel(second, true);
		});

		eventBus.post(new ClientShutdown());
		onEdt(() -> clientUI.removeNavigation(second));

		verify(configManager, never()).setConfiguration(eq(CONFIG_GROUP), eq(SIDEBAR_TAB), any(String.class));
		verify(configManager, never()).unsetConfiguration(CONFIG_GROUP, SIDEBAR_TAB);
	}

	@Test
	public void removingTheOpenTabWhileClosingDoesNotReopenAClosedSidebar() throws Exception
	{
		NavigationButton first = button("First", 1);
		NavigationButton second = button("Second", 2);
		AtomicReference<JTabbedPane> sidebar = new AtomicReference<>();
		onEdt(() ->
		{
			installSidebar();
			sidebar.set((JTabbedPane) get("sidebar"));
			clientUI.addNavigation(first);
			clientUI.addNavigation(second);
			clientUI.openPanel(first, true);
			clientUI.openPanel(second, true);
			sidebar.get().setVisible(false);
			pushHistory();
		});

		eventBus.post(new ClientShutdown());
		onEdt(() -> clientUI.removeNavigation(second));

		verify(configManager, never()).setConfiguration(eq(CONFIG_GROUP), eq(SIDEBAR_CLOSED), any(Boolean.class));
		verify(configManager, never()).unsetConfiguration(CONFIG_GROUP, SIDEBAR_CLOSED);
		assertFalse(sidebar.get().isVisible());
	}

	@Test
	public void consoleInitializationKeepsStdoutLogLinesOutOfThePanel() throws Exception
	{
		ByteArrayOutputStream terminal = new ByteArrayOutputStream();
		System.setOut(new PrintStream(terminal, true, StandardCharsets.UTF_8));

		LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(context);
		encoder.setPattern("%msg%n");
		encoder.start();
		stdoutAppender = new ConsoleAppender<>();
		stdoutAppender.setContext(context);
		stdoutAppender.setEncoder(encoder);
		stdoutAppender.start();
		Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
		root.addAppender(stdoutAppender);

		AtomicReference<LogConsolePanel> panel = new AtomicReference<>();
		onEdt(() -> panel.set(new LogConsolePanel()));
		set("consolePanel", panel.get());
		Method initialize = ClientUI.class.getDeclaredMethod("initializeConsoleLogging");
		initialize.setAccessible(true);
		initialize.invoke(clientUI);

		String line = "console init probe " + System.nanoTime();
		root.warn(line);
		onEdt(() -> { });
		onEdt(() -> { });

		JTextArea textArea = (JTextArea) ((JScrollPane) panel.get().getComponent(0)).getViewport().getView();
		AtomicReference<String> text = new AtomicReference<>();
		onEdt(() ->
		{
			Document document = textArea.getDocument();
			text.set(document.getText(0, document.getLength()));
		});

		assertEquals(1, occurrences(text.get(), line));
		assertTrue(terminal.toString(StandardCharsets.UTF_8).contains(line));
	}

	private void installSidebar() throws Exception
	{
		JTabbedPane sidebar = new JTabbedPane(JTabbedPane.RIGHT);
		sidebar.setSelectedIndex(-1);
		Collection<NavigationButton> entries = (Collection<NavigationButton>) get("sidebarEntries");
		sidebar.addChangeListener(ev ->
		{
			int index = sidebar.getSelectedIndex();
			try
			{
				NavigationButton selected = index < 0 ? null : Iterables.get(entries, index);
				if (selected == get("selectedTab"))
				{
					return;
				}
				set("selectedTab", selected);
				if (sidebar.isVisible())
				{
					pushHistory();
				}
			}
			catch (ReflectiveOperationException ex)
			{
				throw new IllegalStateException(ex);
			}
		});
		set("sidebar", sidebar);
		set("content", new JPanel());
	}

	private void pushHistory() throws ReflectiveOperationException
	{
		Method pushHistory = ClientUI.class.getDeclaredMethod("pushHistory");
		pushHistory.setAccessible(true);
		pushHistory.invoke(clientUI);
	}

	private static NavigationButton button(String tooltip, int priority)
	{
		return NavigationButton.builder()
			.tooltip(tooltip)
			.priority(priority)
			.icon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB))
			.panel(new PluginPanel()
			{
			})
			.build();
	}

	private Object get(String name) throws ReflectiveOperationException
	{
		Field field = ClientUI.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(clientUI);
	}

	private void set(String name, Object value) throws ReflectiveOperationException
	{
		Field field = ClientUI.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(clientUI, value);
	}

	private static int occurrences(String text, String needle)
	{
		int count = 0;
		for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1))
		{
			count++;
		}
		return count;
	}

	private static void onEdt(ThrowingRunnable runnable) throws Exception
	{
		AtomicReference<Exception> failure = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				runnable.run();
			}
			catch (Exception ex)
			{
				failure.set(ex);
			}
		});
		if (failure.get() != null)
		{
			throw failure.get();
		}
	}

	private interface ThrowingRunnable
	{
		void run() throws Exception;
	}
}
