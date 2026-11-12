package net.runelite.client.plugins.microbot.configs;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class SpecialAttackConfigsEmptyWeaponSlotTest {

    private static final String SAVED_WEAPON = "Abyssal whip";
    private static final String SAVED_SHIELD = "Dragon defender";
    private static final int SAVED_WEAPON_ID = 4151;

    private MockedStatic<Rs2Equipment> equipment;
    private MockedStatic<Rs2Inventory> inventory;
    private MockedStatic<Rs2Combat> combat;

    @Before
    public void setUp() {
        equipment = mockStatic(Rs2Equipment.class);
        inventory = mockStatic(Rs2Inventory.class);
        combat = mockStatic(Rs2Combat.class);
        combat.when(Rs2Combat::getSpecEnergy).thenReturn(0);

        List<Rs2ItemModel> saved = new ArrayList<>();
        saved.add(item(SAVED_WEAPON, SAVED_WEAPON_ID, EquipmentInventorySlot.WEAPON));
        saved.add(item(SAVED_SHIELD, 12954, EquipmentInventorySlot.SHIELD));
        SpecialAttackConfigs.setCurrentEquipment(saved);
    }

    @After
    public void tearDown() {
        SpecialAttackConfigs.setCurrentEquipment(null);
        combat.close();
        inventory.close();
        equipment.close();
    }

    @Test
    public void emptyWeaponSlotReEquipsSavedGearInsteadOfThrowing() {
        equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(null);

        boolean result = new SpecialAttackConfigs().useSpecWeapon("dragon dagger", 250, false);

        assertFalse(result);
        inventory.verify(() -> Rs2Inventory.wear(SAVED_SHIELD));
        inventory.verify(() -> Rs2Inventory.wear(SAVED_WEAPON));
    }

    @Test
    public void differentWeaponEquippedReEquipsSavedGear() {
        Rs2ItemModel specWeapon = item("Dragon dagger", 1215, EquipmentInventorySlot.WEAPON);
        equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(specWeapon);

        boolean result = new SpecialAttackConfigs().useSpecWeapon("dragon dagger", 250, false);

        assertFalse(result);
        inventory.verify(() -> Rs2Inventory.wear(SAVED_SHIELD));
        inventory.verify(() -> Rs2Inventory.wear(SAVED_WEAPON));
    }

    @Test
    public void savedWeaponStillEquippedDoesNotReEquip() {
        Rs2ItemModel sameWeapon = item(SAVED_WEAPON, SAVED_WEAPON_ID, EquipmentInventorySlot.WEAPON);
        equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(sameWeapon);

        boolean result = new SpecialAttackConfigs().useSpecWeapon("dragon dagger", 250, false);

        assertFalse(result);
        inventory.verify(() -> Rs2Inventory.wear(SAVED_SHIELD), never());
        inventory.verify(() -> Rs2Inventory.wear(SAVED_WEAPON), never());
    }

    private static Rs2ItemModel item(String name, int id, EquipmentInventorySlot slot) {
        Rs2ItemModel model = mock(Rs2ItemModel.class);
        when(model.getName()).thenReturn(name);
        when(model.getId()).thenReturn(id);
        when(model.getSlot()).thenReturn(slot.getSlotIdx());
        return model;
    }
}
