package net.runelite.client.plugins.microbot.cluesolverv2.util;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.item.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class ClueHelperV2 {

    private final Client client;
    private final ConcurrentHashMap<Class<?>, Field> fieldCache = new ConcurrentHashMap<>();

    private static final Map<String, List<Integer>> REQUIREMENTS_MAP = new HashMap<>();

    static {
        REQUIREMENTS_MAP.put("Spade", Arrays.asList(
                ItemID.SPADE,
                ItemID.EASTFLOOR_SPADE
        ));

        REQUIREMENTS_MAP.put("Light Source", Arrays.asList(
                ItemID.LIT_TORCH,
                ItemID.LIT_CANDLE,
                ItemID.CANDLE_LANTERN_4531,
                ItemID.OIL_LAMP_4524,
                ItemID.BULLSEYE_LANTERN_4550
        ));
    }


    private final Map<Integer, String> requiredItemsMap = new HashMap<>();


    @Inject
    public ClueHelperV2(Client client) {
        this.client = client;
        if (this.client == null) {
            log.error("Client instance was not injected correctly!");
        } else {
            log.debug("Client instance successfully injected.");
        }
    }

    /**
     * Checks if the inventory contains any valid Light Source.
     *
     * @return true if a Light Source is present; false otherwise.
     */
    public boolean hasLightSource() {
        List<Integer> lightSourceIds = REQUIREMENTS_MAP.get("Light Source");
        if (lightSourceIds == null || lightSourceIds.isEmpty()) {
            log.warn("No Light Source items defined in REQUIREMENTS_MAP.");
            return false;
        }

        for (Integer itemId : lightSourceIds) {
            if (Rs2Inventory.contains(itemId)) {
                log.info("Found Light Source with ID: {}", itemId);
                return true;
            }
        }

        log.warn("No Light Source found in inventory.");
        return false;
    }

    /**
     * Retrieves the list of item IDs for a given requirement.
     *
     * @param requirement The name of the requirement (e.g., "Spade", "Light Source").
     * @return An unmodifiable list of item IDs; empty list if none found.
     */
    public List<Integer> getItemIdsForRequirement(String requirement) {
        return REQUIREMENTS_MAP.getOrDefault(requirement, Collections.emptyList());
    }


    /**
     * Attempts to withdraw the first available item that satisfies the given requirement.
     *
     * @param requirement The name of the requirement (e.g., "Spade", "Light Source").
     */
    public void withdrawFirstAvailableItem(String requirement) {
        List<Integer> itemIds = REQUIREMENTS_MAP.get(requirement);
        if (itemIds == null || itemIds.isEmpty()) {
            log.warn("No items defined for requirement: {}", requirement);
            return;
        }

        for (Integer itemId : itemIds) {
            log.info("Attempting to withdraw item: {} with ID: {}", requirement, itemId);
            Rs2Bank.withdrawItem(itemId); // Rs2Bank.withdrawItem is assumed to be a void method

            // After attempting to withdraw, verify if the item is now in the inventory
            if (isItemInInventory(itemId)) {
                log.info("Successfully withdrew item: {} with ID: {}", requirement, itemId);
                break; // Exit after successfully withdrawing one item
            } else {
                log.warn("Failed to withdraw item: {} with ID: {}", requirement, itemId);
            }
        }
    }

    private boolean isItemInInventory(int itemId) {
        // Check inventory
        if (Rs2Inventory.items() != null) {
            for (Rs2Item item : Rs2Inventory.items()) {
                if (item != null && item.getId() == itemId) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Retrieves the clue location from a ClueScroll.
     *
     * @param clue The ClueScroll instance.
     * @return The WorldPoint location of the clue or null if unavailable.
     */
    public WorldPoint getClueLocation(ClueScroll clue) {
        WorldPoint location = getFieldValue(clue, "location", WorldPoint.class, null);
        if (location == null) {
            log.warn("Clue location is null for clue: {}", clue.getClass().getSimpleName());
        } else {
            log.debug("Clue location retrieved: {}", location);
        }
        return location;
    }

    /**
     * Determines the required items for a given clue.
     *
     * @param clue The ClueScroll instance.
     * @return A list of ItemRequirements necessary to solve the clue.
     */
    public List<ItemRequirement> determineRequiredItems(ClueScroll clue) {
        List<ItemRequirement> requiredItems = new ArrayList<>();

        requiredItemsMap.clear();

        try {
            Field itemRequirementsField = getFieldFromClassHierarchy(clue.getClass(), "itemRequirements");

            if (itemRequirementsField != null) {
                itemRequirementsField.setAccessible(true);
                ItemRequirement[] clueItemRequirements = (ItemRequirement[]) itemRequirementsField.get(clue);

                if (clueItemRequirements != null) {
                    log.info("Added item requirements via direct field access. Number of items: {}", clueItemRequirements.length);

                    for (ItemRequirement req : clueItemRequirements) {
                        if (req != null) {
                            log.info("Adding item requirement: {}", req.getCollectiveName(client));
                            requiredItems.add(req);
                            handleItemRequirement(req);
                        } else {
                            log.warn("Encountered a null item requirement in itemRequirements array.");
                        }
                    }
                } else {
                    log.warn("The itemRequirements field is null.");
                }
            } else {
                log.info("The clue does not have an 'itemRequirements' field.");
            }

            requiredItems.forEach(requirement -> {
                if (client == null) {
                    log.error("Client is null when getting collective name for requirement: {}", requirement);
                } else {
                    try {
                        Microbot.getClientThread().invoke(() -> {
                            String collectiveName = requirement.getCollectiveName(client);
                            Integer itemId = getItemIdFromRequirement(requirement);
                            if (itemId != null) {
                                requiredItemsMap.put(itemId, collectiveName);
                                log.info("Cached item - ID: {}, Name: {}", itemId, collectiveName);
                            } else {
                                log.warn("Item ID could not be retrieved for requirement: {}", requirement);
                            }
                        });
                    } catch (Throwable t) {
                        log.error("Error getting collective name for requirement: {}", requirement, t);
                    }
                }
            });
        } catch (Throwable t) {
            log.error("Error determining required items", t);
        }
        return requiredItems;
    }


    /**
     * Extracts the item ID from an ItemRequirement using reflection.
     *
     * @param req The ItemRequirement instance.
     * @return The item ID if accessible; null otherwise.
     */
    private Integer getItemIdFromRequirement(ItemRequirement req) {
        try {
            if (req instanceof SingleItemRequirement) {
                Field itemIdField = SingleItemRequirement.class.getDeclaredField("itemId");
                itemIdField.setAccessible(true);
                return itemIdField.getInt(req);
            } else if (req instanceof AnyRequirementCollection) {
                //TODO Assign anyreqs to list, get ID's from list
                return null;
            }
            // Add more conditions for other ItemRequirement types if necessary
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Unable to access itemId field in requirement: {}", req, e);
        }
        return null;
    }

    /**
     * Handles the different types of ItemRequirements by logging their contents.
     *
     * @param req The ItemRequirement instance.
     */
    private void handleItemRequirement(ItemRequirement req) {
        if (req instanceof SingleItemRequirement) {
            handleSingleItemRequirement((SingleItemRequirement) req);
        } else if (req instanceof AnyRequirementCollection) {
            handleAnyRequirementCollection((AnyRequirementCollection) req);
        } else if (req instanceof MultipleOfItemRequirement) {
            handleMultipleOfItemRequirement((MultipleOfItemRequirement) req);
        } else if (req instanceof RangeItemRequirement) {
            handleRangeItemRequirement((RangeItemRequirement) req);
        } else if (req instanceof SlotLimitationRequirement) {
            handleSlotLimitationRequirement((SlotLimitationRequirement) req);
        } else if (req instanceof AllRequirementsCollection) {
            handleAllRequirementsCollection((AllRequirementsCollection) req);
        } else {
            log.warn("Unknown ItemRequirement type: {}", req.getClass().getSimpleName());
        }
    }

    /**
     * Handles the SingleItemRequirement by logging the item ID.
     *
     * @param singleReq The SingleItemRequirement instance.
     */
    private void handleSingleItemRequirement(SingleItemRequirement singleReq) {
        try {
            Field itemIdField = SingleItemRequirement.class.getDeclaredField("itemId");
            itemIdField.setAccessible(true);
            int itemId = itemIdField.getInt(singleReq);

            log.info("Successfully accessed itemId. SingleItemRequirement - Item ID: {}", itemId);
        } catch (NoSuchFieldException e) {
            log.error("No such field 'itemId' found in SingleItemRequirement. Check for typos or incorrect field name.", e);
        } catch (IllegalAccessException e) {
            log.error("Unable to access itemId field in SingleItemRequirement due to illegal access.", e);
        }
    }

    /**
     * Handles the AnyRequirementCollection by logging the contained requirements.
     *
     * @param anyReq The AnyRequirementCollection instance.
     */
    private void handleAnyRequirementCollection(AnyRequirementCollection anyReq) {
        try {
            Field requirementsField = AnyRequirementCollection.class.getDeclaredField("requirements");
            requirementsField.setAccessible(true);
            ItemRequirement[] requirements = (ItemRequirement[]) requirementsField.get(anyReq);

            if (requirements != null) {
                log.info("AnyRequirementCollection - Number of contained requirements: {}", requirements.length);
                for (ItemRequirement req : requirements) {
                    if (req != null) {
                        handleItemRequirement(req);
                    } else {
                        log.warn("Encountered a null item requirement in AnyRequirementCollection.");
                    }
                }
            } else {
                log.warn("AnyRequirementCollection has no contained requirements.");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Unable to access requirements field in AnyRequirementCollection", e);
        }
    }

    /**
     * Handles the MultipleOfItemRequirement by logging the item ID and required quantity.
     *
     * @param multipleReq The MultipleOfItemRequirement instance.
     */
    private void handleMultipleOfItemRequirement(MultipleOfItemRequirement multipleReq) {
        try {
            Field itemIdField = MultipleOfItemRequirement.class.getDeclaredField("itemId");
            itemIdField.setAccessible(true);
            int itemId = itemIdField.getInt(multipleReq);

            Field quantityField = MultipleOfItemRequirement.class.getDeclaredField("quantity");
            quantityField.setAccessible(true);
            int quantity = quantityField.getInt(multipleReq);

            log.info("Successfully accessed fields. MultipleOfItemRequirement - Item ID: {}, Required Quantity: {}", itemId, quantity);
        } catch (NoSuchFieldException e) {
            log.error("One of the fields (itemId or quantity) not found in MultipleOfItemRequirement. Check for typos or incorrect field name.", e);
        } catch (IllegalAccessException e) {
            log.error("Unable to access fields in MultipleOfItemRequirement due to illegal access.", e);
        }
    }

    /**
     * Handles the RangeItemRequirement by logging the start and end item IDs.
     *
     * @param rangeReq The RangeItemRequirement instance.
     */
    private void handleRangeItemRequirement(RangeItemRequirement rangeReq) {
        try {
            Field startItemIdField = RangeItemRequirement.class.getDeclaredField("startItemId");
            Field endItemIdField = RangeItemRequirement.class.getDeclaredField("endItemId");

            startItemIdField.setAccessible(true);
            endItemIdField.setAccessible(true);

            int startItemId = startItemIdField.getInt(rangeReq);
            int endItemId = endItemIdField.getInt(rangeReq);

            log.info("RangeItemRequirement - Start Item ID: {}, End Item ID: {}", startItemId, endItemId);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Unable to access fields in RangeItemRequirement", e);
        }
    }

    /**
     * Handles the AllRequirementsCollection by logging the contained requirements.
     *
     * @param allReq The AllRequirementsCollection instance.
     */
    private void handleAllRequirementsCollection(AllRequirementsCollection allReq) {
        try {
            Field requirementsField = AllRequirementsCollection.class.getDeclaredField("requirements");
            requirementsField.setAccessible(true);
            ItemRequirement[] requirements = (ItemRequirement[]) requirementsField.get(allReq);

            if (requirements != null) {
                log.info("AllRequirementsCollection - Number of contained requirements: {}", requirements.length);
                for (ItemRequirement req : requirements) {
                    if (req != null) {
                        handleItemRequirement(req);
                    }
                }
            } else {
                log.warn("AllRequirementsCollection has no contained requirements.");
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Unable to access requirements field in AllRequirementsCollection", e);
        }
    }

    /**
     * Handles the SlotLimitationRequirement by logging the required equipment slots.
     *
     * @param slotReq The SlotLimitationRequirement instance.
     */
    private void handleSlotLimitationRequirement(SlotLimitationRequirement slotReq) {
        try {
            Field slotsField = SlotLimitationRequirement.class.getDeclaredField("slots");
            slotsField.setAccessible(true);
            EquipmentInventorySlot[] slots = (EquipmentInventorySlot[]) slotsField.get(slotReq);

            StringBuilder slotNames = new StringBuilder();
            for (EquipmentInventorySlot slot : slots) {
                slotNames.append(slot.name()).append(" ");
            }
            log.info("SlotLimitationRequirement - Slots: {}", slotNames.toString().trim());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            log.error("Unable to access slots field in SlotLimitationRequirement", e);
        }
    }

    /**
     * Retrieves a list of missing items based on the current inventory and equipment.
     *
     * @param requirements The list of required ItemRequirements.
     * @return A list of ItemRequirements that are missing from the player's inventory and equipment.
     */
    public List<ItemRequirement> getMissingItems(List<ItemRequirement> requirements) {

        ItemContainer inventoryContainer = client.getItemContainer(InventoryID.INVENTORY);
        ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);

        Item[] inventoryItemsArray = inventoryContainer != null ? inventoryContainer.getItems() : new Item[0];
        Item[] equippedItemsArray = equipmentContainer != null ? equipmentContainer.getItems() : new Item[0];

        List<Item> allItems = new ArrayList<>();
        Collections.addAll(allItems, inventoryItemsArray);
        Collections.addAll(allItems, equippedItemsArray);

        return requirements.stream()
                .filter(req -> !req.fulfilledBy(allItems.toArray(new Item[0])))
                .collect(Collectors.toList());
    }

    /**
     * Generic method to retrieve the value of a specified field with an optional default value.
     * Adds detailed logging for tracing.
     *
     * @param obj          The object instance containing the field.
     * @param fieldName    The name of the field to retrieve.
     * @param fieldType    The Class type of the field.
     * @param defaultValue The default value to return if retrieval fails.
     * @param <T>          The type parameter.
     * @return The value of the field or the default value.
     */
    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String fieldName, Class<T> fieldType, T defaultValue) {
        try {
            Field field = getCachedField(obj.getClass(), fieldName);
            if (field != null) {
                Object value = field.get(obj);
                if (fieldType.isInstance(value)) {
                    log.debug("Retrieved field '{}' with value: {}", fieldName, value);
                    return (T) value;
                } else {
                    log.warn("Field '{}' in '{}' is not of type '{}'. Actual type: '{}'", fieldName, obj.getClass().getSimpleName(), fieldType.getSimpleName(), value != null ? value.getClass().getSimpleName() : "null");
                }
            } else {
                log.warn("Field '{}' not found in class '{}'", fieldName, obj.getClass().getSimpleName());
            }
        } catch (IllegalAccessException e) {
            log.error("Error accessing field '{}' in '{}'", fieldName, obj.getClass().getSimpleName(), e);
        } catch (ClassCastException e) {
            log.error("Type mismatch when casting field '{}' in '{}'", fieldName, obj.getClass().getSimpleName(), e);
        }
        return defaultValue;
    }

    /**
     * Caches and retrieves a Field object from a class.
     *
     * @param clazz     The Class to search for the field.
     * @param fieldName The name of the field.
     * @return The Field object or null if not found.
     */
    private Field getCachedField(Class<?> clazz, String fieldName) {
        return fieldCache.computeIfAbsent(clazz, c -> getFieldFromClassHierarchy(c, fieldName));
    }

    /**
     * Traverses the class hierarchy to find a declared field.
     *
     * @param clazz     The Class to start searching from.
     * @param fieldName The name of the field.
     * @return The Field object or null if not found.
     */
    private Field getFieldFromClassHierarchy(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                Field field = currentClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                log.debug("Cached field '{}' from class '{}'", fieldName, currentClass.getSimpleName());
                return field;
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        log.warn("Field '{}' not found in class hierarchy of '{}'", fieldName, Objects.requireNonNull(clazz).getSimpleName());
        return null;
    }


    /**
     * Getter for the requiredItemsMap.
     *
     * @return An unmodifiable view of the requiredItemsMap.
     */
    public Map<Integer, String> getRequiredItemsMap() {
        return Collections.unmodifiableMap(requiredItemsMap);
    }
}