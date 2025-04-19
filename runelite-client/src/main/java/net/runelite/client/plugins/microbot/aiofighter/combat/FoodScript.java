package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment.get;

public class FoodScript extends Script {

    String weaponname = "";
    String bodyName = "";
    String legsName = "";
    String helmName = "";

    String shieldName = "";

    public boolean run(AIOFighterConfig config) {
        weaponname = "";
        bodyName = "";
        legsName = "";
        helmName = "";
        shieldName = "";
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.toggleFood()) return;
                if (Rs2Inventory.hasItem("empty vial"))
                    Rs2Inventory.drop("empty vial");
                double treshHold = (double) (Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100) / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
                if (Rs2Equipment.isWearingFullGuthan()) {
                    if (treshHold > 80) //only unequip guthans if we have more than 80% hp
                        unEquipGuthans();
                    return;
                } else {
                    if (treshHold > 51) //return as long as we have more than 51% health and not guthan equipped
                        return;
                }
                List<Rs2ItemModel> foods = Microbot.getClientThread().runOnClientThreadOptional(Rs2Inventory::getInventoryFood)
                        .orElse(null);
                if (foods == null || foods.isEmpty()) {
                    if (!equipFullGuthans()) {
                        Microbot.showMessage("No more food left & no guthans available. Please teleport");
                        sleep(5000);
                    }
                    return;
                }
                for (Rs2ItemModel food : foods) {
                    Rs2Inventory.interact(food, "eat");
                    sleep(1200, 2000);
                    break;
                }
            } catch(Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void unEquipGuthans() {
        if (Rs2Equipment.hasGuthanWeaponEquiped()  && !weaponname.isEmpty()) {
            Rs2Inventory.equip(weaponname);
            if (shieldName != null)
                Rs2Inventory.equip(shieldName);
        }
        if (Rs2Equipment.hasGuthanBodyEquiped() && !bodyName.isEmpty()) {
            Rs2Inventory.equip(bodyName);
        }
        if (Rs2Equipment.hasGuthanLegsEquiped() && !legsName.isEmpty()) {
            Rs2Inventory.equip(legsName);
        }
        if (Rs2Equipment.hasGuthanHelmEquiped() && !helmName.isEmpty()) {
            Rs2Inventory.equip(helmName);
        }
    }

    private boolean equipFullGuthans() {
        Rs2ItemModel shield = get(EquipmentInventorySlot.SHIELD);
        if (shield != null)
            shieldName = shield.name;

        if (!Rs2Equipment.hasGuthanWeaponEquiped()) {
            Rs2ItemModel spearWidget = Microbot.getClientThread().runOnClientThreadOptional(() ->
                    Rs2Inventory.get("guthan's warspear")).orElse(null);
            if (spearWidget == null) return false;
            Rs2ItemModel weapon = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            weaponname = weapon != null ? weapon.name : "";
            Rs2Inventory.equip(spearWidget.name);
        }
        if (!Rs2Equipment.hasGuthanBodyEquiped()) {
            Rs2ItemModel bodyWidget = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Inventory.get("guthan's platebody")).orElse(null);
            if (bodyWidget == null) return false;
            Rs2ItemModel body = Rs2Equipment.get(EquipmentInventorySlot.BODY);
            bodyName = body != null ? body.name : "";
            Rs2Inventory.equip(bodyWidget.name);
        }
        if (!Rs2Equipment.hasGuthanLegsEquiped()) {
            Rs2ItemModel legsWidget = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Inventory.get("guthan's chainskirt")).orElse(null);
            if (legsWidget == null) return false;
            Rs2ItemModel legs = Rs2Equipment.get(EquipmentInventorySlot.LEGS);
            legsName = legs != null ? legs.name : "";
            Rs2Inventory.equip(legsWidget.name);
        }
        if (!Rs2Equipment.hasGuthanHelmEquiped()) {
            Rs2ItemModel helmWidget = Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Inventory.get("guthan's helm")).orElse(null);
            if (helmWidget == null) return false;
            Rs2ItemModel helm = Rs2Equipment.get(EquipmentInventorySlot.HEAD);
            helmName = helm != null ? helm.name : "";
            Rs2Inventory.equip(helmWidget.name);
        }
        return true;
    }
}
