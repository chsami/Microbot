//package net.runelite.client.plugins.microbot.gboc.scripts;
//
//import net.runelite.api.Skill;
//import net.runelite.api.TileObject;
//import net.runelite.api.coords.WorldPoint;
//import net.runelite.api.widgets.ComponentID;
//import net.runelite.client.plugins.microbot.Microbot;
//import net.runelite.client.plugins.microbot.Script;
//import net.runelite.client.plugins.microbot.gboc.GbocConfig;
//import net.runelite.client.plugins.microbot.gboc.GbocPlugin;
//import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
//import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
//import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
//import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
//import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
//import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
//import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
//import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
//import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
//import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
//import net.runelite.client.plugins.microbot.util.player.Rs2Player;
//import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
//import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
//
//
//import java.util.List;
//import java.util.Arrays;
//import java.util.Objects;
//import java.util.concurrent.TimeUnit;
//import javax.inject.Inject;
//
//
//public class HerbRunScript extends Script {
//    private final GbocConfig config;
//
//    @Inject
//    public HerbRunScript(GbocConfig config) {
//        this.config = config;
//    }
//
//    public enum State {
//        IDLE,
//        EMPTY_INV,
//        WITHDRAW,
//        FARMING,
//    }
//    private State currentState = State.IDLE;
//    private static class TeleportData {
//        String itemName;
//        String actionName;
//        Integer[] region;
//        WorldPoint patchLocation;
//        boolean completed;
//
//        public TeleportData(String itemName, String actionName, Integer[] region, WorldPoint patchLocation) {
//            this.itemName = itemName;
//            this.actionName = actionName;
//            this.region = region;
//            this.patchLocation = patchLocation;
//            this.completed = false;
//        }
//    }
//
//    private static final String[] EQUIPMENT = {
//        "Graceful gloves",
//        "Graceful hood",
//        "Graceful cape",
//        "Graceful top",
//        "Graceful legs",
//        "Graceful boots",
//        "Magic secateurs",
//    };
//    private static final String[] ITEMS = {
//        "Spade",
//        "Seed dibber",
//        "Rake",
//        "Bottomless compost bucket",
//    };
//
//    private List<TeleportData> TELEPORT_DATA = Arrays.asList(
//        new TeleportData("Xeric's talisman", "Xeric's Glade", new Integer[]{6967}, new WorldPoint(1739, 3551, 0)),
//        new TeleportData("Explorer's ring", "Teleport", new Integer[]{12083}, new WorldPoint(3058, 3311, 0)),
//        new TeleportData("Ectophial", "Empty", new Integer[]{14391, 14647}, new WorldPoint(3605, 3527, 0)),
//        new TeleportData("Ardougne cloak", "Farm Teleport", new Integer[]{10548}, new WorldPoint(2670, 3375, 0)),
//        new TeleportData("Skills necklace(", "Farming Guild", new Integer[]{4922}, new WorldPoint(1238, 3727, 0)),
//        new TeleportData("Icy basalt", "Weiss", new Integer[]{11325}, new WorldPoint(2848, 3935, 0)),
//        new TeleportData("Camelot teleport", "Break", new Integer[]{11062}, new WorldPoint(2813, 3464, 0)),
//        new TeleportData("Stony basalt", "Troll Stronghold", new Integer[]{11321}, new WorldPoint(2827, 3694, 0))
//    );
//
//    public boolean run() {
//        currentState = State.IDLE;
//        Rs2Antiban.resetAntibanSettings();
//        Rs2AntibanSettings.naturalMouse = true;
//        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
//            if (!Microbot.isLoggedIn() || !super.run() || GbocPlugin.currentAction.isRunning() || Rs2AntibanSettings.actionCooldownActive) return;
//
//            switch (currentState) {
//                case IDLE:
//                    handleIdle();
//                    break;
//                case EMPTY_INV:
//                    handleEmptyInventory();
//                    break;
//                case WITHDRAW:
//                     handleWithdraw();
//                     break;
//                case FARMING:
//                    handleFarming();
//                    break;
//                default:
//                    currentState = State.IDLE;
//            }
//
//        }, 0, 100, TimeUnit.MILLISECONDS);
//        return true;
//    }
//
//    public void shutdown() {
//        currentState = State.IDLE;
//        GbocPlugin.currentAction.reset();
//        super.shutdown();
//    }
//
//    private void handleIdle() {
//        if(isNearBank() && !Rs2Bank.isOpen()) {
//            GbocPlugin.currentAction.set("Open bank", Rs2Bank::openBank);
//            return;
//        }
//        currentState = State.EMPTY_INV;
//    }
//
//    private void handleEmptyInventory() {
//        if(Rs2Bank.isOpen()) {
//            if(!Rs2Inventory.isEmpty()) {
//                GbocPlugin.currentAction.set("Bank Inventory", Rs2Bank::depositAll);
//                return;
//            }
//            if(!Rs2Equipment.isNaked()) {
//                GbocPlugin.currentAction.set("Bank Equipment", Rs2Bank::depositEquipment);
//                return;
//            }
//            currentState = State.WITHDRAW;
//            return;
//        }
//        currentState = State.IDLE;
//    }
//
//    private void handleWithdraw() {
//        for (String itemName : EQUIPMENT ) {
//            if (!Rs2Equipment.isWearing(itemName)) {
//                GbocPlugin.currentAction.set(itemName, () -> Rs2Bank.withdrawAndEquip(itemName));
//                return;
//            }
//        }
//        for (String itemName : ITEMS) {
//            if (!Rs2Inventory.hasItem(itemName)) {
//                GbocPlugin.currentAction.set(itemName, () -> Rs2Bank.withdrawItem(itemName));
//                return;
//            }
//        }
//
//        if (!Rs2Inventory.hasItem(config.herbRunSeed())) {
//            GbocPlugin.currentAction.set(config.herbRunSeed(), () -> Rs2Bank.withdrawX(config.herbRunSeed(), TELEPORT_DATA.size()));
//            return;
//        }
//
//        for (TeleportData data : TELEPORT_DATA) {
//            if (!Rs2Inventory.hasItem(data.itemName) && Rs2Bank.hasItem(data.itemName)) {
//                GbocPlugin.currentAction.set(data.itemName, () -> Rs2Bank.withdrawItem(data.itemName));
//                return;
//            }
//        }
//
//        if (Rs2Bank.isOpen()) {
//            GbocPlugin.currentAction.set("Close bank", Rs2Bank::closeBank);
//            return;
//        }
//        currentState = State.FARMING;
//    }
//
//    private void handleFarming() {
//        if (Rs2Inventory.hasItem("Weeds")) {
//            GbocPlugin.currentAction.set("Drop Weeds", () -> Rs2Inventory.drop("Weeds"));
//            return;
//        }
//        for (TeleportData data : TELEPORT_DATA) {
//            if (data.completed) continue;
//
//            if (Arrays.stream(data.region).noneMatch(regionId -> regionId == Rs2Player.getWorldLocation().getRegionID())) {
//                Runnable action = () -> Rs2Inventory.interact(data.itemName, data.actionName);
//                if (Objects.equals(data.actionName, "Xeric's Glade")) {
//                    action = () -> interactWithRub(data, 2);
//                }
//                if (Objects.equals(data.actionName, "Farming Guild")) {
//                    action = () -> interactWithRub(data, 6);
//                }
//                Runnable finalAction = action;
//                GbocPlugin.currentAction.set(data.actionName + " " + data.itemName, () -> {
//                    finalAction.run();
//                    sleepUntil(() -> Arrays.stream(data.region).anyMatch(regionId -> regionId == Rs2Player.getWorldLocation().getRegionID()));
//                });
//
//            } else {
//                if(Rs2Player.distanceTo(data.patchLocation) > 10){
//                    GbocPlugin.currentAction.set("Walk to patch", () -> Rs2Walker.walkTo(data.patchLocation));
//                    return;
//                }
//                data.completed = handleHerbPatch();
//            }
//            return;
//        }
//    }
//
//    private void interactWithRub(TeleportData data, int selection){
//        Rs2Inventory.interact(data.itemName, "Rub");
//        sleepUntil(() -> Rs2Widget.getWidget(ComponentID.ADVENTURE_LOG_CONTAINER) != null, 10000); //FIX this is broken
//        Rs2Keyboard.keyPress(selection);
//    }
//
//    private boolean handleHerbPatch() {
//
//        if (Rs2Inventory.isFull()) {
//            GbocPlugin.currentAction.set("Note herbs", () -> {
//
//                Rs2NpcModel leprechaun = Rs2Npc.getNpc("Tool leprechaun");
//                if (leprechaun != null) {
//                    Rs2ItemModel unNoted = Rs2Inventory.get( item -> !item.isNoted() && Objects.equals(item.name, "Grimy snapdragon"));
//                    Rs2Inventory.useItemOnNpc(unNoted, leprechaun);
//                }
//            });
//            return false;
//        }
//
//        Integer[] ids = {
//            18816,
//            8151,
//            8153,
//            50697,
//            27115,
//            8152,
//            8150,
//            33979,
//            33176,
//            9372
//        };
//        var obj = Rs2GameObject.findObject(ids);
//        if (obj == null) return true;
//        var state = getHerbPatchState(obj);
//        switch (state) {
//            case "Empty":
//                GbocPlugin.currentAction.set("Compost and seed", () -> {
//                    Rs2Inventory.use("Bottomless compost bucket");
//                    Rs2GameObject.interact(obj);
//                    Rs2Player.waitForXpDrop(Skill.FARMING);
//                    Rs2Inventory.use(config.herbRunSeed());
//                    Rs2GameObject.interact(obj);
//                    sleepUntil( () -> getHerbPatchState(obj).equals("Growing"));
//                });
//                return false;
//            case "Harvestable":
//                GbocPlugin.currentAction.set("Harvest", () -> {
//                    Rs2GameObject.interact(obj);
//                    Rs2Player.waitForWalking();
//                    Rs2Player.waitForAnimation(10000);
//                });
//                return false;
//            case "Weeds":
//                GbocPlugin.currentAction.set("Remove weeds", () -> {
//                    Rs2GameObject.interact(obj);
//                    Rs2Player.waitForAnimation(10000);
//                });
//                return false;
//            //FIX HANDLE DISEASED AND DEAD HERBS
//            default:
//                return true;
//        }
//    }
//
//    /**
//     * Determines the state of a herb patch based on the varbit value.
//     *
//     * @param rs2TileObject The TileObject we are checking
//     * @return String representation of the state: "Weed", "Growing", "Harvestable", or "Diseased"
//     */
//    private static String getHerbPatchState(TileObject rs2TileObject) {
//        var game_obj = Rs2GameObject.convertGameObjectToObjectComposition(rs2TileObject);
//        var varbitValue = Microbot.getVarbitValue(game_obj.getVarbitId());
//
//        if ((varbitValue >= 0 && varbitValue < 3) ||
//                (varbitValue >= 60 && varbitValue <= 67) ||
//                (varbitValue >= 173 && varbitValue <= 191) ||
//                (varbitValue >= 204 && varbitValue <= 219) ||
//                (varbitValue >= 221 && varbitValue <= 255)) {
//            return "Weeds";
//        }
//
//        if ((varbitValue >= 4 && varbitValue <= 7) ||
//                (varbitValue >= 11 && varbitValue <= 14) ||
//                (varbitValue >= 18 && varbitValue <= 21) ||
//                (varbitValue >= 25 && varbitValue <= 28) ||
//                (varbitValue >= 32 && varbitValue <= 35) ||
//                (varbitValue >= 39 && varbitValue <= 42) ||
//                (varbitValue >= 46 && varbitValue <= 49) ||
//                (varbitValue >= 53 && varbitValue <= 56) ||
//                (varbitValue >= 68 && varbitValue <= 71) ||
//                (varbitValue >= 75 && varbitValue <= 78) ||
//                (varbitValue >= 82 && varbitValue <= 85) ||
//                (varbitValue >= 89 && varbitValue <= 92) ||
//                (varbitValue >= 96 && varbitValue <= 99) ||
//                (varbitValue >= 103 && varbitValue <= 106) ||
//                (varbitValue >= 192 && varbitValue <= 195)) {
//            return "Growing";
//        }
//
//        if ((varbitValue >= 8 && varbitValue <= 10) ||
//                (varbitValue >= 15 && varbitValue <= 17) ||
//                (varbitValue >= 22 && varbitValue <= 24) ||
//                (varbitValue >= 29 && varbitValue <= 31) ||
//                (varbitValue >= 36 && varbitValue <= 38) ||
//                (varbitValue >= 43 && varbitValue <= 45) ||
//                (varbitValue >= 50 && varbitValue <= 52) ||
//                (varbitValue >= 57 && varbitValue <= 59) ||
//                (varbitValue >= 72 && varbitValue <= 74) ||
//                (varbitValue >= 79 && varbitValue <= 81) ||
//                (varbitValue >= 86 && varbitValue <= 88) ||
//                (varbitValue >= 93 && varbitValue <= 95) ||
//                (varbitValue >= 100 && varbitValue <= 102) ||
//                (varbitValue >= 107 && varbitValue <= 109) ||
//                (varbitValue >= 196 && varbitValue <= 197)) {
//            return "Harvestable";
//        }
//
//        if ((varbitValue >= 128 && varbitValue <= 169) ||
//                (varbitValue >= 198 && varbitValue <= 200)) {
//            return "Diseased";
//        }
//
//        if ((varbitValue >= 170 && varbitValue <= 172) ||
//                (varbitValue >= 201 && varbitValue <= 203)) {
//            return "Dead";
//        }
//
//        return "Empty";
//    }
//
//    private boolean isNearBank() {
//        return Rs2GameObject.findBank() != null;
//    }
//}
