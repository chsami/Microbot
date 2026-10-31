package net.runelite.client.plugins.microbot.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.event.Level;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptiedSlot;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.emptyContainer;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.fuzzy;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.item;
import static net.runelite.client.plugins.microbot.inventorysetups.InventorySetupTestData.setup;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class InventorySetupMatchingTest
{
	private final List<Rs2ItemModel> inventory = new ArrayList<>();
	private final List<Rs2ItemModel> worn = new ArrayList<>();
	private MockedStatic<Microbot> microbot;
	private MockedStatic<Global> global;
	private MockedStatic<Rs2Inventory> rs2Inventory;
	private MockedStatic<Rs2Equipment> rs2Equipment;
	private MockedStatic<Rs2Bank> rs2Bank;
	private MockedStatic<Rs2RunePouch> rs2RunePouch;

	@Before
	public void setUp()
	{
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());
		microbot = mockStatic(Microbot.class);
		microbot.when(Microbot::getClientThread).thenReturn(clientThread);
		global = mockStatic(Global.class);
		rs2Inventory = mockStatic(Rs2Inventory.class, CALLS_REAL_METHODS);
		rs2Inventory.when(Rs2Inventory::items).thenAnswer(call -> new ArrayList<>(inventory).stream());
		rs2Equipment = mockStatic(Rs2Equipment.class, CALLS_REAL_METHODS);
		rs2Equipment.when(Rs2Equipment::items).thenAnswer(call -> new ArrayList<>(worn));
		rs2Bank = mockStatic(Rs2Bank.class);
		rs2RunePouch = mockStatic(Rs2RunePouch.class);
	}

	@After
	public void tearDown()
	{
		rs2RunePouch.close();
		rs2Bank.close();
		rs2Equipment.close();
		rs2Inventory.close();
		global.close();
		microbot.close();
	}

	private static Rs2ItemModel held(int id, String name, int quantity, int slot)
	{
		Rs2ItemModel model = mock(Rs2ItemModel.class);
		when(model.getId()).thenReturn(id);
		when(model.getName()).thenReturn(name);
		when(model.getQuantity()).thenReturn(quantity);
		when(model.getSlot()).thenReturn(slot);
		return model;
	}

	private static InventorySetup gearSetup(InventorySetupsItem... gear)
	{
		List<InventorySetupsItem> equipment = emptyContainer(14);
		for (InventorySetupsItem piece : gear)
		{
			equipment.set(piece.getSlot(), piece);
		}
		return setup("Gear", emptyContainer(28), equipment);
	}

	private static InventorySetup inventorySetup(InventorySetupsItem... items)
	{
		List<InventorySetupsItem> inv = emptyContainer(28);
		int unslotted = 27;
		for (InventorySetupsItem setupItem : items)
		{
			inv.set(setupItem.getSlot() >= 0 ? setupItem.getSlot() : unslotted--, setupItem);
		}
		return setup("Supplies", inv, emptyContainer(14));
	}

	private static Rs2InventorySetup loader(InventorySetup setup)
	{
		return new Rs2InventorySetup(setup, null);
	}

	private static InventorySetupsItem withStack(InventorySetupsItem setupItem, InventorySetupsStackCompareID stackCompare)
	{
		setupItem.setStackCompare(stackCompare);
		return setupItem;
	}

	@Test
	public void equipmentMatchesWhenEveryPieceIsWorn()
	{
		worn.add(held(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		worn.add(held(ItemID.DRAGON_PARRYINGDAGGER, "Dragon defender", 1, 5));

		assertTrue(loader(gearSetup(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3),
			item(ItemID.DRAGON_PARRYINGDAGGER, "Dragon defender", 1, 5))).doesEquipmentMatch());
	}

	@Test
	public void equipmentDoesNotMatchWhenAPieceIsMissing()
	{
		worn.add(held(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));

		assertFalse(loader(gearSetup(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3),
			item(ItemID.DRAGON_PARRYINGDAGGER, "Dragon defender", 1, 5))).doesEquipmentMatch());
	}

	@Test
	public void strictEquipmentNeedsTheExactName()
	{
		worn.add(held(ItemID.RING_OF_DUELING_3, "Ring of dueling(3)", 1, 12));

		assertFalse(loader(gearSetup(item(ItemID.RING_OF_DUELING_8, "Ring of dueling(8)", 1, 12))).doesEquipmentMatch());
		assertTrue(loader(gearSetup(fuzzy(ItemID.RING_OF_DUELING_8, "Ring of dueling(8)", 1, 12))).doesEquipmentMatch());
	}

	@Test
	public void degradedBarrowsPieceSatisfiesTheSetup()
	{
		worn.add(held(ItemID.BARROWS_DHAROK_BODY_75, "Dharok's platebody 75", 1, 4));

		assertTrue(loader(gearSetup(item(ItemID.BARROWS_DHAROK_BODY, "Dharok's platebody", 1, 4))).doesEquipmentMatch());
		assertTrue(loader(gearSetup(item(ItemID.BARROWS_DHAROK_BODY_100, "Dharok's platebody 100", 1, 4))).doesEquipmentMatch());
	}

	@Test
	public void equipmentCheckWithoutASetupFails()
	{
		boolean paused = Microbot.pauseAllScripts.get();
		try
		{
			Rs2InventorySetup missing = new Rs2InventorySetup((InventorySetup) null, null);
			assertFalse(missing.doesEquipmentMatch());
			assertFalse(missing.doesInventoryMatch());
			assertFalse(missing.loadInventory());
			assertFalse(missing.loadEquipment());
		}
		finally
		{
			Microbot.pauseAllScripts.set(paused);
		}
	}

	@Test
	public void loadEquipmentSkipsTheBankWhenAlreadyGeared()
	{
		worn.add(held(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));

		assertTrue(loader(gearSetup(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3))).loadEquipment());
		rs2Bank.verify(Rs2Bank::openBank, never());
	}

	@Test
	public void slottedItemMustBeInItsSlot()
	{
		InventorySetup setup = inventorySetup(item(ItemID.SHARK, "Shark", 1, 0));

		inventory.add(held(ItemID.SHARK, "Shark", 1, 0));
		assertTrue(loader(setup).doesInventoryMatch());

		inventory.clear();
		inventory.add(held(ItemID.SHARK, "Shark", 1, 1));
		assertFalse(loader(setup).doesInventoryMatch());
	}

	@Test
	public void lowerDoseInAStrictSlotStillMatches()
	{
		inventory.add(held(ItemID._3DOSEPRAYERRESTORE, "Prayer potion(3)", 1, 2));

		assertTrue(loader(inventorySetup(item(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 2))).doesInventoryMatch());
	}

	@Test
	public void differentPotionInASlotDoesNotMatch()
	{
		inventory.add(held(ItemID._4DOSE2RESTORE, "Super restore(4)", 1, 2));

		assertFalse(loader(inventorySetup(item(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 2))).doesInventoryMatch());
	}

	@Test
	public void fuzzySlotAcceptsAnyDose()
	{
		inventory.add(held(ItemID._1DOSEPRAYERRESTORE, "Prayer potion(1)", 1, 2));

		assertTrue(loader(inventorySetup(fuzzy(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 2))).doesInventoryMatch());
	}

	@Test
	public void stackWithoutCompareNeedsTheExactAmount()
	{
		InventorySetup setup = inventorySetup(item(ItemID.LAWRUNE, "Law rune", 100, 0));

		inventory.add(held(ItemID.LAWRUNE, "Law rune", 99, 0));
		assertFalse(loader(setup).doesInventoryMatch());

		inventory.clear();
		inventory.add(held(ItemID.LAWRUNE, "Law rune", 100, 0));
		assertTrue(loader(setup).doesInventoryMatch());
	}

	@Test
	public void greaterThanStackAcceptsUpToTheSavedAmount()
	{
		InventorySetup setup = inventorySetup(withStack(item(ItemID.LAWRUNE, "Law rune", 100, 0), InventorySetupsStackCompareID.Greater_Than));

		inventory.add(held(ItemID.LAWRUNE, "Law rune", 40, 0));
		assertTrue(loader(setup).doesInventoryMatch());

		inventory.clear();
		inventory.add(held(ItemID.LAWRUNE, "Law rune", 101, 0));
		assertFalse(loader(setup).doesInventoryMatch());
	}

	@Test
	public void lessThanAndNotEqualStacksOnlyNeedTheItem()
	{
		inventory.add(held(ItemID.LAWRUNE, "Law rune", 3, 0));

		assertTrue(loader(inventorySetup(withStack(item(ItemID.LAWRUNE, "Law rune", 100, 0), InventorySetupsStackCompareID.Less_Than))).doesInventoryMatch());
		assertTrue(loader(inventorySetup(withStack(item(ItemID.LAWRUNE, "Law rune", 100, 0), InventorySetupsStackCompareID.Standard))).doesInventoryMatch());
	}

	@Test
	public void unslottedRowsAreCountedTogether()
	{
		InventorySetup setup = inventorySetup(item(ItemID.SHARK, "Shark", 1, -1), item(ItemID.SHARK, "Shark", 1, -1),
			item(ItemID.SHARK, "Shark", 1, -1));

		inventory.add(held(ItemID.SHARK, "Shark", 1, 5));
		inventory.add(held(ItemID.SHARK, "Shark", 1, 9));
		assertFalse(loader(setup).doesInventoryMatch());

		inventory.add(held(ItemID.SHARK, "Shark", 1, 20));
		assertTrue(loader(setup).doesInventoryMatch());
	}

	@Test
	public void runePouchNeedsAFifthOfEachSavedRune()
	{
		InventorySetup setup = inventorySetup();
		setup.updateRunePouch(Arrays.asList(item(ItemID.AIRRUNE, "Air rune", 1000, -1), InventorySetupsItem.getDummyItem(),
			item(ItemID.LAWRUNE, "Law rune", 3, -1)));
		rs2RunePouch.when(() -> Rs2RunePouch.contains(anyMap(), anyBoolean())).thenReturn(true);

		assertTrue(loader(setup).doesInventoryMatch());

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Runes, Integer>> required = ArgumentCaptor.forClass(Map.class);
		rs2RunePouch.verify(() -> Rs2RunePouch.contains(required.capture(), org.mockito.ArgumentMatchers.eq(false)));
		assertEquals(Integer.valueOf(200), required.getValue().get(Runes.AIR));
		assertEquals(Integer.valueOf(1), required.getValue().get(Runes.LAW));
		assertEquals(2, required.getValue().size());
	}

	@Test
	public void missingRunePouchContentsFailTheCheck()
	{
		InventorySetup setup = inventorySetup();
		setup.updateRunePouch(Arrays.asList(item(ItemID.AIRRUNE, "Air rune", 1000, -1)));
		rs2RunePouch.when(() -> Rs2RunePouch.contains(anyMap(), anyBoolean())).thenReturn(false);

		assertFalse(loader(setup).doesInventoryMatch());
	}

	@Test
	public void itemsOutsideTheSetupAreReported()
	{
		InventorySetup setup = inventorySetup(item(ItemID.SHARK, "Shark", 1, 0), fuzzy(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 1));
		inventory.add(held(ItemID.SHARK, "Shark", 1, 0));
		inventory.add(held(ItemID._2DOSEPRAYERRESTORE, "Prayer potion(2)", 1, 1));
		inventory.add(held(ItemID.BONES, "Bones", 1, 2));

		List<Rs2ItemModel> extra = loader(setup).getItemsNotInInventorySetup(false);

		assertEquals(Collections.singletonList("Bones"), extra.stream().map(Rs2ItemModel::getName).collect(Collectors.toList()));
		assertTrue(loader(setup).hasNotInventorySetup(false));
	}

	@Test
	public void wornItemsOutsideTheSetupAreReported()
	{
		InventorySetup setup = gearSetup(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		worn.add(held(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		worn.add(held(ItemID.GRACEFUL_BOOTS, "Graceful boots", 1, 10));

		List<Rs2ItemModel> extra = loader(setup).getEquipmentNotInSetup(false);

		assertEquals(Collections.singletonList("Graceful boots"), extra.stream().map(Rs2ItemModel::getName).collect(Collectors.toList()));
	}

	@Test
	public void anySpellbookSettingAcceptsEverySpellbook()
	{
		InventorySetup setup = inventorySetup();
		setup.updateSpellbook(4);
		microbot.when(() -> Microbot.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(2);
		assertTrue(loader(setup).hasSpellBook());

		setup.updateSpellbook(1);
		assertFalse(loader(setup).hasSpellBook());
		setup.updateSpellbook(2);
		assertTrue(loader(setup).hasSpellBook());
	}

	@Test
	public void setupItemListsLeaveOutEmptySlots()
	{
		InventorySetup setup = inventorySetup(item(ItemID.SHARK, "Shark", 1, 0));
		setup.getEquipment().set(3, item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));

		assertEquals(1, loader(setup).getInventoryItems().size());
		assertEquals(1, loader(setup).getEquipmentItems().size());
		assertTrue(loader(setup).getAdditionalItems().isEmpty());
	}

	@Test
	public void keepListMarksNamesFuzzyWhenAnyRowIsFuzzy()
	{
		InventorySetup setup = inventorySetup(item(ItemID.SHARK, "Shark", 1, 0), fuzzy(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 1));

		Map<String, Boolean> keep = loader(setup).itemsToNotDeposit();

		assertEquals(Boolean.FALSE, keep.get("Shark"));
		assertEquals(Boolean.TRUE, keep.get("Prayer potion"));
		assertEquals(2, keep.size());
	}

	private void bankReadyForLoading()
	{
		rs2Bank.when(Rs2Bank::isOpen).thenReturn(true);
		rs2Bank.when(() -> Rs2Bank.verifyBankMirrorAfterOpen(true, 0)).thenReturn(true);
		rs2Bank.when(() -> Rs2Bank.isInventoryItemRetainedForSetupDeposit(any(), any(), any())).thenReturn(true);
		rs2Inventory.when(() -> Rs2Inventory.moveItemToSlot(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
	}

	@Test
	public void emptiedEquipmentSlotDoesNotNeedGear()
	{
		worn.add(held(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		InventorySetup setup = gearSetup(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3), emptiedSlot(0, false));

		assertTrue(loader(setup).doesEquipmentMatch());
	}

	@Test
	public void emptiedEquipmentSlotDoesNotSendTheLoaderToTheBank()
	{
		worn.add(held(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		InventorySetupsItem emptiedHelm = emptiedSlot(0, false);
		emptiedHelm.setName("null");

		assertTrue(loader(gearSetup(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3), emptiedHelm)).loadEquipment());
		rs2Bank.verify(Rs2Bank::openBank, never());
	}

	@Test
	public void emptiedFuzzySlotDoesNotHideItemsOutsideTheSetup()
	{
		InventorySetup setup = inventorySetup(item(ItemID.SHARK, "Shark", 1, 0), emptiedSlot(5, true));
		inventory.add(held(ItemID.SHARK, "Shark", 1, 0));
		inventory.add(held(ItemID.BONES, "Bones", 1, 1));

		List<Rs2ItemModel> extra = loader(setup).getItemsNotInInventorySetup(false);

		assertEquals(Collections.singletonList("Bones"), extra.stream().map(Rs2ItemModel::getName).collect(Collectors.toList()));
	}

	@Test
	public void loadingNeverMovesItemsIntoAnEmptiedSlot()
	{
		bankReadyForLoading();
		InventorySetup setup = inventorySetup(item(ItemID.SHARK, "Shark", 1, 0), item(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 1),
			emptiedSlot(5, true));
		inventory.add(held(ItemID.SHARK, "Shark", 1, 0));
		Rs2ItemModel prayer = held(ItemID._4DOSEPRAYERRESTORE, "Prayer potion(4)", 1, 3);
		inventory.add(prayer);

		loader(setup).loadInventory(false);

		rs2Inventory.verify(() -> Rs2Inventory.moveItemToSlot(prayer, 1));
		rs2Inventory.verify(() -> Rs2Inventory.moveItemToSlot(any(), org.mockito.ArgumentMatchers.eq(5)), never());
	}

	@Test
	public void runePouchLoadSkipsRunesTheClientCannotIdentify()
	{
		bankReadyForLoading();
		InventorySetup setup = inventorySetup();
		setup.updateRunePouch(Arrays.asList(item(ItemID.AETHERRUNE, "Aether rune", 500, -1), item(ItemID.LAWRUNE, "Law rune", 100, -1),
			InventorySetupsItem.getDummyItem()));
		rs2RunePouch.when(() -> Rs2RunePouch.loadFromInventorySetup(anyMap())).thenReturn(true);
		rs2RunePouch.when(() -> Rs2RunePouch.contains(anyMap(), anyBoolean())).thenReturn(true);

		assertTrue(loader(setup).loadInventory(false));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<Runes, InventorySetupsItem>> requested = ArgumentCaptor.forClass(Map.class);
		rs2RunePouch.verify(() -> Rs2RunePouch.loadFromInventorySetup(requested.capture()));
		assertFalse(requested.getValue().containsKey(null));
		assertEquals(ItemID.LAWRUNE, requested.getValue().get(Runes.LAW).getId());
		microbot.verify(() -> Microbot.log(eq(Level.WARN), contains("rune pouch is not supported yet"), eq("Aether rune"), eq("Aether rune")));
	}

	@Test
	public void degradedBarrowsGearStaysOnWhileLoading()
	{
		bankReadyForLoading();
		worn.add(held(ItemID.BARROWS_DHAROK_HEAD_100, "Dharok's helm 100", 1, 0));
		worn.add(held(ItemID.BARROWS_DHAROK_BODY_75, "Dharok's platebody 75", 1, 4));
		InventorySetup setup = gearSetup(item(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0),
			item(ItemID.BARROWS_DHAROK_BODY_100, "Dharok's platebody 100", 1, 4));

		assertTrue(loader(setup).loadEquipment(false));
		rs2Bank.verify(Rs2Bank::depositEquipment, never());
	}

	@Test
	public void wornItemOutsideTheSetupIsStillBankedFirst()
	{
		bankReadyForLoading();
		worn.add(held(ItemID.BARROWS_DHAROK_HEAD_100, "Dharok's helm 100", 1, 0));
		worn.add(held(ItemID.GRACEFUL_BOOTS, "Graceful boots", 1, 10));
		InventorySetup setup = gearSetup(item(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0));

		loader(setup).loadEquipment(false);

		rs2Bank.verify(Rs2Bank::depositEquipment);
		microbot.verify(() -> Microbot.log(eq(Level.INFO), contains("extra gear not in setup"), eq("Graceful boots")));
	}

	@Test
	public void brokenBarrowsPieceDoesNotSatisfyTheSetup()
	{
		worn.add(held(ItemID.BARROWS_DHAROK_HEAD_BROKEN, "Dharok's helm 0", 1, 0));

		assertFalse(loader(gearSetup(item(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0))).doesEquipmentMatch());
		assertFalse(loader(gearSetup(fuzzy(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0))).doesEquipmentMatch());
	}

	@Test
	public void brokenBarrowsPieceIsBankedAndReplaced()
	{
		bankReadyForLoading();
		worn.add(held(ItemID.BARROWS_DHAROK_HEAD_BROKEN, "Dharok's helm 0", 1, 0));
		rs2Bank.when(() -> Rs2Bank.depositEquippedItem(ItemID.BARROWS_DHAROK_HEAD_BROKEN)).thenAnswer(call -> worn.removeIf(piece -> piece.getId() == ItemID.BARROWS_DHAROK_HEAD_BROKEN));
		List<Rs2ItemModel> bank = Arrays.asList(held(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0), held(ItemID.BARROWS_DHAROK_BODY, "Dharok's platebody", 1, 1));
		rs2Bank.when(Rs2Bank::bankItems).thenReturn(bank);
		rs2Bank.when(() -> Rs2Bank.hasItem(org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
		rs2Bank.when(() -> Rs2Bank.withdrawAndEquip(ItemID.BARROWS_DHAROK_HEAD)).thenAnswer(call -> worn.add(held(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0)));
		rs2Bank.when(() -> Rs2Bank.withdrawAndEquip(ItemID.BARROWS_DHAROK_BODY)).thenAnswer(call -> worn.add(held(ItemID.BARROWS_DHAROK_BODY, "Dharok's platebody", 1, 4)));
		InventorySetup setup = gearSetup(item(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0),
			item(ItemID.BARROWS_DHAROK_BODY, "Dharok's platebody", 1, 4));

		assertTrue(loader(setup).loadEquipment());

		rs2Bank.verify(() -> Rs2Bank.depositEquippedItem(ItemID.BARROWS_DHAROK_HEAD_BROKEN));
		rs2Bank.verify(Rs2Bank::depositEquipment, never());
		rs2Bank.verify(() -> Rs2Bank.withdrawAndEquip(ItemID.BARROWS_DHAROK_HEAD));
		rs2Bank.verify(() -> Rs2Bank.withdrawAndEquip(ItemID.BARROWS_DHAROK_BODY));
		assertEquals(Arrays.asList("Dharok's helm", "Dharok's platebody"), worn.stream().map(Rs2ItemModel::getName).collect(Collectors.toList()));
	}

	@Test
	public void emptiedFuzzyEquipmentSlotDoesNotKeepForeignGearOn()
	{
		bankReadyForLoading();
		worn.add(held(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3));
		worn.add(held(ItemID.GRACEFUL_BOOTS, "Graceful boots", 1, 10));
		InventorySetup setup = gearSetup(item(ItemID.ABYSSAL_WHIP, "Abyssal whip", 1, 3), emptiedSlot(10, true));

		loader(setup).loadEquipment(false);

		rs2Bank.verify(Rs2Bank::depositEquipment);
	}

	@Test
	public void brokenBarrowsPieceWithoutAWorkingCopyStopsWithABrokenMessage()
	{
		boolean paused = Microbot.pauseAllScripts.getAndSet(false);
		try
		{
			bankReadyForLoading();
			List<Rs2ItemModel> bank = new ArrayList<>();
			rs2Bank.when(Rs2Bank::bankItems).thenAnswer(call -> new ArrayList<>(bank));
			rs2Bank.when(() -> Rs2Bank.hasBankItem(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyBoolean()))
				.thenAnswer(call -> bank.stream().anyMatch(b -> b.getName().toLowerCase().contains(((String) call.getArgument(0)).toLowerCase())));
			worn.add(held(ItemID.BARROWS_DHAROK_HEAD_BROKEN, "Dharok's helm 0", 1, 0));
			worn.add(held(ItemID.BARROWS_DHAROK_BODY_100, "Dharok's platebody 100", 1, 4));
			rs2Bank.when(() -> Rs2Bank.depositEquippedItem(ItemID.BARROWS_DHAROK_HEAD_BROKEN)).thenAnswer(call -> {
				worn.removeIf(piece -> piece.getId() == ItemID.BARROWS_DHAROK_HEAD_BROKEN);
				return bank.add(held(ItemID.BARROWS_DHAROK_HEAD_BROKEN, "Dharok's helm 0", 1, 0));
			});
			InventorySetup setup = gearSetup(item(ItemID.BARROWS_DHAROK_HEAD, "Dharok's helm", 1, 0),
				item(ItemID.BARROWS_DHAROK_BODY, "Dharok's platebody", 1, 4));

			assertFalse(loader(setup).loadEquipment());

			assertTrue(Microbot.pauseAllScripts.get());
			rs2Bank.verify(() -> Rs2Bank.withdrawAndEquip(ItemID.BARROWS_DHAROK_HEAD), never());
			microbot.verify(() -> Microbot.log(eq(Level.WARN), contains("is broken"), eq("Dharok's helm"), eq("Dharok's helm 0")));
			microbot.verify(() -> Microbot.log(eq(Level.WARN), contains("missing gear"), eq("Dharok's helm"), eq(0), eq(0), eq(1), eq("pausing")));
		}
		finally
		{
			Microbot.pauseAllScripts.set(paused);
		}
	}

	@Test
	public void onlyTheBrokenPieceIsBankedSoDegradedPiecesStayOn()
	{
		bankReadyForLoading();
		worn.add(held(ItemID.BARROWS_GUTHAN_HEAD_BROKEN, "Guthan's helm 0", 1, 0));
		worn.add(held(ItemID.BARROWS_GUTHAN_BODY_50, "Guthan's platebody 50", 1, 4));
		rs2Bank.when(() -> Rs2Bank.depositEquippedItem(ItemID.BARROWS_GUTHAN_HEAD_BROKEN)).thenAnswer(call -> worn.removeIf(piece -> piece.getId() == ItemID.BARROWS_GUTHAN_HEAD_BROKEN));
		List<Rs2ItemModel> bank = Collections.singletonList(held(ItemID.BARROWS_GUTHAN_HEAD, "Guthan's helm", 1, 0));
		rs2Bank.when(Rs2Bank::bankItems).thenReturn(bank);
		rs2Bank.when(() -> Rs2Bank.hasItem(ItemID.BARROWS_GUTHAN_HEAD)).thenReturn(true);
		rs2Bank.when(() -> Rs2Bank.withdrawAndEquip(ItemID.BARROWS_GUTHAN_HEAD)).thenAnswer(call -> worn.add(held(ItemID.BARROWS_GUTHAN_HEAD, "Guthan's helm", 1, 0)));
		InventorySetup setup = gearSetup(item(ItemID.BARROWS_GUTHAN_HEAD, "Guthan's helm", 1, 0),
			item(ItemID.BARROWS_GUTHAN_BODY_100, "Guthan's platebody 100", 1, 4));

		assertTrue(loader(setup).loadEquipment());

		rs2Bank.verify(Rs2Bank::depositEquipment, never());
		rs2Bank.verify(() -> Rs2Bank.depositEquippedItem(ItemID.BARROWS_GUTHAN_BODY_50), never());
		assertEquals(Arrays.asList("Guthan's platebody 50", "Guthan's helm"), worn.stream().map(Rs2ItemModel::getName).collect(Collectors.toList()));
		microbot.verify(() -> Microbot.log(eq(Level.INFO), contains("broken gear not in setup"), eq("Guthan's helm 0")));
	}
}
