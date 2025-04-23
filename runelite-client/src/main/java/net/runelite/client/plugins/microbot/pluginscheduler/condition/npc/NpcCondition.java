package net.runelite.client.plugins.microbot.pluginscheduler.condition.npc;

import java.util.regex.Pattern;

import lombok.EqualsAndHashCode;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

/**
 * Abstract base class for all NPC-based conditions.
 */
@EqualsAndHashCode(callSuper = false)
public abstract class NpcCondition implements Condition {
    @Override
    public ConditionType getType() {
        return ConditionType.NPC;
    }

    /**
     * Creates a pattern for matching NPC names
     */
    protected Pattern createNpcNamePattern(String npcName) {
        if (npcName == null || npcName.isEmpty()) {
            return Pattern.compile(".*");
        }
        
        // Check if the name is already a regex pattern
        if (npcName.startsWith("^") || npcName.endsWith("$") || 
            npcName.contains(".*") || npcName.contains("[") || 
            npcName.contains("(")) {
            return Pattern.compile(npcName);
        }
        
        // Otherwise, create a contains pattern
        return Pattern.compile(".*" + Pattern.quote(npcName) + ".*", Pattern.CASE_INSENSITIVE);
    }
}