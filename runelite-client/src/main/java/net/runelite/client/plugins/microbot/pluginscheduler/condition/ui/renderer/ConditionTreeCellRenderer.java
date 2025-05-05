package net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.renderer;

import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionManager;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.AreaCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.PositionCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.RegionCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ui.util.ConditionConfigPanelUtil;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;

import java.awt.Component;
import java.awt.Color;
import java.awt.Font;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.Icon;

// Condition tree cell renderer
public class ConditionTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final Color PLUGIN_CONDITION_COLOR = new Color(0, 128, 255); // Blue for plugin conditions
    private static final Color USER_CONDITION_COLOR = Color.WHITE; // White for user conditions
    private static final Color SATISFIED_COLOR = new Color(0, 180, 0); // Bright green for satisfied conditions
    private static final Color NOT_SATISFIED_COLOR = new Color(220, 60, 60); // Bright red for unsatisfied conditions
    private static final Color RELEVANT_CONDITION_COLOR = new Color(255, 215, 0); // Gold for relevant conditions
    private static final Color INACTIVE_CONDITION_COLOR = new Color(150, 150, 150); // Gray for inactive conditions
    
    private final ConditionManager conditionManager;
    private final boolean isStopConditionRenderer;
    private boolean isActive = true;
   
    public ConditionTreeCellRenderer(ConditionManager conditionManager, boolean isStopConditionRenderer) {
        this.conditionManager = conditionManager;
        this.isStopConditionRenderer = isStopConditionRenderer;
    }
    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
    }
   
    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, 
                                            boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        // Check if node is null first
        if (value == null) {
            setText("null");
            return this;
        }
        
        // Safely cast to DefaultMutableTreeNode
        if (!(value instanceof DefaultMutableTreeNode)) {
            return this;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        Object userObject = node.getUserObject();
        
        // Default styling
        setFont(getFont().deriveFont(Font.PLAIN));
        
       
        
        // Determine relevance:
        // - For start conditions: relevant when plugin is enabled but not started
        // - For stop conditions: relevant when plugin is running        
        boolean conditionsAreRelevant = isStopConditionRenderer ? isActive : !isActive;
        
                
        if (userObject instanceof LogicalCondition) {
            LogicalCondition logicalCondition = (LogicalCondition) userObject;
            
            // Show condition counts and progress percentage
            int total = logicalCondition.getTotalConditionCount();
            int met = logicalCondition.getMetConditionCount();
            double progress = logicalCondition.getProgressPercentage();
            
            // Check if this is a plugin-defined logical condition
            boolean isPluginDefined = conditionManager != null && 
                                        conditionManager.isPluginDefinedCondition(logicalCondition);
            
            // Determine icon based on logical type
            if (logicalCondition instanceof AndCondition) {
                setIcon(getConditionTypeIcon(logicalCondition));                
                setFont(getFont().deriveFont(isPluginDefined ? Font.BOLD | Font.ITALIC : Font.BOLD));
            } else if (logicalCondition instanceof OrCondition) {
                setIcon(getConditionTypeIcon(logicalCondition));                                
                setFont(getFont().deriveFont(isPluginDefined ? Font.BOLD | Font.ITALIC : Font.BOLD));
            } else {
                // Use appropriate icon based on condition type
                setIcon(getConditionTypeIcon(logicalCondition));
            }
            
            // Color based on condition status and relevance
            if (!conditionsAreRelevant) {
                // If conditions aren't relevant, show in gray
                setForeground(INACTIVE_CONDITION_COLOR);
            } else if (logicalCondition.isSatisfied()) {
                setForeground(SATISFIED_COLOR);  // Green for satisfied conditions
            } else {
                setForeground(NOT_SATISFIED_COLOR);  // Red for unsatisfied conditions
            }
            
            // Show progress info with more detailed formatting
            String text = logicalCondition.getDescription();
            if (total > 0) {
                text += String.format(" [%d/%d met, %.0f%%]", met, total, progress);
            }
            
            if (isPluginDefined) {
                text = "📌 " + text;
            }
            
            // Add a visual indicator for relevance
            if (conditionsAreRelevant) {
                text = "⚡ " + text;
            }
            
            setText(text);
            
            // For tooltips, use the new HTML formatting
            setToolTipText(logicalCondition.getHtmlDescription(200));
        } else if (userObject instanceof NotCondition) {
            NotCondition notCondition = (NotCondition) userObject;
            setIcon(ConditionConfigPanelUtil.getResourceIcon("not-equal.png"));
            
            // Adjust color based on relevance
            if (!conditionsAreRelevant) {
                setForeground(INACTIVE_CONDITION_COLOR);
            } else {
                setForeground(new Color(210, 40, 40));  // Red for NOT
            }
            
            String text = notCondition.getDescription();
            if (conditionsAreRelevant) {
                text = "⚡ " + text;
            }
            setText(text);
        } else if (userObject instanceof Condition) {
            Condition condition = (Condition) userObject;
            
            // Show progress for the condition
            String text = condition.getDescription();
            double progress = condition.getProgressPercentage();
            
            if (progress > 0 && progress < 100) {
                text += String.format(" (%.0f%%)", progress);
            }
            
            // Use appropriate icon based on condition type
            setIcon(getConditionTypeIcon(condition));
            
            // Color based on condition status and relevance
            if (!conditionsAreRelevant) {
                // If conditions aren't relevant, show in gray
                setForeground(INACTIVE_CONDITION_COLOR);
            } else if (condition.isSatisfied()) {
                setForeground(SATISFIED_COLOR);  // Green for satisfied conditions
            } else {
                setForeground(NOT_SATISFIED_COLOR);  // Red for unsatisfied conditions
            }
            
            // Visual indicator for plugin-defined conditions            
            if (conditionManager != null && conditionManager.isPluginDefinedCondition(condition)) {
                setFont(getFont().deriveFont(Font.ITALIC));
                text = "📌 " + text;
            }
            
            // Add a visual indicator for relevance
            if (conditionsAreRelevant) {
                text = "⚡ " + text;
            }
            
            setText(text);
            
            // Enhanced tooltip with status information
            StringBuilder tooltip = new StringBuilder();
            tooltip.append("<html><b>").append(condition.getDescription()).append("</b><br>");
            tooltip.append(condition.isSatisfied() ? 
                "<font color='green'>Satisfied</font>" : 
                "<font color='red'>Not satisfied</font>");
                
            if (condition instanceof TimeCondition) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                ZonedDateTime triggerTime = ((TimeCondition)condition).getCurrentTriggerTime().orElse(null);
                if (triggerTime != null) {
                    tooltip.append("<br>Trigger time: ").append(triggerTime.format(formatter));
                }                               
            }
            
            tooltip.append("</html>");
            setToolTipText(tooltip.toString());
        } else if (userObject instanceof String) {
            // Section headers (Plugin/User Conditions)
            setFont(getFont().deriveFont(Font.BOLD));
            setIcon(null);
            setForeground(Color.YELLOW);
        }
        
        // If selected, keep our custom foreground color but change background
        if (sel) {
            // Keep the foreground color we've set above, but use a selection background that works with it
            setBackground(new Color(60, 60, 60)); // Dark gray selection background
            setBorderSelectionColor(new Color(100, 100, 100)); // Darker border for selection
        }
        
        return this;
    }

    /**
     * Gets an appropriate icon for the condition type
     */
    private Icon getConditionTypeIcon(Condition condition) {
        if (condition instanceof SkillCondition) {
            SkillCondition skillCondition = (SkillCondition) condition;
            Icon skillIcon = skillCondition.getSkillIcon();
            
            if (skillIcon != null) {
                // Ensure icon is properly sized to 24x24
                if (skillIcon instanceof ImageIcon) {
                    ImageIcon imageIcon = (ImageIcon) skillIcon;
                    if (imageIcon.getIconWidth() != 24 || imageIcon.getIconHeight() != 24) {
                        // Rescale if not already the right size
                        return ConditionConfigPanelUtil.getResourceIcon("skill_icon.png");
                    }
                }
                return skillIcon;
            }
            return ConditionConfigPanelUtil.getResourceIcon("skill_icon.png");
        } else if (condition instanceof TimeCondition) {
            if (condition instanceof IntervalCondition) {
                return ConditionConfigPanelUtil.getResourceIcon("clock.png");
            }else if (condition instanceof TimeWindowCondition) {
                return ConditionConfigPanelUtil.getResourceIcon("calendar-icon.png");
            }
            return ConditionConfigPanelUtil.getResourceIcon("clock.png");                    
        } else if (condition instanceof TimeWindowCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("calendar_icon.png");
        } else if (condition instanceof DayOfWeekCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("day_icon.png");
        } else if (condition instanceof AndCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("logic-gate-and.png");
        } else if (condition instanceof OrCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("logic-gate-or.png");
        }else if (condition instanceof LootItemCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("loot_icon.png");
        }else if (condition instanceof AreaCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("area_map.png");
        }else if (condition instanceof RegionCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("region.png"); 
        }else if (condition instanceof PositionCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("position.png");
        }else if (condition instanceof LockCondition) {
            return ConditionConfigPanelUtil.getResourceIcon("padlock.png");   
        }
        
        return UIManager.getIcon("Tree.leafIcon");
    }
}
