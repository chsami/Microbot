package net.runelite.client.plugins.microbot.bee.MossKiller;

import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.MossKillerState;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.models.RS2Item;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.api.ItemID.*;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.eatAt;


public class MossKillerScript extends Script {

    public static double version = 1.0;
    public static MossKillerConfig config;

    public boolean isStarted = false;
    public int playerCounter = 0;
    public boolean bossMode = false;


    public final WorldPoint SEWER_ENTRANCE = new WorldPoint(3237, 3459, 0);
    public final WorldPoint SEWER_LADDER = new WorldPoint(3237, 9859, 0);
    public final WorldPoint NORTH_OF_WEB = new WorldPoint(3210, 9900, 0);
    public final WorldPoint SOUTH_OF_WEB = new WorldPoint(3210, 9898, 0);
    public final WorldPoint OUTSIDE_BOSS_GATE_SPOT = new WorldPoint(3174, 9900, 0);
    public final WorldPoint INSIDE_BOSS_GATE_SPOT = new WorldPoint(3214, 9937, 0);
    public final WorldPoint MOSS_GIANT_SPOT = new WorldPoint(3165, 9879, 0);
    public final WorldPoint VARROCK_SQUARE = new WorldPoint(3212, 3422, 0);
    public final WorldPoint VARROCK_WEST_BANK = new WorldPoint(3253, 3420, 0);

    public int[] strengthPotionIds = {STRENGTH_POTION1, STRENGTH_POTION2, STRENGTH_POTION3, STRENGTH_POTION4}; // Replace ID1, ID2, etc., with the actual potion IDs.


    // Items
    public final int AIR_RUNE = 556;
    public final int FIRE_RUNE = 554;
    public final int LAW_RUNE = 563;

    // TODO: convert axe and food to be a list of all available stuff
    public int BRONZE_AXE = 1351;
    public int FOOD = SWORDFISH;

    public int MOSSY_KEY = 22374;

    public int NATURE_RUNE = 561;
    public int DEATH_RUNE = 560;
    public int CHAOS_RUNE = 562;
    // TODO: add stuff for boss too
    public int[] LOOT_LIST = new int[]{MOSSY_KEY, LAW_RUNE, AIR_RUNE, FIRE_RUNE, DEATH_RUNE, CHAOS_RUNE, NATURE_RUNE};


    public MossKillerState state = MossKillerState.BANK;


    public boolean run(MossKillerConfig config) {
        MossKillerScript.config = config;
        Microbot.enableAutoRunOn = false;
        Rs2Walker.disableTeleports = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if(!isStarted){
                    init();
                }

                Microbot.log(String.valueOf(state));
                Microbot.log("BossMode: " + bossMode);

                if (Rs2Player.getRealSkillLevel(Skill.DEFENCE) >= config.defenseLevel()) {
                    moarShutDown();
                }

                if (Rs2Player.getRealSkillLevel(Skill.ATTACK) >= config.attackLevel()) {
                    moarShutDown();
                }

                if (Rs2Player.getRealSkillLevel(Skill.STRENGTH) >= config.strengthLevel()) {
                    moarShutDown();
                }
                // CODE HERE
                switch(state){
                    case BANK: handleBanking(); break;
                    case TELEPORT: varrockTeleport(); break;
                    case WALK_TO_BANK: walkToVarrockWestBank(); break;
                    case WALK_TO_MOSS_GIANTS: walkToMossGiants(); break;
                    case FIGHT_BOSS: handleBossFight(); break;
                    case FIGHT_MOSS_GIANTS: handleMossGiants(); break;
                    case EXIT_SCRIPT: sleep(10000, 15000); init(); break;
                }

                // switch statement to call functions based on state


                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    public void moarShutDown() {
        varrockTeleport();
        //static sleep to wait till out of combat
        sleep(10000);
        //turn off breakhandler
        stopBreakHandlerPlugin();
        //turn off autologin and all other scripts in 5 seconds
        Microbot.getClientThread().runOnSeperateThread(() -> {
            if (!Microbot.pauseAllScripts) {
                sleep(5000);
                Microbot.pauseAllScripts = true;
            }
            return null;
        });
        Rs2Player.logout();
        sleep(1000);
        shutdown();
    }
    private void checkAndDrinkStrengthPotion() {
        int currentStrengthLevel = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH); // Unboosted strength level
        int boostedStrengthLevel = Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH); // Current boosted strength level

