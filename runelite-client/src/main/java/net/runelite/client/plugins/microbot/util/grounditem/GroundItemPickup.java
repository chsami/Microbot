package net.runelite.client.plugins.microbot.util.grounditem;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;

/** Client-thread snapshots and one bounded pickup attempt; no Ground Items plugin dependency. */
public final class GroundItemPickup {
    private GroundItemPickup() { }

    public enum Result {
        COLLECTED, GROUND_CHANGED, NO_SPACE, REJECTED, TIMED_OUT, CANCELLED;
        public boolean madeProgress() { return this == COLLECTED || this == GROUND_CHANGED; }
    }

    public static boolean cancelled() {
        return Thread.currentThread().isInterrupted() || Microbot.pauseAllScripts.get() || InputArbiter.isHuman();
    }

    public static final class Snapshot {
        public final Rs2TileItemModel model;
        public final GroundItem item;
        public final WorldView view;
        public final int quantity;
        public final long totalValue;
        public final int despawnTicks;
        Snapshot(Rs2TileItemModel model, GroundItem item, WorldView view, int despawnTicks) {
            this.model = model;
            this.item = item;
            this.view = view;
            this.quantity = item.getQuantity();
            this.totalValue = (long) Microbot.getItemManager().getItemPrice(item.getId()) * quantity;
            this.despawnTicks = despawnTicks;
        }
    }

