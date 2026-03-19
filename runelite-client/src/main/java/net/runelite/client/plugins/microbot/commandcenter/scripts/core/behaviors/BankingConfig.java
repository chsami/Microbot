package net.runelite.client.plugins.microbot.commandcenter.scripts.core.behaviors;

import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.List;
import java.util.function.Predicate;

public class BankingConfig {
    public final Predicate<Rs2ItemModel> depositFilter;
    public final List<String> withdrawItems;
    public final Integer withdrawQuantity;

    public BankingConfig(Predicate<Rs2ItemModel> depositFilter, List<String> withdrawItems, Integer withdrawQuantity) {
        this.depositFilter = depositFilter;
        this.withdrawItems = withdrawItems;
        this.withdrawQuantity = withdrawQuantity;
    }
}
