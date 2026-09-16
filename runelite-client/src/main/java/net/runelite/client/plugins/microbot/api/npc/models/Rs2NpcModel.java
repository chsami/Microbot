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
        return npc.getId();
    }

    public int getIndex()
    {
        return npc.getIndex();
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
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            return this.getLocalLocation().distanceTo(
                    Microbot.getClient().getLocalPlayer().getLocalLocation()) <= maxDistance;
        }).orElse(false);
    }

    /**
     * Gets the distance from this NPC to the player.
     * Uses client thread for safe access to player location.
     *
     * @return Distance in tiles
     */
    public int getDistanceFromPlayer() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            return this.getLocalLocation().distanceTo(
                    Microbot.getClient().getLocalPlayer().getLocalLocation());
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
        return getWorldLocation().distanceTo(anchor) <= maxDistance;
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
        int ratio = this.getHealthRatio();
        int scale = this.getHealthScale();

        if (scale == 0) return -1;
        return (double) ratio / (double) scale * 100.0;
    }

    public static Predicate<Rs2NpcModel> matches(boolean exact, String... names) {
        return npc -> {
            String npcName = npc.getName();
            if (npcName == null) return false;
            if (exact) npcName = npcName.toLowerCase();
            final String name = npcName;
            return exact ? Arrays.stream(names).anyMatch(name::equalsIgnoreCase) :
                    Arrays.stream(names).anyMatch(s -> name.contains(s.toLowerCase()));
        };
    }

    /**
     * Gets the overhead prayer icon of the NPC, if any.
     * @return
     */
    public HeadIcon getHeadIcon() {
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

            return HeadIcon.values()[overheadSpriteId];
        }

        Microbot.log("Found overheadSpriteIds: " + Arrays.toString(npc.getOverheadSpriteIds()) + " but failed to find valid overhead prayer.");

        return null;
    }

    public boolean hasLineOfSight() {
        if (npc == null) return false;

        final WorldPoint npcLoc = getWorldLocation();
        if (npcLoc == null) return false;

        final WorldPoint myLoc = new Rs2PlayerModel().getWorldLocation();
        if (myLoc == null) return false;

        if (npcLoc.equals(myLoc)) return true;

        final WorldView wv = Microbot.getClient().getTopLevelWorldView();
        return wv != null && npcLoc.toWorldArea().hasLineOfSightTo(wv, myLoc);
    }

    @Override
    public boolean click() {
        return click("");
    }

    @Override
    public boolean click(String action) {
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

            final NPCComposition npcComposition = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getNpcDefinition(getId())).orElse(null);
            if (npcComposition == null) {
                log.error("Error interacting with NPC '{}' for action '{}': NPCComposition is null", npcName, action);
                return false;
            }

            final String[] actions = npcComposition.getActions();
            if (actions == null) {
                log.error("Error interacting with NPC '{}' for action '{}': Actions are null", npcName, action);
                return false;
            }

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
            if (!Rs2Camera.isTileOnScreen(localPoint)) {
                Rs2Camera.turnTo(npc);
            }

            Microbot.doInvoke(new NewMenuEntry()
                            .param0(0)
                            .param1(0)
                            .opcode(menuAction.getId())
                            .identifier(getIndex())
                            .itemId(-1)
                            .target(npcName)
                            .actor(npc)
                            .option(action)
                    ,
                    Rs2UiHelper.getActorClickbox(npc));
            return true;

        } catch (Exception ex) {
            log.error("Error interacting with NPC '{}' for action '{}': ", npcName, action, ex);
            return false;
        }
    }

    private MenuAction getMenuAction(int index) {
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
