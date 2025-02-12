package net.runelite.client.plugins.microbot.magetrainingarena.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.function.BooleanSupplier;

@Getter
@AllArgsConstructor
public enum Rooms {
    TELEKINETIC("Telekinetic", 23673, null, null, ItemID.LAW_RUNE, Points.TELEKINETIC,
            () -> Rs2Magic.canCast(MagicAction.TELEKINETIC_GRAB)),
    ALCHEMIST("Alchemist", 23675, new WorldArea(3345, 9616, 38, 38, 2), new WorldPoint(3364, 9623, 2), ItemID.NATURE_RUNE, Points.ALCHEMIST,
            () -> Rs2Magic.canCast(MagicAction.HIGH_LEVEL_ALCHEMY) || Rs2Magic.canCast(MagicAction.LOW_LEVEL_ALCHEMY)),
    ENCHANTMENT("Enchantment", 23674, new WorldArea(3339, 9617, 50, 46, 0), new WorldPoint(3363, 9640, 0), ItemID.COSMIC_RUNE, Points.ENCHANTMENT,
            () -> {
                var magicLevel = Microbot.getClient().getBoostedSkillLevel(Skill.MAGIC);
                MagicAction enchant;
                if (magicLevel >= 87 && Rs2Inventory.hasItem("lava") || Rs2Equipment.isWearing("lava")) {
                    enchant = MagicAction.ENCHANT_ONYX_JEWELLERY;
                } else if (magicLevel >= 68) {
                    enchant = MagicAction.ENCHANT_DRAGONSTONE_JEWELLERY;
                } else if (magicLevel >= 57) {
                    enchant = MagicAction.ENCHANT_DIAMOND_JEWELLERY;
                } else if (magicLevel >= 49) {
                    enchant = MagicAction.ENCHANT_RUBY_JEWELLERY;
                } else if (magicLevel >= 27) {
                    enchant = MagicAction.ENCHANT_EMERALD_JEWELLERY;
                } else {
                    enchant = MagicAction.ENCHANT_SAPPHIRE_JEWELLERY;
                }

                if (!Rs2Magic.canCast(enchant)) {
                    Microbot.log("Your missing runes/staff for following spell: " + enchant.getName());
                    return false;
                }
                return true;
            }),
    GRAVEYARD("Graveyard", 23676, new WorldArea(3336, 9614, 54, 51, 1), new WorldPoint(3363, 9640, 1), ItemID.NATURE_RUNE, Points.GRAVEYARD,
            () -> {
                boolean btp = Rs2Magic.canCast(MagicAction.BONES_TO_PEACHES);
                if (!Rs2Magic.canCast(MagicAction.BONES_TO_BANANAS) && !btp) {
                    Microbot.log("Missing requirement to cast " + (btp ? MagicAction.BONES_TO_PEACHES : MagicAction.BONES_TO_BANANAS));
                    return false;
                }
                return true;
            });

    private final String name;
    private final int teleporter;
    private final WorldArea area;
    private final WorldPoint exit;
    private final int runesId;
    private final Points points;
    private final BooleanSupplier requirements;

    @Override
    public String toString() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
