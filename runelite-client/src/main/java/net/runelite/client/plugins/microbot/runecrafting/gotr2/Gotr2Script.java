package net.runelite.client.plugins.microbot.runecrafting.gotr2;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.runecrafting.gotr.GotrConfig;
import net.runelite.client.plugins.microbot.runecrafting.gotr.GotrState;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.Gotr2Config;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.Gotr2State;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.data.*;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class Gotr2Script extends Script {

    public static String version = "1.2.1";
    public static long totalTime = 0;
    public static boolean shouldMineGuardianRemains = true;
    /**
     * Indicates that all available pouches have been filled with essence.
     * This prevents repeated attempts at filling while skilling until the
     * pouches are emptied again when crafting runes.
     */
    public static boolean pouchesFilled = false;
    /**
     * Flag indicating whether a lack of guardian fragments has already been
     * detected. Used to ensure related messages are logged only once until
     * fragments are acquired again.
     */
    public static boolean outOfFragmentsDetected = false;
    public static final String rewardPointRegex = "Total elemental energy:[^>]+>([\\d,]+).*Total catalytic energy:[^>]+>([\\d,]+).";
    public static final Pattern rewardPointPattern = Pattern.compile(rewardPointRegex);

    public static boolean isInMiniGame = false;
    public static boolean isFirstPortal = true;
    public static final int portalId = ObjectID.PORTAL_43729;
    public static final int greatGuardianId = 11403;
    public static final Map<Integer, GuardianPortalInfo> guardianPortalInfo = new HashMap<>();
    public static Optional<Instant> nextGameStart = Optional.empty();
    public static Optional<Instant> timeSincePortal = Optional.empty();
    public static final Set<GameObject> guardians = new HashSet<>();
    public static final List<GameObject> activeGuardianPortals = new ArrayList<>();
    public static NPC greatGuardian;
    public static int elementalRewardPoints;
    public static int catalyticRewardPoints;
    public static Gotr2State state;
    public static Gotr2Config config;
    String GUARDIAN_FRAGMENTS = "guardian fragments";
    String GUARDIAN_ESSENCE = "guardian essence";

    boolean initCheck = false;
    public static boolean optimizedEssenceLoop = false;
    boolean noBind = false;
    boolean timeout = false;
    boolean elemental = false;
    boolean air = false;
    boolean water = false;
    boolean earth = false;
    boolean fire = false;
    boolean airAltar = false;
    boolean waterAltar = false;
    boolean earthAltar = false;
    boolean fireAltar = false;
    boolean needsDeposit = false;

    static boolean useNpcContact = true;
    private final List<Integer> runeIds = ImmutableList.of(
            ItemID.NATURE_RUNE,
            ItemID.LAW_RUNE,
            ItemID.BODY_RUNE,
            ItemID.DUST_RUNE,
            ItemID.LAVA_RUNE,
            ItemID.STEAM_RUNE,
            ItemID.SMOKE_RUNE,
            ItemID.SOUL_RUNE,
            ItemID.WATER_RUNE,
            ItemID.AIR_RUNE,
            ItemID.EARTH_RUNE,
            ItemID.FIRE_RUNE,
            ItemID.MIND_RUNE,
            ItemID.CHAOS_RUNE,
            ItemID.DEATH_RUNE,
            ItemID.BLOOD_RUNE,
            ItemID.COSMIC_RUNE,
            ItemID.ASTRAL_RUNE,
            ItemID.MIST_RUNE,
            ItemID.MUD_RUNE,
            ItemID.WRATH_RUNE);
    private boolean pouchCheck;

    private void initializeGuardianPortalInfo() {
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_AIR, new GuardianPortalInfo("AIR", 1, ItemID.AIR_RUNE, 26887, 4353, RuneType.ELEMENTAL, CellType.WEAK, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_MIND, new GuardianPortalInfo("MIND", 2, ItemID.MIND_RUNE, 26891, 4354, RuneType.CATALYTIC, CellType.WEAK, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_WATER, new GuardianPortalInfo("WATER", 5, ItemID.WATER_RUNE, 26888, 4355, RuneType.ELEMENTAL, CellType.MEDIUM, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_EARTH, new GuardianPortalInfo("EARTH", 9, ItemID.EARTH_RUNE, 26889, 4356, RuneType.ELEMENTAL, CellType.STRONG, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_FIRE, new GuardianPortalInfo("FIRE", 14, ItemID.FIRE_RUNE, 26890, 4357, RuneType.ELEMENTAL, CellType.OVERCHARGED, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_BODY, new GuardianPortalInfo("BODY", 20, ItemID.BODY_RUNE, 26895, 4358, RuneType.CATALYTIC, CellType.WEAK, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_COSMIC, new GuardianPortalInfo("COSMIC", 27, ItemID.COSMIC_RUNE, 26896, 4359, RuneType.CATALYTIC, CellType.MEDIUM, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.LOST_CITY.getState(Microbot.getClient())).orElse(null)));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_CHAOS, new GuardianPortalInfo("CHAOS", 35, ItemID.CHAOS_RUNE, 26892, 4360, RuneType.CATALYTIC, CellType.MEDIUM, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_NATURE, new GuardianPortalInfo("NATURE", 44, ItemID.NATURE_RUNE, 26897, 4361, RuneType.CATALYTIC, CellType.STRONG, QuestState.FINISHED));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_LAW, new GuardianPortalInfo("LAW", 54, ItemID.LAW_RUNE, 26898, 4362, RuneType.CATALYTIC, CellType.STRONG, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.TROLL_STRONGHOLD.getState(Microbot.getClient())).orElse(null)));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_DEATH, new GuardianPortalInfo("DEATH", 65, ItemID.DEATH_RUNE, 26893, 4363, RuneType.CATALYTIC, CellType.OVERCHARGED, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.MOURNINGS_END_PART_II.getState(Microbot.getClient())).orElse(null)));
        guardianPortalInfo.put(ObjectID.GUARDIAN_OF_BLOOD, new GuardianPortalInfo("BLOOD", 77, ItemID.BLOOD_RUNE, 26894, 4364, RuneType.CATALYTIC, CellType.OVERCHARGED, Microbot.getClientThread().runOnClientThreadOptional(() -> Quest.SINS_OF_THE_FATHER.getState(Microbot.getClient())).orElse(null)));
    }

    @Inject
    private ConfigManager configManager;

    public boolean run(Gotr2Config config) {
        this.config = config;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (!initCheck) {
                    if (!Rs2Inventory.hasItem("pickaxe") && !Rs2Equipment.isWearing("pickaxe")) {
                        log("You need to have a pickaxe before you can participate in this minigame.");
                        shutdown();
                    }
                    initializeGuardianPortalInfo();
                    if (!Rs2Magic.isLunar()) {
                        log("Lunar spellbook not found...disabling npc contact");
                        useNpcContact = false;
                        configManager.setConfiguration(config.configGroup, "combination", Combination.NONE);
                    }
                    var magicLevel = Microbot.getClient().getBoostedSkillLevel(Skill.MAGIC);
                    if (magicLevel < 82) {
                        configManager.setConfiguration(config.configGroup, "combination", Combination.NONE);
                    }
                    state = Gotr2State.INITIALIZED;
                    initCheck = true;
                }

                Rs2Walker.setTarget(null);

                if (guardiansLoop()) return;

//                shut down should set state to shut down
//                startup should set state initialize
//                initialize should run only the original init check revert this change back to initialize guardian portal info if ! not lunarmagic magic level lower than 82 and then set state to enter minigame
//                enter minigame should check if not banking or breaking and enter the barrier if a bank or break is called for state should be changed to banking then reset plugin should be called
//                reset plugin should return to enter minigame state which will again check for bank or break and interact with the portal with wait for walking and if !notoutside barrier is detected  it will take 10 uncharged cells and change state to waiting
//                waiting will check for the banking or breaking calls or direct the player to the large mine if agility is high enough or guardian parts if not then start mining as soon as the minigame starts
//                mining state will go until first portal which will set portal state
//                portal state will direct player into the portal and have them mine huge guardian remain until the no animation then fill pouch on repeat until pouches full and inventory full then leave huge mine and set enter altar state
//                enter altar state will detect the active altars in main game area using the getavailable alatrs and enter that altar then set state to crafting runes
//                craftign runes will find the rcaltar in the location and craft runes on it if NONE or use available combinations based on the other Enums in Combination using magic imbue before using the available rune on the altar and then set state to leaving altar
//                leavign altar will; find the exit portal and take the player out and when in main game area change state to optimization loop
//                optimization loop will first check for any portals and set portal state if there are any  if not it will then powerupguardian then charge tile then deposit runes and then run a check of the fragments in inventory against the remaining percent of guardian power we want about 40fragments per 15% of guardian power remaining till 100 if we have more set state to craft essence if we have less set state to mining
//                craft essenmce state will craft essence until xp drops stop fill pouch and repeat until pouches full and inventory full then set state to enter altar
//                mining state will check if first portal has occured if it has it will mine until a portal or fragments until the desired 40fragments per 15% of guardian power remaining till 100 unless power is over 95
//                a constant power check will set state to end game and run a break and bank check before setting the state to waiting


                long endTime = System.currentTimeMillis();
                totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.log("Something went wrong in the GOTR Script: " + ex.getMessage() + ". If the script is stuck, please contact us on discord with this log.");
                ex.printStackTrace();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean guardiansLoop() {
        switch (state) {
            case INITIALIZE:
            case INITIALIZED:
                return handleNotInGame();

            case ENTER_GAME:
                return handleEnterGame();

            case WAITING:
                return handleWaiting();

            case PORTAL:
                return handlePortal();

            case ENTER_ALTAR:
                return handleEnterAltar();

            case CRAFTING_RUNES:
                return handleCraftingRunes();

            case LEAVING_ALTAR:
                return handleLeavingAltar();

            case POWERING_UP:
                return handlePoweringUp();

            case CRAFT_GUARDIAN_ESSENCE:
                return handleCraftEssence();

            case MINE_LARGE_GUARDIAN_REMAINS:
                return handleMining();

            case OPTIMIZATION_LOOP:
                return handleOptimizationLoop();

            case END_GAME:
                return handleEndGame();

            case BANKING:
                return handleBanking();

            case SHUTDOWN:
                return false;
        }
        return false;
    }

    private boolean handleNotInGame() {
        if (craftRunes()) return true;
        if (enterMinigame()) return true;
        return waitForMinigameToStart();
    }

    private boolean handleEnterGame() {
        if (!Rs2Inventory.hasItem("Uncharged cell")) {
            takeUnchargedCells();
        }
        state = Gotr2State.WAITING;
        return true;
    }

    private boolean handleWaiting() {
        int timeToStart = nextGameStart.map(n -> (int) ChronoUnit.SECONDS.between(Instant.now(), n)).orElse(0);
        if (waitingForGameToStart(timeToStart)) {
            return true;
        }
        state = Gotr2State.OPTIMIZATION_LOOP;
        return true;
    }

    private boolean handlePortal() {
        if (mineHugeGuardianRemain()) return true;
        if (Rs2Inventory.allPouchesFull() || Rs2Inventory.isFull()) {
            leaveHugeMine();
            state = Gotr2State.ENTER_ALTAR;
        }
        return true;
    }

    private boolean handleEnterAltar() {
        if (enterAltar()) return true;
        state = Gotr2State.CRAFTING_RUNES;
        return true;
    }

    private boolean handleCraftingRunes() {
        if (craftRunes()) return true;
        state = Gotr2State.LEAVING_ALTAR;
        return true;
    }

    private boolean handleLeavingAltar() {
        TileObject portal = findPortalToLeaveAltar();
        if (portal != null && Rs2GameObject.interact(portal.getId())) {
            sleepUntilTrue(Gotr2Script::isInMainRegion,100,10000);
        }
        state = Gotr2State.OPTIMIZATION_LOOP;
        return true;
    }

    private boolean handleOptimizationLoop() {
        if (usePortal()) {
            state = Gotr2State.PORTAL;
            return true;
        }
        if (mineHugeGuardianRemain()) return true;
        if (powerUpGreatGuardian()) return true;
        if (repairCells()) return true;
        if (!shouldMineGuardianRemains) {
            if (isOutOfFragments()) return true;
            if (depositRunesIntoPool()) return true;
            if (fillPouches()) {
                craftGuardianEssences();
                return true;
            }
            if (!Rs2Inventory.isFull() && !optimizedEssenceLoop) {
                if (leaveLargeMine()) return true;
                if (craftGuardianEssences()) return true;
            } else if (Rs2Inventory.hasItem(GUARDIAN_ESSENCE)) {
                if (leaveLargeMine()) return true;
                if (enterAltar()) return true;
            }
        } else {
            mineGuardianRemains();
        }
        return true;
    }

    private boolean handleMining() {
        mineGuardianRemains();
        return true;
    }

    private boolean handleCraftEssence() {
        craftGuardianEssences();
        return true;
    }

    private boolean handlePoweringUp() {
        powerUpGreatGuardian();
        return true;
    }

    private boolean handleEndGame() {
        // Placeholder for end of game logic
        state = Gotr2State.WAITING;
        return true;
    }

    private boolean handleBanking() {
        // Placeholder for banking logic
        return true;
    }

    private void takeUnchargedCells() {
        if (!Rs2Inventory.hasItem("Uncharged cell")) {
            if (Rs2Inventory.isFull()) {
                if (Rs2Inventory.drop(ItemID.GUARDIAN_ESSENCE)) {
                    Microbot.log("Dropped one Guardian essence to make space for Uncharged cell");
                }
            }
            Rs2GameObject.interact(ObjectID.UNCHARGED_CELLS_43732, "Take-10");
            log("Taking uncharged cells...");
            Rs2Player.waitForAnimation();
        }
    }

    private boolean lootChisel() {
        if (isInHugeMine()) return false;
        if (!Rs2Inventory.isFull() && !Rs2Inventory.hasItem("Chisel")) {
            Rs2GameObject.interact("chisel", "take");
            Rs2Player.waitForWalking();
            log("Looking for chisel...");
            return true;
        }
        return false;
    }

    private boolean usePortal() {
        if (!isInHugeMine() && Microbot.getClient().hasHintArrow() && Rs2Inventory.size() < config.maxAmountEssence()) {
            if (leaveLargeMine()) return true;
            Rs2Walker.walkFastCanvas(Microbot.getClient().getHintArrowPoint());
            sleepUntil(Rs2Player::isMoving);
            Rs2GameObject.interact(Microbot.getClient().getHintArrowPoint());
            log("Found a portal spawn...interacting with it...");
            Rs2Player.waitForWalking();
            sleepUntil(() -> isInHugeMine());
            sleepUntil(() -> getGuardiansPower() > 0);
            return true;
        }
        return false;
    }

    private boolean depositRunesIntoPool() {
        if (config.shouldDepositRunes() && Rs2Inventory.hasItem(runeIds.toArray(Integer[]::new)) && !isInLargeMine() && !isInHugeMine() && !Rs2Inventory.isFull() && !optimizedEssenceLoop) {
            if (Rs2Player.isMoving()) return true;
            if (Rs2GameObject.interact(ObjectID.DEPOSIT_POOL)) {
                log("Deposit runes into pool...");
                sleep(600, 2400);
            }
            return true;
        }
        return false;
    }

    private boolean enterAltar() {
        GameObject availableAltar = getAvailableAltars().stream().findFirst().orElse(null);
        if (availableAltar != null && !Rs2Player.isMoving()) {
            log("Entering with altar " + availableAltar.getId());
            Rs2GameObject.interact(availableAltar);
            state = Gotr2State.ENTER_ALTAR;
            Global.sleepUntil(() -> !isInMainRegion() || !Objects.equals(getAvailableAltars().stream().findFirst().orElse(null), availableAltar), 5000);
            sleep(Rs2Random.randomGaussian(1000, 300));
            return true;
        }
        return false;
    }

    private boolean craftGuardianEssences() {
        if (Rs2GameObject.interact(ObjectID.WORKBENCH_43754)) {
            state = Gotr2State.CRAFT_GUARDIAN_ESSENCE;
            sleep(Rs2Random.randomGaussian(Rs2Random.between(600, 900), Rs2Random.between(150, 300)));
            log("Crafting guardian essences...");
            return true;
        }
        return false;
    }

    private boolean leaveLargeMine() {
        if (isInLargeMine()) {
            Rs2GameObject.interact(ObjectID.RUBBLE_43726);
            Rs2Player.waitForAnimation();
            log("Leaving large mine...");
            state = Gotr2State.LEAVING_LARGE_MINE;
            return true;
        }
        return false;
    }

    private boolean fillPouches() {
        if (Rs2Inventory.isFull() && Rs2Inventory.anyPouchEmpty() && getGuardiansPower() < 90) {
            Rs2Inventory.fillPouches();
            sleep(Rs2Random.randomGaussian(600, 300));
            return true;
        }
        return false;
    }

    private boolean isOutOfFragments() {
        if ((!Rs2Inventory.hasItem(GUARDIAN_FRAGMENTS) && !Rs2Inventory.isFull()) || (getTimeSincePortal() > 85 && !Rs2Inventory.hasItem(GUARDIAN_ESSENCE))) {
            shouldMineGuardianRemains = true;
            if(!Rs2Inventory.hasItem(GUARDIAN_FRAGMENTS))
                log("Memorize that we no longer have guardian fragments...");
            return true;
        }
        shouldMineGuardianRemains = false;
        return false;
    }

    private boolean craftRunes() {
        if (!isInMainRegion() && isInMiniGame()) {
            TileObject rcAltar = findRcAltar();
            if (rcAltar != null) {
                if (Rs2Player.isMoving()) return true;
                if (Rs2Inventory.anyPouchFull() && !Rs2Inventory.isFull()) {
                    Rs2Inventory.emptyPouches();
                    Rs2Inventory.waitForInventoryChanges(5000);
                    sleep(Rs2Random.randomGaussian(350, 150));
                }
                if (Rs2Inventory.hasItem(GUARDIAN_ESSENCE)) {
                    state = Gotr2State.CRAFTING_RUNES;
                    optimizedEssenceLoop = false;
                    Rs2GameObject.interact(rcAltar.getId());
                    log("Crafting runes on altar " + rcAltar.getId());
                    sleep(Rs2Random.randomGaussian(Rs2Random.between(1000, 1500), 300));
                } else if (!Rs2Player.isMoving()) {
                    state = Gotr2State.LEAVING_ALTAR;
                    TileObject rcPortal = findPortalToLeaveAltar();
                    if (Rs2GameObject.interact(rcPortal.getId())) {
                        log("Leaving the altar...");
                        sleepUntilTrue(Gotr2Script::isInMainRegion,100,10000);
                        sleep(Rs2Random.randomGaussian(750, 150));
                    }
                }
                return true;
            }
        }
        return false;
    }

    private static boolean waitForMinigameToStart() {
        if (!isInMainRegion()) {
            TileObject rcPortal = findPortalToLeaveAltar();
            if (rcPortal != null && Rs2GameObject.interact(rcPortal.getId())) {
                state = Gotr2State.LEAVING_ALTAR;
                return true;
            }
        }
        resetPlugin();
        if (state != Gotr2State.WAITING) {
            state = Gotr2State.WAITING;
            log("Make sure to start the script near the minigame barrier.");
            Rs2GameObject.interact(ObjectID.BARRIER_43849, "Peek");
        }
        return state == Gotr2State.WAITING;
    }

    private static boolean enterMinigame() {
        if (Rs2GameObject.interact(ObjectID.BARRIER_43700, "quick-pass")) {
            Rs2Player.waitForWalking();
            state = Gotr2State.ENTER_GAME;
            shouldMineGuardianRemains = true;
            log("Entering game...");
            return true;
        }
        return false;
    }

    private void checkPouches(boolean anyPouchUnknown, int mean, int stddev) {
        if (anyPouchUnknown) {
            Rs2Inventory.checkPouches();
            sleep(Rs2Random.randomGaussian(mean, stddev));
        }
    }

    private boolean mineHugeGuardianRemain() {
        if (isInHugeMine()) {
            if (getGuardiansPower() == 0) {
                repairPouches();
                leaveHugeMine();
                optimizedEssenceLoop = false;
                return false;
            }
            if (!Rs2Inventory.isFull()) {
                if (!Rs2Player.isAnimating()) {
                    Rs2GameObject.interact(ObjectID.HUGE_GUARDIAN_REMAINS);
                    Rs2Player.waitForAnimation();
                    if (!Rs2Player.isAnimating())
                        Rs2GameObject.interact(ObjectID.HUGE_GUARDIAN_REMAINS);
                }
            } else {
                if (Rs2Inventory.allPouchesFull()) {
                    if(Rs2Inventory.hasItem("guardian stone"))
                        optimizedEssenceLoop = true;
                    leaveHugeMine();
                } else {
                    Rs2Inventory.fillPouches();
                    sleep(Rs2Random.randomGaussian(Rs2Random.between(600, 1200), Rs2Random.between(100, 300)));
                    if (!Rs2Inventory.isFull()) {
                        Rs2GameObject.interact(ObjectID.HUGE_GUARDIAN_REMAINS);
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void mineGuardianRemains() {
        if (Microbot.getClient().hasHintArrow())
            return;
        if (Rs2Inventory.isFull()) {
            shouldMineGuardianRemains = false;
            return;
        }
        state = Gotr2State.MINE_LARGE_GUARDIAN_REMAINS;
        if (isInHugeMine()) {
            leaveHugeMine();
            return;
        }
        if (Rs2Player.getSkillRequirement(Skill.AGILITY, 56) && getTimeSincePortal() < 85 && !Rs2Inventory.hasItem(GUARDIAN_ESSENCE)) {
            if (!isInLargeMine() && !isInHugeMine() && (!Rs2Inventory.hasItem(GUARDIAN_FRAGMENTS) || getStartTimer() == -1)) {
                if (Rs2Walker.walkTo(new WorldPoint(3632, 9503, 0), 20)) {
                    log("Traveling to large mine...");
                    Rs2GameObject.interact(ObjectID.RUBBLE_43724);
                    if (sleepUntil(Rs2Player::isAnimating)) {
                        sleepUntil(this::isInLargeMine);
                        if (isInLargeMine()) {
                            sleep(Rs2Random.randomGaussian(Rs2Random.between(2000, 2400), Rs2Random.between(100, 300)));
                            log("Interacting with large guardian remains...");
                            Rs2GameObject.interact(ObjectID.LARGE_GUARDIAN_REMAINS);
                            sleepGaussian(1200, 150);
                        }
                    }
                }
                sleepGaussian(600, 150);
            } else {
                if (!Rs2Player.isAnimating() && getStartTimer() != -1) {
                    if (Rs2Equipment.isWearing("dragon pickaxe")) {
                        Rs2Combat.setSpecState(true, 1000);
                    }
                    checkPouches(Rs2Random.between(1, 20) == 2, Rs2Random.between(100, 600), Rs2Random.between(100, 300));

                    repairPouches();
                    Rs2GameObject.interact(ObjectID.LARGE_GUARDIAN_REMAINS);
                    sleepGaussian(1200, 150);
                }
            }
        } else {
            if (!Rs2Player.isAnimating() && getStartTimer() != -1) {
                if(isInLargeMine()) {
                    leaveLargeMine();
                }
                if (Rs2Equipment.isWearing("dragon pickaxe")) {
                    Rs2Combat.setSpecState(true, 1000);
                }
                repairPouches();
                Rs2GameObject.interact(ObjectID.GUARDIAN_PARTS_43716);
                sleepGaussian(1200, 150);
                shouldMineGuardianRemains = false;
            }
        }
    }

    private void leaveHugeMine() {
        Rs2GameObject.interact(38044);
        log("Leave huge mine...");
        Global.sleepUntil(() -> !isInHugeMine(), 5000);
    }

    private boolean powerUpGreatGuardian() {
        if (Rs2Inventory.hasItem("guardian stone") && !shouldMineGuardianRemains && !isInLargeMine() && !isInHugeMine()) {
            state = Gotr2State.POWERING_UP;
            Rs2Npc.interact("The great guardian", "power-up");
            log("Powering up the great guardian...");
            sleepUntil(Rs2Player::isAnimating);
            sleep(Rs2Random.randomGaussian(Rs2Random.between(1000, 2000), Rs2Random.between(100, 300)));
            return true;
        }
        return false;
    }

    private boolean repairCells() {
        Rs2ItemModel cell = Rs2Inventory.get(CellType.PoweredCellList().toArray(Integer[]::new));
        if (cell != null && isInMainRegion() && isInMiniGame() && !shouldMineGuardianRemains && !isInLargeMine() && !isInHugeMine()) {
            int cellTier = CellType.GetCellTier(cell.getId());
            List<Integer> shieldCellIds = Rs2GameObject.getObjectIdsByName("cell_tile");
            if (Rs2Inventory.hasItemAmount(GUARDIAN_ESSENCE, 10)) {
                for (int shieldCellId : shieldCellIds) {
                    TileObject shieldCell = Rs2GameObject.getTileObject(shieldCellId);
                    if (shieldCell == null) continue;
                    if (CellType.GetShieldTier(shieldCell.getId()) < cellTier) {
                        Microbot.log("Upgrading power cell at " + shieldCell.getWorldLocation());
                        Rs2GameObject.interact(shieldCell, "Place-cell");
                        sleepUntil(() -> !Rs2Player.isMoving());
                        return true;
                    }
                }
            }
            shieldCellIds = shieldCellIds.stream().filter(id -> id != ObjectID.CELL_TILE_BROKEN).collect(Collectors.toList());
            int interactedObjectId = Rs2GameObject.interact(shieldCellIds);
            if (interactedObjectId != -1) {
                log("Using cell with id " + interactedObjectId);
                sleep(Rs2Random.randomGaussian(1000, 300));
                sleepUntil(() -> !Rs2Player.isMoving());
            }
            return true;
        }
        return false;
    }

    public boolean isOutsideBarrier() {
        int outsideBarrierY = 9482;
        return Rs2Player.getWorldLocation().getY() <= outsideBarrierY
                && Rs2Player.getWorldLocation().getRegionID() == 14484;
    }

    public boolean isInLargeMine() {
        int largeMineX = 3637;
        return Rs2Player.getWorldLocation().getRegionID() == 14484
                && Microbot.getClient().getLocalPlayer().getWorldLocation().getX() >= largeMineX;
    }

    public boolean isInHugeMine() {
        int hugeMineX = 3594;
        return Rs2Player.getWorldLocation().getRegionID() == 14484
                && Microbot.getClient().getLocalPlayer().getWorldLocation().getX() <= hugeMineX;
    }

    public ItemManager getItemManager() {
        return Microbot.getItemManager();
    }

    public boolean isInMiniGame() {
        int parentWidgetId = 48889857;
        Widget elementalRuneWidget = Microbot.getClient().getWidget(parentWidgetId);
        return elementalRuneWidget != null;
    }

    public static boolean isInMainRegion() {
        return Rs2Player.getWorldLocation().getRegionID() == 14484;
    }

    public static int getStartTimer() {
        Widget timerWidget = Rs2Widget.getWidget(48889861);
        if (timerWidget != null) {
            String timer = timerWidget.getText();
            if (timer == null) return -1;
            String[] timeParts = timer.split(":");
            if (timeParts.length == 2) {
                int minutes = Integer.parseInt(timeParts[0]);
                int seconds = Integer.parseInt(timeParts[1]);
                int totalSeconds = (minutes * 60) + seconds;
                return totalSeconds;
            }
        }
        return -1;
    }

    public static int getTimeSincePortal() {
        if(getStartTimer() == -1) {
            return -1;
        }
        int firstPortalTimeAdjustment = isFirstPortal ? 40 : 0;
        return timeSincePortal.map(instant -> (int) ChronoUnit.SECONDS.between(instant, Instant.now())-firstPortalTimeAdjustment).orElse(-1);

    }

    public static List<GameObject> getAvailableAltars() {
        int elementalPoints = Microbot.getVarbitValue(13686);
        int catalyticPoints = Microbot.getVarbitValue(13685);
        if (config.Mode() == Mode.BALANCED) {
            Microbot.log(elementalPoints < catalyticPoints ? "We have " + elementalPoints + " elemental points, looking for elemental altar..." : "We have " + catalyticPoints +" catalytic points, looking for catalytic altar...");
        }
        return Rs2GameObject.getGameObjects().stream()
                .filter(x -> {

                    if (!guardianPortalInfo.containsKey(x.getId())) return false;
                    if (guardianPortalInfo.get(x.getId()).getRequiredLevel() > Microbot.getClient().getBoostedSkillLevel(Skill.RUNECRAFT)) {
                        return false;
                    }
                    if (guardianPortalInfo.get(x.getId()).getQuestState() != QuestState.FINISHED) {
                        return false;
                    }

                    if (((DynamicObject) x.getRenderable()).getAnimation() == null) {
                        return false;
                    }
                    if (((DynamicObject) x.getRenderable()).getAnimation().getId() != 9363) {
                        return false;
                    }

                    return true;

                })
                .sorted((config.Mode() == Mode.BALANCED && elementalPoints < catalyticPoints) || config.Mode() == Mode.ELEMENTAL ? Comparator.comparingInt(TileObject::getId) : Comparator.comparingInt(TileObject::getId).reversed())
                .collect(Collectors.toList());
    }

    private int getGuardiansPower() {
        Widget pWidget = Rs2Widget.getWidget(48889874);
        if (pWidget == null) {
            return 0;
        }

        Matcher matcher = Pattern.compile("(\\d+)%").matcher(pWidget.getText());

        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public static TileObject findRcAltar() {
        Integer[] altarIds = new Integer[] {ObjectID.ALTAR_34760, ObjectID.ALTAR_34761, ObjectID.ALTAR_34762, ObjectID.ALTAR_34763, ObjectID.ALTAR_34764,
                ObjectID.ALTAR_34765, ObjectID.ALTAR_34766, ObjectID.ALTAR_34767, ObjectID.ALTAR_34768, ObjectID.ALTAR_34769, ObjectID.ALTAR_34770,
                ObjectID.ALTAR_34771, ObjectID.ALTAR_34772, ObjectID.ALTAR_43479};
        return Rs2GameObject.findObject(altarIds);
    }

    public static TileObject findPortalToLeaveAltar() {
        Integer[] altarIds = new Integer[] {ObjectID.PORTAL_34748, ObjectID.PORTAL_34749, ObjectID.PORTAL_34750, ObjectID.PORTAL_34751, ObjectID.PORTAL_34752,
                ObjectID.PORTAL_34753, ObjectID.PORTAL_34754, ObjectID.PORTAL_34755, ObjectID.PORTAL_34756, ObjectID.PORTAL_34757, ObjectID.PORTAL_34758,
                ObjectID.PORTAL_34758, ObjectID.PORTAL_34759, ObjectID.PORTAL_43478};
        return Rs2GameObject.findObject(altarIds);
    }

    private boolean waitingForGameToStart(int timeToStart) {
        if (isInHugeMine()) return false;

        if (getStartTimer() > Rs2Random.randomGaussian(35, Rs2Random.between(1, 5)) || getStartTimer() == -1 || timeToStart > 10) {

            // Only take cells if we don't already have them
            if (!Rs2Inventory.hasItem("Uncharged cell")) {
                // If in large mine and need cells, leave first
                if (isInLargeMine()) {
                    if (leaveLargeMine()) return true;
                }
                takeUnchargedCells();
                // Return to large mine if we were there before
                if (!isInLargeMine() && shouldMineGuardianRemains) {
                    if (Rs2Walker.walkTo(new WorldPoint(3632, 9503, 0), 20)) {
                        Rs2GameObject.interact(ObjectID.RUBBLE_43724);
                        return true;
                    }
                }
            }

            repairPouches();

            if (!shouldMineGuardianRemains) return true;

            mineGuardianRemains();
            return true;
        }
        return false;
    }

    public static void resetPlugin() {
        guardians.clear();
        activeGuardianPortals.clear();
        greatGuardian = null;
        Microbot.getClient().clearHintArrow();
    }

    private static boolean repairPouches() {
        if (!useNpcContact) {
            repairWithCordelia();
            return true;
        }
        if (Rs2Inventory.hasDegradedPouch()) {
            return Rs2Magic.repairPouchesWithLunar();
        }
        return false;
    }

    private static void repairWithCordelia() {
        if (!Rs2Inventory.hasDegradedPouch()) return;
        if (!Rs2Inventory.hasItem(ItemID.ABYSSAL_PEARLS)) return;
        Rs2NpcModel pouchRepairNpc = Rs2Npc.getNpc(NpcID.APPRENTICE_CORDELIA_12180);
        if (pouchRepairNpc == null) return;
        if (!Rs2Npc.hasAction(pouchRepairNpc.getId(), "Repair")) return;
        if (!Rs2Npc.canWalkTo(pouchRepairNpc, 10)) return;
        if (!Rs2Npc.interact(pouchRepairNpc, "Repair")) return;

        Microbot.log("Repairing pouches...");

        Global.sleepUntil(() -> {
            Rs2Dialogue.clickContinue();
            return !Rs2Inventory.hasDegradedPouch();
        }, 10000);

    }

    public static boolean isGuardianPortal(GameObject gameObject) {
        return guardianPortalInfo.containsKey(gameObject.getId());
    }

    @Override
    public void shutdown() {
        state = Gotr2State.SHUTDOWN;
        super.shutdown();
    }
}