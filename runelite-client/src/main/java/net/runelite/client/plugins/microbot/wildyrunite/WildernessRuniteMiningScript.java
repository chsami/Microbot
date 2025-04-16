package net.runelite.client.plugins.microbot.wildyrunite;

import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.kit.KitType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.Player;
import net.runelite.api.MenuAction;


import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.runelite.api.ObjectID.POOL_OF_REFRESHMENT;

public class WildernessRuniteMiningScript extends Script {

    @Getter
    private int totalMined = 0;
    @Getter
    private int orePrice = 0;

    private static final WorldPoint POOL_OF_REFRESHMENT_TILE = new WorldPoint(3129, 3635, 0);
    private final WorldPoint FEROX_ENCLAVE_BANK = new WorldPoint(3130, 3631, 0);
    private static final WorldPoint LUMBRIDGE_BANK_TILE = new WorldPoint(3209, 3220, 2);
    private final WorldPoint RUNITE_ORE_TILE = new WorldPoint(3059, 3884, 0);
    private boolean isBanking = false;
    private long scriptStartTime;
    private boolean fleeingFromPlayer = false;


    private final AtomicBoolean scriptRunning = new AtomicBoolean(false);
    @Getter
    private final Set<String> recentAttackers = new HashSet<>();

    private Thread worldHopThread;
    private Thread combatThread;
    private Thread zoneThread;

    private void updateStatus(String message) {
        String timestamp = java.time.LocalTime.now().withNano(0).toString();
        Microbot.status = "[" + timestamp + "] " + message;
        Microbot.log("[Status] " + timestamp + " → " + message);
    }

    private void stopAllThreads() {
        if (worldHopThread != null) worldHopThread.interrupt();
        if (combatThread != null) combatThread.interrupt();
        if (zoneThread != null) zoneThread.interrupt();
    }

    private boolean preparePickaxeAndAxe() {
        updateStatus("Checking equipment for pickaxe and axe...");

        boolean hasPick = Rs2Inventory.hasItem("Rune pickaxe")
                || Rs2Inventory.contains("Rune pickaxe")
                || Microbot.getClient().getLocalPlayer().getPlayerComposition().getEquipmentId(KitType.WEAPON) == ItemID.RUNE_PICKAXE;

        if (!hasPick && Rs2Bank.openBank()) {
            updateStatus("Bank opened. Attempting to withdraw Rune pickaxe...");
            if (Rs2Bank.hasItem("Rune pickaxe")) {
                Rs2Bank.withdrawX("Rune pickaxe", 1);
                sleep(600);
                Rs2Inventory.interact("Rune pickaxe", "Wield");
                hasPick = true;
                updateStatus("Rune pickaxe withdrawn and wielded.");
            } else {
                updateStatus("No rune pickaxe found! Stopping script.");
                Rs2Bank.closeBank();
                shutdown();
                return false;
            }

            updateStatus("Looking for best available axe...");
            String[] f2pAxes = {
                    "Rune axe", "Adamant axe", "Mithril axe",
                    "Steel axe", "Black axe", "Iron axe", "Bronze axe"
            };

            for (String axe : f2pAxes) {
                if (Rs2Bank.hasItem(axe)) {
                    Rs2Bank.withdrawX(axe, 1);
                    sleep(600);
                    updateStatus(axe + " withdrawn.");
                    break;
                }
            }

            Rs2Bank.closeBank();
            updateStatus("Bank closed after gear prep.");
        }

        return hasPick;
    }

