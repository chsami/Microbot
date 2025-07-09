package net.runelite.client.plugins.microbot.util.walker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;

/**
 * Represents a comparative analysis between a direct route to a destination and a route via a bank.
 * This class stores information about both routes including distances, paths, and a recommendation.
 * Used by Rs2Walker.compareRoutes() to help determine the most efficient path when teleport items
 * may be needed from the bank.
 */
@Getter
public class TransportRouteAnalysis {
    /** Complete path of WorldPoints representing the direct route to destination */
    private final List<WorldPoint> directPath;
    
    /** Reference to the nearest accessible BankLocation object, null if no bank is accessible */
    private final BankLocation nearestBank;
    
    /** WorldPoint coordinates of the nearest bank location, null if no bank is accessible */
    private final WorldPoint bankLocation;
    
    /** Path of WorldPoints from starting point to the nearest bank */
    private final List<WorldPoint> pathToBank;
    
    /** Path of WorldPoints from bank to destination, accounting for items available in bank */
    private final List<WorldPoint> pathFromBank;
    
    /** Summary text describing the analysis results and recommendation */
    private final String analysis;
    
    /**
     * Constructs a complete transport route analysis result.
     *
     * @param directPath Complete list of WorldPoints for direct route
     * @param nearestBank Reference to nearest BankLocation object
     * @param bankLocation WorldPoint coordinates of nearest bank
     * @param pathToBank List of WorldPoints representing path to bank
     * @param pathFromBank List of WorldPoints representing path from bank to destination (with bank items)
     * @param analysis Text summary of the route analysis and recommendation
     */
    public TransportRouteAnalysis(List<WorldPoint> directPath, 
                                BankLocation nearestBank, WorldPoint bankLocation,List<WorldPoint> pathToBank,
                                List<WorldPoint> pathFromBank,String analysis) {
        this.directPath = directPath;
        this.nearestBank = nearestBank;
        this.bankLocation = bankLocation;
        this.pathToBank = pathToBank;
        this.pathFromBank = pathFromBank;
        this.analysis = analysis;
    }
    
    /**
     * Calculates the direct route distance in tiles from the stored path.
     * @return The direct route distance, or -1 if path is empty or invalid
     */
    public int getDirectDistance() {
        if (directPath == null || directPath.isEmpty()) return -1;
        return directPath.size();
    }
    
    /**
     * Calculates the banking route distance in tiles from the stored paths.
     * @return The total banking route distance (to bank + from bank), or -1 if paths are invalid
     */
    public int getBankingRouteDistance() {
        if (pathToBank == null || pathFromBank == null || 
            pathToBank.isEmpty() || pathFromBank.isEmpty()) return -1;
        return pathToBank.size() + pathFromBank.size();
    }
    
    public int getTileSavings() {
        int directDist = getDirectDistance();
        int bankingDist = getBankingRouteDistance();
        if (directDist == -1 || bankingDist == -1) return 0;
        return Math.abs(directDist - bankingDist);
    }
    
