package net.runelite.client.plugins.microbot.util.equipment;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import org.slf4j.event.Level;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class Rs2Equipment {
    public static ItemContainer equipment() {
        return Microbot.getClient().getItemContainer(InventoryID.WORN);
    }

    private static List<Rs2ItemModel> equipmentItems = Collections.emptyList();

    public static List<Rs2ItemModel> items() {
        return equipmentItems;
    }

    public static void storeEquipmentItemsInMemory(ItemContainerChanged e) {
        assert Microbot.getClient().isClientThread();

        if (e.getContainerId() != InventoryID.WORN) return;
        final ItemContainer itemContainer = e.getItemContainer();
        if (itemContainer == null) return;

        List<Rs2ItemModel> _equipmentItems = new ArrayList<>();
        for (int i = 0; i < Math.min(itemContainer.getItems().length, EquipmentInventorySlot.values().length); i++) {
            Item item = itemContainer.getItems()[i];
            if (item.getId() == -1) continue;
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(item.getId());
            _equipmentItems.add(new Rs2ItemModel(item, itemComposition, i));
        }
        equipmentItems = Collections.unmodifiableList(_equipmentItems);
    }

    @Deprecated(since = "Use interact", forRemoval = true)
    public static boolean useCapeAction(int itemId, String action) {
        Rs2ItemModel item = get(itemId);
        if (item == null) {
            Microbot.status = "Cape is missing in the equipment slot";
            return false;
        }

        invokeMenu(item, action);
        return true;
    }

    @Deprecated(since = "Use interact", forRemoval = true)
    public static boolean useRingAction(JewelleryLocationEnum jewelleryLocationEnum) {
        if (!isWearing(EquipmentInventorySlot.RING)) {
            Microbot.status = "Amulet is missing in the equipment slot";
            return false;
        }
        Microbot.doInvoke(new NewMenuEntry(-1, 25362456, MenuAction.CC_OP.getId(), jewelleryLocationEnum.getIdentifier(), -1, "Equip"),
                new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    @Deprecated(since = "Use interact", forRemoval = true)
    public static boolean useAmuletAction(JewelleryLocationEnum jewelleryLocationEnum) {
        if (!isWearing(EquipmentInventorySlot.AMULET) || !hasEquippedContains(jewelleryLocationEnum.getTooltip())) {
            Microbot.status = "Amulet is missing in the equipment slot";
            return false;
        }
        Microbot.doInvoke(new NewMenuEntry(-1, 25362449, MenuAction.CC_OP.getId(), jewelleryLocationEnum.getIdentifier(), -1, "Equip"),
                new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    public static Rs2ItemModel get(EquipmentInventorySlot slot) {
        return get(x -> x.getSlot() == slot.getSlotIdx());
    }

    public static Rs2ItemModel get(int id) {
        return get(x -> x.getId() == id);
    }

    public static Rs2ItemModel get(String name, boolean exact) {
        return get(x -> exact ? x.getName().equalsIgnoreCase(name) :
                x.getName().toLowerCase().contains(name.toLowerCase()));
    }

    public static Rs2ItemModel get(String name) {
        return get(name, false);
    }

    /**
     * Checks if the equipment contains an item that matches the specified predicate.
     *
     * @param predicate The predicate to apply.
     * @return True if the equipment contains an item that matches the predicate, false otherwise.
     */
    public static boolean contains(Predicate<Rs2ItemModel> predicate) {
        return items().stream().anyMatch(predicate);
    }

    /**
     * Retrieves an equipped item that matches the specified predicate.
     *
     * @param predicate The predicate to apply.
     * @return The matching `Rs2Item` if found, or null otherwise.
     */
    public static Rs2ItemModel get(Predicate<Rs2ItemModel> predicate) {
        return items().stream().filter(predicate).findFirst().orElse(null);
    }

    /**
     * Interacts with an equipped item matching the predicate.
     *
     * @param predicate The predicate to identify the item.
     * @param action    The action to perform.
     * @return True if the interaction was successful, false otherwise.
     */
    public static boolean interact(Predicate<Rs2ItemModel> predicate, String action) {
        Rs2ItemModel item = get(predicate);
        if (item != null) {
            invokeMenu(item, action);
            return true;
        }
        return false;
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasEquipped(String itemName) {
        return isWearing(itemName,true);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasEquippedContains(String itemName) {
        return isWearing(itemName,false);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasEquipped(int id) {
        return isWearing(id);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasEquippedSlot(EquipmentInventorySlot slot) {
        return isWearing(slot);
    }

    public static boolean isEquipped(String name, EquipmentInventorySlot slot) {
        return isEquipped(name, slot, false);
    }

    public static boolean isEquipped(int id, EquipmentInventorySlot slot) {
        final Rs2ItemModel item = get(slot);
        return item != null && item.getId() == id;
    }

    public static boolean isEquipped(String name, EquipmentInventorySlot slot, boolean exact) {
        final Rs2ItemModel item = get(slot);
        if (item == null) return false;
        return exact ? item.getName().equalsIgnoreCase(name) : item.getName().toLowerCase().contains(name.toLowerCase());
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasGuthanWeaponEquiped() {
        return isEquipped("guthan's warspear", EquipmentInventorySlot.WEAPON);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasGuthanBodyEquiped() {
        return isEquipped("guthan's platebody", EquipmentInventorySlot.BODY);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasGuthanLegsEquiped() {
        return isEquipped("guthan's chainskirt", EquipmentInventorySlot.LEGS);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean hasGuthanHelmEquiped() {
        return isEquipped("guthan's helm", EquipmentInventorySlot.HEAD);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean isWearingFullGuthan() {
        return hasGuthanBodyEquiped() && hasGuthanWeaponEquiped() &&
                hasGuthanHelmEquiped() && hasGuthanLegsEquiped();
    }

    public static boolean isWearing(String name) {
        return isWearing(name, false);
    }

    public static boolean isWearing(Predicate<Rs2ItemModel> predicate) {
        return Arrays.stream(EquipmentInventorySlot.values()).anyMatch(slot -> {
            final Rs2ItemModel item = get(slot);
            return item != null && predicate.test(item);
        });
    }

    public static boolean isWearing(EquipmentInventorySlot slot) {
        return isWearing(item -> item.getSlot() == slot.getSlotIdx());
    }

    public static boolean isWearing(int id) {
        return isWearing(item -> item.getId() == id);
    }

    public static boolean isWearing(String name, boolean exact) {
        return isWearing(exact ? item -> item.getName().equalsIgnoreCase(name) :
                item -> item.getName().toLowerCase().contains(name.toLowerCase()));
    }

    public static boolean isWearing(List<String> names, boolean exact, List<EquipmentInventorySlot> ignoreSlots) {
        final EquipmentInventorySlot[] searchSlots = Arrays.stream(EquipmentInventorySlot.values())
                .filter(slot -> ignoreSlots.stream().noneMatch(iSlot -> slot == iSlot))
                .toArray(EquipmentInventorySlot[]::new);
        return names.stream().allMatch(name -> Arrays.stream(searchSlots).anyMatch(slot -> isEquipped(name, slot, exact)));
    }

    /**
     * Unequips an item identified by its ID.
     *
     * This method retrieves the item with the given ID and unequips it if found.
     *
     * @param id The unique identifier of the item to unequip.
     * @return True if the item was found and the action was performed, otherwise false.
     */
    public static boolean unEquip(int id) {
        return interact(id, "remove");
    }

    public static boolean unEquip(EquipmentInventorySlot slot) {
        return interact(slot, "remove");
    }

    public static boolean interact(EquipmentInventorySlot slot, String action) {
        return interact(item -> item.getSlot() == slot.getSlotIdx(), action);
    }

    /**
     * Interacts with an item identified by its ID.
     *
     * This method retrieves the item with the given ID and performs an action on it if found.
     *
     * @param id The unique identifier of the item to interact with.
     * @param action The action to perform on the item (e.g., "use", "equip").
     * @return True if the item was found and the action was performed, otherwise false.
     */
    public static boolean interact(int id, String action) {
        return interact(item -> item.getId() == id, action);
    }

    /**
     * Interacts with an item identified by its name.
     *
     * This method retrieves the item with the given name and performs an action on it if found.
     *
     * @param name The name of the item to interact with.
     * @param action The action to perform on the item (e.g., "use", "equip").
     * @return True if the item was found and the action was performed, otherwise false.
     */
    public static boolean interact(String name, String action) {
        return interact(item -> item.getName().toLowerCase().contains(name.toLowerCase()), action);
    }

    /**
     * Interacts with any item from a list of IDs.
     *
     * This method iterates over a list of item IDs, retrieves the first matching item,
     * and performs an action on it if found.
     *
     * @param ids An array of item IDs to search through.
     * @param action The action to perform on the first matching item (e.g., "use", "equip").
     * @return True if any item from the list was found and the action was performed, otherwise false.
     */
    public static boolean interact(int[] ids, String action) {
        for (int id : ids) {
            if (interact(id, action)) return true;
        }
        return false;
    }


    /**
     * @param name
     * @param action
     * @param exact  name of the item
     * @return
     */
    public static boolean interact(String name, String action, boolean exact) {
        return interact(exact ? item -> item.getName().equalsIgnoreCase(name) :
                item -> item.getName().toLowerCase().contains(name.toLowerCase()), action);
    }

    @Deprecated(since = "Use isWearing", forRemoval = true)
    public static boolean isWearingShield() {
        return isWearing(EquipmentInventorySlot.SHIELD);
    }

    public static boolean isNaked() {
        return items().isEmpty();
    }

    public static void invokeMenu(Rs2ItemModel rs2Item, String action) {
        if (action == null || action.isEmpty()) return;
        if (rs2Item == null) return;

        Rs2Tab.switchToEquipmentTab();
        Microbot.status = action + " " + rs2Item.getName();

        int param0 = -1;
        int param1 = -1;
        int identifier;
        MenuAction menuAction = MenuAction.CC_OP;
        if (action.equalsIgnoreCase("remove")) {
            identifier = 1;
        } else {
            identifier = -1;
            List<String> actions = rs2Item.getEquipmentActions();
            for (int i = 0; i < actions.size(); i++) {
                if (action.equalsIgnoreCase(actions.get(i))) {
                    identifier = i + 2;
                    break;
                }
            }
            if (identifier == -1) {
                Microbot.log("Item=" + rs2Item.getName() + " does not have action=" + action + ". Actions=" + Arrays.toString(actions.stream().filter(Objects::nonNull).toArray()), Level.ERROR);
                return;
            }
        }

        if (rs2Item.getSlot() == EquipmentInventorySlot.CAPE.getSlotIdx()) {
            param1 = 25362448;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.HEAD.getSlotIdx()) {
            param1 = 25362447;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.AMMO.getSlotIdx()) {
            param1 = 25362457;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.AMULET.getSlotIdx()) {
            param1 = 25362449;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx()) {
            param1 = 25362450;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.BODY.getSlotIdx()) {
            param1 = 25362451;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.SHIELD.getSlotIdx()) {
            param1 = 25362452;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.LEGS.getSlotIdx()) {
            param1 = 25362453;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.GLOVES.getSlotIdx()) {
            param1 = 25362454;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.BOOTS.getSlotIdx()) {
            param1 = 25362455;
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.RING.getSlotIdx()) {
            param1 = 25362456;
        }

        Microbot.doInvoke(new NewMenuEntry(param0, param1, menuAction.getId(), identifier, -1, rs2Item.getName()), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), identifier, rs2Item.id, action, target, -1, -1);
    }
}