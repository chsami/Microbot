package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

import java.util.Map;

/**
 * Extended LocationOption with resource tracking capabilities.
 * This class is specifically designed for resource-based locations like mining rocks,
 * fishing spots, or woodcutting trees where the number of available resources matters.
 * 
 * The numberOfResources field allows for intelligent location selection based on
 * resource availability, helping to choose locations with sufficient resources
 * for efficient skilling activities.
 */
@Getter
@Slf4j
public class ResourceLocationOption extends LocationOption {
    
    /**
     * The number of available resources at this location.
     * For example:
     * - Mining: Number of rock spawns
     * - Fishing: Number of fishing spots
     * - Woodcutting: Number of tree spawns
     */
    private final int numberOfResources;
    
    /**
     * Constructor with resource count and members-only flag.
     * 
     * @param worldPoint The world coordinates of this location
     * @param name The display name of this location
     * @param membersOnly Whether this location requires membership
     * @param numberOfResources The number of resources available at this location
     */
    public ResourceLocationOption(WorldPoint worldPoint, String name, boolean membersOnly, int numberOfResources) {
        super(worldPoint, name, membersOnly);
        this.numberOfResources = numberOfResources;
    }
    
    /**
     * Full constructor with all requirements and resource count.
     * 
     * @param worldPoint The world coordinates of this location
     * @param name The display name of this location
     * @param membersOnly Whether this location requires membership
     * @param numberOfResources The number of resources available at this location
     * @param requiredQuests Quest requirements for accessing this location
     * @param requiredSkills Skill level requirements for accessing this location
     * @param requiredVarbits Varbit requirements for accessing this location
     * @param requiredVarplayer Varplayer requirements for accessing this location
     * @param requiredItems Item requirements for accessing this location
     */
    public ResourceLocationOption(WorldPoint worldPoint, String name, 
                                boolean membersOnly,
                                int numberOfResources,
                                Map<Quest, QuestState> requiredQuests, 
                                Map<Skill, Integer> requiredSkills,
                                Map<Integer, Integer> requiredVarbits,
                                Map<Integer, Integer> requiredVarplayer,
                                Map<Integer, Integer> requiredItems) {
        super(worldPoint, name, membersOnly, requiredQuests, requiredSkills, 
              requiredVarbits, requiredVarplayer, requiredItems);
        this.numberOfResources = numberOfResources;
    }
    
    /**
     * Checks if this location has the minimum required number of resources.
     * 
     * @param minResources The minimum number of resources required
     * @return true if this location has enough resources, false otherwise
     */
    public boolean hasMinimumResources(int minResources) {
        return numberOfResources >= minResources;
    }
    
    /**
     * Calculates a resource efficiency score based on the number of resources
     * and distance from a reference point.
     * Higher scores indicate better locations.
     * 
     * @param referencePoint The point to calculate distance from
     * @return Efficiency score (higher is better)
     */
    public double calculateResourceEfficiencyScore(WorldPoint referencePoint) {
        if (referencePoint == null) {
            return numberOfResources; // Just return resource count if no reference point
        }
        
        double distance = Math.sqrt(
            Math.pow(getWorldPoint().getX() - referencePoint.getX(), 2) +
            Math.pow(getWorldPoint().getY() - referencePoint.getY(), 2)
        );
        
        // Avoid division by zero and give closer locations higher scores
        // Formula: resources * (100 / (distance + 1))
        // This gives more weight to resource count while factoring in distance
        return numberOfResources * (100.0 / (distance + 1));
    }
    
    /**
     * Determines if this location is better than another based on resource count and requirements.
     * Prioritizes accessible locations first, then resource count, then proximity.
     * 
     * @param other The other location to compare against
     * @param referencePoint Optional reference point for distance comparison
     * @return true if this location is better than the other
     */
    public boolean isBetterThan(ResourceLocationOption other, WorldPoint referencePoint) {
        if (other == null) return true;
        
        // First priority: accessibility (meeting requirements)
        boolean thisAccessible = this.hasRequirements();
        boolean otherAccessible = other.hasRequirements();
        
        if (thisAccessible && !otherAccessible) return true;
        if (!thisAccessible && otherAccessible) return false;
        
        // Second priority: resource count (more resources = better)
        if (this.numberOfResources != other.numberOfResources) {
            return this.numberOfResources > other.numberOfResources;
        }
        
        // Third priority: efficiency score (considers distance)
        if (referencePoint != null) {
            return this.calculateResourceEfficiencyScore(referencePoint) > 
                   other.calculateResourceEfficiencyScore(referencePoint);
        }
        
        // Fallback: prefer this location if all else is equal
        return true;
    }
    
    @Override
    public String toString() {
        return getName() + " (" + getWorldPoint().getX() + ", " + getWorldPoint().getY() + 
               ", " + getWorldPoint().getPlane() + ") - Resources: " + numberOfResources;
    }
}
