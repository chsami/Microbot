package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
     * Container class for location data with requirements.
     */
@Getter
@Slf4j
public class LocationOption {
        private final WorldPoint worldPoint;
        private final String name;
        private final boolean membersOnly; // Indicates if this location is members-only
        private final Map<Quest, QuestState> requiredQuests;
        private final Map<Skill, Integer> requiredSkills;
        private final Map<Integer,Integer> requiredVarbits;
        private final Map<Integer,Integer> requiredVarplayer;
        private final Map<Integer,Integer> requiredItems; //id key ,and amount value
        
        
        
        public LocationOption(WorldPoint worldPoint, String name, boolean membersOnly) {
            this(worldPoint, name,membersOnly, new HashMap<>(), new HashMap<>(),new HashMap<>(),new HashMap<>(),new HashMap<>());
        }
        
        public LocationOption(WorldPoint worldPoint, String name, 
                            boolean membersOnly,
                            Map<Quest, QuestState> requiredQuests, 
                            Map<Skill, Integer> requiredSkills,
                            Map <Integer,Integer> requiredVarbits,
                            Map <Integer,Integer> requiredVarplayer,
                            Map <Integer,Integer> requiredItems
                            ) {
            this.worldPoint = worldPoint;            
            this.name = name;
            this.membersOnly = membersOnly;
            this.requiredQuests = requiredQuests != null ? new HashMap<>(requiredQuests) : new HashMap<>();
            this.requiredSkills = requiredSkills != null ? new HashMap<>(requiredSkills) : new HashMap<>();
            this.requiredVarbits = requiredVarbits != null ? new HashMap<>(requiredVarbits) : new HashMap<>();
            this.requiredVarplayer = requiredVarplayer != null ? new HashMap<>(requiredVarplayer) : new HashMap<>();
            this.requiredItems = requiredItems != null ? new HashMap<>(requiredItems) : new HashMap<>();
            
        }
        public boolean canReach() {
            return Rs2Walker.canReach(worldPoint);
        }
        /**
         * Checks if the player meets all requirements for this location.
         * Improved implementation using streams for better performance and readability.
         */
        public boolean hasRequirements() {
            if (Microbot.getClient() == null) {
                log.debug("LocationRequirement hasRequirements called outside client thread");
                return false;
            }
            if(!Microbot.isLoggedIn()){
                log.debug("Player is not logged in, cannot check location requirements");
                return false;
            }
            // Check quest requirements using streams
            boolean questRequirementsMet = requiredQuests.entrySet().stream()
                    .allMatch(questReq -> {
                        QuestState currentState = Rs2Player.getQuestState(questReq.getKey());
                        QuestState requiredState = questReq.getValue();
                        
                        // If required state is FINISHED, player must have finished
                        if (requiredState == QuestState.FINISHED) {
                            return currentState == QuestState.FINISHED;
                        }
                        // If required state is IN_PROGRESS, player must have started (IN_PROGRESS or FINISHED)
                        if (requiredState == QuestState.IN_PROGRESS) {
                            return currentState == QuestState.IN_PROGRESS || currentState == QuestState.FINISHED;
                        }
                        return true;
                    });
            
            if (!questRequirementsMet) {
                return false;
            }
            
            // Check skill requirements using streams
            boolean skillRequirementsMet = requiredSkills.entrySet().stream()
                    .allMatch(skillReq -> Rs2Player.getSkillRequirement(skillReq.getKey(), skillReq.getValue()));
            
            if (!skillRequirementsMet) {
                return false;
            }
            
            // Check varbit requirements using streams
            boolean varbitRequirementsMet = requiredVarbits.entrySet().stream()
                    .allMatch(varbitReq -> Microbot.getVarbitValue(varbitReq.getKey()) == varbitReq.getValue());
            
            if (!varbitRequirementsMet) {
                return false;
            }
            
            // Check varplayer requirements using streams
            boolean varplayerRequirementsMet = requiredVarplayer.entrySet().stream()
                    .allMatch(varplayerReq -> Microbot.getVarbitPlayerValue(varplayerReq.getKey()) == varplayerReq.getValue());
            
            if (!varplayerRequirementsMet) {
                return false;
            }
            
            // Check item requirements using streams
            boolean itemRequirementsMet = requiredItems.entrySet().stream()
                    .allMatch(itemReq -> {
                        int itemId = itemReq.getKey();
                        int requiredAmount = itemReq.getValue();
                        
                        int numberOfItems = Rs2Inventory.count(itemId) + 
                                          (Rs2Equipment.isWearing(itemId) ? 1 : 0); //TODO we must check if we are checking for stackable items..
                        int numberOfItemsInPouch = Rs2RunePouch.getQuantity(itemId);
                        int numberOfItemsInBank = Rs2Bank.count(itemId);
                                          // todo check rune pouches ? when the ids runes..,
                        // bolt ammo slot ? when the ids is any ammo
                        
                        if (numberOfItems+numberOfItemsInPouch +numberOfItemsInBank< requiredAmount) {
                            log.warn("Missing required item: {} x{} (have {})", itemId, requiredAmount, numberOfItems);
                            Microbot.log("Missing required item: " + itemId + " x" + requiredAmount + " (have " + numberOfItems + ")");
                            return false;
                        }
                        return true;
                    });
            
            return itemRequirementsMet;
        }
        
        @Override
        public String toString() {
            return name + " (" + worldPoint.getX() + ", " + worldPoint.getY() + ", " + worldPoint.getPlane() + ")";
        }
    }
