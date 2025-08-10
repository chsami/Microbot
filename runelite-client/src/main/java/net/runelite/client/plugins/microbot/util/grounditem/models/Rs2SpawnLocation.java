package net.runelite.client.plugins.microbot.util.grounditem.models;

import lombok.Getter;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import java.util.List;
import java.time.Duration;

@Getter
public class Rs2SpawnLocation {
    final private int itemID;
    final private String name;
    final private String subName;    
    final private List<WorldPoint> locations;
    final private boolean members;    
    final private int mapID;
    final private Duration respawnTime; // Optional respawn time for the spawn location, can be null if not applicable
    private ItemComposition itemComposition;

    private ItemComposition getItemComposition() {
        if (itemComposition == null) {
            itemComposition =  Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(itemID)).orElse(null);
        }
        return itemComposition;
    }  
    public String getItemName() {
        return getItemComposition() != null ? getItemComposition().getName() : "Unknown Item";
    }   
    /**
     * Constructor for a spawn location with all parameters.
     * 
     * @param itemName The name of the item that spawns here.
     * @param name The name of the spawn location (e.g., "Lumbridge Castle").
     * @param subName The sub-name of the spawn location (e.g., "Lumbridge Castle - Ground Floor").
     * @param locations List of WorldPoints where the item can spawn.
     * @param members Whether the spawn location is members-only.
     * @param mapID The map ID for the spawn location, e.g., 0 for Lumbridge Castle -> floor.
     */
    public Rs2SpawnLocation(int id, String name, String subName, List<WorldPoint> locations, boolean members, int mapID){
        this.itemID = id;
        this.locations = locations;
        this.members = members;        
        this.mapID = mapID; // Map ID for the spawn location, e.g., 0 for Lumbridge Castle -> floor 
        this.subName = subName; // Sub-name of the spawn location, e.g., "Lumbridge Castle - Ground Floor"
        this.name = name; // Name of the spawn location, e.g., "Lumbridge Castle"
        this.respawnTime = null; // Optional respawn time, can be null if not applicable
    }
   
    public Rs2SpawnLocation(int id, String name, String subName, List<WorldPoint> locations, boolean members, int mapID, Duration respawnTime){
        this.itemID = id; // ID of the item that spawns here
        this.locations = locations;
        this.members = members;        
        this.mapID = mapID; // Map ID for the spawn location, e.g., 0 for Lumbridge Castle -> floor 
        this.subName = subName; // Sub-name of the spawn location, e.g., "Lumbridge Castle - Ground Floor"
        this.name = name; // Name of the spawn location, e.g., "Lumbridge Castle"
        this.respawnTime = respawnTime; // Optional respawn time
    }
    /**
     * Adds a new spawn location to the list of locations.
     * 
     * @param location The WorldPoint where the item can spawn.
     */
    public void addSpawnLocation(WorldPoint location) {
        if (location != null && !locations.contains(location)) {
            locations.add(location);
        }
    }
    public int numberOfSpawns(){
        return locations.size();
    }
    public WorldPoint nearestSpawnLocation(){
        WorldPoint currentPlayerLocation = Rs2Player.getWorldLocation();
        if (locations.isEmpty() || currentPlayerLocation == null) {
            return null; // No spawn locations available
        }
        return locations.stream()
                .min((loc1, loc2) -> Integer.compare(
                        loc1.distanceTo(currentPlayerLocation),
                        loc2.distanceTo(currentPlayerLocation)))
                .orElse(null); // Return the closest spawn location
        
    }

    /**
     * Returns a multi-line display string with detailed spawn location information.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing spawn location details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Spawn Location Details ===\n");
        sb.append("Item Name:\t\t").append(getItemName()).append("\n");
        sb.append("Location Name:\t\t").append(name != null ? name : "Unknown Location").append("\n");
        sb.append("Sub-Location:\t\t").append(subName != null ? subName : "No sub-location").append("\n");
        sb.append("Map ID:\t\t\t").append(mapID != -1 ? mapID : "Unknown").append("\n");
        sb.append("Members Only:\t\t").append(members ? "Yes" : "No").append("\n");
        sb.append("Number of Spawns:\t").append(numberOfSpawns()).append("\n");
        
        if (respawnTime != null) {
            sb.append("Respawn Time:\t\t").append(respawnTime.toSeconds()).append(" seconds\n");
        }
        
        if (!locations.isEmpty()) {
            sb.append("Spawn Locations:\n");
            for (int i = 0; i < locations.size(); i++) {
                WorldPoint loc = locations.get(i);
                sb.append("\t").append(i + 1).append(".\t").append(loc.toString()).append("\n");
            }
            
            WorldPoint nearest = nearestSpawnLocation();
            if (nearest != null) {
                sb.append("Nearest Spawn:\t\t").append(nearest.toString()).append("\n");
                WorldPoint playerLoc = Rs2Player.getWorldLocation();
                if (playerLoc != null) {
                    sb.append("Distance:\t\t").append(nearest.distanceTo(playerLoc)).append(" tiles\n");
                }
            }
        } else {
            sb.append("Spawn Locations:\tNone available\n");
        }
        
        return sb.toString();
    }

    
}