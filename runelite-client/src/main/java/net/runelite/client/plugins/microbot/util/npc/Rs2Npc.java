package net.runelite.client.plugins.microbot.util.npc;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.npcoverlay.HighlightedNpc;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static net.runelite.api.Perspective.LOCAL_TILE_SIZE;

public class Rs2Npc {
    /**
     * Retrieves an NPC by its index, returning an {@link Rs2NpcModel}.
     *
     * <p>This method searches for an NPC with the given index and returns it as an {@link Rs2NpcModel}.
     * If no NPC is found, {@code null} is returned.</p>
     *
     * @param index The index of the NPC.
     * @return The {@link Rs2NpcModel} with the specified index, or {@code null} if not found.
     */
    public static Rs2NpcModel getNpcByIndex(int index) {
        return getNpcs(npc -> npc.getIndex() == index).
                findFirst()
                .orElse(null);
    }

    /**
     * Validates if the given NPC is interactable and moves the player toward it.
     *
     * <p>If the NPC is not null, the method will:</p>
     * <ul>
     *   <li>Attempt to walk to the NPC's location.</li>
     *   <li>Adjust the camera to focus on the NPC.</li>
     *   <li>Return the NPC if it remains valid.</li>
     * </ul>
     *
     * @param npc The {@link Rs2NpcModel} to validate.
     * @return The validated {@link Rs2NpcModel}, or {@code null} if the NPC is invalid.
     */
    public static Rs2NpcModel validateInteractable(Rs2NpcModel npc) {
        if (npc != null) {
            Rs2Walker.walkTo(npc.getWorldLocation());
            Rs2Camera.turnTo(npc);
            return npc;
        }
        return null;
    }

