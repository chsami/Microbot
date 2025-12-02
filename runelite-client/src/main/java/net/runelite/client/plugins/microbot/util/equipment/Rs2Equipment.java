package net.runelite.client.plugins.microbot.util.equipment;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import org.slf4j.event.Level;

import java.awt.Rectangle;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Rs2Equipment {
    private static List<Rs2ItemModel> equipmentItems = Collections.emptyList();

    public static ItemContainer equipment() {
        return Microbot.getClient().getItemContainer(InventoryID.WORN);
    }

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

    public static Stream<Rs2ItemModel> all() {
        final List<Rs2ItemModel> items = items();
        if (items == null) return Stream.empty();
        return items.stream();
    }

    public static Stream<Rs2ItemModel> all(Predicate<Rs2ItemModel> predicate) {
        return all().filter(predicate);
    }

    public static Stream<Rs2ItemModel> all(EquipmentInventorySlot... slots) {
        return all(Rs2ItemModel.matches(slots));
    }

    public static Stream<Rs2ItemModel> all(int... ids) {
        return all(Rs2ItemModel.matches(ids));
    }

    public static Stream<Rs2ItemModel> all(String[] names, boolean exact) {
        return all(Rs2ItemModel.matches(exact, names));
    }

    public static Stream<Rs2ItemModel> all(String name, boolean exact) {
        return all(Rs2ItemModel.matches(exact, name));
    }

    public static Stream<Rs2ItemModel> all(String... names) {
        return all(names, false);
    }

    public static Rs2ItemModel get() {
        return all().findFirst().orElse(null);
    }

    /**
     * Retrieves an equipped item that matches the specified predicate.
     *
     * @param predicate The predicate to apply.
     * @return The matching `Rs2Item` if found, or null otherwise.
     */
    public static Rs2ItemModel get(Predicate<Rs2ItemModel> predicate) {
        return all(predicate).findFirst().orElse(null);
    }

    public static Rs2ItemModel get(EquipmentInventorySlot... slots) {
        return all(slots).findFirst().orElse(null);
    }

    public static Rs2ItemModel get(int... ids) {
        return all(ids).findFirst().orElse(null);
    }

    public static Rs2ItemModel get(String[] names, boolean exact) {
        return all(names, exact).findFirst().orElse(null);
    }

    public static Rs2ItemModel get(String name, boolean exact) {
        return all(name, exact).findFirst().orElse(null);
    }

    public static Rs2ItemModel get(String... names) {
        return all(names).findFirst().orElse(null);
    }

    public static boolean isWearing() {
        return get() != null;
    }

    public static boolean isWearing(Predicate<Rs2ItemModel> predicate) {
        return get(predicate) != null;
    }

    public static boolean isWearing(EquipmentInventorySlot... slots) {
        return get(slots) != null;
    }

    public static boolean isWearing(int... ids) {
        return get(ids) != null;
    }

    public static boolean isWearing(String[] names, boolean exact) {
        return get(names, exact) != null;
    }

    public static boolean isWearing(String name, boolean exact) {
        return get(name, exact) != null;
    }

    public static boolean isWearing(String... names) {
        return get(names) != null;
    }

    private static <T> Stream<T> getOthers(T[] values, T[] ignore) {
        if (values == null || values.length == 0) return Stream.empty();
        if (ignore == null || ignore.length == 0) return Arrays.stream(values);

        final Set<T> ignoreSet = Set.of(ignore);
        return Arrays.stream(values).filter(value -> !ignoreSet.contains(value));
    }

    public static boolean isWearing(String[] names, boolean exact, EquipmentInventorySlot[] searchSlots) {
        final Rs2ItemModel[] equipment = all(searchSlots).toArray(Rs2ItemModel[]::new);
        return Arrays.stream(names).allMatch(
                name -> Arrays.stream(equipment).anyMatch(Rs2ItemModel.matches(exact, name))
        );
    }

    public static boolean isWearing(String[] names, boolean exact, EquipmentInventorySlot[] slots, boolean areSearchSlots) {
        final EquipmentInventorySlot[] searchSlots = areSearchSlots ? slots :
                getOthers(EquipmentInventorySlot.values(), slots).toArray(EquipmentInventorySlot[]::new);
        return isWearing(names, exact, searchSlots);
    }

    public static boolean isWearing(String[] names, EquipmentInventorySlot[] searchSlots) {
        return isWearing(names, false, searchSlots);
    }

    private static boolean unEquip(Rs2ItemModel item) {
        return interact(item, "remove");
    }

    public static boolean unEquip(Predicate<Rs2ItemModel> predicate) {
        return unEquip(get(predicate));
    }

    public static boolean unEquip(EquipmentInventorySlot... slots) {
        return unEquip(get(slots));
    }

    /**
     * Unequips an item identified by its ID.<p>
     * This method retrieves the item with the given ID and unequips it if found.
     *
     * @param ids The unique identifier of the item to unequip.
     * @return {@code true} if the item exists and the action is performed, otherwise {@code false}.
     */
    public static boolean unEquip(int... ids) {
        return unEquip(get(ids));
    }

    public static boolean unEquip(String[] names, boolean exact) {
        return unEquip(get(names, exact));
    }

    public static boolean unEquip(String name, boolean exact) {
        return unEquip(get(name, exact));
    }

    public static boolean unEquip(String... names) {
        return unEquip(names, false);
    }

    // TODO: can only be made public if we ensure item really is an equipment item
    private static boolean interact(Rs2ItemModel item, String action) {
        if (item == null) return false;
        invokeMenu(item, action);
        return true;
    }

    /**
     * Interacts with an equipped item matching the predicate.
     *
     * @param predicate The predicate to identify the item.
     * @param action    The action to perform.
     * @return {@code true} if the item exists and the action is performed, otherwise {@code false}.
     */
    public static boolean interact(Predicate<Rs2ItemModel> predicate, String action) {
        return interact(get(predicate), action);
    }

    public static boolean interact(EquipmentInventorySlot slot, String action) {
        return interact(get(slot), action);
    }

    public static boolean interact(EquipmentInventorySlot[] slots, String action) {
        if (slots == null) return false;
        for (EquipmentInventorySlot slot : slots) {
            if (interact(slot, action)) return true;
        }
        return false;
    }

    /**
     * Interacts with an item identified by its ID.
     * <p>
     * This method retrieves the item with the given ID and performs an action on it if found.
     *
     * @param id The unique identifier of the item to interact with.
     * @param action The action to perform on the item (e.g., "use", "equip").
     * @return {@code true} if the item exists and the action is performed, otherwise {@code false}.
     */
    public static boolean interact(int id, String action) {
        return interact(get(id), action);
    }

    /**
     * Interacts with any item from a list of IDs.
     * <p>
     * This method iterates over a list of item IDs, retrieves the first matching item,
     * and performs an action on it if found.
     *
     * @param ids An array of item IDs to search through.
     * @param action The action to perform on the first matching item (e.g., "use", "equip").
     * @return {@code true} if the item exists and the action is performed, otherwise {@code false}.
     */
    public static boolean interact(int[] ids, String action) {
        if (ids == null) return false;
        for (int id : ids) {
            if (interact(id, action)) return true;
        }
        return false;
    }

    /**
     * Interacts with an item identified by its name.
     *
     * This method retrieves the item with the given name and performs an action on it if found.
     *
     * @param name The name of the item to interact with.
     * @param action The action to perform on the item (e.g., "use", "equip").
     * @return {@code true} if the item exists and the action is performed, otherwise {@code false}.
     */
    public static boolean interact(String name, String action) {
        return interact(get(name), action);
    }

    /**
     * @param name
     * @param action
     * @param exact  name of the item
     * @return {@code true} if the item exists and the action is performed, otherwise {@code false}.
     */
    public static boolean interact(String name, String action, boolean exact) {
        return interact(get(name, exact), action);
    }

    public static boolean interact(String[] names, String action, boolean exact) {
        if (names == null) return false;
        for (String name : names) {
            if (interact(name, action, exact)) return true;
        }
        return false;
    }

    public static boolean interact(String[] names, String action) {
        return interact(names, action, false);
    }

    public static void invokeMenu(Rs2ItemModel rs2Item, String action) {
        if (action == null || action.isEmpty()) return;
        if (rs2Item == null) return;

        if (!Rs2Tab.switchTo(InterfaceTab.EQUIPMENT)) {
            Microbot.log("Failed to switch to equipment tab", Level.ERROR);
            return;
        }
        Microbot.status = action + " " + rs2Item.getName();

        int param0 = -1;
        int param1 = -1;
        int identifier;
        String target = rs2Item.getName();
        MenuAction menuAction = MenuAction.CC_OP;
        if (action.equalsIgnoreCase("remove")) {
            identifier = 1;
        } else {
            identifier = -1;
            List<String> actions = rs2Item.getEquipmentActions();
            for (int i = 0; i < actions.size(); i++) {
                if (actions.get(i).toLowerCase().contains(action.toLowerCase())) {
                    identifier = i + 2;
                    break;
                }
            }
            // We could not find the action in the equipment actions, so we try to find it in the sub-menu actions
            if (identifier == -1) {
                Map.Entry<String, Integer> subMenuEntry = rs2Item.getIndexOfSubAction(action);
                if (subMenuEntry == null) {
                    Microbot.log("Item=" + rs2Item.getName() + " does not have a subaction=" + action, Level.ERROR);
                    return;
                }
                int mainMenuIndex = actions.indexOf(subMenuEntry.getKey());
                if (mainMenuIndex < 0) {
                    Microbot.log("Cannot find action=%s, in main actions=%s, mainMenuIndex=%s", subMenuEntry.getKey(), String.join(", ", actions), mainMenuIndex, Level.ERROR);
                    return;
                }
                target = "";
                identifier = NewMenuEntry.findIdentifier(subMenuEntry.getValue() + 1, mainMenuIndex + 2);
            }
        }
        Rectangle rectangle = new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
        if (rs2Item.getSlot() == EquipmentInventorySlot.CAPE.getSlotIdx()) {
            param1 = 25362448;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,16);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.HEAD.getSlotIdx()) {
            param1 = 25362447;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,15);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.AMMO.getSlotIdx()) {
            param1 = 25362457;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,25);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.AMULET.getSlotIdx()) {
            param1 = 25362449;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,17);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx()) {
            param1 = 25362450;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,18);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.BODY.getSlotIdx()) {
            param1 = 25362451;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,19);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.SHIELD.getSlotIdx()) {
            param1 = 25362452;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,20);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.LEGS.getSlotIdx()) {
            param1 = 25362453;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,21);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.GLOVES.getSlotIdx()) {
            param1 = 25362454;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,22);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.BOOTS.getSlotIdx()) {
            param1 = 25362455;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,23);
        } else if (rs2Item.getSlot() == EquipmentInventorySlot.RING.getSlotIdx()) {
            param1 = 25362456;
            rectangle = getSafeBounds(InterfaceID.WORNITEMS,24);
        }

        Microbot.doInvoke(new NewMenuEntry()
                .param0(param0)
                .param1(param1)
                .opcode(menuAction.getId())
                .identifier(identifier)
                .itemId(-1)
                .target(rs2Item.getName())
                ,
                rectangle);
        //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), identifier, rs2Item.id, action, target, -1, -1);
    }

    private static Rectangle getSafeBounds(int interfaceId, int childId) {
        Widget widget = Rs2Widget.getWidget(interfaceId, childId);
        if (widget != null && widget.getBounds() != null) {
            return widget.getBounds();
        }
        return new Rectangle(1, 1,
                Microbot.getClient().getCanvasWidth(),
                Microbot.getClient().getCanvasHeight());
    }
}