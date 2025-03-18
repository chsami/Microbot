package net.runelite.client.plugins.microbot.liftedmango.herbrun;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.ScriptItem;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.herbrun.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.herbrun.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.herbrun.FarmingWorld;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.farming.CropState;

import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class HerbrunScript extends Script {
    @Inject
    private ConfigManager configManager;
    @Inject
    private FarmingWorld farmingWorld;
    private FarmingHandler farmingHandler;
    private final HerbrunConfig config;
    private HerbPatch currentPatch;

    @Inject
    public HerbrunScript(HerbrunConfig config) {
        this.config = config;
    }

    private final List<HerbPatch> herbPatches = new ArrayList<>();

    public boolean run() {
        this.farmingHandler = new FarmingHandler(Microbot.getClient(), configManager);
        herbPatches.clear();
        Microbot.getClientThread().runOnClientThread(() -> {
            for (FarmingPatch patch : farmingWorld.getTabs().get(Tab.HERB)) {
                HerbPatch _patch = new HerbPatch(patch, config, farmingHandler);
                if (_patch.getPrediction() != CropState.GROWING && _patch.isEnabled()) herbPatches.add(_patch);
            }
            return true;
        });
        if (herbPatches.isEmpty()) {
            log("No herb patches ready to farm");
            shutdown();
            return true;
        }
        log("Will visit " + herbPatches.size() + " herb patches");

        // Basic items
        requiredItems.add(ScriptItem.builder().name("Spade").build());
        requiredItems.add(ScriptItem.builder().name("Seed dibber").build());
        requiredItems.add(ScriptItem.builder().name("Rake").build());

        // Compost handling
        if (config.COMPOST()) {
            requiredItems.add(ScriptItem.builder().name("Bottomless compost bucket").build());
        } else {
            requiredItems.add(ScriptItem.builder().name("Ultracompost").quantity(herbPatches.size()).build());
        }


        // Add items from herb patches
        for (HerbPatch patch : herbPatches) {
            for (Map.Entry<String, Integer> entry : patch.getItems().entrySet()) {
                String itemName = entry.getKey();
                int itemAmount = entry.getValue();

                // Check if we already have this item in our list
                boolean found = false;
                for (ScriptItem item : requiredItems) {
                    if (item.getName().equals(itemName)) {
                        // If found, add amounts
                        item.setQuantity(item.getQuantity() + itemAmount);
                        found = true;
                        break;
                    }
                }

                // If not found, add a new item
                if (!found) {
                    requiredItems.add(ScriptItem.builder().name(itemName).quantity(itemAmount).build());
                }
            }
        }

        // Seeds
        requiredItems.add(ScriptItem.builder().id(config.SEED().getItemId()).quantity(herbPatches.size()).build());


        // Add equipment with equipped flag
        requiredItems.add(ScriptItem.builder().name("Magic secateurs").equipped(true).build());
        // Add graceful items if needed
        if (config.GRACEFUL()) {
            requiredItems.add(ScriptItem.builder().name("Graceful gloves").equipped(true).build());
            requiredItems.add(ScriptItem.builder().name("Graceful hood").equipped(true).build());
            requiredItems.add(ScriptItem.builder().name("Graceful cape").equipped(true).build());
            requiredItems.add(ScriptItem.builder().name("Graceful top").equipped(true).build());
            requiredItems.add(ScriptItem.builder().name("Graceful legs").equipped(true).build());
            requiredItems.add(ScriptItem.builder().name("Graceful boots").equipped(true).build());
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn() || !super.run()) return;

            if (Rs2Inventory.hasItem("Weeds")) {
                Rs2Inventory.drop("Weeds");
            }
            if (currentPatch == null) getNextPatch();
            if (currentPatch == null) shutdown();

            if (currentPatch.isInRange(20)) {
                if (handleHerbPatch()) getNextPatch();
            } else {
                boolean arrived = Rs2Walker.walkTo(currentPatch.getLocation(), 20);
                sleepUntil(() -> arrived);
            }


        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }
    private void getNextPatch() {

        if (currentPatch == null) {
            if (herbPatches.isEmpty()) {
                return;
            }
            currentPatch = herbPatches.remove(0);
        }
    }

    private boolean handleHerbPatch() {
        if (Rs2Inventory.isFull()) {
        Rs2NpcModel leprechaun = Rs2Npc.getNpc("Tool leprechaun");
        if (leprechaun != null) {
            Rs2ItemModel unNoted = Rs2Inventory.getUnNotedItem("Grimy", false);
            Rs2Inventory.use(unNoted);
            Rs2Npc.interact(leprechaun);
            Rs2Inventory.waitForInventoryChanges(10000);
        }
            return false;
        }

        Integer[] ids = {
                18816,
                8151,
                8153,
                50697,
                27115,
                8152,
                8150,
                33979,
                33176,
                9372
        };
        var obj = Rs2GameObject.findObject(ids);
        if (obj == null) return false;
        var state = getHerbPatchState(obj);
        switch (state) {
            case "Empty":
                Rs2Inventory.use("Bottomless compost bucket");
                Rs2GameObject.interact(obj);
                Rs2Player.waitForXpDrop(Skill.FARMING);
                Rs2Inventory.use(config.SEED().getItemId());
                Rs2GameObject.interact(obj);
                sleepUntil(() -> getHerbPatchState(obj).equals("Growing"));
                return false;
            case "Harvestable":
                Rs2GameObject.interact(obj, "Pick");
                Rs2Player.waitForWalking();
                sleepUntil(() -> getHerbPatchState(obj).equals("Empty") || Rs2Inventory.isFull(), 20000);
                return false;
            case "Weeds":
                Rs2GameObject.interact(obj);
                Rs2Player.waitForAnimation(10000);
                return false;
            case "Dead":
                Rs2GameObject.interact(obj, "Clear");
                sleepUntil(() -> getHerbPatchState(obj).equals("Empty"));
                return false;
            default:
                currentPatch = null;
                return true;
        }
    }

    private static String getHerbPatchState(TileObject rs2TileObject) {
        var game_obj = Rs2GameObject.convertGameObjectToObjectComposition(rs2TileObject);
        var varbitValue = Microbot.getVarbitValue(game_obj.getVarbitId());

        if ((varbitValue >= 0 && varbitValue < 3) ||
                (varbitValue >= 60 && varbitValue <= 67) ||
                (varbitValue >= 173 && varbitValue <= 191) ||
                (varbitValue >= 204 && varbitValue <= 219) ||
                (varbitValue >= 221 && varbitValue <= 255)) {
            return "Weeds";
        }

        if ((varbitValue >= 4 && varbitValue <= 7) ||
                (varbitValue >= 11 && varbitValue <= 14) ||
                (varbitValue >= 18 && varbitValue <= 21) ||
                (varbitValue >= 25 && varbitValue <= 28) ||
                (varbitValue >= 32 && varbitValue <= 35) ||
                (varbitValue >= 39 && varbitValue <= 42) ||
                (varbitValue >= 46 && varbitValue <= 49) ||
                (varbitValue >= 53 && varbitValue <= 56) ||
                (varbitValue >= 68 && varbitValue <= 71) ||
                (varbitValue >= 75 && varbitValue <= 78) ||
                (varbitValue >= 82 && varbitValue <= 85) ||
                (varbitValue >= 89 && varbitValue <= 92) ||
                (varbitValue >= 96 && varbitValue <= 99) ||
                (varbitValue >= 103 && varbitValue <= 106) ||
                (varbitValue >= 192 && varbitValue <= 195)) {
            return "Growing";
        }

        if ((varbitValue >= 8 && varbitValue <= 10) ||
                (varbitValue >= 15 && varbitValue <= 17) ||
                (varbitValue >= 22 && varbitValue <= 24) ||
                (varbitValue >= 29 && varbitValue <= 31) ||
                (varbitValue >= 36 && varbitValue <= 38) ||
                (varbitValue >= 43 && varbitValue <= 45) ||
                (varbitValue >= 50 && varbitValue <= 52) ||
                (varbitValue >= 57 && varbitValue <= 59) ||
                (varbitValue >= 72 && varbitValue <= 74) ||
                (varbitValue >= 79 && varbitValue <= 81) ||
                (varbitValue >= 86 && varbitValue <= 88) ||
                (varbitValue >= 93 && varbitValue <= 95) ||
                (varbitValue >= 100 && varbitValue <= 102) ||
                (varbitValue >= 107 && varbitValue <= 109) ||
                (varbitValue >= 196 && varbitValue <= 197)) {
            return "Harvestable";
        }

        if ((varbitValue >= 128 && varbitValue <= 169) ||
                (varbitValue >= 198 && varbitValue <= 200)) {
            return "Diseased";
        }

        if ((varbitValue >= 170 && varbitValue <= 172) ||
                (varbitValue >= 201 && varbitValue <= 203)) {
            return "Dead";
        }

        return "Empty";
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
