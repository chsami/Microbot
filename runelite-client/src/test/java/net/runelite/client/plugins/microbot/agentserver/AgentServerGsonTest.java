package net.runelite.client.plugins.microbot.agentserver;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AgentServerGsonTest {

	private final Gson gson = AgentServerPlugin.createGson();

	static class Description {
		String name = "Inventory";
		List<String> actions = Collections.emptyList();
		List<Description> children = Collections.emptyList();
	}

	@Test
	public void serializesWidgetDescribeResponseWithEmptyList() {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("groupId", 149);
		response.put("childId", 0);
		response.put("count", 0);
		response.put("widgets", Collections.emptyList());

		JsonObject json = gson.fromJson(gson.toJson(response), JsonObject.class);

		assertEquals(149, json.get("groupId").getAsInt());
		assertEquals(0, json.getAsJsonArray("widgets").size());
	}

	@Test
	public void serializesImmutableJdkCollectionsAndMaps() {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("singleton", Collections.singletonList("a"));
		response.put("listOf", List.of(1, 2));
		response.put("unmodifiable", Collections.unmodifiableList(new ArrayList<>(Arrays.asList("x", "y"))));
		response.put("setOf", Set.of("s"));
		response.put("emptyMap", Collections.emptyMap());
		response.put("mapOf", Map.of("k", List.of()));

		JsonObject json = gson.fromJson(gson.toJson(response), JsonObject.class);

		assertEquals("a", json.getAsJsonArray("singleton").get(0).getAsString());
		assertEquals(2, json.getAsJsonArray("listOf").get(1).getAsInt());
		assertEquals("y", json.getAsJsonArray("unmodifiable").get(1).getAsString());
		assertEquals("s", json.getAsJsonArray("setOf").get(0).getAsString());
		assertEquals(0, json.getAsJsonObject("emptyMap").size());
		assertEquals(0, json.getAsJsonObject("mapOf").getAsJsonArray("k").size());
	}

	@Test
	public void serializesNestedEmptyListFields() {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("widgets", Collections.singletonList(new Description()));

		JsonArray widgets = gson.fromJson(gson.toJson(response), JsonObject.class).getAsJsonArray("widgets");

		JsonObject widget = widgets.get(0).getAsJsonObject();
		assertEquals("Inventory", widget.get("name").getAsString());
		assertEquals(0, widget.getAsJsonArray("actions").size());
		assertEquals(0, widget.getAsJsonArray("children").size());
	}

	@Test
	public void deserializesRequestBodiesUnchanged() {
		@SuppressWarnings("unchecked")
		Map<String, Object> parsed = gson.fromJson("{\"name\":\"x\",\"args\":[1,2],\"opts\":{\"a\":true}}", LinkedHashMap.class);

		assertEquals("x", parsed.get("name"));
		assertTrue(parsed.get("args") instanceof List);
		assertEquals(2, ((List<?>) parsed.get("args")).size());
		assertTrue(parsed.get("opts") instanceof Map);
		assertEquals(true, ((Map<?, ?>) parsed.get("opts")).get("a"));
	}
}
