package net.runelite.client.plugins.microbot.inventorysetups.serialization;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptiedSlot;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.fuzzy;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.item;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InventorySetupSerializationTest
{
	private static final Gson STORAGE_GSON = RuneLiteAPI.GSON.newBuilder()
		.registerTypeAdapter(long.class, new LongTypeAdapter())
		.registerTypeAdapter(InventorySetupItemSerializable.class, new InventorySetupItemSerializableTypeAdapter())
		.create();

	private static InventorySetup roundTrip(InventorySetup setup)
	{
		String json = STORAGE_GSON.toJson(InventorySetupSerializable.convertFromInventorySetup(setup));
		return InventorySetupSerializable.convertToInventorySetup(STORAGE_GSON.fromJson(json, InventorySetupSerializable.class));
	}

	private static InventorySetup fullSetup()
	{
		List<InventorySetupsItem> inventory = emptyContainer(28);
		inventory.set(0, item(ItemID.SHARK, "Shark", 1, 0));
		InventorySetupsItem prayer = fuzzy(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 1);
		prayer.setLocked(true);
		inventory.set(1, prayer);
		InventorySetupsItem laws = item(ItemID.LAWRUNE, "Law rune", 250, 2);
		laws.setStackCompare(InventorySetupsStackCompareID.Less_Than);
		inventory.set(2, laws);
		List<InventorySetupsItem> equipment = emptyContainer(14);
		equipment.set(3, item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		List<InventorySetupsItem> runePouch = Arrays.asList(item(ItemID.AIRRUNE, "Air rune", 1000, -1),
			InventorySetupsItem.getDummyItem(), item(ItemID.FIRERUNE, "Fire rune", 500, -1));
		List<InventorySetupsItem> boltPouch = Arrays.asList(item(ItemID.DRAGON_BOLTS, "Dragon bolts", 300, -1),
			InventorySetupsItem.getDummyItem(), InventorySetupsItem.getDummyItem(), InventorySetupsItem.getDummyItem());
		List<InventorySetupsItem> quiver = Arrays.asList(item(ItemID.AMETHYST_ARROW, "Amethyst arrow", 800, -1));
		Map<Integer, InventorySetupsItem> additional = new HashMap<>();
		additional.put(ItemID.ANGLERFISH, fuzzy(ItemID.ANGLERFISH, "Anglerfish", 1, -1));
		InventorySetup setup = new InventorySetup(inventory, equipment, runePouch, boltPouch, quiver, additional,
			"Vorkath", "bring antifire", Color.RED, true, Color.BLUE, true, true, 2, true, ItemID.ABYSSAL_WHIP);
		return setup;
	}

	@Test
	public void everySetupFieldSurvivesSaveAndLoad()
	{
		InventorySetup loaded = roundTrip(fullSetup());

		assertEquals("Vorkath", loaded.getName());
		assertEquals("bring antifire", loaded.getNotes());
		assertEquals(Color.RED, loaded.getHighlightColor());
		assertEquals(Color.BLUE, loaded.getDisplayColor());
		assertTrue(loaded.isHighlightDifference());
		assertTrue(loaded.isFilterBank());
		assertTrue(loaded.isUnorderedHighlight());
		assertTrue(loaded.isFavorite());
		assertEquals(2, loaded.getSpellBook());
		assertEquals(ItemID.ABYSSAL_WHIP, loaded.getIconID());
		assertEquals(28, loaded.getInventory().size());
		assertEquals(14, loaded.getEquipment().size());
	}

	@Test
	public void everyItemFieldSurvivesSaveAndLoad()
	{
		InventorySetup loaded = roundTrip(fullSetup());

		InventorySetupsItem prayer = loaded.getInventory().get(1);
		assertEquals(ItemID._4DOSEPRAYERRESTORE, prayer.getId());
		assertTrue(prayer.isFuzzy());
		assertTrue(prayer.isLocked());
		assertEquals(1, prayer.getSlot());
		assertEquals(1, prayer.getQuantity());

		InventorySetupsItem laws = loaded.getInventory().get(2);
		assertEquals(250, laws.getQuantity());
		assertEquals(InventorySetupsStackCompareID.Less_Than, laws.getStackCompare());
		assertFalse(laws.isFuzzy());
		assertFalse(laws.isLocked());

		assertEquals(ItemID.ABYSSAL_WHIP, loaded.getEquipment().get(3).getId());
		assertEquals(3, loaded.getEquipment().get(3).getSlot());
		assertTrue(InventorySetupsItem.itemIsDummy(loaded.getInventory().get(27)));
	}

	@Test
	public void specialContainersSurviveSaveAndLoad()
	{
		InventorySetup loaded = roundTrip(fullSetup());

		assertEquals(3, loaded.getRune_pouch().size());
		assertEquals(1000, loaded.getRune_pouch().get(0).getQuantity());
		assertTrue(InventorySetupsItem.itemIsDummy(loaded.getRune_pouch().get(1)));
		assertEquals(ItemID.FIRERUNE, loaded.getRune_pouch().get(2).getId());
		assertEquals(ItemID.DRAGON_BOLTS, loaded.getBoltPouch().get(0).getId());
		assertEquals(4, loaded.getBoltPouch().size());
		assertEquals(800, loaded.getQuiver().get(0).getQuantity());
		assertTrue(loaded.getAdditionalFilteredItems().get(ItemID.ANGLERFISH).isFuzzy());
	}

	@Test
	public void setupWithoutPouchesStillHasNoPouchesAfterLoad()
	{
		InventorySetup setup = fullSetup();
		setup.updateRunePouch(null);
		setup.updateBoltPouch(null);
		setup.updateQuiver(null);

		InventorySetup loaded = roundTrip(setup);

		assertNull(loaded.getRune_pouch());
		assertNull(loaded.getBoltPouch());
		assertNull(loaded.getQuiver());
	}

	@Test
	public void emptiedSlotKeepsItsSlotSettingsAfterLoad()
	{
		InventorySetup setup = fullSetup();
		InventorySetupsItem emptied = emptiedSlot(5, true);
		emptied.setStackCompare(InventorySetupsStackCompareID.Standard);
		setup.getInventory().set(5, emptied);

		InventorySetupsItem loaded = roundTrip(setup).getInventory().get(5);

		assertEquals(-1, loaded.getId());
		assertEquals(0, loaded.getQuantity());
		assertTrue(loaded.isFuzzy());
		assertEquals(InventorySetupsStackCompareID.Standard, loaded.getStackCompare());
		assertEquals(5, loaded.getSlot());
	}

	@Test
	public void defaultsAreLeftOutOfTheSavedJson()
	{
		String json = STORAGE_GSON.toJson(InventorySetupItemSerializable.convertFromInventorySetupItem(item(ItemID.SHARK, "Shark", 1, 4)));

		assertEquals("{\"id\":" + ItemID.SHARK + ",\"s\":4}", json);
	}

	@Test
	public void namesAreNotSavedAndLoadAsEmpty()
	{
		InventorySetup loaded = roundTrip(fullSetup());

		assertEquals("", loaded.getInventory().get(0).getName());
	}

	@Test
	public void dummyIsSavedAsNullAndLoadsBackAsDummy()
	{
		assertNull(InventorySetupItemSerializable.convertFromInventorySetupItem(InventorySetupsItem.getDummyItem()));
		assertNull(InventorySetupItemSerializable.convertFromInventorySetupItem(null));
		assertTrue(InventorySetupsItem.itemIsDummy(InventorySetupItemSerializable.convertToInventorySetupItem(null)));
	}

	@Test
	public void savedSetupFromBeforeSlotsLoadsItemsAsUnslotted()
	{
		String json = "{\"inv\":[{\"id\":" + ItemID.SHARK + ",\"q\":1},null],\"eq\":[null],\"name\":\"Old\"}";

		InventorySetup loaded = InventorySetupSerializable.convertToInventorySetup(STORAGE_GSON.fromJson(json, InventorySetupSerializable.class));

		assertEquals(-1, loaded.getInventory().get(0).getSlot());
		assertTrue(InventorySetupsItem.itemIsDummy(loaded.getInventory().get(1)));
	}

	@Test
	public void missingOptionalSetupFieldsLoadAsDefaults()
	{
		String json = "{\"inv\":[],\"eq\":[],\"name\":\"Bare\"}";

		InventorySetup loaded = InventorySetupSerializable.convertToInventorySetup(STORAGE_GSON.fromJson(json, InventorySetupSerializable.class));

		assertEquals("Bare", loaded.getName());
		assertEquals("", loaded.getNotes());
		assertNotNull(loaded.getAdditionalFilteredItems());
		assertTrue(loaded.getAdditionalFilteredItems().isEmpty());
		assertEquals(0, loaded.getSpellBook());
		assertEquals(-1, loaded.getIconID());
		assertFalse(loaded.isFavorite());
		assertFalse(loaded.isFilterBank());
		assertNull(loaded.getRune_pouch());
	}

	@Test(timeout = 10000)
	public void unknownItemFieldsAreIgnored()
	{
		String json = "{\"id\":" + ItemID.SHARK + ",\"future\":true,\"s\":2}";

		InventorySetupItemSerializable loaded = STORAGE_GSON.fromJson(json, InventorySetupItemSerializable.class);

		assertEquals(ItemID.SHARK, loaded.getId());
		assertEquals(2, loaded.getS());
	}

	@Test
	public void longValuesThatOverflowAreClampedInsteadOfFailing()
	{
		Gson gson = RuneLiteAPI.GSON.newBuilder().registerTypeAdapter(long.class, new LongTypeAdapter()).create();

		assertEquals(Long.MAX_VALUE, (long) gson.fromJson("\"99999999999999999999\"", long.class));
		assertEquals(42L, (long) gson.fromJson("42", long.class));
	}

	@Test
	public void storedJsonUsesTheCompactKeys()
	{
		JsonObject json = new JsonParser().parse(STORAGE_GSON.toJson(InventorySetupSerializable.convertFromInventorySetup(fullSetup()))).getAsJsonObject();

		assertTrue(json.has("inv"));
		assertTrue(json.has("rp"));
		assertTrue(json.has("afi"));
		JsonObject laws = json.getAsJsonArray("inv").get(2).getAsJsonObject();
		assertEquals(250, laws.get("q").getAsInt());
		assertEquals("Less_Than", laws.get("sc").getAsString());
	}

	@Test(timeout = 10000)
	public void unknownSetupFieldsInsideItemsDoNotStallLoading()
	{
		String json = "{\"inv\":[{\"id\":" + ItemID.SHARK + ",\"charges\":{\"left\":3},\"s\":0}],\"eq\":[],\"name\":\"Newer\"}";

		InventorySetup loaded = InventorySetupSerializable.convertToInventorySetup(STORAGE_GSON.fromJson(json, InventorySetupSerializable.class));

		assertEquals(ItemID.SHARK, loaded.getInventory().get(0).getId());
		assertEquals(0, loaded.getInventory().get(0).getSlot());
	}

	@Test(timeout = 10000)
	public void importedSetupWithoutSlotsLoadsItemsUnslotted()
	{
		String portable = "{\"setup\":{\"inv\":[{\"id\":" + ItemID.SHARK + "},{\"id\":" + ItemID.LAWRUNE + ",\"q\":100},null],"
			+ "\"eq\":[{\"id\":" + ItemID.ABYSSAL_WHIP + "}],\"name\":\"Shared\"},\"layout\":[" + ItemID.SHARK + "]}";

		InventorySetup imported = RuneLiteAPI.GSON.fromJson(portable, InventorySetupPortable.class).getSerializedSetup();

		assertEquals(-1, imported.getInventory().get(0).getSlot());
		assertEquals(-1, imported.getInventory().get(1).getSlot());
		assertEquals(100, imported.getInventory().get(1).getQuantity());
		assertEquals(-1, imported.getEquipment().get(0).getSlot());
		assertTrue(InventorySetupsItem.itemIsDummy(imported.getInventory().get(2)));
	}

	@Test(timeout = 10000)
	public void exportedSetupImportsWithTheSameItems()
	{
		InventorySetup setup = fullSetup();
		setup.getInventory().set(5, emptiedSlot(5, true));
		InventorySetupPortable exported = new InventorySetupPortable(InventorySetupSerializable.convertFromInventorySetup(setup), new int[]{ItemID.SHARK});

		InventorySetup imported = RuneLiteAPI.GSON.fromJson(RuneLiteAPI.GSON.toJson(exported), InventorySetupPortable.class).getSerializedSetup();

		assertEquals(1, imported.getInventory().get(1).getSlot());
		assertTrue(imported.getInventory().get(1).isFuzzy());
		assertTrue(imported.getInventory().get(1).isLocked());
		assertEquals(InventorySetupsStackCompareID.Less_Than, imported.getInventory().get(2).getStackCompare());
		assertEquals(5, imported.getInventory().get(5).getSlot());
		assertTrue(imported.getInventory().get(5).isFuzzy());
		assertTrue(InventorySetupsItem.itemIsDummy(imported.getInventory().get(27)));
		assertTrue(imported.getAdditionalFilteredItems().get(ItemID.ANGLERFISH).isFuzzy());
		assertEquals(28, imported.getInventory().size());
	}

	@Test(timeout = 10000)
	public void nullItemFieldsLoadAsDefaults()
	{
		String json = "{\"id\":" + ItemID.SHARK + ",\"q\":null,\"f\":null,\"sc\":null,\"l\":null,\"s\":null}";

		InventorySetupsItem loaded = InventorySetupItemSerializable.convertToInventorySetupItem(RuneLiteAPI.GSON.fromJson(json, InventorySetupItemSerializable.class));

		assertEquals(ItemID.SHARK, loaded.getId());
		assertEquals(1, loaded.getQuantity());
		assertFalse(loaded.isFuzzy());
		assertFalse(loaded.isLocked());
		assertEquals(InventorySetupsStackCompareID.None, loaded.getStackCompare());
		assertEquals(-1, loaded.getSlot());
	}
}
