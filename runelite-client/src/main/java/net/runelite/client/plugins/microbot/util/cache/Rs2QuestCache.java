package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperPlugin;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import net.runelite.client.plugins.microbot.util.cache.strategy.simple.QuestUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.serialization.CacheSerializable;

import java.util.function.Supplier;

/**
 * Thread-safe cache for quest states using the unified cache architecture.
 * Automatically updates when quest-related events are received and supports persistence.
 * 
 * This class extends Rs2UnifiedCache and provides specific quest caching functionality
 * with proper EventBus integration for @Subscribe methods.
 */
@Slf4j
public class Rs2QuestCache extends Rs2Cache<Quest, QuestState> implements CacheSerializable {
    
    private static Rs2QuestCache instance;
    
    private static Quest trackedQuest = null; // Currently tracked quest, if any
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2QuestCache() {
        super("QuestCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.withUpdateStrategy(new QuestUpdateStrategy())
                .withPersistence("quests");
    }
    
    /**
     * Gets the singleton instance of Rs2QuestCache.
     * 
     * @return The singleton quest cache instance
     */
    public static synchronized Rs2QuestCache getInstance() {
        if (instance == null) {
            instance = new Rs2QuestCache();
        }
        return instance;
    }
    
    /**
     * Gets the cache instance for backward compatibility.
     * 
    /**
     * Gets the cache instance for backward compatibility.
     * 
     * @return The singleton unified cache instance
     */
    public static Rs2Cache<Quest, QuestState> getCache() {
        return getInstance();
    }
    
    /**
     * Checks if QuestHelperPlugin is available and enabled using Microbot utilities.
     * 
     * @return true if QuestHelperPlugin is available for integration
     */
    private static boolean isQuestHelperAvailable() {
        try {
            return Microbot.getPluginManager() != null && 
                   Microbot.getPluginManager().getPlugins().stream()
                           .anyMatch(plugin -> plugin instanceof QuestHelperPlugin && 
                                               Microbot.getPluginManager().isPluginEnabled(plugin));
        } catch (Exception e) {
            log.debug("QuestHelperPlugin not available: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the QuestHelperPlugin instance using Microbot utilities.
     * 
     * @return The QuestHelperPlugin instance, or null if not available
     */
    private static QuestHelperPlugin getQuestHelperPlugin() {
        try {
            if (!isQuestHelperAvailable()) {
                return null;
            }
            
            return (QuestHelperPlugin) Microbot.getPluginManager().getPlugins().stream()
                    .filter(plugin -> plugin instanceof QuestHelperPlugin)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("Error getting QuestHelperPlugin: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets the currently selected quest from QuestHelperPlugin if available.
     * 
     * @return The currently selected QuestHelper, or null if none selected or plugin unavailable
     */
    private static QuestHelper getSelectedQuestHelper() {
        QuestHelperPlugin questHelperPlugin = getQuestHelperPlugin();
        if (questHelperPlugin != null) {
            return questHelperPlugin.getSelectedQuest();
        }
        return null;
    }
    
    /**
     * Gets the currently active RuneLite Quest from QuestHelperPlugin.
     * Maps QuestHelper to RuneLite Quest objects using QuestHelperQuest info.
     * Also attempts to detect active quests from client state when QuestHelper is unavailable.
     * 
     * @return The currently active Quest, or null if none selected or not mappable
     */
    private static Quest getCurrentlyActiveQuest() {
        // First try using QuestHelper plugin if available
        QuestHelper selectedQuestHelper = getSelectedQuestHelper();
        if (selectedQuestHelper != null) {
            try {
                // Get the QuestHelperQuest from the selected QuestHelper
                net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest questHelperQuest = 
                    selectedQuestHelper.getQuest();
                
                if (questHelperQuest == null) {
                    log.debug("QuestHelper active but no QuestHelperQuest available: {}", 
                        selectedQuestHelper.getClass().getSimpleName());
                } else {
                    // Get the display name of the quest
                    String questName = questHelperQuest.getName();
                    log.debug("QuestHelper active: {} ({})", selectedQuestHelper.getClass().getSimpleName(), questName);
                    
                    // Try to find matching RuneLite Quest enum by name
                    for (Quest quest : Quest.values()) {
                        if (quest.getName().equalsIgnoreCase(questName) || 
                            quest.getName().replaceAll("_", " ").equalsIgnoreCase(questName)) {
                            log.debug("Mapped QuestHelper {} to RuneLite Quest {}", questName, quest.getName());
                            // Track this quest for updates
                            trackedQuest = quest;
                            return quest;
                        }
                    }
                    
                    log.debug("Could not map QuestHelper {} to any RuneLite Quest", questName);
                }
            } catch (Exception e) {
                log.debug("Error getting active quest from QuestHelper: {}", e.getMessage(), e);
            }
        }
        
        // If QuestHelper doesn't have a selected quest or we couldn't map it,
        // use the trackedQuest if we have one from previous detection
        if (trackedQuest != null) {
            // Check if the quest is still in progress
            QuestState state = loadQuestStateFromClient(trackedQuest);
            if (state == QuestState.IN_PROGRESS) {
                log.debug("Using previously tracked quest: {}", trackedQuest.getName());
                return trackedQuest;
            } else if (state == QuestState.FINISHED) {
                log.info("Previously tracked quest is now completed: {}", trackedQuest.getName());
                // Clear tracked quest as it's now complete
                trackedQuest = null;
            }
        }
        
        // As a fallback, look for any IN_PROGRESS quests in the client
        try {
            if (Microbot.getClient() != null) {
                for (Quest quest : Quest.values()) {
                    QuestState state = loadQuestStateFromClient(quest);
                    if (state == QuestState.IN_PROGRESS) {
                        log.debug("Found active quest from client state: {}", quest.getName());
                        // Set as tracked quest for future reference
                        trackedQuest = quest;
                        return quest;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking client quests: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Loads quest state from the client for a specific quest.
     * 
     * @param quest The quest to load state for
     * @return The QuestState for the quest
     */
    private static QuestState loadQuestStateFromClient(Quest quest) {
        try {
            if (Microbot.getClient() == null) {
                log.warn("Client is null when loading quest state for {}", quest);
                return QuestState.NOT_STARTED;
            }
            
            QuestState state = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                quest.getState(Microbot.getClient())).orElse(QuestState.NOT_STARTED);
            
            log.trace("Loaded quest state from client: {} = {}", quest, state);
            return state;
        } catch (Exception e) {
            log.error("Error loading quest state for {}: {}", quest, e.getMessage(), e);
            return QuestState.NOT_STARTED;
        }
    }
    
    /**
     * Gets quest state from the cache or loads it from the client.
     * 
     * @param quest The quest to retrieve state for
     * @return The QuestState
     */
    public static QuestState getQuestState(Quest quest) {
        return getInstance().get(quest, () -> loadQuestStateFromClient(quest));
    }
    
    /**
     * Gets quest state from the cache or loads it with a custom supplier.
     * 
     * @param quest The quest to retrieve state for
     * @param valueLoader Custom supplier for loading the quest state
     * @return The QuestState
     */
    public static QuestState getQuestState(Quest quest, Supplier<QuestState> valueLoader) {
        return getInstance().get(quest, valueLoader);
    }
    
    /**
     * Manually updates a quest state in the cache.
     * 
     * @param quest The quest to update
     * @param state The new quest state
     */
    public static void updateQuestState(Quest quest, QuestState state) {
        getInstance().put(quest, state);
        log.debug("Updated quest cache: {} = {}", quest, state);
    }
    
    /**
     * Checks if a quest is started (not NOT_STARTED).
     * 
     * @param quest The quest to check
     * @return true if the quest is started
     */
    public static boolean isQuestStarted(Quest quest) {
        return getQuestState(quest) != QuestState.NOT_STARTED;
    }
    
    /**
     * Checks if a quest is completed (FINISHED).
     * 
     * @param quest The quest to check
     * @return true if the quest is completed
     */
    public static boolean isQuestCompleted(Quest quest) {
        return getQuestState(quest) == QuestState.FINISHED;
    }
    
    /**
     * Checks if a quest is in progress (IN_PROGRESS).
     * 
     * @param quest The quest to check
     * @return true if the quest is in progress
     */
    public static boolean isQuestInProgress(Quest quest) {
        return getQuestState(quest) == QuestState.IN_PROGRESS;
    }
    
    /**
     * Updates all cached quests by retrieving fresh states from the game client.
     * This method iterates over all currently cached quests and refreshes their states.
     */
    public static void updateAllFromClient() {
        getInstance().update();
    }
    
    /**
     * Updates all cached data by retrieving fresh values from the game client.
     * Implements the abstract method from Rs2Cache.
     * 
     * Iterates over all currently cached quest keys and refreshes their states from the client.
     */
    @Override
    public void update() {
        log.debug("Updating all cached quests from client...");
        
        if (Microbot.getClient() == null) {
            log.warn("Cannot update quests - client is null");
            return;
        }
        
        int beforeSize = size();
        int updatedCount = 0;
        
        // Get all currently cached quest keys and update them
        java.util.Set<Quest> cachedQuests = entryStream()
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
        
        for (Quest quest : cachedQuests) {
            try {
                // Refresh the quest state from client using the private method
                QuestState freshState = loadQuestStateFromClient(quest);
                if (freshState != null) {
                    put(quest, freshState);
                    updatedCount++;
                    log.debug("Updated quest {} with fresh state: {}", quest.getName(), freshState);
                }
            } catch (Exception e) {
                log.warn("Failed to update quest {}: {}", quest.getName(), e.getMessage());
            }
        }
        
        log.info("Updated {} quests from client (cache had {} entries total)", 
                updatedCount, beforeSize);
    }
    
   
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     */    
        
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        try {
            getInstance().handleEvent(event);
            
            // Check for quest progression using tracked quest or find one
            Quest questToCheck = trackedQuest != null ? trackedQuest : getCurrentlyActiveQuest();
            if (questToCheck != null) {
                // Use clientThread to check and update state
                Microbot.getClientThread().invokeLater(() -> {
                    try {
                        if (Microbot.getClient() == null) {
                            return;
                        }
                        
                        QuestState oldState = getQuestState(questToCheck);
                        QuestState newState = questToCheck.getState(Microbot.getClient());
                        
                        if (oldState != newState) {
                            log.info("VarbitChanged - Quest state changed for {}: {} to {}", 
                                    questToCheck.getName(), oldState, newState);
                            updateQuestState(questToCheck, newState);
                            
                            // If quest is now complete, clear it from tracked quest
                            if (newState == QuestState.FINISHED && trackedQuest == questToCheck) {
                                log.info("VarbitChanged - Quest completed: {}", questToCheck.getName());
                                trackedQuest = null;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error checking quest state in VarbitChanged: {}", e.getMessage());
                    }
                });
            }
            
            // Since a varbit changed, check if we need to find a new active quest 
            // in case this varbit is related to a quest starting
            if (trackedQuest == null) {
                Microbot.getClientThread().invokeLater(() -> {
                    try {
                        for (Quest quest : Quest.values()) {
                            QuestState state = quest.getState(Microbot.getClient());
                            if (state == QuestState.IN_PROGRESS && getQuestState(quest) != state) {
                                log.info("VarbitChanged - Found newly started quest: {}", quest.getName());
                                updateQuestState(quest, state);
                                trackedQuest = quest;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error detecting quest changes in VarbitChanged: {}", e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            log.error("Error handling VarbitChanged event: {}", e.getMessage(), e);
        }
    }
    
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        try {
            if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
                return;
            }
            
            String message = chatMessage.getMessage();
            
            // Quest completion detection
            if (message.contains("Congratulations! Quest complete!") || 
                message.contains("you've completed a quest")) {
                log.info("Quest completion detected!");
                
                // If we have a tracked quest, check if it's complete
                if (trackedQuest != null) {
                    Microbot.getClientThread().invokeLater(() -> {
                        try {
                            QuestState state = trackedQuest.getState(Microbot.getClient());
                            if (state == QuestState.FINISHED) {
                                log.info("Tracked quest completed: {}", trackedQuest.getName());
                                updateQuestState(trackedQuest, QuestState.FINISHED);
                                trackedQuest = null;
                            }
                        } catch (Exception e) {
                            log.error("Error checking tracked quest completion: {}", e.getMessage());
                        }
                    });
                } else {
                    // If no tracked quest, update all from client to capture the completed one
                    log.info("No tracked quest when completion detected, updating all");
                    updateAllFromClient();
                }
            } 
            // Quest start detection
            else if (message.contains("You've started a new quest")) {
                log.info("Quest start detected!");
                
                try {
                    // Extract quest name from formatted message
                    final String questName = message.substring(message.indexOf(">") + 1)
                                                  .substring(0, message.substring(message.indexOf(">") + 1).indexOf("<"));
                    log.info("Started quest: {}", questName);
                    
                    // Find matching quest by name
                    for (Quest quest : Quest.values()) {
                        if (quest.getName().equalsIgnoreCase(questName) || 
                            quest.getName().replaceAll("_", " ").equalsIgnoreCase(questName)) {
                            
                            // Update state and set as tracked quest
                            Microbot.getClientThread().invokeLater(() -> {
                                try {
                                    QuestState state = quest.getState(Microbot.getClient());
                                    if (state == QuestState.IN_PROGRESS) {
                                        log.info("Confirmed quest started: {}", quest.getName());
                                        updateQuestState(quest, QuestState.IN_PROGRESS);
                                        trackedQuest = quest;
                                    }
                                } catch (Exception e) {
                                    log.error("Error updating started quest: {}", e.getMessage());
                                }
                            });
                            
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Error extracting quest name: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error handling ChatMessage event: {}", e.getMessage(), e);
        }
    }
    
    
    // ============================================
    // Legacy API Compatibility Methods
    // ============================================
    
    /**
     * Invalidates quest cache entry - Legacy compatibility method.
     * 
     * @param quest The quest to invalidate
     */
    public static void invalidateQuest(Quest quest) {
        getInstance().remove(quest);
    }
    
    /**
     * Resets the singleton instance. Used for testing.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
    
    // ============================================
    // CacheSerializable Implementation
    // ============================================
    
    @Override
    public String getConfigKey() {
        return "quests";
    }
    
    @Override
    public String getConfigGroup() {
        return "microbot";
    }
    
    @Override
    public boolean shouldPersist() {
        return true; // Quest states should be persisted for progress tracking
    }
}
