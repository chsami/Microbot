package net.runelite.client.plugins.microbot.globval.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.GlobalWidgetInfo;

import java.awt.event.KeyEvent;


/**
 * An enumerated type representing the interface tabs and their WidgetInfo.
 */
@AllArgsConstructor
public enum InterfaceTab {
    COMBAT("Combat Options", VarbitID.STONE_COMBAT_KEY, 0,
            GlobalWidgetInfo.FIXED_CLASSIC_COMBAT_OPTIONS,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_COMBAT_OPTIONS,
            GlobalWidgetInfo.RESIZABLE_MODERN_COMBAT_OPTIONS),
    SKILLS("Skills", VarbitID.STONE_STATS_KEY, 1,
            GlobalWidgetInfo.FIXED_CLASSIC_SKILLS,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_SKILLS,
            GlobalWidgetInfo.RESIZABLE_MODERN_SKILLS),
    QUESTS("Quest List", VarbitID.STONE_JOURNAL_KEY, 2,
            GlobalWidgetInfo.FIXED_CLASSIC_QUESTS,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_QUESTS,
            GlobalWidgetInfo.RESIZABLE_MODERN_QUESTS),
    INVENTORY("Inventory", VarbitID.STONE_INV_KEY, 3,
            GlobalWidgetInfo.FIXED_CLASSIC_INVENTORY,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_INVENTORY,
            GlobalWidgetInfo.RESIZABLE_MODERN_INVENTORY),
    EQUIPMENT("Worn Equipment", VarbitID.STONE_WORN_KEY, 4,
            GlobalWidgetInfo.FIXED_CLASSIC_WORN_EQUIPMENT,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_WORN_EQUIPMENT,
            GlobalWidgetInfo.RESIZABLE_MODERN_WORN_EQUIPMENT),
    PRAYER("Prayer", VarbitID.STONE_PRAYER_KEY, 5,
            GlobalWidgetInfo.FIXED_CLASSIC_PRAYER,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_PRAYER,
            GlobalWidgetInfo.RESIZABLE_MODERN_PRAYER),
    MAGIC("Magic", VarbitID.STONE_MAGIC_KEY, 6,
            GlobalWidgetInfo.FIXED_CLASSIC_MAGIC,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_MAGIC,
            GlobalWidgetInfo.RESIZABLE_MODERN_MAGIC),
    FRIENDS("Friends List", VarbitID.STONE_FRIENDS_KEY, 9,
            GlobalWidgetInfo.FIXED_CLASSIC_FRIEND_LIST,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_FRIEND_LIST,
            GlobalWidgetInfo.RESIZABLE_MODERN_FRIEND_LIST),
    SETTINGS("Settings", VarbitID.STONE_OPTIONS1_KEY, 11,
            GlobalWidgetInfo.FIXED_CLASSIC_SETTINGS,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_SETTINGS,
            GlobalWidgetInfo.RESIZABLE_MODERN_SETTINGS),
    MUSIC("Music Player", VarbitID.STONE_MUSIC_KEY, 13,
            GlobalWidgetInfo.FIXED_CLASSIC_MUSIC_PLAYER,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_MUSIC_PLAYER,
            GlobalWidgetInfo.RESIZABLE_MODERN_MUSIC_PLAYER),
    LOGOUT("Logout", VarbitID.STONE_LOGOUT_KEY, 10,
            GlobalWidgetInfo.FIXED_CLASSIC_LOGOUT,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_LOGOUT,
            GlobalWidgetInfo.RESIZABLE_MODERN_LOGOUT),
    CHAT("Chat Channel", VarbitID.STONE_CLANCHAT_KEY, 7,
            GlobalWidgetInfo.FIXED_CLASSIC_CHAT_CHANNEL,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_CHAT_CHANNEL,
            GlobalWidgetInfo.RESIZABLE_MODERN_CHAT_CHANNEL),
    ACC_MAN("Account Management", VarbitID.STONE_ACCOUNT_KEY, 8,
            GlobalWidgetInfo.FIXED_CLASSIC_ACC_MANAGEMENT,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_ACC_MANAGEMENT,
            GlobalWidgetInfo.RESIZABLE_MODERN_ACC_MANAGEMENT),
    EMOTES("Emotes", VarbitID.STONE_OPTIONS2_KEY, 12,
            GlobalWidgetInfo.FIXED_CLASSIC_EMOTES,
            GlobalWidgetInfo.RESIZABLE_CLASSIC_EMOTES,
            GlobalWidgetInfo.RESIZABLE_MODERN_EMOTES),
    // bogus widget info
    NOTHING_SELECTED("NothingSelected", -1, -1,
            GlobalWidgetInfo.CHATBOX_FULL_INPUT,
            GlobalWidgetInfo.CHATBOX_FULL_INPUT,
            GlobalWidgetInfo.CHATBOX_FULL_INPUT);

    @Getter
    private final String name;
    @Getter
    private final int hotkeyVarbit;
    @Getter
    private final int index;
    private final GlobalWidgetInfo fixedClassicInfo;
    private final GlobalWidgetInfo resizableClassicInfo;
    private final GlobalWidgetInfo resizableModernInfo;

    public Widget getFixedClassicWidget() {
        return Microbot.getClient().getWidget(fixedClassicInfo.getGroupId(), fixedClassicInfo.getChildId());
    }

    public Widget getResizableClassicWidget() {
        return Microbot.getClient().getWidget(resizableClassicInfo.getGroupId(), resizableClassicInfo.getChildId());
    }

    public Widget getResizableModernWidget() {
        return Microbot.getClient().getWidget(resizableModernInfo.getGroupId(), resizableModernInfo.getChildId());
    }

    public int getHotkey() {
        // special case for nothing selected
        if (hotkeyVarbit == -1) return -1;

        switch (Microbot.getVarbitValue(hotkeyVarbit)) {
            case 1: return KeyEvent.VK_F1;
            case 2: return KeyEvent.VK_F2;
            case 3: return KeyEvent.VK_F3;
            case 4: return KeyEvent.VK_F4;
            case 5: return KeyEvent.VK_F5;
            case 6: return KeyEvent.VK_F6;
            case 7: return KeyEvent.VK_F7;
            case 8: return KeyEvent.VK_F8;
            case 9: return KeyEvent.VK_F9;
            case 10: return KeyEvent.VK_F10;
            case 11: return KeyEvent.VK_F11;
            case 12: return KeyEvent.VK_F12;
            case 13: return KeyEvent.VK_ESCAPE;
            default: return -1;
        }
    }
}
