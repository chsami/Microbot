package net.runelite.client.plugins.microbot.util.inventory;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.ParamID;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Rs2ItemModel {
    @Getter
    public int id;
    @Getter
    @Setter
    public int quantity;
    @Getter
    public int slot = -1;
    @Getter
    public String name;
    @Getter
    String[] inventoryActions;
    @Getter
    List<String> equipmentActions = new ArrayList();
    @Getter
    boolean isStackable;
    @Getter
    boolean isNoted;
    @Getter
    boolean isTradeable;
    @Getter
    ItemComposition itemComposition;
    int[] wearableActionIndexes = new int[]{
            ParamID.OC_ITEM_OP1,
            ParamID.OC_ITEM_OP2,
            ParamID.OC_ITEM_OP3,
            ParamID.OC_ITEM_OP4,
            ParamID.OC_ITEM_OP5,
            ParamID.OC_ITEM_OP6,
            ParamID.OC_ITEM_OP7,
            ParamID.OC_ITEM_OP8};


    public Rs2ItemModel(Item item, ItemComposition itemComposition, int slot) {
        this.id = item.getId();
        this.quantity = item.getQuantity();
        this.name = itemComposition.getName();
        this.slot = slot;
        this.isStackable = itemComposition.isStackable();
        this.isNoted = itemComposition.getNote() == 799;
        if (this.isNoted) {
            Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getItemDefinition(this.id - 1)).ifPresent(itemDefinition -> this.isTradeable = itemDefinition.isTradeable());
        } else {
            this.isTradeable = itemComposition.isTradeable();
        }
        this.inventoryActions = itemComposition.getInventoryActions();
        this.itemComposition = itemComposition;
        addEquipmentActions(itemComposition);
    }

    public boolean isFood() {
        return Arrays.stream(inventoryActions).anyMatch(x -> x != null && x.equalsIgnoreCase("eat"));
    }
    
    public boolean canEquip() {
        return Arrays.stream(inventoryActions)
                .filter(Objects::nonNull)
                .anyMatch(x -> x.toLowerCase().contains("wear") || x.toLowerCase().contains("wield"));
    }

    private void addEquipmentActions(ItemComposition itemComposition) {
        for (int i = 0; i < wearableActionIndexes.length; i++) {
            try {
                String value = itemComposition.getStringValue(wearableActionIndexes[i]);
                this.equipmentActions.add(value);
            } catch (Exception ex) {
                this.equipmentActions.add("");
            }
        }
    }

    public int getPrice() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getItemManager().getItemPrice(id) * quantity).orElse(0);
    }

    public int getHaPrice() {
        return itemComposition.getHaPrice();
    }

    public boolean isHaProfitable() {
        int natureRunePrice = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getItemManager().getItemPrice(ItemID.NATURE_RUNE)).orElse(0);
        return (getHaPrice() - natureRunePrice) > (getPrice()/quantity) && isTradeable;

    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Rs2ItemModel other = (Rs2ItemModel) obj;
        return id == other.id;
    }
}
