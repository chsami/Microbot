package net.runelite.client.plugins.microbot.util.inventory;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.ParamID;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

@Slf4j
public class Rs2ItemModel {
    @Getter
    private int id;
    @Getter
    @Setter
	private int quantity;
    @Getter
	private int slot = -1;
	private String name;
	private String[] inventoryActions;
    @Getter
	private List<String> equipmentActions = new ArrayList<>();
	private boolean isStackable;
	private boolean isNoted;
	private boolean isTradeable;
	private ItemComposition itemComposition;
	private int[] wearableActionIndexes = new int[]{
            ParamID.OC_ITEM_OP1,
            ParamID.OC_ITEM_OP2,
            ParamID.OC_ITEM_OP3,
            ParamID.OC_ITEM_OP4,
            ParamID.OC_ITEM_OP5,
            ParamID.OC_ITEM_OP6,
            ParamID.OC_ITEM_OP7,
            ParamID.OC_ITEM_OP8
	};


    public Rs2ItemModel(Item item, ItemComposition itemComposition, int slot) {
        this.id = item.getId();
        this.quantity = item.getQuantity();
        this.name = itemComposition.getName();
        this.slot = slot;
        this.isStackable = itemComposition.isStackable();
        this.isNoted = itemComposition.getNote() == 799;
        // This is to ensure the item is linked correctly if it's noted
        if (this.isNoted) {
            Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getItemDefinition(itemComposition.getLinkedNoteId())).ifPresent(itemDefinition -> this.isTradeable = itemDefinition.isTradeable());
        } else {
            this.isTradeable = itemComposition.isTradeable();
        }
        this.inventoryActions = itemComposition.getInventoryActions();
        this.itemComposition = itemComposition;
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            addEquipmentActions(itemComposition);
       	    return true;
        });
    }

    /**
     * Creates an Rs2ItemModel from cached data (ID, quantity, slot).
     * This is used when loading bank data from config where we don't have the full ItemComposition.
     * ItemComposition data will be loaded lazily when needed.
     * 
     * @param id Item ID
     * @param quantity Item quantity
     * @param slot Item slot position
     * @return Rs2ItemModel with basic data, ItemComposition loaded lazily
     */
    public static Rs2ItemModel createFromCache(int id, int quantity, int slot) {
        return new Rs2ItemModel(id, quantity, slot);
    }

    /**
     * Private constructor for creating Rs2ItemModel from cached data.
     * ItemComposition will be loaded lazily when needed.
     */
    private Rs2ItemModel(int id, int quantity, int slot) {
        this.id = id;
        this.quantity = quantity;
        this.slot = slot;
        
        // Initialize with defaults - will be loaded lazily
        this.name = null;
        this.isStackable = false;
        this.isNoted = false;
        this.isTradeable = false;
        this.inventoryActions = new String[0];
        this.itemComposition = null;
        this.equipmentActions = new ArrayList<>();
    }

    /**
     * Lazy loads the ItemComposition if not already loaded.
     * This ensures we can work with cached items while minimizing performance impact.
     */
    private void ensureCompositionLoaded() {
        
        if (itemComposition == null && id > 0) {
            itemComposition = Microbot.getClientThread().runOnClientThreadOptional(()->Microbot.getItemManager().getItemComposition(id)).orElse(null);
            if (itemComposition != null) {
                this.name = itemComposition.getName();
                this.isStackable = itemComposition.isStackable();
                this.isNoted = itemComposition.getNote() == 799;
                if (this.isNoted) {
                    Microbot.getClientThread().runOnClientThreadOptional(() ->
                            Microbot.getClient().getItemDefinition(itemComposition.getLinkedNoteId())).ifPresent(itemDefinition -> this.isTradeable = itemDefinition.isTradeable());
                } else {
                    this.isTradeable = itemComposition.isTradeable();
                }
                this.inventoryActions = itemComposition.getInventoryActions();
				Microbot.getClientThread().runOnClientThreadOptional(() -> {
					addEquipmentActions(itemComposition);
					return true;
				});
            }
        }
    }

    /**
     * Gets the item name, loading composition if needed.
     */
    public String getName() {
        if (name == null) {
            ensureCompositionLoaded();
        }
        return name != null ? name : "Unknown Item";
    }

    /**
     * Gets whether the item is stackable, loading composition if needed.
     */
    public boolean isStackable() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return isStackable;
    }

    /**
     * Gets whether the item is noted, loading composition if needed.
     */
    public boolean isNoted() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return isNoted;
    }

    /**
     * Gets whether the item is tradeable, loading composition if needed.
     */
    public boolean isTradeable() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return isTradeable;
    }

    /**
     * Gets the inventory actions, loading composition if needed.
     */
    public String[] getInventoryActions() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return inventoryActions;
    }
    /**
     * Gets the equipment actions, loading composition if needed.
     * This returns a list of actions that can be performed on the item when equipped.
     */
    public List<String> getEquipmentActions() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return equipmentActions;
    }

    /**
     * Gets the item composition, loading it if needed.
     */
    public ItemComposition getItemComposition() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return itemComposition;
    }

	public boolean isFood() {
		if (isNoted()) return false;

		String lowerName = getName().toLowerCase();

		boolean isEdible = Arrays.stream(getInventoryActions()).anyMatch(action -> action != null && action.equalsIgnoreCase("eat"));

		return (isEdible || lowerName.contains("jug of wine")) && !lowerName.contains("rock cake");
	}

    private void addEquipmentActions(ItemComposition itemComposition) {
        for (int i = 0; i < wearableActionIndexes.length; i++) {
            try {
                String value = itemComposition.getStringValue(wearableActionIndexes[i]);
                this.equipmentActions.add(value);
            } catch (Exception ex) {
                this.equipmentActions.add("");
                log.warn("Failed to get wearable action for index {} on item {}: {}", wearableActionIndexes[i], id, ex.getMessage());
                ex.printStackTrace();
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
                Microbot.getItemManager().getItemPrice(ItemID.NATURERUNE)).orElse(0);
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rs2ItemModel {\n");
        sb.append("\tid: ").append(id).append("\n");
        sb.append("\tname: '").append(getName()).append("'\n");
        sb.append("\tquantity: ").append(quantity).append("\n");
        sb.append("\tslot: ").append(slot).append("\n");
        sb.append("\tisStackable: ").append(isStackable()).append("\n");
        sb.append("\tisNoted: ").append(isNoted()).append("\n");
        sb.append("\tisTradeable: ").append(isTradeable()).append("\n");
        sb.append("\tisFood: ").append(isFood()).append("\n");
        
        // Price information
        int price = getPrice();
        sb.append("\tprice: ").append(price).append(" gp (total)\n");
        if (quantity > 0) {
            sb.append("\tunitPrice: ").append(price / quantity).append(" gp (each)\n");
        }
        
        // High Alchemy information
        if (itemComposition != null) {
            int haPrice = getHaPrice();
            sb.append("\thaPrice: ").append(haPrice).append(" gp\n");
            sb.append("\tisHaProfitable: ").append(isHaProfitable()).append("\n");
        }
        
        // Actions
        String[] invActions = getInventoryActions();
        if (invActions != null && invActions.length > 0) {
            sb.append("\tinventoryActions: [");
            for (int i = 0; i < invActions.length; i++) {
                if (invActions[i] != null && !invActions[i].isEmpty()) {
                    if (i > 0) sb.append(", ");
                    sb.append("'").append(invActions[i]).append("'");
                }
            }
            sb.append("]\n");
        }
        
        // Equipment actions
        if (!equipmentActions.isEmpty()) {
            sb.append("\tequipmentActions: [");
            boolean first = true;
            for (String action : equipmentActions) {
                if (action != null && !action.isEmpty()) {
                    if (!first) sb.append(", ");
                    sb.append("'").append(action).append("'");
                    first = false;
                }
            }
            sb.append("]\n");
        }
        
        // Composition status
        sb.append("\tcompositionLoaded: ").append(itemComposition != null).append("\n");
        
        sb.append("}");
        return sb.toString();
    }

    private static <T> Predicate<Rs2ItemModel> matches(T[] values, BiPredicate<Rs2ItemModel, T> biPredicate) {
        return item -> Arrays.stream(values).filter(Objects::nonNull).anyMatch(value -> biPredicate.test(item, value));
    }

    public static Predicate<Rs2ItemModel> matches(boolean exact, String... names) {
        return matches(names, exact ? (item, name) -> item.getName().equalsIgnoreCase(name) :
                (item, name) -> item.getName().toLowerCase().contains(name.toLowerCase()));
    }

    public static Predicate<Rs2ItemModel> matches(int... ids) {
        return item -> Arrays.stream(ids).anyMatch(id -> item.getId() == id);
    }

    public static Predicate<Rs2ItemModel> matches(EquipmentInventorySlot... slots) {
        return matches(slots, (item, slot) -> item.getSlot() == slot.getSlotIdx());
    }
}
