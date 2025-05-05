package net.runelite.client.plugins.microbot.util.player;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.kit.KitType;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.grounditems.GroundItemsPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.VarbitValues;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.ActorModel;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;

import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.api.MenuAction.CC_OP;
import static net.runelite.client.plugins.microbot.util.Global.*;

public class Rs2Player {
    static int VENOM_VALUE_CUTOFF = -38;
    private static int antiFireTime = -1;
    private static int superAntiFireTime = -1;
    private static int divineRangedTime = -1;
    private static int divineBastionTime = -1;
    private static int divineCombatTime = -1;
    private static int divineMagicTime = -1;
    public static int antiVenomTime = -1;
    public static int staminaBuffTime = -1;
    public static int antiPoisonTime = -1;
    public static int teleBlockTime = -1;
    public static int goadingTime = -1;
    public static Instant lastAnimationTime = null;
    private static final long COMBAT_TIMEOUT_MS = 10000;
    private static long lastCombatTime = 0;
    @Getter
    public static int lastAnimationID = AnimationID.IDLE;

    public static boolean hasPrayerRegenerationActive() {
        return Microbot.getVarbitValue(Varbits.BUFF_PRAYER_REGENERATION) > 0;
    }

    public static boolean hasAntiFireActive() {
        return antiFireTime > 0 || hasSuperAntiFireActive();
    }

    public static boolean hasSuperAntiFireActive() {
        return superAntiFireTime > 0;
    }

    public static boolean hasDivineRangedActive() {
        return divineRangedTime > 0 || hasDivineBastionActive();
    }

    public static boolean hasRangingPotionActive(int threshold) {
        return Microbot.getClient().getBoostedSkillLevel(Skill.RANGED) - threshold > Microbot.getClient().getRealSkillLevel(Skill.RANGED);
    }

    public static boolean hasDivineBastionActive() {
        return divineBastionTime > 0;
    }

    public static boolean hasDivineCombatActive() {
        return divineCombatTime > 0;
    }

    public static boolean hasDivineMagicActive() {
        return divineMagicTime > 0;
    }

    public static boolean hasGoadingActive() {
        return goadingTime > 0;
    }

    public static boolean hasAttackActive(int threshold) {
        return Microbot.getClient().getBoostedSkillLevel(Skill.ATTACK) - threshold > Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
    }

