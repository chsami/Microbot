package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.commandcenter.scripts.core.CCBehavior;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.function.Supplier;

@Slf4j
public class BankingBehavior implements CCBehavior {

    private final BankingConfig config;
    private final Supplier<WorldPoint> activityLocationSupplier;

    public BankingBehavior(BankingConfig config, Supplier<WorldPoint> activityLocationSupplier) {
        this.config = config;
        this.activityLocationSupplier = activityLocationSupplier;
    }

    @Override public int priority() { return 50; }
    @Override public String name() { return "Banking"; }

    @Override
    public boolean shouldActivate() {
        return isInventoryFull();
    }

    @Override
    public void execute() {
        if (!walkToAndOpenBank()) return;
        if (!isBankOpen()) return;

        depositMatchingItems();
        withdrawConfiguredItems();
        closeBank();
        walkToActivityLocation();

        log.debug("Banking cycle complete");
    }

    @Override
    public void reset() { /* stateless */ }

    // --- Overridable for tests ---

    protected boolean isInventoryFull() {
        return Rs2Inventory.isFull();
    }

    protected boolean walkToAndOpenBank() {
        return Rs2Bank.walkToBankAndUseBank();
    }

    protected boolean isBankOpen() {
        return Rs2Bank.isOpen();
    }

    protected void depositMatchingItems() {
        if (config.depositFilter != null) {
            Rs2Bank.depositAll(config.depositFilter);
        } else {
            Rs2Bank.depositAll();
        }
    }

    protected void withdrawConfiguredItems() {
        if (config.withdrawItems != null) {
            for (String item : config.withdrawItems) {
                if (config.withdrawQuantity != null) {
                    Rs2Bank.withdrawItem(true, item);
                } else {
                    Rs2Bank.withdrawAll(item);
                }
            }
        }
    }

    protected void closeBank() {
        Rs2Bank.closeBank();
    }

    protected void walkToActivityLocation() {
        WorldPoint loc = activityLocationSupplier.get();
        if (loc != null) {
            Rs2Walker.walkTo(loc);
        }
    }
}
