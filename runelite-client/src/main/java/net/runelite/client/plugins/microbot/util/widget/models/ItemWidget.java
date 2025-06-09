package net.runelite.client.plugins.microbot.util.widget.models;

import net.runelite.api.FontTypeFace;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;

import javax.annotation.Nullable;
import java.awt.*;

public class ItemWidget implements Widget {

    int id;
    int index;
    String name;
    int itemId;
    int quantity;
    private int slot;

    // temp constructor untill we move everything from inventory to a memory list instead of widgets
    public ItemWidget(Widget w) {
        this.name = w.getName();
        this.itemId = w.getItemId();
        this.quantity = w.getItemQuantity();
        this.index = w.getIndex();
    }

    public ItemWidget(String name, int itemId, int quantity, int index) {
        this.name = name;
        this.itemId = itemId;
        this.quantity = quantity;
        this.index = index;
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getType() {
        return 0;
    }

    @Override
    public void setType(int type) {

    }

    @Override
    public int getContentType() {
        return 0;
    }

    @Override
    public Widget setContentType(int contentType) {
        return null;
    }

    @Override
    public int getClickMask() {
        return 0;
    }

    @Override
    public Widget setClickMask(int mask) {
        return null;
    }

    @Override
    public Widget getParent() {
        return null;
    }

    @Override
    public int getParentId() {
        return 0;
    }

    @Override
    public Widget getChild(int index) {
        return null;
    }

    @Nullable
    @Override
    public Widget[] getChildren() {
        return new Widget[0];
    }

    @Override
    public void setChildren(Widget[] children) {

    }

    @Override
    public Widget[] getDynamicChildren() {
        return new Widget[0];
    }

    @Override
    public Widget[] getStaticChildren() {
        return new Widget[0];
    }

    @Override
    public Widget[] getNestedChildren() {
        return new Widget[0];
    }

    @Override
    public int getRelativeX() {
        return 0;
    }

    @Override
    public void setRelativeX(int x) {

    }

    @Override
    public int getRelativeY() {
        return 0;
    }

    @Override
    public void setRelativeY(int y) {

    }

    @Override
    public void setForcedPosition(int x, int y) {

    }

    @Override
    public String getText() {
        return null;
    }

    @Override
    public Widget setText(String text) {
        return null;
    }

    @Override
    public int getTextColor() {
        return 0;
    }

    @Override
    public Widget setTextColor(int textColor) {
        return null;
    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public Widget setOpacity(int transparency) {
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Widget setName(String name) {
        return null;
    }

    @Override
    public int getModelId() {
        return 0;
    }

    @Override
    public Widget setModelId(int id) {
        return null;
    }

    @Override
    public int getModelType() {
        return 0;
    }

    @Override
    public Widget setModelType(int type) {
        return null;
    }

    @Override
    public int getAnimationId() {
        return 0;
    }

    @Override
    public Widget setAnimationId(int animationId) {
        return null;
    }

    @Override
    public int getRotationX() {
        return 0;
    }

    @Override
    public Widget setRotationX(int modelX) {
        return null;
    }

    @Override
    public int getRotationY() {
        return 0;
    }

    @Override
    public Widget setRotationY(int modelY) {
        return null;
    }

    @Override
    public int getRotationZ() {
        return 0;
    }

    @Override
    public Widget setRotationZ(int modelZ) {
        return null;
    }

    @Override
    public int getModelZoom() {
        return 0;
    }

    @Override
    public Widget setModelZoom(int modelZoom) {
        return null;
    }

    @Override
    public int getSpriteId() {
        return 0;
    }

    @Override
    public boolean getSpriteTiling() {
        return false;
    }

    @Override
    public Widget setSpriteTiling(boolean tiling) {
        return null;
    }

    @Override
    public Widget setSpriteId(int spriteId) {
        return null;
    }

    @Override
    public boolean isHidden() {
        return false;
    }

    @Override
    public boolean isSelfHidden() {
        return false;
    }

    @Override
    public Widget setHidden(boolean hidden) {
        return null;
    }

    @Override
    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) {
        this.slot = slot;
    }

    @Override
    public Point getCanvasLocation() {
        return null;
    }

    @Override
    public int getWidth() {
        return 0;
    }

    @Override
    public void setWidth(int width) {

    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public void setHeight(int height) {

    }

    @Override
    public Rectangle getBounds() {
        return null;
    }

    @Override
    public int getItemId() {
        return itemId;
    }

    @Override
    public Widget setItemId(int itemId) {
        return null;
    }

    @Override
    public int getItemQuantity() {
        return quantity;
    }

    @Override
    public Widget setItemQuantity(int quantity) {
        return null;
    }

    @Override
    public boolean contains(Point point) {
        return false;
    }

    @Override
    public int getScrollX() {
        return 0;
    }

    @Override
    public Widget setScrollX(int scrollX) {
        return null;
    }

    @Override
    public int getScrollY() {
        return 0;
    }

    @Override
    public Widget setScrollY(int scrollY) {
        return null;
    }

    @Override
    public int getScrollWidth() {
        return 0;
    }

    @Override
    public Widget setScrollWidth(int width) {
        return null;
    }

    @Override
    public int getScrollHeight() {
        return 0;
    }

    @Override
    public Widget setScrollHeight(int height) {
        return null;
    }

    @Override
    public int getOriginalX() {
        return 0;
    }

    @Override
    public Widget setOriginalX(int originalX) {
        return null;
    }

    @Override
    public int getOriginalY() {
        return 0;
    }

    @Override
    public Widget setOriginalY(int originalY) {
        return null;
    }

    @Override
    public Widget setPos(int x, int y) {
        return null;
    }

    @Override
    public Widget setPos(int x, int y, int xMode, int yMode) {
        return null;
    }

    @Override
    public int getOriginalHeight() {
        return 0;
    }

    @Override
    public Widget setOriginalHeight(int originalHeight) {
        return null;
    }

    @Override
    public int getOriginalWidth() {
        return 0;
    }

    @Override
    public Widget setOriginalWidth(int originalWidth) {
        return null;
    }

    @Override
    public Widget setSize(int width, int height) {
        return null;
    }

    @Override
    public Widget setSize(int width, int height, int widthMode, int heightMode) {
        return null;
    }

    @Nullable
    @Override
    public String[] getActions() {
        String[] actions = new String[10];
        //0
        switch (Microbot.getClient().getVarbitValue(6590)) {
            case 0:
                actions[0] = "Withdraw-1";
                break;
            case 1:
                actions[0] = "Withdraw-5";
                break;
            case 2:
                actions[0] = "Withdraw-10";
                break;
            case 3:
                actions[0] = "Withdraw-" + Microbot.getClient().getVarbitValue(3960);
                break;
            case 4:
                actions[0] = "Withdraw-All";
                break;
        }

        //1
        if (Microbot.getClient().getVarbitValue(6590) != 0) {
            actions[1] = "Withdraw-1";
        }

        //2-3
        actions[2] = "Withdraw-5";
        actions[3] = "Withdraw-10";

        //4
        if (Microbot.getClient().getVarbitValue(3960) > 0) {
            actions[4] = "Withdraw-" + Microbot.getClient().getVarbitValue(3960);
        }

        //5-7
        actions[5] = "Withdraw-X";
        actions[6] = "Withdraw-All";
        actions[7] = "Withdraw-All-but-1";

        //8
        if (Microbot.getClient().getVarbitValue(3755) == 0) {
            actions[8] = "Placeholder";
        }
        //9
        actions[9] = "Examine";
        return actions;
    }

    public Widget createStaticChild(int type) {
        return null;
    }

    @Override
    public Widget createChild(int index, int type) {
        return null;
    }

    @Override
    public Widget createChild(int type) {
        return null;
    }

    @Override
    public void deleteAllChildren() {

    }

    @Override
    public void setAction(int index, String action) {

    }

    @Override
    public void clearActions() {
        
    }

    @Override
    public void setOnOpListener(Object... args) {

    }

    @Override
    public void setOnDialogAbortListener(Object... args) {

    }

    @Override
    public void setOnKeyListener(Object... args) {

    }

    @Override
    public void setOnMouseOverListener(Object... args) {

    }

    @Override
    public void setOnMouseRepeatListener(Object... args) {

    }

    @Override
    public void setOnMouseLeaveListener(Object... args) {

    }

    @Override
    public void setOnTimerListener(Object... args) {

    }

    @Override
    public void setOnTargetEnterListener(Object... args) {

    }

    @Override
    public void setOnTargetLeaveListener(Object... args) {

    }

    @Override
    public boolean hasListener() {
        return false;
    }

    @Override
    public Widget setHasListener(boolean hasListener) {
        return null;
    }

    @Override
    public boolean isIf3() {
        return false;
    }

    @Override
    public void revalidate() {

    }

    @Override
    public void revalidateScroll() {

    }

    @Override
    public Object[] getOnOpListener() {
        return new Object[0];
    }

    @Override
    public Object[] getOnKeyListener() {
        return new Object[0];
    }

    @Override
    public Object[] getOnLoadListener() {
        return new Object[0];
    }

    @Override
    public Object[] getOnInvTransmitListener() {
        return new Object[0];
    }

    @Override
    public int getFontId() {
        return 0;
    }

    @Override
    public Widget setFontId(int id) {
        return null;
    }

    @Override
    public int getBorderType() {
        return 0;
    }

    @Override
    public void setBorderType(int thickness) {

    }

    @Override
    public boolean getTextShadowed() {
        return false;
    }

    @Override
    public Widget setTextShadowed(boolean shadowed) {
        return null;
    }

    @Override
    public int getDragDeadZone() {
        return 0;
    }

    @Override
    public void setDragDeadZone(int deadZone) {

    }

    @Override
    public int getDragDeadTime() {
        return 0;
    }

    @Override
    public void setDragDeadTime(int deadTime) {

    }

    @Override
    public int getItemQuantityMode() {
        return 0;
    }

    @Override
    public Widget setItemQuantityMode(int itemQuantityMode) {
        return null;
    }

    @Override
    public int getXPositionMode() {
        return 0;
    }

    @Override
    public Widget setXPositionMode(int xpm) {
        return null;
    }

    @Override
    public int getYPositionMode() {
        return 0;
    }

    @Override
    public Widget setYPositionMode(int ypm) {
        return null;
    }

    @Override
    public int getLineHeight() {
        return 0;
    }

    @Override
    public Widget setLineHeight(int lineHeight) {
        return null;
    }

    @Override
    public int getXTextAlignment() {
        return 0;
    }

    @Override
    public Widget setXTextAlignment(int xta) {
        return null;
    }

    @Override
    public int getYTextAlignment() {
        return 0;
    }

    @Override
    public Widget setYTextAlignment(int yta) {
        return null;
    }

    @Override
    public int getWidthMode() {
        return 0;
    }

    @Override
    public Widget setWidthMode(int widthMode) {
        return null;
    }

    @Override
    public int getHeightMode() {
        return 0;
    }

    @Override
    public Widget setHeightMode(int heightMode) {
        return null;
    }

    @Override
    public FontTypeFace getFont() {
        return null;
    }

    @Override
    public boolean isFilled() {
        return false;
    }

    @Override
    public Widget setFilled(boolean filled) {
        return null;
    }

    @Override
    public String getTargetVerb() {
        return null;
    }

    @Override
    public void setTargetVerb(String targetVerb) {

    }

	@Override
	public int getTargetPriority()
	{
		return 0;
	}

	@Override
	public void setTargetPriority(int priority)
	{

	}

	@Override
    public boolean getNoClickThrough() {
        return false;
    }

    @Override
    public void setNoClickThrough(boolean noClickThrough) {

    }

    @Override
    public boolean getNoScrollThrough() {
        return false;
    }

    @Override
    public void setNoScrollThrough(boolean noScrollThrough) {

    }

    @Override
    public int[] getVarTransmitTrigger() {
        return new int[0];
    }

    @Override
    public void setVarTransmitTrigger(int... trigger) {

    }

    @Override
    public void setOnClickListener(Object... args) {

    }

    @Override
    public void setOnHoldListener(Object... args) {

    }

    @Override
    public void setOnReleaseListener(Object... args) {

    }

    @Override
    public void setOnDragCompleteListener(Object... args) {

    }

    @Override
    public void setOnDragListener(Object... args) {

    }

    @Override
    public void setOnScrollWheelListener(Object... args) {

    }

    @Override
    public Widget getDragParent() {
        return null;
    }

    @Override
    public Widget setDragParent(Widget dragParent) {
        return null;
    }

    @Override
    public Object[] getOnVarTransmitListener() {
        return new Object[0];
    }

    @Override
    public void setOnVarTransmitListener(Object... args) {

    }
}
