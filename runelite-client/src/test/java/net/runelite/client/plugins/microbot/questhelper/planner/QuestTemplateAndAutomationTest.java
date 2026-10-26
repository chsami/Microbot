package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestCombatLoadout;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions;
import org.junit.Test;

public class QuestTemplateAndAutomationTest
{
	private static final class MemoryStore implements QuestQueue.Store
	{
		String json;

		@Override
		public String read()
		{
			return json;
		}

		@Override
		public void write(String value)
		{
			json = value;
		}
	}

	@Test
	public void travelQuestsStartWithStaminaOnlyAndCombatQuestsAlsoWithFood()
	{
		QuestProfile travel = QuestProfile.defaultFor("A", false);
		assertTrue(travel.getSupplies().isStaminaOn());
		assertEquals("", travel.getSupplies().getFood());
		QuestProfile combat = QuestProfile.defaultFor("B", true);
		assertEquals("Lobster", combat.getSupplies().getFood());
		assertEquals(5, combat.getSupplies().getFoodQty());
		assertFalse("the quest setup keeps the armour until supplies are edited", combat.getSupplies().isCustomised());
	}

	@Test
	public void aSavedTemplateIsWhatNewQuestsOfThatTypeStartWith()
	{
		MemoryStore store = new MemoryStore();
		QuestQueue queue = new QuestQueue(store);
		QuestProfile.Supplies travel = QuestProfile.defaultSupplies(QuestProfile.Type.TRAVEL);
		travel.setStaminaQty(4);
		travel.getItems().add(new QuestCombatLoadout.InventoryItem("Varrock teleport", -1, 5, false));
		queue.setTemplate(QuestProfile.Type.TRAVEL, travel);

		QuestProfile fresh = queue.profileOrDefault("Cooks Assistant", false);
		assertEquals(4, fresh.getSupplies().getStaminaQty());
		assertEquals(1, fresh.getSupplies().getItems().size());
		// combat quests have no template yet
		assertEquals("Lobster", queue.profileOrDefault("Imp Catcher", true).getSupplies().getFood());
		// it survives a restart
		QuestQueue again = new QuestQueue(store);
		again.load();
		assertEquals(4, again.profileOrDefault("X", false).getSupplies().getStaminaQty());
		// a saved profile is never overwritten by the template
		QuestProfile saved = QuestProfile.defaultFor("Y", false);
		saved.getSupplies().setStaminaQty(1);
		again.saveProfile(saved);
		assertEquals(1, again.profileOrDefault("Y", false).getSupplies().getStaminaQty());
	}

	@Test
	public void switchedOffSuppliesAreNamedSoThePreflightCanDropThem()
	{
		QuestProfile.Supplies supplies = new QuestProfile.Supplies();
		supplies.setFood("Lobster");
		supplies.setFoodQty(5);
		supplies.getItems().add(new QuestCombatLoadout.InventoryItem("Varrock teleport", -1, 3, false));
		supplies.setPotionQty(QuestPotions.Kind.PRAYER_POTION, 2);
		supplies.setBuys("stamina", false);
		supplies.setBuys("food", false);
		supplies.setBuys("item:Varrock teleport", false);
		supplies.setBuys("potion:prayer_potion", false);
		Set<String> names = supplies.noBuyNames();
		assertTrue(names.contains("stamina potion"));
		assertTrue(names.contains("lobster"));
		assertTrue(names.contains("varrock teleport"));
		assertTrue(names.contains("prayer potion"));
		assertFalse(names.contains("energy potion"));
	}

	@Test
	public void theAgentServerCanSwitchAutomationAndTheSpendLimit()
	{
		Map<String, Object> body = new Gson().fromJson("{\"fullyAutomated\":true,\"maxSpendPerQuest\":250000}",
			new TypeToken<Map<String, Object>>() { }.getType());
		QuestQueueRequest request = QuestQueueRequest.parse(body, name -> true, name -> QuestProfile.defaultFor(name, false));
		assertNull(request.error);
		assertEquals(Boolean.TRUE, request.fullyAutomated);
		assertEquals(Integer.valueOf(250000), request.maxSpendPerQuest);

		Map<String, Object> bad = new Gson().fromJson("{\"maxSpendPerQuest\":-5}",
			new TypeToken<Map<String, Object>>() { }.getType());
		assertTrue(QuestQueueRequest.parse(bad, name -> true, name -> QuestProfile.defaultFor(name, false)).error != null);

		Map<String, Object> none = new Gson().fromJson("{}", new TypeToken<Map<String, Object>>() { }.getType());
		assertNull(QuestQueueRequest.parse(none, name -> true, name -> QuestProfile.defaultFor(name, false)).fullyAutomated);
		assertEquals(Arrays.asList(), new QuestQueue(new MemoryStore()).entries());
	}
}
