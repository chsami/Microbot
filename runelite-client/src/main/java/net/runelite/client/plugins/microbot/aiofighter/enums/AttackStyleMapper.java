package net.runelite.client.plugins.microbot.aiofighter.enums;

/**
 * This class provides a utility method for mapping a string representation of an attack style to an AttackStyle enum.
 */
public class AttackStyleMapper {
    /**
     * Maps a string representation of an attack style to an AttackStyle enum.
     * The mapping is case-insensitive and can handle multiple attack styles separated by a comma.
     * @param style The string representation of the attack style.
     * @return The corresponding AttackStyle enum.
     */
    public static AttackStyle mapToAttackStyle(String style) {
        // Convert style to lowercase for case-insensitive matching
        String lowerCaseStyle = style.toLowerCase();

        // Check if the style contains multiple attack styles separated by a comma
        if (lowerCaseStyle.contains(",")) {
            return AttackStyle.MIXED;
        }

        // Check for presence of melee sub-styles
        boolean hasMelee = lowerCaseStyle.contains("melee") ||
                lowerCaseStyle.contains("crush") ||
                lowerCaseStyle.contains("slash") ||
                lowerCaseStyle.contains("stab");

        // Check for presence of magic-related styles
        boolean hasMagic = lowerCaseStyle.contains("magic");

        // Check for presence of ranged-related styles
        boolean hasRanged = lowerCaseStyle.contains("ranged");

        // Determine the appropriate AttackStyle based on the presence of different styles
        if (hasMelee && hasMagic && hasRanged) {
            return AttackStyle.MIXED;
        } else if ((hasMelee && hasMagic) || (hasMelee && hasRanged) || (hasMagic && hasRanged)) {
            return AttackStyle.MIXED;
        } else if (hasMelee) {
            return AttackStyle.MELEE;
        } else if (hasMagic) {
            return AttackStyle.MAGE;
        } else if (hasRanged) {
            return AttackStyle.RANGED;
        } else {
            return AttackStyle.MIXED;  // Default case if style does not match any category.
        }
    }
}