        // Calculate the strength potion boost
        int strengthPotionBoost = (int) Math.floor(3 + (0.1 * currentStrengthLevel)); // Potion boost value

        // Calculate the expected boosted strength level
        int expectedBoostedStrength = currentStrengthLevel + strengthPotionBoost;

        // Check if the boosted strength level is less than 2 levels below the expected boosted level
        if (boostedStrengthLevel < expectedBoostedStrength - 2) {
            System.out.println("are we getting into the drinking bracket?");
            // Try to drink any available Strength potion (1 to 4 doses)
            if (Rs2Inventory.contains("Strength potion(1)")) {
                Rs2Inventory.interact("Strength potion(1)", "Drink");
            } else if (Rs2Inventory.contains("Strength potion(2)")) {
                Rs2Inventory.interact("Strength potion(2)", "Drink");
            } else if (Rs2Inventory.contains("Strength potion(3)")) {
                Rs2Inventory.interact("Strength potion(3)", "Drink");
            } else if (Rs2Inventory.contains("Strength potion(4)")) {
                Rs2Inventory.interact("Strength potion(4)", "Drink");
            }

            sleep(300);
        } else {
            System.out.println("Boosted strength level is high enough, no need to drink.");
        }
    }

    /**
     * Stops the BreakHandlerPlugin if it's currently active.
     *
     * @return true if the plugin was successfully stopped, false if it was not found or not active.
     */
    public static boolean stopBreakHandlerPlugin() {
        // Attempt to retrieve the BreakHandlerPlugin from the active plugin list
        BreakHandlerPlugin breakHandlerPlugin = (BreakHandlerPlugin) Microbot.getPluginManager().getPlugins().stream()
                .filter(plugin -> plugin.getClass().getName().equals(BreakHandlerPlugin.class.getName()))
                .findFirst()
                .orElse(null);

        // Check if the plugin was found
        if (breakHandlerPlugin == null) {
            System.out.println("BreakHandlerPlugin not found or not running.");
            return false;
        }

        try {
            // Stop the BreakHandlerPlugin
            Microbot.getPluginManager().stopPlugin(breakHandlerPlugin);
            System.out.println("BreakHandlerPlugin successfully stopped.");
            return true;
        } catch (PluginInstantiationException e) {
            System.err.println("Failed to stop BreakHandlerPlugin: " + e.getMessage());
            throw new RuntimeException("An error occurred while stopping BreakHandlerPlugin", e);
        }
    }

    public void handleMossGiants() {

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (!Rs2Inventory.contains(FOOD) || BreakHandlerScript.breakIn <= 15){
            Microbot.log("Inventory does not contains FOOD or break in less than 15");
            if (Rs2Inventory.contains(FOOD)) {Microbot.log("We have food");}
                if (BreakHandlerScript.breakIn <= 15) {Microbot.log("Break in less than 15");}
            state = MossKillerState.TELEPORT;
            return;
        }

        if(Rs2Walker.getDistanceBetween(playerLocation, MOSS_GIANT_SPOT) > 10){
            init();
            return;
        }

        int randomValue = (int) Rs2Random.truncatedGauss(35, 60, 4.0);
        eatAt(randomValue);


        // Check if loot is nearby and pick it up if it's in LOOT_LIST
        for (int lootItem : LOOT_LIST) {
            if(!Rs2Inventory.isFull() && Rs2GroundItem.interact(lootItem, "Take", 10)){
                sleep(1000, 3000);
            }
        }

        // Check if any players are near
        if(!getNearbyPlayers(7).isEmpty() && config.hopWhenPlayerIsNear()){
            // todo: add check in config if member or not
            if(playerCounter > 15) {
                sleep(10000, 15000);
                int world = Login.getRandomWorld(false, null);
                if(world == 301){
                    return;
                }
                boolean isHopped = Microbot.hopToWorld(world);
                sleepUntil(() -> isHopped, 5000);
                if (!isHopped) return;
                playerCounter = 0;
                int randomThreshold = (int) Rs2Random.truncatedGauss(0, 5, 1.5); // Adjust mean and deviation as needed
                if (randomThreshold > 3) {
                    Rs2Inventory.open();
                }
                return;
            }
            playerCounter++;
        } else {
            playerCounter = 0;
        }

        if (!Rs2Combat.inCombat()) {
            Rs2Npc.attack("Moss giant");
        }

        sleep(800, 2000);
    }

    public List<Player> getNearbyPlayers(int distance) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        List<Player> players = Rs2Player.getPlayers();

        return players.stream()
                .filter(p -> p != null && p.getWorldLocation().distanceTo(playerLocation) <= distance)
                .collect(Collectors.toList());
    }

    public void handleBossFight(){
        toggleRunEnergy();
        boolean growthlingAttacked = false;

        if(!Rs2Inventory.contains(FOOD)){
            state = MossKillerState.TELEPORT;
            return;
        }

        if(!Rs2Inventory.contains(BRONZE_AXE)){
            getBronzeAxeFromInstance();
            return;
        }

        int randomValue = (int) Rs2Random.truncatedGauss(50, 75, 4.0);
        if (eatAt(randomValue)) {
           sleep(750, 1250);
        }

        checkAndDrinkStrengthPotion();


        List<NPC> monsters = Microbot.getClient().getNpcs();


        for (NPC npc : monsters) {
            if ("Growthling".equals(npc.getName()) || npc.getId() == 8194) {

                if(!growthlingAttacked){
                    if (Rs2Npc.interact(npc.getId(), "attack")) {
                        Microbot.log("Attacking growthling!");
                        growthlingAttacked = true;
                        sleep(250, 1000);
                    }
                } else {
                    Rs2Npc.attack(npc.getId());
                }


                double health = Rs2Npc.getHealth(npc);

                if (health <= 10.0) {
                    if (Rs2Inventory.use(BRONZE_AXE)) {
                        sleep(750, 1000);
                        Rs2Npc.interact(npc, "Use");
                        sleep(750, 1250);
                    }
                }
            }
        }

        if(Rs2Npc.getNpc("Bryophyta") == null){
            Microbot.log("Boss is dead, lets loot.");
            RS2Item[] groundItems = Rs2GroundItem.getAll(10);
            if(groundItems.length > 0){
                for (RS2Item item : groundItems){
                    if (item != null){
                        if(Rs2GroundItem.interact(item)){
                            sleepUntil(() -> Rs2Inventory.contains(item.getItem().getId()), 10000);
                            sleep(250, 750);
                        }
                    }
                }
                sleep(1000, 3000);
                state = MossKillerState.TELEPORT;
                return;
            }
        } else if(!growthlingAttacked){
            Rs2Npc.attack(Rs2Npc.getNpc("Bryophyta"));
        }
    }


    public void walkToMossGiants() {

        int currentPitch = Rs2Camera.getPitch(); // Assume Rs2Camera.getPitch() retrieves the current pitch value.
        int currentZoom = Rs2Camera.getZoom(); // Assume Rs2Camera.getZoom() retrieves the current zoom level.
        // Ensure the pitch is within the desired range
        if (currentPitch < 350) {
            int pitchValue = Rs2Random.between(360, 400); // Random value within the range
            Rs2Camera.setPitch(pitchValue); // Adjust the pitch
        }

        if (currentZoom < 300) {
            int zoomValue = Rs2Random.between(380, 300);
            Rs2Camera.setZoom(zoomValue);
        }

        if(config.keyThreshold() == 1 && Rs2Inventory.contains(MOSSY_KEY) && Rs2Inventory.contains(BRONZE_AXE)) {
            bossMode = true;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if(!Rs2Inventory.contains(FOOD)) {
            state = MossKillerState.WALK_TO_BANK;
        }

        toggleRunEnergy();

        System.out.println("getting here to walk to sewer entrance");

        if (Rs2Walker.getDistanceBetween(playerLocation, SEWER_ENTRANCE) > 3 && playerLocation.getPlane() == 0) {
            if (Rs2Walker.getDistanceBetween(playerLocation, VARROCK_WEST_BANK) < 10 || Rs2Walker.getDistanceBetween(playerLocation, VARROCK_SQUARE) < 10) { // if near bank
                Rs2Walker.walkTo(SEWER_ENTRANCE, 5);
                sleepUntil(() -> Rs2Walker.getDistanceBetween(playerLocation, SEWER_ENTRANCE) < 3 && !Rs2Player.isMoving(), 3000);
                return;
            }
        }

        System.out.println("getting here after walking to sewer entrance");

        if (Rs2Walker.getDistanceBetween(playerLocation, SEWER_ENTRANCE) < 10) {
            if (Rs2GameObject.exists(882)) { // open manhole
                System.out.println("interacting sewer entrance");
                Rs2GameObject.interact(882, "Climb-down");
                sleep(2500,4500);
                return;
            }
            if (Rs2GameObject.exists(881)) { // closed manhole
                System.out.println("interacting opening manhole");
                Rs2GameObject.interact(881, "Open");
                return;
            }
        }

        if (Rs2Walker.getDistanceBetween(playerLocation, MOSS_GIANT_SPOT) > 10) {
                        if (bossMode) {
                            BreakHandlerScript.setLockState(true);
                            if (Rs2Inventory.contains(MOSSY_KEY)) {
                                if (eatAt(70)) {
                                    sleep(1900,2200);
                                    eatAt(80);
                                }
                                Microbot.log("Walking to outside boss gaet spot");
                                if (Rs2Walker.getDistanceBetween(playerLocation, OUTSIDE_BOSS_GATE_SPOT) > 10) {
                                    Rs2Walker.walkTo(OUTSIDE_BOSS_GATE_SPOT, 10);
                                    sleepUntil(() -> Rs2Walker.getDistanceBetween(playerLocation, OUTSIDE_BOSS_GATE_SPOT) <= 10 && !Rs2Player.isMoving(), 600);}
                                else if (Rs2Walker.getDistanceBetween(playerLocation, OUTSIDE_BOSS_GATE_SPOT) < 10) {
                                    Rs2Walker.walkFastCanvas(OUTSIDE_BOSS_GATE_SPOT, true);
                                    sleepUntil(() -> Rs2Walker.getDistanceBetween(playerLocation, OUTSIDE_BOSS_GATE_SPOT) < 5 && !Rs2Player.isMoving(), 600);
                                }

                                if (bossMode && Rs2Walker.getDistanceBetween(playerLocation, OUTSIDE_BOSS_GATE_SPOT) < 5) {
                                    if (Rs2GameObject.exists(32534) && Rs2GameObject.interact(32534, "Open")) {
                                        sleepUntil(Rs2Dialogue::isInDialogue);
                                        if (Rs2Dialogue.isInDialogue()) {
                                            sleep(500);
                                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                                            sleep(1000, 3000);
                                            Rs2Keyboard.typeString("1");
                                            sleep(2500, 3000);
                                            if (!Rs2Dialogue.isInDialogue()) {
                                                state = MossKillerState.FIGHT_BOSS;
                                                return;
                                            }
                                        }
                                    }
                                }
                                return;
                            } else {
                                state = MossKillerState.BANK;
                            }
                        } else {
                            Rs2Walker.walkTo(MOSS_GIANT_SPOT);
                        }
                    }

            if (Rs2Walker.getDistanceBetween(playerLocation, MOSS_GIANT_SPOT) < 7) {
                state = MossKillerState.FIGHT_MOSS_GIANTS;
                return;
            }

            state = MossKillerState.WALK_TO_MOSS_GIANTS;
        }



    public void handleBanking(){
        if(bossMode && !Rs2Inventory.contains(MOSSY_KEY) && Rs2Walker.getDistanceBetween(Rs2Player.getWorldLocation(), VARROCK_WEST_BANK) > 6) {
            state = MossKillerState.WALK_TO_BANK;
            return;
        }

        if(!bossMode && Rs2Inventory.hasItem(AIR_RUNE) &&
                Rs2Inventory.hasItem(LAW_RUNE) &&
                Rs2Inventory.hasItem(FIRE_RUNE) &&
                Rs2Inventory.hasItemAmount(FOOD, 25) &&
                !Rs2Equipment.isNaked()) {
            state = MossKillerState.WALK_TO_MOSS_GIANTS;
            return;
        } else if (bossMode && !Rs2Inventory.hasItemAmount(FOOD, 22)) {
            state = MossKillerState.WALK_TO_BANK;
        }

        banking();

        sleep(500, 1000);
    }

    public void banking() {

        if(Rs2Bank.openBank()) {
            sleepUntil(() -> Rs2Bank.isOpen(), 60000);
            Rs2Bank.depositAll();
            sleepUntil(() -> Rs2Inventory.isEmpty());
            sleep(1000, 1500);
            if(!Rs2Bank.hasItem(AIR_RUNE) || !Rs2Bank.hasItem(LAW_RUNE) || !Rs2Bank.hasItem(FIRE_RUNE) || !Rs2Bank.hasItem(FOOD)){
                state = MossKillerState.EXIT_SCRIPT;
                return;
            }
            int keyTotal = Rs2Bank.count("Mossy key");
            Microbot.log("Key Total: " + keyTotal);
            if (keyTotal >= config.keyThreshold()){
                Microbot.log("keyTotal >= config threshold");
                bossMode = true;
                Rs2Bank.withdrawItem(MOSSY_KEY);
                // Rs2Bank.withdrawOne(MOSSY_KEY);
                Microbot.log("Sleeping until mossy key");
                sleepUntil(() -> Rs2Inventory.contains(MOSSY_KEY));
                sleep(1000, 1300);
                Rs2Bank.withdrawOne(BRONZE_AXE);
                sleepUntil(() -> Rs2Inventory.contains(BRONZE_AXE));
                sleep(200, 600);
                for (int id : strengthPotionIds) {
                    if (Rs2Bank.hasItem(id)) {
                        Rs2Bank.withdrawOne(id);
                        break;
                    }
                }
                sleep(1000, 1300);

            } else if(bossMode && keyTotal > 0) {
                Microbot.log("bossMode and keyTotal > 0");
                Rs2Bank.withdrawOne(MOSSY_KEY);
                sleepUntil(() -> Rs2Inventory.contains(MOSSY_KEY));
                sleep(1000, 1300);
                Rs2Bank.withdrawOne(BRONZE_AXE);
                sleepUntil(() -> Rs2Inventory.contains(BRONZE_AXE));
                sleep(200, 600);
                for (int id : strengthPotionIds) {
                    if (Rs2Bank.hasItem(id)) {
                        Rs2Bank.withdrawOne(id);
                        break;
                    }
                }
                sleep(1000, 1300);
            } else if(keyTotal == 0){
                Microbot.log("keyTotal == 0");
                bossMode = false;
            }

            Microbot.log(String.valueOf(config.isSlashWeaponEquipped()));

            if(!config.isSlashWeaponEquipped()){
                Rs2Bank.withdrawOne(KNIFE);
                sleep(500, 1200);
            }

            // Randomize withdrawal order and add sleep between each to mimic human behavior
            withdrawItemWithRandomSleep(AIR_RUNE, FIRE_RUNE, LAW_RUNE, FOOD);
            Rs2Bank.withdrawAll(FOOD);
            sleepUntil(() -> Rs2Inventory.contains(FOOD));
            sleep(500, 1000);
            if (Rs2Inventory.containsAll(new int[]{AIR_RUNE, FIRE_RUNE, LAW_RUNE, FOOD})) {
                if(Rs2Bank.closeBank()){

                    state = MossKillerState.WALK_TO_MOSS_GIANTS;
                }
            }


        }
    }

    private void withdrawItemWithRandomSleep(int... itemIds) {
        for (int itemId : itemIds) {
            Rs2Bank.withdrawAll(itemId);
            sleepUntil(() -> Rs2Inventory.contains(itemId), 3000);
            sleep(300, 700);
        }
    }

    private void getBronzeAxeFromInstance() {
        if(Rs2Inventory.isFull()) {Rs2Inventory.interact(FOOD, "Eat");}
        sleep(1200,1800);
        Rs2GameObject.interact( 32536, "Take-axe");
        sleepUntil(() -> Rs2Inventory.contains(BRONZE_AXE), 10000);
        eatAt(70);
        if (!Rs2Inventory.contains(BRONZE_AXE)) {
            Rs2GameObject.interact( 32536, "Take-axe");
            sleepUntil(() -> Rs2Inventory.contains(BRONZE_AXE));
        }
    }



    public void walkToVarrockWestBank(){
        BreakHandlerScript.setLockState(false);
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        toggleRunEnergy();
        if(!bossMode && Rs2Inventory.containsAll(new int[]{AIR_RUNE, FIRE_RUNE, LAW_RUNE, FOOD})){
            state = MossKillerState.WALK_TO_MOSS_GIANTS;
            return;
        }
        if(Rs2Walker.getDistanceBetween(playerLocation, VARROCK_WEST_BANK) > 6){
            if (playerLocation.getY() > 6000) {state = MossKillerState.TELEPORT;}
            Rs2Walker.walkTo(VARROCK_WEST_BANK, 4);
        } else {
            System.out.println("distance to varrock west bank < 5, bank now");
            state = MossKillerState.BANK;
        }
    }

    public void varrockTeleport(){
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        Microbot.log(String.valueOf(Rs2Walker.getDistanceBetween(playerLocation, VARROCK_SQUARE)));
        sleep(1000, 2000);
        if(Rs2Walker.getDistanceBetween(playerLocation, VARROCK_SQUARE) <= 10 && playerLocation.getY() < 5000){
            state = MossKillerState.WALK_TO_BANK;
            return;
        }
        if(Rs2Inventory.containsAll(AIR_RUNE, FIRE_RUNE, LAW_RUNE)){
            Rs2Magic.cast(MagicAction.VARROCK_TELEPORT);
        } else {
            state = MossKillerState.WALK_TO_BANK;
        }
        sleep(2000, 3500);

    }

    public void toggleRunEnergy(){
        if(Microbot.getClient().getEnergy() > 4000 && !Rs2Player.isRunEnabled()){
            Rs2Player.toggleRunEnergy(true);
        }
    }


    public void getInitiailState(){
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if(Rs2Walker.getDistanceBetween(playerLocation, VARROCK_SQUARE) < 10 || Rs2Walker.getDistanceBetween(playerLocation, VARROCK_WEST_BANK) < 10){
            state = MossKillerState.WALK_TO_BANK;
            return;
        }

        if(Rs2Walker.getDistanceBetween(playerLocation, MOSS_GIANT_SPOT) < 10){
            state = MossKillerState.FIGHT_MOSS_GIANTS;
            return;
        }


        System.out.println("Must start near varrock square, bank, or moss giatn spot.");
        state = MossKillerState.EXIT_SCRIPT;
    }

    public void init(){
        //todo: set up food
        //todo:  set up membs
        //todo: set up loot filter
        // check state



        getInitiailState();

        if(!Rs2Combat.enableAutoRetialiate()){
            System.out.println("Could not turn on auto retaliate.");
            state = MossKillerState.EXIT_SCRIPT;
        }

        isStarted = true;
    }
}
