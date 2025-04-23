package net.runelite.client.plugins.microbot.bee.MossKiller;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.CombatMode;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.*;

import static net.runelite.api.EquipmentInventorySlot.WEAPON;
import static net.runelite.api.GraphicID.SNARE;
import static net.runelite.api.GraphicID.SPLASH;
import static net.runelite.api.HeadIcon.*;
import static net.runelite.api.ItemID.RUNE_SCIMITAR;

@PluginDescriptor(
        name = PluginDescriptor.Bee + "Moss Killer",
        description = "Bee & Mntn's Moss Killer",
        tags = {"Keys", "Bryophyta", "Mntn", "Bee", "Moss Giants", "F2p"},
        enabledByDefault = false
)
@Slf4j
public class MossKillerPlugin extends Plugin {
    @Inject
    private MossKillerConfig config;

    @Provides
    MossKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MossKillerConfig.class);
    }

    @Inject
    private Client client;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private MossKillerOverlay mossKillerOverlay;
    @Inject
    private WildyKillerScript wildyKillerScript;
    @Inject
    private WildySaferScript wildySaferScript;
    @Inject
    private MossKillerScript exampleScript;
    private boolean worldHopFlag = false;
    @Getter
    private int deathCounter = 0;
    private boolean isDeathProcessed = false;

    private boolean isTeleblocked = false;
    private boolean lobsterEaten = false;
    private int tickCount = 0;

    private final Map<Rs2PlayerModel, Integer> attackerTickMap = new HashMap<>();
    private static final int MIN_TICKS_TO_TRACK = 2;
    private static final String MOSS_GIANT_NAME = "Moss Giant";

    private static boolean isSnared = false;
    private int snareTickCounter = 0;


    public static WorldPoint bryoTile = null; // Stores the southwest tile when Bryophyta dies

    private boolean useWindBlast = false;
    private boolean useMelee = false;
    private boolean useRange = false;


    private int consecutiveHitsplatsMain = 0;
    private int consecutiveHitsplatsSafeSpot1 = 0;
    private long lastHitsplatTimeMain = 0;
    private long lastHitsplatTimeSafeSpot1 = 0;
    private static final long CONSECUTIVE_HITSPLAT_TIMEOUT = 3000; //

    private boolean runeScimitar = false;

    private final Object targetLock = new Object();
    public Rs2PlayerModel currentTarget = null;

    private boolean hideOverlay;

    @Getter
    private boolean defensive;

    @Getter
    private boolean superNullTarget = false;

    private boolean isJammed;

    private boolean hitsplatIsTheirs = false;

    private int hitsplatSetTick = -1; // Initialize to an invalid tick value

    // Tracks the nearby player condition
    private boolean isPlayerNearby = false;


    @Override
    protected void startUp() throws AWTException, PluginInstantiationException {
        if (overlayManager != null) {
            overlayManager.add(mossKillerOverlay);
        }
        Microbot.useStaminaPotsIfNeeded = false;
        Microbot.enableAutoRunOn = false;
        hideOverlay = config.isHideOverlay();
        toggleOverlay(hideOverlay);
        if (!config.wildy() && !config.wildySafer()) {
            exampleScript.run(config);
        }
        if (config.wildy() && !config.wildySafer()) {
            wildyKillerScript.run(config);
            wildyKillerScript.handleAsynchWalk("Start-up");
        }
        if (config.wildySafer() && !config.wildy()) {
            wildySaferScript.run(config);
            System.out.println("running wildy safer script");
        }
    }

    public Rs2PlayerModel getCurrentTarget() {
        synchronized (targetLock) {
            return currentTarget;
        }
    }

    public void setCurrentTarget(Rs2PlayerModel target) {
        synchronized (targetLock) {
            currentTarget = target;
            System.out.println("Target set to: " + (target != null ? target.getName() : "null"));
        }
    }

    @Subscribe
    public void onConfigChanged(final ConfigChanged event) {

        if (!event.getGroup().equals(MossKillerConfig.configGroup)) {
            return;
        }

        if (event.getKey().equals(MossKillerConfig.hideOverlay)) {
            hideOverlay = config.isHideOverlay();
            toggleOverlay(hideOverlay);
        }

    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (config.wildy()) {
            if (event.getVarbitId() == Varbits.TELEBLOCK) {
                int teleblockValue = event.getValue(); // Get the current value of the teleblock varbit
                isTeleblocked = teleblockValue > 100;
            }
        }
    }

    public Boolean getAttackStyle() {
        // Check current attack style
        int attackStyle = Microbot.getClient().getVarpValue(VarPlayer.ATTACK_STYLE);

        if (attackStyle == 4) {
            return true; // Return true if attackStyle is exactly 4
        } else if (attackStyle < 4) {
            return false; // Return false if attackStyle is less than 4
        }

        return null; // Optional: Return null for unexpected cases
    }

    private void toggleOverlay(boolean hideOverlay) {
        if (overlayManager != null) {
            boolean hasOverlay = overlayManager.anyMatch(ov -> ov.getName().equalsIgnoreCase(MossKillerOverlay.class.getSimpleName()));

            if (hideOverlay) {
                if (!hasOverlay) return;

                overlayManager.remove(mossKillerOverlay);
            } else {
                if (hasOverlay) return;

                overlayManager.add(mossKillerOverlay);
            }
        }
    }

    public void resetTarget() {
        currentTarget = null;
    }

    public boolean hasRuneScimitar() {
        return runeScimitar;
    }

    public boolean isTeleblocked() {
        return isTeleblocked;
    }

    public boolean shouldHopWorld() {
        return worldHopFlag;
    }

    public boolean useWindBlast() {
        return useWindBlast;
    }

    public boolean useMelee() {
        return useMelee;
    }

    public boolean useRange() {
        return useRange;
    }

    public boolean playerJammed() {
        return isJammed;
    }

    public void resetWorldHopFlag() {
        worldHopFlag = false;
    }

    public void targetPrayers() {
        if (currentTarget != null) {

            // Check and act based on their prayer overhead
            if (currentTarget.getOverheadIcon() == RANGED) {
                if (isSnared) {
                    useWindBlast = true; // Use wind blast if snared
                    useMelee = false;    // Don't use melee
                } else {
                    useWindBlast = false;
                    useMelee = true; // Use melee if not snared
                }
                useRange = false; // No ranged in this case

            } else if (currentTarget.getOverheadIcon() == MELEE) {
                useRange = true;  // Use ranged attacks
                useWindBlast = false;
                useMelee = false;

            } else if (currentTarget.getOverheadIcon() == MAGIC) {
                if (isSnared) {
                    useRange = true;  // Use ranged if snared
                    useMelee = false; // No melee if snared
                } else {
                    useRange = false;
                    useMelee = true; // Use melee if not snared
                }
                useWindBlast = false; // No wind blast in this case
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (config.wildy()) {

            checkNearbyPlayers();

            if (hitsplatIsTheirs && hitsplatSetTick != -1) {
                System.out.println("hitsplat counter is more than -1");
                if (client.getTickCount() - hitsplatSetTick >= 4) {
                    hitsplatIsTheirs = false; // Reset the flag
                    hitsplatSetTick = -1;     // Reset the tracker
                }
            }

            if (client.getLocalPlayer() != null) {
                int currentHp = client.getLocalPlayer().getHealthRatio();

                // Player has 0 HP, and this death has not been processed yet
                if (currentHp == 0 && !isDeathProcessed) {
                    worldHopFlag = true; // Notify the script to hop worlds
                    deathCounter++; // Increment the death counter
                    isDeathProcessed = true; // Mark this death as processed
                }

                // Reset the death processed flag when the player's HP is greater than 0
                if (currentHp > 0) {
                    isDeathProcessed = false;
                }
            }

            targetPrayers();

            runeScimitar = Rs2Equipment.isEquipped(RUNE_SCIMITAR, WEAPON);

            if (isSnared) {
                snareTickCounter++;
                if (snareTickCounter >= 16) {  // Reset after 16 ticks
                    isSnared = false;
                    snareTickCounter = 0;
                    System.out.println("Snare effect ended.");
                }
            }

            int health = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS); // Assuming Rs2Player has a method to get health

            if (health == 0) {
                stopWalking();
            }

            if (lobsterEaten) {
                tickCount++;

                if (tickCount >= 3) {
                    lobsterEaten = false; // Reset the boolean after 3 ticks
                    tickCount = 0; // Reset the counter
                    System.out.println("3 ticks elapsed. eating attack delay over.");
                    // Set your script's boolean or trigger action here
                }
            }

            if (config.combatMode() == CombatMode.FIGHT) {
                trackAttackers();
            }

            int attackStyle = client.getVarpValue(VarPlayer.ATTACK_STYLE);
            defensive = attackStyle == 3;

            if (currentTarget == null) {
                tickCount++;

                if (tickCount >= 9) {
                    superNullTarget = true;
                    //System.out.println("9 ticks elapsed. target probably properly gone.");
                }
            } else {
                superNullTarget = false;
            }

            if (!Rs2Player.isMoving()) {
                tickCount++;

                if (tickCount >= 50) {
                    isJammed = true;
                }

            } else {
                tickCount = 0;
                isJammed = false;
            }
        }

        if (!config.wildy() && !config.wildySafer()) {
            NPC bryophyta = findBryophyta();

            // Check if Bryophyta's HP is 0
            if (bryophyta != null && bryophyta.getHealthRatio() == 0) {
                bryoTile = getBryophytaWorldLocation(bryophyta);
            }
        }
    }

    public NPC findBryophyta() {
        return client.getNpcs().stream()
                .filter(npc -> npc.getName() != null && npc.getName().equalsIgnoreCase("Bryophyta"))
                .findFirst()
                .orElse(null);
    }

    private WorldPoint getBryophytaWorldLocation(NPC bryophyta) {
        if (bryophyta == null) {
            return null;
        }

        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
            LocalPoint l = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), bryophyta.getWorldLocation());
            return WorldPoint.fromLocalInstance(Microbot.getClient(), l);
        } else {
            return bryophyta.getWorldLocation();
        }
    }

    private void trackAttackers() {
        Player localPlayer = Microbot.getClient().getLocalPlayer();

        // Check if a Moss Giant is interacting with the player
        Actor interactingActor = localPlayer.getInteracting();
        if (isMossGiant(interactingActor)) {
            resetAttackers();
            resetTarget(); // Reset target if Moss Giant interacts
            return;
        }

        // First, check if current target is still valid, to avoid reassigning
        if (currentTarget != null) {
            boolean targetInList = false;
            for (Rs2PlayerModel player : Rs2Player.getPlayersInCombatLevelRange()) {
                if (Objects.equals(player.getName(), currentTarget.getName())) {
                    targetInList = true;
                    currentTarget = player; // Update target reference to current instance
                    break;
                }
            }

            // If target is above threshold combat level, only reset if dead or not in list
            if (isAboveCombatThreshold(currentTarget)) {
                if (currentTarget.isDead() || !targetInList) {
                    System.out.println("High-level target is dead or not found, resetting target");
                    resetTarget();
                } else {
                    System.out.println("Maintaining high-level target: " + currentTarget.getName());
                    return; // Skip the rest of the tracking logic - maintain current target
                }
            } else if (currentTarget.isDead() || !targetInList) {
                System.out.println("Target is dead or not found, resetting target");
                resetTarget();
            }
        }

        List<Rs2PlayerModel> potentialTargets = Rs2Player.getPlayersInCombatLevelRange();

        // Update attackers map based on interactions
        for (Rs2PlayerModel player : potentialTargets) {
            System.out.println("Checking player: " + player.getName() + ", interacting with local: " + (player.getInteracting() == localPlayer));

            if (player.getInteracting() == localPlayer && !isNonCombatAnimation(player) && hitsplatIsTheirs()) {
                if ((Microbot.getVarbitPlayerValue(1075) == -1
                        && WildyKillerScript.CORRIDOR.contains(Rs2Player.getWorldLocation()))
                        || (Microbot.getVarbitPlayerValue(1075) != -1
                        && !WildyKillerScript.CORRIDOR.contains(Rs2Player.getWorldLocation()))) {

                    int currentCount = attackerTickMap.getOrDefault(player, 0);
                    attackerTickMap.put(player, currentCount + 1);
                    System.out.println("Player " + player.getName() + " tick count increased to: " + (currentCount + 1));
                }
            }
        }

        // Create a copy of the entry set to avoid concurrent modification
        List<Map.Entry<Rs2PlayerModel, Integer>> entries = new ArrayList<>(attackerTickMap.entrySet());

        // Process each entry - similar to your original logic
        for (Map.Entry<Rs2PlayerModel, Integer> entry : entries) {
            Rs2PlayerModel player = entry.getKey();
            int tickCount = entry.getValue();

            System.out.println("Processing " + player.getName() + " with count " + tickCount);

            // Follow your original logic closely
            // Increment tick count if the player is interacting and performing combat animation
            if (player.getInteracting() == localPlayer && !isNonCombatAnimation(player)) {
                tickCount += 1;
                attackerTickMap.put(player, tickCount);
                System.out.println(player.getName() + " in combat with us, ticks now: " + tickCount);
            }

            // Increment tick count if the player is interacting and their hitsplat is applied to you
            if (player.getInteracting() == localPlayer && hitsplatIsTheirs()) {
                tickCount += 1;
                attackerTickMap.put(player, tickCount);
                System.out.println(player.getName() + " hitsplat applied, ticks now: " + tickCount);
            }

            // If the player is no longer interacting, decrease their tick count - keeping your original formula
            if (player.getInteracting() != localPlayer && player.getAnimation() != 829) {
                tickCount = Math.max(0, tickCount - 2);
                attackerTickMap.put(player, tickCount);
                System.out.println(player.getName() + " no longer interacting, ticks now: " + tickCount);
            }

            // If player no longer interacting and doing non-combat anim
            if (player.getInteracting() != localPlayer && isNonCombatAnimation(player) && player.getAnimation() != 829) {
                tickCount = Math.max(0, tickCount - 2);
                attackerTickMap.put(player, tickCount);
                System.out.println(player.getName() + " non-combat animation, ticks now: " + tickCount);
            }

            System.out.println("Final tick count for " + player.getName() + ": " + tickCount);

            // If tick count is >= MIN_TICKS_TO_TRACK, they become your target
            if (tickCount >= MIN_TICKS_TO_TRACK) {
                currentTarget = player;
                System.out.println("Setting target to: " + player.getName() + " with ticks: " + tickCount);
                break;
            }

            // If tick count is 0, remove player from the map
            if (tickCount == 0) {
                attackerTickMap.remove(player);
                System.out.println("Removing " + player.getName() + " from map due to 0 ticks");
                if (currentTarget == player) {
                    resetTarget();
                    System.out.println("Resetting target since it was " + player.getName());
                }
            }
        }

        // Check if current target is valid
        if (currentTarget != null) {
            boolean targetInList = false;
            for (Rs2PlayerModel player : Rs2Player.getPlayersInCombatLevelRange()) {
                if (Objects.equals(player.getName(), currentTarget.getName())) {
                    targetInList = true;
                    break;
                }
            }

            if (currentTarget.isDead() || !targetInList) {
                System.out.println("Target is dead or not found, resetting target");
                resetTarget();
            } else {
                System.out.println("Current target remains: " + currentTarget.getName());
            }
        }
    }

    // Add this method to check if a player is above the combat threshold
    private boolean isAboveCombatThreshold(Rs2PlayerModel player) {
        if (player == null) return false;

        // Define your combat threshold - adjust this value as needed
        final int COMBAT_THRESHOLD = 87; // Example threshold

        return player.getCombatLevel() > COMBAT_THRESHOLD;
    }

    /**
     * Determines if a player is performing a non-combat animation (walking/running).
     */
    private boolean isNonCombatAnimation(Player player) {
        int animationId = player.getAnimation();
        //walking, running, or doing defense animations
        return animationId == -1 || animationId == 1156 || animationId == 403 || animationId == 420 || animationId == 410;
    }


    public boolean hitsplatIsTheirs() {
        return hitsplatIsTheirs;
    }

    private void checkNearbyPlayers() {
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) {
            isPlayerNearby = false;
            return;
        }

        isPlayerNearby = client.getPlayers().stream()
                .anyMatch(player -> player != null
                        && player != localPlayer // Exclude yourself
                        && player.getWorldLocation().distanceTo(localPlayer.getWorldLocation()) <= 10); // Adjust distance
    }


    private boolean isMossGiant(Actor actor) {
        return actor instanceof NPC && Objects.requireNonNull(((NPC) actor).getName()).equalsIgnoreCase(MOSS_GIANT_NAME);
    }

    private void resetAttackers() {
        attackerTickMap.clear();
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {

        if (config.wildy()) {
            if (event.getMessage().equals("You eat the swordfish.")) {
                lobsterEaten = true;
                tickCount = 0; // Reset the tick counter
            }

            if (currentTarget != null) {
                wildyKillerScript.isTargetOutOfReach = event.getMessage().equals("I can't reach that.");
            } else {
                wildyKillerScript.isTargetOutOfReach = false; // Explicitly set to false when no target
            }
        }

    }

    /**
     * Map to track recent hitsplats and their timestamps for each player.
     */
    private final Map<Player, Integer> recentHitsplats = new HashMap<>();

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        Actor target = event.getActor();
        Hitsplat hitsplat = event.getHitsplat();

        if (config.wildySafer()) {
            // SafeSpot logic
            if (wildySaferScript.isAtSafeSpot()) {
                if (target == client.getLocalPlayer()) {
                    long currentTime = System.currentTimeMillis();

                    // Debug to see what's happening
                    System.out.println("Hit registered at safespot, count: " + consecutiveHitsplatsMain);
                            System.out.println("*** SETTING MOVE TO TRUE ***");
                            wildySaferScript.move = true;
                            consecutiveHitsplatsMain = 0; // Reset counter

                        // Update the last hitsplat time
                        lastHitsplatTimeMain = currentTime;
                    }

                if (target == client.getLocalPlayer()){
                    if (wildySaferScript.iveMoved && wildySaferScript.isAtSafeSpot1()) {
                            System.out.println("*** SETTING SAFESPOT1ATTACK TO TRUE ***");
                            wildySaferScript.safeSpot1Attack = true;
                        System.out.println("*** you've been hit while at safespot1 ***");
                        }
                    }

                }
                }

        if(config.wildy()) {

        if (currentTarget != null) {
            wildyKillerScript.hitsplatApplied = event.getHitsplat().isMine();
        }


        if (target == Microbot.getClient().getLocalPlayer()) {
            if (hitsplat.getHitsplatType() == HitsplatID.BLOCK_ME || hitsplat.getHitsplatType() == HitsplatID.DAMAGE_ME) {
                //System.out.println("registered a hit");
                WorldView worldView = client.getWorldView(-1); // or getTopLevelWorldView()
                if (worldView != null && worldView.players() != null && worldView.players().iterator().hasNext()) {
                    int playerCount = 0;
                    for (Player player : worldView.players()) {
                        if (player != client.getLocalPlayer()) {
                            playerCount++;
                        }
                    }

                    if (playerCount > 0) {
                        // There are players other than the local player
                        System.out.println("There are other players in the world view.");
                        for (Player player : worldView.players()) {
                            System.out.println("there is a player nearby");
                            System.out.println("is doing combat animation " + (!isNonCombatAnimation(player)));
                            System.out.println("Interacting with me he is " + (player.getInteracting() == client.getLocalPlayer()));
                            if (player.getInteracting() == client.getLocalPlayer() && !isNonCombatAnimation (player)) {
                                Microbot.log("Someone is interacting with me while doing a combat animation");
                                recentHitsplats.put(player, client.getTickCount());
                                hitsplatIsTheirs = true;
                                hitsplatSetTick = client.getTickCount(); // Record the tick when the flag is set
                            }
                        }
                    }
                }

            }
        }
    }
    }
    /**
     * Determines which player caused the hitsplat based on interaction and proximity.
     */
    private Player getAttackerForHitsplat(Rs2PlayerModel localPlayer) {
        for (Rs2PlayerModel player : Rs2Player.getPlayersInCombatLevelRange()) {
            if (player.getInteracting() == localPlayer && !isNonCombatAnimation(player)) {
                ;
                return player;
            }
        }
        return null;
    }


    public boolean lobsterEaten() {
        return lobsterEaten;
    }


    private void stopWalking() {
        Rs2Walker.setTarget(null); // Stops the player from walking
        System.out.println("Walking stopped due to zero health.");
    }


    public void resetTeleblock() {
        isTeleblocked = false; // Reset when needed
    }

    @Subscribe
    public void onGraphicChanged(GraphicChanged event) {
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer != null && localPlayer.hasSpotAnim(SNARE)) {
            handleSnare();  // Method to handle being snared
        }
        if (localPlayer != null && localPlayer.hasSpotAnim(SPLASH)) {
            hitsplatIsTheirs = true;  // Method to handle being snared
        }
    }

    private void handleSnare() {
        isSnared = true;
        snareTickCounter = 0;  // Reset the counter when snared
        System.out.println("Player is snared!");
    }

    public static boolean isPlayerSnared() {
        return isSnared;
    }

    protected void shutDown() {
        exampleScript.shutdown();
        wildyKillerScript.shutdown();
        wildySaferScript.shutdown();
        overlayManager.remove(mossKillerOverlay);
        }
    }
