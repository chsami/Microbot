package net.runelite.client.plugins.microbot.barrows;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class BarrowsScript extends Script {

    public static boolean test = false;
    public static boolean inTunnels = false;
    public static String WhoisTun = "Unknown";
    public String neededRune = "unknown";
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
    public static boolean outOfPoweredStaffCharges = false;
    public static boolean usingPoweredStaffs = false;
    public static boolean firstRun = false;

    public boolean run(BarrowsConfig config, BarrowsPlugin plugin) {
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

                var inventorySetup = new Rs2InventorySetup(config.inventorySetup().getName(), mainScheduledFuture);

                if(firstRun) {
                    if (!inventorySetup.doesEquipmentMatch()) {
                        while(!inventorySetup.doesEquipmentMatch()) {
                            if(!super.isRunning()){ break; }
                            if (Rs2Bank.getNearestBank().getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) > 6) {
                                Rs2Bank.walkToBank();
                            }
                            if (Rs2Bank.getNearestBank().getWorldPoint().distanceTo(Rs2Player.getWorldLocation()) <= 6) {
                                inventorySetup.loadEquipment();
                            }
                        }
                    }
                    firstRun = false;
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

                //powered staffs
                if(Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Trident of the") ||
                        Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Tumeken's") ||
                            Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("sceptre") ||
                                Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Sanguinesti") ||
                                    Rs2Equipment.get(EquipmentInventorySlot.WEAPON).getName().contains("Crystal staff")) {
                    usingPoweredStaffs = true;
                } else {
                    usingPoweredStaffs = false;
                    gettheRune();
                    minRuneAmt = config.minRuneAmount();
                }

                minForgottenBrews = config.minForgottenBrew();
                shouldAttackSkeleton = config.shouldGainRP();

                if(usingPoweredStaffs) {
                    if (outOfPoweredStaffCharges) {
                        Microbot.log("No charges left on our staff. Stopping...");
                        super.shutdown();
                    }
                }

                outOfSupplies(config);

                if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.TELEPORT_TO_HOUSE) {
                    if (!inTunnels && !shouldBank && Rs2Player.getWorldLocation().distanceTo(new WorldPoint(3573, 3296, 0)) > 60) {
                        //needed to intercept the walker
                        if(Rs2GameObject.getGameObject(4525) == null){
                            Rs2Inventory.interact("Teleport to house", "Inside");
                            sleepUntil(() -> Rs2Player.getAnimation() == 4069, Rs2Random.between(2000, 4000));
                            sleepUntil(() -> !Rs2Player.isAnimating(), Rs2Random.between(6000, 10000));
                            sleepUntil(() -> Rs2GameObject.getGameObject(4525) != null, Rs2Random.between(6000, 10000));
                        }
                        handlePOH(config);
                        return;
                    }
                }

                if(!inTunnels && shouldBank == false) {
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        Rs2WorldArea mound = brother.getHumpWP();
                        NeededPrayer = brother.whatToPray;
                        outOfSupplies(config);
                        if(shouldBank){
                            return;
                        }

                        stopFutureWalker();
                        closeBank();

                        if(!usingPoweredStaffs){
                            setAutoCast();
                        }

                        Microbot.log("Checking mound for: " + brother.getName());

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

                        plugin.getLockCondition().lock();

                        //Enter mound
                        if (Rs2Player.getWorldLocation().getPlane() != 3) {
                            Microbot.log("Entering the mound");

                            handlePOH(config);

                            goToTheMound(mound);

                            digIntoTheMound(mound);

                        }
                        if (Rs2Player.getWorldLocation().getPlane() == 3) {
                            Microbot.log("We're in the mound");

                            if(config.shouldPrayAgainstWeakerBrothers()){
                                activatePrayer();
                            } else {
                                if(!brother.getName().contains("Torag") && !brother.getName().contains("Guthan") && !brother.getName().contains("Verac")){
                                    activatePrayer();
                                }
                            }

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
                                    sleepUntil(() -> Microbot.getClient().getHintArrowNpc()!=null || Rs2Dialogue.isInDialogue(), Rs2Random.between(750, 1500));
                                }
                                if(Rs2Dialogue.isInDialogue() && Rs2Dialogue.hasDialogueText("You've found a hidden")){
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

                                    if(config.shouldPrayAgainstWeakerBrothers()){
                                        activatePrayer();
                                    } else {
                                        if(!brother.getName().contains("Torag") && !brother.getName().contains("Guthan") && !brother.getName().contains("Verac")){
                                            activatePrayer();
                                        }
                                    }

                                    sleep(500,1500);
                                    eatFood();
                                    outOfSupplies(config);
                                    antiPatternDropVials();
                                    drinkforgottonbrew();

                                    if(config.shouldPrayAgainstWeakerBrothers()){
                                        drinkPrayerPot();
                                    } else {
                                        if(!brother.getName().contains("Torag") && !brother.getName().contains("Guthan") && !brother.getName().contains("Verac")){
                                            drinkPrayerPot();
                                        }
                                    }

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
                                    dialogueEnterTunnels();
                                    return;
                                }
                            }

                            leaveTheMound();
                        }
                    }
                }

                if(!WhoisTun.equals("Unknown") && shouldBank == false && !inTunnels){
                    int howManyBrothersWereKilled = Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) + Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM);
                    if(howManyBrothersWereKilled <= 4){
                        Microbot.log("We seem to have missed someone, checking all mounds again.");
                        return;
                    } else {
                        Microbot.log("Going to the tunnels.");
                    }

                    stopFutureWalker();
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        if (brother.name.equals(WhoisTun)) {
                            // Found the tunnel brother's mound
                            Rs2WorldArea tunnelMound = brother.getHumpWP();

                            handlePOH(config);

                            // Walk to the mound
                            goToTheMound(tunnelMound);

                            digIntoTheMound(tunnelMound);

                            while(!Rs2Dialogue.isInDialogue()) {
                                GameObject sarc = Rs2GameObject.get("Sarcophagus");

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

                                if(!Rs2Dialogue.isInDialogue()){
                                    //Somehow we got tun wrong.
                                    Microbot.log("We're in the wrong tunnel mound. Leaving...");
                                    this.leaveTheMound();
                                    WhoisTun = "Unknown";
                                    return;
                                }

                            }

                            dialogueEnterTunnels();

                            break;
                        }
                    }
                }


                if(inTunnels && !shouldBank) {
                    Microbot.log("In the tunnels");
                    if(!varbitCheckEnabled){
                        varbitCheckEnabled=true;
                    }
                    leaveTheMound();
                    stuckInTunsCheck();
                    solvePuzzle();
                    checkForBrother(config);
                    eatFood();
                    outOfSupplies(config);
                    gainRP(config);

                    if(!Rs2Player.isMoving()) {
                        startWalkingToTheChest();
                    }

                    solvePuzzle();
                    checkForBrother(config);

                    if(Rs2GameObject.findObjectById(20973) != null && Rs2GameObject.hasLineOfSight(Rs2GameObject.findObjectById(20973))){
                        //chest ID: 20973
                        stopFutureWalker();

                        TileObject chest = Rs2GameObject.findObjectById(20973);

                        if(Rs2GameObject.interact(chest, "Open")){
                            sleepUntil(()-> Microbot.getClient().getHintArrowNpc()!=null && Microbot.getClient().getHintArrowNpc().getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 5, Rs2Random.between(4000,6000));
                        }

                        checkForBrother(config);

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

                            suppliesCheck(config);

                            if(shouldBank){
                                Microbot.log("We should bank.");
                                ChestsOpened++;
                                WhoisTun = "Unknown";
                                inTunnels = false;
                            } else {
                                if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.BARROWS_TELEPORT){
                                    Rs2Inventory.interact("Barrows teleport", "Break");
                                    sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                                    ChestsOpened++;
                                    WhoisTun = "Unknown";
                                    inTunnels = false;
                                } else {
                                    Rs2Inventory.interact("Teleport to house", "Inside");
                                    sleepUntil(() -> Rs2Player.getWorldLocation().getY() < 9600 || Rs2Player.getWorldLocation().getY() > 9730, Rs2Random.between(6000, 10000));
                                    ChestsOpened++;
                                    WhoisTun = "Unknown";
                                    inTunnels = false;
                                    handlePOH(config);
                                }
                            }

                        }
                    }
                    tunnelLoopCount++;
                }

                if(shouldBank){
                    if(!Rs2Bank.isOpen()){
                        //stop the walker
                        stopFutureWalker();
                        //tele out
                        outOfSupplies(config);
                        //walk to and open the bank
                        Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE);
                        //unlock
                        plugin.getLockCondition().unlock();
                    } else {
                        Rs2Food ourfood = config.food();
                        int ourFoodsID = ourfood.getId();
                        String ourfoodsname = ourfood.getName();

                        if(Rs2Inventory.isFull() || Rs2Inventory.contains(it->it!=null&&it.getName().contains("'s") || it.getName().contains("Coins"))){
                            if(Rs2Inventory.contains(it->it!=null&&it.getName().contains("'s"))){
                                Rs2ItemModel piece = Rs2Inventory.get(it->it!=null&&it.getName().contains("'s"));

                                if(piece!=null){
                                    barrowsPieces.add(piece.getName());
                                    if(barrowsPieces.contains("Nothing yet.")){
                                        barrowsPieces.remove("Nothing yet.");
                                    }
                                }

                            }
                            Rs2Bank.depositAllExcept(neededRune, "Moonlight moth", "Moonlight moth mix (2)", "Teleport to house", "Spade", "Prayer potion(4)", "Prayer potion(3)", "Forgotten brew(4)", "Forgotten brew(3)", "Barrows teleport",
                                    ourfoodsname);
                        }

                        int howtoBank = Rs2Random.between(0,100);
                        if(!usingPoweredStaffs) {
                            if (howtoBank <= 40) {
                                if (Rs2Inventory.get(neededRune) == null || Rs2Inventory.get(neededRune).getQuantity() <= config.minRuneAmount()) {
                                    if (Rs2Bank.getBankItem(neededRune) != null) {
                                        if (Rs2Bank.getBankItem(neededRune).getQuantity() > config.minRuneAmount()) {
                                            if (Rs2Bank.withdrawX(neededRune, Rs2Random.between(config.minRuneAmount(), Rs2Bank.getBankItem(neededRune).getQuantity()))) {
                                                String therune = neededRune;
                                                sleepUntil(() -> Rs2Inventory.get(therune).getQuantity() > config.minRuneAmount(), Rs2Random.between(2000, 4000));
                                            }
                                        }
                                    } else {
                                        if(neededRune.equals("Wrath rune")){
                                            if(Rs2Bank.hasItem("Blood rune") && Rs2Bank.count("Blood rune") > config.minRuneAmount()){
                                                neededRune = "Blood rune";
                                                return;
                                            }
                                        }
                                        Microbot.log("We're out of " + neededRune + "s. stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        } else {
                            if(outOfPoweredStaffCharges){
                                Microbot.log("We're out of staff charges. stopping...");
                                super.shutdown();
                            }
                        }

                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 60){
                            if(Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) < Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())){
                                if(Rs2Bank.getBankItem(config.prayerRestoreType().getPrayerRestoreTypeID())!=null){
                                    if(Rs2Bank.getBankItem(config.prayerRestoreType().getPrayerRestoreTypeID()).getQuantity()>=config.targetPrayerPots()){
                                        int amt = ((Rs2Random.between(config.minPrayerPots(),config.targetPrayerPots())) - (Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID())));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(config.prayerRestoreType().getPrayerRestoreTypeID(), amt)){
                                            sleepUntil(()-> Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) > Rs2Random.between(4,8), Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+config.prayerRestoreType().getPrayerRestoreTypeID()+" need at least "+config.targetPrayerPots()+" stopping...");
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
                                            Microbot.log("We're out of " + " Forgotten brew " + " need at least " + config.targetForgottenBrew() + " stopping...");
                                            super.shutdown();
                                        }
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID())==null || Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()).getQuantity() < Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports())){
                                if(Rs2Bank.getBankItem(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID())!=null){
                                    if(Rs2Bank.getBankItem(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()).getQuantity()>=config.targetBarrowsTeleports()){
                                        if(Rs2Bank.withdrawX(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID(), Rs2Random.between(config.minBarrowsTeleports(),config.targetBarrowsTeleports()))){
                                            sleep(Rs2Random.between(300,750));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                        super.shutdown();
                                    }
                                } else {
                                    Microbot.log("We're out of "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()+" need at least "+config.targetBarrowsTeleports()+" stopping...");
                                    super.shutdown();
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){

                            if(Rs2Inventory.count(ourFoodsID) < config.targetFoodAmount()){
                                if(Rs2Bank.getBankItem(ourFoodsID)!=null){
                                    if(Rs2Bank.getBankItem(ourFoodsID).getQuantity()>=config.targetFoodAmount()){
                                        int amt = (Rs2Random.between(config.minFood(),config.targetFoodAmount()) - (Rs2Inventory.count(ourFoodsID)));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(ourFoodsID, amt)){
                                            sleepUntil(()-> Rs2Inventory.count(ourFoodsID) >= 10, Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+ourfoodsname+" need at least "+config.targetFoodAmount()+" stopping...");
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

                        suppliesCheck(config);

                        if(!shouldBank){
                            closeBank();
                            if(!Rs2Bank.isOpen()){
                                reJfount();
                                handlePOH(config);
                            }
                        } else {
                            if(Rs2Player.getRunEnergy() <= 5){
                                closeBank();
                                if(!Rs2Bank.isOpen()){
                                    reJfount();
                                }
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

    public void checkForWorldMap(){
        if(Rs2Widget.getWidget(38993938) != null){
            if(Rs2Widget.getWidget(38993938).getText().contains("Key")){
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            }
        }
    }

    public void closeBank(){
        if(Rs2Bank.isOpen()){
            while(Rs2Bank.isOpen()) {

                if(!super.isRunning()){break;}

                if (Rs2Bank.closeBank()) {
                    sleepUntil(() -> !Rs2Bank.isOpen(), Rs2Random.between(2000, 4000));
                }
            }
        }
    }

    public void handlePOH(BarrowsConfig config){
        if(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID() == ItemID.TELEPORT_TO_HOUSE){
            if(Rs2GameObject.getGameObject(4525) != null){
                Microbot.log("We're in our POH");
                GameObject rejPool = Rs2GameObject.getGameObject(it->it!=null&&it.getId() == 29238 || it.getId() == 29239 || it.getId() == 29241 || it.getId() == 29240);
                if(rejPool != null){
                    if(Rs2GameObject.interact(rejPool, "Drink")){
                        sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                        sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
                    }
                }
                GameObject regularPortal = Rs2GameObject.getGameObject("Barrows Portal");
                if(regularPortal != null){
                    while(Rs2GameObject.getGameObject(4525) != null){
                        if(!super.isRunning()){break;}
                        if(!Rs2Player.isMoving()){
                            if(Rs2GameObject.interact(regularPortal, "Enter")){
                                sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(10000,15000));
                                sleepUntil(()-> Rs2GameObject.getGameObject("Barrows Portal") == null, Rs2Random.between(10000,15000));
                            }
                        }
                    }

                } else {
                    // we have a nexus 33410
                    Microbot.log("No nexus support yet, shutting down");
                    super.shutdown();
                }
            }
        }
    }

    public boolean everyBrotherWasKilled(){
        if(Microbot.getVarbitValue(Varbits.BARROWS_KILLED_DHAROK) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_GUTHAN) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_KARIL) == 1&&
                Microbot.getVarbitValue(Varbits.BARROWS_KILLED_TORAG) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_VERAC) == 1&&Microbot.getVarbitValue(Varbits.BARROWS_KILLED_AHRIM) == 1){
            return true;
        }
        return false;
    }

    public void dialogueEnterTunnels(){
        if (Rs2Dialogue.isInDialogue()) {
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
        }
    }

    public void digIntoTheMound(Rs2WorldArea moundArea){
        while (moundArea.contains(Rs2Player.getWorldLocation()) && Rs2Player.getWorldLocation().getPlane() != 3) {
            checkForWorldMap();

            if (!super.isRunning()) {
                break;
            }

            //antipattern turn on prayer early
            antiPatternEnableWrongPrayer();

            antiPatternActivatePrayer();
            //antipattern

            if (Rs2Inventory.contains("Spade")) {
                if (Rs2Inventory.interact("Spade", "Dig")) {
                    sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 3, Rs2Random.between(3000, 5000));
                }
            }

            if (Rs2Player.getWorldLocation().getPlane() == 3) {
                //we made it in
                break;
            }
        }
    }

    public void goToTheMound(Rs2WorldArea moundArea){
        while (!moundArea.contains(Rs2Player.getWorldLocation())) {
            checkForWorldMap();
            int totalTiles = moundArea.toWorldPointList().size();
            WorldPoint randomMoundTile;
            if (!super.isRunning()) {
                break;
            }

            //antipattern turn on prayer early
            antiPatternEnableWrongPrayer();

            antiPatternActivatePrayer();

            antiPatternDropVials();
            //antipattern

            // We're not in the mound yet.
            randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
            if(Rs2Walker.walkTo(randomMoundTile)){
                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));
            }
            if (moundArea.contains(Rs2Player.getWorldLocation())) {
                if(!Rs2Player.isMoving()) {
                    break;
                }
            } else {
                Microbot.log("At the mound, but we can't dig yet.");
                randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));

                //strange old man body blocking us
                if(Rs2Npc.getNpc("Strange Old Man")!=null){
                    if(Rs2Npc.getNpc("Strange Old Man").getWorldLocation() != null){
                        if(Rs2Npc.getNpc("Strange Old Man").getWorldLocation() == randomMoundTile){
                            while(Rs2Npc.getNpc("Strange Old Man").getWorldLocation() == randomMoundTile){
                                if(!super.isRunning()){break;}
                                randomMoundTile = moundArea.toWorldPointList().get(Rs2Random.between(0,(totalTiles-1)));
                                sleep(250,500);
                            }
                        }
                    }
                }

                Rs2Walker.walkCanvas(randomMoundTile);
                sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(2000,4000));
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

    public void gainRP(BarrowsConfig config){
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
                        outOfSupplies(config);
                        antiPatternDropVials();

                        if(shouldBank){
                            Microbot.log("Breaking out we're out of supplies.");
                            break;
                        }

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
    public void suppliesCheck(BarrowsConfig config){
        if(!usingPoweredStaffs) {
            if (Rs2Equipment.get(EquipmentInventorySlot.RING) == null || !Rs2Inventory.contains("Spade") ||
                    Rs2Inventory.count(config.food().getName()) < 2 || (Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) == null)
                    || Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews ||
                    Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) < 1 ||
                    Rs2Inventory.get(neededRune) == null || Rs2Inventory.get(neededRune).getQuantity() <= minRuneAmt || Rs2Player.getRunEnergy() <= 5) {
                Microbot.log("We need to bank.");
                if (Rs2Equipment.get(EquipmentInventorySlot.RING) == null) {
                    Microbot.log("We don't have a ring of dueling equipped.");
                }
                if (!Rs2Inventory.contains("Spade")) {
                    Microbot.log("We don't have a spade.");
                }
                if (Rs2Inventory.count(config.food().getName()) < 2) {
                    Microbot.log("We have less than 2 food.");
                }
                if ((Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) == null)) {
                    Microbot.log("We don't have a "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemName());
                }
                if (Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews) {
                    Microbot.log("We forgot our Forgotten brew.");
                }
                if (Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) < 1) {
                    Microbot.log("We don't have enough "+config.prayerRestoreType().getPrayerRestoreTypeName());
                }
                if (Rs2Inventory.get(neededRune) == null || Rs2Inventory.get(neededRune).getQuantity() <= minRuneAmt) {
                    Microbot.log("We have less than 180 " + neededRune);
                }
                if(Rs2Player.getRunEnergy() <= 5){
                    Microbot.log("We need more run energy ");
                }
                shouldBank = true;
            } else {
                shouldBank = false;
            }
        }
        if(usingPoweredStaffs){
            if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null || !Rs2Inventory.contains("Spade") ||
                    Rs2Inventory.count(config.food().getName())<2 || (Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) == null)
                    || Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews ||
                    Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) < 1 || outOfPoweredStaffCharges
                    || Rs2Player.getRunEnergy() <= 5){
                Microbot.log("We need to bank.");
                if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null){
                    Microbot.log("We don't have a ring of dueling equipped.");
                }
                if(!Rs2Inventory.contains("Spade")){
                    Microbot.log("We don't have a spade.");
                }
                if(Rs2Inventory.count(config.food().getName())<2){
                    Microbot.log("We have less than 2 food.");
                }
                if((Rs2Inventory.get(config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemID()) ==null)){
                    Microbot.log("We don't have a "+config.selectedToBarrowsTPMethod().getToBarrowsTPMethodItemName());
                }
                if(Rs2Inventory.count(it->it!=null&&it.getName().contains("Forgotten brew(")) < minForgottenBrews){
                    Microbot.log("We forgot our Forgotten brew.");
                }
                if(Rs2Inventory.count(config.prayerRestoreType().getPrayerRestoreTypeID()) < 1){
                    Microbot.log("We don't have enough prayer potions.");
                }
                if(outOfPoweredStaffCharges){
                    Microbot.log("We're out of staff charges.");
                }
                if(Rs2Player.getRunEnergy() <= 5){
                    Microbot.log("We need more run energy ");
                }
                shouldBank = true;
            } else {
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
                    tunnelLoopCount = 0;
                }
            }
        }
        if(tunnelLoopCount >= 30){
            tunnelLoopCount = 0;
        }
    }

    public void gettheRune(){
        if(!neededRune.equals("unknown")) return;

        neededRune = "unknown";
        int magicLvl = Rs2Player.getRealSkillLevel(Skill.MAGIC);

        if(magicLvl >= 41 && magicLvl < 62){
            neededRune = "Death rune";
        }

        if(magicLvl >= 62 && magicLvl < 81){
            neededRune = "Blood rune";
        }

        if(magicLvl >= 81){
            neededRune = "Wrath rune";
        }
    }

    public void setAutoCast(){
        if(neededRune == "Wrath rune"){
            if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_SURGE) {
                Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_SURGE, false);
            }
        }

        if(neededRune == "Blood rune"){
            if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_WAVE) {
                Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_WAVE, false);
            }
        }

        if(neededRune == "Death rune"){
            if (Rs2Magic.getCurrentAutoCastSpell() != Rs2CombatSpells.WIND_BLAST) {
                Rs2Combat.setAutoCastSpell(Rs2CombatSpells.WIND_BLAST, false);
            }
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
            Rs2ItemModel whatToDrop = Rs2Inventory.get(it->it!=null&&it.getName().contains("Vial")||it.getName().contains("Butterfly jar"));
            if(whatToDrop!=null) {
                if (Rs2Inventory.contains(whatToDrop.getName())) {
                    if (Rs2Inventory.drop(whatToDrop.getName())) {
                        sleep(0, 750);
                    }
                }
            }
        }
    }
    public void outOfSupplies(BarrowsConfig config){
        suppliesCheck(config);
        // Needed because the walker won't teleport to the enclave while in the tunnels or in a barrow
        if(shouldBank && (inTunnels || Rs2Player.getWorldLocation().getPlane() == 3)){
            if(Rs2Equipment.interact(EquipmentInventorySlot.RING, "Ferox Enclave")){
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
                if(rej == null){ break; }
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
        boolean skipThePot = false;
        NPC hintArrow = Microbot.getClient().getHintArrowNpc();
        Rs2NpcModel currentBrother = null;
        if(hintArrow != null)  currentBrother = new Rs2NpcModel(hintArrow);
        if(currentBrother != null && !currentBrother.getName().contains("Dharok") && currentBrother.getHealthPercentage() < Rs2Random.between(35,42)) skipThePot = true;

        if(!skipThePot) {
            if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) <= Rs2Random.between(8, 15)) {
                if (Rs2Inventory.contains(it -> it != null && it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth"))) {
                    Rs2ItemModel prayerpotion = Rs2Inventory.get(it -> it != null && it.getName().contains("Prayer potion") || it.getName().contains("moth mix") || it.getName().contains("Moonlight moth"));
                    String action = "Drink";
                    if (prayerpotion.getName().equals("Moonlight moth")) {
                        action = "Release";
                    }
                    if (Rs2Inventory.interact(prayerpotion, action)) {
                        sleep(0, 750);
                    }
                }
            }
        }
    }
    public void checkForBrother(BarrowsConfig config){
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
                        if(!Rs2Npc.hasLineOfSight(currentBrother)){
                            break;
                        }
                        sleep(750,1500);
                        drinkPrayerPot();
                        eatFood();
                        outOfSupplies(config);
                        antiPatternDropVials();
                        drinkforgottonbrew();

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

        int widgets[] = {1638413, 1638415, 1638417};
        int modelIDs[] = {6725, 6731, 6713, 6719};

        for (int widget : widgets) {
            if(!super.isRunning()) break;

            if(Rs2Widget.getWidget(widget)!=null){
                for (int modelID : modelIDs) {
                    if(!super.isRunning()) break;

                    if(Rs2Widget.getWidget(widget).getModelId() == modelID){
                        Microbot.log("Solution found");
                        stopFutureWalker();
                        Rs2Widget.clickWidget(widget);
                    }
                }
            } else {
                break;
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

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
