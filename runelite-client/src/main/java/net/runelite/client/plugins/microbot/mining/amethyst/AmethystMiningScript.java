package net.runelite.client.plugins.microbot.mining.amethyst;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.amethyst.enums.AmethystCraftingOption;
import net.runelite.client.plugins.microbot.mining.amethyst.enums.MiningSpot;
import net.runelite.client.plugins.microbot.mining.amethyst.enums.Status;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class AmethystMiningScript extends Script {
    public static String version = "1.2.0";
    public static Status status = Status.IDLE;
    public static boolean lockedStatus = false;
    public static WallObject oreVein;
    private static AmethystMiningConfig config;
    private static MiningSpot miningSpot = MiningSpot.NULL;
    private String pickAxeInInventory = "";
    public static final String gemBag = "Gem bag";
    public static final String openGemBag = "Open gem bag";
    public static final String chisel = "Chisel";
    public static ArrayList<String> itemsToKeep = new ArrayList<>();
    public static int inventoryCountSinceLastGemBagCheck = 0;

    public boolean run(AmethystMiningConfig config) {
        AmethystMiningScript.config = config;
        initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }



    private void executeTask() {
        try {
            if (!super.run() || !Microbot.isLoggedIn()) {
                miningSpot = MiningSpot.NULL;
                oreVein = null;
                return;
            }
            if (config.pickAxeInInventory() && pickAxeInInventory.isEmpty()) {
                pickAxeInInventory = Rs2Inventory.get("pickaxe").name;
                if (!pickAxeInInventory.isEmpty()) {
                    itemsToKeep.add(pickAxeInInventory);
                }
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
        } catch (Exception e) {
            Microbot.log("Error in AmethystMiningScript: " + e.getMessage());
        }
    }

    private void handleDragonPickaxeSpec() {
        if (Rs2Equipment.isWearing("dragon pickaxe")) {
            Rs2Combat.setSpecState(true, 1000);
        }
    }

    private void handleInventory() {
        if(lockedStatus){
            oreVein = null;
            miningSpot = MiningSpot.NULL;
            return;
        }
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

            Rs2Bank.depositAllExcept(itemsToKeep);
            if (config.gemBag() && inventoryCountSinceLastGemBagCheck >= 5) {
                Rs2Bank.emptyGemBag();
                inventoryCountSinceLastGemBagCheck = 0;
            }

            sleep(100, 300);

            if (config.pickAxeInInventory() && !Rs2Inventory.hasItem(pickAxeInInventory)) {
                Rs2Bank.withdrawOne(pickAxeInInventory);
            }
            if (config.gemBag() && !(Rs2Inventory.hasItem(gemBag) || Rs2Inventory.hasItem(openGemBag))) {
                Rs2Bank.withdrawOne(gemBag);
                Rs2Bank.withdrawOne(openGemBag);
            }
            if (config.chiselAmethysts() && !Rs2Inventory.hasItem(chisel)) {
                Rs2Bank.withdrawOne(chisel);
            }
            sleep(600);
            lockedStatus = false;
        }
    }


    private void chiselAmethysts() {
        AmethystCraftingOption craftingOption = config.amethystCraftingOption();
        int requiredLevel = craftingOption.getRequiredLevel();
        Rs2ItemModel chisel = Rs2Inventory.get("chisel");
        Rs2Inventory.moveItemToSlot(chisel, 27);
        sleepUntil(() -> Rs2Inventory.slotContains(27, "chisel"), 5000);
        if (Microbot.getClient().getRealSkillLevel(Skill.CRAFTING) >= requiredLevel ) {
            Rs2Inventory.combineClosest(ItemID.CHISEL, ItemID.AMETHYST);
            sleepUntil(() -> Rs2Widget.getWidget(17694733) != null);
            Rs2Keyboard.keyPress(craftingOption.getDialogOption());
            sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.AMETHYST), 40000);
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            inventoryCountSinceLastGemBagCheck++;
            if(inventoryCountSinceLastGemBagCheck >= 5) {
                status = Status.BANKING;
                lockedStatus = true;
            }


        } else {
            Microbot.showMessage("You do not have the required crafting level to make " + craftingOption.getDisplayName());
            status = Status.BANKING;
        }
    }

    private void handleMining() {
        if (oreVein != null && AntibanPlugin.isMining()) return;
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
                .filter(this::isVein).min((a, b) -> Integer.compare(distanceToPlayer(a), distanceToPlayer(b))).orElse(null);
    }

    private boolean isVein(WallObject wallObject) {
        int id = wallObject.getId();
        return id == 11388 || id == 11389;
    }

    private int distanceToPlayer(WallObject wallObject) {
        WorldPoint closestWalkableNeighbour = Rs2Tile.getNearestWalkableTile(wallObject.getWorldLocation());
        if (closestWalkableNeighbour == null) return 999;
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo2D(closestWalkableNeighbour);
    }

    private void interactWithVein(WallObject vein) {
        if (Rs2GameObject.interact(vein))
            oreVein = vein;
        sleepUntil(AntibanPlugin::isMining, 5000);
        if (!AntibanPlugin.isMining()) {
            oreVein = null;
        }
    }

    private boolean walkToMiningSpot() {
        WorldPoint miningWorldPoint = miningSpot.getWorldPoint();
        return Rs2Walker.walkTo(miningWorldPoint, 8);
    }

    private void moveToMiningSpot() {
        Rs2Walker.walkFastCanvas(miningSpot.getWorldPoint());
    }

    private void initialize() {
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        status = Status.IDLE;
        miningSpot = MiningSpot.NULL;
        oreVein = null;
        if (config.gemBag()) {
            itemsToKeep.add(gemBag);
            itemsToKeep.add(openGemBag);
        }
        if (config.chiselAmethysts()) {
            itemsToKeep.add(chisel);
        }
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
