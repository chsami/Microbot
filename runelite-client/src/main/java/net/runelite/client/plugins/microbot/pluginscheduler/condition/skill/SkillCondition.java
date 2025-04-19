package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Abstract base class for skill-based conditions.
 */
@Getter 
@EqualsAndHashCode(callSuper = false)
public abstract class SkillCondition implements Condition {
    private static final int ICON_SIZE = 24; // Standard size for all skill icons
    protected final Skill skill;
    
    /**
     * Constructor requiring a skill to be set
     */
    protected SkillCondition(Skill skill) {
        this.skill = skill;
    }
    
    /**
     * Gets the skill associated with this condition
     */
    public Skill getSkill() {
        return skill;
    }
    
    /**
     * Checks if this condition is for the total of all skills
     */
    public boolean isTotal() {
        return skill == null || skill == Skill.OVERALL;
    }
    
    /**
     * Gets all skills to be considered for total calculations
     * Excludes TOTAL itself and other non-tracked skills
     */
    protected Skill[] getAllTrackableSkills() {
        return Skill.values();
    }
    
    /**
     * Gets a properly scaled icon for the skill (24x24 pixels)
     */
    public Icon getSkillIcon() {
        try {
            // Try to get the SkillIconManager from Microbot
            SkillIconManager iconManager = Microbot.getClientThread().runOnClientThreadOptional(
                                            ()-> {return Microbot.getInjector().getInstance(SkillIconManager.class);}).orElse(null);
            
            if (iconManager != null) {
                // Get the skill image (small=true for smaller version)
                BufferedImage skillImage;
                String skillName = isTotal() ? "overall" : skill.getName().toLowerCase();
                if (isTotal()) {                    
                    String skillIconPath = "/skill_icons/" + skillName + ".png";
                    // Use XpPanel.class since we know it successfully loads this resource
                    skillImage = ImageUtil.loadImageResource(getClass(), skillIconPath);                    
                } else {
                    skillImage = iconManager.getSkillImage(skill, true);
                }
                
                // Scale the image if needed
                if (skillImage.getWidth() != ICON_SIZE || skillImage.getHeight() != ICON_SIZE) {
                    skillImage = ImageUtil.resizeImage(skillImage, ICON_SIZE, ICON_SIZE);
                }
                
                return new ImageIcon(skillImage);
            }
            
            // Fall back to direct loading if SkillIconManager is unavailable
            return null;
        } catch (Exception e) {
            // Fall back to generic skill icon
            return null;
        }
    }
    
    /**
     * Reset condition - must be implemented by subclasses
     */
    @Override
    public void reset() {
        reset(false);
    }
    
    /**
     * Reset condition with option to randomize targets
     */
    public abstract void reset(boolean randomize);
}