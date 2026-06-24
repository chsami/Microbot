package net.runelite.client.plugins.microbot.util.grounditem;

import java.lang.reflect.Method;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

public class GroundItemMenuActionResolutionTest
{
    @Test
    public void legacyGroundItemRejectsUnresolvedAction() throws Exception
    {
        assertUnresolvedActionIsNotCancel(Rs2GroundItem.class);
    }

    @Test
    public void tileItemRejectsUnresolvedAction() throws Exception
    {
        assertUnresolvedActionIsNotCancel(Rs2TileItemModel.class);
    }

    @Test
    public void bothPathsResolveTakeIndexToThirdOption() throws Exception
    {
        assertEquals(MenuAction.GROUND_ITEM_THIRD_OPTION, resolve(Rs2GroundItem.class, 2));
        assertEquals(MenuAction.GROUND_ITEM_THIRD_OPTION, resolve(Rs2TileItemModel.class, 2));
    }

    private static void assertUnresolvedActionIsNotCancel(Class<?> type) throws Exception
    {
        MenuAction result = resolve(type, -1);
        assertNull(result);
        assertNotEquals(MenuAction.CANCEL, result);
    }

    private static MenuAction resolve(Class<?> type, int index) throws Exception
    {
        Method method = type.getDeclaredMethod("groundItemMenuAction", int.class);
        method.setAccessible(true);
        return (MenuAction) method.invoke(null, index);
    }
}
