package net.runelite.client.plugins.microbot.util.magic;

import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import java.util.HashMap;
import static org.junit.Assert.*;

public class Rs2MagicBlightedSacksTest {
    @Test public void oneSackPaysForOneWholeIceCast() {
        assertEquals(0, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.ICE_RUSH, 1, 1, true));
        assertEquals(0, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.ICE_BURST, 2, 2, true));
        assertEquals(3, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.ICE_BLITZ, 5, 2, true));
        assertEquals(0, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.ICE_BARRAGE, 1, 3, true));
    }
    @Test public void sacksCannotCastOutsideWildernessOrOtherSpells() {
        assertEquals(1, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.ICE_BARRAGE, 1, 1, false));
        assertEquals(1, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.BLOOD_BARRAGE, 1, 1, true));
        assertEquals(1, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.WATER_STRIKE, 1, 1, true));
        assertEquals(1, Rs2Magic.runeCastsAfterIceSacks(Rs2CombatSpells.ICE_BURST, 1, -1, true));
    }
    @Test public void runeChecksHonorInventoryAndBankFilters() {
        try (MockedStatic<Rs2Pvp> pvp = Mockito.mockStatic(Rs2Pvp.class);
             MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Bank> bank = Mockito.mockStatic(Rs2Bank.class);
             MockedStatic<Rs2Magic> magic = Mockito.mockStatic(Rs2Magic.class, Mockito.CALLS_REAL_METHODS)) {
            pvp.when(Rs2Pvp::isInWilderness).thenReturn(true);
            inventory.when(() -> Rs2Inventory.itemQuantity(ItemID.BLIGHTED_SACK_ICEBARRAGE)).thenReturn(2);
            bank.when(() -> Rs2Bank.count(ItemID.BLIGHTED_SACK_ICEBARRAGE)).thenReturn(3);
            magic.when(() -> Rs2Magic.getRunes(Mockito.any(RuneFilter.class))).thenAnswer(call -> new HashMap<>());

            RuneFilter inventoryOnly = RuneFilter.builder().includeBank(false).build();
            RuneFilter bankOnly = RuneFilter.builder().includeInventory(false).includeBank(true).build();
            RuneFilter both = RuneFilter.builder().includeBank(true).build();
            assertEquals(Rs2CombatSpells.ICE_BARRAGE.getRequiredRunes(2),
                    Rs2Magic.getMissingRunes(Rs2CombatSpells.ICE_BARRAGE, 4, inventoryOnly));
            assertEquals(Rs2CombatSpells.ICE_BARRAGE.getRequiredRunes(1),
                    Rs2Magic.getMissingRunes(Rs2CombatSpells.ICE_BARRAGE, 4, bankOnly));
            assertTrue(Rs2Magic.hasRequiredRunes(Rs2CombatSpells.ICE_BARRAGE, 4, both));
            assertFalse(Rs2Magic.hasRequiredRunes(Rs2CombatSpells.ICE_BARRAGE, 4, inventoryOnly));

            pvp.when(Rs2Pvp::isInWilderness).thenReturn(false);
            assertEquals(Rs2CombatSpells.ICE_BARRAGE.getRequiredRunes(4),
                    Rs2Magic.getMissingRunes(Rs2CombatSpells.ICE_BARRAGE, 4, both));
            assertEquals(Rs2CombatSpells.BLOOD_BARRAGE.getRequiredRunes(4),
                    Rs2Magic.getMissingRunes(Rs2CombatSpells.BLOOD_BARRAGE, 4, both));
        }
    }
}
