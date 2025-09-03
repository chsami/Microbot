package net.runelite.client.plugins.microbot.util.magic.reanimate;

import lombok.Getter;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.util.Arrays;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.interact;

/**
 * An enumeration representing different types of ensouled heads along with the reanimation spells
 * and corresponding names associated with each type. This enumeration categorizes Ensouled heads into
 * tiers based on their spell requirements and names.
 * <p>
 * Each tier specifies a spell from the `Rs2Spells` collection to be used for reanimation and
 * a set of substring names used to identify the matching item models.
 * <p>
 * Features:
 * - Maps tiers (BASIC, ADEPT, EXPERT, MASTER) to the `Rs2Spells` spell.
 * - Provides utility to identify and retrieve head items fitting the defined categories
 * using name substring matching.
 */
public enum HeadType {
    BASIC(Rs2Spells.BASIC_REANIMATION, "goblin", "monkey", "imp", "minotaur", "scorpion", "bear", "unicorn"),
    ADEPT(Rs2Spells.ADEPT_REANIMATION, "dog", "chaos", "ogre", "giant", "elf", "troll", "horror"),
    EXPERT(Rs2Spells.EXPERT_REANIMATION, "kalphite", "dagannoth", "bloodveld", "tzhaar", "demon", "hellhound"),
    MASTER(Rs2Spells.MASTER_REANIMATION, "aviansie", "abyssal", "dragon");

    @Getter
    private final Rs2Spells spell;
    private final String[] names;

    HeadType(Rs2Spells spell, String... names) {
        this.spell = spell;
        this.names = names;
    }

    public boolean reanimate(Rs2ItemModel head) {
        if (!isEnsouled(head)) return false;
        if (!Rs2Magic.cast(spell)) {
            return false;
        }
        sleepUntil(() -> Microbot.getClientThread().runOnClientThreadOptional(() -> Rs2Tab.getCurrentTab() == InterfaceTab.INVENTORY).orElse(false), 5000);
        return interact(head, "Reanimate");
    }

    public Rs2ItemModel getHead() {
        return Rs2Inventory.get(this::canReanimate);
    }

    public boolean canReanimate(Rs2ItemModel head) {
        if (head == null) return false;
        return canReanimate(head.getName());
    }

    public boolean canReanimate(String headName) {
        if (headName == null) return false;
        String lower = headName.toLowerCase();
        for (String n : names) {
            if (lower.contains(n)) {
                return true;
            }
        }
        return false;
    }

    public static HeadType getHeadType(Rs2ItemModel head) {
        if (!isEnsouled(head)) return null;
        return Arrays.stream(values()).filter(ht -> ht.canReanimate(head.getName())).findFirst().orElse(null);
    }

    public static boolean isEnsouled(Rs2ItemModel head) {
        if (head == null) return false;
        String name = head.getName();
        return name != null && name.contains("Ensouled");
    }
}
