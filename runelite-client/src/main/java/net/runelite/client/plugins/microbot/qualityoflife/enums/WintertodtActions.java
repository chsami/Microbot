package net.runelite.client.plugins.microbot.qualityoflife.enums;

import lombok.Setter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

import java.awt.*;

public enum WintertodtActions {

    FEED("Feed", new NewMenuEntry("Feed", "Burning brazier", 29314, MenuAction.GAME_OBJECT_FIRST_OPTION, 62, 61, false)),
    FLETCH("Fletch", new NewMenuEntry("Fletch Kindle", "", 0, MenuAction.WIDGET_TARGET, Rs2Inventory.slot(ItemID.KNIFE), 9764864, false)),
    NONE("None", null);


    private final String action;
    @Setter
    private NewMenuEntry menuEntry;

    WintertodtActions(String action, NewMenuEntry menuEntry) {
        this.action = action;
        this.menuEntry = menuEntry;
    }

    public static void fletchBrumaRootsOnClicked() {
        int brumaRootSlot = Rs2Inventory.slot(ItemID.WINT_BRUMA_ROOT);
        if (brumaRootSlot == -1) {
            Microbot.log("<col=5F1515>Bruma root not found in inventory</col>");
            return;
        }
        Microbot.log("<col=245C2D>Fletching Kindling</col>");
        NewMenuEntry combinedMenuEntry = new NewMenuEntry("Fletch", "Bruma root", 0, MenuAction.WIDGET_TARGET_ON_WIDGET, brumaRootSlot, 9764864, false);
        combinedMenuEntry.setItemId(ItemID.WINT_BRUMA_ROOT);
        Microbot.doInvoke(combinedMenuEntry, new Rectangle(1, 1));
    }

    public String getAction() {
        return action;
    }

    public NewMenuEntry getMenuEntry() {
        return menuEntry;
    }

}
