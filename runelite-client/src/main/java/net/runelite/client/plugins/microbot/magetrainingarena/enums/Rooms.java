package net.runelite.client.plugins.microbot.magetrainingarena.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import org.slf4j.event.Level;

import java.util.function.BooleanSupplier;

import static net.runelite.client.plugins.microbot.magetrainingarena.MageTrainingArenaScript.tryEquipBestStaffAndCast;

@Getter
@AllArgsConstructor
public enum Rooms {
    TELEKINETIC("Telekinetic", 23673, null, null, Points.TELEKINETIC,
            () -> {
                boolean result = tryEquipBestStaffAndCast(Rs2Spells.TELEKINETIC_GRAB, Rs2Inventory.hasRunePouch());
                Microbot.log("TELEKINETIC req met: " + result, Level.DEBUG);
                return result;
            }),

    ALCHEMIST("Alchemist", 23675,
            new WorldArea(3345, 9616, 38, 38, 2),
            new WorldPoint(3364, 9623, 2),
            Points.ALCHEMIST,
            () -> {
                boolean high = tryEquipBestStaffAndCast(Rs2Spells.HIGH_LEVEL_ALCHEMY, Rs2Inventory.hasRunePouch());
                boolean low = tryEquipBestStaffAndCast(Rs2Spells.LOW_LEVEL_ALCHEMY, Rs2Inventory.hasRunePouch());
                Microbot.log("ALCHEMIST req met: HIGH=" + high + " LOW=" + low, Level.DEBUG);
                return high || low;
            }),

    ENCHANTMENT("Enchantment", 23674,
            new WorldArea(3339, 9617, 50, 46, 0),
            new WorldPoint(3363, 9640, 0),
            Points.ENCHANTMENT,
            () -> {
                int level = Microbot.getClient().getBoostedSkillLevel(Skill.MAGIC);
                boolean result;

                if (level >= 87) {
                    result = tryEquipBestStaffAndCast(Rs2Spells.ENCHANT_ONYX_JEWELLERY, Rs2Inventory.hasRunePouch());
                } else if (level >= 68) {
                    result = tryEquipBestStaffAndCast(Rs2Spells.ENCHANT_DRAGONSTONE_JEWELLERY, Rs2Inventory.hasRunePouch());
                } else if (level >= 57) {
                    result = tryEquipBestStaffAndCast(Rs2Spells.ENCHANT_DIAMOND_JEWELLERY, Rs2Inventory.hasRunePouch());
                } else if (level >= 49) {
                    result = tryEquipBestStaffAndCast(Rs2Spells.ENCHANT_RUBY_JEWELLERY, Rs2Inventory.hasRunePouch());
                } else if (level >= 27) {
                    result = tryEquipBestStaffAndCast(Rs2Spells.ENCHANT_EMERALD_JEWELLERY, Rs2Inventory.hasRunePouch());
                } else {
                    result = tryEquipBestStaffAndCast(Rs2Spells.ENCHANT_SAPPHIRE_JEWELLERY, Rs2Inventory.hasRunePouch());
                }

                Microbot.log("ENCHANTMENT req met (level=" + level + "): " + result, Level.DEBUG);
                return result;
            }),

    GRAVEYARD("Graveyard", 23676,
            new WorldArea(3336, 9614, 54, 51, 1),
            new WorldPoint(3363, 9640, 1),
            Points.GRAVEYARD,
            () -> {
                boolean bananas = tryEquipBestStaffAndCast(Rs2Spells.BONES_TO_BANANAS, Rs2Inventory.hasRunePouch());
                boolean peaches = tryEquipBestStaffAndCast(Rs2Spells.BONES_TO_PEACHES, Rs2Inventory.hasRunePouch());

                Microbot.log("GRAVEYARD req met: BANANAS=" + bananas + " PEACHES=" + peaches, Level.DEBUG);
                if (!bananas && !peaches) {
                    Microbot.log("Missing requirement to cast Bones to Bananas or Peaches.", Level.DEBUG);
                    return false;
                }
                return true;
            });

    private final String name;
    private final int teleporter;
    private final WorldArea area;
    private final WorldPoint exit;
    private final Points points;
    private final BooleanSupplier requirements;

    @Override
    public String toString() {
        String n = name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }
}