    /**
     * Validates if the given NPC is interactable.
     *
     * <p><b>Deprecated:</b> Since version 1.7.2, use {@link #validateInteractable(Rs2NpcModel)} instead.</p>
     *
     * <p>If the NPC is not null, the method will:</p>
     * <ul>
     *   <li>Attempt to walk to the NPC's location.</li>
     *   <li>Adjust the camera to focus on the NPC.</li>
     *   <li>Return the NPC if it remains valid.</li>
     * </ul>
     *
     * @param npc The {@link NPC} to validate.
     * @return The validated {@link NPC}, or {@code null} if the NPC is invalid.
     * @deprecated Since 1.7.2 - Use {@link #validateInteractable(Rs2NpcModel)} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static NPC validateInteractable(NPC npc) {
        NPC vaildNPC = validateInteractable(new Rs2NpcModel(npc)).getRuneliteNpc();
        return vaildNPC;
    }

    /**
     * Checks if the NPC is currently moving based on its pose animation.
     * An NPC is considered moving if its pose animation is different from its idle pose animation.
     *
     * @param npc The NPC to check.
     * @return {@code true} if the NPC is moving, {@code false} if it is idle.
     */
    public static boolean isMoving(NPC npc) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                npc.getPoseAnimation() != npc.getIdlePoseAnimation()
        ).orElse(false);
    }

    /**
     * Retrieves a list of NPCs currently interacting with the local player.
     *
     * @return A sorted list of {@link NPC} objects interacting with the local player.
     */
    public static Stream<Rs2NpcModel> getNpcsForPlayer() {
        List<Rs2NpcModel> npcs = getNpcs(x -> x.getInteracting() == Microbot.getClient().getLocalPlayer())
                .sorted(Comparator.comparingInt(value ->
                        value.getLocalLocation().distanceTo(
                                Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList());

        return npcs.stream();
    }

    /**
     * Retrieves a filtered list of NPCs currently interacting with the local player.
     *
     * <p>This method filters NPCs based on a given condition and returns the results
     * as a sorted list of {@link Rs2NpcModel} objects.</p>
     *
     * @param predicate A {@link Predicate} to filter the NPCs.
     * @return A sorted list of {@link Rs2NpcModel} objects matching the given criteria.
     */
    public static Stream<Rs2NpcModel> getNpcsForPlayer(Predicate<Rs2NpcModel> predicate) {
        List<Rs2NpcModel> npcs = getNpcs(x -> x.getInteracting() == Microbot.getClient().getLocalPlayer())
                .filter(predicate)
                .sorted(Comparator.comparingInt(value ->
                        value.getLocalLocation().distanceTo(
                                Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList());

        return npcs.stream();
    }

    /**
     * Retrieves a list of NPCs with a specified name that are currently interacting with the local player.
     *
     * <p>This method filters NPCs based on their name and interaction status.</p>
     *
     * <p>NPCs are considered valid if:</p>
     * <ul>
     *   <li>Their name matches the given name exactly (if {@code exact} is {@code true}).</li>
     *   <li>Their name contains the given name (if {@code exact} is {@code false}).</li>
     *   <li>They are actively interacting with the local player.</li>
     * </ul>
     *
     * @param name  The name of the NPC to search for.
     * @param exact {@code true} to match the name exactly, {@code false} to allow partial matches.
     * @return A sorted list of {@link Rs2NpcModel} objects that match the criteria and are interacting with the local player.
     */
    public static List<Rs2NpcModel> getNpcsForPlayer(String name, boolean exact) {
        if (name == null || name.isEmpty()) return Collections.emptyList();
        return getNpcsForPlayer(x -> {
            String npcName = x.getName();
            if (npcName == null || npcName.isEmpty()) return false;
            return (exact ? npcName.equalsIgnoreCase(name) : npcName.toLowerCase().contains(name.toLowerCase())) && x.getInteracting() == Microbot.getClient().getLocalPlayer();
        }).sorted(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
          .collect(Collectors.toList());
    }

    /**
     * Retrieves a list of NPCs with a specified name that are currently interacting with the local player.
     *
     * <p>This method is a shorthand for {@link #getNpcsForPlayer(String, boolean)} with partial name matching.</p>
     *
     * @param name The name of the NPC to search for.
     * @return A sorted list of {@link Rs2NpcModel} objects that match the given name and are interacting with the local player.
     */
    public static List<Rs2NpcModel> getNpcsForPlayer(String name) {
        return getNpcsForPlayer(name, false);
    }

    /**
     * Retrieves the health percentage of a given {@link Actor}.
     *
     * <p>The health percentage is calculated using the formula:</p>
     * <pre>
     *     (healthRatio / healthScale) * 100
     * </pre>
     *
     * <p><b>Note:</b> If the actor's health ratio or scale is invalid (i.e., missing or zero),
     * this method may return unexpected values.</p>
     *
     * @param npc The {@link Actor} whose health percentage is to be retrieved.
     * @return The health percentage of the actor as a {@code double}.
     */
    public static double getHealth(Actor npc) {
        int ratio = npc.getHealthRatio();
        int scale = npc.getHealthScale();

        return (double) ratio / (double) scale * 100;
    }

    /**
     * Retrieves a stream of NPCs filtered by a given condition.
     *
     * <p>This method filters NPCs based on the specified predicate, allowing for flexible
     * selection of NPCs based on various attributes such as name, interaction status, health, etc.</p>
     *
     * @param predicate A {@link Predicate} that defines the filtering condition for NPCs.
     * @return A sorted {@link Stream} of {@link Rs2NpcModel} objects that match the given predicate.
     */
    public static Stream<Rs2NpcModel> getNpcs(Predicate<Rs2NpcModel> predicate) {
        List<Rs2NpcModel> npcList = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getTopLevelWorldView().npcs().stream()
                .filter(Objects::nonNull)
                .map(Rs2NpcModel::new)
                .filter(predicate)
                .filter(x -> x.getName() != null)
                .sorted(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .collect(Collectors.toList()))
                .orElse(new ArrayList<>());

        return npcList.stream();
    }

    /**
     * Retrieves a stream of all NPCs in the game world.
     *
     * <p>This method is a shorthand for {@link #getNpcs(Predicate)} that retrieves all
     * NPCs without applying any filtering conditions.</p>
     *
     * @return A sorted {@link Stream} of all {@link Rs2NpcModel} objects in the game world.
     */
    public static Stream<Rs2NpcModel> getNpcs() {
        return getNpcs(npc -> true);
    }

    /**
     * Retrieves a stream of NPCs filtered by name.
     *
     * <p>This method searches for NPCs with a specified name and filters them based on
     * whether the match should be exact or allow partial matches.</p>
     *
     * <p>Filtering behavior:</p>
     * <ul>
     *   <li>If {@code exact} is {@code true}, the NPC name must match exactly (case insensitive).</li>
     *   <li>If {@code exact} is {@code false}, the NPC name must contain the given name (case insensitive).</li>
     * </ul>
     *
     * @param name  The name of the NPC to search for.
     * @param exact {@code true} to match the name exactly, {@code false} to allow partial matches.
     * @return A {@link Stream} of {@link Rs2NpcModel} objects that match the given name criteria.
     */
    public static Stream<Rs2NpcModel> getNpcs(String name, boolean exact) {
        if (name == null || name.isEmpty()) return Stream.empty();
        return getNpcs(npc -> {
            String npcName = npc.getName();
            if (npcName == null || npcName.isEmpty()) return false;
            return exact ? npcName.equalsIgnoreCase(name) : npcName.toLowerCase().contains(name.toLowerCase());
        });
    }

    /**
     * Retrieves a stream of NPCs filtered by partial name match.
     *
     * <p>This method is a shorthand for {@link #getNpcs(String, boolean)} with partial matching enabled.</p>
     *
     * @param name The name of the NPC to search for.
     * @return A {@link Stream} of {@link Rs2NpcModel} objects whose names contain the given string.
     */
    public static Stream<Rs2NpcModel> getNpcs(String name) {
        return getNpcs(name, false);
    }

    /**
     * Retrieves a stream of NPCs filtered by their ID.
     *
     * @param id The unique identifier of the NPC to search for.
     * @return A {@link Stream} of {@link Rs2NpcModel} objects that match the given NPC ID.
     */
    public static Stream<Rs2NpcModel> getNpcs(int id) {
        return getNpcs().filter(x -> x.getId() == id);
    }

    /**
     * Retrieves a stream of attackable NPCs.
     *
     * <p>This method filters NPCs based on the following conditions:</p>
     * <ul>
     *   <li>The NPC has a combat level greater than 0.</li>
     *   <li>The NPC is not dead.</li>
     *   <li>The NPC is not currently interacting with another entity unless the player is in a multi-combat area.</li>
     * </ul>
     *
     * <p>The resulting stream of NPCs is sorted by proximity to the player, with closer NPCs appearing first.</p>
     *
     * @return A sorted {@link Stream} of {@link Rs2NpcModel} objects that the player can attack.
     */
    public static Stream<Rs2NpcModel> getAttackableNpcs() {
        return getNpcs(npc -> npc.getCombatLevel() > 0 && !npc.isDead())
                .filter(npc -> Rs2Player.isInMulti() || !npc.isInteracting())
                .sorted(Comparator.comparingInt(value ->
                        value.getLocalLocation().distanceTo(
                                Microbot.getClient().getLocalPlayer().getLocalLocation())));
    }

    /**
     * Retrieves a stream of attackable NPCs based on specified criteria.
     *
     * <p>This method filters NPCs based on the following conditions:</p>
     * <ul>
     *   <li>The NPC has a combat level greater than 0.</li>
     *   <li>The NPC is not dead.</li>
     *   <li>If {@code reachable} is {@code true}, the NPC must be reachable from the player's current location.</li>
     *   <li>The NPC is either not interacting with any entity or is interacting with the local player.</li>
     * </ul>
     *
     * <p>The resulting stream of NPCs is sorted by proximity to the player, with closer NPCs appearing first.</p>
     *
     * @param reachable If {@code true}, only include NPCs that are reachable from the player's current location.
     *                  If {@code false}, include all NPCs matching the other criteria regardless of reachability.
     * @return A sorted {@link Stream} of {@link Rs2NpcModel} objects that the player can attack.
     */
    public static Stream<Rs2NpcModel> getAttackableNpcs(boolean reachable) {
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());

        return getNpcs(npc -> npc.getCombatLevel() > 0
                && !npc.isDead()
                && (!reachable || playerLocation.distanceToPath(npc.getWorldLocation()) < Integer.MAX_VALUE)
                && (!npc.isInteracting() || npc.getInteracting() == Microbot.getClient().getLocalPlayer()))
                .sorted(Comparator.comparingInt(value ->
                        value.getLocalLocation().distanceTo(
                                Microbot.getClient().getLocalPlayer().getLocalLocation())));
    }

    /**
     * Retrieves a stream of attackable NPCs filtered by name.
     *
     * <p>This method first filters NPCs based on attackable criteria, then applies name filtering:</p>
     * <ul>
     *   <li>The NPC must meet the conditions defined in {@link #getAttackableNpcs()}.</li>
     *   <li>If {@code exact} is {@code true}, the NPC name must match exactly (case insensitive).</li>
     *   <li>If {@code exact} is {@code false}, the NPC name must contain the given name (case insensitive).</li>
     * </ul>
     *
     * @param name  The name of the NPC to search for.
     * @param exact {@code true} to match the name exactly, {@code false} to allow partial matches.
     * @return A sorted {@link Stream} of {@link Rs2NpcModel} objects that match the given name and are attackable.
     */
    public static Stream<Rs2NpcModel> getAttackableNpcs(String name, boolean exact) {
        if (name == null || name.isEmpty()) return Stream.empty();
        return getAttackableNpcs().filter(x -> {
            String npcName = x.getName();
            if (npcName == null || npcName.isEmpty()) return false;
            return exact ? npcName.equalsIgnoreCase(name) : npcName.toLowerCase().contains(name.toLowerCase());
        });
    }

    public static Stream<Rs2NpcModel> getAttackableNpcs(String name) {
        return getAttackableNpcs(name, false);
    }

    /**
     * Retrieves an array of active Pest Control portals.
     *
     * <p>This method searches for NPCs with the name "portal" (case insensitive, allowing partial matches)
     * and filters them based on the following conditions:</p>
     * <ul>
     *   <li>The portal is not dead.</li>
     *   <li>The portal has a health ratio greater than 0.</li>
     * </ul>
     *
     * <p>The resulting portals are sorted by proximity to the player, with closer portals appearing first.</p>
     *
     * @return An array of {@link Rs2NpcModel} representing the active Pest Control portals.
     */
    public static Rs2NpcModel[] getPestControlPortals() {
        return getNpcs("portal", false)
                .filter(npc -> !npc.isDead() && npc.getHealthRatio() > 0)
                .sorted(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())))
                .toArray(Rs2NpcModel[]::new);
    }

    /**
     * Retrieves the first NPC that matches the given name exactly.
     *
     * <p>This method is a shorthand for {@link #getNpc(String, boolean)} with exact matching enabled.</p>
     *
     * @param name The name of the NPC to search for.
     * @return The first {@link Rs2NpcModel} found with the exact matching name, or {@code null} if no match is found.
     */
    public static Rs2NpcModel getNpc(String name) {
        return getNpc(name, false);
    }

    /**
     * Retrieves the first NPC that matches the given name.
     *
     * <p>This method searches for NPCs based on the provided name and applies one of the following matching criteria:</p>
     * <ul>
     *   <li>If {@code exact} is {@code true}, the NPC name must match exactly (case insensitive).</li>
     *   <li>If {@code exact} is {@code false}, the NPC name must contain the given name (case insensitive).</li>
     * </ul>
     *
     * @param name  The name of the NPC to search for.
     * @param exact {@code true} to match the name exactly, {@code false} to allow partial matches.
     * @return The first {@link Rs2NpcModel} that matches the given criteria, or {@code null} if no match is found.
     */
    public static Rs2NpcModel getNpc(String name, boolean exact) {
        return getNpcs(name, exact)
                .findFirst()
                .orElse(null);
    }

    /**
     * Retrieves the first NPC that matches the given in-game ID.
     *
     * <p>This method searches for an NPC using its unique game ID.</p>
     *
     * @param id The unique identifier of the NPC to search for.
     * @return The first {@link Rs2NpcModel} that matches the given ID, or {@code null} if no match is found.
     */
    public static Rs2NpcModel getNpc(int id) {
        return getNpcs(x -> x.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /**
     * Retrieves the closest NPC with a given ID while excluding specific NPC indexes.
     *
     * <p>This method filters NPCs based on the following criteria:</p>
     * <ul>
     *   <li>The NPC's ID matches the specified {@code id}.</li>
     *   <li>The NPC's index is <b>not</b> present in {@code excludedIndexes}.</li>
     * </ul>
     *
     * <p>The closest matching NPC to the local player is returned as an {@link Optional}.</p>
     *
     * @param id              The unique identifier of the NPC to search for.
     * @param excludedIndexes A list of NPC indexes to exclude from the results.
     * @return An {@link Optional} containing the closest matching {@link Rs2NpcModel}, or empty if no match is found.
     */
    public static Optional<Rs2NpcModel> getNpc(int id, List<Integer> excludedIndexes) {
        return getNpcs(x -> x.getId() == id && !excludedIndexes.contains(x.getIndex()))
                .min(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(Microbot.getClient().getLocalPlayer().getLocalLocation())));
    }

    /**
     * Retrieves the first NPC that represents a random event and is currently interacting with the player.
     *
     * <p>This method identifies NPCs associated with random events by checking if they have a "Dismiss" option
     * in their available actions.</p>
     *
     * <p>The method filters NPCs based on the following conditions:</p>
     * <ul>
     *   <li>The NPC has a valid composition and action list.</li>
     *   <li>The NPC's actions include "Dismiss".</li>
     *   <li>The NPC is currently interacting with the local player.</li>
     * </ul>
     *
     * @return The first {@link Rs2NpcModel} representing a random event NPC, or {@code null} if none are found.
     */
    public static Rs2NpcModel getRandomEventNPC() {
        return getNpcs(npc -> {
            NPCComposition npcComposition = npc.getComposition();
            if (npcComposition == null) return false;
            List<String> npcActions = Arrays.asList(npcComposition.getActions());
            if (npcActions.isEmpty()) return false;
            return npcActions.contains("Dismiss") && npc.getInteracting() == Microbot.getClient().getLocalPlayer();
        }).findFirst().orElse(null);
    }

    /**
     * Retrieves the first banker NPC available for banking.
     *
     * <p>This method searches for NPCs that have the "Bank" option in their available actions.
     * It checks both the NPC's base composition and any transformed composition.</p>
     *
     * <p>The method filters NPCs based on the following conditions:</p>
     * <ul>
     *   <li>The NPC has a valid base composition or transformed composition.</li>
     *   <li>Either the base composition or transformed composition has an action list containing "Bank".</li>
     * </ul>
     *
     * @return The first {@link Rs2NpcModel} that functions as a banker, or {@code null} if no banker NPCs are found.
     */
    public static Rs2NpcModel getBankerNPC() {
        return getNpcs(npc -> {
            NPCComposition baseComposition = npc.getComposition();
            NPCComposition transformedComposition = npc.getTransformedComposition();

            List<String> baseActions = baseComposition != null ? Arrays.asList(baseComposition.getActions()) : Collections.emptyList();
            List<String> transformedActions = transformedComposition != null ? Arrays.asList(transformedComposition.getActions()) : Collections.emptyList();

            return baseActions.contains("Bank") || transformedActions.contains("Bank");
        }).findFirst().orElse(null);
    }

    /**
     * Checks if an NPC with a given ID has a specified action available.
     *
     * <p>This method retrieves the NPC definition based on the given ID and checks if
     * the specified action is present in the NPC's available actions.</p>
     *
     * @param id     The unique identifier of the NPC.
     * @param action The action to check for (e.g., "Talk-to", "Bank", "Trade").
     * @return {@code true} if the NPC has the specified action, {@code false} otherwise.
     */
    public static boolean hasAction(int id, String action) {
        NPCComposition npcComposition = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getNpcDefinition(id)).orElse(null);

        if (npcComposition == null) return false;

        return Arrays.stream(npcComposition.getActions())
                .anyMatch(x -> x != null && x.equalsIgnoreCase(action));
    }

    /**
     * Interacts with an NPC using a specified action.
     *
     * <p>This method wraps an NPC in an {@link Rs2NpcModel} before delegating to the recommended overload.</p>
     *
     * @param npc    The NPC to interact with.
     * @param action The action to perform on the NPC (e.g., "Talk-to", "Attack", "Trade").
     * @return {@code true} if the interaction was successful, {@code false} otherwise.
     * @deprecated Since 1.7.2 - Use {@link #interact(Rs2NpcModel, String)} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean interact(NPC npc, String action) {
        return interact(new Rs2NpcModel(npc), action);
    }

    /**
     * Interacts with an NPC using a specified action.
     *
     * <p>This method performs interaction logic, including:</p>
     * <ul>
     *   <li>Checking if the NPC is reachable.</li>
     *   <li>Handling cases where the bot repeatedly fails to reach the NPC.</li>
     *   <li>Determining the correct {@link MenuAction} for the specified interaction.</li>
     *   <li>Executing the interaction via the RuneLite menu system.</li>
     * </ul>
     *
     * <p>If the NPC cannot be reached after multiple attempts, the bot will pause all scripts and notify the user.</p>
     *
     * @param npc    The {@link Rs2NpcModel} to interact with.
     * @param action The action to perform on the NPC (e.g., "Talk-to", "Attack", "Trade").
     * @return {@code true} if the interaction was successfully executed, {@code false} if the NPC was unreachable.
     */

    public static boolean interact(Rs2NpcModel npc, String action) {
        if (npc == null) return false;

        Microbot.status = action + " " + npc.getName();
        try {
            if (Microbot.isCantReachTargetDetectionEnabled && Microbot.cantReachTarget) {
                if (!hasLineOfSight(npc)) {
                    if (Microbot.cantReachTargetRetries >= Rs2Random.between(3, 5)) {
                        Microbot.pauseAllScripts = true;
                        Microbot.showMessage("Your bot tried to interact with an NPC for "
                                + Microbot.cantReachTargetRetries + " times but failed. Please take a look at what is happening.");
                        return false;
                    }
                    Rs2Walker.walkTo(Rs2Tile.getNearestWalkableTileWithLineOfSight(npc.getWorldLocation()), 0);
                    Microbot.pauseAllScripts = false;
                    Microbot.cantReachTargetRetries++;
                    return false;
                } else {
                    Microbot.pauseAllScripts = false;
                    Microbot.cantReachTarget = false;
                    Microbot.cantReachTargetRetries = 0;
                }
            }

            NPCComposition npcComposition = Microbot.getClientThread().runOnClientThreadOptional(
                    () -> Microbot.getClient().getNpcDefinition(npc.getId())).orElse(null);

            if (npcComposition == null || npcComposition.getActions() == null) {
                Microbot.log("Error: Could not get NPC composition or actions for NPC: " + npc.getName());
                return false;
            }

            int index = -1;
            String[] actions = npcComposition.getActions();

            if (action == null || action.isEmpty()) {
                OptionalInt optionalIndex = IntStream.range(0, actions.length)
                        .filter(i -> actions[i] != null && !actions[i].isEmpty())
                        .findFirst();

                if (optionalIndex.isPresent()) {
                    index = optionalIndex.getAsInt();
                    action = actions[index];
                }
            }
            else {
                String finalAction = action;
                OptionalInt optionalIndex = IntStream.range(0, actions.length)
                        .filter(i -> actions[i] != null && actions[i].equalsIgnoreCase(finalAction))
                        .findFirst();

                if (optionalIndex.isPresent()) {
                    index = optionalIndex.getAsInt();
                }
            }

            MenuAction menuAction = getMenuAction(index);

            if (menuAction == null) {
                if (index == -1 && !Microbot.getClient().isWidgetSelected()) {
                    Microbot.log("Error: Action '" + action + "' not found for NPC: " + npc.getName());
                } else {
                    Microbot.log("Error: Could not get menu action for action '" + action + "' on NPC: " + npc.getName());
                }
                return false;
            }

            if (!Rs2Camera.isTileOnScreen(npc.getLocalLocation())) {
                Rs2Camera.turnTo(npc);
            }

            Microbot.doInvoke(new NewMenuEntry(0, 0, menuAction.getId(), npc.getIndex(), -1, npc.getName(), npc),
                    Rs2UiHelper.getActorClickbox(npc));
            return true;

        } catch (Exception ex) {
            Microbot.log("Error interacting with NPC '" + npc.getName() + "' for action '" + action + "': " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }
    /**
     * Retrieves the corresponding {@link MenuAction} for a given interaction index.
     *
     * <p>This method determines which {@link MenuAction} should be used based on the provided
     * menu index. It follows this order:</p>
     * <ul>
     *   <li>If a widget is currently selected, {@link MenuAction#WIDGET_TARGET_ON_NPC} is used.</li>
     *   <li>If {@code index} is 0, the first NPC menu option is used.</li>
     *   <li>If {@code index} is 1, the second NPC menu option is used.</li>
     *   <li>If {@code index} is 2, the third NPC menu option is used.</li>
     *   <li>If {@code index} is 3, the fourth NPC menu option is used.</li>
     *   <li>If {@code index} is 4, the fifth NPC menu option is used.</li>
     * </ul>
     *
     * @param index The menu index corresponding to the NPC interaction option (0-4).
     * @return The corresponding {@link MenuAction}, or {@code null} if the index is invalid.
     */
    @Nullable
    private static MenuAction getMenuAction(int index) {
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

    /**
     * Interacts with an NPC using the default interaction option.
     *
     * <p>This method wraps an {@link NPC} in an {@link Rs2NpcModel} before delegating to the recommended overload.</p>
     *
     * @param npc The NPC to interact with.
     * @return {@code true} if the interaction was successful, {@code false} otherwise.
     * @deprecated Since 1.7.2 - Use {@link #interact(Rs2NpcModel)} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean interact(NPC npc) {
        return interact(new Rs2NpcModel(npc), "");
    }

    /**
     * Interacts with an NPC using the default interaction option.
     *
     * <p>This method is a shorthand for {@link #interact(Rs2NpcModel, String)} with an empty action string,
     * which typically triggers the NPC's default interaction.</p>
     *
     * @param npc The {@link Rs2NpcModel} to interact with.
     * @return {@code true} if the interaction was successfully executed, {@code false} otherwise.
     */
    public static boolean interact(Rs2NpcModel npc) {
        return interact(npc, "");
    }

    /**
     * Interacts with an NPC using its unique in-game ID and the default interaction option.
     *
     * <p>This method retrieves the NPC using its ID and then interacts with it.</p>
     *
     * @param id The unique identifier of the NPC.
     * @return {@code true} if the interaction was successful, {@code false} if the NPC was not found.
     */
    public static boolean interact(int id) {
        return interact(id, "");
    }

    /**
     * Interacts with an NPC using its unique in-game ID and a specified action.
     *
     * <p>This method searches for an NPC by its ID and then interacts with it using the provided action.</p>
     *
     * @param npcId  The unique identifier of the NPC.
     * @param action The action to perform on the NPC (e.g., "Talk-to", "Attack", "Trade").
     * @return {@code true} if the interaction was successfully executed, {@code false} if the NPC was not found.
     */
    public static boolean interact(int npcId, String action) {
        Rs2NpcModel npc = getNpc(npcId);
        return interact(npc, action);
    }

    /**
     * Attacks the specified NPC if conditions are met.
     *
     * <p>The attack will only be executed if the following conditions are met:</p>
     * <ul>
     *   <li>The NPC is not {@code null}.</li>
     *   <li>The NPC is within line of sight.</li>
     *   <li>The player is not already in combat.</li>
     *   <li>The NPC is not currently interacting with another player (unless in a multi-combat zone).</li>
     * </ul>
     *
     * <p>This method wraps an {@link NPC} in an {@link Rs2NpcModel} before delegating to the recommended overload.</p>
     *
     * @param npc The {@link NPC} to attack.
     * @return {@code true} if the attack action was successfully executed, {@code false} otherwise.
     * @deprecated Since 1.7.2 - Use {@link #attack(Rs2NpcModel)} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean attack(NPC npc) {
        if (npc == null) return false;
        if (!hasLineOfSight(new Rs2NpcModel(npc))) return false;
        if (Rs2Combat.inCombat()) return false;
        if (npc.isInteracting() && npc.getInteracting() != Microbot.getClient().getLocalPlayer() && !Rs2Player.isInMulti())
            return false;

        return interact(new Rs2NpcModel(npc), "attack");
    }


    /**
     * Attacks the specified NPC if conditions are met.
     *
     * <p>The attack will only be executed if the following conditions are met:</p>
     * <ul>
     *   <li>The NPC is not {@code null}.</li>
     *   <li>The NPC is within line of sight.</li>
     *   <li>The player is not already in combat.</li>
     *   <li>The NPC is not currently interacting with another player (unless in a multi-combat zone).</li>
     * </ul>
     *
     * @param npc The {@link Rs2NpcModel} to attack.
     * @return {@code true} if the attack action was successfully executed, {@code false} otherwise.
     */
    public static boolean attack(Rs2NpcModel npc) {
        if (npc == null) return false;
        if (!hasLineOfSight(npc)) return false;
        if (Rs2Combat.inCombat()) return false;
        if (npc.isInteracting() && npc.getInteracting() != Microbot.getClient().getLocalPlayer() && !Rs2Player.isInMulti())
            return false;

        return interact(npc, "attack");
    }

    /**
     * Attacks an NPC using its unique in-game ID.
     *
     * <p>This method searches for an NPC by its ID and attempts to attack it if conditions are met.</p>
     *
     * @param npcId The unique identifier of the NPC.
     * @return {@code true} if the attack action was successfully executed, {@code false} if the NPC was not found or could not be attacked.
     */
    public static boolean attack(int npcId) {
        Rs2NpcModel npc = getNpc(npcId);
        return attack(npc);
    }

    /**
     * Attacks an NPC using its name.
     *
     * <p>This method searches for an NPC by its name and attempts to attack it if conditions are met.</p>
     *
     * <p>Equivalent to calling {@link #attack(List)} with a single name.</p>
     *
     * @param npcName The name of the NPC to attack.
     * @return {@code true} if the attack action was successfully executed, {@code false} if no matching NPC was found or could not be attacked.
     */
    public static boolean attack(String npcName) {
        return attack(Collections.singletonList(npcName));
    }

    /**
     * Attacks the first NPC found from a list of possible names.
     *
     * <p>This method iterates through the provided list of NPC names and attacks the first valid NPC it finds,
     * ensuring the following conditions are met:</p>
     * <ul>
     *   <li>The NPC is within line of sight.</li>
     *   <li>The player is not already in combat.</li>
     *   <li>The NPC is not interacting with another player (unless in a multi-combat zone).</li>
     * </ul>
     *
     * @param npcNames A list of NPC names to search for.
     * @return {@code true} if an NPC was found and the attack action was successfully executed, {@code false} otherwise.
     */
    public static boolean attack(List<String> npcNames) {
        for (String npcName : npcNames) {
            Rs2NpcModel npc = getNpc(npcName);
            if (npc == null) continue;
            if (!hasLineOfSight(npc)) continue;
            if (Rs2Combat.inCombat()) continue;
            if (npc.isInteracting() && npc.getInteracting() != Microbot.getClient().getLocalPlayer() && !Rs2Player.isInMulti())
                continue;
            if (npc.isDead()) continue;

            return interact(npc, "attack");
        }
        return false;
    }

    /**
     * Interacts with an NPC by name using the specified action.
     *
     * <p>This method searches for an NPC by its name and attempts to interact with it.</p>
     *
     * @param npcName The name of the NPC to interact with.
     * @param action  The action to perform on the NPC (e.g., "Talk-to", "Attack", "Pickpocket").
     * @return {@code true} if the interaction was successfully executed, {@code false} if the NPC was not found.
     */
    public static boolean interact(String npcName, String action) {
        Rs2NpcModel npc = getNpc(npcName);
        return interact(npc, action);
    }

    /**
     * Attempts to pickpocket an NPC by name.
     *
     * <p>This method searches for an NPC by its name and attempts to pickpocket it.</p>
     *
     * @param npcName The name of the NPC to pickpocket.
     * @return {@code true} if the pickpocket action was successfully executed, {@code false} if the NPC was not found.
     */
    public static boolean pickpocket(String npcName) {
        Rs2NpcModel npc = getNpc(npcName);
        return pickpocket(npc);
    }

    /**
     * Attempts to pickpocket the first NPC found in a highlighted NPC list.
     *
     * <p>This method iterates through a map of highlighted NPCs and attempts to pickpocket the first valid NPC.</p>
     *
     * @param highlightedNpcs A map of NPCs and their corresponding {@link HighlightedNpc} data.
     * @return {@code true} if the pickpocket action was successfully executed, {@code false} if no NPC was found.
     */
    public static boolean pickpocket(Map<NPC, HighlightedNpc> highlightedNpcs) {
        for (NPC npc : highlightedNpcs.keySet()) {
            return interact(new Rs2NpcModel(npc), "pickpocket");
        }
        return false;
    }

    /**
     * Attempts to pickpocket the specified NPC.
     *
     * <p>This method wraps an {@link NPC} in an {@link Rs2NpcModel} before attempting the pickpocket action.</p>
     *
     * @param npc The NPC to pickpocket.
     * @return {@code true} if the pickpocket action was successfully executed, {@code false} otherwise.
     */
    public static boolean pickpocket(NPC npc) {
        return interact(new Rs2NpcModel(npc), "pickpocket");
    }

    /**
     * Checks if an NPC is within the player's line of sight.
     *
     * <p>This method wraps an {@link NPC} in an {@link Rs2NpcModel} before checking for line of sight.</p>
     *
     * @param npc The NPC to check.
     * @return {@code true} if the NPC is within line of sight, {@code false} otherwise.
     * @deprecated Since 1.7.2 - Use {@link #hasLineOfSight(Rs2NpcModel)} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean hasLineOfSight(NPC npc) {
        return hasLineOfSight(new Rs2NpcModel(npc));
    }

    /**
     * Checks if an NPC is within the player's line of sight.
     *
     * <p>This method determines whether the player has an unobstructed view of the NPC based on world location and collision detection.</p>
     *
     * @param npc The {@link Rs2NpcModel} to check.
     * @return {@code true} if the NPC is within line of sight, {@code false} otherwise.
     */
    public static boolean hasLineOfSight(Rs2NpcModel npc) {
        if (npc == null) return false;
        if (npc.getWorldLocation().equals(Rs2Player.getWorldLocation())) return true;

        return npc.getWorldLocation().toWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), Microbot.getClient().getLocalPlayer().getWorldLocation().toWorldArea());
    }

    /**
     * Retrieves the world location of an NPC.
     *
     * <p>This method wraps an {@link NPC} in an {@link Rs2NpcModel} before delegating to the recommended overload.</p>
     *
     * @param npc The {@link NPC} whose world location is to be retrieved.
     * @return The {@link WorldPoint} representing the NPC's world location.
     * @deprecated Since 1.7.2 - Use {@link Rs2NpcModel#getWorldLocation()} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static WorldPoint getWorldLocation(NPC npc) {
        return getWorldLocation(new Rs2NpcModel(npc));
    }

    /**
     * Retrieves the world location of an NPC.
     *
     * <p>This method previously handled world location retrieval for NPCs, including checks for instanced areas.
     * However, it is now recommended to call {@link Rs2NpcModel#getWorldLocation()} directly.</p>
     *
     * @param npc The {@link Rs2NpcModel} whose world location is to be retrieved.
     * @return The {@link WorldPoint} representing the NPC's world location.
     * @deprecated Since 1.7.2 - Use {@link Rs2NpcModel#getWorldLocation()} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static WorldPoint getWorldLocation(Rs2NpcModel npc) {
        return npc.getWorldLocation();
    }

    /**
     * Checks whether the player can walk to an NPC within a given distance.
     *
     * <p>This method wraps an {@link NPC} in an {@link Rs2NpcModel} before calling the recommended overload.</p>
     *
     * @param npc      The {@link NPC} to check.
     * @param distance The maximum number of tiles the player can walk.
     * @return {@code true} if the player can walk to the NPC, {@code false} otherwise.
     * @deprecated Since 1.7.2 - Use {@link #canWalkTo(Rs2NpcModel, int)} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean canWalkTo(NPC npc, int distance) {
        return canWalkTo(new Rs2NpcModel(npc), distance);
    }

    /**
     * Checks whether the player can walk to an NPC within a given distance.
     *
     * <p>This method determines if an NPC is reachable based on the player's current position,
     * taking into account walkable tiles and potential obstacles.</p>
     *
     * <p>The check considers:</p>
     * <ul>
     *   <li>Tiles reachable from the player's current location within the given distance.</li>
     *   <li>Whether the tile is walkable based on {@link Rs2Tile#getReachableTilesFromTile(WorldPoint, int)}.</li>
     *   <li>Handling for non-walkable local locations by checking adjacent tile proximity.</li>
     * </ul>
     *
     * @param npc      The {@link Rs2NpcModel} to check.
     * @param distance The maximum number of tiles the player can walk.
     * @return {@code true} if the player can walk to the NPC, {@code false} otherwise.
     */
    public static boolean canWalkTo(Rs2NpcModel npc, int distance) {
        if (npc == null) return false;
        var location = npc.getWorldLocation();

        var tiles = Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance);

        if (tiles.keySet().stream().anyMatch(tile -> tile.equals(location))) {
            return true;
        }

        var localLocation = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
        if (localLocation != null && !Rs2Tile.isWalkable(localLocation))
            return tiles.keySet().stream().anyMatch(x -> x.distanceTo(location) < 2);

        return false;
    }

    /**
     * Retrieves a list of NPCs that are currently attacking the specified player.
     *
     * <p>This method filters NPCs based on whether they are interacting with the given player
     * and are marked as dead. It is now recommended to use {@link #getNpcsForPlayer(Predicate)}
     * for better consistency and maintainability.</p>
     *
     * @param player The {@link Player} for whom attacking NPCs are retrieved.
     * @return A {@link List} of {@link Rs2NpcModel} instances representing NPCs attacking the player.
     * @deprecated Since 1.7.2 - Use {@link #getNpcsForPlayer(Predicate)} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static List<Rs2NpcModel> getNpcsAttackingPlayer(Player player) {
        return getNpcs(x -> x.getInteracting() != null && x.getInteracting() == player && x.isDead())
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a list of NPCs within the player's line of sight, filtered by name with an option for partial matching.
     *
     * <p>This method allows searching for NPCs by name with two matching modes:</p>
     * <ul>
     *   <li><b>Exact match:</b> The NPC's name must exactly match the provided name.</li>
     *   <li><b>Partial match:</b> The NPC's name must contain the provided name (case-insensitive).</li>
     * </ul>
     *
     * @param name  The name of the NPC to search for.
     * @param exact If {@code true}, only exact matches are returned. If {@code false}, partial matches are allowed.
     * @return A {@link List} of {@link Rs2NpcModel} objects within line of sight that match the given criteria.
     */
    public static List<Rs2NpcModel> getNpcsInLineOfSight(String name, boolean exact) {
        if (name == null || name.isEmpty()) return Collections.emptyList();

        return getNpcs(npc -> {
            String npcName = npc.getName();
            if (npcName == null || npcName.isEmpty()) return false;
            return hasLineOfSight(npc) && (exact ? npcName.equalsIgnoreCase(name) : npcName.toLowerCase().contains(name.toLowerCase()));
        }).collect(Collectors.toList());
    }

    /**
     * Retrieves a list of NPCs within the player's line of sight, filtered by name with partial matching.
     *
     * <p>This method checks all NPCs in the game world and filters them based on:</p>
     * <ul>
     *   <li>The NPC's name must contain the provided name (case-insensitive).</li>
     *   <li>The NPC must be within the player's line of sight.</li>
     * </ul>
     *
     * <p>This method is a shorthand for {@link #getNpcsInLineOfSight(String, boolean)} with partial matching enabled.</p>
     *
     * @param name The name (or partial name) of the NPC to search for.
     * @return A {@link List} of {@link Rs2NpcModel} objects within line of sight that contain the given name.
     */
    public static List<Rs2NpcModel> getNpcsInLineOfSight(String name) {
        return getNpcsInLineOfSight(name, false);
    }

    /**
     * Retrieves the first NPC within the player's line of sight that matches the given name, with an option for partial matching.
     *
     * <p>This method allows searching for NPCs by name with two matching modes:</p>
     * <ul>
     *   <li><b>Exact match:</b> The NPC's name must exactly match the provided name.</li>
     *   <li><b>Partial match:</b> The NPC's name must contain the provided name (case-insensitive).</li>
     * </ul>
     *
     * <p>If multiple NPCs match, the first one found is returned.</p>
     *
     * @param name  The name of the NPC to search for.
     * @param exact If {@code true}, only exact matches are considered. If {@code false}, partial matches are allowed.
     * @return The first {@link Rs2NpcModel} object matching the criteria within line of sight, or {@code null} if none are found.
     */
    public static Rs2NpcModel getNpcInLineOfSight(String name, boolean exact) {
        List<Rs2NpcModel> npcsInLineOfSight = getNpcsInLineOfSight(name, exact);
        return npcsInLineOfSight.isEmpty() ? null : npcsInLineOfSight.get(0);
    }

    /**
     * Retrieves the first NPC within the player's line of sight, filtered by name with partial matching.
     *
     * <p>This method checks all NPCs in the game world and filters them based on:</p>
     * <ul>
     *   <li>The NPC's name must contain the provided name (case-insensitive).</li>
     *   <li>The NPC must be within the player's line of sight.</li>
     * </ul>
     *
     * <p>If multiple NPCs match, the first one found is returned.</p>
     *
     * <p>This method is a shorthand for {@link #getNpcsInLineOfSight(String, boolean)} with partial matching enabled.</p>
     *
     * @param name The name (or partial name) of the NPC to search for.
     * @return The first {@link Rs2NpcModel} object that matches the given name within line of sight, or {@code null} if none are found.
     */
    public static Rs2NpcModel getNpcInLineOfSight(String name) {
        List<Rs2NpcModel> npcsInLineOfSight = getNpcsInLineOfSight(name, false);
        return npcsInLineOfSight.isEmpty() ? null : npcsInLineOfSight.get(0);
    }

    /**
     * Retrieves the nearest NPC that has a specified action available.
     *
     * <p>This method searches for NPCs that have the given action in their interaction menu.
     * The NPC closest to the player's current location is returned.</p>
     *
     * <p>The method filters NPCs based on:</p>
     * <ul>
     *   <li>The NPC's composition is not null.</li>
     *   <li>The NPC's list of available actions contains the specified action.</li>
     *   <li>The NPC is the closest to the player based on path distance.</li>
     * </ul>
     *
     * @param action The action to search for (e.g., "Bank", "Talk-to", "Trade").
     * @return The nearest {@link Rs2NpcModel} that has the specified action, or {@code null} if none are found.
     */
    public static Rs2NpcModel getNearestNpcWithAction(String action) {
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());
        boolean isInstance = Microbot.getClient().getTopLevelWorldView().getScene().isInstance();
        return getNpcs()
                .filter(value -> value.getComposition() != null
                        && value.getComposition().getActions() != null
                        && Arrays.asList(value.getComposition().getActions()).contains(action))
                .min(Comparator.comparingInt(value -> playerLocation.distanceToPath(isInstance ? Rs2WorldPoint.toLocalInstance(value.getWorldLocation()) : value.getWorldLocation())))
                .orElse(null);
    }

    /**
     * Retrieves the first valid action from the given list that the NPC supports.
     *
     * @param npc             The {@link Rs2NpcModel} to check available actions on.
     * @param possibleActions A list of possible actions to match against the NPC's menu options.
     * @return The first matching action as a {@link String}, or an empty string if no match is found.
     */
    public static String getAvailableAction(Rs2NpcModel npc, List<String> possibleActions) {
        if (npc == null || possibleActions == null || possibleActions.isEmpty()) return "";

        NPCComposition composition = Microbot.getClientThread().runOnClientThreadOptional(
                        () -> Microbot.getClient().getNpcDefinition(npc.getId()))
                .orElse(null);

        if (composition == null || composition.getActions() == null) return "";

        return Arrays.stream(composition.getActions())
                .filter(Objects::nonNull)
                .filter(npcAction ->
                        possibleActions.stream()
                                .anyMatch(action -> action.equalsIgnoreCase(npcAction)))
                .findFirst()
                .orElse("");
    }

    /**
     * Retrieves the first NPC that has a specified action available.
     *
     * <p>This method searches for NPCs that have the given action in their interaction menu.
     * If an NPC has a transformed composition, it also checks for actions in the transformed state.</p>
     *
     * <p>The method filters NPCs based on:</p>
     * <ul>
     *   <li>The NPC's composition is not null.</li>
     *   <li>The NPC's list of available actions contains the specified action.</li>
     *   <li>If the NPC has a transformed composition, its actions are also checked.</li>
     * </ul>
     *
     * @param action The action to search for (e.g., "Bank", "Talk-to", "Trade").
     * @return The first {@link NPC} that has the specified action, or {@code null} if none are found.
     */
    public static Rs2NpcModel getNpcWithAction(String action) {
        return getNpcs()
                .filter(value -> (value.getComposition() != null
                        && value.getComposition().getActions() != null
                        && Arrays.asList(value.getComposition().getActions()).contains(action))
                        || (value.getComposition().transform() != null
                        && value.getComposition().transform().getActions() != null
                        && Arrays.asList(value.getComposition().transform().getActions()).contains(action)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Moves the mouse cursor over the given actor (e.g., NPC, player, or other interactable entity).
     *
     * <p>This method attempts to hover over the specified actor using a natural mouse movement,
     * if natural mouse settings are enabled. If natural mouse movement is disabled,
     * the method will log a debug message (if enabled) and return {@code false}.</p>
     *
     * <p>The method follows these steps:</p>
     * <ul>
     *   <li>Checks if natural mouse movement is enabled.</li>
     *   <li>Retrieves the actor's clickable area.</li>
     *   <li>Calculates a random hover point within the clickable area.</li>
     *   <li>Moves the mouse to the calculated point.</li>
     * </ul>
     *
     * @param actor The {@link Actor} (e.g., NPC, player) to hover over.
     * @return {@code true} if the mouse was successfully moved over the actor, {@code false} otherwise.
     */
    public static boolean hoverOverActor(Actor actor) {
        if (!Rs2AntibanSettings.naturalMouse) {
            if (Rs2AntibanSettings.devDebug)
                Microbot.log("Natural mouse is not enabled, can't hover");
            return false;
        }
        Point point = Rs2UiHelper.getClickingPoint(Rs2UiHelper.getActorClickbox(actor), true);
        if (point.getX() == 1 && point.getY() == 1) {
            return false;
        }
        Microbot.getNaturalMouse().moveTo(point.getX(), point.getY());
        return true;
    }

    // Walks to the nearest NPC location with the given name
    public static boolean walkToNearestMonster(String name, int minClustering, boolean avoidWilderness) {
        WorldPoint nearestNpcLocation = Rs2NpcManager.getClosestLocation(name,minClustering,avoidWilderness).getClosestToCenter();
        if (nearestNpcLocation == null) {
            return false;
        }
        return Rs2Walker.walkTo(nearestNpcLocation);
    }

    public static boolean walkToNearestMonster(String name, int minClustering) {
        return walkToNearestMonster(name, minClustering, false);
    }

    // Walks to the nearest NPC location with the given name
    public static boolean walkToNearestMonster(String name) {
        return walkToNearestMonster(name, 1, false);
    }

    /**
     * Determines whether the specified NPC is within the player's current attack range.
     * <p>
     * This method will return {@code false} if the NPC or the player's local location
     * cannot be determined. The effective attack range is calculated by
     * {@link Rs2Combat#getAttackRange(boolean, boolean)}, taking into account
     * manual-cast weapons and/or special attacks as specified.
     *
     * @param npc                  the target NPC model; may be {@code null}
     * @param includeManualCast    if {@code true}, include any manual-cast attack range (e.g., magic spells)
     * @param includeSpecialAttack if {@code true}, include any special-attack range
     * @return {@code true} if both locations are known and the tile distance between player
     *         and NPC (using LOCAL_TILE_SIZE) is less than or equal to the computed attack range;
     *         {@code false} otherwise
     */
    public static boolean isInAttackRange(Rs2NpcModel npc, boolean includeManualCast, boolean includeSpecialAttack) {
        if (npc == null) return false;

        LocalPoint playerLocal = Microbot.getClient().getLocalPlayer().getLocalLocation();
        LocalPoint npcLocal = npc.getLocalLocation();

        if (npcLocal == null || playerLocal == null) return false;

        int distanceInTiles = playerLocal.distanceTo(npcLocal) / LOCAL_TILE_SIZE;
        int attackRange = Rs2Combat.getAttackRange(includeManualCast, includeSpecialAttack);

        return distanceInTiles <= attackRange;
    }

    /**
     * Determines whether the specified NPC is within the player's basic attack range,
     * without considering manual-cast weapons or special attacks.
     *
     * @param npc the target NPC model; may be {@code null}
     * @return {@code true} if the NPC is within the default attack range, {@code false} otherwise
     * @see #isInAttackRange(Rs2NpcModel, boolean, boolean)
     */
    public static boolean isInAttackRange(Rs2NpcModel npc) {
        return isInAttackRange(npc, false, false);
    }
}
