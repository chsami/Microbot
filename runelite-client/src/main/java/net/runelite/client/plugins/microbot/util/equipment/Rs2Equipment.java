package net.runelite.client.plugins.microbot.util.equipment;

import net.runelite.api.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class Rs2Equipment {
    public static ItemContainer equipment() {
        return Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);
    }

    public static List<Rs2ItemModel> equipmentItems = new ArrayList<>();
    
    public static List<Rs2ItemModel> items() {
        return equipmentItems;
    }

    public static void storeEquipmentItemsInMemory(ItemContainerChanged e) {
        if (e.getContainerId() == InventoryID.EQUIPMENT.getId() && e.getItemContainer() != null) {
            List<Rs2ItemModel> _equipmentItems = new ArrayList<>();
            for (int i = 0; i < e.getItemContainer().getItems().length; i++) {
                Item item = equipment().getItems()[i];
                if (item.getId() == -1) continue;
                int finalI = i;
                Optional<EquipmentInventorySlot> equipmentSlot = Arrays.stream(EquipmentInventorySlot.values()).filter(x -> x.getSlotIdx() == finalI).findFirst();
                if (equipmentSlot.isEmpty()) continue;
                int slot = equipmentSlot.get().getSlotIdx();
                ItemComposition itemComposition = Microbot.getClientThread().runOnClientThreadOptional(() ->
                        Microbot.getClient().getItemDefinition(item.getId())).orElse(null);
                _equipmentItems.add(new Rs2ItemModel(item, itemComposition, slot));
            }
            equipmentItems = _equipmentItems;
        }
    }

    public static boolean useCapeAction(int itemId, String action) {
        if (!hasEquippedSlot(EquipmentInventorySlot.CAPE)) {
            Microbot.status = "Cape is missing in the equipment slot";
            return false;
        }

        Rs2ItemModel item = get(itemId);

        if (item== null) {
            return false;
        }

        int identifier = -1;
        for (int i = 0; i < item.getEquipmentActions().size(); i++) {
            if (item.getEquipmentActions().get(i) != null && item.getEquipmentActions().get(i).toLowerCase().contains(action.toLowerCase())) {
                identifier = i + 2;
                break;
            }
        }

        if (identifier < 0) {
            Microbot.log("Failed to find action: " + action + " in your cape slot");
            return false;
        }

        Microbot.doInvoke(new NewMenuEntry(-1, 25362448, MenuAction.CC_OP.getId(), identifier, -1, action),
                new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    public static boolean useRingAction(JewelleryLocationEnum jewelleryLocationEnum) {
        if (!hasEquippedSlot(EquipmentInventorySlot.RING)) {
            Microbot.status = "Amulet is missing in the equipment slot";
            return false;
        }
        Microbot.doInvoke(new NewMenuEntry(-1, 25362456, MenuAction.CC_OP.getId(), jewelleryLocationEnum.getIdentifier(), -1, "Equip"),
                new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }

    public static boolean useAmuletAction(JewelleryLocationEnum jewelleryLocationEnum) {
        if (!hasEquippedSlot(EquipmentInventorySlot.AMULET) || !hasEquippedContains(jewelleryLocationEnum.getTooltip())) {
            Microbot.status = "Amulet is missing in the equipment slot";
            return false;
        }
        Microbot.doInvoke(new NewMenuEntry(-1, 25362449, MenuAction.CC_OP.getId(), jewelleryLocationEnum.getIdentifier(), -1, "Equip"),
                new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        return true;
    }


    public static Rs2ItemModel get(EquipmentInventorySlot slot) {
        return equipmentItems.stream().filter(x -> x.slot == slot.getSlotIdx()).findFirst().orElse(null);
    }

    public static Rs2ItemModel get(int id) {
        return equipmentItems.stream().filter(x -> x.id == id).findFirst().orElse(null);
    }

    public static Rs2ItemModel get(String name, boolean exact) {
        if (exact) {
            return equipmentItems.stream().filter(x -> x.name.equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
        }
        return equipmentItems.stream().filter(x -> x.name.toLowerCase().contains(name.toLowerCase()))
                .findFirst()
                .orElse(null);

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
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (EquipmentInventorySlot value : EquipmentInventorySlot.values()) {
                Rs2ItemModel item = get(value);
                if (item == null) continue;
                if (item.name.equalsIgnoreCase(itemName)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    public static boolean hasEquippedContains(String itemName) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            for (EquipmentInventorySlot value : EquipmentInventorySlot.values()) {
                Rs2ItemModel item = get(value);
                if (item == null) continue;
                if (item.name.toLowerCase().contains(itemName.toLowerCase())) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    public static boolean hasEquipped(int id) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {

            final ItemContainer container = Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);

            if (container == null) return false;
            for (EquipmentInventorySlot value : EquipmentInventorySlot.values()) {
                Item itemSlot = container.getItem(value.getSlotIdx());
                if (itemSlot == null) continue;
                if (itemSlot.getId() == id) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    public static boolean hasEquippedSlot(EquipmentInventorySlot slot) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {

            final ItemContainer container =  Microbot.getClient().getItemContainer(InventoryID.EQUIPMENT);

            if (container == null) return false;

            Item itemSlot = container.getItem(slot.getSlotIdx());

            return itemSlot != null;
        }).orElse(false);
    }

    public static boolean isEquipped(String name, EquipmentInventorySlot slot) {
        return isEquipped(name, slot, false);
    }

    public static boolean isEquipped(int id, EquipmentInventorySlot slot) {
        final Rs2ItemModel item = get(slot);

        return item != null && item.id == id;
    }

    public static boolean isEquipped(String name, EquipmentInventorySlot slot, boolean exact) {
        final Rs2ItemModel item = get(slot);
        if (exact) {
            return item != null && item.name.equalsIgnoreCase(name);
        } else {
            return item != null && item.name.toLowerCase().contains(name.toLowerCase());
        }
    }

    public static boolean hasGuthanWeaponEquiped() {
        return isEquipped("guthan's warspear", EquipmentInventorySlot.WEAPON);
    }

    public static boolean hasGuthanBodyEquiped() {
        return isEquipped("guthan's platebody", EquipmentInventorySlot.BODY);
    }

    public static boolean hasGuthanLegsEquiped() {
        return isEquipped("guthan's chainskirt", EquipmentInventorySlot.LEGS);
    }

    public static boolean hasGuthanHelmEquiped() {
        return isEquipped("guthan's helm", EquipmentInventorySlot.HEAD);
    }

    public static boolean isWearingFullGuthan() {
        return hasGuthanBodyEquiped() && hasGuthanWeaponEquiped() &&
                hasGuthanHelmEquiped() && hasGuthanLegsEquiped();
    }

    public static boolean isWearing(String name) {
        return isWearing(name, false);
    }

    public static boolean isWearing(int id) {
        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()
        ) {
            if (isEquipped(id, slot)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWearing(String name, boolean exact) {
        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()
        ) {
            if (isEquipped(name, slot, exact)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWearing(List<String> names, boolean exact, List<EquipmentInventorySlot> ignoreSlots) {
        for (String name : names) {
            for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
                if (ignoreSlots.stream().anyMatch(x -> x.getSlotIdx() == slot.getSlotIdx()))
                    continue;
                if (!isEquipped(name, slot, exact)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     *
     * @param id
     * @return
     */
    public static boolean unEquip(int id) {
        return interact(id, "remove");
    }

    public static boolean unEquip(EquipmentInventorySlot slot) {
        return interact(slot, "remove");
    }

    public static boolean interact(EquipmentInventorySlot slot, String action) {
        Rs2ItemModel item = get(slot);
        if (item != null) {
            invokeMenu(item, action);
            return true;
        }
        return false;
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
        Rs2ItemModel item = get(id);
        if (item != null) {
            invokeMenu(item, action);
            return true;
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
     * @return True if the item was found and the action was performed, otherwise false.
     */
    public static boolean interact(String name, String action) {
        Rs2ItemModel item = get(name);
        if (item != null) {
            invokeMenu(item, action);
            return true;
        }
        return false;
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
        for (Integer id : ids) {
            Rs2ItemModel item = get(id);
            if (item != null) {
                invokeMenu(item, action);
                return true;
            }
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
        Rs2ItemModel item = get(name, exact);
        if (item != null) {
            invokeMenu(item, action);
            return true;
        }
        return false;
    }

    public static boolean isWearingShield() {
        return equipmentItems.stream().anyMatch(x -> x.getSlot() == EquipmentInventorySlot.SHIELD.getSlotIdx());
    }

    public static boolean isNaked() {
        return equipmentItems.stream().allMatch(x -> x.id == -1);
    }

    public static void invokeMenu(Rs2ItemModel rs2Item, String action) {
        if (rs2Item == null) return;

        Rs2Tab.switchToEquipmentTab();
        Microbot.status = action + " " + rs2Item.name;

        int param0 = -1;
        int param1 = -1;
        int identifier = action.equalsIgnoreCase("remove") ? 1 : 0;
        MenuAction menuAction = MenuAction.CC_OP;
        if (identifier == 0) {
            if (action.isEmpty()) return;

            List<String> actions = rs2Item.getEquipmentActions();

            for (int i = 0; i < actions.size(); i++) {
                System.out.println(actions.get(i));
                if (action.equalsIgnoreCase(actions.get(i))) {
                    identifier = i + 2;
                    break;
                }
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


        Microbot.doInvoke(new NewMenuEntry(param0, param1, menuAction.getId(), identifier, -1, rs2Item.name), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        //Rs2Reflection.invokeMenu(param0, param1, menuAction.getId(), identifier, rs2Item.id, action, target, -1, -1);
    }
}
