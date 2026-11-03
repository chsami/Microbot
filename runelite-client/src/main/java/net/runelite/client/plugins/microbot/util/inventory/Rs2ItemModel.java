package net.runelite.client.plugins.microbot.util.inventory;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.*;
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
    private String[][] subops;
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
        this.slot = slot;
        initializeFromComposition(itemComposition);
    }

    /**
     * Creates an Rs2ItemModel from cached data (ID, quantity, slot).
     * This is used when loading bank data from config where we don't have the full ItemComposition.
     * ItemComposition data will be loaded lazily when needed.
     *
     * @param id       Item ID
     * @param quantity Item quantity
     * @param slot     Item slot position
     * @return Rs2ItemModel with basic data, ItemComposition loaded lazily
     */
    public static Rs2ItemModel createFromCache(int id, int quantity, int slot) {
        return new Rs2ItemModel(id, quantity, slot, null);
    }


    /**
     * Constructor for creating Rs2ItemModel with explicit ItemComposition.
     */
    public Rs2ItemModel(int id, int quantity, int slot, ItemComposition itemComposition) {
        this.id = id;
        this.quantity = quantity;
        this.slot = slot;
        if (itemComposition == null) {
            //lazy loading will handle this
            initializeDefaults();
        } else {
            initializeFromComposition(itemComposition);
        }
    }

    /**
     * Private constructor for creating Rs2ItemModel from cached data.
     * ItemComposition will be loaded lazily when needed.
     */
    public Rs2ItemModel(int id, int quantity, int slot) {
        this.id = id;
        this.quantity = quantity;
        this.slot = slot;
        ItemComposition itemComposition = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getItemDefinition(id)).orElse(null);
        if (itemComposition == null) {
            initializeDefaults();
        } else {
            initializeFromComposition(itemComposition);
        }
    }

    /**
     * Lazy loads the ItemComposition if not already loaded.
     * This ensures we can work with cached items while minimizing performance impact.
     */
    private void ensureCompositionLoaded() {
        if (itemComposition == null && id > 0) {
            Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(id)).ifPresent(this::initializeFromComposition);
        }
    }

    /**
     * Gets the item name, loading composition if needed.
     */
    public String getName() {
        if (itemComposition == null) {
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
     * Gets the sub-actions, loading composition if needed.
     * This returns a list of sub-menu actions that can be performed on the item.
     * The first index corresponds to the main menu's index, the second index is the sub-menu index.
     *
     * @return A list of sub-actions, or null if none are available
     */
    public String[][] getSubops() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return subops;
    }

    /**
     * Retrieves the index of a sub-action from the sub-actions list, matching the given action.
     * <p>
     * This method searches through the sub-actions of the item, attempting to find a match
     * for the specified action. If a match is found, it returns the corresponding main action
     * and index of the sub-action. If no match is found or if the sub-actions are unavailable,
     * it returns a default result with null and -1.
     *
     * @param action The action to search for in the sub-actions list. Case-insensitive comparison is used.
     * @return A Map.Entry containing the inventory action (String) and the index of the sub-action (Integer)
     * if a match is found; otherwise, returns a Map.Entry with null and -1.
     */
    public Map.Entry<String, Integer> getIndexOfSubAction(String action) {
        if (action == null) return null;
        String alc = action.toLowerCase();

        String[][] subOps = getSubops();
        if (subOps == null) {
            return null;
        }
        for (int i = 0; i < subOps.length; i++) {
            String[] subOpsActions = subOps[i];
            if (subOpsActions == null) continue;
            for (int j = 0; j < subOpsActions.length; j++) {
                String subObsAction = subOpsActions[j];
                if (subObsAction != null && subObsAction.toLowerCase().contains(alc)) {
                    return Map.entry(inventoryActions[i], j);
                }
            }
        }

        return null;
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

    public long getPrice() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getItemManager().getItemPrice(id) * quantity).orElse(0L);
    }

    public int getHaPrice() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }
        return itemComposition != null ? itemComposition.getHaPrice() : 0;
    }

    public boolean isHaProfitable() {
        long natureRunePrice = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getItemManager().getItemPrice(ItemID.NATURERUNE)).orElse(0L);
        return (getHaPrice() - natureRunePrice) > (getPrice() / quantity) && isTradeable;

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
        long price = getPrice();
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

    /**
     * Initialize default values when ItemComposition is not available.
     */
    private void initializeDefaults() {
        this.name = null;
        this.isStackable = false;
        this.isNoted = false;
        this.isTradeable = false;
        this.inventoryActions = new String[0];
        this.itemComposition = null;
    }

    /**
     * Gets the noted variant of this item if it exists and is stackable.
     * Returns this item's ID if the item is already noted or has no noted variant.
     *
     * @return The noted item ID if available, otherwise the original item ID
     */
    public int getNotedId() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }

        if (itemComposition == null) {
            return id; // fallback to original ID
        }
        if (!isNoted()) {
            return itemComposition.getLinkedNoteId() != -1 ? itemComposition.getLinkedNoteId() : id;
        }
        return getNotedItemId(itemComposition);
    }

    /**
     * Gets the unnoted variant of this item if it exists.
     * Returns this item's ID if the item is already unnoted or has no unnoted variant.
     *
     * @return The unnoted item ID if available, otherwise the original item ID
     */
    public int getUnNotedId() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }

        if (itemComposition == null) {
            return id; // fallback to original ID
        }
        if (isNoted()) {
            // If this item is noted, return the unnoted variant
            return itemComposition.getLinkedNoteId() != -1 ? itemComposition.getLinkedNoteId() : id; // return original ID if no unnoted variant
        }
        return getUnNotedId(itemComposition);
    }

    /**
     * Gets the linked item ID (noted/unnoted counterpart) of this item.
     *
     * @return The linked item ID
     */
    public int getLinkedId() {
        if (itemComposition == null) {
            ensureCompositionLoaded();
        }

        if (itemComposition == null) {
            return id; // fallback to original ID
        }

        return getLinkedItemId(itemComposition);
    }

    /**
     * Static method to get the noted variant of an item ID.
     *
     * @param itemId The original item ID
     * @return The noted item ID if available, otherwise the original item ID
     */
    public static int getNotedId(int itemId) {
        ItemComposition composition = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getItemDefinition(itemId)
        ).orElse(null);

        return getNotedItemId(composition);
    }

    /**
     * Static method to get the unnoted variant of an item ID.
     *
     * @param itemId The original item ID
     * @return The unnoted item ID if available, otherwise the original item ID
     */
    public static int getUnNotedId(int itemId) {
        ItemComposition composition = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getItemDefinition(itemId)
        ).orElse(null);

        return getUnNotedId(composition);
    }

    /**
     * Helper method to get the noted variant from ItemComposition.
     * Returns the noted ID if the item has a stackable noted variant.
     *
     * @param composition The ItemComposition to check
     * @return The noted item ID if available, otherwise the original item ID
     */
    private static int getNotedItemId(ItemComposition composition) {
        try {
            if (composition == null) {
                return -1;
            }

            int itemId = composition.getId();
            boolean isNoted = composition.getNote() == 799;
            int linkedNoteId = composition.getLinkedNoteId();
            // if already stackable, return original ID
            if ((isNoted && composition.isStackable()) || linkedNoteId == -1) {
                return itemId;
            }

            return linkedNoteId;

        } catch (Exception e) {
            return -1; // fall back to original on error
        }
    }

    /**
     * Helper method to get the unnoted variant from ItemComposition.
     * Returns the unnoted ID if the item has an unnoted variant.
     *
     * @param composition The ItemComposition to check
     * @return The unnoted item ID if available, otherwise the original item ID
     */
    private static int getUnNotedId(ItemComposition composition) {
        try {
            if (composition == null) {
                log.warn("Could not get item composition for item ID, returning original ID");
                return -1;
            }
            int itemId = composition.getId();
            boolean isNoted = composition.getNote() == 799;
            int linkedNoteId = composition.getLinkedNoteId();
            // if already stackable, return original ID
            if ((!isNoted && !composition.isStackable()) || linkedNoteId == -1) {
                return itemId;
            }
            return linkedNoteId;

        } catch (Exception e) {
            log.error("Error getting unnoted item ID: {}", e.getMessage());
            return -1; // fall back to original on error
        }
    }

    /**
     * Helper method to get the linked item ID from ItemComposition.
     *
     * @param composition The ItemComposition to check
     * @return The linked item ID
     */
    private static int getLinkedItemId(ItemComposition composition) {
        try {
            if (composition == null) {
                log.warn("no item composition for item ID, returning -1");
                return -1;
            }

            // check if this item has a noted variant
            return composition.getLinkedNoteId();

        } catch (Exception e) {
            log.error("Error getting linked item ID: {}", e.getMessage());
            return -1; // fall back on error
        }
    }

    /**
     * Initialize item properties from ItemComposition.
     */
    private void initializeFromComposition(ItemComposition itemComposition) {
        this.name = itemComposition.getName();
        this.isStackable = itemComposition.isStackable();
        this.isNoted = itemComposition.getNote() == 799;

        // Handle noted item tradeable status
        if (this.isNoted) {
            Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Microbot.getClient().getItemDefinition(itemComposition.getLinkedNoteId())
            ).ifPresent(itemDefinition -> this.isTradeable = itemDefinition.isTradeable());
        } else {
            this.isTradeable = itemComposition.isTradeable();
        }

        this.inventoryActions = itemComposition.getInventoryActions();
        // This has to be run in microbot's client thread
        this.subops = Microbot.getClientThread().runOnClientThreadOptional(itemComposition::getSubops).orElse(null);
        this.itemComposition = itemComposition;

        // Add equipment actions asynchronously
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            addEquipmentActions(itemComposition);
            return true;
        });
    }

    /**
     * Retrieves an inventory action that contains the specified substring, ignoring case sensitivity.
     * Searches through all non-null inventory actions and returns the first match.
     *
     * @param partOfAction The substring to search for within the inventory actions. Case-insensitive comparison is used.
     * @return The first matching inventory action that contains the specified substring, or null if no match is found.
     */
    public String getAction(String partOfAction) {
        return Arrays.stream(getInventoryActions())
                .filter(Objects::nonNull)
                .filter(x -> x.toLowerCase().contains(partOfAction))
                .findFirst().orElse(null);
    }

    /**
     * Retrieves the most relevant action from a list of possible actions by matching them against
     * the inventory actions of the item. The relevance is determined by the order of the given actions
     * and their occurrence within the inventory actions.
     *
     * @param actions The list of actions to search for, provided as varargs. Null values will be ignored.
     * @return The most relevant matching action from the inventory actions, or null if no match is found.
     */
    public String getActionFromList(List<String> actions) {
        return Arrays.stream(getInventoryActions())
                .filter(action -> action != null && actions.stream().anyMatch(keyword -> action.toLowerCase().contains(keyword.toLowerCase())))
                .min(Comparator.comparingInt(action ->
                        actions.stream()
                                .filter(keyword -> action.toLowerCase().contains(keyword.toLowerCase()))
                                .mapToInt(actions::indexOf)
                                .findFirst()
                                .orElse(Integer.MAX_VALUE)
                )).orElse(null);
    }
}
