package net.runelite.client.plugins.microbot.api.tileitem.models;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
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

    private final WorldView worldView;

    public Rs2TileItemModel(Tile tileObject, TileItem tileItem) {
        this(tileObject, tileItem, Microbot.getClientThread().invoke(() -> {
            LocalPoint local = tileObject.getLocalLocation();
            return local == null ? null : Microbot.getClient().getWorldView(local.getWorldView());
        }));
    }

    public Rs2TileItemModel(Tile tileObject, TileItem tileItem, WorldView worldView) {
        this.worldView = worldView;
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
        return worldView;
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
        return click("Take");
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
        return net.runelite.client.plugins.microbot.util.grounditem.GroundItemPickup.click(this, action);
    }
}