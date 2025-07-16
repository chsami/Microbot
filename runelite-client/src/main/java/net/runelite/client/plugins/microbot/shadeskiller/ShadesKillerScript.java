package net.runelite.client.plugins.microbot.shadeskiller;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.shadeskiller.enums.State;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.shadeskiller.enums.State.USE_TELEPORT_TO_BANK;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.eatAt;


public class ShadesKillerScript extends Script {
    public static double version = 1.0;
    public static State state = State.BANKING;

    ShadesKillerConfig config;
    List<String> keys = Arrays.asList("Silver key crimson", "Silver key red", "Silver key brown", "Silver key black", "Silver key purple");

    boolean initScript = false;
    boolean resetActions = false;

    boolean coffinHasItems = true;


    private void withdrawCoffin() {
        if (config.useCoffin()) {
            Rs2Bank.withdrawOne("coffin");
        }
    }

    private String getKeyInBank() {
        for (String key : keys) {
            if (Rs2Bank.hasItem(key))
                return key;
        }
        return "";
    }

    private String getKeyInInventory() {
        for (String key : keys) {
            if (Rs2Inventory.hasItem(key))
                return key;
        }
        return "";
    }

    private boolean hasRequiredItemsToKillShades() {
        return Rs2Inventory.hasItem(getKeyInInventory())
                && Rs2Inventory.hasItem(config.teleportItemToShades())
                && Rs2Inventory.hasItem(config.teleportItemToBank())
                && Rs2Inventory.hasItemAmount(config.food().getName(), config.foodAmount(), false, true);
    }

    private boolean withdrawRequiredItems() {
        sleepUntil(Rs2Bank::isOpen);
        Rs2Bank.depositAll();
        sleep(600, 1000);
        String key = getKeyInBank();
        if (key.isEmpty()) {
            Microbot.showMessage("You are missing a silver key.");
            sleep(5000);
            return false;
        }
        Rs2Bank.withdrawOne(key, Rs2Random.between(100, 600));
        Rs2Bank.withdrawOne(config.teleportItemToShades(), Rs2Random.between(100, 600));
        Rs2Bank.withdrawOne(config.teleportItemToBank(), Rs2Random.between(100, 600));
        final int missing = config.foodAmount() - Rs2Inventory.count(config.food().getName(), true);
        if (missing > 0) Rs2Bank.withdrawX(config.food().getName(), missing, true);
        withdrawCoffin();
        sleep(800, 1200);
        return true;
    }

    private void emptyCoffin() {
        if (!config.useCoffin()) return;
        if (!Rs2Inventory.hasItem("coffin")) {
            Rs2Bank.depositAll();
            Rs2Bank.withdrawOne("coffin");
        }
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        sleep(800, 1200);
        Rs2Inventory.interact("coffin", "configure");
        boolean isEmptyCoffin = sleepUntilTrue(() -> Rs2Widget.hasWidget("Empty Coffin."), 100, 3000);
        if (!isEmptyCoffin) return;
        Rs2Widget.clickWidget("Empty Coffin");
        boolean isMakeInterface = sleepUntilTrue(() -> Rs2Widget.hasWidget("What would you like to take?"), 100, 3000);
        if (!isMakeInterface) {
            boolean isCoffinEmpty = Rs2Widget.hasWidget("Your coffin is empty.");
            if (isCoffinEmpty) {
                coffinHasItems = false;
            }
            return;
        }
        Rs2Widget.clickWidget(17694738);
        Rs2Bank.openBank();
        sleepUntil(Rs2Bank::isOpen);
        Rs2Bank.depositAll("fiyr remains");
    }

    public boolean run(ShadesKillerConfig config) {
        this.config = config;
        initScript = true;
        state = State.BANKING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                long startTime = System.currentTimeMillis();

                if (initScript) {
                    if (Rs2Player.getWorldLocation().distanceTo(config.SHADES().location) < 30) {
                        state = State.WALK_TO_SHADES;
                    }
                    initScript = false;
                }

                boolean ate = eatAt(config.eatAt());
                if (ate) {
                    resetActions = true;
                }

                if (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) <= config.hpTreshhold() && Rs2Inventory.hasItem(config.teleportItemToBank())) {
                    state = USE_TELEPORT_TO_BANK;
                }

