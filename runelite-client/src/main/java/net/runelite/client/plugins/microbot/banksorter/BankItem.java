package net.runelite.client.plugins.microbot.banksorter;

import lombok.Getter;

@Getter
public class BankItem {
    private final int id;
    private final String name;
    private final int quantity;
    private final int originalIndex;

    public BankItem(int id, String name, int quantity, int originalIndex) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.originalIndex = originalIndex;
    }

}
