package net.runelite.client.plugins.microbot.LunarTablets;

import com.google.inject.Provides;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Timer;
import java.util.concurrent.TimeUnit;


public class LunarTabletsScript extends Script {
    private LunarTabletsConfig config;
    @Provides
    LunarTabletsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(LunarTabletsConfig.class);
    }
    public static boolean test = false;
    public boolean run(LunarTabletsConfig config) {
        this.config = config;
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
               if (!Microbot.isLoggedIn()) return;
               if (!super.run()) return;
               long startTime = System.currentTimeMillis();

                if(!Rs2Inventory.contains("Astral rune")||!Rs2Inventory.contains("Law rune")||!Rs2Inventory.contains("Soft clay")) {
                    handleBanking();
                }

                if(Rs2Inventory.contains("Astral rune")&&Rs2Inventory.contains("Law rune")&&Rs2Inventory.contains("Soft clay")) {
                    walkToLecturn();
                }

                if(Rs2Inventory.contains("Astral rune")&&Rs2Inventory.contains("Law rune")&&Rs2Inventory.contains("Soft clay")) {
                    makeTablets();
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
    public int generateRandomNumber(int min, int max) {
        return Rs2Random.nextInt(min, max, 1000, true);
    }
    public void makeTablets(){
        WorldPoint lecturn =(new WorldPoint(2078, 3914, 0));
        WorldPoint player = Rs2Player.getWorldLocation();
        if(player.distanceTo(lecturn)<=4){
            if(Rs2Widget.getWidget(26411026)!=null){
                String selectedTabletName = config.selectedTablet().getName();
                if(selectedTabletName.equals("Moonclan Teleport")){
                    if(Rs2Widget.clickWidget(26411027)){
                        sleep(generateRandomNumber(0,1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                if(selectedTabletName.equals("Ourania Teleport")){
                    if(Rs2Widget.clickWidget(26411028)) {
                        sleep(generateRandomNumber(0, 1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                if(selectedTabletName.equals("Waterbirth Teleport")){
                    if(Rs2Widget.clickWidget(26411029)) {
                        sleep(generateRandomNumber(0, 1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                if(selectedTabletName.equals("Barbarian Teleport")){
                    if(Rs2Widget.clickWidget(26411030)) {
                        sleep(generateRandomNumber(0, 1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                if(selectedTabletName.equals("Khazard Teleport")){
                    if(Rs2Widget.clickWidget(26411031)) {
                        sleep(generateRandomNumber(0, 1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                if(selectedTabletName.equals("Fishing Guild Teleport")){
                    if(Rs2Widget.clickWidget(26411032)) {
                        sleep(generateRandomNumber(0, 1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                if(selectedTabletName.equals("Catherby Teleport")){
                    if(Rs2Widget.clickWidget(26411033)) {
                        sleep(generateRandomNumber(0, 1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                if(selectedTabletName.equals("Ice Plateau Teleport")){
                    if(Rs2Widget.clickWidget(26411034)) {
                        sleep(generateRandomNumber(0, 1000));
                        Rs2Widget.clickWidget(26411022);
                    }
                }
                boolean stillmaking = true;
                while(stillmaking){
                    int pre = 0;
                    if(Rs2Inventory.contains("Soft clay")){
                        pre = Rs2Inventory.count("Soft clay");
                    }
                    sleep(generateRandomNumber(3000,6000));
                    int post = 0;
                    if(Rs2Inventory.contains("Soft clay")){
                        post = Rs2Inventory.count("Soft clay");
                    }
                    if(!Rs2Inventory.contains("Soft clay")||pre==post){
                        stillmaking = false;
                        break;
                    }
                }
            } else {
                // interact with lecturn
                if(Rs2GameObject.interact("Lectern", "Study")){
                    sleep(generateRandomNumber(0,1000));
                }
            }
        }
    }
    public void walkToLecturn(){
        WorldPoint lecturn =(new WorldPoint(2078, 3914, 0));
        WorldPoint player = Rs2Player.getWorldLocation();
        if(player.distanceTo(lecturn)>4){
            Rs2Walker.walkTo(new WorldPoint(2078, 3914, 0));
        }
    }
    public void handleBanking() {
        String selectedTabletName = config.selectedTablet().getName();
        System.out.println("Selected Lunar Tablet: " + selectedTabletName);

        if (!Rs2Bank.isOpen()){
            Rs2Bank.walkToBankAndUseBank(BankLocation.LUNAR_ISLE);
            return;
        }
        System.out.println("Bank opened successfully.");

        if (Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("teleport"))) {
            int howToBank = generateRandomNumber(0, 100);
            int howToDeposit = generateRandomNumber(80, 100);
            while (Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("teleport"))){
                if (howToBank <= howToDeposit) {
                    System.out.println("Depositing all teleport items.");
                    Rs2Bank.depositAll(item -> item.getName().toLowerCase().contains("teleport"));
                    sleepUntil(() -> !Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("teleport")), 3500);
                } else {
                    System.out.println("Depositing all items.");
                    Rs2Bank.depositAll();
                    sleepUntil(() -> !Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("teleport")), 3500);
                }
        }
        }
        int BankClayCount = Rs2Bank.count("Soft clay"); int InvClayCount = Rs2Inventory.count("Soft clay");
        int BankLawCount = Rs2Bank.count("Law rune");  int InvLawCount = Rs2Inventory.count("Law rune");
        int BankAstralCount = Rs2Bank.count("Astral rune");   int InvAstralCount = Rs2Inventory.count("Astral rune");
            if(BankClayCount<2&&BankLawCount<2&&BankAstralCount<2&&InvClayCount<2&&InvLawCount<2&&InvAstralCount<2) {
                System.out.println("We're out of items.");
                shutdown();
            } else {
                System.out.println("Withdrawing what we need.");
                int howToBank2 = generateRandomNumber(0, 100);
                int howToWithdraw = generateRandomNumber(80, 100);
                    if(!Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("teleport"))){
                        if (!Rs2Inventory.contains("Soft clay") || !Rs2Inventory.contains("Astral rune") || !Rs2Inventory.contains("Law rune")) {
                            while (!Rs2Inventory.contains("Soft clay") || !Rs2Inventory.contains("Astral rune") || !Rs2Inventory.contains("Law rune")) {
                                if (!Rs2Inventory.contains("Law rune")) {
                                    if (howToWithdraw > howToBank2) {
                                    Rs2Bank.withdrawAll("Law rune");
                                    sleepUntil(() -> Rs2Inventory.contains("Law rune"), 3500);
                                    }
                                }
                                if (!Rs2Inventory.contains("Astral rune")) {
                                    if (howToWithdraw > howToBank2) {
                                    Rs2Bank.withdrawAll("Astral rune");
                                    sleepUntil(() -> Rs2Inventory.contains("Astral rune"), 3500);
                                    }
                                }
                                if(Rs2Inventory.contains("Astral rune")&&Rs2Inventory.contains("Law rune")&&!Rs2Inventory.contains("Soft clay")) {
                                Rs2Bank.withdrawAll("Soft clay");
                                sleepUntil(() -> Rs2Inventory.contains("Soft clay"), 3500);
                                }
                            }
                        }
                    }
            }
    }
}