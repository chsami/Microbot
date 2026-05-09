package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class Rs2BankSetupDepositRetainTest {
	@Test
	public void retainMatchesExactId() {
		Rs2ItemModel item = Mockito.mock(Rs2ItemModel.class);
		Mockito.when(item.getId()).thenReturn(995);
		Mockito.when(item.getName()).thenReturn("Coins");

		boolean keep = Rs2Bank.isInventoryItemRetainedForSetupDeposit(item, Set.of(995, 996), Collections.emptyMap());
		Assert.assertTrue(keep);
	}

	@Test
	public void retainMatchesFuzzyNameSubstring() {
		Rs2ItemModel item = Mockito.mock(Rs2ItemModel.class);
		Mockito.when(item.getId()).thenReturn(123);
		Mockito.when(item.getName()).thenReturn("Shark");

		Map<String, Boolean> fuzzy = new LinkedHashMap<>();
		fuzzy.put("shark", true);

		boolean keep = Rs2Bank.isInventoryItemRetainedForSetupDeposit(item, Collections.emptySet(), fuzzy);
		Assert.assertTrue(keep);
	}

	@Test
	public void retainExactNameDoesNotMatchSubstring() {
		Rs2ItemModel item = Mockito.mock(Rs2ItemModel.class);
		Mockito.when(item.getId()).thenReturn(321);
		Mockito.when(item.getName()).thenReturn("Raw shark");

		Map<String, Boolean> exact = new LinkedHashMap<>();
		exact.put("Shark", false);

		boolean keep = Rs2Bank.isInventoryItemRetainedForSetupDeposit(item, Collections.emptySet(), exact);
		Assert.assertFalse(keep);
	}

	@Test
	public void foreignItemNotRetained() {
		Rs2ItemModel item = Mockito.mock(Rs2ItemModel.class);
		Mockito.when(item.getId()).thenReturn(1);
		Mockito.when(item.getName()).thenReturn("Junk");

		boolean keep = Rs2Bank.isInventoryItemRetainedForSetupDeposit(item, Set.of(995), Map.of("lobster", true));
		Assert.assertFalse(keep);
	}
}
