package net.runelite.client.plugins.microbot.crafting.scripts;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.BoltTips;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.awt.event.KeyEvent;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class GemsScript extends Script implements ICraftingScript {

    public static double version = 1.0;

    @Override
    public String getName() {
        return "Gems";
    }

    @Override
    public String getVersion() {
        return String.valueOf(version);
    }

    @Override
    public String getState() {
        return Microbot.status;
    }

    @Override
    public Map<String, String> getCustomProperties() {
        return Collections.emptyMap();
    }


    public boolean run(CraftingConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!Rs2Player.getSkillRequirement(Skill.CRAFTING, config.gemType().getLevelRequired())) {
                    Microbot.showMessage("Crafting level too low to craft " + config.gemType().getName());
                    shutdown();
                    return;
                }
                final String uncutGemName = "uncut " + config.gemType().getName();
                if (!Rs2Inventory.hasItem(uncutGemName) || !Rs2Inventory.hasItem("chisel")) {
                    Microbot.status = "BANKING";
                    Rs2Bank.openBank();
                    if (Rs2Bank.isOpen()) {
                        Rs2Bank.depositAll("crushed gem");
                        Rs2Bank.depositAll(config.gemType().getName());
                        sleepUntil(() -> !Rs2Inventory.hasItem(config.gemType().getName()) && !Rs2Inventory.hasItem("crushed gem"));
                        if (Rs2Bank.hasItem(uncutGemName)) {
                            Rs2Bank.withdrawItem(true, "chisel");
                            Rs2Bank.withdrawAll(true, uncutGemName);
                        } else {
                            Microbot.showMessage("You've ran out of materials!");
                            shutdown();
                        }
                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                    }
                } else {
                    Microbot.status = "CUTTING GEMS";
                    Rs2Inventory.use("chisel");
                    Rs2Inventory.use(uncutGemName);
                    sleep(600);
                    Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                    sleep(4000);
                    sleepUntil(() -> !Microbot.isGainingExp || !Rs2Inventory.hasItem(uncutGemName), 30000);

                    if (config.fletchIntoBoltTips()) {
                        Microbot.status = "FLETCHING BOLT TIPS";
                        BoltTips boltTip = BoltTips.valueOf(config.gemType().name());
                        if (Rs2Player.getSkillRequirement(Skill.FLETCHING, boltTip.getFletchingLevelRequired()) &&
                                Rs2Inventory.hasItem(config.gemType().getName()) &&
                                Rs2Inventory.hasItem("chisel")) {
                            Rs2Inventory.use("chisel");
                            Rs2Inventory.use(config.gemType().getName());
                            sleep(600);
                            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                            sleep(4000);
                            sleepUntil(() -> !Microbot.isGainingExp || !Rs2Inventory.hasItem(config.gemType().getName()), 30000);
                        }
                    }
                }
                Microbot.status = "IDLE";
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.status = "IDLE";
    }
}