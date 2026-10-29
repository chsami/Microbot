package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AlertEventTest
{
	private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000042");

	@Test
	public void heartbeatCarriesStateButNoReasonOrMessage()
	{
		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.HEARTBEAT)
			.reason("error")
			.message("ignored")
			.runningScripts(List.of("Fisher", "Woodcutter"))
			.loggedIn(true)
			.breakActive(false)
			.build()
			.toJson(SESSION);

		assertEquals(SESSION.toString(), json.get("sessionId").getAsString());
		assertEquals("heartbeat", json.get("type").getAsString());
		assertTrue(json.get("reason").isJsonNull());
		assertTrue(json.get("message").isJsonNull());
		assertTrue(json.get("plugin").isJsonNull());
		assertEquals(2, json.getAsJsonArray("runningScripts").size());
		assertTrue(json.get("loggedIn").getAsBoolean());
		assertFalse(json.get("breakActive").getAsBoolean());
	}

	@Test
	public void scriptStoppedKeepsReasonAndMessage()
	{
		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.SCRIPT_STOPPED)
			.plugin("Fisher")
			.pluginVersion("1.4.0")
			.reason("error")
			.message("Bank closed (IllegalStateException)")
			.build()
			.toJson(SESSION);

		assertEquals("Fisher", json.get("plugin").getAsString());
		assertEquals("1.4.0", json.get("pluginVersion").getAsString());
		assertEquals("error", json.get("reason").getAsString());
		assertEquals("Bank closed (IllegalStateException)", json.get("message").getAsString());
		assertEquals(0, json.getAsJsonArray("runningScripts").size());
	}

	@Test
	public void scriptErrorKeepsMessageButNotReason()
	{
		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.SCRIPT_ERROR)
			.reason("error")
			.message("boom")
			.build()
			.toJson(SESSION);

		assertTrue(json.get("reason").isJsonNull());
		assertEquals("boom", json.get("message").getAsString());
	}

	@Test
	public void intendedIsOnlySentForLogouts()
	{
		JsonObject loggedOut = AlertEvent.builder()
			.type(AlertEvent.LOGGED_OUT)
			.intended(true)
			.build()
			.toJson(SESSION);
		JsonObject heartbeat = AlertEvent.builder()
			.type(AlertEvent.HEARTBEAT)
			.intended(true)
			.build()
			.toJson(SESSION);
		JsonObject unintended = AlertEvent.builder()
			.type(AlertEvent.LOGGED_OUT)
			.build()
			.toJson(SESSION);

		assertTrue(loggedOut.get("intended").getAsBoolean());
		assertFalse(heartbeat.get("intended").getAsBoolean());
		assertFalse(unintended.get("intended").getAsBoolean());
	}

	@Test
	public void fieldsAreCutToTheApiLimits()
	{
		List<String> scripts = new ArrayList<>();
		for (int i = 0; i < 25; i++)
		{
			scripts.add("Script " + i);
		}
		scripts.addAll(Arrays.asList("Script 0", " ", null, "x".repeat(150)));

		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.SCRIPT_ERROR)
			.plugin("p".repeat(150))
			.pluginVersion("v".repeat(40))
			.message("m".repeat(800))
			.runningScripts(scripts)
			.build()
			.toJson(SESSION);

		assertEquals(AlertEvent.MAX_PLUGIN_CHARS, json.get("plugin").getAsString().length());
		assertEquals(AlertEvent.MAX_VERSION_CHARS, json.get("pluginVersion").getAsString().length());
		assertEquals(AlertEvent.MAX_MESSAGE_CHARS, json.get("message").getAsString().length());
		JsonArray running = json.getAsJsonArray("runningScripts");
		assertEquals(AlertEvent.MAX_RUNNING_SCRIPTS, running.size());
		assertEquals("Script 0", running.get(0).getAsString());
		assertEquals("Script 19", running.get(19).getAsString());
	}

	@Test
	public void blankValuesBecomeNull()
	{
		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.SCRIPT_STOPPED)
			.plugin("  ")
			.message("")
			.build()
			.toJson(SESSION);

		assertTrue(json.get("plugin").isJsonNull());
		assertTrue(json.get("message").isJsonNull());
	}

	@Test
	public void availableScriptsAreSentSortedByNameWithTheirClassName()
	{
		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.HEARTBEAT)
			.availableScripts(List.of(
				new AlertEvent.AvailableScript("net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin", "Woodcutter"),
				new AlertEvent.AvailableScript("net.runelite.client.plugins.microbot.fishing.AutoFishingPlugin", "auto fisher"),
				new AlertEvent.AvailableScript("net.runelite.client.plugins.microbot.agility.MicroAgilityPlugin$Inner", "Agility")))
			.build()
			.toJson(SESSION);

		JsonArray available = json.getAsJsonArray("availableScripts");
		assertEquals(3, available.size());
		assertEquals("Agility", available.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("net.runelite.client.plugins.microbot.agility.MicroAgilityPlugin$Inner", available.get(0).getAsJsonObject().get("id").getAsString());
		assertEquals("auto fisher", available.get(1).getAsJsonObject().get("name").getAsString());
		assertEquals("net.runelite.client.plugins.microbot.fishing.AutoFishingPlugin", available.get(1).getAsJsonObject().get("id").getAsString());
		assertEquals("Woodcutter", available.get(2).getAsJsonObject().get("name").getAsString());
		assertEquals(2, available.get(0).getAsJsonObject().size());
	}

	@Test
	public void availableScriptsAreAlwaysSentEvenWhenEmpty()
	{
		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.SCRIPT_STOPPED)
			.build()
			.toJson(SESSION);

		assertTrue(json.get("availableScripts").isJsonArray());
		assertEquals(0, json.getAsJsonArray("availableScripts").size());
	}

	@Test
	public void availableScriptsSkipIdsTheApiWouldRejectAndCutLongNames()
	{
		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.HEARTBEAT)
			.availableScripts(Arrays.asList(
				new AlertEvent.AvailableScript("com.example.Good", "n".repeat(120)),
				new AlertEvent.AvailableScript("com.example.Bad-Id", "Dash"),
				new AlertEvent.AvailableScript("com.example.Bad Id", "Space"),
				new AlertEvent.AvailableScript("", "Empty"),
				new AlertEvent.AvailableScript(null, "Null"),
				new AlertEvent.AvailableScript("a".repeat(201), "Too long"),
				new AlertEvent.AvailableScript("a".repeat(200), "Just fits"),
				new AlertEvent.AvailableScript("com.example.Blank", "  "),
				new AlertEvent.AvailableScript("com.example.Good", "z duplicate"),
				null))
			.build()
			.toJson(SESSION);

		JsonArray available = json.getAsJsonArray("availableScripts");
		assertEquals(2, available.size());
		assertEquals("Just fits", available.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("com.example.Good", available.get(1).getAsJsonObject().get("id").getAsString());
		assertEquals(AlertEvent.MAX_SCRIPT_NAME_CHARS, available.get(1).getAsJsonObject().get("name").getAsString().length());
	}

	@Test
	public void availableScriptsAreCappedAtThreeHundred()
	{
		List<AlertEvent.AvailableScript> scripts = new ArrayList<>();
		for (int i = 0; i < 350; i++)
		{
			scripts.add(new AlertEvent.AvailableScript("com.example.Script" + i, String.format("Script %03d", 349 - i)));
		}

		JsonObject json = AlertEvent.builder()
			.type(AlertEvent.HEARTBEAT)
			.availableScripts(scripts)
			.build()
			.toJson(SESSION);

		JsonArray available = json.getAsJsonArray("availableScripts");
		assertEquals(AlertEvent.MAX_AVAILABLE_SCRIPTS, available.size());
		assertEquals("Script 000", available.get(0).getAsJsonObject().get("name").getAsString());
		assertEquals("com.example.Script349", available.get(0).getAsJsonObject().get("id").getAsString());
		assertEquals("Script 299", available.get(299).getAsJsonObject().get("name").getAsString());
	}
}