                switch (state) {
                    case BANKING:
                        if (hasRequiredItemsToKillShades()) {
                            state = State.USE_TELEPORT_TO_SHADES;
                            return;
                        }
                        boolean foundBank = Rs2Bank.openBank();
                        if (!foundBank) {
                            Rs2Bank.walkToBank();
                            return;
                        }
                        if (!Rs2Player.isFullHealth()) {
                            Rs2Bank.depositAll();
                            // Rs2Bank refactor - this can stay unchanged since we just depositedAll
                            Rs2Bank.withdrawX(true, config.food().getName(), config.foodAmount(), true);
                            Rs2Bank.closeBank();
                            sleep(1200);
                            while (!Rs2Player.isFullHealth() && Rs2Inventory.hasItem(config.food().getName(), true)) {
                                eatAt(99);
                                Rs2Player.waitForAnimation();
                            }
                        }
                        if (coffinHasItems && config.useCoffin()) {
                            emptyCoffin();
                            return;
                        }
                        if (Rs2Bank.isOpen()) {
                            boolean result = withdrawRequiredItems();
                            if (!result) return;
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                        }
                        break;
                    case USE_TELEPORT_TO_SHADES:
                        if (Rs2Inventory.hasItem(config.teleportItemToShades())) {
                            Rs2Inventory.interact(config.teleportItemToShades(), config.teleportActionToShades());
                            Rs2Player.waitForAnimation();
                        } else {
                            state = State.WALK_TO_SHADES;
                        }
                        break;
                    case WALK_TO_SHADES:
                        if (config.SHADES().shadeArea.intersectsWith(Rs2Player.getWorldLocation().toWorldArea())) {
                            state = State.FIGHT_SHADES;
                            return;
                        }

                        Rs2Walker.walkTo(config.SHADES().location, 1);
                        break;

                    case USE_TELEPORT_TO_BANK:
                        if (Rs2Inventory.hasItem(config.teleportItemToBank())) {
                            Rs2Inventory.interact(config.teleportItemToBank(),
                                    config.teleportActionToBank());
                            Rs2Player.waitForAnimation();
                        } else {
                            state = State.BANKING;
                        }
                        break;
                    case FIGHT_SHADES:
                        boolean isLooting = Rs2GroundItem.lootAtGePrice(config.priceOfItemsToLoot());
                        if (isLooting) return;
                        var npc = Rs2Npc.getNpcsForPlayer(config.SHADES().names.get(0)).stream().findFirst().orElse(null);
                        //if npc is attacking us, then attack back
                        if (npc != null && !Microbot.getClient().getLocalPlayer().isInteracting()) {
                            Rs2Npc.attack(npc);
                            return;
                        }
                        //if no npc is attacking us, attack a new npc
                        if (!Rs2Combat.inCombat() && !isLooting) {
                            Rs2Npc.attack(config.SHADES().names);
                        }
                        Rs2Combat.setSpecState(true, config.specialAttack() * 10);
                        if (Rs2Inventory.isFull() && config.useCoffin()) {
                            Rs2Inventory.interact("coffin", "fill");
                            coffinHasItems = true;
                            boolean isInventoryNoLongerFull = sleepUntilTrue(() -> !Rs2Inventory.isFull(), 100, 2000);
                            if (!isInventoryNoLongerFull && Rs2Inventory.getInventoryFood().isEmpty()) {
                                state = USE_TELEPORT_TO_BANK;
                            }
                        } else if (Rs2Inventory.isFull() && Rs2Inventory.getInventoryFood().isEmpty()) {
                            state = USE_TELEPORT_TO_BANK;
                        }
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
