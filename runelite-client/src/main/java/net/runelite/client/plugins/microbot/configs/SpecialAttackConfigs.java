package net.runelite.client.plugins.microbot.configs;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.nmz.NmzScript;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.misc.SpecialAttackWeaponEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public class SpecialAttackConfigs {

    @Setter
    private static List<Rs2ItemModel> currentEquipment = null;

    @Getter
    private boolean useSpecialAttack = false;

    @Getter
    private SpecialAttackWeaponEnum specialAttackWeapon = null;

    @Getter
    private int minimumSpecEnergy = -1;

    public SpecialAttackConfigs setSpecialAttack(boolean useSpecialAttack) {
        this.useSpecialAttack = useSpecialAttack;
        return this;
    }

    public SpecialAttackConfigs setSpecialAttackWeapon(SpecialAttackWeaponEnum specialAttackWeapon) {
        this.specialAttackWeapon = specialAttackWeapon;
        return this;
    }

    public SpecialAttackConfigs setMinimumSpecEnergy(int minimumSpecEnergy) {
        this.minimumSpecEnergy = minimumSpecEnergy;
        return this;
    }

    public void reset() {
        setCurrentEquipment(null);
        this.setMinimumSpecEnergy(-1);
        this.setSpecialAttackWeapon(null);
        this.setSpecialAttack(false);
    }

    public boolean useSpecWeapon() {
        if (!Microbot.isLoggedIn()) return false;
        if (!Rs2Combat.inCombat()) return false;
        if (!Microbot.getSpecialAttackConfigs().isUseSpecialAttack()) return false;
        if (Rs2Combat.getSpecEnergy() < Microbot.getSpecialAttackConfigs().getMinimumSpecEnergy() && currentEquipment == null) return false;

        //check spec weapon in inventory
        for (SpecialAttackWeaponEnum specialAttackWeapon :SpecialAttackWeaponEnum.values()) {
            boolean hasPresetSpecialAttackWeapon = Rs2Inventory.hasItem(specialAttackWeapon.getName())
                    && Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon() != null
                    && Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon().getName().toLowerCase().contains(specialAttackWeapon.getName());
            boolean hasRandomSpecialAttackWeapon = Rs2Inventory.hasItem(specialAttackWeapon.getName()) && Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon() == null;
            if (hasPresetSpecialAttackWeapon || hasRandomSpecialAttackWeapon) {
                if (useSpecWeapon(specialAttackWeapon.getName(), specialAttackWeapon.getEnergyRequired(), specialAttackWeapon.is2H()))
                    return true;
            }
        }
        //check spec weapon in equipment slot
        for (SpecialAttackWeaponEnum specialAttackWeapon :SpecialAttackWeaponEnum.values()) {
            boolean hasPresetSpecialAttackWeapon = Rs2Equipment.isWearing(specialAttackWeapon.getName())
                    && Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon() != null
                    && Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon().getName().toLowerCase().contains(specialAttackWeapon.getName());
            boolean hasRandomSpecialAttackWeapon = Rs2Equipment.isWearing(specialAttackWeapon.getName()) && Microbot.getSpecialAttackConfigs().getSpecialAttackWeapon() == null;
            if (hasPresetSpecialAttackWeapon || hasRandomSpecialAttackWeapon) {
                if (useSpecWeapon(specialAttackWeapon.getName(), specialAttackWeapon.getEnergyRequired(), specialAttackWeapon.is2H()))
                    return true;
            }
        }
        return false;
    }

    public boolean useSpecWeapon(String name, int specEnergy, boolean is2H) {
        if (name.isEmpty()) return false;
        if (Rs2Equipment.isWearingShield() && is2H && Rs2Inventory.isFull()) return false;

        if (currentEquipment == null) {
            currentEquipment = new ArrayList<>();
            currentEquipment.addAll(Rs2Equipment.equipmentItems);
        }

        if (Rs2Combat.getSpecEnergy() < specEnergy && !NmzScript.isHasSurge()) {
            Rs2ItemModel rs2Item = currentEquipment.stream().filter(x -> x.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx()).findFirst().orElse(null);
            if (rs2Item != null && rs2Item.id != Rs2Equipment.get(EquipmentInventorySlot.WEAPON).id) {
                Rs2ItemModel weapon = currentEquipment
                        .stream()
                        .filter(x -> x.getSlot() == EquipmentInventorySlot.WEAPON.getSlotIdx())
                        .findFirst()
                        .orElse(null);
                Rs2ItemModel shield = currentEquipment
                        .stream()
                        .filter(x -> x.getSlot() == EquipmentInventorySlot.SHIELD.getSlotIdx())
                        .findFirst()
                        .orElse(null);

                if (shield != null) {
                    Rs2Inventory.wear(shield.name);
                }
                if (weapon != null) {
                    Rs2Inventory.wear(Objects.requireNonNull(weapon).name);
                }
            }
            return false;
        }

        boolean didInteract = Rs2Inventory.wear(name);
        if (didInteract) sleep(600);
        return Rs2Combat.setSpecState(true, specEnergy);
    }
}
