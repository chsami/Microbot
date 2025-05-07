package net.runelite.client.plugins.microbot.revKiller;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.kit.KitType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.questhelper.collections.ItemWithCharge;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.http.api.worlds.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class revKillerScript extends Script {
    private revKillerConfig config;
    @Provides
    revKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(revKillerConfig.class);
    }
    private ItemManager itemManager;
    int potthresh = generateRandomNumber(3,6);
    int potsUntilSwap = 0;
    private boolean hoppedWorld = false;

    WorldPoint cave = new WorldPoint(3076, 3652, 0);

    WorldPoint caveBeginning = new WorldPoint(3201, 10058, 0);

    public static boolean test = false;
    WorldPoint selectedWP;
    WorldPoint revimp;
    int selectedArrow;
    int LowOnArrowsCount = generateRandomNumber(30,60);
    List<World> filteredWorlds = new ArrayList<>();
    long randomdelay = generateRandomNumber(350,1000);
    protected ScheduledFuture<?> checkForPKerFuture;
    protected ScheduledFuture<?> healthCheckFuture;
    private boolean weDied = false;
    private boolean useTimedWorldHopper = false;
    private long howLongUntilHop = 0;
    private volatile boolean shouldFlee = false;
    private long startTime = System.currentTimeMillis();


    public boolean run(revKillerConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                //get the selected rev WP via config.
                selectedWP = config.selectedRev().getWorldPoint();
                selectedArrow = config.selectedArrow().getArrowID();
                // set it to our script
                revimp = selectedWP;
                useTimedWorldHopper = config.shouldUseTimedWorldHopper();
                randomdelay = generateRandomNumber(400,900);
                if(howLongUntilHop == 0){
                    if(useTimedWorldHopper) {
                        howLongUntilHop = config.hopInMinutes();
                    }
                }

                if(shouldFlee){
                    return;
                }

                DidWeDie();

                if(areWeEquipped()){

                    if(Rs2Player.getWorldLocation().distanceTo(revimp)>10){

                        WalkToRevs();

                    } else {

                        equipArrows();

                        drinkPotion();

                        loot();

                        specialAttack();

                        fightrev();

                        specialAttack();

                        loot();

                    }
                }

                if(!areWeEquipped()||isItTimeToGo()){
                    Bankfortrip();
                }
                
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
            //changed delay to 500ms for faster pker finding
        }, 0, randomdelay, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    public void EatFood(){
        if(Rs2Player.getHealthPercentage() <= generateRandomNumber(70,80)){
            if(Rs2Inventory.contains("Shark")){
                if(Rs2Inventory.interact("Shark", "Eat")){
                    sleepUntil(()-> isPkerAround(),generateRandomNumber(2500,3500));
                }
            }
        }
    }

    public boolean WeAreInTheCaves(){

        if(Rs2Player.getWorldLocation().getY() > 9000){
            return true;
        }

        return false;
    }

    public boolean timeToBreak(){

        if (BreakHandlerScript.breakIn <= 300) {
            return true;
        }

        return false;
    }

    public void handleBreaks() {
        if (timeToBreak()) {
            if(Rs2Bank.isOpen()){
                if(Rs2Bank.closeBank()){
                    sleepUntil(()-> !Rs2Bank.isOpen(), generateRandomNumber(2000,5000));
                }
            }
            if(!Rs2Bank.isOpen()) {
                sleepUntil(() -> BreakHandlerScript.isBreakActive(), 2000000);
            }
        }
    }

    public void DidWeDie(){
        if(weDied){
            Microbot.log("We died");
            Rs2Player.logout();
            super.shutdown();
        }
    }

    public void WalkToRevs(){
        drinkStamPotion();
        if(!WeAreInTheCaves()){
            //we must walk to the cave entrence
            if(Rs2Player.getWorldLocation().distanceTo(cave) > 6){
                if(Rs2Walker.walkTo(cave, Rs2Player.getWorldLocation().distanceTo(cave) - (generateRandomNumber(2,5)))){
                    Microbot.log("Walking to cave. with new method.");
                }
            } else {
                if(!Rs2Dialogue.isInDialogue()){
                    Microbot.log("At the cave, clicking.");
                    if(Rs2GameObject.interact(31555, "Enter")){
                        sleepUntil(()-> Rs2Dialogue.isInDialogue(), generateRandomNumber(1000,3000));
                    }
                }
                if(Rs2Dialogue.isInDialogue()){
                    if(Rs2Dialogue.hasContinue()){
                        Rs2Dialogue.clickContinue();
                        sleep(500,1000);
                    }
                    if(Rs2Dialogue.getDialogueOption("Yes", false)!=null){
                        Rs2Dialogue.clickOption("Yes", false);
                        sleep(500,1000);
                    }
                    if(Rs2Dialogue.getDialogueOption("Accept", false)!=null){
                        Rs2Dialogue.clickOption("Accept", false);
                        sleep(500,1000);
                    }
                }
            }
        } else {
            if(WeAreInTheCaves()){
                if(Rs2Walker.walkTo(revimp, Rs2Player.getWorldLocation().distanceTo(revimp) - (generateRandomNumber(2,5)))){
                    Microbot.log("Walking to Revs. with new method.");
                }
            }
        }
    }

    public boolean TeleTimerIsThere(){

        if(!Rs2Widget.isWidgetVisible(162, 558)){
            return true;
        }

        return false;
    }

    public void hopWorldsBasedOnTimer(){
        if(useTimedWorldHopper) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - startTime >= howLongUntilHop * 60 * 1000) {
                Microbot.log("Hopping to a new world, it's been "+howLongUntilHop+" minutes");
                if(Rs2Player.isInCombat()){
                    sleepUntil(()-> isPkerAround() || !Rs2Player.isInCombat(), generateRandomNumber(10000,20000));
                    if(isPkerAround()){
                        return;
                    }
                }
                hopToNewWorld();
                startTime = currentTime;
                howLongUntilHop = config.hopInMinutes();
                int mixUp = Rs2Random.between(5,10);
                int io = 0;
                while (io < mixUp){
                    if(!super.isRunning()){
                        break;
                    }
                    if(Rs2Random.between(0,100) < 50) {
                        if (howLongUntilHop > 8 * 60 * 1000) {
                            howLongUntilHop -= 2 * 60 * 1000;
                        }
                    }
                    if(Rs2Random.between(0,100) < 50) {
                        if (howLongUntilHop < 30 * 60 * 1000) {
                            howLongUntilHop += 2 * 60 * 1000;
                        }
                    }
                    if(howLongUntilHop < 8 || howLongUntilHop > 30){
                        howLongUntilHop = config.hopInMinutes();
                    }
                    io++;
                }
                Microbot.log("We'll hop to a new world in "+howLongUntilHop+" minutes");
            }
        }
    }

    public void getAwayFromPker(){
        // code to run or teleport from pker
        Microbot.log("Attemping to get away from the PKer.");
        if(!Rs2Player.isTeleBlocked()){
            Microbot.log("At least we're not teleblocked.");
            if(Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 30) {
                while (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 30) {
                    if (!super.isRunning()) {
                        break;
                    }
                    Microbot.log("Walking to below");
                    WorldPoint safe1 = (new WorldPoint(3199, 10071, 0));
                    WorldPoint safe2 = (new WorldPoint(3226, 10067, 0));

                    if(Rs2Player.getWorldLocation().distanceTo(safe1) <= Rs2Player.getWorldLocation().distanceTo(safe2)){
                        Rs2Walker.walkTo(safe1, Rs2Player.getWorldLocation().distanceTo(safe1) - (generateRandomNumber(2,5)));
                    }

                    if(Rs2Player.getWorldLocation().distanceTo(safe1) > Rs2Player.getWorldLocation().distanceTo(safe2)){
                        Rs2Walker.walkTo(safe2, Rs2Player.getWorldLocation().distanceTo(safe2) - (generateRandomNumber(2,5)));
                    }

                    if (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 30) {
                        break;
                    }
                }
            }
            if(Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) >= 20 && Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 30) {
                while (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) >= 20 && Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 30) {
                    Microbot.log("Attempting to teleport via glory");
                    if (!super.isRunning()) {
                        break;
                    }
                    if (Rs2Equipment.useAmuletAction(JewelleryLocationEnum.EDGEVILLE)) {
                        sleepUntil(()-> TeleTimerIsThere() || Rs2Player.getAnimation() == 714,generateRandomNumber(250,500));
                        sleepUntil(()-> !TeleTimerIsThere() || Rs2Player.getAnimation() == 714,generateRandomNumber(1300,1500));
                        if(Rs2Player.getAnimation() == 714){
                            //we successfully teleported out
                            sleepUntil(()-> !Rs2Player.isAnimating() && !WeAreInTheCaves(),generateRandomNumber(4000,6000));
                        }
                    }
                    if (!WeAreInTheCaves()) {
                        if(Rs2Player.isInCombat()){
                            sleepUntil(()-> !Rs2Player.isInCombat(), generateRandomNumber(10000,15000));
                            sleep(0,1200);
                        }
                        hopToNewWorld();
                        break;
                    }
                    if (Rs2Player.isTeleBlocked()) {
                        break;
                    }
                }
                shouldFlee = false;
                return;
            }
            if(Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 20) {
                while (Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 20) {
                    Microbot.log("Attempting to teleport via dueling");
                    if (!super.isRunning()) {
                        break;
                    }
                    if (Rs2Equipment.useRingAction(JewelleryLocationEnum.FEROX_ENCLAVE)) {
                        sleepUntil(()-> TeleTimerIsThere() || Rs2Player.getAnimation() == 714,generateRandomNumber(250,500));
                        sleepUntil(()-> !TeleTimerIsThere() || Rs2Player.getAnimation() == 714,generateRandomNumber(1300,1500));
                        if(Rs2Player.getAnimation() == 714){
                            //we successfully teleported out
                            sleepUntil(()-> !Rs2Player.isAnimating() && !WeAreInTheCaves(),generateRandomNumber(4000,6000));
                        }
                    }
                    if (!WeAreInTheCaves()) {
                        if(Rs2Player.isInCombat()){
                            sleepUntil(()-> !Rs2Player.isInCombat(), generateRandomNumber(10000,15000));
                            sleep(0,1200);
                        }
                        hopToNewWorld();
                        break;
                    }
                    if (Rs2Player.isTeleBlocked()) {
                        break;
                    }
                }
            }
            shouldFlee = false;
        } else {
            Microbot.log("We're teleblocked! Attempting to run to the bank");
            enablePrayer();
            Rs2Walker.walkTo(BankLocation.FEROX_ENCLAVE.getWorldPoint());
            Rs2Prayer.disableAllPrayers();
            shouldFlee = false;
        }
    }

    public void enablePrayer(){
        if(!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
            if (Rs2Player.getRealSkillLevel(Skill.PRAYER) >= 37) {
                if (Rs2Player.getBoostedSkillLevel(Skill.PRAYER) > 0) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC);
                }
            }
        }
    }

    public void fightrev(){
        Rs2NpcModel Rev = (Rs2Npc.getNpc("Revenant", false));
        if(Rev!=null){

            List<Rs2PlayerModel> playerlist = new ArrayList<Rs2PlayerModel>();
            playerlist.addAll(Rs2Player.getPlayers(it->it!=null&&it.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())<= 8&&!it.equals(Rs2Player.getLocalPlayer())).collect(Collectors.toList()));

            if(!playerlist.isEmpty()){
                if(!Rs2Player.isInCombat()) {
                    Microbot.log("There's another player here hopping.");
                    hopToNewWorld();
                    return;
                }
            }

            if(!Rev.isInteracting() && !Rs2Player.isInteracting() && !Rev.isDead()) {
                if(useTimedWorldHopper){
                    hopWorldsBasedOnTimer();
                }
                if(Rev==null){
                    //we hopped
                    return;
                }
                Microbot.log("Attacking Rev");
                if (Rs2Npc.interact(Rev, "Attack")) {
                    sleepUntil(() -> Rev.isDead() || !Rs2Player.isInCombat() || Rs2GroundItem.isItemBasedOnValueOnGround(500,12) || isItTimeToGo() || Rs2Player.getHealthPercentage() <= generateRandomNumber(70, 80), generateRandomNumber(60000, 120000));
                    hoppedWorld=false;
                }
            }

            if(Rev.isInteracting() && playerlist.isEmpty()) {
                if(hoppedWorld) {
                    Microbot.log("Rev is attacking us attacking back.");
                    if (Rs2Npc.interact(Rev, "Attack")) {
                        hoppedWorld=false;
                        sleepUntil(() -> Rev.isDead() || !Rs2Player.isInCombat() || Rs2GroundItem.isItemBasedOnValueOnGround(500, 12) || isItTimeToGo() || Rs2Player.getHealthPercentage() <= generateRandomNumber(70, 80), generateRandomNumber(60000, 120000));
                    }
                }
            }

        } else {
            if(!Rs2Player.isInCombat()) {
                Microbot.log("No revs found, hopping");
                sleepUntil(()-> isPkerAround(), generateRandomNumber(0,1200));
                hopToNewWorld();
            }
        }
    }

    public void startHealthCheck() {
        Microbot.log("Health detection started");
        healthCheckFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::futureEatFood,
                0,
                1000,
                TimeUnit.MILLISECONDS
        );
    }

    public void futureEatFood(){
        EatFood();
    }

    public void startPkerDetection() {
        Microbot.log("PKer detection started");
        checkForPKerFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::futurePKCheck,
                0,
                250,
                TimeUnit.MILLISECONDS
        );
    }

    public void stopFutures() {
        if(checkForPKerFuture!=null){
            checkForPKerFuture.cancel(true);
        }
        if(healthCheckFuture!=null) {
            healthCheckFuture.cancel(true);
        }
    }

    public void futurePKCheck(){
        try {
            if(isPkerAround()){
                shouldFlee = true;
                getAwayFromPker();
            }
        } catch (Exception e) {
            Microbot.log("Error during PK check: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void hopToNewWorld(){
        // Thank you george!

        World currentWorld = Microbot.getWorldService().getWorlds().findWorld(Microbot.getClient().getWorld());

        if(currentWorld == null){
            Microbot.log("Couldn't find our world");
            return;
        }

        int newRandomWorld = Login.getRandomWorld(true, currentWorld.getRegion());

        if(newRandomWorld == 0){
            Microbot.log("Couldn't find a new random world");
            return;
        }

        Microbot.log("We're going to world " + newRandomWorld);
        int attempts = 0;
        int tries = generateRandomNumber(2,6);
        while(Rs2Player.getWorld() != newRandomWorld){
            if (!super.isRunning()) {
                break;
            }
            if(Microbot.hopToWorld(newRandomWorld)){
                sleepUntil(() -> !Microbot.isHopping() || isPkerAround() || Rs2Player.getWorld() == newRandomWorld, generateRandomNumber(5000, 10000));
            }
            if(Rs2Player.getWorld() == newRandomWorld){
                break;
            }
            if(attempts>=tries){
                break;
            }
            sleep(500,700);
            attempts++;
        }
        hoppedWorld = true;
    }

    public void equipArrows(){
        if(Rs2Inventory.contains(selectedArrow)){
            if(Rs2Inventory.interact(selectedArrow, "Wield")){
                sleepUntil(()-> !Rs2Inventory.contains(selectedArrow), generateRandomNumber(5000,15000));
            }
        }
    }

    public void specialAttack(){
        if(50>generateRandomNumber(0,100)) {
            if (Rs2Combat.getSpecEnergy() >= generateRandomNumber(600,1000)) {
                if(!Rs2Combat.getSpecState()) {
                    if (Rs2Combat.setSpecState(true)) {
                        sleepUntil(() -> isPkerAround(), generateRandomNumber(350, 600));
                    }
                }
            }
        }
    }

    public void drinkPotion(){
        if(!Rs2Player.hasRangingPotionActive(potthresh)){
            Microbot.log("Drinking ranging potion");
            if(potsUntilSwap>=generateRandomNumber(2,10)){
                potthresh = generateRandomNumber(3,6);
                potsUntilSwap=0;
            }
            if(Rs2Inventory.get(it->it!=null&&it.getName().contains("Ranging"))!=null){
                if(Rs2Inventory.interact(Rs2Inventory.get(it->it!=null&&it.getName().contains("Ranging")),"Drink")){
                    sleepUntil(()-> isPkerAround(), generateRandomNumber(2500,3500));
                    potsUntilSwap++;
                }
            }
        }
    }

    public void drinkStamPotion(){
        if(!Rs2Player.hasStaminaActive()){
            if(Rs2Player.getRunEnergy() <= generateRandomNumber(20,40)) {
                Microbot.log("Drinking stamina potion");
                if (Rs2Inventory.get(it -> it != null && it.getName().contains("Stamina")) != null) {
                    if (Rs2Inventory.interact(Rs2Inventory.get(it -> it != null && it.getName().contains("Stamina")), "Drink")) {
                        sleepUntil(() -> Rs2Player.hasStaminaActive(), generateRandomNumber(500, 1000));
                    }
                }
            }
        }
    }

    public void loot(){
        if(Rs2GroundItem.isItemBasedOnValueOnGround(500,10)){
            while(Rs2GroundItem.isItemBasedOnValueOnGround(500,10)){
                if(!super.isRunning()){
                    break;
                }
                if(Rs2Inventory.isFull()){
                    if(Rs2Inventory.contains("Shark")){
                        if(Rs2Inventory.interact("Shark", "Eat")){
                            sleepUntil(()-> !Rs2Player.isAnimating(),generateRandomNumber(2000,3500));
                        }
                    } else {
                        break;
                    }
                }
                if(Rs2GroundItem.lootItemBasedOnValue(new LootingParameters(500,50000000, 10,1,1,false,false))){
                    sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(750,1500));
                    if(Rs2Player.isMoving()){
                        sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(3000,6000));
                    }
                }
                if(!Rs2GroundItem.isItemBasedOnValueOnGround(500,10)){
                    break;
                }
            }
        }
    }

    public void OpenTheInv(){
        if(!Rs2Inventory.isOpen()) {
            if (!Rs2Bank.isOpen()) {
                if(Rs2Inventory.open()){
                    sleepUntil(()-> Rs2Inventory.isOpen(), generateRandomNumber(500,1000));
                }
            }
        }
    }

    public void stuckAtEnclave(){
        WorldPoint stuckSpot = new WorldPoint(3124,3636,0);
        if(Rs2Player.getWorldLocation().equals(stuckSpot)){
            if(Rs2Equipment.get(EquipmentInventorySlot.RING)!=null){

            } else {
                Microbot.log("We're stuck outside of the enclave");
                if(Rs2GameObject.exists(39653)){
                    if(Rs2GameObject.interact(39653, "Pass-Through")){
                        sleepUntil(()-> Rs2Player.isMoving(), Rs2Random.between(2000,4000));
                        sleepUntil(()-> !Rs2Player.isMoving(), Rs2Random.between(4000,8000));
                    }
                }
            }
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        //Thank you george!
        if (event.getActor() == Microbot.getClient().getLocalPlayer()) {
            weDied = true;
        }
    }

    public void Bankfortrip(){
        if(!Rs2Bank.isOpen()){
            if(WeAreInTheCaves()){
                Microbot.log("Teleing to bank");
                WorldPoint safe1 = (new WorldPoint(3199, 10071, 0));
                WorldPoint safe2 = (new WorldPoint(3226, 10067, 0));
                if(Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 20){
                    if(Rs2Player.getWorldLocation().distanceTo(safe1) <= Rs2Player.getWorldLocation().distanceTo(safe2)){
                        Rs2Walker.walkTo(safe1, Rs2Player.getWorldLocation().distanceTo(safe1) - (generateRandomNumber(2,5)));
                    }
                    if(Rs2Player.getWorldLocation().distanceTo(safe1) > Rs2Player.getWorldLocation().distanceTo(safe2)){
                        Rs2Walker.walkTo(safe2, Rs2Player.getWorldLocation().distanceTo(safe2) - (generateRandomNumber(2,5)));
                    }
                }
                if(Rs2Pvp.getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 20) {
                    if (Rs2Equipment.useRingAction(JewelleryLocationEnum.FEROX_ENCLAVE)) {
                        sleepUntil(()-> Rs2Player.isAnimating(), generateRandomNumber(2000,4000));
                        sleepUntil(()-> !Rs2Player.isAnimating(), generateRandomNumber(6000,10000));
                        Microbot.log("Teleing");
                    }
                }
            }
            if(!WeAreInTheCaves()) {
                Microbot.log("Walking and using bank");
                DidWeDie();
                OpenTheInv();
                stuckAtEnclave();
                Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE);
            }
        } else {
            // we're at the bank and it should be open.
            shouldFlee = false;
            handleBreaks();
            int howtobank;
            howtobank = generateRandomNumber(0,100);
            Microbot.log("Random number: " + howtobank);
            //equipring
            if(howtobank <= 80){
                if(isItTimeToGo()||Rs2Inventory.contains(it->it!=null&&it.getName().contains("sack")||it.getName().contains("Blighted")||it.getName().contains("rune"))){
                    //If we have more than 200k loot or the Inventory is full
                    Microbot.log("We have loot, depositing all");
                    Rs2Bank.depositAll();
                    sleepUntil(()-> Rs2Inventory.isEmpty(), generateRandomNumber(5000,15000));
                }
            }
            howtobank = generateRandomNumber(0,100);
            if(howtobank <= 40){
                if(Rs2Equipment.get(EquipmentInventorySlot.AMULET).getName().equals("Amulet of glory")){
                    Microbot.log("Getting a fresh amulet of glory");
                    if(Rs2Bank.count(ItemID.AMULET_OF_GLORY6)>0){
                        if(!Rs2Inventory.contains(ItemID.AMULET_OF_GLORY6)){
                            if(Rs2Bank.withdrawX(ItemID.AMULET_OF_GLORY6, 1)){
                                sleepUntil(()-> Rs2Inventory.contains(ItemID.AMULET_OF_GLORY6), generateRandomNumber(5000,15000));
                            }
                        }
                    } else {
                        Microbot.log("Out of amulets of glory 6");
                        super.shutdown();
                    }
                    if(Rs2Inventory.contains(ItemID.AMULET_OF_GLORY6)){
                        if(Rs2Inventory.interact(ItemID.AMULET_OF_GLORY6, "Wear")){
                            sleepUntil(()-> Rs2Equipment.get(EquipmentInventorySlot.AMULET).getId() == ItemID.AMULET_OF_GLORY6, generateRandomNumber(5000,15000));
                        }
                    }
                    if(Rs2Inventory.contains(ItemID.AMULET_OF_GLORY)){
                        Rs2Bank.depositOne("Amulet of glory", true);
                        sleepUntil(()-> !Rs2Inventory.contains(ItemID.AMULET_OF_GLORY), generateRandomNumber(5000,15000));
                    }
                }
            }
            howtobank = generateRandomNumber(0,100);
            if(howtobank <= 40){
                if(Rs2Equipment.get(EquipmentInventorySlot.RING)!=null){
                    // we have our ring do nothing
                } else {
                    Microbot.log("Getting the ring of dueling");
                    if(Rs2Bank.count(ItemID.RING_OF_DUELING8)>0){
                        if(!Rs2Inventory.contains(ItemID.RING_OF_DUELING8)){
                            if(Rs2Bank.withdrawX(ItemID.RING_OF_DUELING8, 1)){
                                sleepUntil(()-> Rs2Inventory.contains(ItemID.RING_OF_DUELING8), generateRandomNumber(5000,15000));
                            }
                        }
                    } else {
                        Microbot.log("Out of rings of dueling");
                        super.shutdown();
                    }
                    if(Rs2Inventory.contains(ItemID.RING_OF_DUELING8)){
                        if(Rs2Inventory.interact(ItemID.RING_OF_DUELING8, "Wear")){
                            sleepUntil(()-> Rs2Equipment.get(EquipmentInventorySlot.RING).getName().contains("dueling"), generateRandomNumber(5000,15000));
                        }
                    }
                }
            }
            howtobank = generateRandomNumber(0,100);
            //bracelet of eth
            if(howtobank <= 40){
                if(Rs2Equipment.get(EquipmentInventorySlot.GLOVES)!=null){
                    if(Rs2Equipment.get(EquipmentInventorySlot.GLOVES).getName().contains("ethereum")){
                        if(Rs2Equipment.get(EquipmentInventorySlot.GLOVES).getId() == ItemID.BRACELET_OF_ETHEREUM_UNCHARGED || ItemWithCharge.findItem(ItemID.BRACELET_OF_ETHEREUM).getCharges() < 100){
                            Microbot.log("We need to charge our bracelet");
                            if(Rs2Bank.hasItem("Revenant ether") && Rs2Bank.count("Revenant ether") > 100){
                                if(!Rs2Inventory.contains("Revenant ether")){
                                    if(Rs2Bank.withdrawX("Revenant ether", Rs2Random.between(100,300))){
                                        sleepUntil(()-> Rs2Inventory.contains("Revenant ether"), Rs2Random.between(2000,4000));
                                    }
                                }
                            } else {
                                Microbot.log("We're out of ether. Stopping.");
                                super.shutdown();
                            }
                            if(Rs2Inventory.contains("Revenant ether")){
                                if(Rs2Bank.isOpen()){
                                    Rs2Bank.closeBank();
                                    sleepUntil(()-> !Rs2Bank.isOpen(), Rs2Random.between(2000,4000));
                                }
                                if(!Rs2Bank.isOpen()){
                                    if(Rs2Equipment.get(EquipmentInventorySlot.GLOVES)!=null){
                                        //we need to unequip our braclet.
                                        Rs2Equipment.unEquip(EquipmentInventorySlot.GLOVES);
                                        sleepUntil(()-> !Rs2Equipment.hasEquippedSlot(EquipmentInventorySlot.GLOVES), Rs2Random.between(2000,4000));
                                    }
                                    if(Rs2Inventory.contains("Revenant ether") && (Rs2Inventory.contains(ItemID.BRACELET_OF_ETHEREUM) || Rs2Inventory.contains(ItemID.BRACELET_OF_ETHEREUM_UNCHARGED))){
                                        Rs2Inventory.interact("Revenant ether", "use");
                                        Rs2Inventory.interact(it->it!=null&&it.getName().contains("ethereum"), "use");
                                        sleepUntil(()-> !Rs2Inventory.contains("Revenant ether"), Rs2Random.between(2000,4000));
                                    }
                                    if(!Rs2Inventory.contains("Revenant ether") && (Rs2Inventory.contains(ItemID.BRACELET_OF_ETHEREUM))){
                                        Rs2Inventory.interact(it->it!=null&&it.getName().contains("ethereum"), "Wear");
                                        sleepUntil(()-> Rs2Equipment.hasEquippedSlot(EquipmentInventorySlot.GLOVES), Rs2Random.between(2000,4000));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            howtobank = generateRandomNumber(0,100);
            //equip arrows
            if(howtobank <= 40){
                Microbot.log("We have "+Rs2Equipment.get(EquipmentInventorySlot.AMMO).getQuantity()+" arrows left");
                if(Rs2Equipment.get(EquipmentInventorySlot.AMMO).getQuantity() < LowOnArrowsCount){
                    if(Rs2Bank.count(selectedArrow)>100){
                        if(!Rs2Inventory.contains(selectedArrow)||Rs2Inventory.get(selectedArrow).getQuantity() < LowOnArrowsCount){
                            if(Rs2Bank.withdrawX(selectedArrow, (generateRandomNumber(120,300)-Rs2Equipment.get(EquipmentInventorySlot.AMMO).getQuantity()) )){
                                sleepUntil(()-> Rs2Inventory.contains(selectedArrow), generateRandomNumber(5000,15000));
                            }
                        }
                    } else {
                        Microbot.log("Out of arrows");
                        super.shutdown();
                    }
                    if(Rs2Inventory.contains(selectedArrow)){
                        if(Rs2Inventory.interact(selectedArrow, "Wield")){
                            sleepUntil(()-> Rs2Equipment.get(EquipmentInventorySlot.AMMO).getQuantity() > 50, generateRandomNumber(5000,15000));
                        }
                    }
                }
            }
            howtobank = generateRandomNumber(0,100);
            //get stamina pot
            if(howtobank <= 40){
                Microbot.log("Withdrawing Stamina potion");
                if(!Rs2Inventory.contains(it->it!=null&&it.getName().contains("Stamina"))){
                    if(Rs2Bank.count("Stamina potion(4)") > 0){
                        if(!Rs2Inventory.contains(it->it!=null&&it.getName().contains("Stamina"))){
                            Rs2Bank.withdrawOne("Stamina potion(4)");
                            sleepUntil(()-> Rs2Inventory.contains(it->it!=null&&it.getName().contains("Stamina")), generateRandomNumber(5000,15000));
                        }
                    } else {
                        Microbot.log("Out of stamina potions");
                        super.shutdown();
                    }
                }
            }
            howtobank = generateRandomNumber(0,100);

            if(howtobank <= 40){
                Microbot.log("Withdrawing Ranging potion");
                if(!Rs2Inventory.contains(it->it!=null&&it.getName().contains("Ranging"))){
                    if(Rs2Bank.count("Ranging potion(4)") > 0){
                        if(!Rs2Inventory.contains(it->it!=null&&it.getName().contains("Ranging"))){
                            Rs2Bank.withdrawX("Ranging potion(4)", 2);
                            sleepUntil(()-> Rs2Inventory.contains(it->it!=null&&it.getName().contains("Ranging")), generateRandomNumber(5000,15000));
                        }
                    } else {
                        Microbot.log("Out of ranging potions");
                        super.shutdown();
                    }
                }
            }
            howtobank = generateRandomNumber(0,100);

            if(howtobank <= 40){
                Microbot.log("Withdrawing Sharks");
                if(Rs2Inventory.count("Shark") < 10){
                    if(Rs2Bank.count("Shark") > 9){
                        if(!Rs2Inventory.contains("Shark")||Rs2Inventory.count("Shark") < 10){
                            if(Rs2Bank.withdrawX("Shark",(10-Rs2Inventory.count("Shark")))) {
                                sleepUntil(() -> Rs2Inventory.count("Shark") >= 10, generateRandomNumber(5000, 15000));
                            }
                        }
                    } else {
                        Microbot.log("Out of sharks");
                        super.shutdown();
                    }
                }
            }
            howtobank = generateRandomNumber(0,100);

        }
    }
    public int generateRandomNumber(int min, int max) {
        return Rs2Random.nextInt(min, max, 1000, true);
    }
    public boolean isItTimeToGo(){
        int value = 0; //set to 0 so list doesn't compound with each run
        List<Rs2ItemModel> ItemsInInventory = new ArrayList<Rs2ItemModel>();
        ItemsInInventory.addAll(Rs2Inventory.items());

        for (Rs2ItemModel item : ItemsInInventory) {
            if(item!=null){
                value+=item.getPrice();
            }
        }

        if(value>=config.leaveAtValue()){
            Microbot.log("We have enough loot");
            return true;
        }
        if(Rs2Inventory.isFull()){
            Microbot.log("We have enough loot");
            return true;
        }
        if(timeToBreak()){
            Microbot.log("It's time to break");
            return true;
        }
        Microbot.log("We have "+value+" worth of loot");
        return false;
    }

    public boolean areWeEquipped() {

    if (Rs2Equipment.get(EquipmentInventorySlot.AMMO) == null) {
        Microbot.log("We have no ammo!");
        return false;
    }

    if (Rs2Equipment.get(EquipmentInventorySlot.AMMO).getQuantity() < LowOnArrowsCount) {
        Microbot.log("We don't have enough ammo!");
        return false;
    }

    if (!Rs2Inventory.contains(it -> it != null && it.getName().contains("Stamina"))) {
        Microbot.log("We have no stam!");
        return false;
    }

    if (!Rs2Inventory.contains(it -> it != null && it.getName().contains("Ranging"))) {
        Microbot.log("We have no Ranging potion!");
        return false;
    }

    if (!Rs2Inventory.contains("Shark")) {
        Microbot.log("We're out of sharks!");
        return false;
    }

    if (Rs2Inventory.count("Shark") < 2) {
        Microbot.log("We have less than 2 sharks!");
        return false;
    }

    if (Rs2Equipment.get(EquipmentInventorySlot.RING) == null) {
        Microbot.log("ring is null");
        return false;
    }

    if (!Rs2Equipment.get(EquipmentInventorySlot.RING).getName().contains("dueling")) {
        Microbot.log("We don't have our ring of dueling");
        return false;
    }

    if (isItTimeToGo()) {
        if(timeToBreak()){
            Microbot.log("It's time to break");
            return false;
        }
        Microbot.log("We have too much loot! Banking");
        return false;
    }

    if (Rs2Equipment.get(EquipmentInventorySlot.AMULET) == null) {
        Microbot.log("amulet is null");
        return false;
    }

    if (!Rs2Equipment.get(EquipmentInventorySlot.AMULET).getName().contains("Amulet of glory(")) {
        Microbot.log("amulet is not charged");
        return false;
    }

    Microbot.log("We're fully equipped and ready to go.");
    return true;

    }


    public boolean isPkerAround(){

        List<Rs2PlayerModel> playerlist = new ArrayList<Rs2PlayerModel>();
        playerlist.addAll(Rs2Player.getPlayersInCombatLevelRange());
        List<String> weapons = Arrays.asList(
                "staff", "shadow", "wand", "sceptre", "ballista",
                "crossbow", "dragon dagger", "dragon claws", "eclipse atlatl", "dark bow"
        );

        for (Rs2PlayerModel player : playerlist) {
            Microbot.log("There may be a pker around us "+player.getName());

            String NameOfPlayersWeapon = Rs2Player.getPlayerEquipmentNames(player).get(KitType.WEAPON);

            if(NameOfPlayersWeapon == null){
                continue;
            }

            String lowercaseWeapon = NameOfPlayersWeapon.toLowerCase();

            Microbot.log("They have a "+NameOfPlayersWeapon);

            for (String weapon : weapons) {
                if (lowercaseWeapon.contains(weapon) || lowercaseWeapon.equals(weapon)) {
                    Microbot.log("This player is wielding a " + NameOfPlayersWeapon + " which is used to pk so we're outy.");
                    return true;
                }
            }

            if ((player.getInteracting() != null) && (player.getInteracting().equals(Rs2Player.getLocalPlayer()))) {
                Microbot.log(player.getName() + " is attacking us");
                return true;
            }

            if((player.getInteracting() != null && player.getInteracting().getName() != null) && player.getInteracting().getName().equals(Rs2Player.getLocalPlayer().getName())){
                Microbot.log(player.getName() + " is attacking us");
                return true;
            }

        }
        return false;
    }
}