    private void startWorldHopThread() {
        worldHopThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && scriptRunning.get()) {
                if (shouldHopWorld()) {
                    int world = Login.getRandomWorld(Rs2Player.isMember());
                    Microbot.hopToWorld(world);
                    updateStatus("World hop triggered. New world: " + world);
                }
                sleep(2000);
            }
        });
        worldHopThread.start();
    }

    private boolean shouldHopWorld() {
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc.getY() > 3643 && Microbot.getClient().getPlayers().stream() //if above ferox encalve can start changing worlds
                .anyMatch(p -> p != null && !p.equals(Microbot.getClient().getLocalPlayer()));
    }

    private void monitorZone() {
        zoneThread = new Thread(() -> {
            boolean previouslyInFerox = true;
            while (!Thread.currentThread().isInterrupted() && scriptRunning.get()) {
                boolean inFerox = Rs2Player.getWorldLocation().distanceTo(FEROX_ENCLAVE_BANK) < 30;
                if (inFerox) {
                    updateStatus("Detected in Ferox. Preparing tools.");
                    preparePickaxeAndAxe();
                }
                if (!inFerox && previouslyInFerox) {
                    updateStatus("Left Ferox, enabling world hop thread.");
                    startWorldHopThread();
                }
                previouslyInFerox = inFerox;
                sleep(3000);
            }
        });
        zoneThread.start();
    }

    private void monitorCombatAndHealth() {
        combatThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && scriptRunning.get()) {
                if (Rs2Combat.inCombat()) {
                    Player local = Microbot.getClient().getLocalPlayer();

                    Microbot.getClient().getPlayers().stream()
                            .filter(p -> p != null && !p.equals(local) && p.getInteracting() == local)
                            .findFirst()
                            .ifPresent(attacker -> {
                                if (!fleeingFromPlayer) {
                                    fleeingFromPlayer = true;
                                    recentAttackers.add(attacker.getName());
                                    updateStatus("⚠️ Under attack by player: " + attacker.getName());

                                    if (Rs2Player.getHealthPercentage() < 50 &&
                                            !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_ITEM)) {
                                        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM);
                                        updateStatus("🛡️ Low HP → Protect Item activated.");
                                    }

                                    stopWalking();
                                    updateStatus("🏃 Fleeing to Ferox Enclave...");
                                    walkToFerox();
                                }
                            });
                }

                sleep(1000);
            }
        });
        combatThread.start();
    }


    public int calculateGpPerHour() {
        long elapsedMillis = System.currentTimeMillis() - scriptStartTime;
        double hours = elapsedMillis / (1000.0 * 60 * 60);
        if (hours == 0) return 0;
        return (int) ((totalMined * orePrice) / hours);
    }

    private void setTopDownCameraView() {
        if (Microbot.getClient() == null) return;
        Microbot.getClient().setCameraPitchTarget(383);
        Microbot.getClient().setCameraYawTarget(0);
        Microbot.getClient().setCameraShakeDisabled(true);
        updateStatus("Camera set to top-down view.");
    }

    private void applyFastMiningAntibanSetup() {
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.randomIntervals = false;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = false;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.actionCooldownChance = 0.1;

        Rs2Antiban.setActivity(Activity.GENERAL_MINING);
        Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
        updateStatus("Applied antiban mining configuration (EXTREME speed).");
    }

    public void run(WildernessRuniteMiningConfig config) {
        if (scriptRunning.get() || Microbot.getClient() == null) return;
        scriptRunning.set(true);
        scriptStartTime = System.currentTimeMillis();

        updateStatus("Waiting for login...");
        while (!Microbot.isLoggedIn() && scriptRunning.get()) {
            sleep(500);
        }
        updateStatus("Logged in. Starting script...");

        applyFastMiningAntibanSetup();
        Microbot.enableAutoRunOn = true;
        setTopDownCameraView();

        try {
            orePrice = Microbot.getItemManager().search("Runite ore").get(0).getPrice();
            updateStatus("Runite ore price fetched: " + orePrice + " gp");
        } catch (Exception e) {
            orePrice = 11500;
            updateStatus("Failed to fetch ore price, using default: 11500 gp");
        }

        if (!preparePickaxeAndAxe()) {
            shutdown();
            return;
        }

        monitorCombatAndHealth();
        monitorZone();

        new Thread(() -> {
            while (scriptRunning.get()) {
                if (!Microbot.isLoggedIn()) {
                    updateStatus("Not logged in. Waiting...");
                    sleep(600);
                    continue;
                }

                if (Rs2Player.getWorldLocation().distanceTo(new WorldPoint(3222, 3218, 0)) < 20) {
                    updateStatus("Detected in Lumbridge. Starting recovery...");
                    handleLumbridgeDeathRecovery();
                    continue;
                }

                if (fleeingFromPlayer) {
                    updateStatus("⚠️ Fleeing from player → Pausing all actions.");
                    // When arriving safely at Ferox, reset the state
                    if (Rs2Player.getWorldLocation().distanceTo(FEROX_ENCLAVE_BANK) < 5) {
                        updateStatus("✅ Safe at Ferox. Resuming script.");
                        fleeingFromPlayer = false;
                    } else {
                        sleep(1000);
                        continue;
                    }
                }

                // Main banking logic
                if (hasEnoughOre(config) || isBanking) {
                    // 🔁 Always walk if not banking or too far from bank
                    if (!isBanking || Rs2Player.getWorldLocation().distanceTo(FEROX_ENCLAVE_BANK) >= 5) {
                        isBanking = true;
                        updateStatus("Inventory full. Banking runite ore...");
                        walkToFerox();
                    }

                    if (Rs2Player.getWorldLocation().distanceTo(FEROX_ENCLAVE_BANK) < 5) {
                        updateStatus("Arrived at Ferox. Banking...");
                        bankOres();
                        drinkPoolIfAtFerox();

                        if (config.stopAfterOneRun()) {
                            updateStatus("Stopping script after one full run.");
                            shutdown();
                            return;
                        }

                        updateStatus("Banking done. Walking back to ore...");
                        isBanking = false;
                        walkToOre();
                    }

                    continue;
                }

                if (Rs2Player.getWorldLocation().distanceTo(RUNITE_ORE_TILE) > 3) {
                    updateStatus("Walking to Runite ore tile...");
                    walkToOre();
                    continue;
                }

                GameObject rock = Rs2GameObject.findReachableObject(
                        Rocks.RUNITE.getName(), true, 10, Rs2Player.getWorldLocation());

                if (rock != null && !Rs2Player.isAnimating()) {
                    int oreBefore = Rs2Inventory.count("Runite ore");
                    updateStatus("Rock found. Attempting to mine...");

                    if (Rs2GameObject.interact(rock, "Mine")) {
                        boolean startedMining = sleepUntil(Rs2Player::isAnimating, 2000);

                        if (!startedMining) {
                            updateStatus("Mining did not start → Hopping world.");
                            int world = Login.getRandomWorld(Rs2Player.isMember());
                            Microbot.hopToWorld(world);
                            updateStatus("Hopped to world: " + world);
                            sleep(2000);
                            continue;
                        }

                        // Wait until mining finishes
                        sleepUntil(() -> !Rs2Player.isAnimating(), 8000);
                        sleep(300); // short delay for inventory to update

                        int oreAfter = Rs2Inventory.count("Runite ore");

                        if (oreAfter > oreBefore) {
                            updateStatus("Successfully mined ore.");
                        }

                        sleep(500);
                    }
                } else if (rock == null) {
                    updateStatus("No rock found → Hopping world.");
                    int world = Login.getRandomWorld(Rs2Player.isMember());
                    Microbot.hopToWorld(world);
                    updateStatus("Hopped to world: " + world);
                    sleep(2000);
                }

                sleep(600);
            }
        }).start();
    }


    private void stopWalking() {
        Microbot.log("Stopping any active pathing...");
        Rs2Walker.setTarget(null); // This clears any current web-walking
    }

    @Override
    public void shutdown() {
        updateStatus("Shutting down script.");
        scriptRunning.set(false);
        stopAllThreads();
        Rs2Antiban.resetAntibanSettings();
        stopWalking();
        super.shutdown();
    }

    private boolean hasEnoughOre(WildernessRuniteMiningConfig config) {
        return Rs2Inventory.count("Runite ore") >= getOreLimit(config);
    }

    private int getOreLimit(WildernessRuniteMiningConfig config) {
        int userSetLimit = config.oreLimit();
        int occupied = 0;
        if (Rs2Inventory.hasItem("Rune pickaxe")) occupied++;
        if (Rs2Inventory.contains(item -> item.getName().toLowerCase().contains(" axe"))) occupied++;
        int maxOreSpace = 28 - occupied;
        int defaultLimit = Microbot.getClient().getRealSkillLevel(net.runelite.api.Skill.PRAYER) >= 25 ? 3 : 2;
        return userSetLimit > 0 ? Math.min(userSetLimit, maxOreSpace) : defaultLimit;
    }

    private void walkToOre() {
        updateStatus("Walking to ore location...");
        Rs2Walker.walkTo(RUNITE_ORE_TILE);
        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(RUNITE_ORE_TILE) < 5, 15000);
    }

    private void walkToFerox() {
        updateStatus("Walking to Ferox Enclave...");
        Rs2Walker.walkTo(FEROX_ENCLAVE_BANK);
        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(FEROX_ENCLAVE_BANK) < 5, 15000);
    }

    private void bankOres() {
        if (Rs2Bank.openBank()) {
            updateStatus("Bank opened. Depositing ores and gems...");
            sleepUntil(Rs2Bank::isOpen, 5000);

            int oreInInventory = Rs2Inventory.count("Runite ore");
            if (oreInInventory > 0) {
                totalMined += oreInInventory;
                updateStatus("Depositing " + oreInInventory + " ore(s). Total mined: " + totalMined);
                Rs2Bank.depositAll("Runite ore");
            }

            String[] uncutGems = {
                    "Uncut sapphire", "Uncut emerald", "Uncut ruby", "Uncut diamond",
                    "Uncut dragonstone", "Uncut onyx", "Uncut opal", "Uncut jade", "Uncut red topaz"
            };

            for (String gem : uncutGems) {
                if (Rs2Inventory.hasItem(gem)) {
                    Rs2Bank.depositAll(gem);
                    sleep(300);
                }
            }

            Rs2Bank.closeBank();
            updateStatus("Banking complete.");
        }
    }

    private void drinkPoolIfAtFerox() {
        if (Rs2Player.getWorldLocation().distanceTo(POOL_OF_REFRESHMENT_TILE) < 20) {
            updateStatus("Drinking from Pool of Refreshment...");
            Rs2Walker.walkTo(POOL_OF_REFRESHMENT_TILE);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(POOL_OF_REFRESHMENT_TILE) < 3, 5000);
            TileObject pool = Rs2GameObject.findObjectById(POOL_OF_REFRESHMENT);
            if (pool != null && Rs2GameObject.interact(pool, "Drink")) {
                sleepUntil(() -> Rs2Player.getRunEnergy() >= 100, 5000);
                sleep(500);
                updateStatus("Recovered run energy at pool.");
            }
        }
    }

    private void handleLumbridgeDeathRecovery() {
        updateStatus("Died → Recovering from Lumbridge");

        boolean hasPickaxe = Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("pickaxe"));
        boolean hasAxe = Rs2Inventory.contains(item -> item.getName().toLowerCase().contains(" axe"));

        if (hasPickaxe && hasAxe) {
            updateStatus("Tools found → Walking to wilderness...");
            Rs2Inventory.interact("Rune pickaxe", "Wield");
            walkToOre();
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(LUMBRIDGE_BANK_TILE) > 5) {
            Rs2Walker.walkTo(LUMBRIDGE_BANK_TILE);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(LUMBRIDGE_BANK_TILE) < 5, 10000);
        }

        // ✅ NEW: Bank runite ore instead of dropping it
        if (Rs2Inventory.hasItem("Runite ore")) {
            updateStatus("Banking ores before withdrawing tools...");
            if (Rs2Bank.openBank()) {
                sleepUntil(Rs2Bank::isOpen, 5000);

                int oreCount = Rs2Inventory.count("Runite ore"); // count before deposit
                Rs2Bank.depositAll("Runite ore");
                totalMined += oreCount; // ✅ add to totalMined
                updateStatus("Deposited " + oreCount + " ores from death recovery. Total mined: " + totalMined);

                sleep(600);
            }
        }


        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen, 5000);

            if (!hasAxe) {
                String[] f2pAxes = {
                        "Rune axe", "Adamant axe", "Mithril axe",
                        "Steel axe", "Black axe", "Iron axe", "Bronze axe"
                };
                for (String axe : f2pAxes) {
                    if (Rs2Bank.hasItem(axe)) {
                        Rs2Bank.withdrawX(axe, 1);
                        updateStatus("Withdrew axe: " + axe);
                        sleep(600);
                        break;
                    }
                }
            }

            if (!hasPickaxe && Rs2Bank.hasItem("Rune pickaxe")) {
                Rs2Bank.withdrawX("Rune pickaxe", 1);
                Rs2Inventory.interact("Rune pickaxe", "Wield");
                updateStatus("Withdrew and wielded Rune pickaxe");
                sleep(600);
            }

            Rs2Bank.closeBank();
        }

        updateStatus("Recovered → Walking back to wilderness...");
        walkToOre();
    }

}
