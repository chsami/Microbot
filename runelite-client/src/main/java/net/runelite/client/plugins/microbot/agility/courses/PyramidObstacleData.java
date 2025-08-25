package net.runelite.client.plugins.microbot.agility.courses;

import net.runelite.api.coords.WorldPoint;
import java.util.Arrays;
import java.util.List;

/**
 * Data class containing all obstacle area definitions for the Agility Pyramid course.
 * Separates data from logic to improve maintainability.
 */
public class PyramidObstacleData {
    
    /**
     * Represents a rectangular area where a specific obstacle can be interacted with
     */
    public static class ObstacleArea {
        public final int minX, minY, maxX, maxY, plane;
        public final int obstacleId;
        public final WorldPoint obstacleLocation;
        public final String name;
        
        public ObstacleArea(int minX, int minY, int maxX, int maxY, int plane, int obstacleId, WorldPoint obstacleLocation, String name) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
            this.plane = plane;
            this.obstacleId = obstacleId;
            this.obstacleLocation = obstacleLocation;
            this.name = name;
        }
        
        public boolean containsPlayer(WorldPoint playerPos) {
            return playerPos.getPlane() == plane &&
                   playerPos.getX() >= minX && playerPos.getX() <= maxX &&
                   playerPos.getY() >= minY && playerPos.getY() <= maxY;
        }
    }
    
    // Compact obstacle area definitions using builder pattern for readability
    public static final List<ObstacleArea> OBSTACLE_AREAS = Arrays.asList(
        // Floor 0 -> 1
        area(3354, 2830, 3354, 2830, 0, 10857, 3354, 2831, "Stairs (up)"),
        
        // Floor 1 - Clockwise path
        area(3354, 2833, 3355, 2833, 1, 10865, 3354, 2849, "Low wall"),
        area(3354, 2834, 3354, 2848, 1, 10865, 3354, 2849, "Low wall"),
        area(3354, 2850, 3355, 2850, 1, 10860, 3364, 2851, "Ledge (east)"),
        area(3354, 2851, 3363, 2852, 1, 10860, 3364, 2851, "Ledge (east)"),
        area(3364, 2850, 3375, 2852, 1, 10868, 3368, 2845, "Plank (approach)"),
        area(3374, 2845, 3375, 2849, 1, 10868, 3368, 2845, "Plank (east)"),
        area(3368, 2834, 3375, 2844, 1, 10882, 3371, 2831, "Gap (floor 1)"),
        area(3371, 2832, 3372, 2832, 1, 10886, 3362, 2831, "Ledge 3"),
        area(3362, 2832, 3370, 2832, 1, 10886, 3362, 2831, "Ledge 3"),
        area(3361, 2832, 3362, 2832, 1, 10857, 3356, 2831, "Stairs (floor 1 up)"),
        area(3356, 2831, 3360, 2833, 1, 10857, 3356, 2831, "Stairs (floor 1 up)"),
        
        // Floor 2 - Three gaps in sequence
        area(3356, 2835, 3357, 2837, 2, 10884, 3356, 2835, "Gap Cross 1 (floor 2)"),
        area(3356, 2838, 3357, 2847, 2, 10859, 3356, 2841, "Gap Jump (floor 2)"),
        area(3356, 2848, 3360, 2850, 2, 10861, 3356, 2849, "Gap Cross 2 (floor 2)"),
        // Ledge after gaps
        area(3372, 2841, 3373, 2850, 2, 10860, 3372, 2839, "Ledge (floor 2) after gap - east path"),
        area(3364, 2849, 3373, 2850, 2, 10860, 3372, 2839, "Ledge (floor 2) after gap - south path"),
        area(3367, 2849, 3367, 2850, 2, 10860, 3372, 2839, "Ledge (floor 2) at (3367, 2849-2850)"),
        area(3359, 2850, 3360, 2850, 2, 10860, 3364, 2841, "Ledge (floor 2) after gap"),
        area(3361, 2849, 3363, 2850, 2, 10860, 3364, 2841, "Ledge (floor 2) south approach"),
        // Low wall areas
        area(3370, 2834, 3373, 2840, 2, 10865, 3370, 2833, "Low wall (floor 2) after ledge"),
        area(3372, 2835, 3373, 2839, 2, 10860, 3364, 2841, "Ledge (floor 2) from wrong position"),
        area(3364, 2841, 3373, 2851, 2, 10865, 3370, 2833, "Low wall (floor 2)"),
        area(3364, 2851, 3365, 2851, 2, 10865, 3370, 2833, "Low wall (floor 2) from ledge"),
        area(3364, 2849, 3365, 2850, 2, 10865, 3370, 2833, "Low wall (floor 2) approach"),
        area(3366, 2849, 3373, 2851, 2, 10865, 3370, 2833, "Low wall (floor 2) east"),
        // End of floor 2
        area(3369, 2834, 3370, 2834, 2, 10859, 3365, 2833, "Gap jump (floor 2 end)"),
        area(3363, 2834, 3365, 2834, 2, 10857, 3358, 2833, "Stairs (floor 2 up)"),
        area(3358, 2833, 3362, 2834, 2, 10857, 3358, 2833, "Stairs (floor 2 up)"),
        
        // Floor 3 - Clockwise path
        area(3358, 2837, 3359, 2838, 3, 10865, 3358, 2837, "Low wall (floor 3)"),
        area(3358, 2840, 3359, 2842, 3, 10888, 3358, 2840, "Ledge 2"),
        // Gap jump areas
        area(3358, 2847, 3371, 2848, 3, 10859, 3358, 2843, "Gap jump area (floor 3) after ledge"),
        area(3370, 2843, 3371, 2848, 3, 10859, 3358, 2843, "Gap jump area (floor 3) east"),
        area(3358, 2843, 3362, 2846, 3, 10859, 3358, 2843, "Gap jump 1 (floor 3)"),
        area(3363, 2843, 3367, 2846, 3, 10859, 3363, 2843, "Gap jump 2 (floor 3)"),
        area(3368, 2843, 3369, 2846, 3, 10859, 3368, 2843, "Gap jump 3 (floor 3)"),
        // Plank and stairs
        area(3370, 2835, 3371, 2841, 3, 10868, 3370, 2835, "Plank (floor 3)"),
        area(3369, 2840, 3371, 2842, 3, 10868, 3370, 2835, "Plank (floor 3) - gap landing"),
        area(3360, 2835, 3369, 2836, 3, 10857, 3360, 2835, "Stairs (floor 3 up)"),
        
        // Floor 4 (uses special coordinate system, plane=2)
        area(3040, 4695, 3041, 4696, 2, 10859, 3040, 4697, "Gap jump (floor 4 start)"),
        area(3042, 4695, 3042, 4697, 2, 10859, 3040, 4695, "Gap jump (floor 4 start alt)"),
        area(3040, 4698, 3042, 4702, 2, 10865, 3040, 4699, "Low wall (floor 4)"),
        area(3041, 4697, 3042, 4697, 2, 10865, 3040, 4699, "Low wall (floor 4 alt)"),
        area(3043, 4701, 3043, 4702, 2, 10859, 3048, 4695, "Gap jump (floor 4 second)"),
        area(3043, 4695, 3049, 4700, 2, 10859, 3048, 4695, "Gap jump (floor 4 mid)"),
        area(3047, 4693, 3049, 4696, 2, 10865, 3047, 4693, "Low wall (floor 4 end)"),
        area(3048, 4695, 3049, 4696, 2, 10865, 3047, 4693, "Low wall (floor 4 end alt)"),
        area(3042, 4693, 3047, 4695, 2, 10857, 3042, 4693, "Stairs (floor 4 up)"),
        
        // Floor 5 (pyramid top, plane=3)
        area(3042, 4697, 3043, 4698, 3, 10851, 3042, 4697, "Climbing rocks (grab pyramid)"),
        area(3042, 4697, 3043, 4698, 3, 10859, 3046, 4698, "Gap jump (floor 5) from pyramid spot"),
        area(3044, 4697, 3047, 4700, 3, 10859, 3046, 4698, "Gap jump (floor 5)"),
        area(3047, 4696, 3047, 4700, 3, 10855, 3044, 4695, "Doorway (floor 5)"),
        area(3044, 4695, 3046, 4696, 3, 10855, 3044, 4695, "Doorway (floor 5 approach)")
    );
    
    // Helper method to create ObstacleArea with less verbosity
    private static ObstacleArea area(int minX, int minY, int maxX, int maxY, int plane, 
                                     int obstacleId, int locX, int locY, String name) {
        return new ObstacleArea(minX, minY, maxX, maxY, plane, obstacleId, 
                               new WorldPoint(locX, locY, plane), name);
    }
}