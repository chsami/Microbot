package net.runelite.client.plugins.microbot.api.tileitem.models;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;

import java.awt.*;
import java.util.Arrays;
import java.util.function.Supplier;

@Slf4j
public class Rs2TileItemModel implements TileItem, IEntity {

    @Getter
    private final Tile tile;
    @Getter
    private final TileItem tileItem;

    public Rs2TileItemModel(Tile tileObject, TileItem tileItem) {
        this.tile = tileObject;
        this.tileItem = tileItem;
    }
    
    @Override
    public int getId() {
        return tileItem.getId();
    }

    @Override
    public int getQuantity() {
        return tileItem.getQuantity();
    }

    @Override
    public int getVisibleTime() {
        return tileItem.getVisibleTime();
    }

    @Override
    public int getDespawnTime() {
        return tileItem.getDespawnTime();
    }

    @Override
    public int getOwnership() {
        return tileItem.getOwnership();
    }

    @Override
    public boolean isPrivate() {
        return tileItem.isPrivate();
    }

    @Override
    public Model getModel() {
        return tileItem.getModel();
    }

    @Override
    public int getModelHeight() {
        return tileItem.getModelHeight();
    }

    @Override
    public void setModelHeight(int modelHeight) {
        tileItem.setModelHeight(modelHeight);
    }

    @Override
    public int getAnimationHeightOffset() {
        return tileItem.getAnimationHeightOffset();
    }

    @Override
    public int getRenderMode() {
        return tileItem.getRenderMode();
    }

    @Override
    public Node getNext() {
        return tileItem.getNext();
    }

    @Override
    public Node getPrevious() {
        return tileItem.getPrevious();
    }

    @Override
    public long getHash() {
        return tileItem.getHash();
    }

    public String getName() {
        return Microbot.getClientThread().invoke(() -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            return itemComposition.getName();
        });
    }

    public WorldPoint getWorldLocation() {
        return tile.getWorldLocation();
    }

    public LocalPoint getLocalLocation() {
        return tile.getLocalLocation();
    }

    @Override
    public WorldView getWorldView() {
        return Microbot.getClient().getTopLevelWorldView();
    }

    public boolean isNoted() {
        return Microbot.getClientThread().invoke((Supplier<Boolean>) () -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            return itemComposition.getNote() == 799;
        });
    }


    public boolean isStackable() {
        return Microbot.getClientThread().invoke((Supplier<Boolean>) () -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            return itemComposition.isStackable();
        });
    }

    public boolean isProfitableToHighAlch() {
        return Microbot.getClientThread().invoke((Supplier<Boolean>) () -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            int highAlchValue = itemComposition.getPrice() * 60 / 100;
            int marketPrice = Microbot.getItemManager().getItemPrice(itemComposition.getId());
            return marketPrice > highAlchValue;
        });
    }

    public boolean willDespawnWithin(int ticks) {
        return getDespawnTime() - Microbot.getClient().getTickCount() <= ticks;
    }

    public boolean isLootAble() {
        return  !(tileItem.getOwnership() == TileItem.OWNERSHIP_OTHER);
    }

    public boolean isOwned() {
        return tileItem.getOwnership() == TileItem.OWNERSHIP_SELF;
    }

    public boolean isDespawned() {
        int despawnTime = getDespawnTime();
        return despawnTime != -1 && despawnTime <= Microbot.getClient().getTickCount();
    }

    public int getTotalGeValue() {
        return Microbot.getClientThread().invoke(() -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            int price = itemComposition.getPrice();
            return price * tileItem.getQuantity();
        });
    }

    public boolean isTradeable()  {
        return Microbot.getClientThread().invoke((Supplier<Boolean>) () -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            return itemComposition.isTradeable();
        });
    }

    public boolean isMembers()  {
        return Microbot.getClientThread().invoke((Supplier<Boolean>) () -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            return itemComposition.isMembers();
        });
    }

    public int getTotalValue() {
        return Microbot.getClientThread().invoke(() -> {
            ItemComposition itemComposition = Microbot.getClient().getItemDefinition(tileItem.getId());
            int price = Microbot.getItemManager().getItemPrice(itemComposition.getId());
            return price * tileItem.getQuantity();
        });
    }

    public boolean click() {
        return click("");
    }

    /**
     * Picks up this ground item (equivalent to clicking "Take").
     *
     * @return true if the interaction was dispatched successfully
     */
    public boolean pickup() {
        return click("Take");
    }

    public boolean click(String action) {
        try {
            int param0;
            int param1;
            int identifier;
            String target;
            MenuAction menuAction;
            ItemComposition item;

            item = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getItemDefinition(getId())).orElse(null);
            if (item == null) return false;
            identifier = getId();

            LocalPoint localPoint = getLocalLocation();
            if (localPoint == null) return false;

            param0 = localPoint.getSceneX();
            target = "<col=ff9040>" + getName();
            param1 = localPoint.getSceneY();

            String[] groundActions = Rs2Reflection.getGroundItemActions(item);

            int index = -1;
            if (action.isEmpty()) {
                if (groundActions.length == 0 || groundActions[0] == null) return false;
                action = groundActions[0];
                index = 0;
            } else {
                for (int i = 0; i < groundActions.length; i++) {
                    String groundAction = groundActions[i];
                    if (groundAction == null || !groundAction.equalsIgnoreCase(action)) continue;
                    index = i;
                }
            }

            if (Microbot.getClient().isWidgetSelected()) {
                menuAction = MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
            } else {
                menuAction = groundItemMenuAction(index);
                if (menuAction == null) {
                    log.error("Unable to resolve ground action '{}' for item {} ({}); discovered actions={}",
                            action, getName(), getId(), Arrays.toString(groundActions));
                    return false;
                }
            }

            Polygon canvas = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
            Rectangle bounds = canvas != null ? canvas.getBounds()
                    : new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
            Microbot.doInvoke(new NewMenuEntry()
                    .option(action).param0(param0).param1(param1).opcode(menuAction.getId())
                    .identifier(identifier).itemId(-1).target(target), bounds);
            return true;
        } catch (Exception ex) {
            Microbot.logStackTrace("Rs2TileItemModel", ex);
            return false;
        }
    }

    private static MenuAction groundItemMenuAction(int index) {
        switch (index) {
            case 0: return MenuAction.GROUND_ITEM_FIRST_OPTION;
            case 1: return MenuAction.GROUND_ITEM_SECOND_OPTION;
            case 2: return MenuAction.GROUND_ITEM_THIRD_OPTION;
            case 3: return MenuAction.GROUND_ITEM_FOURTH_OPTION;
            case 4: return MenuAction.GROUND_ITEM_FIFTH_OPTION;
            default: return null;
        }
    }
}
