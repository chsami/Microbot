package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import org.junit.Test;

public class QuestQueueRequestTest
{
	private static QuestQueueRequest parse(String json)
	{
		Map<String, Object> body = new Gson().fromJson(json, new TypeToken<Map<String, Object>>() { }.getType());
		return QuestQueueRequest.parse(body, name -> !name.startsWith("Nope"),
			name -> QuestProfile.defaultFor(name, name.contains("Slayer")));
	}

	@Test
	public void plainNamesGetTheDefaultProfileAndObjectsOverrideIt()
	{
		QuestQueueRequest request = parse("{\"quests\":[\"Cooks Assistant\",{\"name\":\"Dragon Slayer I\","
			+ "\"type\":\"travel\",\"obtain\":\"bank\",\"staminaDoses\":3,\"minStamina\":2,\"food\":\"Lobster\","
			+ "\"foodQty\":6,\"teleports\":[{\"name\":\"Varrock teleport\",\"qty\":2}],\"combat\":\"skip\"}],\"start\":true}");
		assertNull(request.error);
		assertTrue(request.start);
		assertTrue(request.approve);
		assertEquals(2, request.entries.size());
		assertEquals(QuestProfile.Type.TRAVEL, request.entries.get(0).profile.getType());
		QuestProfile second = request.entries.get(1).profile;
		assertEquals(QuestProfile.Type.TRAVEL, second.getType());
		assertEquals(QuestProfile.Obtain.BANK_ONLY, second.getObtain());
		assertEquals(3, second.getSupplies().getStaminaQty());
		assertEquals(2, second.getMinStamina());
		assertEquals(6, second.getSupplies().getFoodQty());
		assertEquals(1, second.getSupplies().getItems().size());
		assertEquals("skip", request.entries.get(1).combat);
		assertNull(request.entries.get(0).combat);
	}

	@Test
	public void badInputIsRejectedWithAMessage()
	{
		assertNotNull(parse("{\"quests\":[\"Nope\"]}").error);
		assertNotNull(parse("{\"quests\":\"x\"}").error);
		assertNotNull(parse("{\"quests\":[{\"name\":\"A\",\"type\":\"flying\"}]}").error);
		assertNotNull(parse("{\"quests\":[{\"name\":\"A\",\"minStamina\":9}]}").error);
		assertNotNull(parse("{\"quests\":[{\"name\":\"A\",\"combat\":5}]}").error);
		assertNotNull(parse("{\"mode\":\"sideways\"}").error);
		assertNotNull(parse("{\"pool\":[\"Nope\"]}").error);
		assertNotNull(parse("{\"defaultMinStamina\":7}").error);
	}

	@Test
	public void settingsOnlyBodiesAreFine()
	{
		QuestQueueRequest request = parse("{\"mode\":\"random\",\"pool\":[\"A\",\"B\"],\"pauseMinMinutes\":2,"
			+ "\"pauseMaxMinutes\":5,\"defaultMinStamina\":1,\"approve\":false}");
		assertNull(request.error);
		assertFalse(request.hasQuests);
		assertEquals(QuestQueue.Mode.RANDOM, request.mode);
		assertEquals(2, request.pool.size());
		assertEquals(Integer.valueOf(2), request.pauseMin);
		assertEquals(Integer.valueOf(1), request.defaultMinStamina);
		assertFalse(request.approve);
	}
}
