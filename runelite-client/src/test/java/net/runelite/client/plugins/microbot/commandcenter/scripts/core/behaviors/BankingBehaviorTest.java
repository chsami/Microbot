package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class BankingBehaviorTest {

    private BankingBehavior behaviorWith(boolean inventoryFull, boolean bankOpen) {
        BankingConfig config = new BankingConfig(
            item -> item.getName().contains("Logs"),
            null, null
        );
        return new BankingBehavior(config, () -> new WorldPoint(3200, 3200, 0)) {
            @Override protected boolean isInventoryFull() { return inventoryFull; }
            @Override protected boolean walkToAndOpenBank() { return bankOpen; }
            @Override protected boolean isBankOpen() { return bankOpen; }
            @Override protected void depositMatchingItems() { }
            @Override protected void withdrawConfiguredItems() { }
            @Override protected void closeBank() { }
            @Override protected void walkToActivityLocation() { }
        };
    }

    @Test
    public void shouldActivate_whenInventoryFull() {
        assertTrue(behaviorWith(true, false).shouldActivate());
    }

    @Test
    public void shouldNotActivate_whenInventoryHasSpace() {
        assertFalse(behaviorWith(false, false).shouldActivate());
    }

    @Test
    public void priority_is50() {
        assertEquals(50, behaviorWith(false, false).priority());
    }

    @Test
    public void name_isBanking() {
        assertEquals("Banking", behaviorWith(false, false).name());
    }

    @Test
    public void execute_whenBankOpens_completesFullCycle() {
        boolean[] called = new boolean[4];
        BankingConfig config = new BankingConfig(
            item -> item.getName().contains("Logs"), null, null
        );
        BankingBehavior b = new BankingBehavior(config, () -> new WorldPoint(3200, 3200, 0)) {
            @Override protected boolean isInventoryFull() { return true; }
            @Override protected boolean walkToAndOpenBank() { return true; }
            @Override protected boolean isBankOpen() { return true; }
            @Override protected void depositMatchingItems() { called[0] = true; }
            @Override protected void withdrawConfiguredItems() { called[1] = true; }
            @Override protected void closeBank() { called[2] = true; }
            @Override protected void walkToActivityLocation() { called[3] = true; }
        };
        b.execute();
        assertTrue("deposit should be called", called[0]);
        assertTrue("withdraw should be called", called[1]);
        assertTrue("closeBank should be called", called[2]);
        assertTrue("walkBack should be called", called[3]);
    }

    @Test
    public void execute_whenBankFailsToOpen_abortsEarly() {
        boolean[] called = new boolean[1];
        BankingConfig config = new BankingConfig(null, null, null);
        BankingBehavior b = new BankingBehavior(config, () -> null) {
            @Override protected boolean isInventoryFull() { return true; }
            @Override protected boolean walkToAndOpenBank() { return false; }
            @Override protected boolean isBankOpen() { return false; }
            @Override protected void depositMatchingItems() { called[0] = true; }
            @Override protected void withdrawConfiguredItems() { }
            @Override protected void closeBank() { }
            @Override protected void walkToActivityLocation() { }
        };
        b.execute();
        assertFalse("deposit should NOT be called when bank fails to open", called[0]);
    }
}
