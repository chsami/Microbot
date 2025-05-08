package net.runelite.client.plugins.microbot.birdhouseruns;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.config.Notification;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsInfo.states;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.birdhouseruns.FornBirdhouseRunsInfo.*;

public class FornBirdhouseRunsScript extends Script {
    private static final WorldPoint birdhouseLocation1 = new WorldPoint(3763, 3755, 0);
    private static final WorldPoint birdhouseLocation2 = new WorldPoint(3768, 3761, 0);
    private static final WorldPoint birdhouseLocation3 = new WorldPoint(3677, 3882, 0);
    private static final WorldPoint birdhouseLocation4 = new WorldPoint(3679, 3815, 0);
    public static double version = 1.0;
    private boolean initialized;
    @Inject
    private Notifier notifier;
    private final FornBirdhouseRunsPlugin plugin;
    private final FornBirdhouseRunsConfig config;

    @Inject
    FornBirdhouseRunsScript(FornBirdhouseRunsPlugin plugin, FornBirdhouseRunsConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = true;
        botStatus = states.GEARING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!initialized) {
                    initialized = true;
                    var inventorySetup = new Rs2InventorySetup(config.inventorySetup(), mainScheduledFuture);
                    if (!inventorySetup.doesInventoryMatch() || !inventorySetup.doesEquipmentMatch()) {
                        Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint(), 20);
                        if (!inventorySetup.loadEquipment() || !inventorySetup.loadInventory()) {
                            Microbot.log("Failed to load inventory setup");
                            plugin.reportFinished("Birdhouse run failed to load inventory setup",false);                                                        
                            this.shutdown();
                            return;
                        }
                        if (Rs2Bank.isOpen()) Rs2Bank.closeBank();
                    }
                    botStatus = states.TELEPORTING;
                }
                if (!super.run()) return;

                switch (botStatus) {
                    case TELEPORTING:
                        Rs2Walker.walkTo(new WorldPoint(3764, 3869, 1), 5);
                        botStatus = states.VERDANT_TELEPORT;
                        break;
                    case VERDANT_TELEPORT:
                        interactWithObject(30920);
                        sleepUntil(() -> Rs2Widget.findWidget("Mycelium Transportation System") != null);
                        Rs2Widget.clickWidget(39845895);
                        sleepUntil(() -> Rs2Player.distanceTo(birdhouseLocation1) < 20);
                        botStatus = states.DISMANTLE_HOUSE_1;
                        break;
                    case DISMANTLE_HOUSE_1:
                        dismantleBirdhouse(30568, states.BUILD_HOUSE_1);
                        break;
                    case BUILD_HOUSE_1:
                        buildBirdhouse(birdhouseLocation1, states.SEED_HOUSE_1);
                        break;
                    case SEED_HOUSE_1:
                        seedHouse(birdhouseLocation1, states.DISMANTLE_HOUSE_2);
                    case DISMANTLE_HOUSE_2:
                        dismantleBirdhouse(30567, states.BUILD_HOUSE_2);
                        break;
                    case BUILD_HOUSE_2:
                        buildBirdhouse(birdhouseLocation2, states.SEED_HOUSE_2);
                        break;
                    case SEED_HOUSE_2:
                        seedHouse(birdhouseLocation2, states.MUSHROOM_TELEPORT);
                        break;
                    case MUSHROOM_TELEPORT:
                        interactWithObject(30924);
                        sleepUntil(() -> Rs2Widget.findWidget("Mycelium Transportation System") != null);
                        Rs2Widget.clickWidget(39845903);
                        sleepUntil(() -> Rs2Player.distanceTo(birdhouseLocation3) < 20);
                        botStatus = states.DISMANTLE_HOUSE_3;
                        break;
                    case DISMANTLE_HOUSE_3:
                        dismantleBirdhouse(30565, states.BUILD_HOUSE_3);
                        break;
                    case BUILD_HOUSE_3:
                        buildBirdhouse(birdhouseLocation3, states.SEED_HOUSE_3);
                        break;
                    case SEED_HOUSE_3:
                        seedHouse(birdhouseLocation3, states.DISMANTLE_HOUSE_4);
                        break;
                    case DISMANTLE_HOUSE_4:
                        Rs2Walker.walkTo(new WorldPoint(3680, 3813, 0));
                        dismantleBirdhouse(30566, states.BUILD_HOUSE_4);
                        break;
                    case BUILD_HOUSE_4:
                        buildBirdhouse(birdhouseLocation4, states.SEED_HOUSE_4);
                        break;
                    case SEED_HOUSE_4:
                        seedHouse(birdhouseLocation4, states.FINISHING);
                        break;
                    case FINISHING:
                        if (config.goToBank()) {
                            Rs2Walker.walkTo(Rs2Bank.getNearestBank().getWorldPoint());
                            emptyNests();
                            if (!Rs2Bank.isOpen()) Rs2Bank.openBank();
                            Rs2Bank.depositAll();
                        }

                        botStatus = states.FINISHED;
                        notifier.notify(Notification.ON, "Birdhouse run is finished.");
                        plugin.reportFinished("Birdhouse run finished",true);
                        this.shutdown();
                        break;
                    case FINISHED:

                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void emptyNests() {
        var ids = List.of(
                ItemID.BIRD_NEST,
                ItemID.BIRD_NEST_5071,
                ItemID.BIRD_NEST_5072,
                ItemID.BIRD_NEST_5073,
                ItemID.BIRD_NEST_5074,
                ItemID.BIRD_NEST_22798,
                ItemID.BIRD_NEST_22800
        );

        for (Rs2ItemModel item : Rs2Inventory.items()) {
            if (ids.contains(item.id)) {
                Rs2Inventory.interact(item, "Search");
            }
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        initialized = false;
        botStatus = states.TELEPORTING;
    }

    private boolean interactWithObject(int objectId) {
        Rs2GameObject.interact(objectId);
        sleepUntil(Rs2Player::isInteracting);
        sleepUntil(() -> !Rs2Player.isInteracting());
        return true;
    }

    private void seedHouse(WorldPoint worldPoint, states status) {
        Rs2Inventory.use(" seed");
        sleepUntil(Rs2Inventory::isItemSelected);
        Rs2GameObject.interact(worldPoint);
        sleepUntil(() -> Rs2Widget.findWidget("full of seed") != null, 1000);
        botStatus = status;
    }

    private void buildBirdhouse(WorldPoint worldPoint, states status) {
        if (!Rs2Inventory.hasItem("bird house") && Rs2Inventory.hasItem(ItemID.CLOCKWORK)) {
            Rs2Inventory.use(ItemID.HAMMER);
            Rs2Inventory.use(" logs");
            Rs2Inventory.waitForInventoryChanges(5000);
        }
        Rs2GameObject.interact(worldPoint, "Build");
        sleepUntil(Rs2Player::isAnimating);
        botStatus = status;
    }

    private void dismantleBirdhouse(int itemId, states status) {
        Rs2GameObject.interact(itemId, "Empty");
        Rs2Player.waitForXpDrop(Skill.HUNTER);
        botStatus = status;
    }
}
