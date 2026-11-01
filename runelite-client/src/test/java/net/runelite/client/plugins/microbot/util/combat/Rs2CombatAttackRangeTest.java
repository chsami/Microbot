package net.runelite.client.plugins.microbot.util.combat;

import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2CombatAttackRangeTest {

    private static final int SHORTBOW_ID = 841;
    private static final int WEAPON_CATEGORY = 3;
    private static final int STYLE_ENUM_ID = 1234;
    private static final int STRUCT_ID = 5678;

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Equipment> equipment;
    private EnumComposition weaponStyles;
    private EnumComposition styleEnum;

    @Before
    public void setUp() {
        weaponStyles = mock(EnumComposition.class);
        styleEnum = mock(EnumComposition.class);
        Rs2ItemModel shortbow = mock(Rs2ItemModel.class);
        when(shortbow.getId()).thenReturn(SHORTBOW_ID);

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(() -> Microbot.getVarbitPlayerValue(anyInt())).thenReturn(0);
        microbot.when(() -> Microbot.getVarbitValue(anyInt())).thenReturn(WEAPON_CATEGORY);
        microbot.when(() -> Microbot.getEnum(EnumID.WEAPON_STYLES)).thenReturn(weaponStyles);
        microbot.when(() -> Microbot.getEnum(STYLE_ENUM_ID)).thenReturn(styleEnum);
        when(weaponStyles.getIntValue(WEAPON_CATEGORY)).thenReturn(STYLE_ENUM_ID);

        equipment = Mockito.mockStatic(Rs2Equipment.class);
        equipment.when(() -> Rs2Equipment.get(EquipmentInventorySlot.WEAPON)).thenReturn(shortbow);
    }

    @After
    public void tearDown() {
        equipment.close();
        microbot.close();
    }

    @Test
    public void styleIsNullWhenStyleEnumHasNoIntValues() {
        when(styleEnum.getIntVals()).thenReturn(null);

        assertNull(Rs2Combat.getWeaponAttackStyle());
        assertEquals(7, Rs2Combat.getAttackRange());
    }

    @Test
    public void styleIsNullWhenWeaponStylesEnumIsMissing() {
        microbot.when(() -> Microbot.getEnum(EnumID.WEAPON_STYLES)).thenReturn(null);

        assertNull(Rs2Combat.getWeaponAttackStyle());
        assertEquals(7, Rs2Combat.getAttackRange());
    }

    @Test
    public void styleIsNullWhenWeaponCategoryHasNoEntry() {
        when(weaponStyles.getIntValue(WEAPON_CATEGORY)).thenReturn(-1);

        assertNull(Rs2Combat.getWeaponAttackStyle());
        assertEquals(7, Rs2Combat.getAttackRange());
    }

    @Test
    public void styleIsNullWhenAttackStyleIndexIsOutOfRange() {
        when(styleEnum.getIntVals()).thenReturn(new int[]{STRUCT_ID});
        microbot.when(() -> Microbot.getVarbitPlayerValue(anyInt())).thenReturn(3);

        assertNull(Rs2Combat.getWeaponAttackStyle());
        assertEquals(7, Rs2Combat.getAttackRange());
    }

    @Test
    public void styleIsNullWhenStructIsMissing() {
        when(styleEnum.getIntVals()).thenReturn(new int[]{STRUCT_ID});
        microbot.when(() -> Microbot.getStructComposition(STRUCT_ID)).thenReturn(null);

        assertNull(Rs2Combat.getWeaponAttackStyle());
        assertEquals(7, Rs2Combat.getAttackRange());
    }

    @Test
    public void longrangeStyleExtendsRange() {
        StructComposition struct = mock(StructComposition.class);
        when(struct.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Longrange");
        when(styleEnum.getIntVals()).thenReturn(new int[]{STRUCT_ID});
        microbot.when(() -> Microbot.getStructComposition(STRUCT_ID)).thenReturn(struct);

        assertEquals("Longrange", Rs2Combat.getWeaponAttackStyle());
        assertEquals(9, Rs2Combat.getAttackRange());
    }
}