    public static boolean hasStrengthActive(int threshold) {
        return Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH) - threshold > Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
    }

    public static boolean hasDefenseActive(int threshold) {
        return Microbot.getClient().getBoostedSkillLevel(Skill.DEFENCE) - threshold > Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);
    }

    public static boolean hasMagicActive(int threshold) {
        return Microbot.getClient().getBoostedSkillLevel(Skill.MAGIC) - threshold > Microbot.getClient().getRealSkillLevel(Skill.MAGIC);
    }

    public static boolean hasAntiVenomActive() {
        if (Rs2Equipment.isWearing("serpentine helm")) {
            return true;
        } else return antiVenomTime < VENOM_VALUE_CUTOFF;
    }

    public static boolean hasAntiPoisonActive() {
        return antiPoisonTime > 0;
    }

    public static boolean hasStaminaBuffActive() {
        return staminaBuffTime > 0;
    }
    
    public static boolean isTeleBlocked() {
        return teleBlockTime > 0;
    }

    private static final Map<Player, Long> playerDetectionTimes = new ConcurrentHashMap<>();

    public static void handlePotionTimers(VarbitChanged event) {
        if (event.getVarbitId() == Varbits.ANTIFIRE) {
            antiFireTime = event.getValue();
        }
        if (event.getVarbitId() == Varbits.SUPER_ANTIFIRE) {
            superAntiFireTime = event.getValue();
        }
        if (event.getVarbitId() == Varbits.DIVINE_RANGING) {
            divineRangedTime = event.getValue();
        }
        if (event.getVarbitId() == Varbits.DIVINE_BASTION) {
            divineBastionTime = event.getValue();
        }
        if (event.getVarbitId() == Varbits.DIVINE_SUPER_COMBAT) {
            divineCombatTime = event.getValue();
        }
        if (event.getVarbitId() == Varbits.STAMINA_EFFECT) {
            staminaBuffTime = event.getValue();
        }
        if (event.getVarbitId() == Varbits.BUFF_GOADING_POTION) {
            goadingTime = event.getValue();
        }
        if (event.getVarbitId() == Varbits.DIVINE_MAGIC) {
            divineMagicTime = event.getValue();
        }
        if (event.getVarpId() == VarPlayer.POISON) {
            if (event.getValue() >= VENOM_VALUE_CUTOFF) {
                antiVenomTime = 0;
            } else {
                antiVenomTime = event.getValue();
            }
            final int poisonVarp = event.getValue();

            if (poisonVarp == 0) {
                antiPoisonTime = -1;
            } else {
                antiPoisonTime = poisonVarp;
            }
        }
    }
    
    /**
     * Handles updates to the teleblock timer based on changes to the {@link Varbits#TELEBLOCK} varbit.
     *
     * @see Varbits#TELEBLOCK
     */
    public static void handleTeleblockTimer(VarbitChanged event){
        if (event.getVarbitId() == Varbits.TELEBLOCK) {
            int time = event.getValue();
            
            if (time < 101) {
                teleBlockTime = -1;
            } else {
                teleBlockTime = time;
            }
        }
    }

    public static void handleAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getActor();
        if (player != Microbot.getClient().getLocalPlayer()) {
            return;
        }

        if (player.getAnimation() != AnimationID.IDLE) {
            lastAnimationTime = Instant.now();
            lastAnimationID = player.getAnimation();
        }
    }

    /**
     * Wait for walking
     */
    public static void waitForWalking() {
        boolean result = sleepUntilTrue(Rs2Player::isWalking, 100, 5000);
        if (!result) return;
        sleepUntil(() -> !Rs2Player.isWalking());
    }

    /**
     * Waits for the player to start walking within the specified time limit.
     * If the player starts walking, it then waits until the player stops walking.
     *
     * @param time The maximum time (in milliseconds) to wait for the player to start walking.
     *             If the player does not start walking within this time, the method exits early.
     */
    public static void waitForWalking(int time) {
        boolean result = sleepUntilTrue(Rs2Player::isWalking, 100, time);
        if (!result) return;
        sleepUntil(() -> !Rs2Player.isWalking(), time);
    }

    /**
     * Waits for an XP drop in the specified skill within a default timeout of 5000 milliseconds.
     *
     * @param skill The skill to monitor for an XP drop.
     * @return {@code true} if an XP drop was detected within the timeout, {@code false} otherwise.
     */
    public static boolean waitForXpDrop(Skill skill) {
        return waitForXpDrop(skill, 5000, false);
    }

    /**
     * Waits for an XP drop in the specified skill within a given timeout.
     *
     * @param skill The skill to monitor for an XP drop.
     * @param time  The maximum time (in milliseconds) to wait for the XP drop.
     * @return {@code true} if an XP drop was detected within the timeout, {@code false} otherwise.
     */
    public static boolean waitForXpDrop(Skill skill, int time) {
        return waitForXpDrop(skill, time, false);
    }

    /**
     * Waits for an XP drop in the specified skill or stops if the inventory is full.
     *
     * @param skill              The skill to monitor for an XP drop.
     * @param inventoryFullCheck If {@code true}, also stops waiting if the inventory becomes full.
     * @return {@code true} if an XP drop was detected or the inventory became full, {@code false} otherwise.
     */
    public static boolean waitForXpDrop(Skill skill, boolean inventoryFullCheck) {
        return waitForXpDrop(skill, 5000, inventoryFullCheck);
    }

    /**
     * Waits for an XP drop in the specified skill within a given timeout or stops if the inventory is full.
     *
     * @param skill              The skill to monitor for an XP drop.
     * @param time               The maximum time (in milliseconds) to wait for the XP drop.
     * @param inventoryFullCheck If {@code true}, also stops waiting if the inventory becomes full.
     * @return {@code true} if an XP drop was detected or the inventory became full, {@code false} otherwise.
     */
    public static boolean waitForXpDrop(Skill skill, int time, boolean inventoryFullCheck) {
        final int skillExp = Microbot.getClient().getSkillExperience(skill);
        return sleepUntilTrue(() ->
                        skillExp != Microbot.getClient().getSkillExperience(skill) ||
                                (inventoryFullCheck && Rs2Inventory.isFull()),
                100, time
        );
    }

    /**
     * Wait for animation
     */
    public static void waitForAnimation() {
        boolean result = sleepUntilTrue(Rs2Player::isAnimating, 100, 5000);
        if (!result) return;
        sleepUntil(() -> !Rs2Player.isAnimating());
    }

    /**
     * Waits for the player to start animating within a given time limit.
     * If the player starts animating, it then waits until the animation stops.
     *
     * @param time The maximum time (in milliseconds) to wait for the player to start animating.
     *             If the player does not start animating within this time, the method exits early.
     */
    public static void waitForAnimation(int time) {
        boolean result = sleepUntilTrue(() -> Rs2Player.isAnimating(time), 100, 5000);
        if (!result) return;
        sleepUntil(() -> !Rs2Player.isAnimating(time));
    }

    /**
     * Checks if the player has been animating within the past specified milliseconds.
     *
     * @param ms The time window (in milliseconds) to check for recent animations.
     * @return {@code true} if the player has been animating within the last {@code ms} milliseconds,
     *         or if the player's current animation is not {@code AnimationID.IDLE}, {@code false} otherwise.
     */
    public static boolean isAnimating(int ms) {
        return (lastAnimationTime != null && Duration.between(lastAnimationTime, Instant.now()).toMillis() < ms)
                || getAnimation() != AnimationID.IDLE;
    }

    /**
     * Checks if the player has been animating within the past 600 milliseconds.
     *
     * @return {@code true} if the player has been animating within the last 600 milliseconds,
     *         or if the player's current animation is not {@code AnimationID.IDLE}, {@code false} otherwise.
     */
    public static boolean isAnimating() {
        return isAnimating(600);
    }

    /**
     * Checks if the player is currently walking.
     *
     * @return {@code true} if the player is moving, {@code false} otherwise.
     * @deprecated Since version 1.7.2, use {@link #isMoving()} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean isWalking() {
        return Rs2Player.isMoving();
    }

    /**
     * Checks if the player is currently moving based on their pose animation.
     * A player is considered moving if their pose animation is different from their idle pose animation.
     *
     * @return {@code true} if the player is moving, {@code false} if they are idle.
     */
    public static boolean isMoving() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            if (localPlayer == null) {
                return false;
            }
            return localPlayer.getPoseAnimation() != localPlayer.getIdlePoseAnimation();
        }).orElse(false);
    }

    /**
     * Checks if the player is currently interacting with another entity (NPC, player, or object).
     *
     * @return {@code true} if the player is interacting with another entity, {@code false} otherwise.
     */
    public static boolean isInteracting() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getLocalPlayer().isInteracting())
                .orElse(false);
    }

    /**
     * Checks if the player has an active membership.
     *
     * @return {@code true} if the player is a member (has remaining membership days), {@code false} otherwise.
     */
    public static boolean isMember() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getVarpValue(VarPlayer.MEMBERSHIP_DAYS) > 0
        ).orElse(false);
    }

    /**
     * Checks if the player is currently in a members-only world.
     *
     * @return {@code true} if the player is in a members-only world, {@code false} otherwise.
     */
    public static boolean isInMemberWorld() {
        WorldResult worldResult = Microbot.getWorldService().getWorlds();

        if (worldResult != null) {
            List<net.runelite.http.api.worlds.World> worlds = worldResult.getWorlds();
            return worlds.stream()
                    .anyMatch(x -> x.getId() == Microbot.getClient().getWorld() && x.getTypes().contains(WorldType.MEMBERS));
        }

        return false;
    }


    @Deprecated(since = "Use the Rs2Combat.specState method", forRemoval = true)
    public static void toggleSpecialAttack(int energyRequired) {
        int currentSpecEnergy = Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT);
        if (currentSpecEnergy >= energyRequired && (Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_ENABLED) == 0)) {
            Rs2Widget.clickWidget("special attack");
        }
    }

    /**
     * Toggles the player's run energy on or off.
     *
     * @param toggle {@code true} to enable running, {@code false} to disable it.
     * @return {@code true} if the toggle action was performed successfully or was already in the desired state,
     *         {@code false} if the run energy toggle widget was not found.
     */
    public static boolean toggleRunEnergy(boolean toggle) {
        if (Microbot.getVarbitPlayerValue(173) == 0 && !toggle) return true;
        if (Microbot.getVarbitPlayerValue(173) == 1 && toggle) return true;

        Widget widget = Rs2Widget.getWidget(WidgetInfo.MINIMAP_TOGGLE_RUN_ORB.getId());
        if (widget == null) return false;

        Microbot.getMouse().click(widget.getCanvasLocation());
        sleep(150, 300);

        return true;
    }

    /**
     * Checks if the player's run energy is currently enabled.
     *
     * @return {@code true} if run energy is enabled, {@code false} otherwise.
     */
    public static boolean isRunEnabled() {
        return Microbot.getVarbitPlayerValue(173) == 1;
    }

    /**
     * Logs the player out of the game
     */
    public static void logout() {
        if (!Microbot.isLoggedIn()) return;
        if (Rs2Tab.getCurrentTab() != InterfaceTab.LOGOUT) {
            Rs2Tab.switchToLogout();
            sleepUntil(() -> Rs2Tab.getCurrentTab() == InterfaceTab.LOGOUT);
        }

        Widget currentWorldWidget = Rs2Widget.getWidget(69, 3);
        if (currentWorldWidget != null) {
            // From World Switcher
            Microbot.doInvoke(new NewMenuEntry(-1, 4522009, CC_OP.getId(), 1, -1, "Logout"), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        } else {
            // From red logout button
            Microbot.doInvoke(new NewMenuEntry(-1, 11927560, CC_OP.getId(), 1, -1, "Logout"), new Rectangle(1, 1, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight()));
        }
    }

    /**
     * Logs out the player if a specified number of players are detected within a given distance and time.
     *
     * @param amountOfPlayers The number of players to detect before triggering a logout.
     * @param time            The duration (in milliseconds) that the players must remain detected before logging out.
     *                         If {@code time <= 0}, the logout occurs immediately upon detection.
     * @param distance        The maximum distance (in tiles) from the player to check for other players.
     *                         If {@code distance <= 0}, all detected players are considered.
     * @return {@code true} if the player logged out, {@code false} otherwise.
     */
    public static boolean logoutIfPlayerDetected(int amountOfPlayers, int time, int distance) {
        List<Rs2PlayerModel> players = getPlayers(player -> true).collect(Collectors.toList());
        long currentTime = System.currentTimeMillis();

        if (distance > 0) {
            players = players.stream()
                    .filter(x -> x != null && x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= distance)
                    .collect(Collectors.toList());
        }
        if (time > 0 && players.size() > amountOfPlayers) {
            // Update detection times for currently detected players
            for (Rs2PlayerModel player : players) {
                playerDetectionTimes.putIfAbsent(player, currentTime);
            }

            // Remove players who are no longer detected
            playerDetectionTimes.keySet().retainAll(players);

            // Check if any player has been detected for longer than the specified time
            for (Rs2PlayerModel player : players) {
                long detectionTime = playerDetectionTimes.getOrDefault(player, 0L);
                if (currentTime - detectionTime >= time) {
                    logout();
                    playerDetectionTimes.clear();
                    return true;
                }
            }
        } else if (time <= 0 && players.size() >= amountOfPlayers) {
            logout();
            playerDetectionTimes.clear();
            return true;
        }
        return false;
    }

    /**
     * Logs out the player if a specified number of players are detected within a given time,
     * checking all players regardless of distance.
     *
     * @param amountOfPlayers The number of players to detect before triggering a logout.
     * @param time            The duration (in milliseconds) that the players must remain detected before logging out.
     *                         If {@code time <= 0}, the logout occurs immediately upon detection.
     * @return {@code true} if the player logged out, {@code false} otherwise.
     */
    public static boolean logoutIfPlayerDetected(int amountOfPlayers, int time) {
        return logoutIfPlayerDetected(amountOfPlayers, time, 0);
    }

    /**
     * Logs out the player if a specified number of players are detected, 
     * triggering an immediate logout upon detection.
     *
     * @param amountOfPlayers The number of players to detect before triggering a logout.
     * @return {@code true} if the player logged out, {@code false} otherwise.
     */
    public static boolean logoutIfPlayerDetected(int amountOfPlayers) {
        return logoutIfPlayerDetected(amountOfPlayers, 0, 0);
    }

    /**
     * Hops to a random world if a specified number of players are detected within a given distance and time.
     *
     * @param amountOfPlayers The number of players to detect before triggering a world hop.
     * @param time            The duration (in milliseconds) that the players must remain detected before hopping worlds.
     *                         If {@code time <= 0}, the hop occurs immediately upon detection.
     * @param distance        The maximum distance (in tiles) from the player to check for other players.
     *                         If {@code distance <= 0}, all detected players are considered.
     * @return {@code true} if the player detected and successfully hopped worlds, {@code false} otherwise.
     */
    public static boolean hopIfPlayerDetected(int amountOfPlayers, int time, int distance) {
        List<Rs2PlayerModel> players = getPlayers(player -> true).collect(Collectors.toList());
        long currentTime = System.currentTimeMillis();

        if (distance > 0) {
            players = players.stream()
                    .filter(x -> x != null && x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= distance)
                    .collect(Collectors.toList());
        }

        if (time > 0 && players.size() >= amountOfPlayers) {
            // Update detection times for currently detected players
            for (Rs2PlayerModel player : players) {
                playerDetectionTimes.putIfAbsent(player, currentTime);
            }

            // Remove players who are no longer detected
            playerDetectionTimes.keySet().retainAll(players);

            // Check if any player has been detected for longer than the specified time
            for (Rs2PlayerModel player : players) {
                long detectionTime = playerDetectionTimes.getOrDefault(player, 0L);
                if (currentTime - detectionTime >= time) {
                    int randomWorld = Login.getRandomWorld(isMember());
                    Microbot.hopToWorld(randomWorld);
                    return true;
                }
            }
        } else if (players.size() >= amountOfPlayers) {
            int randomWorld = Login.getRandomWorld(isMember());
            Microbot.hopToWorld(randomWorld);
            return true;
        }
        return false;
    }

    /**
     * Consumes food when the player's health percentage falls below the specified threshold.
     * Uses default food consumption behavior.
     *
     * @param percentage The health percentage at which food should be consumed.
     * @return {@code true} if food was consumed, {@code false} if no action was taken.
     */
    public static boolean eatAt(int percentage) {
        // Call the full method with a default value of false for fastFood
        return eatAt(percentage, false);
    }

    /**
     * Consumes food when the player's health percentage falls below the specified threshold.
     * The method searches the inventory for the first available food item.
     *
     * @param percentage The health percentage at which food should be consumed.
     * @param fastFood If true, prioritize faster food consumption behavior.
     * @return {@code true} if food was consumed, {@code false} if no action was taken.
     */
    public static boolean eatAt(int percentage, boolean fastFood) {
        double threshold = getHealthPercentage();
        if (threshold <= percentage) {
            if (fastFood && fastFoodPresent()) {
                return useFastFood(); // hypothetical fast food consuming method
            }
            return useFood(); // default method
        }
        return false;
    }

    /**
     * Consumes the first available high-priority food item from the player's inventory.
     *
     * <p>Only food items defined in {@link Rs2Food} with a priority of {@code 1} are considered fast food.</p>
     * <p>This method ignores noted items and will not attempt to drink items like Jug of Wine.</p>
     *
     * @return {@code true} if a fast food item was consumed, {@code false} if none were found.
     */
    public static boolean useFastFood() {
        List<Rs2ItemModel> foods = Rs2Inventory.getInventoryFood();
        if (foods.isEmpty()) return false;

        Optional<Rs2ItemModel> food = foods.stream()
                .filter(rs2Item -> !rs2Item.isNoted())
                .filter(rs2Item -> Rs2Food.getIds().contains(rs2Item.getId()))
                .filter(rs2Item -> {
                    for (Rs2Food f : Rs2Food.values()) {
                        if (f.getId() == rs2Item.getId() && f.getTickdelay() == 1) return true;
                    }
                    return false;
                })
                .findFirst();

        return food.filter(rs2ItemModel -> Rs2Inventory.interact(rs2ItemModel, "eat")).isPresent();

    }

    /**
     * Finds and consumes the best available food item from the player's inventory.
     *
     * <p>If in the Wilderness, prioritizes blighted food items but falls back to regular food if none are available.</p>
     * <p>If the selected food is a "Jug of Wine," the player will drink it instead of eating.</p>
     *
     * @return {@code true} if food was consumed, {@code false} if no food was available.
     */
    public static boolean useFood() {
        List<Rs2ItemModel> foods = Rs2Inventory.getInventoryFood();
        if (foods.isEmpty()) return false;

        boolean inWilderness = Microbot.getVarbitValue(Varbits.IN_WILDERNESS) == 1;

        // Separate blighted and non-blighted food
        List<Rs2ItemModel> blightedFoods = foods.stream()
                .filter(rs2Item -> !rs2Item.isNoted() && rs2Item.getName().toLowerCase().contains("blighted"))
                .collect(Collectors.toList());

        List<Rs2ItemModel> regularFoods = foods.stream()
                .filter(rs2Item -> !rs2Item.isNoted() && !rs2Item.getName().toLowerCase().contains("blighted"))
                .collect(Collectors.toList());

        // Select food to use: prefer blighted in Wilderness, otherwise use any available food
        Rs2ItemModel foodToUse = (inWilderness && !blightedFoods.isEmpty()) ? blightedFoods.get(0)
                : !regularFoods.isEmpty() ? regularFoods.get(0) : null;

        if (foodToUse == null) return false;

        return foodToUse.getName().toLowerCase().contains("jug of wine")
                ? Rs2Inventory.interact(foodToUse, "drink")
                : Rs2Inventory.interact(foodToUse, "eat");
    }

    /**
     * Calculates the player's current health as a percentage of their real (base) health.
     *
     * @return the health percentage as a double. For example:
     *         150.0 if boosted, 80.0 if drained, or 100.0 if unchanged.
     */
    public static double getHealthPercentage() {
        return (double) (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100) / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
    }

    /**
     * Retrieves a list of players around the player.
     *
     * @return A list of {@code Rs2PlayerModel} objects representing nearby players, excluding the local player.
     * @deprecated Since 1.7.2, use {@link #getPlayers(Predicate)} for better filtering support.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static List<Player> getPlayers() {
        return Microbot.getClient()
                .getTopLevelWorldView()
                .players()
                .stream()
                .filter(Objects::nonNull)
                .filter(x -> x != Microbot.getClient().getLocalPlayer())
                .collect(Collectors.toList());
    }

    /**
     * Get a stream of players around you, optionally filtered by a predicate.
     *
     * @param predicate A condition to filter players (optional).
     * @return A stream of Rs2PlayerModel objects representing nearby players.
     */
    public static Stream<Rs2PlayerModel> getPlayers(Predicate<Rs2PlayerModel> predicate) {
        List<Rs2PlayerModel> players = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getTopLevelWorldView().players()
                        .stream()
                        .filter(Objects::nonNull)
                        .map(Rs2PlayerModel::new)
                        .filter(x -> x.getPlayer() != Microbot.getClient().getLocalPlayer())
                        .filter(predicate)
                        .collect(Collectors.toList())
        ).orElse(new ArrayList<>());

        return players.stream();
    }

    /**
     * Retrieves a player by name with an optional exact match.
     *
     * @param playerName The name of the player to search for.
     * @param exact      If {@code true}, performs an exact name match (case insensitive).
     *                   If {@code false}, checks if the player name contains the given string.
     * @return The first matching {@code Rs2PlayerModel}, or {@code null} if no player is found.
     */
    public static Rs2PlayerModel getPlayer(String playerName, boolean exact) {
        return getPlayers(player -> {
            String name = player.getName();
            if (name == null) return false;
            return exact ? name.equalsIgnoreCase(playerName) : name.toLowerCase().contains(playerName.toLowerCase());
        }).findFirst().orElse(null);
    }

    /**
     * Retrieves a player by name using a partial match.
     *
     * @param playerName The name of the player to search for.
     * @return The first matching {@code Rs2PlayerModel}, or {@code null} if no player is found.
     *         Uses {@code getPlayer(playerName, false)} to perform a case-insensitive partial match.
     */
    public static Rs2PlayerModel getPlayer(String playerName) {
        return getPlayer(playerName, false);
    }

    /**
     * Use this method to get a list of players that are in combat
     *
     * @return a list of players that are in combat
     */
    public static List<Rs2PlayerModel> getPlayersInCombat() {
        return getPlayers(player -> player.getHealthRatio() != -1).collect(Collectors.toList());
    }

    /**
     * Calculates the player's health as a percentage.
     *
     * <p>The method retrieves the player's health ratio and scale, then calculates 
     * the percentage based on these values.</p>
     *
     * <p><b>Note:</b> If health information is unavailable (i.e., missing or invalid values),
     * the method returns {@code -1}.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player or actor to calculate health for.
     * @return The health percentage (0-100), or {@code -1} if health information is unavailable.
     */
    public static int calculateHealthPercentage(Rs2PlayerModel rs2Player) {
        int healthRatio = rs2Player.getHealthRatio();
        int healthScale = rs2Player.getHealthScale();

        // Check if health information is available
        if (healthRatio == -1 || healthScale == -1 || healthScale == 0) {
            return -1; // Health information is missing or invalid
        }

        // Calculate health percentage
        return (int) ((healthRatio / (double) healthScale) * 100);
    }

    /**
     * Calculates the player's health as a percentage.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel}
     * before calculating health percentage.</p>
     *
     * @param player The {@link Player} to calculate health for.
     * @return The health percentage (0-100), or {@code -1} if health information is unavailable.
     * @deprecated Since 1.7.2, use {@link #calculateHealthPercentage(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static int calculateHealthPercentage(Player player) {
        return calculateHealthPercentage(new Rs2PlayerModel(player));
    }

    /**
     * Retrieves a map of the player's equipped items, mapping {@link KitType} to their corresponding item IDs.
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player whose equipment is to be retrieved.
     * @return A {@code Map<KitType, Integer>} containing the equipment slot types as keys and the corresponding item IDs as values.
     */
    public static Map<KitType, Integer> getPlayerEquipmentIds(Rs2PlayerModel rs2Player) {
        Map<KitType, Integer> equipmentMap = new HashMap<>();

        for (KitType kitType : KitType.values()) {
            int itemId = rs2Player.getPlayerComposition().getEquipmentId(kitType);
            equipmentMap.put(kitType, itemId);
        }

        return equipmentMap;
    }

    /**
     * Retrieves a map of the player's equipped items by converting a {@link Player} object into an {@link Rs2PlayerModel}.
     *
     * @param player The {@link Player} whose equipment is to be retrieved.
     * @return A {@code Map<KitType, Integer>} containing the equipment slot types as keys and the corresponding item IDs as values.
     * @deprecated Since 1.7.2, use {@link #getPlayerEquipmentIds(Rs2PlayerModel)} for consistency and better type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static Map<KitType, Integer> getPlayerEquipmentIds(Player player) {
        return getPlayerEquipmentIds(new Rs2PlayerModel(player));
    }


    /**
     * Retrieves a map of the player's equipped items, mapping {@link KitType} to their corresponding item names.
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player whose equipment names are to be retrieved.
     * @return A {@code Map<KitType, String>} containing the equipment slot types as keys and the corresponding item names as values.
     */
    public static Map<KitType, String> getPlayerEquipmentNames(Rs2PlayerModel rs2Player) {
        Map<KitType, String> equipmentMap = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Map<KitType, String> tempMap = new HashMap<>();
            for (KitType kitType : KitType.values()) {
                String itemName = Microbot.getItemManager()
                        .getItemComposition(rs2Player.getPlayerComposition().getEquipmentId(kitType))
                        .getName();
                tempMap.put(kitType, itemName);
            }
            return tempMap;
        }).orElse(new HashMap<>());

        return equipmentMap;
    }

    /**
     * Retrieves a map of the player's equipped items by converting a {@link Player} object into an {@link Rs2PlayerModel}.
     *
     * @param player The {@link Player} whose equipment names are to be retrieved.
     * @return A {@code Map<KitType, String>} containing the equipment slot types as keys and the corresponding item names as values.
     * @deprecated Since 1.7.2, use {@link #getPlayerEquipmentNames(Rs2PlayerModel)} for consistency and better type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static Map<KitType, String> getPlayerEquipmentNames(Player player) {
        return getPlayerEquipmentNames(new Rs2PlayerModel(player));
    }


    /**
     * Checks if a player has a specific item equipped by its item ID.
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player whose equipment is being checked.
     * @param itemId    The ID of the item to check for.
     * @return {@code true} if the player has the specified item equipped, {@code false} otherwise.
     */
    public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, int itemId) {
        Map<KitType, Integer> equipment = getPlayerEquipmentIds(rs2Player);

        return equipment.values().stream()
                .anyMatch(equippedItemId -> equippedItemId == itemId);
    }

    /**
     * Checks if a player has a specific item equipped by its item ID.
     * Converts a {@link Player} object into an {@link Rs2PlayerModel} before performing the check.
     *
     * @param player The {@link Player} whose equipment is being checked.
     * @param itemId The ID of the item to check for.
     * @return {@code true} if the player has the specified item equipped, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #hasPlayerEquippedItem(Rs2PlayerModel, int)} for consistency and better type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean hasPlayerEquippedItem(Player player, int itemId) {
        return hasPlayerEquippedItem(new Rs2PlayerModel(player), itemId);
    }
    
    /**
     * Checks if a player has any of the specified items equipped by their item IDs.
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player whose equipment is being checked.
     * @param itemIds   An array of item IDs to check for.
     * @return {@code true} if the player has any of the specified items equipped, {@code false} otherwise.
     */
    public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, int[] itemIds) {
        Map<KitType, Integer> equipment = getPlayerEquipmentIds(rs2Player);

        return equipment.values().stream()
                .anyMatch(equippedItemId -> Arrays.stream(itemIds).anyMatch(id -> id == equippedItemId));
    }

    /**
     * Checks if a player has any of the specified items equipped by their item IDs.
     * Converts a {@link Player} object into an {@link Rs2PlayerModel} before performing the check.
     *
     * @param player  The {@link Player} whose equipment is being checked.
     * @param itemIds An array of item IDs to check for.
     * @return {@code true} if the player has any of the specified items equipped, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #hasPlayerEquippedItem(Rs2PlayerModel, int[])} for consistency and better type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean hasPlayerEquippedItem(Player player, int[] itemIds) {
        return hasPlayerEquippedItem(new Rs2PlayerModel(player), itemIds);
    }


    /**
     * Checks if a player has a specific item equipped by its name.
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player whose equipment is being checked.
     * @param itemName  The name of the item to check for.
     * @return {@code true} if the player has the specified item equipped, {@code false} otherwise.
     */
    public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, String itemName) {
        Map<KitType, String> equipment = getPlayerEquipmentNames(rs2Player);

        return equipment.values().stream()
                .anyMatch(equippedItem -> equippedItem.equalsIgnoreCase(itemName));
    }

    /**
     * Checks if a player has a specific item equipped by its name.
     * Converts a {@link Player} object into an {@link Rs2PlayerModel} before performing the check.
     *
     * @param player   The {@link Player} whose equipment is being checked.
     * @param itemName The name of the item to check for.
     * @return {@code true} if the player has the specified item equipped, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #hasPlayerEquippedItem(Rs2PlayerModel, String)} for consistency and better type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean hasPlayerEquippedItem(Player player, String itemName) {
        return hasPlayerEquippedItem(new Rs2PlayerModel(player), itemName);
    }


    /**
     * Checks if a player has any of the specified items equipped by their names.
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player whose equipment is being checked.
     * @param itemNames A list of item names to check for.
     * @return {@code true} if the player has any of the specified items equipped, {@code false} otherwise.
     */
    public static boolean hasPlayerEquippedItem(Rs2PlayerModel rs2Player, List<String> itemNames) {
        Map<KitType, String> equipment = getPlayerEquipmentNames(rs2Player);

        return equipment.values().stream()
                .anyMatch(equippedItem -> itemNames.stream().anyMatch(equippedItem::equalsIgnoreCase));
    }

    /**
     * Checks if a player has any of the specified items equipped by their names.
     * Converts a {@link Player} object into an {@link Rs2PlayerModel} before performing the check.
     *
     * @param player    The {@link Player} whose equipment is being checked.
     * @param itemNames A list of item names to check for.
     * @return {@code true} if the player has any of the specified items equipped, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #hasPlayerEquippedItem(Rs2PlayerModel, List)} for consistency and better type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean hasPlayerEquippedItem(Player player, List<String> itemNames) {
        return hasPlayerEquippedItem(new Rs2PlayerModel(player), itemNames);
    }


    /**
     * Retrieves the combat level of the local player.
     *
     * @return The combat level of the local player.
     */
    public static int getCombatLevel() {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getClient().getLocalPlayer().getCombatLevel()
        ).orElse(0);
    }

    /**
     * Updates the last combat time when the player engages in or is hit during combat.
     */
    public static void updateCombatTime() {
        Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player localPlayer = Microbot.getClient().getLocalPlayer();
            if (localPlayer != null) {
                lastCombatTime = System.currentTimeMillis();
            }
            return null;
        });
    }
    /**
     * Get the local player as an {@link Rs2PlayerModel}.
     *
     * @return The local player wrapped in an {@link Rs2PlayerModel}.
     */
    public static Rs2PlayerModel getLocalPlayer() {
        return new Rs2PlayerModel(Microbot.getClient().getLocalPlayer());
    }

    /**
     * Get the raw local player instance.
     *
     * @return The raw {@link Player} object.
     * @deprecated Since 1.7.2, use {@link #getLocalPlayer()} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static Player getLocalPlayer(boolean raw) {
        return Microbot.getClient().getLocalPlayer();
    }

    /**
     * Checks if the player is in combat based on recent activity.
     *
     * @return True if the player is in combat, false otherwise.
     */
    public static boolean isInCombat() {
        return System.currentTimeMillis() - lastCombatTime < COMBAT_TIMEOUT_MS;
    }

    /**
     * Gets a list of Rs2PlayerModel objects representing players around the local player 
     * within the combat level range and wilderness level where they can attack and be attacked.
     *
     * @return A list of Rs2PlayerModel objects within the combat range and attackable wilderness levels.
     */
    public static List<Rs2PlayerModel> getPlayersInCombatLevelRange() {
        return getPlayersMatchingCombatCriteria().collect(Collectors.toList());
    }

    /**
     * Gets a list of Player objects around the local player within the combat level range 
     * and wilderness level where they can attack and be attacked.
     *
     * @param raw If true, returns a list of raw Player objects instead of Rs2PlayerModel.
     * @return A list of Player objects within the combat range and attackable wilderness levels.
     * @deprecated Since 1.7.2, use {@link #getPlayersInCombatLevelRange()} instead.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static List<Player> getPlayersInCombatLevelRange(boolean raw) {
        return getPlayersMatchingCombatCriteria()
                .map(Rs2PlayerModel::getPlayer)
                .collect(Collectors.toList());
    }

    /**
     * Helper method that applies the combat level filtering and returns a Stream of Rs2PlayerModel.
     *
     * @return A Stream of Rs2PlayerModel objects that match the combat range criteria.
     */
    private static Stream<Rs2PlayerModel> getPlayersMatchingCombatCriteria() {
        int localCombatLevel = getCombatLevel();
        int localWildernessLevel = Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation());

        if (localWildernessLevel == 0) return Stream.empty();

        int localMinCombatLevel = Math.max(3, localCombatLevel - localWildernessLevel);
        int localMaxCombatLevel = Math.min(126, localCombatLevel + localWildernessLevel);

        return getPlayers(player -> {
            int playerCombatLevel = player.getCombatLevel();
            int playerWildernessLevel = Rs2Pvp.getWildernessLevelFrom(player.getWorldLocation());

            if (playerWildernessLevel == 0) return false;

            int playerMinCombatLevel = Math.max(3, playerCombatLevel - playerWildernessLevel);
            int playerMaxCombatLevel = Math.min(126, playerCombatLevel + playerWildernessLevel);

            boolean localCanAttackPlayer = playerCombatLevel >= localMinCombatLevel && playerCombatLevel <= localMaxCombatLevel;
            boolean playerCanAttackLocal = localCombatLevel >= playerMinCombatLevel && localCombatLevel <= playerMaxCombatLevel;

            return localCanAttackPlayer && playerCanAttackLocal;
        });
    }

    /**
     * Retrieves the player's current world location as a {@link WorldPoint}.
     *
     * <p>If the player is in an instanced world, the method converts the local position 
     * to an instanced {@link WorldPoint}. Otherwise, it returns the player's standard 
     * world location.</p>
     *
     * @return The {@link WorldPoint} representing the player's current location.
     */
    public static WorldPoint getWorldLocation() {
        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
            LocalPoint l = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), getLocalPlayer().getWorldLocation());
            WorldPoint playerInstancedWorldLocation = WorldPoint.fromLocalInstance(Microbot.getClient(), l);
            return playerInstancedWorldLocation;
        } else {
            return getLocalPlayer().getWorldLocation();
        }
    }

    /**
     * Retrieves the player's current location as an {@link Rs2WorldPoint}.
     *
     * <p>This method wraps the player's {@link WorldPoint} location into an {@link Rs2WorldPoint}.</p>
     *
     * @return An {@link Rs2WorldPoint} representing the player's current world location.
     */
    public static Rs2WorldPoint getRs2WorldPoint() {
        return new Rs2WorldPoint(getWorldLocation());
    }

    /**
     * Checks if the player is within a specified distance of a given {@link WorldPoint}.
     *
     * @param worldPoint The {@link WorldPoint} to check proximity to.
     * @param distance   The radius (in tiles) around the {@code worldPoint} to check.
     * @return {@code true} if the player is within the specified distance, {@code false} otherwise.
     */
    public static boolean isNearArea(WorldPoint worldPoint, int distance) {
        WorldArea worldArea = new WorldArea(worldPoint, distance, distance);
        return worldArea.contains(getWorldLocation());
    }

    /**
     * Retrieves the player's current {@link LocalPoint} location.
     *
     * <p>This is commonly used in instanced areas where world coordinates differ from local coordinates.</p>
     *
     * @return The {@link LocalPoint} representing the player's current position.
     */
    public static LocalPoint getLocalLocation() {
        return Microbot.getClient().getLocalPlayer().getLocalLocation();
    }

    /**
     * Checks if the player is at full health.
     *
     * @return {@code true} if the player's boosted hitpoints level is greater than or equal to their real hitpoints level,
     *         {@code false} otherwise.
     */
    public static boolean isFullHealth() {
        return Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS)
                >= Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
    }

    /**
     * Checks if the player is in a multi-combat area.
     *
     * @return {@code true} if the player is inside a multi-combat zone, {@code false} otherwise.
     */
    public static boolean isInMulti() {
        return Microbot.getVarbitValue(Varbits.MULTICOMBAT_AREA)
                == VarbitValues.INSIDE_MULTICOMBAT_ZONE.getValue();
    }

    public static boolean drinkPrayerPotion() {
        int maxPrayer = getRealSkillLevel(Skill.PRAYER);
        int maxHerblore = getRealSkillLevel(Skill.HERBLORE);
        int restoreAmount;

        if (hasPotion("moonlight moth mix")) {
            restoreAmount = 22;
        } else if (hasPotion("moonlight potion")) {
            int prayerRestore = (maxPrayer / 4) + 7;
            int herbloreRestore = (int) Math.floor((maxHerblore * 3.0 / 10.0)) + 7;
            restoreAmount = Math.max(prayerRestore, herbloreRestore);
        } else if (hasPotion("super restore") || hasPotion("blighted super restore") ) {
            restoreAmount = (maxPrayer / 4) + 8;
        } else {
            restoreAmount = (maxPrayer / 4) + 7;
        }

        int threshold = maxPrayer - restoreAmount;
        int randomizedThreshold = Rs2Random.randomGaussian(threshold - 5, 3);
        randomizedThreshold = Math.min(randomizedThreshold, threshold);
        //System.out.println("Threshold: " + randomizedThreshold);

        return drinkPrayerPotionAt(randomizedThreshold);
    }

    /**
     * Drinks a prayer potion when the player's prayer points fall below the specified threshold.
     *
     * <p><b>Priority Order:</b></p>
     * <ul>
     *     <li>Uses a Prayer Regeneration Potion first, if its effect is not active.</li>
     *     <li>If in the Wilderness or a PvP world, prioritizes a Blighted Super Restore.</li>
     *     <li>If neither of the above is available, uses a standard prayer potion variant.</li>
     * </ul>
     *
     * @param prayerPoints The prayer points threshold at which a potion should be consumed.
     * @return {@code true} if a potion was successfully consumed, {@code false} if no applicable potion was used.
     */
    public static boolean drinkPrayerPotionAt(int prayerPoints) {
        // Check if current prayer level is above the threshold
        if (getBoostedSkillLevel(Skill.PRAYER) > prayerPoints) return false;

        boolean inWilderness = Microbot.getVarbitValue(Varbits.IN_WILDERNESS) == 1;
        boolean isInPVPWorld = Microbot.getClient().getWorldType().contains(WorldType.PVP);

        // Prioritize Prayer Regeneration Potion if effect is not active
        if (!Rs2Player.hasPrayerRegenerationActive() && usePotion(Rs2Potion.getPrayerRegenerationPotion())) return true;

        // If in Wilderness or PvP world, prioritize Blighted Super Restore
        if (inWilderness || isInPVPWorld)  {
            if (hasPotion("blighted super restore")) {
                return usePotion("blighted super restore");
            }
        }

        // Use a standard prayer potion from available variants
        return usePotion(Rs2Potion.getPrayerPotionsVariants().toArray(new String[0]));
    }

    /**
     * Drinks a combat-related potion for the specified skill.
     *
     * <p>This method defaults to allowing super combat potions when applicable.</p>
     *
     * @param skill The {@link Skill} for which a potion should be consumed.
     * @return {@code true} if a potion was successfully consumed, {@code false} otherwise.
     */
    public static boolean drinkCombatPotionAt(Skill skill) {
        return drinkCombatPotionAt(skill, true);
    }

    /**
     * Drinks a combat-related potion to boost the specified skill.
     *
     * <p><b>Priority Order:</b></p>
     * <ul>
     *     <li>If the current boosted level is already 5 or more above the real level, no potion is consumed.</li>
     *     <li>If {@code superCombat} is {@code true} and the skill is Attack, Strength, or Defence,
     *         a super combat potion is prioritized.</li>
     *     <li>If a super combat potion is not available, the method falls back to a skill-specific potion.</li>
     * </ul>
     *
     * @param skill       The {@link Skill} for which a potion should be consumed.
     * @param superCombat If {@code true}, prioritizes using a super combat potion for melee skills.
     * @return {@code true} if a potion was successfully consumed, {@code false} otherwise.
     */
    public static boolean drinkCombatPotionAt(Skill skill, boolean superCombat) {
        // If the current boosted level is already 5 or more above the real level, don't drink
        if (Microbot.getClient().getBoostedSkillLevel(skill)
                - Microbot.getClient().getRealSkillLevel(skill) > 5) {
            return false;
        }

        // If superCombat is specified and the skill is Attack, Strength, or Defence, try super combat potions first
        if (superCombat && (skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE)) {
            if (usePotion(Rs2Potion.getCombatPotionsVariants().toArray(new String[0]))) {
                return true;
            }
        }

        // Then fall back to skill-specific potions based on which skill is requested
        switch (skill) {
            case ATTACK:
                return usePotion(Rs2Potion.getAttackPotionsVariants().toArray(new String[0]));

            case STRENGTH:
                return usePotion(Rs2Potion.getStrengthPotionsVariants().toArray(new String[0]));

            case DEFENCE:
                return usePotion(Rs2Potion.getDefencePotionsVariants().toArray(new String[0]));

            case RANGED:
                return usePotion(Rs2Potion.getRangePotionsVariants().toArray(new String[0]));

            case MAGIC:
                return usePotion(Rs2Potion.getMagicPotionsVariants().toArray(new String[0]));
            default:
                return false; // If the skill is not covered, return false
        }
    }

    /**
     * Drinks an anti-poison potion if the player does not have an active anti-poison effect.
     *
     * <p>If an anti-venom effect is active, no potion will be consumed.</p>
     *
     * @return {@code true} if an anti-poison potion was found and used, 
     *         or if the player already has an anti-venom effect, {@code false} otherwise.
     */
    public static boolean drinkAntiPoisonPotion() {
        if (!hasAntiPoisonActive() || hasAntiVenomActive()) {
            return true;
        }
        return usePotion(Rs2Potion.getAntiPoisonVariants().toArray(new String[0]));
    }

    /**
     * Drinks an anti-fire potion if the player does not have an active anti-fire effect.
     *
     * @return {@code true} if an anti-fire potion was found and used, 
     *         or if the player already has an active anti-fire effect, {@code false} otherwise.
     */
    public static boolean drinkAntiFirePotion() {
        if (hasAntiFireActive()) {
            return true;
        }
        return usePotion(Rs2Potion.getAntifirePotionsVariants().toArray(new String[0]));
    }

    /**
     * Drinks a goading potion if the player does not already have an active goading effect.
     *
     * @return {@code true} if a goading potion was found and used, 
     *         {@code false} if the effect is already active or no potion was available.
     */
    public static boolean drinkGoadingPotion() {
        if (hasGoadingActive()) {
            return false;
        }
        return usePotion(Rs2Potion.getGoadingPotion());
    }
    
    /**
     * Helper method to check for the presence of any item in the provided IDs and interact with it.
     *
     * @param itemIds Array of item IDs to check in the inventory.
     * @return true if an item was found and interacted with; false otherwise.
     */
    private static boolean usePotion(Integer ...itemIds) {
        Rs2ItemModel potion = Rs2Inventory.get(item ->
                !item.isNoted() && Arrays.stream(itemIds).anyMatch(id -> id == item.getId())
        );
        
        if (potion == null) return false;
        
        return Rs2Inventory.interact(potion, "drink");
    }

    /**
     * Checks for the presence of any item in the provided IDs within the inventory.
     *
     * @param itemIds Array of item IDs to check in the inventory.
     * @return true if an item matching the IDs exists; false otherwise.
     */
    private static boolean hasPotion(Integer... itemIds) {
        Rs2ItemModel potion = Rs2Inventory.get(item ->
                !item.isNoted() && Arrays.stream(itemIds).anyMatch(id -> id == item.getId())
        );
        
        return potion != null;
    }

    /**
     * Helper method to check for the presence of any item in the provided names and interact with it.
     *
     * @param itemNames Array of item names to check in the inventory.
     * @return true if an item was found and interacted with; false otherwise.
     */
    private static boolean usePotion(String... itemNames) {
        Pattern usesRegexPattern = Pattern.compile("^(.*?)(?:\\(\\d+\\))?$");

        Rs2ItemModel potion = Rs2Inventory.get(item -> {
            if (item.isNoted()) return false;

            Matcher matcher = usesRegexPattern.matcher(item.getName());
            if (matcher.find()) {
                String trimmedName = matcher.group(1).trim();
                return Arrays.stream(itemNames).anyMatch(name -> name.equalsIgnoreCase(trimmedName));
            }

            return false;
        });

        if (potion == null) return false;

        return Rs2Inventory.interact(potion, "drink");
    }
    
    /**
     * Checks for the presence of any item in the provided names within the inventory.
     *
     * @param itemNames Array of item names to check in the inventory.
     * @return true if an item matching the names exists; false otherwise.
     */
    private static boolean hasPotion(String... itemNames) {
        Pattern usesRegexPattern = Pattern.compile("^(.*?)(?:\\(\\d+\\))?$");

        Rs2ItemModel potion = Rs2Inventory.get(item -> {
            if (item.isNoted()) return false;

            Matcher matcher = usesRegexPattern.matcher(item.getName());
            if (matcher.find()) {
                String trimmedName = matcher.group(1).trim();
                return Arrays.stream(itemNames).anyMatch(name -> name.equalsIgnoreCase(trimmedName));
            }

            return false;
        });
        
        return potion != null;
    }

    /**
     * Checks if the player has any remaining prayer points.
     *
     * @return {@code true} if the player's boosted prayer level is greater than zero, {@code false} otherwise.
     */
    public static boolean hasPrayerPoints() {
        return Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > 0;
    }

    /**
     * Calculates the player's current prayer level as a percentage of their base prayer level.
     *
     * @return a value between 0 and 100 representing the percentage of prayer remaining.
     */
    public static int getPrayerPercentage() {
        int current = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        int base = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);

        return (int) ((current / (double) base) * 100);
    }

    /**
     * Checks if the player is currently standing on a game object.
     *
     * @return {@code true} if a game object exists at the player's current location, {@code false} otherwise.
     */
    public static boolean isStandingOnGameObject() {
        WorldPoint playerPoint = getWorldLocation();
        return Rs2GameObject.getGameObject(playerPoint) != null
                && isStandingOnGroundItem();
    }

    /**
     * Checks if the player is currently standing on a ground item.
     *
     * @return {@code true} if there are any ground items at the player's current location, {@code false} otherwise.
     */
    public static boolean isStandingOnGroundItem() {
        WorldPoint playerPoint = getWorldLocation();
        return Rs2GroundItem.getGroundItems().values().stream().anyMatch(x -> x.getLocation().equals(playerPoint));
    }

    /**
     * Retrieves the player's current animation ID.
     *
     * @return The animation ID of the player's current action, or {@code -1} if the player is null.
     */
    public static int getAnimation() {
        if (Microbot.getClient().getLocalPlayer() == null) return -1;
        return Microbot.getClient().getLocalPlayer().getAnimation();
    }

    /**
     * Retrieves the player's current pose animation ID.
     *
     * @return The pose animation ID of the player.
     */
    public static int getPoseAnimation() {
        return Microbot.getClient().getLocalPlayer().getPoseAnimation();
    }

    /**
     * Retrieves the current state of a specified quest.
     *
     * @param quest The {@link Quest} to check.
     * @return The {@link QuestState} representing the player's progress in the quest.
     */
    public static QuestState getQuestState(Quest quest) {
        Client client = Microbot.getClient();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> quest.getState(client)).orElse(null);
    }

    /**
     * Retrieves the player's real (unboosted) level for a given skill.
     *
     * @param skill The {@link Skill} to check.
     * @return The player's real level for the specified skill.
     */
    public static int getRealSkillLevel(Skill skill) {
        return Microbot.getClient().getRealSkillLevel(skill);
    }

    /**
     * Retrieves the player's current boosted level for a given skill.
     *
     * @param skill The {@link Skill} to check.
     * @return The player's boosted level for the specified skill.
     */
    public static int getBoostedSkillLevel(Skill skill) {
        return Microbot.getClient().getBoostedSkillLevel(skill);
    }

    /**
     * Checks if the player meets the required level for a given skill.
     *
     * <p>The method allows checking against either the real skill level or the boosted skill level.</p>
     *
     * @param skill        The {@link Skill} to check.
     * @param levelRequired The required level for the skill.
     * @param isBoosted    {@code true} to check against the boosted skill level, {@code false} to check against the real skill level.
     * @return {@code true} if the player meets or exceeds the required level, {@code false} otherwise.
     */
    public static boolean getSkillRequirement(Skill skill, int levelRequired, boolean isBoosted) {
        if (isBoosted) return getBoostedSkillLevel(skill) >= levelRequired;
        return getRealSkillLevel(skill) >= levelRequired;
    }

    /**
     * Checks if the player meets the required real level for a given skill.
     *
     * <p>This method is a shorthand for {@code getSkillRequirement(skill, levelRequired, false)}.</p>
     *
     * @param skill        The {@link Skill} to check.
     * @param levelRequired The required level for the skill.
     * @return {@code true} if the player's real skill level meets or exceeds the required level, {@code false} otherwise.
     */
    public static boolean getSkillRequirement(Skill skill, int levelRequired) {
        return getSkillRequirement(skill, levelRequired, false);
    }

    /**
     * Checks if the player is an Ironman or Hardcore Ironman.
     *
     * <p>Account types are determined based on the {@link Varbits#ACCOUNT_TYPE} value:</p>
     * <ul>
     *     <li>1 - Ironman</li>
     *     <li>2 - Hardcore Ironman</li>
     *     <li>3 - Ultimate Ironman</li>
     * </ul>
     *
     * @return {@code true} if the player is an Ironman, Hardcore Ironman, or Ultimate Ironman, {@code false} otherwise.
     */
    public static boolean isIronman() {
        int accountType = Microbot.getVarbitValue(Varbits.ACCOUNT_TYPE);
        return accountType > 0 && accountType <= 3;
    }

    /**
     * Checks if the player is a Group Ironman.
     *
     * <p>Group Ironman account types are determined based on the {@link Varbits#ACCOUNT_TYPE} value:</p>
     * <ul>
     *     <li>4 - Group Ironman</li>
     *     <li>5 - Hardcore Group Ironman</li>
     * </ul>
     *
     * @return {@code true} if the player is a Group Ironman or Hardcore Group Ironman, {@code false} otherwise.
     */
    public static boolean isGroupIronman() {
        int accountType = Microbot.getVarbitValue(Varbits.ACCOUNT_TYPE);
        return accountType >= 4;
    }

    /**
     * Retrieves the player's current world.
     *
     * @return The world number the player is currently in.
     */
    public static int getWorld() {
        return Microbot.getClient().getWorld();
    }

    /**
     * Calculates the distance from the player's current location to a specified endpoint.
     *
     * <p><b>Note:</b> This method uses the ShortestPath algorithm and does not work in instanced regions.
     * If the player is in an instance, it falls back to a direct {@link WorldPoint#distanceTo(WorldPoint)} calculation.</p>
     *
     * @param endpoint The target {@link WorldPoint} to measure the distance to.
     * @return The distance between the player's current location and the endpoint.
     */
    public static int distanceTo(WorldPoint endpoint) {
        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
            return getWorldLocation().distanceTo(endpoint);
        }
        return Rs2Walker.getDistanceBetween(getWorldLocation(), endpoint);
    }

    /**
     * Checks if the player is at risk of logging out due to inactivity.
     *
     * <p>This method determines the lowest idle time between mouse and keyboard activity
     * and compares it to the client's idle timeout, factoring in a specified random delay.</p>
     *
     * @param randomDelay The time (in ticks) to subtract from the client's idle timeout 
     *                    to introduce variability in detecting inactivity.
     * @return {@code true} if the player's idle time exceeds or equals the adjusted timeout, {@code false} otherwise.
     */
    public static boolean checkIdleLogout(long randomDelay) {
        long idleClientTicks = Long.min(
                Microbot.getClient().getMouseIdleTicks(),
                Microbot.getClient().getKeyboardIdleTicks()
        );

        return idleClientTicks >= Microbot.getClient().getIdleTimeout() - randomDelay;
    }

    /**
     * Checks whether the player is in a cave.
     *
     * <p>A player is considered to be in a cave if their {@link WorldPoint#getY()} coordinate
     * is 6400 or higher and they are not in an instanced world.</p>
     *
     * @return {@code true} if the player is inside a cave, {@code false} otherwise.
     */
    public static boolean isInCave() {
        return Rs2Player.getWorldLocation().getY() >= 6400
                && !Microbot.getClient().getTopLevelWorldView().isInstance();
    }
    
    /**
     * Checks whether the player is currently in an instanced world.
     *
     * @return {@code true} if the player is in an instanced world, {@code false} otherwise.
     */
    public static boolean IsInInstance() {
        return Microbot.getClient().getTopLevelWorldView().getScene().isInstance();
    }

    /**
     * Retrieves the player's current run energy as a percentage.
     *
     * <p>Run energy is stored in the client as an integer value (e.g., 7500 for 75.00%).
     * This method converts it to a whole number percentage.</p>
     *
     * @return The player's run energy as an integer percentage (0-100).
     */
    public static int getRunEnergy() {
        return Microbot.getClient().getEnergy() / 100;
    }

    /**
     * Checks if the player has an active stamina effect.
     *
     * <p>A stamina effect reduces the rate at which run energy depletes.
     * This method checks the {@link Varbits#RUN_SLOWED_DEPLETION_ACTIVE} value.</p>
     *
     * @return {@code true} if the stamina effect is active, {@code false} otherwise.
     */
    public static boolean hasStaminaActive() {
        return Microbot.getVarbitValue(Varbits.RUN_SLOWED_DEPLETION_ACTIVE) != 0;
    }

    /**
     * Retrieves the current graphic ID of the local player.
     *
     * <p>This ID represents the graphical effect currently applied to the player, 
     * such as spell casts or special attack animations.</p>
     *
     * @return The graphic ID of the local player.
     */
    public static int getGraphicId() {
        return Microbot.getClient().getLocalPlayer().getGraphic();
    }

    /**
     * Checks if the local player has a specific spot animation active.
     *
     * <p>Spot animations (also known as graphics IDs) are special visual effects 
     * applied to the player, such as teleportation effects or status conditions.</p>
     *
     * @param graphicId The graphic ID of the spot animation to check. See {@link GraphicID} for predefined values.
     * @return {@code true} if the local player has the specified spot animation, {@code false} otherwise.
     */
    public static boolean hasSpotAnimation(int graphicId) {
        return Microbot.getClient().getLocalPlayer().hasSpotAnim(graphicId);
    }

    /**
     * Checks if the local player is currently stunned.
     *
     * <p>A player is considered stunned if they have the spot animation with graphic ID {@code 245} active.</p>
     *
     * @return {@code true} if the player is stunned, {@code false} otherwise.
     */
    public static boolean isStunned() {
        return hasSpotAnimation(245);
    }

    /**
     * Invokes the "attack" action on the specified player.
     *
     * <p>This method interacts with the specified {@link Rs2PlayerModel} to initiate an attack.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player to attack.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     */
    public static boolean attack(Rs2PlayerModel rs2Player) {
        return invokeMenu(rs2Player, "attack");
    }

    /**
     * Invokes the "attack" action on the specified player.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel} before invoking the attack action.</p>
     *
     * @param player The {@link Player} to attack.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #attack(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean attack(Player player) {
        return attack(new Rs2PlayerModel(player));
    }

    /**
     * Invokes the "walk here" action to move to the same location as the specified player.
     *
     * <p>This method interacts with the specified {@link Rs2PlayerModel} to initiate movement to their position.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player under whose position to walk.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     */
    public static boolean walkUnder(Rs2PlayerModel rs2Player) {
        return invokeMenu(rs2Player, "walk here");
    }

    /**
     * Invokes the "walk here" action to move to the same location as the specified player.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel} before invoking the movement action.</p>
     *
     * @param player The {@link Player} under whose position to walk.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #walkUnder(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean walkUnder(Player player) {
        return walkUnder(new Rs2PlayerModel(player));
    }

    /**
     * Invokes the "trade with" action on the specified player.
     *
     * <p>This method interacts with the specified {@link Rs2PlayerModel} to initiate a trade.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player to trade with.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     */
    public static boolean trade(Rs2PlayerModel rs2Player) {
        return invokeMenu(rs2Player, "trade with");
    }

    /**
     * Invokes the "trade with" action on the specified player.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel} before invoking the trade action.</p>
     *
     * @param player The {@link Player} to trade with.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #trade(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean trade(Player player) {
        return trade(new Rs2PlayerModel(player));
    }

    /**
     * Invokes the "follow" action on the specified player.
     *
     * <p>This method interacts with the specified {@link Rs2PlayerModel} to initiate following them.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} representing the player to follow.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     */
    public static boolean follow(Rs2PlayerModel rs2Player) {
        return invokeMenu(rs2Player, "follow");
    }

    /**
     * Invokes the "follow" action on the specified player.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel} before invoking the follow action.</p>
     *
     * @param player The {@link Player} to follow.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #follow(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean follow(Player player) {
        return follow(new Rs2PlayerModel(player));
    }

    /**
     * Invokes the "cast" action on the specified player.
     *
     * <p>This method interacts with the specified {@link Rs2PlayerModel} to cast a spell or ability on them.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} to cast on.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     */
    public static boolean cast(Rs2PlayerModel rs2Player) {
        return invokeMenu(rs2Player, "cast");
    }

    /**
     * Invokes the "cast" action on the specified player.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel} before invoking the cast action.</p>
     *
     * @param player The {@link Player} to cast on.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #cast(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean cast(Player player) {
        return cast(new Rs2PlayerModel(player));
    }

    /**
     * Selects the "USE" option on a player for an item that is already selected via {@code Rs2Inventory.use(item)}.
     *
     * <p>This method interacts with the specified {@link Rs2PlayerModel} to use the selected item on them.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} to use the item on.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     */
    public static boolean use(Rs2PlayerModel rs2Player) {
        return invokeMenu(rs2Player, "use");
    }

    /**
     * Selects the "USE" option on a player for an item that is already selected via {@code Rs2Inventory.use(item)}.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel} before invoking the use action.</p>
     *
     * @param player The {@link Player} to use the item on.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #use(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean use(Player player) {
        return use(new Rs2PlayerModel(player));
    }
    
    /**
     * Selects the "CHALLENGE" option on a player for Soul Wars.
     *
     * <p>This method interacts with the specified {@link Rs2PlayerModel} to issue a challenge.</p>
     *
     * @param rs2Player The {@link Rs2PlayerModel} to challenge.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     */
    public static boolean challenge(Rs2PlayerModel rs2Player) {
        return invokeMenu(rs2Player, "challenge");
    }

    /**
     * Selects the "CHALLENGE" option on a player for Soul Wars.
     *
     * <p>This method converts a {@link Player} object into an {@link Rs2PlayerModel} before invoking the challenge action.</p>
     *
     * @param player The {@link Player} to challenge.
     * @return {@code true} if the action was invoked successfully, {@code false} otherwise.
     * @deprecated Since 1.7.2, use {@link #challenge(Rs2PlayerModel)} for consistency and improved type handling.
     */
    @Deprecated(since = "1.7.2", forRemoval = true)
    public static boolean challenge(Player player) {
        return challenge(new Rs2PlayerModel(player));
    }

    /**
     * Executes a specific menu action on a given player.
     *
     * @param rs2Player the player to interact with
     * @param action the action to invoke (e.g., "attack", "walk here", "trade with", "follow")
     * @return true if the action was invoked successfully, false otherwise
     */

    private static boolean invokeMenu(Rs2PlayerModel rs2Player, String action) {
        if (rs2Player == null) return false;

        // Set the current status for the action being performed
        Microbot.status = action + " " + rs2Player.getName();

        // Determine the appropriate menu action based on the action string
        MenuAction menuAction = MenuAction.CC_OP;

        if(action.equalsIgnoreCase("attack")) {
            menuAction = MenuAction.PLAYER_SECOND_OPTION;
        } else if (action.equalsIgnoreCase("walk here")) {
            menuAction = MenuAction.WALK;
        } else if (action.equalsIgnoreCase("follow")) {
            menuAction = MenuAction.PLAYER_THIRD_OPTION;
        } else if (action.equalsIgnoreCase("challenge")) {
            menuAction = MenuAction.PLAYER_FIRST_OPTION;
        } else if (action.equalsIgnoreCase("trade with")) {
            menuAction = MenuAction.PLAYER_FOURTH_OPTION;
        } else if (action.equalsIgnoreCase("cast")) {
            menuAction = MenuAction.WIDGET_TARGET_ON_PLAYER;
        }else if (action.equalsIgnoreCase("use")) {
            menuAction = MenuAction.WIDGET_TARGET_ON_PLAYER;
        }

        // Invoke the menu entry using the selected action
        Microbot.doInvoke(
                new NewMenuEntry(0, 0, menuAction.getId(), rs2Player.getId(), -1, rs2Player.getName(), rs2Player),
                Rs2UiHelper.getActorClickbox(rs2Player)
        );

        return true;
    }

    /**
     * Retrieves the actor that the local player is currently interacting with.
     *
     * @return The interacting actor as an {@link Actor} object. If the interacting actor is an NPC,
     *         it returns an {@link Rs2NpcModel} object. If the local player is not interacting with anyone,
     *         or if the local player is null, it returns null.
     */
    public static Actor getInteracting() {
        Optional<Actor> result = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) return null;

            var interactingActor = Microbot.getClient().getLocalPlayer().getInteracting();

            if (interactingActor instanceof net.runelite.api.NPC) {
                return new Rs2NpcModel((NPC) interactingActor);
            }

            return interactingActor;
        });

        return result.orElse(null);
    }
    /**
     * Checks if the player has finished Tutorial Island.
     *
     * <p>This method checks the player's progress on Tutorial Island by retrieving the value of Varbit 281.
     * If the value is greater than or equal to 1000, it indicates that the player has completed Tutorial Island.</p>
     *
     * @return {@code true} if the player has finished Tutorial Island, {@code false} otherwise.
     */
    public static boolean isInTutorialIsland() {
        return Microbot.getVarbitPlayerValue(281) >= 1000;
    }

    /**
     * Checks if there is any fast food available in the player's inventory.
     *
     * <p>Fast food is defined as food with a {@code tickDelay} of 1 in {@link Rs2Food}.</p>
     * <p>Noted items are ignored.</p>
     *
     * @return {@code true} if at least one fast food item is found, {@code false} otherwise.
     */
    public static boolean fastFoodPresent() {
        List<Rs2ItemModel> foods = Rs2Inventory.getInventoryFood();
        if (foods.isEmpty()) return false;

        return foods.stream()
                .filter(rs2Item -> !rs2Item.isNoted())
                .filter(rs2Item -> Rs2Food.getIds().contains(rs2Item.getId()))
                .anyMatch(rs2Item -> {
                    for (Rs2Food food : Rs2Food.values()) {
                        if (food.getId() == rs2Item.getId() && food.getTickdelay() == 1) {
                            return true;
                        }
                    }
                    return false;
                });
    }
}
