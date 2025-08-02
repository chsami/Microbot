package net.runelite.client.plugins.microbot.aiofighter.enums;

import lombok.extern.slf4j.Slf4j;

@Slf4j
/**
 * This class provides a utility method for mapping a string representation of an attack style to an AttackStyle enum.
 * If the style string contains a comma, it will split it into multiple attack styles and only prioritize the first one.
 */
public class AttackStyleMapper {

    /**
     * Maps a string representation of an attack style to an AttackStyle enum.
     * The mapping is case-insensitive. If multiple attack styles are provided (separated by commas),
     * only the first attack style is considered.
     *
     * @param style The string representation of the attack style.
     * @return The corresponding AttackStyle enum.
     * @throws IllegalArgumentException if the provided style does not match any recognized attack style.
     */
    public static AttackStyle mapToAttackStyle(String style) {
        if (style == null || style.isEmpty()) {
            log.error("Attack style is null or empty, defaulting to MELEE.");
            return AttackStyle.MELEE; // Default to MELEE if no style is provided.
        }

        // Convert to lowercase for case-insensitive matching.
        String lowerCaseStyle = style.toLowerCase();

        // If multiple attack styles are provided, only consider the first one.
        if (lowerCaseStyle.contains(",")) {
            String[] styles = lowerCaseStyle.split(",");
            lowerCaseStyle = styles[0].trim();
        }

        // Determine the attack style based on keywords.
        if (lowerCaseStyle.contains("melee") || lowerCaseStyle.contains("crush") ||
                lowerCaseStyle.contains("slash") || lowerCaseStyle.contains("stab")) {
            return AttackStyle.MELEE;
        } else if (lowerCaseStyle.contains("magic")) {
            return AttackStyle.MAGE;
        } else if (lowerCaseStyle.contains("ranged")) {
            return AttackStyle.RANGED;
        } else {
            log.error("Unrecognized attack style: " + style);
            return AttackStyle.MELEE; // Default to MELEE if unrecognized.
        }
    }
}
