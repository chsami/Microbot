package net.runelite.client.plugins.microbot.barrows;

import com.google.common.collect.ImmutableList;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.barrows.BarrowsPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.example.ExampleConfig;
import net.runelite.client.plugins.microbot.globval.enums.Prayers;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.woodcutting.Rs2Woodcutting;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class BarrowsScript extends Script {

    public static boolean test = false;
    public static boolean inTunnels = false;
    public String WhoisTun = "Unknown";
    private String neededRune = "unknown";
    private volatile boolean shouldWalk = false;
    private boolean shouldBank = false;
    int scriptDelay = Rs2Random.between(300,600);
    public boolean run(BarrowsConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                gettheRune();

                if(Rs2Player.getWorldLocation().getY() > 9600 && Rs2Player.getWorldLocation().getY() < 9730) {
                    inTunnels = true;
                } else {
                    inTunnels = false;
                }

                if(Rs2Magic.getCurrentAutoCastSpell() == null){
                    Microbot.log("Please select your wind spell in auto-cast then restart the script. stopping...");
                    super.shutdown();
                }

                if(Rs2Inventory.getInventoryFood().isEmpty()){
                    Microbot.log("No food in inventory. Please get some food then restart the script. stopping...");
                    super.shutdown();
                }

                if(neededRune.equals("Unknown") || neededRune.equals("unknown")){
                    Microbot.log("No catalytic rune in inventory. stopping...");
                    super.shutdown();
                }

                if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null || !Rs2Inventory.contains("Spade") ||
                        Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName())<5 || (Rs2Inventory.get("Barrows teleport") !=null && Rs2Inventory.get("Barrows teleport").getQuantity() < 1)
                        || Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < 1 ||
                        Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") < 1 ||
                        Rs2Inventory.get(neededRune).getQuantity()<=180){
                    Microbot.log("We need to bank.");
                    if(Rs2Equipment.get(EquipmentInventorySlot.RING)==null){
                        Microbot.log("We don't have a ring of dueling equipped.");
                    }
                    if(!Rs2Inventory.contains("Spade")){
                        Microbot.log("We don't have a spade.");
                    }
                    if(Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName())<5){
                        Microbot.log("We have less than 5 food.");
                    }
                    if((Rs2Inventory.get("Barrows teleport") !=null && Rs2Inventory.get("Barrows teleport").getQuantity() < 1)){
                        Microbot.log("We don't have a barrows teleport.");
                    }
                    if(Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < 1){
                        Microbot.log("We forgot our Forgotten brew.");
                    }
                    if(Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") < 1){
                        Microbot.log("We don't have enough prayer potions.");
                    }
                    if(Rs2Inventory.get(neededRune).getQuantity()<=180){
                        Microbot.log("We have less than 180 "+neededRune);
                    }
                    shouldBank = true;
                } else {
                    //if we're not all ready at the bank. This is needed because it could swap shouldBank to false while standing at the bank with 1 prayer potion
                    if(Rs2Player.getWorldLocation().distanceTo(BankLocation.FEROX_ENCLAVE.getWorldPoint()) > 40){
                        shouldBank = false;
                    }
                }

                if(!inTunnels && shouldBank == false) {
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        WorldPoint mound = brother.getHumpWP();
                        Microbot.log("Checking mound for: " + brother.getName() + " at " + mound);
                        //Enter mound
                        if (Rs2Player.getWorldLocation().getPlane() != 3) {
                            Microbot.log("Entering the mound");
                            while (mound.distanceTo(Rs2Player.getWorldLocation()) > 1) {
                                //antipattern turn on prayer early
                                if(!Rs2Prayer.isPrayerActive(brother.whatToPray)){
                                    if(Rs2Random.between(0,100) <= Rs2Random.between(1,5)) {
                                        drinkPrayerPot();
                                        Rs2Prayer.toggle(brother.whatToPray);
                                        sleep(0, 750);
                                    }
                                }
                                //antipattern
                                // We're not in the mound yet.
                                Rs2Walker.walkTo(mound);
                                if (mound.distanceTo(Rs2Player.getWorldLocation()) <= 1) {
                                    if(!Rs2Player.isMoving()) {
                                        break;
                                    }
                                } else {
                                    Microbot.log("At the mound, but we can't dig yet.");
                                    Rs2Walker.walkCanvas(mound);
                                    sleep(500,1500);
                                }
                                if (!super.isRunning()) {
                                    break;
                                }
                            }
                            while (mound.distanceTo(Rs2Player.getWorldLocation()) <= 1 && Rs2Player.getWorldLocation().getPlane() != 3) {
                                //antipattern turn on prayer early
                                if(!Rs2Prayer.isPrayerActive(brother.whatToPray)){
                                    if(Rs2Random.between(0,100) <= Rs2Random.between(1,10)) {
                                        drinkPrayerPot();
                                        Rs2Prayer.toggle(brother.whatToPray);
                                        sleep(0, 750);
                                    }
                                }
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
                                if (!super.isRunning()) {
                                    break;
                                }
                            }
                        }
                        if (Rs2Player.getWorldLocation().getPlane() == 3) {
                            Microbot.log("We're in the mound");
                            if(!Rs2Prayer.isPrayerActive(brother.whatToPray)){
                                Microbot.log("Turning on Prayer.");
                                while(!Rs2Prayer.isPrayerActive(brother.whatToPray)){
                                    drinkPrayerPot();
                                    Rs2Prayer.toggle(brother.whatToPray);
                                    sleep(0,750);
                                    if (Rs2Prayer.isPrayerActive(brother.whatToPray)) {
                                        //we made it in
                                        Microbot.log("Praying");
                                        break;
                                    }
                                    if (!super.isRunning()) {
                                        break;
                                    }
                                }
                            }
                            // we're in the mound, prayer is active
                            GameObject sarc = Rs2GameObject.get("Sarcophagus");
                            Rs2NpcModel currentBrother = null;
                            Microbot.log("Found the Sarcophagus");
                            while(currentBrother == null) {
                                Microbot.log("Searching the Sarcophagus");
                                if (Rs2GameObject.interact(sarc, "Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving(), Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat(), Rs2Random.between(3000, 6000));
                                    // the brother could take a second to spawn in.
                                    sleep(500,1250);
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
                                if (!super.isRunning()) {
                                    break;
                                }
                            }
                            //The ghost should be here assuming its not the tunnel.
                            if(currentBrother != null && !Rs2Player.isInCombat()){
                                while(!Rs2Player.isInCombat()){
                                    Microbot.log("Attacking the brother");
                                    Rs2Npc.interact(currentBrother, "Attack");
                                    sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                                }
                            }
                            //fighting
                            if(Rs2Player.isInCombat()){
                                Microbot.log("Fighting the brother.");
                                while(!currentBrother.isDead()){
                                    sleep(500,1500);
                                    eatFood();
                                    outOfSupplies();
                                    drinkforgottonbrew();
                                    drinkPrayerPot();
                                    if(!super.isRunning()){
                                        break;
                                    }
                                    if(Microbot.getClient().getHintArrowNpc() == null){
                                        break;
                                    }
                                    if(currentBrother.isDead()){
                                        break;
                                    }
                                }
                            }
                            // at this point the brother should be dead and we should be free to leave.
                            // We could be in ahrims mound while ahrim is tunnel. We need to stop the bot from leaving the mound and going back in.
                            if(brother.name.equals(WhoisTun) && brother.name.contains("Ahrim")) {
                                if (Rs2Dialogue.isInDialogue()) {
                                    while (Rs2Dialogue.isInDialogue()) {
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
                                        if (!super.isRunning()) {
                                            break;
                                        }
                                    }
                                    return;
                                }
                            }

                            if(Rs2Player.getWorldLocation().getPlane() == 3){
                                while(Rs2Player.getWorldLocation().getPlane() == 3) {
                                    Microbot.log("Leaving the mound");
                                    if (Rs2GameObject.interact("Staircase", "Climb-up")) {
                                        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != 3, Rs2Random.between(3000, 6000));
                                    }
                                    if(Rs2Player.getWorldLocation().getPlane() != 3){
                                        //anti pattern turn off prayer
                                        if(Rs2Random.between(0,100) <= Rs2Random.between(0,25)){
                                            disablePrayer();
                                        }
                                        //anti pattern turn off prayer
                                        break;
                                    }
                                    if(!super.isRunning()){
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                if(!WhoisTun.equals("Unknown") && shouldBank == false && !inTunnels){
                    Microbot.log("Going to the tunnels.");
                    for (BarrowsBrothers brother : BarrowsBrothers.values()) {
                        if (brother.name.equals(WhoisTun)) {
                            // Found the tunnel brother's mound
                            WorldPoint tunnelMound = brother.getHumpWP();
                            System.out.println("Navigating to tunnel mound: " + brother.name);

                            // Walk to the mound
                            while (tunnelMound.distanceTo(Rs2Player.getWorldLocation()) > 1) {
                                Rs2Walker.walkTo(tunnelMound);
                                sleep(300, 600);
                                if(tunnelMound.distanceTo(Rs2Player.getWorldLocation()) <= 1){
                                    if(!Rs2Player.isMoving()) {
                                        break;
                                    }
                                } else {
                                    Microbot.log("At the mound, but we can't dig yet.");
                                    Rs2Walker.walkCanvas(tunnelMound);
                                }
                                if (!super.isRunning()) break;
                            }

                            while (tunnelMound.distanceTo(Rs2Player.getWorldLocation()) <= 1 && Rs2Player.getWorldLocation().getPlane() != 3) {
                                if (Rs2Inventory.contains("Spade")) {
                                    if (Rs2Inventory.interact("Spade", "Dig")) {
                                        sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 3, Rs2Random.between(3000, 5000));
                                    }
                                }
                                if (Rs2Player.getWorldLocation().getPlane() == 3) {
                                    //we made it in
                                    break;
                                }
                                if (!super.isRunning()) {
                                    break;
                                }
                            }

                            GameObject sarc = Rs2GameObject.get("Sarcophagus");

                            while(!Rs2Dialogue.isInDialogue()) {
                                if (Rs2GameObject.interact(sarc, "Search")) {
                                    sleepUntil(() -> Rs2Player.isMoving(), Rs2Random.between(1000, 3000));
                                    sleepUntil(() -> !Rs2Player.isMoving() || Rs2Player.isInCombat(), Rs2Random.between(3000, 6000));
                                }
                                if(Rs2Dialogue.isInDialogue()){
                                    while(Rs2Dialogue.isInDialogue()){
                                        if(Rs2Dialogue.hasContinue()){
                                            Rs2Dialogue.clickContinue();
                                            sleep(300,600);
                                        }
                                        if(Rs2Dialogue.hasDialogueOption("Yeah I'm fearless!")){
                                            if(Rs2Dialogue.clickOption("Yeah I'm fearless!")){
                                                sleep(300,600);
                                                inTunnels = true;
                                            }
                                        }
                                        if(!Rs2Dialogue.isInDialogue()){
                                            break;
                                        }
                                        if (inTunnels) {
                                            break;
                                        }
                                        if (!super.isRunning()) {
                                            break;
                                        }
                                    }
                                    break;
                                }

                                if (inTunnels) {
                                    break;
                                }
                                if (!super.isRunning()) {
                                    break;
                                }
                            }

                            break; // done
                        }
                    }
                }


                if(inTunnels && shouldBank == false) {
                    Microbot.log("In the tunnels");
                    WhoisTun = "Unknown";
                    solvePuzzle();
                    //checkForBrother(); causes issues; commented out for now.
                    eatFood();
                    outOfSupplies();
                    WorldPoint Chest = new WorldPoint(3552,9694,0);

                    //threaded walk because the brother could appear, the puzzle door could be there.
                    if (Rs2Player.getWorldLocation().distanceTo(Chest) > 4) {
                        shouldWalk = true;
                        new Thread(() -> {
                            while (shouldWalk) {
                                Rs2Walker.walkTo(Chest);
                            }
                        }).start();
                    }
                    sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                    shouldWalk = false;
                    //threaded walk because the brother could appear, the puzzle door could be there.

                    solvePuzzle();

                    if(Rs2Player.getWorldLocation().distanceTo(Chest)<=4){
                        //we need to get the chest ID: 20973
                        TileObject chest = Rs2GameObject.findObjectById(20973);
                        if(Rs2GameObject.interact(chest, "Open")){
                            sleepUntil(()-> Microbot.getClient().getHintArrowNpc()!=null && Microbot.getClient().getHintArrowNpc().getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= 5, Rs2Random.between(3000,5000));
                        }
                        checkForBrother();
                        if(Microbot.getClient().getHintArrowNpc() == null){
                            int io = 0;
                            while(io < 3) {
                                if(Rs2Widget.hasWidget("Barrows chest")){
                                    break;
                                } else {
                                    if (Rs2GameObject.interact(chest, "Search")) {
                                        sleep(500, 1500);
                                    }
                                }
                                if(!super.isRunning()){
                                    break;
                                }
                                io++;
                            }
                            //we looted the chest time to reset
                            if(Rs2Inventory.get("Barrows teleport") == null || Rs2Inventory.get("Barrows teleport").getQuantity() <= 1 || (Rs2Inventory.count("Prayer potion(3)") + Rs2Inventory.count("Prayer potion(4)"))<=3 || Rs2Inventory.getInventoryFood().isEmpty() || Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName())<=3){
                                Microbot.log("We should bank.");
                                shouldBank = true;
                            } else {
                                shouldBank = false;
                                Rs2Inventory.interact("Barrows teleport", "Break");
                                sleepUntil(()-> Rs2Player.isAnimating(), Rs2Random.between(1000,2000));
                                sleepUntil(()-> !Rs2Player.isAnimating(), Rs2Random.between(3000,5000));
                            }
                        }
                    }
                }

                if(shouldBank){
                    if(!Rs2Bank.isOpen()){
                        Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE);
                    } else {


                        if(Rs2Inventory.isFull() || Rs2Inventory.contains(it->it!=null&&it.getName().contains("'s") ||
                                it.getName().contains("Coins"))){
                            List<Rs2ItemModel> ourfood = Rs2Inventory.getInventoryFood();
                            String ourfoodsname = ourfood.get(0).getName();
                            Rs2Bank.depositAllExcept(neededRune, "Spade", "Prayer potion(4)", "Prayer potion(3)", "Forgotten brew(4)", "Forgotten brew(3)", "Barrows teleport",
                                    ourfoodsname);
                        }

                        int howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(Rs2Inventory.get(neededRune).getQuantity()<200){
                                if(Rs2Bank.getBankItem(neededRune)!=null){
                                    if(Rs2Bank.getBankItem(neededRune).getQuantity()>200){
                                        if(Rs2Bank.withdrawX(neededRune, Rs2Random.between(200,1000))){
                                            String therune = neededRune;
                                            sleepUntil(()-> Rs2Inventory.get(therune).getQuantity() > 200, Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+neededRune+"s. stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") < Rs2Random.between(4,8)){
                                if(Rs2Bank.getBankItem("Prayer potion(4)")!=null){
                                    if(Rs2Bank.getBankItem("Prayer potion(4)").getQuantity()>4){
                                        int amt = ((Rs2Random.between(4,8)) - (Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)")));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX("Prayer potion(4)", amt)){
                                            sleepUntil(()-> Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") > Rs2Random.between(4,8), Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+" Prayer potions "+" need at least 4 stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") < Rs2Random.between(1,3)){
                                if(Rs2Bank.getBankItem("Forgotten brew(4)")!=null){
                                    if(Rs2Bank.getBankItem("Forgotten brew(4)").getQuantity()>3){
                                        int amt = ((Rs2Random.between(2,4)) - (Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)")));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX("Forgotten brew(4)", amt)){
                                            sleepUntil(()-> Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") > Rs2Random.between(1,3), Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+" Forgotten brew "+" need at least 4 stopping...");
                                        super.shutdown();
                                    }
                                }
                            }
                        }
                        howtoBank = Rs2Random.between(0,100);
                        if(howtoBank<= 40){
                            if(Rs2Inventory.get("Barrows teleport")==null || Rs2Inventory.get("Barrows teleport").getQuantity() < Rs2Random.between(1,3)){
                                if(Rs2Bank.getBankItem("Barrows teleport")!=null){
                                    if(Rs2Bank.getBankItem("Barrows teleport").getQuantity()>3){
                                        if(Rs2Bank.withdrawX("Barrows teleport", Rs2Random.between(1,10))){
                                            sleep(Rs2Random.between(300,750));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+" Barrows teleports "+" need at least 3 stopping...");
                                        super.shutdown();
                                    }
                                } else {
                                    Microbot.log("We're out of "+" Barrows teleports "+" need at least 3 stopping...");
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
                            if(Rs2Inventory.count(neededFood) < 10){
                                if(Rs2Bank.getBankItem(neededFood)!=null){
                                    if(Rs2Bank.getBankItem(neededFood).getQuantity()>10){
                                        int amt = (Rs2Random.between(5,15) - (Rs2Inventory.count(neededFood)));
                                        if(amt <= 0){
                                            amt = 1;
                                        }
                                        Microbot.log("Withdrawing "+amt);
                                        if(Rs2Bank.withdrawX(neededFood, amt)){
                                            String finalfood = neededFood;
                                            sleepUntil(()-> Rs2Inventory.count(finalfood) >= 10, Rs2Random.between(2000,4000));
                                        }
                                    } else {
                                        Microbot.log("We're out of "+neededFood+" need at least 10 stopping...");
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
                                Rs2Inventory.count(Rs2Inventory.getInventoryFood().get(0).getName())>=10 && Rs2Inventory.get("Barrows teleport").getQuantity() >= 2
                                && Rs2Inventory.count("Forgotten brew(4)") + Rs2Inventory.count("Forgotten brew(3)") >= 1 &&
                                Rs2Inventory.count("Prayer potion(4)") + Rs2Inventory.count("Prayer potion(3)") >= 4 &&
                                Rs2Inventory.get(neededRune).getQuantity()>=180){
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

                scriptDelay = Rs2Random.between(250,400);
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, scriptDelay, TimeUnit.MILLISECONDS);
        return true;
    }
    public void gettheRune(){
        Rs2CombatSpells ourspell = Rs2Magic.getCurrentAutoCastSpell();
        neededRune = "unknown";
        if(ourspell.getName().contains("Blast") || ourspell.getName().contains("blast")){
            neededRune = "Death rune";
        }
        if(ourspell.getName().contains("Wave") || ourspell.getName().contains("wave")){
            neededRune = "Blood rune";
        }
        if(ourspell.getName().contains("Surge") || ourspell.getName().contains("surge")){
            neededRune = "Wrath rune";
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
                sleepUntil(()-> Rs2Player.isAnimating(), Rs2Random.between(2000,4000));
                sleepUntil(()-> !Rs2Player.isAnimating(), Rs2Random.between(4000,6000));
            }
        }
    }
    public void disablePrayer(){
        if(Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)){
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
            sleep(0,750);
            return;
        }
        if(Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)){
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
            sleep(0,750);
            return;
        }
        if(Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)){
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
            sleep(0,750);
            return;
        }
    }
    public void reJfount(){
        int rejat = Rs2Random.between(10,30);
        int runener = Rs2Random.between(50,65);
        while(Rs2Player.getBoostedSkillLevel(Skill.PRAYER) < rejat || Rs2Player.getRunEnergy() <= runener){
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
            if(Rs2Player.getBoostedSkillLevel(Skill.PRAYER) >= rejat){
                break;
            }
            if(!super.isRunning()){
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
            shouldWalk = false;
            Rs2PrayerEnum neededprayer = Rs2PrayerEnum.PROTECT_MELEE;
            if (currentBrother != null) {
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
                        drinkPrayerPot();
                        Rs2Prayer.toggle(neededprayer);
                        sleep(0,750);
                        if (Rs2Prayer.isPrayerActive(neededprayer)) {
                            //we made it in
                            Microbot.log("Praying");
                            break;
                        }
                        if (!super.isRunning()) {
                            break;
                        }
                    }
                }
                //fight brother
                if(currentBrother != null && !Rs2Player.isInCombat()){
                    while(!Rs2Player.isInCombat()){
                        Microbot.log("Attacking the brother");
                        Rs2Npc.interact(currentBrother, "Attack");
                        sleepUntil(()-> Rs2Player.isInCombat(), Rs2Random.between(3000,6000));
                    }
                }
                //fighting
                if(Rs2Player.isInCombat()){
                    Microbot.log("Fighting the brother.");
                    while(!currentBrother.isDead()){
                        sleep(500,1500);
                        eatFood();
                        outOfSupplies();
                        drinkPrayerPot();
                        if(!super.isRunning()){
                            break;
                        }
                        if(!Rs2Player.isInCombat()){
                            break;
                        }
                        if(Microbot.getClient().getHintArrowNpc() == null){
                            break;
                        }
                        if(currentBrother.isDead()){
                            break;
                        }
                    }
                }
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
            if(Rs2Widget.getWidget(1638413).getModelId() == 6725 || Rs2Widget.getWidget(1638413).getModelId() == 6731
            ||Rs2Widget.getWidget(1638413).getModelId() == 6713||Rs2Widget.getWidget(1638413).getModelId() == 6719){
                Microbot.log("Solution found");
                Rs2Widget.clickWidget(1638413);
                sleep(500,1500);
            }
        }

        if(Rs2Widget.getWidget(1638415)!=null){
            if(Rs2Widget.getWidget(1638415).getModelId() == 6725 || Rs2Widget.getWidget(1638415).getModelId() == 6731
                    ||Rs2Widget.getWidget(1638415).getModelId() == 6713||Rs2Widget.getWidget(1638415).getModelId() == 6719){
                Microbot.log("Solution found");
                Rs2Widget.clickWidget(1638415);
                sleep(500,1500);
            }
        }

        if(Rs2Widget.getWidget(1638417)!=null){
            if(Rs2Widget.getWidget(1638417).getModelId() == 6725 || Rs2Widget.getWidget(1638417).getModelId() == 6731
                    ||Rs2Widget.getWidget(1638417).getModelId() == 6713||Rs2Widget.getWidget(1638417).getModelId() == 6719){
                Microbot.log("Solution found");
                Rs2Widget.clickWidget(1638417);
                sleep(500,1500);
            }
        }

    }


    public enum BarrowsBrothers {
        DHAROK ("Dharok the Wretched", new WorldPoint(3574, 3297, 0), Rs2PrayerEnum.PROTECT_MELEE),
        GUTHAN ("Guthan the Infested", new WorldPoint(3576, 3283, 0), Rs2PrayerEnum.PROTECT_MELEE),
        KARIL  ("Karil the Tainted", new WorldPoint(3565, 3276, 0), Rs2PrayerEnum.PROTECT_RANGE),
        TORAG  ("Torag the Corrupted", new WorldPoint(3553, 3283, 0), Rs2PrayerEnum.PROTECT_MELEE),
        VERAC  ("Verac the Defiled", new WorldPoint(3557, 3297, 0), Rs2PrayerEnum.PROTECT_MELEE),
        AHRIM  ("Ahrim the Blighted", new WorldPoint(3564, 3290, 0), Rs2PrayerEnum.PROTECT_MAGIC);

        private String name;

        private WorldPoint humpWP;

        private Rs2PrayerEnum whatToPray;


        BarrowsBrothers(String name, WorldPoint humpWP, Rs2PrayerEnum whatToPray) {
            this.name = name;
            this.humpWP = humpWP;
            this.whatToPray = whatToPray;
        }

        public String getName() { return name; }
        public WorldPoint getHumpWP() { return humpWP; }
        public Rs2PrayerEnum getWhatToPray() { return whatToPray; }

    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