    public static List<Snapshot> snapshot() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || Microbot.getClient().getGameState() != GameState.LOGGED_IN) return Collections.<Snapshot>emptyList();
            List<Snapshot> result = new ArrayList<>();
            Microbot.getRs2TileItemCache().getStream().forEach(model -> {
                if (model.getWorldView() != player.getWorldView() || !isCurrent(model)) return;
                ItemComposition definition = Microbot.getClient().getItemDefinition(model.getId());
                if (definition == null) return;
                int remaining = Math.max(0, model.getDespawnTime() - Microbot.getClient().getTickCount());
                GroundItem item = GroundItem.builder().id(model.getId()).itemId(model.getId())
                        .name(definition.getName()).quantity(model.getQuantity()).location(model.getWorldLocation())
                        .stackable(definition.isStackable()).tradeable(definition.isTradeable())
                        .ownership(model.getOwnership()).gePrice(Microbot.getItemManager().getItemPrice(model.getId()))
                        .spawnTime(Instant.now()).despawnTime(Duration.ofMillis(remaining * 600L)).build();
                result.add(new Snapshot(model, item, player.getWorldView(), remaining));
            });
            return result;
        }).orElse(Collections.emptyList());
    }

    /** Legacy location-only requests are restricted to the player's current view. */
    public static Snapshot find(GroundItem item) {
        if (item == null || item.getLocation() == null) return null;
        return snapshot().stream().filter(s -> s.item.getId() == item.getId()
                && s.item.getLocation().equals(item.getLocation())).findFirst().orElse(null);
    }

    public static Result take(Snapshot target, int minimumFreeSlots) {
        if (cancelled()) return Result.CANCELLED;
        if (target == null) return Result.REJECTED;
        int free = Rs2Inventory.emptySlotCount();
        boolean existingStack = target.item.isStackable() && Rs2Inventory.hasItem(target.item.getId());
        if (free - (existingStack ? 0 : 1) < Math.max(0, minimumFreeSlots)) return Result.NO_SPACE;
        int before = Rs2Inventory.itemQuantity(target.item.getId());
        if (!click(target.model, "Take")) return cancelled() ? Result.CANCELLED : Result.REJECTED;
        Global.sleepUntil(() -> cancelled() || Rs2Inventory.itemQuantity(target.item.getId()) > before
                || currentQuantity(target.model) < target.quantity, 5000);
        if (cancelled()) return Result.CANCELLED;
        if (Rs2Inventory.itemQuantity(target.item.getId()) > before) return Result.COLLECTED;
        if (currentQuantity(target.model) < target.quantity) return Result.GROUND_CHANGED;
        return Result.TIMED_OUT;
    }

    static int currentQuantity(Rs2TileItemModel model) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> isCurrent(model) ? model.getQuantity() : -1).orElse(-1);
    }

    public static boolean click(Rs2TileItemModel model, String action) {
        if (model == null || action == null || action.isBlank() || cancelled() || Microbot.getClient().isClientThread()) return false;
        try {
            LocalPoint local = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    isCurrent(model) ? model.getLocalLocation() : null).orElse(null);
            if (local == null) return false;
            if (!Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Camera.isTileOnScreen(local)).orElse(false)) Rs2Camera.turnTo(local);
            Map.Entry<NewMenuEntry, Rectangle> dispatch = Microbot.getClientThread().runOnClientThreadOptional(() -> prepare(model, action)).orElse(null);
            if (dispatch == null || cancelled()) return false;
            net.runelite.api.Point point = Rs2UiHelper.getClickingPoint(dispatch.getValue(), true);
            return Microbot.getMouse().tryClick(point, dispatch.getKey(), () -> !cancelled()
                    && Microbot.getClientThread().runOnClientThreadOptional(() -> {
                        Map.Entry<NewMenuEntry, Rectangle> fresh = prepare(model, action);
                        return fresh != null && fresh.getKey().getType() == dispatch.getKey().getType()
                                && fresh.getValue().contains(point.getX(), point.getY());
                    }).orElse(false));
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static Map.Entry<NewMenuEntry, Rectangle> prepare(Rs2TileItemModel model, String action) {
        assert Microbot.getClient().isClientThread();
        // An active item/spell selection must never turn Take into Use/Cast on a ground item.
        if (!isCurrent(model) || Microbot.getClient().isWidgetSelected()) return null;
        String[] actions = Rs2Reflection.getGroundItemActionsStrict(Microbot.getClient().getItemDefinition(model.getId()));
        int index = -1;
        for (int i = 0; i < Math.min(5, actions.length); i++) if (action.equalsIgnoreCase(actions[i])) { index = i; break; }
        if (index < 0) return null;

        LocalPoint local = model.getLocalLocation();
        Polygon canvas = Perspective.getCanvasTilePoly(Microbot.getClient(), local);
        if (canvas == null) return null;
        Rectangle bounds = canvas.getBounds().intersection(new Rectangle(0, 0,
                Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        if (bounds.isEmpty()) return null;
        NewMenuEntry entry = new NewMenuEntry().option(actions[index]).target("<col=ff9040>" + model.getName())
                .identifier(model.getId()).opcode(groundItemMenuAction(index).getId()).param0(local.getSceneX()).param1(local.getSceneY())
                .itemId(-1).worldViewId(model.getWorldView().getId());
        return new AbstractMap.SimpleImmutableEntry<>(entry, bounds);
    }

    static MenuAction groundItemMenuAction(int index) {
        MenuAction[] actions = {MenuAction.GROUND_ITEM_FIRST_OPTION, MenuAction.GROUND_ITEM_SECOND_OPTION,
                MenuAction.GROUND_ITEM_THIRD_OPTION, MenuAction.GROUND_ITEM_FOURTH_OPTION, MenuAction.GROUND_ITEM_FIFTH_OPTION};
        return index < 0 || index >= actions.length ? null : actions[index];
    }
    private static boolean isCurrent(Rs2TileItemModel model) {
        assert Microbot.getClient().isClientThread();
        WorldView view = model.getWorldView();
        if (view == null || Microbot.getClient().getGameState() != GameState.LOGGED_IN
                || Microbot.getClient().getWorldView(view.getId()) != view || view.getScene() == null) return false;
        Tile tile = model.getTile();
        LocalPoint local = tile.getLocalLocation();
        if (local == null || local.getWorldView() != view.getId()) return false;
        Tile[][][] tiles = view.getScene().getTiles();
        int plane = tile.getPlane(), x = local.getSceneX(), y = local.getSceneY();
        if (plane < 0 || plane >= tiles.length || x < 0 || x >= tiles[plane].length
                || y < 0 || y >= tiles[plane][x].length || tiles[plane][x][y] != tile) return false;
        List<TileItem> items = tile.getGroundItems();
        return items != null && items.stream().anyMatch(item -> item == model.getTileItem());
    }
}
