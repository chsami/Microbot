package net.runelite.client.plugins.microbot.barrows;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class BarrowsScript extends Script {

    public static boolean test = false;
    public static boolean inTunnels = false;
    public String WhoisTun = "Unknown";
    private String neededRune = "unknown";
    private boolean shouldBank = false;
    private boolean shouldAttackSkeleton = false;
    private boolean varbitCheckEnabled = true;
    private int tunnelLoopCount = 0;
    private WorldPoint FirstLoopTile;
    private Rs2PrayerEnum NeededPrayer;
    int scriptDelay = Rs2Random.between(300,600);
    public static int ChestsOpened = 0;
    private int minRuneAmt;
    public static List<String> barrowsPieces = new ArrayList<>();
    private ScheduledFuture<?> WalkToTheChestFuture;
    private WorldPoint Chest = new WorldPoint(3552,9694,0);
    private int minForgottenBrews = 0;

    public boolean run(BarrowsConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (Rs2Player.getQuestState(Quest.HIS_FAITHFUL_SERVANTS) != QuestState.FINISHED) {
                    Microbot.showMessage("Complete the 'His Faithful Servants' quest for the webwalker to function correctly");
                    shutdown();
                    return;
                }

                if(barrowsPieces.isEmpty()){
                    barrowsPieces.add("Nothing yet.");
                }

                if(Rs2Player.getWorldLocation().getY() > 9600 && Rs2Player.getWorldLocation().getY() < 9730) {
                    inTunnels = true;
                } else {

                    if(tunnelLoopCount != 0){
                        //reset the tunnels loop counter
                        tunnelLoopCount = 0;
                    }

                    inTunnels = false;
                }

                if(Rs2Magic.getCurrentAutoCastSpell() == null){
                    Microbot.log("Please select your wind spell in auto-cast then restart the script. stopping...");
                    super.shutdown();
                }

                gettheRune();
                minRuneAmt = config.minRuneAmount();
                minForgottenBrews = config.minForgottenBrew();
                shouldAttackSkeleton = config.shouldGainRP();

                if(Rs2Inventory.getInventoryFood().isEmpty()){
                    Microbot.log("No food in inventory. Please get some food then restart the script. stopping...");
                    super.shutdown();
                }

                if(neededRune.equals("Unknown") || neededRune.equals("unknown")){
                    Microbot.log("No catalytic rune in inventory. stopping...");
                    super.shutdown();
                }

                suppliesCheck();

                if(!inTunnels && shouldBank == false) {
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        Rs2WorldArea mound = brother.getHumpWP();
                        int totalTiles = mound.toWorldPointList().size();
                        WorldPoint randomMoundTile = mound.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                        NeededPrayer = brother.whatToPray;
                        suppliesCheck();
                        outOfSupplies();
                        if(shouldBank){
                            return;
                        }

                        stopFutureWalker();

                        Microbot.log("Checking mound for: " + brother.getName() + " at " + randomMoundTile +"Using prayer: "+NeededPrayer);

                        if(everyBrotherWasKilled()){
                            if(WhoisTun.equals("Unknown")){
                                Microbot.log("We're not sure who tunnel is, and every brother is dead. Checking all mounds manually");
                                varbitCheckEnabled = false;
                            }
                        } else {
                            if(!varbitCheckEnabled){
                                varbitCheckEnabled = true;
                            }
                        }

                        if(!WhoisTun.equals("Unknown")){
                            if(!varbitCheckEnabled){
                                varbitCheckEnabled = true;
                            }
                        }

                        //resume progress from varbits
                        if(varbitCheckEnabled) {
                            if (brother.name.contains("Dharok")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) == 1) {
                                    Microbot.log("We all ready killed Dharok.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Guthan")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) == 1) {
                                    Microbot.log("We all ready killed Guthan.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Karil")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) == 1) {
                                    Microbot.log("We all ready killed Karil.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Torag")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) == 1) {
                                    Microbot.log("We all ready killed Torag.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Verac")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) == 1) {
                                    Microbot.log("We all ready killed Verac.");
                                    continue;
                                }
                            }
                            if (brother.name.contains("Ahrim")) {
                                if (Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM) == 1) {
                                    Microbot.log("We all ready killed Ahrim.");
                                    continue;
                                }
                            }
                        }

                        //Enter mound
                        if (Rs2Player.getWorldLocation().getPlane() != 3) {
                            Microbot.log("Entering the mound");
                            while (!mound.contains(Rs2Player.getWorldLocation())) {
                                if (!super.isRunning()) {
                                    break;
                                }

                                //antipattern turn on prayer early
                                antiPatternEnableWrongPrayer();

                                antiPatternActivatePrayer();

                                antiPatternDropVials();
                                //antipattern

                                // We're not in the mound yet.
                                randomMoundTile = mound.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                                Rs2Walker.walkTo(randomMoundTile);
                                sleep(300, 600);
                                if (mound.contains(Rs2Player.getWorldLocation())) {
                                    if(!Rs2Player.isMoving()) {
                                        break;
                                    }
                                } else {
                                    Microbot.log("At the mound, but we can't dig yet.");
                                    randomMoundTile = mound.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                                    Rs2Walker.walkCanvas(randomMoundTile);
                                    sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                                    sleep(300,600);
                                }
                            }
                            while (mound.contains(Rs2Player.getWorldLocation()) && Rs2Player.getWorldLocation().getPlane() != 3) {

                                if (!super.isRunning()) {
                                    break;
                                }

                                //antipattern turn on prayer early
                                antiPatternEnableWrongPrayer();

                                antiPatternActivatePrayer();
                                //antipattern

                                digIntoTheMound();

                                if (Rs2Player.getWorldLocation().getPlane() == 3) {
                                    //we made it in
                                    break;
                                }
                            }
                        }
                        if (Rs2Player.getWorldLocation().getPlane() == 3) {
                            Microbot.log("We're in the mound");

                            activatePrayer();

                            // we're in the mound, prayer is active
                            GameObject sarc = Rs2GameObject.get("Sarcophagus");
                            Rs2NpcModel currentBrother = null;
                            Microbot.log("Found the Sarcophagus");
                            while(currentBrother == null) {
                                Microbot.log("Searching the Sarcophagus");
                                if (!super.isRunning()) {
                                    break;
                                }

                                if (Rs2GameObject.interact(sarc, "Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving(), Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat(), Rs2Random.between(3000, 6000));
                                    // the brother could take a second to spawn in.
                                    sleepUntil(() -> Microbot.getClient().getHintArrowNpc()!=null, Rs2Random.between(750, 1500));
                                }
                                if(Rs2Dialogue.isInDialogue()){
                                    WhoisTun = brother.name;
                                    Microbot.log(brother.name+" is our tunnel");
                                    break;
                                }

                                if(Microbot.getClient().getHintArrowNpc() != null) {
                                    NPC hintArrow = Microbot.getClient().getHintArrowNpc();
                                    currentBrother = new Rs2NpcModel(hintArrow);
                                } else {
                                    break;
                                }

                                if (currentBrother != null) {
                                    break;
                                }
                            }
                            //The ghost should be here assuming its not the tunnel.
                            if(currentBrother != null && !Rs2Player.isInCombat()){
                                while(!Rs2Player.isInCombat()){
                                    if (!super.isRunning()) {
                                        break;
                                    }
                                    Microbot.log("Attacking the brother");
                                    Rs2Npc.interact(currentBrother, "Attack");
                                    sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                                }
                            }
                            //fighting
                            if(Rs2Player.isInCombat()){
                                Microbot.log("Fighting the brother.");
                                while(!currentBrother.isDead()){

                                    if (!super.isRunning()) {
                                        break;
                                    }

                                    activatePrayer();

                                    sleep(500,1500);
                                    eatFood();
                                    outOfSupplies();
                                    antiPatternDropVials();
                                    drinkforgottonbrew();
                                    drinkPrayerPot();
                                    if(Microbot.getClient().getHintArrowNpc() == null){
                                        break;
                                    }
                                    if(currentBrother.isDead()){
                                        //anti pattern
                                        disablePrayer();
                                        //anti pattern
                                        break;
                                    }
                                }
                            }
                            // at this point the brother should be dead and we should be free to leave.
                            // We could be in ahrims mound while ahrim is tunnel. We need to stop the bot from leaving the mound and going back in.
                            if(brother.name.equals(WhoisTun) && brother.name.contains("Ahrim")) {
                                if (Rs2Dialogue.isInDialogue()) {
                                    while (Rs2Dialogue.isInDialogue()) {
                                        if (!super.isRunning()) {
                                            break;
                                        }
                                        if (Rs2Dialogue.hasContinue()) {
                                            Rs2Dialogue.clickContinue();
                                            sleep(300, 600);
                                        }
                                        if (Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!")) {
                                            if (Rs2Dialogue.clickOption("Yeah I'm fearless!")) {
                                                sleep(300, 600);
                                                inTunnels = true;
                                            }
                                        }
                                        if (!Rs2Dialogue.isInDialogue()) {
                                            break;
                                        }
                                        if (inTunnels) {
                                            break;
                                        }
                                    }
                                    return;
                                }
                            }

                            leaveTheMound();
                        }
                    }
                }

                if(!WhoisTun.equals("Unknown") && shouldBank == false && !inTunnels){
                    Microbot.log("Going to the tunnels.");
                    stopFutureWalker();
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        if (brother.name.equals(WhoisTun)) {
                            // Found the tunnel brother's mound
                            Rs2WorldArea tunnelMound = brother.getHumpWP();
                            int totalTiles = tunnelMound.toWorldPointList().size();
                            WorldPoint randomMoundTile = tunnelMound.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                            System.out.println("Navigating to tunnel mound: " + brother.name);

                            // Walk to the mound
                            while (!tunnelMound.contains(Rs2Player.getWorldLocation())) {
                                if (!super.isRunning()) {
                                    break;
                                }
                                //anti pattern
                                antiPatternDropVials();
                                //anti pattern
                                randomMoundTile = tunnelMound.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                                Rs2Walker.walkTo(randomMoundTile);
                                sleep(300, 600);
                                if (tunnelMound.contains(Rs2Player.getWorldLocation())) {
                                    if(!Rs2Player.isMoving()) {
                                        break;
                                    }
                                } else {
                                    Microbot.log("At the mound, but we can't dig yet.");
                                    randomMoundTile = tunnelMound.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                                    Rs2Walker.walkCanvas(randomMoundTile);
                                    sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                                    sleep(300,600);
                                }
                            }

                            while (tunnelMound.contains(Rs2Player.getWorldLocation()) && Rs2Player.getWorldLocation().getPlane() != 3) {
                                if (!super.isRunning()) {
                                    break;
                                }

                                digIntoTheMound();

                                if (Rs2Player.getWorldLocation().getPlane() == 3) {
                                    //we made it in
                                    break;
                                }
                            }

                            GameObject sarc = Rs2GameObject.get("Sarcophagus");

                            while(!Rs2Dialogue.isInDialogue()) {
                                if (!super.isRunning()) {
                                    break;
                                }
                                if (Rs2GameObject.interact(sarc, "Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving(), Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat(), Rs2Random.between(3000, 6000));
                                    sleepUntil(() -> Rs2Dialogue.isInDialogue(), Rs2Random.between(3000, 6000));
                                }

                                if(Rs2Dialogue.isInDialogue()){
                                    break;
                                }

                                if (inTunnels) {
                                    break;
                                }

                                if (Rs2Player.getWorldLocation().getPlane() != 3) {
                                    //we're not in the mound
                                    break;
                                }

                            }


                            while(Rs2Dialogue.isInDialogue()) {
                                if (!super.isRunning()) {
                                    break;
                                }
                                if (Rs2Dialogue.hasContinue()) {
                                    Rs2Dialogue.clickContinue();
                                    sleepUntil(() -> Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!"), Rs2Random.between(2000, 5000));
                                    sleep(300, 600);
                                }
                                if (Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!")) {
                                    if (Rs2Dialogue.clickOption("Yeah I'm fearless!")) {
                                        sleepUntil(() -> Rs2Player.getWorldLocation().getY() > 9600 && Rs2Player.getWorldLocation().getY() < 9730, Rs2Random.between(2500, 6000));
                                        //allow some time for the tunnel to load.
                                        sleep(1000, 2000);
                                        inTunnels = true;
                                    }
                                }
                                if (!Rs2Dialogue.isInDialogue()) {
                                    break;
                                }
                                if (inTunnels) {
                                    break;
                                }
                                if (Rs2Player.getWorldLocation().getPlane() != 3) {
                                    //we're not in the mound
                                    break;
                                }
                            }


                            break; // done
                        }
                    }
                }


                if(inTunnels && shouldBank == false) {
                    Microbot.log("In the tunnels");
                    if(!varbitCheckEnabled){
                        varbitCheckEnabled=true;
                    }
                    leaveTheMound();
                    stuckInTunsCheck();
                    tunnelLoopCount++;
                    solvePuzzle();
                    checkForBrother();
                    eatFood();
                    outOfSupplies();
                    gainRP();

                    //threaded walk because the brother could appear, the puzzle door could be there.
                    if(!Rs2Player.isMoving()) {
                        startWalkingToTheChest();
                    }
                    //threaded walk because the brother could appear, the puzzle door could be there.
                    //Moved Rs2Walker.setTarget(null); inside the puzzle solver and bother check.

                    solvePuzzle();
                    checkForBrother();

                    if(Rs2Player.getWorldLocation().distanceTo(Chest)==5){
                        //too close for the walker to engage but too far to want to click the chest.
                        stopFutureWalker();
                        //stop the walker and future
                        Microbot.log("Walking on screen to the chest");
                        Rs2Walker.walkCanvas(Chest);
                        sleepUntil(()-> !Rs2Player.isMoving() || Chest.distanceTo(Rs2Player.getWorldLocation())<=4, Rs2Random.between(2000,5000));
                    }

                    if(Rs2Player.getWorldLocation().distanceTo(Chest)<=4){
                        //we need to get the chest ID: 20973
                        stopFutureWalker();
                        //stop the walker and future
                        TileObject chest = Rs2GameObject.findObjectById(20973);
                        if(Rs2GameObject.interact(chest, "Open")){
                            sleepUntil(()-> Microbot.getClient().getHintArrowNpc()!=null && Microbot.getClient().getHintArrowNpc().getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 5, Rs2Random.between(4000,6000));
                        }

                        checkForBrother();

                        if(Microbot.getClient().getHintArrowNpc()==null) {
                            int io = 0;
                            while (io < 2) {

                                if (!super.isRunning()) {
                                    break;
                                }

                                if (Rs2GameObject.interact(chest, "Search")) {
                                    sleep(500, 1500);
                                }

                                if (Rs2Widget.hasWidget("Barrows chest")) {
                                    break;
                                }

                                io++;
                            }
                            //we looted the chest time to reset

                            if (Rs2Inventory.get("Barrows teleport") == null || Rs2Inventory.get("Barrows teleport").getQuantity() < 1 || (Rs2Inventory.count("Prayer potion(3)") + Rs2Inventory.count("Prayer potion(4)")) <= 2 || Rs2Inventory.getInventoryFood().isEmpty() || Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName()) <= 3 || Rs2Player.getRunEnergy() <= 35 || Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < config.minForgottenBrew()) {
                                Microbot.log("We should bank.");
                                shouldBank = true;
                                ChestsOpened++;
                                WhoisTun = "Unknown";
                                inTunnels = false;
                            } else {
                                shouldBank = false;
                                Rs2Inventory.interact("Barrows teleport", "Break");
                                sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                                sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                                ChestsOpened++;
                                WhoisTun = "Unknown";
                                inTunnels = false;
                            }
                        }
                    }
                }

                if(shouldBank){
                    if(!Rs2Bank.isOpen()){
                        //stop the walker
                        stopFutureWalker();
                        //tele out
                        outOfSupplies();
                        //walk to and open the bank
                        Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE);
                    } else {

                        Microbot.log("Our rune is "+neededRune+" We'll need at least "+config.minRuneAmount());
                        Microbot.log("Our min food is "+config.minFood()+" Our max food is "+config.targetFoodAmount());
                        Microbot.log("Our min prayer pot amt is "+config.minPrayerPots()+" Our max prayer pot amt is "+config.targetPrayerPots());
                        Microbot.log("Our min forgotten brew amt is "+config.minForgottenBrew()+" Our max forgotten brew amt is "+config.targetForgottenBrew());
                        Microbot.log("Our min barrows teleport amt is "+config.minBarrowsTeleports()+" Our max barrows teleport amt is "+config.targetBarrowsTeleports());

                        if(Rs2Inventory.isFull() || Rs2Inventory.contains(it->it!=null&&it.getName().contains("'s") || it.getName().contains("Coins"))){
                            List<Rs2ItemModel> ourfood = Rs2Inventory.getInventoryFood();
                            String ourfoodsname = ourfood.get(0).getName();
                            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("'s"))){
                                Rs2ItemModel piece = Rs2Inventory.get(it->it!=null&&it.getName().contains("'s"));

                                if(piece!=null){
                                    barrowsPieces.add(piece.getName());
                                    if(barrowsPieces.contains("Nothing yet.")){
                                        barrowsPieces.remove("Nothing yet.");
                                    }
                                }

                            }

                            Rs2Bank.depositAllExcept(neededRune, "Spade", "Prayer potion(4)", "Prayer potion(3)", "Forgotten brew(4)", "Forgotten brew(3)", "Barrows teleport",
                                    ourfoodsname);
                        }

                        int howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(Rs2Inventory.get(neededRune).getQuantity() < config.minRuneAmount()){
                                if(Rs2Bank.getBankItem(neededRune)!=null){
                                    if(Rs2Bank.getBankItem(neededRune).getQuantity() > config.minRuneAmount()){
                                        if(Rs2Bank.withdrawX(neededRune, Rs2Random.between(config.minRuneAmount(),1000))){
                                            String therune = neededRune;
                                            sleepUntil(()-> Rs2Inventory.get(therune).getQuantity() > config.minRuneAmount(), Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+neededRune+"s. stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 60){
                            if(Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") < Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())){
                                if(Rs2Bank.getBankItem("Prayer potion(4)")!=null){
                                    if(Rs2Bank.getBankItem("Prayer potion(4)").getQuantity()>=config.targetPrayerPots()){
                                        int amt = ((Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())) - (Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)")));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX("Prayer potion(4)", amt)){
                                            sleepUntil(()-> Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") > Rs2Random.between(4,8), Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+" Prayer potions "+" need at least "+config.targetPrayerPots()+" stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(config.minForgottenBrew() > 0) {
                                if (Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < Rs2Random.between(config.minForgottenBrew(), config.targetForgottenBrew())) {
                                    if (Rs2Bank.getBankItem("Forgotten brew(4)") != null) {
                                        if (Rs2Bank.getBankItem("Forgotten brew(4)").getQuantity() >= config.targetForgottenBrew()) {
                                            int amt = ((Rs2Random.between(config.minForgottenBrew(), config.targetForgottenBrew())) - (Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)")));
                                            if (amt <= 0) {
                                                amt = 1;
                                            }
                                            Microbot.log("Withdrawing " + amt);
                                            if (Rs2Bank.withdrawX("Forgotten brew(4)", amt)) {
                                                sleepUntil(() -> Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") > Rs2Random.between(1, 3), Rs2Random.between(2000, 4000));
                                            }
                                        } else {
                                            Microbot.log("We're out of " + " Forgotten brew " + " need at least " + config.targetPrayerPots() + " stopping...");
                                            super.shutdown();
                                        }
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(Rs2Inventory.get("Barrows teleport")==null || Rs2Inventory.get("Barrows teleport").getQuantity() < Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports())){
                                if(Rs2Bank.getBankItem("Barrows teleport")!=null){
                                    if(Rs2Bank.getBankItem("Barrows teleport").getQuantity()>=config.targetBarrowsTeleports()){
                                        if(Rs2Bank.withdrawX("Barrows teleport", Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports()))){
                                            sleep(Rs2Random.between(300,750));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+" Barrows teleports "+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                        super.shutdown();
                                    }
                                } else {
                                    Microbot.log("We're out of "+" Barrows teleports "+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                    super.shutdown();
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            String neededFood = "Unknown";
                            if(!Rs2Inventory.getInventoryFood().isEmpty()){
                                if(Rs2Inventory.getInventoryFood().get(0) !=null) {
                                    neededFood = Rs2Inventory.getInventoryFood().get(0).getName();
                                }
                            }
                            if(Rs2Inventory.count(neededFood) < config.targetFoodAmount()){
                                if(Rs2Bank.getBankItem(neededFood)!=null){
                                    if(Rs2Bank.getBankItem(neededFood).getQuantity()>=config.targetFoodAmount()){
                                        int amt = (Rs2Random.between(config.minFood(),config.targetFoodAmount()) - (Rs2Inventory.count(neededFood)));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(neededFood, amt)){
                                            String finalfood = neededFood;
                                            sleepUntil(()-> Rs2Inventory.count(finalfood) >= 10, Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+neededFood+" need at least "+config.targetFoodAmount()+" stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(!Rs2Inventory.contains("Spade")){
                                if(Rs2Bank.getBankItem("Spade")!=null){
                                    if(Rs2Bank.getBankItem("Spade").getQuantity()>=1){
                                        Rs2Bank.withdrawOne("Spade");
                                        sleepUntil(()-> Rs2Inventory.contains("Spade"), Rs2Random.between(2000,4000));
                                    } else {
                                        Microbot.log("We're out of "+"Spade"+"s. stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank <= 40){
                            if(Rs2Equipment.get(EquipmentInventorySlot.RING)!=null){
                                // we have our ring do nothing
                            } else {
                                Microbot.log("Getting the ring of dueling");
                                if(Rs2Bank.count(ItemID.RING_OF_DUELING8)>0){
                                    if(!Rs2Inventory.contains(ItemID.RING_OF_DUELING8)){
                                        if(Rs2Bank.withdrawX(ItemID.RING_OF_DUELING8, 1)){
                                            sleepUntil(()-> Rs2Inventory.contains(ItemID.RING_OF_DUELING8), Rs2Random.between(5000,15000));
                                        }
                                    }
                                } else {
                                    Microbot.log("Out of rings of dueling");
                                    super.shutdown();
                                }
                                if(Rs2Inventory.contains(ItemID.RING_OF_DUELING8)){
                                    if(Rs2Inventory.interact(ItemID.RING_OF_DUELING8, "Wear")){
                                        sleepUntil(()-> Rs2Equipment.get(EquipmentInventorySlot.RING).getName().contains("dueling"), Rs2Random.between(5000,15000));
                                    }
                                }
                            }
                        }

                        if(Rs2Equipment.get(EquipmentInventorySlot.RING)!=null && Rs2Inventory.contains("Spade") &&
                                Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName())>=config.minFood() &&
                                Rs2Inventory.get("Barrows teleport").getQuantity() >= config.minBarrowsTeleports() &&
                                Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") >= config.minForgottenBrew() &&
                                Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") >= config.minPrayerPots() &&
                                Rs2Inventory.get(neededRune).getQuantity()>=config.minRuneAmount()){
                            Microbot.log("We have everything we need. Going back to barrows.");
                            reJfount();
                            if(Rs2Bank.isOpen()) {
                                if (Rs2Bank.closeBank()) {
                                    sleepUntil(() -> !Rs2Bank.isOpen(), Rs2Random.between(2000, 4000));
                                    shouldBank = false;
                                }
                            } else {
                                shouldBank = false;
                            }

                        }

                    }
                }

                scriptDelay = Rs2Random.between(200,750);
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, scriptDelay, TimeUnit.MILLISECONDS);
        return true;
    }

    public boolean everyBrotherWasKilled(){
        if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) == 1&&
                Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM) == 1){
            return true;
        }
        return false;
    }

    public void digIntoTheMound(){
        if (Rs2Inventory.contains("Spade")) {
            if (Rs2Inventory.interact("Spade", "Dig")) {
                sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 3, Rs2Random.between(3000, 5000));
            }
        }
    }

    public void leaveTheMound(){
        if(Rs2GameObject.get("Staircase", true) != null) {
            if (Rs2GameObject.hasLineOfSight(Rs2GameObject.get("Staircase", true))) {
                if (Rs2Player.getWorldLocation().getPlane() == 3) {
                    while (Rs2Player.getWorldLocation().getPlane() == 3) {
                        Microbot.log("Leaving the mound");
                        if (!super.isRunning()) {
                            break;
                        }
                        if (Rs2GameObject.interact("Staircase", "Climb-up")) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != 3, Rs2Random.between(3000, 6000));
                        }
                        if (Rs2Player.getWorldLocation().getPlane() != 3) {
                            //anti pattern turn off prayer
                            disablePrayer();
                            //anti pattern turn off prayer
                            break;
                        }
                    }
                }
                if (inTunnels) {
                    inTunnels = false;
                }
            }
        }
    }

    public void gainRP(){
        if(shouldAttackSkeleton){
            int RP = Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL);
            if(RP>870){
                Microbot.log("We have enough RP");
                return;
            }
            Rs2NpcModel skele = Rs2Npc.getNpc("Skeleton");
            if(skele == null || skele.isDead()){
                return;
            }
            if(Rs2Npc.hasLineOfSight(skele)){
                stopFutureWalker();
                if(!Rs2Player.isInCombat()){
                    if(Rs2Npc.attack(skele)){
                        sleepUntil(()-> Rs2Player.isInCombat()&&!Rs2Player.isMoving(), Rs2Random.between(4000,8000));
                    }
                }
                if(Rs2Player.isInCombat()){
                    while(Rs2Player.isInCombat()){
                        Microbot.log("Fighting the Skeleton.");
                        if (!super.isRunning()) {
                            break;
                        }
                        sleep(750,1500);
                        eatFood();
                        outOfSupplies();
                        antiPatternDropVials();

                        if(!Rs2Player.isInCombat()){
                            Microbot.log("Breaking out we're no longer in combat.");
                            break;
                        }

                        if(skele.isDead()){
                            Microbot.log("Breaking out the skeleton is dead.");
                            break;
                        }

                        if(Microbot.getVarbitValue(Varbits.BARROWS_REWARD_POTENTIAL)>870){
                            Microbot.log("Breaking out we have enough RP.");
                            break;
                        }

                        if(Microbot.getClient().getHintArrowNpc()!=null) {
                            Rs2NpcModel barrowsbrother = new Rs2NpcModel(Microbot.getClient().getHintArrowNpc());
                            if(Rs2Npc.hasLineOfSight(barrowsbrother)) {
                                Microbot.log("The brother is here.");
                                break;
                            }
                        }

                    }
                }
            }
        }
    }
    public void stopFutureWalker(){
        if(WalkToTheChestFuture!=null) {
            Rs2Walker.setTarget(null);
            WalkToTheChestFuture.cancel(true);
            //stop the walker and future
        }
    }
    public void suppliesCheck(){
        if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null || !Rs2Inventory.contains("Spade") ||
                Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName())<2 || (Rs2Inventory.get("Barrows teleport") !=null && Rs2Inventory.get("Barrows teleport").getQuantity() < 1)
                || Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < minForgottenBrews ||
                Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") < 1 ||
                Rs2Inventory.get(neededRune).getQuantity()<=minRuneAmt){
            Microbot.log("We need to bank.");
            if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null){
                Microbot.log("We don't have a ring of dueling equipped.");
            }
            if(!Rs2Inventory.contains("Spade")){
                Microbot.log("We don't have a spade.");
            }
            if(Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName())<2){
                Microbot.log("We have less than 2 food.");
            }
            if((Rs2Inventory.get("Barrows teleport") !=null && Rs2Inventory.get("Barrows teleport").getQuantity() < 1)){
                Microbot.log("We don't have a barrows teleport.");
            }
            if(Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < minForgottenBrews){
                Microbot.log("We forgot our Forgotten brew.");
            }
            if(Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") < 1){
                Microbot.log("We don't have enough prayer potions.");
            }
            if(Rs2Inventory.get(neededRune).getQuantity()<=minRuneAmt){
                Microbot.log("We have less than 180 "+neededRune);
            }
            shouldBank = true;
        } else {
            //if we're not all ready at the bank. This is needed because it could swap shouldBank to false while standing at the bank with 1 prayer potion
            if(Rs2Player.getWorldLocation().distanceTo(BankLocation.FEROX_ENCLAVE.getWorldPoint()) > 40){
                shouldBank = false;
            }
        }
    }
    public void stuckInTunsCheck(){
        //needed for rare occasions where the walker messes up
        if(tunnelLoopCount < 1){
            FirstLoopTile = Rs2Player.getWorldLocation();
        }
        if(tunnelLoopCount >= 15){
            WorldPoint currentTile = Rs2Player.getWorldLocation();
            if(currentTile!=null&&FirstLoopTile!=null){
                if(currentTile.equals(FirstLoopTile)){
                    Microbot.log("We seem to be stuck. Resetting the walker");
                    stopFutureWalker();
                }
            }
        }
        if(tunnelLoopCount >= 30){
            tunnelLoopCount = 0;
        }
    }

    public void gettheRune(){
        Rs2CombatSpells ourspell = Rs2Magic.getCurrentAutoCastSpell();
        neededRune = "unknown";
        if(ourspell.getName().toLowerCase().contains("blast")){
            neededRune = "Death rune";
        }
        if(ourspell.getName().toLowerCase().contains("wave")){
            neededRune = "Blood rune";
        }
        if(ourspell.getName().toLowerCase().contains("surge")){
            neededRune = "Wrath rune";
        }
    }

    public void activatePrayer(){
        if(!Rs2Prayer.isPrayerActive(NeededPrayer)){
            Microbot.log("Turning on Prayer.");
            while(!Rs2Prayer.isPrayerActive(NeededPrayer)){
                if (!super.isRunning()) {
                    break;
                }
                drinkPrayerPot();
                Rs2Prayer.toggle(NeededPrayer);
                sleep(0,750);
                if (Rs2Prayer.isPrayerActive(NeededPrayer)) {
                    //we made it in
                    Microbot.log("Praying");
                    break;
                }
            }
        }
    }
    public void antiPatternEnableWrongPrayer(){
        if(!Rs2Prayer.isPrayerActive(NeededPrayer)){
            if(Rs2Random.between(0,100) <= Rs2Random.between(1,4)) {
                Rs2PrayerEnum wrongPrayer = null;
                int random = Rs2Random.between(0,100);
                if(random <= 50){
                    wrongPrayer = Rs2PrayerEnum.PROTECT_MELEE;
                }
                if(random > 50 && random < 75){
                    wrongPrayer = Rs2PrayerEnum.PROTECT_RANGE;
                }
                if(random >= 75){
                    wrongPrayer = Rs2PrayerEnum.PROTECT_MAGIC;
                }
                drinkPrayerPot();
                Rs2Prayer.toggle(wrongPrayer);
                sleep(0, 750);
            }
        }
    }
    public void antiPatternActivatePrayer(){
        if(!Rs2Prayer.isPrayerActive(NeededPrayer)){
            if(Rs2Random.between(0,100) <= Rs2Random.between(1,8)) {
                drinkPrayerPot();
                Rs2Prayer.toggle(NeededPrayer);
                sleep(0, 750);
            }
        }
    }
    public void antiPatternDropVials(){
        if(Rs2Random.between(0,100) <= Rs2Random.between(1,25)) {
                if (Rs2Inventory.contains("Vial")) {
                    Rs2Inventory.drop("Vial");
                    sleep(0, 750);
                }
        }
    }
    public void outOfSupplies(){
        // Needed because the walker won't teleport to the enclave while in the tunnels or in a barrow
        if(shouldBank && (inTunnels || Rs2Player.getWorldLocation().getPlane() == 3)){
            if(Rs2Equipment.useRingAction(JewelleryLocationEnum.FEROX_ENCLAVE)){
                Microbot.log("We're out of supplies. Teleporting.");
                if(inTunnels){
                    inTunnels=false;
                }
                sleepUntil(() -> Rs2Player.isAnimating(), Rs2Random.between(2000, 4000));
                sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
            }
        }
    }
    public void disablePrayer(){
        if(Rs2Random.between(0,100) >= Rs2Random.between(0,5)) {
            Rs2Prayer.disableAllPrayers();
            sleep(0,750);
        }
    }
    public void reJfount(){
        int rejat = Rs2Random.between(10,30);
        int runener = Rs2Random.between(50,65);
        while(Rs2Player.getBoostedSkillLevel(Skill.PRAYER) < rejat || Rs2Player.getRunEnergy() <= runener){
            if (!super.isRunning()) {
                break;
            }
            if(Rs2Bank.isOpen()){
                if(Rs2Bank.closeBank()){
                    sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,4000));
                }
            } else {
                GameObject rej = Rs2GameObject.get("Pool of Refreshment", true);
                Microbot.log("Drinking");
                if(Rs2GameObject.interact(rej, "Drink")){
                    sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(1000,3000));
                    sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(5000,10000));
                    sleepUntil(()-> Rs2Player.isAnimating(), Rs2Random.between(1000,4000));
                    sleepUntil(()-> !Rs2Player.isAnimating(), Rs2Random.between(1000,4000));
                }
            }
            if(Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= rejat && Rs2Player.getRunEnergy() >= runener){
                break;
            }

        }
    }
    public void drinkPrayerPot(){
        if(Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= Rs2Random.between(9,15)){
            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Prayer potion"))){
                Rs2ItemModel prayerpotion = Rs2Inventory.get(it->it!=null&&it.getName().contains("Prayer potion"));
                if(Rs2Inventory.interact(prayerpotion, "Drink")){
                    sleep(0,750);
                }
            }
        }
    }
    public void checkForBrother(){
        NPC hintArrow = Microbot.getClient().getHintArrowNpc();
        Rs2NpcModel currentBrother = null;
        if (hintArrow != null) {
            currentBrother = new Rs2NpcModel(hintArrow);
            stopFutureWalker();
            Rs2PrayerEnum neededprayer = Rs2PrayerEnum.PROTECT_MELEE;
            if (currentBrother != null && Rs2Npc.hasLineOfSight(currentBrother)) {
                if(currentBrother.getName().contains("Ahrim")){
                    neededprayer = Rs2PrayerEnum.PROTECT_MAGIC;
                }
                if(currentBrother.getName().contains("Karil")){
                    neededprayer = Rs2PrayerEnum.PROTECT_RANGE;
                }
                //activate prayer
                if(!Rs2Prayer.isPrayerActive(neededprayer)){
                    Microbot.log("Turning on Prayer.");
                    while(!Rs2Prayer.isPrayerActive(neededprayer)){
                        if (!super.isRunning()) {
                            break;
                        }
                        drinkPrayerPot();
                        Rs2Prayer.toggle(neededprayer);
                        sleep(0,750);
                        if (Rs2Prayer.isPrayerActive(neededprayer)) {
                            //we made it in
                            Microbot.log("Praying");
                            break;
                        }
                    }
                }
                //fight brother
                if(currentBrother != null && !Rs2Player.isInCombat()){
                    while(!Rs2Player.isInCombat()){
                        if (!super.isRunning()) {
                            break;
                        }
                        Microbot.log("Attacking the brother");
                        Rs2Npc.interact(currentBrother, "Attack");
                        sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                    }
                }
                //fighting
                    while(Microbot.getClient().getHintArrowNpc() != null){
                        Microbot.log("Fighting the brother.");
                        if (!super.isRunning()) {
                            break;
                        }
                        sleep(750,1500);
                        drinkPrayerPot();
                        eatFood();
                        outOfSupplies();
                        antiPatternDropVials();

                        if(!Rs2Prayer.isPrayerActive(neededprayer)){
                            Microbot.log("Turning on Prayer.");
                            while(!Rs2Prayer.isPrayerActive(neededprayer)){
                                if (!super.isRunning()) {
                                    break;
                                }
                                drinkPrayerPot();
                                Rs2Prayer.toggle(neededprayer);
                                sleep(0,750);
                                if (Rs2Prayer.isPrayerActive(neededprayer)) {
                                    //we made it in
                                    Microbot.log("Praying");
                                    break;
                                }
                            }
                        }

                        if(!Rs2Player.isInCombat()){
                            if(Microbot.getClient().getHintArrowNpc() == null) {
                                // if we're not in combat and the brother isn't there.
                                Microbot.log("Breaking out hint arrow is null.");
                                break;
                            } else {
                                // if we're not in combat and the brother is there.
                                Microbot.log("Attacking the brother");
                                Rs2Npc.interact(currentBrother, "Attack");
                                sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                            }
                        }

                        if(currentBrother.isDead()){
                            Microbot.log("Breaking out the brother is dead.");
                            sleepUntil(()-> Microbot.getClient().getHintArrowNpc() == null, Rs2Random.between(3000,6000));
                            break;
                        }

                    }
            }
        }
    }

    private void walkToChest(){
        Rs2Walker.walkTo(Chest);
    }

    private void startWalkingToTheChest() {
        if(WalkToTheChestFuture == null || WalkToTheChestFuture.isCancelled() || WalkToTheChestFuture.isDone()) {
            if(inTunnels) {
                WalkToTheChestFuture = scheduledExecutorService.scheduleWithFixedDelay(
                        this::walkToChest,
                        0,
                        500,
                        TimeUnit.MILLISECONDS
                );
            }
        }
    }

    public void drinkforgottonbrew() {
            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("Forgotten brew"))) {
                if(Rs2Player.getBoostedSkillLevel(Skill.MAGIC) <= (Rs2Player.getRealSkillLevel(Skill.MAGIC) + Rs2Random.between(1,4))) {
                    Microbot.log("Drinking a Forgotten brew.");
                    if(Rs2Inventory.contains("Forgotten brew(1)")) {
                        Rs2Inventory.interact("Forgotten brew(1)", "Drink");
                        sleep(300,1000);
                        return;
                    }
                    if(Rs2Inventory.contains("Forgotten brew(2)")) {
                        Rs2Inventory.interact("Forgotten brew(2)", "Drink");
                        sleep(300,1000);
                        return;
                    }
                    if(Rs2Inventory.contains("Forgotten brew(3)")) {
                        Rs2Inventory.interact("Forgotten brew(3)", "Drink");
                        sleep(300,1000);
                        return;
                    }
                    if(Rs2Inventory.contains("Forgotten brew(4)")) {
                        Rs2Inventory.interact("Forgotten brew(4)", "Drink");
                        sleep(300,1000);
                        return;
                    }
                }
            }
    }

    public void eatFood(){
        if(Rs2Player.getHealthPercentage() <= 60){
            if(Rs2Inventory.contains(it->it!=null&&it.isFood())){
                Rs2ItemModel food = Rs2Inventory.get(it->it!=null&&it.isFood());
                if(Rs2Inventory.interact(food, "Eat")){
                    sleep(0,750);
                }
            }
        }
    }
    public void solvePuzzle(){

        //correct model ids are  6725, 6731, 6713, 6719
        //widget ids are 1638413, 1638415,1638417
        if(Rs2Widget.getWidget(1638413)!=null){
            stopFutureWalker();
            if(Rs2Widget.getWidget(1638413).getModelId() == 6725 || Rs2Widget.getWidget(1638413).getModelId() == 6731
            ||Rs2Widget.getWidget(1638413).getModelId() == 6713||Rs2Widget.getWidget(1638413).getModelId() == 6719){
                Microbot.log("Solution found");
                if(Rs2Widget.getWidget(1638413)!=null) {
                    Rs2Widget.clickWidget(1638413);
                    sleep(500, 1500);
                }
            }
        }

        if(Rs2Widget.getWidget(1638415)!=null){
            stopFutureWalker();
            if(Rs2Widget.getWidget(1638415).getModelId() == 6725 || Rs2Widget.getWidget(1638415).getModelId() == 6731
                    ||Rs2Widget.getWidget(1638415).getModelId() == 6713||Rs2Widget.getWidget(1638415).getModelId() == 6719){
                Microbot.log("Solution found");
                if(Rs2Widget.getWidget(1638415)!=null) {
                    Rs2Widget.clickWidget(1638415);
                    sleep(500, 1500);
                }
            }
        }

        if(Rs2Widget.getWidget(1638417)!=null){
            stopFutureWalker();
            if(Rs2Widget.getWidget(1638417).getModelId() == 6725 || Rs2Widget.getWidget(1638417).getModelId() == 6731
                    ||Rs2Widget.getWidget(1638417).getModelId() == 6713||Rs2Widget.getWidget(1638417).getModelId() == 6719){
                Microbot.log("Solution found");
                if(Rs2Widget.getWidget(1638417)!=null) {
                    Rs2Widget.clickWidget(1638417);
                    sleep(500, 1500);
                }
            }
        }

    }


    public enum BarrowsBrothers {
        DHAROK ("Dharok the Wretched", new Rs2WorldArea(3573,3296,3,3,0), Rs2PrayerEnum.PROTECT_MELEE),
        GUTHAN ("Guthan the Infested", new Rs2WorldArea(3575,3280,3,3,0), Rs2PrayerEnum.PROTECT_MELEE),
        KARIL  ("Karil the Tainted", new Rs2WorldArea(3564,3274,3,3,0), Rs2PrayerEnum.PROTECT_RANGE),
        TORAG  ("Torag the Corrupted", new Rs2WorldArea(3552,3282,2,2,0), Rs2PrayerEnum.PROTECT_MELEE),
        VERAC  ("Verac the Defiled", new Rs2WorldArea(3556,3297,3,3,0), Rs2PrayerEnum.PROTECT_MELEE),
        AHRIM  ("Ahrim the Blighted", new Rs2WorldArea(3563,3288,3,3,0), Rs2PrayerEnum.PROTECT_MAGIC);

        private String name;

        private Rs2WorldArea humpWP;

        private Rs2PrayerEnum whatToPray;


        BarrowsBrothers(String name, Rs2WorldArea humpWP, Rs2PrayerEnum whatToPray) {
            this.name = name;
            this.humpWP = humpWP;
            this.whatToPray = whatToPray;
        }

        public String getName() { return name; }
        public Rs2WorldArea getHumpWP() { return humpWP; }
        public Rs2PrayerEnum getWhatToPray() { return whatToPray; }

    }

    public enum PoweredStaffs {
        TRIDENT_OF_THE_SEAS_E ("Trident of the seas (e)", 10);

        private String name;

        private int ChargesLeft;

        PoweredStaffs(String name, int chargesLeft){
            this.name = name;
            this.ChargesLeft = chargesLeft;
        }

        public String getName() { return name; }

        public int getChargesLeft() { return ChargesLeft; }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
