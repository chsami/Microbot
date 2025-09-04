package net.runelite.client.plugins.microbot.woodcutting.Forestry;

import net.runelite.api.Constants;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.woodcutting.AutoWoodcuttingPlugin;
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;

import lombok.extern.slf4j.Slf4j;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
@Slf4j
public class HivesEvent implements BlockingEvent {

    private final AutoWoodcuttingPlugin plugin;
    private final Set<Integer> completedBeehives = new HashSet<>();
    private Rs2NpcModel currentBeehive = null;
    private int initialLogCount = -1;
    
    public HivesEvent(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean validate() {
        try{
            if (plugin == null || !Microbot.isPluginEnabled(plugin)) return false;
            if (Microbot.getClient() == null || !Microbot.isLoggedIn()) return false;
            var beehives = Rs2Npc.getNpcs(x -> x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_1 || x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_2);
            return beehives.findAny().isPresent() && Rs2Inventory.count(plugin.config.TREE().getLogID()) > 1;    
        } catch (Exception e) {
            log.error("HivesEvent: Exception in validate method", e);
            return false;
        }
    }  

    @Override
    public boolean execute() {
        plugin.currentForestryEvent = ForestryEvents.BEE_HIVE;
        completedBeehives.clear();
        initialLogCount = Rs2Inventory.count(plugin.config.TREE().getLogID());
        
        log.info("Starting beehive event with {} logs", initialLogCount);
        Rs2Walker.setTarget(null); // stop walking, stop moving to bank for example
        while (this.validate()) {
            // handle log quantity dialog
            if (Rs2Widget.findWidget("How many logs would you like to add", null, false) != null) {
                log.info("Adding logs to beehive (dialog open)");
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isAnimating(1200), 6000);
                continue;
            }
            
            // find available beehives, excluding ones we've already completed
            List<Rs2NpcModel> availableBeehives = Rs2Npc.getNpcs(x -> 
                    (x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_1 || 
                     x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_2) &&
                    !completedBeehives.contains(x.getIndex()))
                    .collect(Collectors.toList());
            
            if (availableBeehives.isEmpty()) {
                log.info("No more available beehives to work on");
                break;
            }
            
            // select a beehive to work on (prioritize the same one we're currently building)
            final Rs2NpcModel targetBeehive;
            if (currentBeehive != null && availableBeehives.stream().anyMatch(b -> b.getIndex() == currentBeehive.getIndex())) {
                targetBeehive = currentBeehive;
                log.debug("Continuing work on current beehive {}", targetBeehive.getIndex());
            } else {
                targetBeehive = availableBeehives.get(0);
                currentBeehive = targetBeehive;
                log.info("Starting work on new beehive {}", targetBeehive.getIndex());
            }
            
            // check if we're already working on this beehive
            if (Rs2Player.isInteracting() || Rs2Player.isAnimating()) {
                log.debug("Already working on beehive, waiting for completion");
                // wait for current action to complete or timeout after 30 seconds
                boolean completed = sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isAnimating(), 30000);
                
                if (!completed) {
                    log.warn("Building action timed out, marking beehive {} as potentially completed", targetBeehive.getIndex());
                    completedBeehives.add(targetBeehive.getIndex());
                    currentBeehive = null;
                }
                continue;
            }
            
            // check if beehive still exists (might have been completed by others or disappeared)
            if (!Rs2Npc.getNpcs(x -> x.getIndex() == targetBeehive.getIndex()).findAny().isPresent()) {
                log.info("Beehive {} completed or disappeared", targetBeehive.getIndex());
                completedBeehives.add(targetBeehive.getIndex());
                currentBeehive = null;
                continue;
            }
            
            // interact with the beehive
            int currentLogCount = Rs2Inventory.count(plugin.config.TREE().getLogID());
            if (currentLogCount <= 1) {
                log.info("Insufficient logs remaining ({}) to continue building", currentLogCount);
                break;
            }
            
            log.info("Building beehive {} (logs: {})", targetBeehive.getIndex(), currentLogCount);
            if (Rs2Npc.interact(targetBeehive, "Build")) {
                // wait for interaction to start
                sleepUntil(() -> Rs2Player.isInteracting() || Rs2Player.isAnimating(), 3000);
                
                // wait for building action to complete with timeout
                boolean buildCompleted = sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isAnimating(), 15000);
                
                if (buildCompleted) {
                    int newLogCount = Rs2Inventory.count(plugin.config.TREE().getLogID());
                    if (newLogCount < currentLogCount) {
                        log.info("Successfully contributed logs to beehive {} ({} -> {} logs)", 
                                targetBeehive.getIndex(), currentLogCount, newLogCount);
                    }
                    
                    // check if this beehive is now completed (disappeared)
                    if (!Rs2Npc.getNpcs(x -> x.getIndex() == targetBeehive.getIndex()).findAny().isPresent()) {
                        log.info("Beehive {} completed successfully", targetBeehive.getIndex());
                        completedBeehives.add(targetBeehive.getIndex());
                        currentBeehive = null;
                    }
                } else {
                    log.warn("Building action timed out for beehive {}", targetBeehive.getIndex());
                    // don't mark as completed yet, might just be slow
                }
                
                // brief pause between interactions to appear more natural
                sleepUntil(() -> false, Constants.GAME_TICK_LENGTH/2 + (int)(Math.random() * Constants.GAME_TICK_LENGTH*3/2)); // 300 - 900 ms
            } else {
                log.warn("Failed to interact with beehive {}", targetBeehive.getIndex());
                // brief delay before trying again
                sleepUntil(() -> false, 2000);
            }
        }
        
        int finalLogCount = Rs2Inventory.count(plugin.config.TREE().getLogID());
        int logsUsed = initialLogCount - finalLogCount;
        log.info("Beehive event completed. Logs used: {}, Beehives worked on: {}", logsUsed, completedBeehives.size());
        
        plugin.incrementForestryEventCompleted();
        return true;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.NORMAL;
    }
}
