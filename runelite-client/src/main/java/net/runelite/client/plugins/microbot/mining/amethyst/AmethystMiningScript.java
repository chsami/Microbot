package net.runelite.client.plugins.microbot.mining.amethyst;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.amethyst.enums.AmethystCraftingOption;
import net.runelite.client.plugins.microbot.mining.amethyst.enums.MiningSpot;
import net.runelite.client.plugins.microbot.mining.amethyst.enums.Status;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class AmethystMiningScript extends Script {
    public static String version = "1.1.1";
    public static Status status = Status.IDLE;
    public static WallObject oreVein;
    private static AmethystMiningConfig config;
    private static MiningSpot miningSpot = MiningSpot.NULL;
    private String pickAxeInInventory = "";

    public boolean run(AmethystMiningConfig config) {
        AmethystMiningScript.config = config;
        initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean isClickHereToPlayButtonVisible() {
        Widget clickHereToPlayButton = Rs2Widget.getWidget(24772680);
        return (clickHereToPlayButton != null && !Microbot.getClientThread().runOnClientThread(clickHereToPlayButton::isHidden));
    }

    private void executeTask() {
        if (!super.run() || !Microbot.isLoggedIn() || isClickHereToPlayButtonVisible() || Rs2Antiban.isIdleTooLong(50)) {
            miningSpot = MiningSpot.NULL;
            oreVein = null;
            return;
        }
        if (config.pickAxeInInventory() && pickAxeInInventory.isEmpty()) {
            pickAxeInInventory = Rs2Inventory.get("pickaxe").name;
        }
        if (pickAxeInInventory.isEmpty() && config.pickAxeInInventory()) {
            Microbot.showMessage("Pickaxe was not found in your inventory");
            sleep(5000);
            return;
        }

        if (Rs2AntibanSettings.actionCooldownActive) return;

        if (Rs2Player.isAnimating() || Microbot.getClient().getLocalPlayer().isInteracting()) return;

        handleDragonPickaxeSpec();
        handleInventory();

        switch (status) {
            case IDLE:
                return;
            case MINING:
                handleMining();
                break;
            case BANKING:
                bankItems();
                break;
            case CHISELING:
                chiselAmethysts();
                break;
        }
    }

    private void handleDragonPickaxeSpec() {
        if (Rs2Equipment.isWearing("dragon pickaxe")) {
            Rs2Combat.setSpecState(true, 1000);
        }
    }

    private void handleInventory() {
        if (!Rs2Inventory.isFull()) {
            status = Status.MINING;
        } else {
            oreVein = null;
            miningSpot = MiningSpot.NULL;
            if (config.chiselAmethysts())
                status = Status.CHISELING;
            else
                status = Status.BANKING;
        }
    }

    private void bankItems() {
        if (Rs2Walker.walkTo(BankLocation.MINING_GUILD.getWorldPoint()))
            bank();
    }

    private void bank() {
        TileObject bank = Rs2GameObject.findObjectById(4483);
        if (Rs2Bank.openBank(bank)) {
            sleepUntil(Rs2Bank::isOpen);
            Rs2Bank.depositAllExcept(pickAxeInInventory);
            sleep(100, 300);

            if (config.pickAxeInInventory() && !Rs2Inventory.hasItem(pickAxeInInventory)) {
                Rs2Bank.withdrawOne(pickAxeInInventory);
            }
            sleep(600);
        }
    }


    private void chiselAmethysts() {
        AmethystCraftingOption craftingOption = config.amethystCraftingOption();
        int requiredLevel = craftingOption.getRequiredLevel();
        Rs2Item chisel = Rs2Inventory.get("chisel");
        Rs2Inventory.moveItemToSlot(chisel, 27);
        sleepUntil(() -> Rs2Inventory.slotContains(27, "chisel"), 5000);
        if (Microbot.getClient().getRealSkillLevel(Skill.CRAFTING) >= requiredLevel) {
            Rs2Inventory.combineClosest(ItemID.CHISEL, ItemID.AMETHYST);
            sleepUntil(() -> Rs2Widget.getWidget(17694733) != null);
            Rs2Keyboard.keyPress(craftingOption.getDialogOption());
            sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.AMETHYST), 40000);
            Rs2Antiban.takeMicroBreakByChance();

        } else {
            Microbot.showMessage("You do not have the required crafting level to make " + craftingOption.getDisplayName());
            status = Status.BANKING;
        }
    }

    private void handleMining() {
        if (oreVein != null) return;
        if (miningSpot == MiningSpot.NULL)
            miningSpot = MiningSpot.getRandomMiningSpot();
        else {
            if (walkToMiningSpot()) {
                if (Rs2Player.isMoving()) return;
                mineVein();
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }

    }

    private boolean mineVein() {
        if (Rs2Player.isMoving()) return false;

        WallObject closestVein = findClosestVein();
        if (closestVein == null) {
            moveToMiningSpot();
            return false;
        }

        interactWithVein(closestVein);
        return true;
    }

    private WallObject findClosestVein() {
        return Rs2GameObject.getWallObjects().stream()
                .filter(this::isVein)
                .sorted(Comparator.comparingInt(this::distanceToPlayer))
                .filter(Rs2GameObject::hasLineOfSight)
                .findFirst()
                .orElse(null);
    }

    private boolean isVein(WallObject wallObject) {
        int id = wallObject.getId();
        return id == 11388 || id == 11389;
    }

    private int distanceToPlayer(WallObject wallObject) {
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(wallObject.getWorldLocation());
    }

    private void interactWithVein(WallObject vein) {
        if (Rs2GameObject.interact(vein))
            oreVein = vein;
        sleepUntil(Rs2Player::isAnimating, 10000);
        if (!Rs2Player.isAnimating()) {
            oreVein = null;
        }
    }

    private boolean walkToMiningSpot() {
        WorldPoint miningWorldPoint = miningSpot.getWorldPoint();
        return Rs2Walker.walkTo(miningWorldPoint, 5);
    }

    private void moveToMiningSpot() {
        Rs2Walker.walkFastCanvas(miningSpot.getWorldPoint());
    }

    private void initialize() {
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        status = Status.IDLE;
        miningSpot = MiningSpot.NULL;
        oreVein = null;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        status = Status.IDLE;
        miningSpot = MiningSpot.NULL;
        oreVein = null;
    }
}
