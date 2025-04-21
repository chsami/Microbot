package net.runelite.client.plugins.microbot.util.menu;

import net.runelite.api.*;
import net.runelite.api.widgets.Widget;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public class NewMenuEntry implements MenuEntry {
    private String option;
    private String target;
    private int identifier;
    private MenuAction type;
    private int param0;
    private int param1;
    private boolean forceLeftClick;
    private int itemId;
    private Actor actor;
    private TileObject gameObject;
    private Widget widget;

    public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target) {
        this.option = target;
        this.target = "";
        this.identifier = identifier;
        this.type = MenuAction.of(opcode);
        this.param0 = param0;
        this.param1 = param1;
        this.forceLeftClick = false;
        this.itemId = itemId;
    }

    public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String target, Actor actor) {
        this.option = "Use";
        this.target = target;
        this.identifier = identifier;
        this.type = MenuAction.of(opcode);
        this.param0 = param0;
        this.param1 = param1;
        this.forceLeftClick = false;
        this.itemId = itemId;
        this.actor = actor;
    }

    public NewMenuEntry(int param0, int param1, int opcode, int identifier, int itemId, String option, String target, TileObject gameObject) {
        this.option = "Use";
        this.target = target;
        this.identifier = identifier;
        this.type = MenuAction.of(opcode);
        this.param0 = param0;
        this.param1 = param1;
        this.forceLeftClick = false;
        this.itemId = itemId;
        this.option = option;
        this.gameObject = gameObject;
    }

    public NewMenuEntry(String option, String target, int identifier, MenuAction type, int param0, int param1, boolean forceLeftClick) {
        this.option = option;
        this.target = target;
        this.identifier = identifier;
        this.type = type;
        this.param0 = param0;
        this.param1 = param1;
        this.forceLeftClick = forceLeftClick;
    }

    public NewMenuEntry(String option, int param0, int param1, int opcode, int identifier, int itemId, String target) {
        this.option = option;
        this.target = target;
        this.identifier = identifier;
        this.type = MenuAction.of(opcode);
        this.param0 = param0;
        this.param1 = param1;
        this.forceLeftClick = false;
        this.itemId = itemId;
    }

    public NewMenuEntry() {
    }

    public String getOption() {
        return this.option;
    }

    public MenuEntry setOption(String option) {
        this.option = option;
        return this;
    }

    public String getTarget() {
        return this.target;
    }

    public MenuEntry setTarget(String target) {
        this.target = target;
        return this;
    }

    public int getIdentifier() {
        return this.identifier;
    }

    public MenuEntry setIdentifier(int identifier) {
        this.identifier = identifier;
        return this;
    }

    public MenuAction getType() {
        return this.type;
    }

    public MenuEntry setType(MenuAction type) {
        this.type = type;
        return this;
    }

    public int getParam0() {
        return this.param0;
    }

    public MenuEntry setParam0(int param0) {
        this.param0 = param0;
        return this;
    }

    public int getParam1() {
        return this.param1;
    }

    public MenuEntry setParam1(int param1) {
        this.param1 = param1;
        return this;
    }

    public boolean isForceLeftClick() {
        return this.forceLeftClick;
    }

    public MenuEntry setForceLeftClick(boolean forceLeftClick) {
        this.forceLeftClick = forceLeftClick;
        return this;
    }

    @Override
    public int getWorldViewId() {
        return 0;
    }

    @Override
    public MenuEntry setWorldViewId(int worldViewId) {
        return null;
    }

    public boolean isDeprioritized() {
        return false;
    }

    public MenuEntry setDeprioritized(boolean deprioritized) {
        return this;
    }

    public MenuEntry onClick(Consumer<MenuEntry> callback) {
        return this;
    }

    @Override
    public Consumer<MenuEntry> onClick() {
        return null;
    }

    public MenuEntry getParent() {
        return this;
    }

    public boolean isItemOp() {
        return false;
    }

    public int getItemOp() {
        return 0;
    }

    public int getItemId() {
        return itemId;
    }

    @Override
    public MenuEntry setItemId(int itemId) {
        this.itemId = itemId;
        return this;
    }


    public MenuEntry setWidget(Widget widget) {
        this.widget = widget;
        return this;
    }

    @Nullable
    public Widget getWidget() {
        return widget;
    }

    @Nullable
    public NPC getNpc() {
        return actor instanceof NPC ? (NPC) actor : null;

    }

    @Nullable
    public Player getPlayer() {
        return actor instanceof Player ? (Player) actor : null;
    }

    @Nullable
    public Actor getActor() {
        return actor;
    }

    @Nullable
    public TileObject getGameObject() {
        return gameObject;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public Menu getSubMenu() {
        return null;
    }

    @NotNull
    @Override
    public Menu createSubMenu() {
        return null;
    }

    @Override
    public void deleteSubMenu() {

    }

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof NewMenuEntry)) {
            return false;
        } else {
            NewMenuEntry other = (NewMenuEntry)o;
            if (!other.canEqual(this)) {
                return false;
            } else if (this.getIdentifier() != other.getIdentifier()) {
                return false;
            } else if (this.getParam0() != other.getParam0()) {
                return false;
            } else if (this.getParam1() != other.getParam1()) {
                return false;
            } else if (this.isForceLeftClick() != other.isForceLeftClick()) {
                return false;
            } else {
                Object this$option = this.getOption();
                Object other$option = other.getOption();
                if (this$option == null) {
                    if (other$option != null) {
                        return false;
                    }
                } else if (!this$option.equals(other$option)) {
                    return false;
                }

                Object this$target = this.getTarget();
                Object other$target = other.getTarget();
                if (this$target == null) {
                    if (other$target != null) {
                        return false;
                    }
                } else if (!this$target.equals(other$target)) {
                    return false;
                }

                Object this$type = this.getType();
                Object other$type = other.getType();
                if (this$type == null) {
                    return other$type == null;
                } else return this$type.equals(other$type);
            }
        }
    }

    protected boolean canEqual(Object other) {
        return other instanceof NewMenuEntry;
    }

    public int hashCode() {
        boolean PRIME = true;
        int result = 1;
        result = result * 59 + this.getIdentifier();
        result = result * 59 + this.getParam0();
        result = result * 59 + this.getParam1();
        result = result * 59 + (this.isForceLeftClick() ? 79 : 97);
        Object $option = this.getOption();
        result = result * 59 + ($option == null ? 43 : $option.hashCode());
        Object $target = this.getTarget();
        result = result * 59 + ($target == null ? 43 : $target.hashCode());
        Object $type = this.getType();
        result = result * 59 + ($type == null ? 43 : $type.hashCode());
        return result;
    }

    public String toString() {
        String var10000 = this.getOption();
        return "NewMenuEntry(option=" + var10000 + ", target=" + this.getTarget() + ", identifier=" + this.getIdentifier() + ", type=" + this.getType() + ", param0=" + this.getParam0() + ", param1=" + this.getParam1() + ", forceLeftClick=" + this.isForceLeftClick() + ")";
    }

    /**
     * Calculates the identifier for a given menu option using a custom offset.
     *
     * The identifier is computed using the formula: (menuOption * 65536) + offset.
     * For example:
     *   - menuOption 1 with default offset (+6): (1 * 65536) + 6 = 65542
     *
     * @param menuOption the menu option number (starting at 1)
     * @param offset the offset value to be added (can be positive or negative)
     * @return the corresponding identifier
     */
    public static int findIdentifier(int menuOption, int offset) {
        return menuOption * 65536 + offset;
    }

    /**
     * Calculates the identifier for a given menu option using the default offset of +6.
     *
     * @param menuOption the menu option number (starting at 1)
     * @return the corresponding identifier with the default offset
     */
    public static int findIdentifier(int menuOption) {
        return findIdentifier(menuOption, 6);
    }

}
