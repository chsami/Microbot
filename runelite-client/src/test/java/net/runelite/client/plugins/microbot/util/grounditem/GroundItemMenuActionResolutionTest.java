package net.runelite.client.plugins.microbot.util.grounditem;
import net.runelite.api.MenuAction;
import org.junit.Test;
import static org.junit.Assert.*;
public class GroundItemMenuActionResolutionTest {
    @Test public void unresolvedActionCannotBecomeCancel() {
        assertNull(GroundItemPickup.groundItemMenuAction(-1));
        assertNull(GroundItemPickup.groundItemMenuAction(5));
    }
    @Test public void actionSlotsRetainTheirGroundOpcodes() {
        assertEquals(MenuAction.GROUND_ITEM_FIRST_OPTION, GroundItemPickup.groundItemMenuAction(0));
        assertEquals(MenuAction.GROUND_ITEM_THIRD_OPTION, GroundItemPickup.groundItemMenuAction(2));
        assertEquals(MenuAction.GROUND_ITEM_FIFTH_OPTION, GroundItemPickup.groundItemMenuAction(4));
    }
}
