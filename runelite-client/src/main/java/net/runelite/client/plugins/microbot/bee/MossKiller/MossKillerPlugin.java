package net.runelite.client.plugins.microbot.bee.MossKiller;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.CombatMode;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private MossKillerScript exampleScript;

    private boolean worldHopFlag = false;
    @Getter
    private int deathCounter = 0;
    private boolean isDeathProcessed = false;

    private boolean isTeleblocked = false;
    private boolean lobsterEaten = false;
    private int tickCount = 0;

    private final Map<Player, Integer> attackerTickMap = new HashMap<>();
    private static final int MIN_TICKS_TO_TRACK = 2;
    private static final String MOSS_GIANT_NAME = "Moss Giant";

    private static boolean isSnared = false;
    private int snareTickCounter = 0;

    private boolean useWindBlast = false;
    private boolean useMelee = false;
    private boolean useRange = false;

    private boolean runeScimitar = false;

    public Player currentTarget = null;

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
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(mossKillerOverlay);
        }
        Microbot.useStaminaPotsIfNeeded = false;
        hideOverlay = config.isHideOverlay();
        toggleOverlay(hideOverlay);
        if(!config.wildy()) {
        exampleScript.run(config);
        } else {wildyKillerScript.run(config);
        wildyKillerScript.handleAsynchWalk("Start-up");
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
        if (event.getVarbitId() == Varbits.TELEBLOCK) {
            int teleblockValue = event.getValue(); // Get the current value of the teleblock varbit
            isTeleblocked = teleblockValue > 100;
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
                if(!hasOverlay) return;

                overlayManager.remove(mossKillerOverlay);
            } else {
                if (hasOverlay) return;

                overlayManager.add(mossKillerOverlay);
            }
        }
    }

    public void resetTarget() {
        currentTarget = null;}

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

        checkNearbyPlayers();

        if (hitsplatIsTheirs && hitsplatSetTick != -1) {
            // Check if 4 ticks have passed since the flag was set
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
            tickCount ++;

            if (tickCount >= 9)
            {superNullTarget = true;
                //System.out.println("9 ticks elapsed. target probably properly gone.");
            }

        } else {
            superNullTarget = false;}

        if (!Rs2Player.isMoving()) {
            tickCount ++;

            if (tickCount >= 50)
            {isJammed = true;
            }

        } else {
            tickCount = 0;
            isJammed = false;}

    }

    private void trackAttackers() {
        Player localPlayer = client.getLocalPlayer();

        // Check if a Moss Giant is interacting with the player
        Actor interactingActor = localPlayer.getInteracting();
        if (isMossGiant(interactingActor)) {
            resetAttackers();
            resetTarget(); // Reset target if Moss Giant interacts
            return;
        }

        List<Player> potentialTargets = Rs2Player.getPlayersInCombatLevelRange();

        // Update attackers map based on interactions
        for (Player player : potentialTargets) {
            if (player != null && player.getInteracting() == localPlayer
                    && !isNonCombatAnimation(player)
                    && hitsplatIsTheirs()) {
                if (Microbot.getVarbitPlayerValue(1075) == -1
                        && WildyKillerScript.CORRIDOR.contains(Rs2Player.getWorldLocation())
                        || Microbot.getVarbitPlayerValue(1075) != -1
                        && !WildyKillerScript.CORRIDOR.contains(Rs2Player.getWorldLocation()))
                    attackerTickMap.put(player, attackerTickMap.getOrDefault(player, 0) + 1);
            }
        }

        // Remove players no longer interacting with local player
        //attackerTickMap.entrySet().removeIf(entry -> entry.getKey().getInteracting() != localPlayer);

        // On each tick
        for (Map.Entry<Player, Integer> entry : attackerTickMap.entrySet()) {
            Player player = entry.getKey();
            int tickCount = entry.getValue();

            // Increment tick count if the player is interacting and performing combat animation or hitsplat
            if (player.getInteracting() == localPlayer && !isNonCombatAnimation(player)) {
                attackerTickMap.put(player, tickCount + 1); // Keep incrementing if combat animation
            }

            // Increment tick count if the player is interacting and their hitsplat is applied to you
            if (player.getInteracting() == localPlayer && hitsplatIsTheirs) {
                attackerTickMap.put(player, tickCount + 1); // Increment if hitsplat applies to you
            }

            // If the player is no longer interacting, decrease their tick count
            if (player.getInteracting() != localPlayer && player.getAnimation() != 829) {
                attackerTickMap.put(player, Math.max(0, tickCount - 2)); // Decrease by 2 tick per interval
            }

            // If the player is no longer interacting and no longer doing combat animations decrease their tick count
            if (player.getInteracting() != localPlayer && isNonCombatAnimation(player) && player.getAnimation() != 829) {
                attackerTickMap.put(player, Math.max(0, tickCount - 2)); // Decrease by 2 tick per interval
            }

            System.out.println(tickCount);

            // If tick count is >= MIN_TICKS_TO_TRACK, they become your target
            if (tickCount >= MIN_TICKS_TO_TRACK) {
                currentTarget = player;
                break;  // Set the first valid target
            }

            // If tick count is 0, remove player from the map
            if (tickCount == 0) {
                resetAttackers();
                resetTarget();
            }
        }


        if (currentTarget != null) {
            if (currentTarget.isDead() || !Rs2Player.getPlayers().contains(currentTarget)) {
                resetTarget();
            }
        }
    }

    /**
     * Determines if a player is performing a non-combat animation (walking/running).
     * Add specific walking/running animation IDs as needed.
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
        Player localPlayer = client.getLocalPlayer();
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

    /**
     * Map to track recent hitsplats and their timestamps for each player.
     */
    private final Map<Player, Integer> recentHitsplats = new HashMap<>();

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {

        if(config.wildy()) {

        if (currentTarget != null) {
            wildyKillerScript.hitsplatApplied = event.getHitsplat().isMine();
        }

        Actor target = event.getActor();
        Hitsplat hitsplat = event.getHitsplat();

        if (target == client.getLocalPlayer()) {
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
                            if (player.getInteracting() == client.getLocalPlayer() && !isNonCombatAnimation(player)) {
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
    private Player getAttackerForHitsplat(Player localPlayer) {
        for (Player player : Rs2Player.getPlayersInCombatLevelRange()) {
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
        Player localPlayer = client.getLocalPlayer();
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
        overlayManager.remove(mossKillerOverlay);
        }
    }