    /**
     * Determines if the direct route is faster than the banking route.
     * When distances are equal, direct route is considered faster.
     * 
     * @return true if direct route is faster or equal, false if banking route is faster
     */
    public boolean isDirectIsFaster() {
        int directDist = getDirectDistance();
        int bankingDist = getBankingRouteDistance();
        
        // If banking route is unavailable, direct is always faster
        if (bankingDist == -1) return true;
        // If direct route is unavailable, banking is faster
        if (directDist == -1) return false;
        // When equal, direct is considered faster (maintaining existing logic)
        return directDist <= bankingDist;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TransportRouteAnalysis {\n");
        sb.append("\tdirectIsFaster: ").append(isDirectIsFaster()).append("\n");
        
        int directDist = getDirectDistance();
        int bankingDist = getBankingRouteDistance();
        sb.append("\tdirectDistance: ").append(directDist == -1 ? "N/A" : directDist).append(" tiles\n");
        sb.append("\tbankingRouteDistance: ").append(bankingDist == -1 ? "N/A" : bankingDist).append(" tiles\n");
        sb.append("\ttileSavings: ").append(getTileSavings()).append(" tiles\n");
        sb.append("\tnearestBank: ").append(nearestBank != null ? nearestBank.name() : "None").append("\n");
        sb.append("\tbankLocation: ").append(bankLocation != null ? bankLocation : "N/A").append("\n");
        sb.append("\tanalysis: \"").append(analysis != null ? analysis : "No analysis available").append("\"\n");
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Gets all required transports for the direct path with default parameters.
     * @return List of required transports for direct path
     */
    public List<Transport> getTransportsForDirectPath(){            
        return getTransportsForDirectPath(0, TransportType.TELEPORTATION_ITEM, true);
    }
    
    /**
     * Gets all required transports for the direct path with custom parameters.
     * @param startIndex The index to start from in the path
     * @param prefTransportType The preferred transport type
     * @param applyFiltering Whether to apply filtering
     * @return List of required transports for direct path
     */
    public List<Transport> getTransportsForDirectPath(int startIndex, TransportType prefTransportType, boolean applyFiltering){            
        List<Transport> transports = Rs2Walker.getTransportsForPath(directPath, startIndex, prefTransportType, applyFiltering);
        return transports;
    }
    
    /**
     * Gets all required transports for the banking route with default parameters.
     * @return List of required transports for banking route (to and from bank)
     */
    public List<Transport> getTransportsForBankingPath(){            
        return getTransportsForBankingPath(0, TransportType.TELEPORTATION_ITEM, true);
    }
    
    /**
     * Gets all required transports for the banking route with custom parameters.
     * @param startIndex The index to start from in the path
     * @param prefTransportType The preferred transport type
     * @param applyFiltering Whether to apply filtering
     * @return List of required transports for banking route (to and from bank)
     */
    public List<Transport> getTransportsForBankingPath(int startIndex, TransportType prefTransportType, boolean applyFiltering){            
        List<Transport> transportsToTargetToBank = Rs2Walker.getTransportsForPath(pathToBank, startIndex, prefTransportType, applyFiltering);            
        List<Transport> transportsToTargetFromBank = Rs2Walker.getTransportsForPath(pathFromBank, startIndex, prefTransportType, applyFiltering);            
        return new ArrayList<>(){{
            addAll(transportsToTargetToBank);
            addAll(transportsToTargetFromBank);
        }};
    }
    
    /**
     * Gets missing transports for the direct path.
     * @return List of missing transports for direct path
     */
    public List<Transport> getMissingTransportsForDirectPath(){
        List<Transport> missingTransports = Rs2Walker.getMissingTransports(getTransportsForDirectPath());
        return missingTransports;
    }
    
    /**
     * Gets missing transport items with their quantities for the direct path.
     * @return Map of item IDs to their required quantities
     */
    public Map<Integer, Integer> getMissingTransportsItemsWithQuantitiesForDirectPath(){
        List<Transport> missingTransports = getMissingTransportsForDirectPath();
        Map<Integer, Integer> missingItemsWithQuantities = Rs2Walker.getMissingTransportItemIdsWithQuantities(missingTransports);
        return missingItemsWithQuantities;
    }
    
    /**
     * Gets missing transports for the banking route (to and from bank).
     * @return List of missing transports for the banking route
     */
    public List<Transport> getMissingTransportsForBankingRoute(){
        List<Transport> missingTransports = Rs2Walker.getMissingTransports(getTransportsForBankingPath(0, TransportType.TELEPORTATION_ITEM, true));
        return missingTransports;
    }
    
    /**
     * Gets missing transport items with their quantities for the banking route.
     * @return Map of item IDs to their required quantities
     */
    public Map<Integer, Integer> getMissingTransportsItemsWithQuantitiesForBankingRoute(){
        List<Transport> missingTransports = getMissingTransportsForBankingRoute();
        Map<Integer, Integer> missingItemsWithQuantities = Rs2Walker.getMissingTransportItemIdsWithQuantities(missingTransports);
        return missingItemsWithQuantities;
    }
    
    /**
     * Gets all required transports for the path to bank with default parameters.
     * @return List of required transports for path to bank
     */
    public List<Transport> getTransportsForPathToBank() {
        return getTransportsForPathToBank(0, TransportType.TELEPORTATION_ITEM, true);
    }
    
    /**
     * Gets all required transports for the path to bank with custom parameters.
     * @param startIndex The index to start from in the path
     * @param prefTransportType The preferred transport type
     * @param applyFiltering Whether to apply filtering
     * @return List of required transports for path to bank
     */
    public List<Transport> getTransportsForPathToBank(int startIndex, TransportType prefTransportType, boolean applyFiltering) {
        return Rs2Walker.getTransportsForPath(pathToBank, startIndex, prefTransportType, applyFiltering);
    }
    
    /**
     * Gets all required transports for the path from bank with default parameters.
     * @return List of required transports for path from bank
     */
    public List<Transport> getTransportsForPathFromBank() {
        return getTransportsForPathFromBank(0, TransportType.TELEPORTATION_ITEM, true);
    }
    
    /**
     * Gets all required transports for the path from bank with custom parameters.
     * @param startIndex The index to start from in the path
     * @param prefTransportType The preferred transport type
     * @param applyFiltering Whether to apply filtering
     * @return List of required transports for path from bank
     */
    public List<Transport> getTransportsForPathFromBank(int startIndex, TransportType prefTransportType, boolean applyFiltering) {
        return Rs2Walker.getTransportsForPath(pathFromBank, startIndex, prefTransportType, applyFiltering);
    }
    
    /**
     * Gets missing transports specifically for the path from bank to destination.
     * @return List of missing transports for path from bank
     */
    public List<Transport> getMissingTransportsForPathFromBank() {
        List<Transport> missingTransports = Rs2Walker.getMissingTransports(getTransportsForPathFromBank());
        return missingTransports;
    }
    
    /**
     * Gets missing transport items with their quantities specifically for the path from bank to destination.
     * @return Map of item IDs to their required quantities for path from bank
     */
    public Map<Integer, Integer> getMissingTransportsItemsWithQuantitiesForPathFromBank() {
        List<Transport> missingTransports = getMissingTransportsForPathFromBank();
        Map<Integer, Integer> missingItemsWithQuantities = Rs2Walker.getMissingTransportItemIdsWithQuantities(missingTransports);
        return missingItemsWithQuantities;
    }
}

