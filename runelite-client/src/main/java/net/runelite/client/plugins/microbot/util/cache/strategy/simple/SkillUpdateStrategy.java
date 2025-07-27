package net.runelite.client.plugins.microbot.util.cache.strategy.simple;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.model.SkillData;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;

/**
 * Cache update strategy for skill data.
 * Handles StatChanged events to update skill information with enhanced data
 * including temporal tracking and change detection.
 */
@Slf4j
public class SkillUpdateStrategy implements CacheUpdateStrategy<Skill, SkillData> {
    
    @Override
    public void handleEvent(Object event, CacheOperations<Skill, SkillData> cache) {
        if (event instanceof StatChanged) {
            handleStatChanged((StatChanged) event, cache);
        }
    }
    
    private void handleStatChanged(StatChanged event, CacheOperations<Skill, SkillData> cache) {
        try {
            Skill skill = event.getSkill();
            if (skill != null && Microbot.getClient() != null) {
                
                // Get current skill data from client
                int level = Microbot.getClient().getRealSkillLevel(skill);
                int boostedLevel = Microbot.getClient().getBoostedSkillLevel(skill);
                int experience = Microbot.getClient().getSkillExperience(skill);
                
                // Get existing data to preserve previous values
                SkillData existingData = cache.get(skill);
                
                // Create new skill data with temporal and change tracking
                SkillData skillData;
                if (existingData != null) {
                    // Use withUpdate to preserve previous values
                    skillData = existingData.withUpdate(level, boostedLevel, experience);
                } else {
                    // No previous data available
                    skillData = new SkillData(level, boostedLevel, experience);
                }
                
                cache.put(skill, skillData);
                
                // Log level ups and significant experience gains
                if (skillData.isLevelUp()) {
                    log.debug("Level up detected: {} leveled from {} to {}", skill, skillData.getPreviousLevel(), level);
                }
                if (skillData.getExperienceGained() > 0) {
                    log.debug("\n\tUpdated skill cache: {} (level: {}, boosted: {}, exp: {}, gained: {} exp)", 
                             skill, level, boostedLevel, experience, skillData.getExperienceGained());
                } else {
                    log.trace("Updated skill cache: {} (level: {}, boosted: {}, exp: {})", 
                             skill, level, boostedLevel, experience);
                }
            }
        } catch (Exception e) {
            log.error("Error handling StatChanged event: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{StatChanged.class};
    }
    
    @Override
    public void onAttach(CacheOperations<Skill, SkillData> cache) {
        log.debug("SkillUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(CacheOperations<Skill, SkillData> cache) {
        log.debug("SkillUpdateStrategy detached from cache");
    }
}
