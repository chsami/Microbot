package net.runelite.client.plugins.microbot.inventorysetups;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.RuneLiteAPI;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptiedSlot;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.fuzzy;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.item;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class InventorySetupsPersistentDataManagerTest
{
	@Test
	@SuppressWarnings("unchecked")
	public void savedSetupLoadsBackWithEveryItemSetting() throws Exception
	{
		ConfigManager configManager = mock(ConfigManager.class);
		InventorySetup saved = setup("Vorkath", emptyContainer(28), emptyContainer(14));
		InventorySetupsItem prayer = fuzzy(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 1);
		prayer.setLocked(true);
		saved.getInventory().set(1, prayer);
		InventorySetupsItem laws = item(ItemID.LAWRUNE, "Law rune", 250, 2);
		laws.setStackCompare(InventorySetupsStackCompareID.Less_Than);
		saved.getInventory().set(2, laws);
		saved.getInventory().set(5, emptiedSlot(5, true));
		saved.getEquipment().set(3, item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		saved.updateRunePouch(Arrays.asList(item(ItemID.AIRRUNE, "Air rune", 1000, -1), InventorySetupsItem.getDummyItem()));
		saved.getAdditionalFilteredItems().put(ItemID.ANGLERFISH, fuzzy(ItemID.ANGLERFISH, "Anglerfish", 1, -1));
		List<InventorySetup> setups = new ArrayList<>(Collections.singletonList(saved));
		InventorySetupsPersistentDataManager manager = new InventorySetupsPersistentDataManager(mock(MInventorySetupsPlugin.class),
			configManager, new InventorySetupsCache(), RuneLiteAPI.GSON, setups, new ArrayList<>());

		manager.updateConfig(true, false);

		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(configManager, times(2)).setConfiguration(eq(MInventorySetupsPlugin.CONFIG_GROUP), key.capture(), json.capture());
		String order = json.getAllValues().get(key.getAllValues().indexOf(InventorySetupsPersistentDataManager.CONFIG_KEY_SETUPS_ORDER_V3));
		String wholePrefix = ConfigManager.getWholeKey(MInventorySetupsPlugin.CONFIG_GROUP, null, InventorySetupsPersistentDataManager.CONFIG_KEY_SETUPS_V3_PREFIX);
		String setupKey = key.getAllValues().stream().filter(k -> k.startsWith(InventorySetupsPersistentDataManager.CONFIG_KEY_SETUPS_V3_PREFIX)).findFirst().get();
		String setupJson = json.getAllValues().get(key.getAllValues().indexOf(setupKey));
		when(configManager.getConfigurationKeys(anyString())).thenReturn(Collections.singletonList(wholePrefix + setupKey.substring(InventorySetupsPersistentDataManager.CONFIG_KEY_SETUPS_V3_PREFIX.length())));
		when(configManager.getConfiguration(MInventorySetupsPlugin.CONFIG_GROUP, InventorySetupsPersistentDataManager.CONFIG_KEY_SETUPS_ORDER_V3)).thenReturn(order);
		when(configManager.getConfiguration(MInventorySetupsPlugin.CONFIG_GROUP, setupKey)).thenReturn(setupJson);
		Method loadV3Setups = InventorySetupsPersistentDataManager.class.getDeclaredMethod("loadV3Setups");
		loadV3Setups.setAccessible(true);

		List<InventorySetup> loaded = (List<InventorySetup>) loadV3Setups.invoke(manager);

		assertEquals(1, loaded.size());
		InventorySetup setup = loaded.get(0);
		assertEquals("Vorkath", setup.getName());
		assertTrue(setup.getInventory().get(1).isFuzzy());
		assertTrue(setup.getInventory().get(1).isLocked());
		assertEquals(1, setup.getInventory().get(1).getSlot());
		assertEquals(250, setup.getInventory().get(2).getQuantity());
		assertEquals(InventorySetupsStackCompareID.Less_Than, setup.getInventory().get(2).getStackCompare());
		assertEquals(-1, setup.getInventory().get(5).getId());
		assertEquals(5, setup.getInventory().get(5).getSlot());
		assertTrue(setup.getInventory().get(5).isFuzzy());
		assertTrue(InventorySetupsItem.itemIsDummy(setup.getInventory().get(27)));
		assertEquals(ItemID.ABYSSAL_WHIP, setup.getEquipment().get(3).getId());
		assertEquals(1000, setup.getRune_pouch().get(0).getQuantity());
		assertTrue(InventorySetupsItem.itemIsDummy(setup.getRune_pouch().get(1)));
		assertTrue(setup.getAdditionalFilteredItems().get(ItemID.ANGLERFISH).isFuzzy());
		assertTrue(setupJson.contains("\"s\":1"));
		assertFalse(setupJson.contains("\"slot\""));
	}

	@Test
	public void setupImportedWithEveryItemInTheFirstSlotIsRepaired()
	{
		InventorySetup imported = setup("Imported", emptyContainer(28), emptyContainer(14));
		imported.getInventory().set(0, item(ItemID.SHARK, "Shark", 1, 0));
		imported.getInventory().set(3, item(ItemID.LAWRUNE, "Law rune", 100, 0));
		imported.getInventory().set(5, fuzzy(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 0));
		imported.getInventory().set(9, emptiedSlot(9, true));

		assertTrue(InventorySetupsPersistentDataManager.repairSharedFirstInventorySlot(imported));

		assertEquals(0, imported.getInventory().get(0).getSlot());
		assertEquals(3, imported.getInventory().get(3).getSlot());
		assertEquals(5, imported.getInventory().get(5).getSlot());
		assertEquals(9, imported.getInventory().get(9).getSlot());
		assertEquals(28, imported.getInventory().size());
		assertEquals(ItemID.LAWRUNE, imported.getInventory().get(3).getId());
		assertEquals(100, imported.getInventory().get(3).getQuantity());
		assertTrue(InventorySetupsItem.itemIsDummy(imported.getInventory().get(1)));
		assertFalse(InventorySetupsPersistentDataManager.repairSharedFirstInventorySlot(imported));
	}

	@Test
	public void correctlySlottedSetupIsLeftAlone()
	{
		InventorySetup normal = setup("Normal", emptyContainer(28), emptyContainer(14));
		normal.getInventory().set(0, item(ItemID.SHARK, "Shark", 1, 0));
		normal.getInventory().set(3, item(ItemID.LAWRUNE, "Law rune", 100, 3));
		normal.getEquipment().set(3, item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 0));
		InventorySetup unslotted = setup("Unslotted", emptyContainer(28), emptyContainer(14));
		unslotted.getInventory().set(4, item(ItemID.SHARK, "Shark", 1, -1));
		unslotted.getInventory().set(6, item(ItemID.SHARK, "Shark", 1, 0));

		assertFalse(InventorySetupsPersistentDataManager.repairSharedFirstInventorySlot(normal));
		assertFalse(InventorySetupsPersistentDataManager.repairSharedFirstInventorySlot(unslotted));

		assertEquals(0, normal.getInventory().get(0).getSlot());
		assertEquals(3, normal.getInventory().get(3).getSlot());
		assertEquals(0, normal.getEquipment().get(3).getSlot());
		assertEquals(-1, unslotted.getInventory().get(4).getSlot());
		assertEquals(0, unslotted.getInventory().get(6).getSlot());
	}
}
