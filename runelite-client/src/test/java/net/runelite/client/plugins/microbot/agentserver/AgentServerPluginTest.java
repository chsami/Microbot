package net.runelite.client.plugins.microbot.agentserver;

import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AgentServerPluginTest {
	@Mock
	private AgentServerConfig config;

	@Mock
	private ConfigManager configManager;

	private AgentServerPlugin plugin;

	@Before
	public void setUp() throws Exception {
		plugin = new AgentServerPlugin();
		setField("config", config);
		setField("configManager", configManager);
		when(config.port()).thenReturn(8081);
	}

	@Test
	public void ensurePortUsesConfiguredDefaultWhenNoStoredPort() throws Exception {
		when(configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_PORT)).thenReturn(null);

		assertEquals(8081, invokeEnsurePort());
		verify(configManager, never()).setConfiguration(eq(AgentServerConfig.GROUP), eq(AgentServerConfig.KEY_PORT), anyString());
	}

	@Test
	public void ensurePortUsesStoredPortWhenPresent() throws Exception {
		when(configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_PORT)).thenReturn("18181");

		assertEquals(18181, invokeEnsurePort());
	}

	@Test
	public void ensurePortFallsBackToConfiguredDefaultWhenStoredPortIsInvalid() throws Exception {
		when(configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_PORT)).thenReturn("not-a-port");

		assertEquals(8081, invokeEnsurePort());
	}

	@Test
	public void shutDownKeepsATokenFileThisInstanceDidNotWrite() throws Exception {
		java.nio.file.Path home = java.nio.file.Files.createTempDirectory("agent-home");
		java.nio.file.Path token = home.resolve(".runelite").resolve(".agent-token");
		java.nio.file.Files.createDirectories(token.getParent());
		java.nio.file.Files.writeString(token, "other-client-token");
		String originalHome = System.getProperty("user.home");
		System.setProperty("user.home", home.toString());
		try {
			plugin.shutDown();
		} finally {
			System.setProperty("user.home", originalHome);
		}
		assertTrue(java.nio.file.Files.exists(token));
	}

	private int invokeEnsurePort() throws Exception {
		Method method = AgentServerPlugin.class.getDeclaredMethod("ensurePort");
		method.setAccessible(true);
		return (Integer) method.invoke(plugin);
	}

	private void setField(String name, Object value) throws Exception {
		Field field = AgentServerPlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}
}
