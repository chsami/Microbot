package net.runelite.client.plugins.microbot.util.shop.models;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
@EqualsAndHashCode()
public class Rs2ShopSource {

    @Getter    
    final private String shopNpcName;    // NPC name, if applicable
    @Getter    
    final WorldArea locationArea;
    @Getter    
    final private boolean members;
    @Getter 
    final private Map<Quest,QuestState> quests; // Quests required to access the shop, with progress state
    @Getter 
    final private Map<Integer,Integer> varbitReq; // Varbit requirements for the shop, with progress state
    @Getter 
    final private Map<Integer,Integer> varPlayerReq; // Varbit requirements for the shop, with progress state
    @Getter    
    final private Rs2ShopType shopType;        
    @Getter
    final private double changePercent; // -1 means unknown ()    
    @Getter
    final private double percentageSoldAt; // -1 means unknown (for the grand exchange , price to sell at, <0, adjusted to current mean price unter cut)
    @Getter
    final private double percentageBoughtAt; // -1 means unknown( for the grand exchange, price to buy at, <0 , adjusted to current mean price under cut)
    @Getter    
    final private String notes; // notes for the shop source

 
    public static Rs2ShopSource createGEShopSource( double percentageSoldAt, double percentageBoughtAt) {
        WorldPoint locationArea = BankLocation.GRAND_EXCHANGE.getWorldPoint();
        WorldPoint southWestPointGE = new WorldPoint(locationArea.getX() - 5, locationArea.getY() - 5, locationArea.getPlane());
        WorldArea area = new WorldArea(southWestPointGE.getX(), southWestPointGE.getY(), 10, 10, locationArea.getPlane());
        return new Rs2ShopSource( "Grand Exchange", area, Rs2ShopType.GRAND_EXCHANGE,percentageSoldAt,percentageBoughtAt,0.0, false);
    }
    public Rs2ShopSource(
        String shopName,            
        WorldArea locationArea,
        Rs2ShopType shopType,
        double percentageSoldAt,
        double percentageBoughtAt,
        double changePercent,
        boolean members ) {
        this( shopName, locationArea, shopType, percentageSoldAt, percentageBoughtAt, changePercent ,Map.of(),Map.of(),Map.of(), members, "");
    }
    public Rs2ShopSource(
            String shopName,            
            WorldArea locationArea,
            Rs2ShopType shopType,
            double percentageSoldAt,
            double percentageBoughtAt, 
            double changePercent,
            Map <Quest, QuestState> quests,
            Map <Integer, Integer> varbitReq,
            Map <Integer, Integer> varPlayerReq,
            boolean members, 
            String notes) {        
        this.shopNpcName = shopName;
        this.locationArea = locationArea;
        this.shopType = shopType;
        this.members = members;        
        this.percentageSoldAt = percentageSoldAt;
        this.percentageBoughtAt = percentageBoughtAt;
        this.changePercent = changePercent;      
        this.quests = quests != null ? quests : Map.of();
        this.varbitReq = varbitReq != null ? varbitReq : Map.of();
        this.varPlayerReq = varPlayerReq != null ? varPlayerReq : Map.of();
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "ShopSource{" +
                "shopName='" +  + '\'' +
                ", locationArea='" + locationArea + '\'' +                
                ", shopType=" + shopType +
                ", percentageSoldAt=" + percentageSoldAt +
                ", percentageBoughtAt=" + percentageBoughtAt +
                
                ", changePercent=" + changePercent +
                ", members=" + members +
                ", notes='" + notes + '\'' +
                '}';
    }
    
    /**
     * Returns a multi-line display string with detailed shop information.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing shop source details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Shop Source Details ===\n");        
        sb.append("Shop Npc Name:\t\t").append(shopNpcName).append("\n");
        sb.append("Location:\t\t").append(locationArea != null ? locationArea.toString() : "Unknown").append("\n");
        sb.append("Members Only:\t\t").append(members ? "Yes" : "No").append("\n");       
        if (percentageSoldAt != -1) {
            sb.append("Sell % of Shop Value:\t\t").append(String.format("%.1f%%", percentageSoldAt * 100)).append("\n");
        }
        
        if (percentageBoughtAt != -1) {
            sb.append("Buy % of Shop Value:\t\t").append(String.format("%.1f%%", percentageBoughtAt * 100)).append("\n");
        }
        
        if (changePercent != -1) {
            sb.append("Price Change:\t\t").append(String.format("%.2f%%", changePercent)).append("\n");
        }
        
        if (notes != null && !notes.isEmpty()) {
            sb.append("Notes:\t\t\t").append(notes).append("\n");
        }
        
        return sb.toString();
    }

    public WorldPoint getLocation() {
        return new WorldPoint(
            locationArea.getX() + locationArea.getWidth() / 2,
            locationArea.getY() + locationArea.getHeight() / 2,
            locationArea.getPlane()
        );
    }

    public Rs2NpcModel getShopNPC() {
        WorldArea area = getLocationArea();
        List<Rs2NpcModel> npcs = Rs2Npc.getNpcs().collect(Collectors.toList());
        List<Rs2NpcModel> potentialNPCShops = npcs.stream().filter(npc -> npc.getComposition().getActions() != null
                && Arrays.asList(npc.getComposition().getActions()).stream().anyMatch("Trade"::equalsIgnoreCase)
                && area.contains(npc.getWorldLocation())).collect(Collectors.toList());
        if (potentialNPCShops.isEmpty()) {
            log.error("Could not find shop NPC for shop: " + this.shopNpcName + " in area: " + area);
            return null;
        }
        return potentialNPCShops.get(0);
    }

    public Rs2NpcModel getPotentialShopNPCNearPlayer() {
        int searchRange = 10;
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        List<Rs2NpcModel> npcs = Rs2Npc.getNpcs().collect(Collectors.toList());
        List<Rs2NpcModel> potentialNPCShops = npcs.stream().filter(npc -> npc.getComposition().getActions() != null
                && Arrays.asList(npc.getComposition().getActions()).stream().anyMatch("Trade"::equalsIgnoreCase)
                && npc.getWorldLocation().distanceTo(playerLocation) < searchRange).collect(Collectors.toList());
        if (potentialNPCShops == null || potentialNPCShops.isEmpty()) {
            log.error("Could not find shop NPC for shop: " + this.shopNpcName + " at near player location: "
                    + playerLocation + "with range of: " + searchRange);
            return null;
        }

        for (Rs2NpcModel npc : potentialNPCShops) {
            if (this.shopNpcName.equalsIgnoreCase(npc.getName())) {
                return npc;
            } else if (this.shopNpcName.toLowerCase().contains("General Store".toLowerCase())) {
                return npc;
            } else if (this.shopNpcName.toLowerCase().contains(npc.getName().toLowerCase())) {
                return npc;
            }
        }
        log.error("Could not find shop NPC for shop: " + this.shopNpcName + " at near player location: "
                + playerLocation + "with range of: " + searchRange);
        return null;
    }

    public boolean canAccess() {
        if (this.isMembers() && !Rs2Player.isMember()) {
            return false;
        }
        if (this.getQuests() != null && !this.getQuests().isEmpty()) {
            for (Map.Entry<Quest, QuestState> entry : this.getQuests().entrySet()) {
                Quest quest = entry.getKey();
                QuestState state = entry.getValue();
                QuestState playerQuestState = Rs2Player.getQuestState(quest);
                if (state != playerQuestState && playerQuestState != QuestState.FINISHED) {
                    return false;
                }
            }
        }
        
        return Rs2Walker.canReach(getLocation(), true);
    } 

}