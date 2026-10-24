package net.runelite.client.plugins.microbot.agentserver.handler;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ScriptHandlerPluginLookupTest {

	@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "Agility")
	static class MicroAgilityPlugin extends Plugin {
	}

	@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "Agility Pyramid")
	static class MicroPyramidPlugin extends Plugin {
	}

	@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "Agent Server")
	static class MicroAgentServerPlugin extends Plugin {
	}

	private final List<Plugin> plugins = List.of(new MicroAgilityPlugin(), new MicroPyramidPlugin(), new MicroAgentServerPlugin());

	@After
	public void cleanUp() {
		ScriptHeartbeatRegistry.remove("a.b.guardkiller.GuardKillerScript");
		ScriptHeartbeatRegistry.remove("a.b.guardkiller.GuardKillerBankScript");
		ScriptHeartbeatRegistry.remove("a.b.other.OtherScript");
	}

	@Test
	public void plainNameStripsHtmlAndTagPrefix() {
		assertEquals("Agent Server", ScriptHandler.plainName(PluginDescriptor.Mocrosoft + "Agent Server"));
		assertEquals("Plain", ScriptHandler.plainName("Plain"));
	}

	@Test
	public void exactNameWinsOverLongerPartialMatches() {
		assertTrue(ScriptHandler.matchPluginByName(plugins, "agility") instanceof MicroAgilityPlugin);
	}

	@Test
	public void uniquePartialMatchResolves() {
		assertTrue(ScriptHandler.matchPluginByName(plugins, "pyramid") instanceof MicroPyramidPlugin);
	}

	@Test
	public void ambiguousPartialMatchIsRejected() {
		try {
			ScriptHandler.matchPluginByName(plugins, "ag");
			fail("expected an ambiguity error");
		} catch (AgentHandler.BadRequestException e) {
			assertTrue(e.getMessage().contains("Agility Pyramid"));
			assertTrue(e.getMessage().contains("Agent Server"));
		}
	}

	@Test
	public void htmlMarkupDoesNotMatch() {
		assertNull(ScriptHandler.matchPluginByName(plugins, "font"));
	}

	@Test
	public void healthForPluginClassFindsScriptsInItsPackage() {
		ScriptHeartbeatRegistry.recordHeartbeat("a.b.guardkiller.GuardKillerScript");
		ScriptHeartbeatRegistry.recordHeartbeat("a.b.other.OtherScript");

		List<Map<String, Object>> health = ScriptHandler.healthFor("a.b.guardkiller.GuardKillerPlugin");

		assertEquals(1, health.size());
		assertEquals("a.b.guardkiller.GuardKillerScript", health.get(0).get("scriptClassName"));
	}

	@Test
	public void healthForPluginWithSeveralScriptsReturnsAll() {
		ScriptHeartbeatRegistry.recordHeartbeat("a.b.guardkiller.GuardKillerScript");
		ScriptHeartbeatRegistry.recordHeartbeat("a.b.guardkiller.GuardKillerBankScript");

		assertEquals(2, ScriptHandler.healthFor("a.b.guardkiller.GuardKillerPlugin").size());
	}

	@Test
	public void healthForScriptClassIsExact() {
		ScriptHeartbeatRegistry.recordHeartbeat("a.b.guardkiller.GuardKillerScript");
		ScriptHeartbeatRegistry.recordHeartbeat("a.b.guardkiller.GuardKillerBankScript");

		List<Map<String, Object>> health = ScriptHandler.healthFor("a.b.guardkiller.GuardKillerScript");

		assertEquals(1, health.size());
		assertEquals("a.b.guardkiller.GuardKillerScript", health.get(0).get("scriptClassName"));
	}

	@Test
	public void healthForUnknownPluginIsEmpty() {
		assertTrue(ScriptHandler.healthFor("a.b.nothing.NothingPlugin").isEmpty());
	}
}
