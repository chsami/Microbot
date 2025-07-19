package net.runelite.client.plugins.microbot.crafting.scripts;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.CraftingConfig;
import net.runelite.client.plugins.microbot.crafting.enums.Staffs;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

class ProgressiveStaffmakingModel {
    @Getter
    @Setter
    private Staffs itemToCraft;
}

public class StaffScript extends Script implements ICraftingScript {

    public static double version = 1.0;
    ProgressiveStaffmakingModel model = new ProgressiveStaffmakingModel();

    String battleStaff = "Battlestaff";
    Staffs itemToCraft;

    @Override
    public String getName() {
        return "Staff Making";
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


    public void run(CraftingConfig config) {
        if (config.staffType() == Staffs.PROGRESSIVE)
            calculateItemToCraft();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            if (config.Afk() && Rs2Random.between(1, 100) == 2)
                sleep(1000, 60000);
            try {

                if (config.staffType() == Staffs.PROGRESSIVE) {
                    itemToCraft = model.getItemToCraft();
                } else {
                    itemToCraft = config.staffType();
                }

                if (Rs2Inventory.hasItem(battleStaff)
                        && Rs2Inventory.hasItem(itemToCraft.getOrb())) {
                    craft(config);
                }
                if (!Rs2Inventory.hasItem(battleStaff)
                        || !Rs2Inventory.hasItem(itemToCraft.getOrb())) {
                    bank(config);
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
    }

    private void bank(CraftingConfig config) {
        Rs2Bank.openBank();
        sleepUntilOnClientThread(Rs2Bank::isOpen);

        Rs2Bank.depositAll(itemToCraft.getItemName());
        sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(itemToCraft.getItemName()));

        Rs2Bank.withdrawDeficit(battleStaff, 14);
        sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(battleStaff));

        verifyItemInBank(itemToCraft.getOrb());

        Rs2Bank.withdrawDeficit(itemToCraft.getOrb(), 14);
        sleepUntilOnClientThread(() -> Rs2Inventory.hasItem(itemToCraft.getOrb()));

        sleep(600, 3000);
        Rs2Bank.closeBank();
    }

    private void verifyItemInBank(String item) {
        if (Rs2Bank.isOpen() && !Rs2Bank.hasItem(item)) {
            Rs2Bank.closeBank();
            Microbot.status = "[Shutting down] - Reason: " + item + " not found in the bank.";
            Microbot.getNotifier().notify(Microbot.status);
            shutdown();
        }
    }

    private void craft(CraftingConfig config) {
        Rs2Inventory.combine(battleStaff, itemToCraft.getOrb());

        sleepUntilOnClientThread(() -> Rs2Widget.getWidget(17694734) != null);

        Rs2Keyboard.keyPress('1');

        sleepUntilOnClientThread(() -> Rs2Widget.getWidget(17694734) == null);

        sleepUntilOnClientThread(() -> !Rs2Inventory.hasItem(itemToCraft.getOrb()), 60000);
    }

    public ProgressiveStaffmakingModel calculateItemToCraft() {
        int craftingLvl = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        if (craftingLvl < Staffs.EARTH_BATTLESTAFF.getLevelRequired()) {
            model.setItemToCraft(Staffs.WATER_BATTLESTAFF);
        } else if (craftingLvl < Staffs.FIRE_BATTLESTAFF.getLevelRequired()) {
            model.setItemToCraft(Staffs.EARTH_BATTLESTAFF);
        } else if (craftingLvl < Staffs.AIR_BATTLESTAFF.getLevelRequired()) {
            model.setItemToCraft(Staffs.FIRE_BATTLESTAFF);
        } else if (craftingLvl < 99) {
            model.setItemToCraft(Staffs.AIR_BATTLESTAFF);
        }
        return model;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
