package net.runelite.client.plugins.microbot.api.npc.models;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.IEntity;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.recovery.CantReachTargetRecovery;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.IntStream;

@Getter
@Slf4j
public class Rs2NpcModel extends Rs2ActorModel implements IEntity
{

    private final NPC npc;

    public Rs2NpcModel(final NPC npc)
    {
        super(npc);
        this.npc = npc;
    }

    @Override
    public int getId()
    {
        return Microbot.getClientThread().invoke(() -> npc.getId());
    }

    public int getIndex()
    {
        return Microbot.getClientThread().invoke(() -> npc.getIndex());
    }


    // Enhanced utility methods for cache operations

    /**
     * Checks if this NPC is within a specified distance from the player.
     * Uses client thread for safe access to player location.
     *
     * @param maxDistance Maximum distance in tiles
     * @return true if within distance, false otherwise
     */
    public boolean isWithinDistanceFromPlayer(int maxDistance) {
        int distance = getDistanceFromPlayer();
        return distance != Integer.MAX_VALUE && distance <= maxDistance;
    }

    /**
     * Gets the distance from this NPC to the player.
     * Uses client thread for safe access to player location.
     *
     * @return Distance in tiles
     */
    public int getDistanceFromPlayer() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null || npc.getWorldView() == null || npc.getWorldView() != player.getWorldView())
                return Integer.MAX_VALUE;
            WorldPoint location = npc.getWorldLocation();
            WorldPoint origin = player.getWorldLocation();
            return location == null || origin == null ? Integer.MAX_VALUE : location.distanceTo(origin);
        }).orElse(Integer.MAX_VALUE);
    }

    /**
     * Checks if this NPC is within a specified distance from a given location.
     *
     * @param anchor The anchor point
     * @param maxDistance Maximum distance in tiles
     * @return true if within distance, false otherwise
     */
    public boolean isWithinDistance(WorldPoint anchor, int maxDistance) {
        if (anchor == null) return false;
        WorldPoint location = getSceneWorldLocation();
        if (location == null) return false;
        int distance = location.distanceTo(anchor);
        return distance != Integer.MAX_VALUE && distance <= maxDistance;
    }

    /**
     * Checks if this NPC is currently interacting with the player.
     * Uses client thread for safe access to player reference.
     *
     * @return true if interacting with player, false otherwise
     */
    public boolean isInteractingWithPlayer() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            return this.getInteracting() == Microbot.getClient().getLocalPlayer();
        }).orElse(false);
    }

    /**
     * Checks if this NPC is currently moving.
     *
     * @return true if moving, false if idle
     */
    public boolean isMoving() {

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                this.getPoseAnimation() != this.getIdlePoseAnimation()
        ).orElse(false);
    }

    /**
     * Gets the health percentage of this NPC.
     *
     * @return Health percentage (0-100), or -1 if unknown
     */
    public double getHealthPercentage() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int ratio = npc.getHealthRatio();
            int scale = npc.getHealthScale();
            return scale <= 0 ? -1.0 : (double) ratio / scale * 100.0;
        }).orElse(-1.0);
    }

    public static Predicate<Rs2NpcModel> matches(boolean exact, String... names) {
        return npc -> {
            if (npc == null || names == null) return false;
            String npcName = npc.getName();
            if (npcName == null) return false;
            npcName = npcName.toLowerCase(java.util.Locale.ROOT);
            final String name = npcName;
            return exact ? Arrays.stream(names).filter(java.util.Objects::nonNull).anyMatch(name::equalsIgnoreCase) :
                    Arrays.stream(names).filter(java.util.Objects::nonNull).anyMatch(s -> name.contains(s.toLowerCase(java.util.Locale.ROOT)));
        };
    }

    /**
     * Gets the overhead prayer icon of the NPC, if any.
     * @return
     */
    public HeadIcon getHeadIcon() {
        return Microbot.getClientThread().invoke(this::getHeadIconOnClientThread);
    }

    private HeadIcon getHeadIconOnClientThread() {
        if (npc == null) {
            return null;
        }

        if (npc.getOverheadSpriteIds() == null) {
            Microbot.log("Failed to find the correct overhead prayer.");
            return null;
        }

        for (int i = 0; i < npc.getOverheadSpriteIds().length; i++) {
            int overheadSpriteId = npc.getOverheadSpriteIds()[i];

            if (overheadSpriteId == -1) continue;

            if (overheadSpriteId < 0 || overheadSpriteId >= HeadIcon.values().length) continue;
            return HeadIcon.values()[overheadSpriteId];
        }

        Microbot.log("Found overheadSpriteIds: " + Arrays.toString(npc.getOverheadSpriteIds()) + " but failed to find valid overhead prayer.");

        return null;
    }

    public boolean hasLineOfSight() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            if (npc == null || player == null || npc.getWorldView() != player.getWorldView()) return false;
            WorldPoint location = npc.getWorldLocation();
            WorldPoint origin = player.getWorldLocation();
            WorldView view = npc.getWorldView();
            return location != null && origin != null && view != null
                    && location.getPlane() == origin.getPlane()
                    && location.toWorldArea().hasLineOfSightTo(view, origin);
        }).orElse(false);
    }

    @Override
    public boolean click() {
        return click("");
    }

    @Override
    public boolean click(String action) {
        if (Microbot.getClient().isClientThread() || Thread.currentThread().isInterrupted()) return false;
        if (npc == null) {
            log.error("Error interacting with NPC for action '{}': NPC is null", action);
            return false;
        }

        var npcName = getName();

        Microbot.status = action + " " + npcName;
        try {
            if (CantReachTargetRecovery.shouldStart(
                    Microbot.isCantReachTargetDetectionEnabled, Microbot.cantReachTarget)) {
                if (!hasLineOfSight()) {
                    if (CantReachTargetRecovery.retryExhausted(
                            Microbot.cantReachTargetRetries, Rs2Random.between(3, 5))) {
                        Microbot.pauseAllScripts.compareAndSet(false, true);
                        Microbot.showMessage("Your bot tried to interact with an NPC for "
                                + Microbot.cantReachTargetRetries + " times but failed. Please take a look at what is happening.");
                        return false;
                    }
                    final WorldPoint npcWorldPoint = getWorldLocation();
                    if (npcWorldPoint == null) {
                        log.error("Error interacting with NPC '{}' for action '{}': WorldPoint is null", npcName, action);
                        return false;
                    }
                    Microbot.cantReachTargetRetries++;
                    if (CantReachTargetRecovery.walkTo(npcWorldPoint, 2)) {
                        Microbot.pauseAllScripts.compareAndSet(true, false);
                        Microbot.cantReachTarget = false;
                        Microbot.cantReachTargetRetries = 0;
                        // fall through and click from beside it
                    } else {
                        return false;
                    }
                } else {
                    Microbot.pauseAllScripts.compareAndSet(true, false);
                    Microbot.cantReachTarget = false;
                    Microbot.cantReachTargetRetries = 0;
                }
            }

            final String[] actions = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                if (!isCurrent()) return null;
                NPCComposition composition = npc.getTransformedComposition();
                return composition == null || composition.getActions() == null ? null : composition.getActions().clone();
            }).orElse(null);
            if (actions == null) return false;

            final int index;
            if (action == null || action.isBlank()) {
                index = IntStream.range(0, actions.length)
                        .filter(i -> actions[i] != null && !actions[i].isEmpty())
                        .findFirst().orElse(-1);
            } else {
                final String finalAction = action;
                index = IntStream.range(0, actions.length)
                        .filter(i -> actions[i] != null && actions[i].equalsIgnoreCase(finalAction))
                        .findFirst().orElse(-1);
            }

            final MenuAction menuAction = getMenuAction(index);
            if (menuAction == null) {
                if (index == -1) {
                    log.error("Error interacting with NPC '{}' for action '{}': Action not found. Actions={}", npcName, action, actions);
                } else {
                    log.error("Error interacting with NPC '{}' for action '{}': Invalid Index={}. Actions={}", npcName, action, index, actions);
                }
                return false;
            }
            action = menuAction == MenuAction.WIDGET_TARGET_ON_NPC ? "Use" : actions[index];

            final LocalPoint localPoint = getLocalLocation();
            if (localPoint == null) {
                log.error("Error interacting with NPC '{}' for action '{}': LocalPoint is null", npcName, action);
                return false;
            }
            if (!Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Camera.isTileOnScreen(localPoint)).orElse(false)) {
                Rs2Camera.turnTo(npc);
            }

            final String resolvedAction = action;
            java.util.Map.Entry<NewMenuEntry, java.awt.Rectangle> dispatch = Microbot.getClientThread()
                    .runOnClientThreadOptional(() -> {
                        if (!isCurrent()) return null;
                        NPCComposition composition = npc.getTransformedComposition();
                        String[] currentActions = composition == null ? null : composition.getActions();
                        if (menuAction != MenuAction.WIDGET_TARGET_ON_NPC && (currentActions == null
                                || index < 0 || index >= currentActions.length
                                || !resolvedAction.equalsIgnoreCase(currentActions[index]))) return null;
                        return new java.util.AbstractMap.SimpleImmutableEntry<>(new NewMenuEntry()
                                .param0(0).param1(0).opcode(menuAction.getId()).identifier(npc.getIndex())
                                .itemId(-1).target(npc.getName()).actor(npc).option(resolvedAction)
                                .worldViewId(npc.getWorldView().getId()), Rs2UiHelper.getActorClickbox(npc));
                    }).orElse(null);
            if (dispatch == null || Thread.currentThread().isInterrupted()) return false;
            return Microbot.tryDoInvoke(dispatch.getKey(), dispatch.getValue());

        } catch (Exception ex) {
            log.error("Error interacting with NPC '{}' for action '{}': ", npcName, action, ex);
            return false;
        }
    }

    private boolean isCurrent() {
        assert Microbot.getClient().isClientThread() : "Client-thread-only helper";
        WorldView view = npc == null ? null : npc.getWorldView();
        return view != null && Microbot.getClient().getGameState() == GameState.LOGGED_IN
                && Microbot.getClient().getWorldView(view.getId()) == view
                && view.npcs().byIndex(npc.getIndex()) == npc;
    }

    private MenuAction getMenuAction(int index) {
        if (!Microbot.getClient().isClientThread()) return Microbot.getClientThread().invoke(() -> getMenuAction(index));
        if (Microbot.getClient().isWidgetSelected()) {
            return MenuAction.WIDGET_TARGET_ON_NPC;
        }

        switch (index) {
            case 0:
                return MenuAction.NPC_FIRST_OPTION;
            case 1:
                return MenuAction.NPC_SECOND_OPTION;
            case 2:
                return MenuAction.NPC_THIRD_OPTION;
            case 3:
                return MenuAction.NPC_FOURTH_OPTION;
            case 4:
                return MenuAction.NPC_FIFTH_OPTION;
            default:
                return null;
        }
    }
}
