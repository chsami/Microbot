package net.runelite.client.plugins.microbot.pluginscheduler.ui;

import javax.swing.ImageIcon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.NotCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillLevelCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillXpCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.type.ScheduledPlugin;
import net.runelite.client.ui.ColorScheme;
import java.awt.Component;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.Icon;

// Condition tree cell renderer
public class ConditionTreeCellRenderer extends DefaultTreeCellRenderer {

    private static final Color PLUGIN_CONDITION_COLOR = new Color(0, 128, 255); // Blue for plugin conditions
    private static final Color USER_CONDITION_COLOR = Color.WHITE; // White for user conditions
    
    private final SchedulerPlugin schedulerPlugin;
    private ScheduledPlugin scheduledPlugin;
        // Icons for tree nodes
    private ImageIcon andIcon;
    private ImageIcon orIcon;
    private ImageIcon notIcon;
    private ImageIcon timeIcon;
    private ImageIcon skillIcon;
    private ImageIcon itemIcon;


    public ConditionTreeCellRenderer(SchedulerPlugin schedulerPlugin) {
        this.schedulerPlugin = schedulerPlugin;
          // Load icons (you should create these resources)
          try {
            andIcon = new ImageIcon(getClass().getResource("/img/and.png"));
            orIcon = new ImageIcon(getClass().getResource("/img/or.png"));
            notIcon = new ImageIcon(getClass().getResource("/img/not.png"));
            timeIcon = new ImageIcon(getClass().getResource("/img/time.png"));
            skillIcon = new ImageIcon(getClass().getResource("/img/skill.png"));
            itemIcon = new ImageIcon(getClass().getResource("/img/item.png"));
        } catch (Exception e) {
             // Fallback - use text labels if icons aren't available
            System.err.println("Failed to load condition icons: " + e.getMessage());
            
            // All icons will remain null, and the renderer will use text-only labels
            // You could optionally create simple text-based icons:
            andIcon = createTextIcon("AND");
            orIcon = createTextIcon("OR");
            notIcon = createTextIcon("NOT");
            timeIcon = createTextIcon("TIME");
            skillIcon = createTextIcon("SKILL");
            itemIcon = createTextIcon("ITEM");
        }
    }
    /**
     * Sets the scheduled plugin for this renderer
     */
    public void setScheduledPlugin(ScheduledPlugin scheduledPlugin) {
        this.scheduledPlugin = scheduledPlugin;
    }
    /**
     * Creates a simple text-based icon as a fallback when image resources aren't available
     */
    private ImageIcon createTextIcon(String text) {
        java.awt.Font font = new java.awt.Font("SansSerif", java.awt.Font.BOLD, 9);
        java.awt.FontMetrics metrics = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                .createGraphics().getFontMetrics(font);
        int width = metrics.stringWidth(text) + 4;
        int height = metrics.getHeight() + 4;
        
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(width, height, 
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, 
                java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setColor(java.awt.Color.BLACK);
        g2d.setFont(font);
        g2d.drawString(text, 2, metrics.getAscent() + 1);
        g2d.dispose();
        
        return new ImageIcon(image);
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
        
        if (userObject instanceof LogicalCondition) {
            LogicalCondition logicalCondition = (LogicalCondition) userObject;
            
            // Show condition counts and progress percentage
            int total = logicalCondition.getTotalConditionCount();
            int met = logicalCondition.getMetConditionCount();
            double progress = logicalCondition.getProgressPercentage();
            
            // Determine icon based on logical type
            if (logicalCondition instanceof AndCondition) {
                setIcon(getResourceIcon("and_icon.png"));
                setForeground(new Color(230, 140, 0));  // Orange for AND
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (logicalCondition instanceof OrCondition) {
                setIcon(getResourceIcon("or_icon.png"));
                setForeground(new Color(0, 140, 210));  // Blue for OR
                setFont(getFont().deriveFont(Font.BOLD));
            }
            
            // Show progress info
            String text = logicalCondition.getDescription();
            if (total > 0) {
                text += String.format(" [%d/%d met, %.0f%%]", met, total, progress);
            }
            
            setText(text);
        } else if (userObject instanceof NotCondition) {
            NotCondition notCondition = (NotCondition) userObject;
            setIcon(getResourceIcon("not_icon.png"));
            setForeground(new Color(210, 40, 40));  // Red for NOT
            setText(notCondition.getDescription());
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
            
            // Color based on condition status
            if (condition.isMet()) {
                setForeground(new Color(0, 150, 0));  // Green for met conditions
            } else {
                setForeground(Color.WHITE);
            }
            
            setText(text);
            
            // Visual indicator for plugin-defined conditions            
            if (scheduledPlugin !=null && scheduledPlugin.getStopConditionManager().isPluginDefinedCondition(condition)) {
                setFont(getFont().deriveFont(Font.ITALIC));
                text = "📌 " + text;
                setText(text);
            }
        } else if (userObject instanceof String) {
            // Section headers (Plugin/User Conditions)
            setFont(getFont().deriveFont(Font.BOLD));
            setIcon(null);
            setForeground(Color.YELLOW);
        }
        
        return this;
    }

    /**
     * Gets an appropriate icon for the condition type
     */
    private Icon getConditionTypeIcon(Condition condition) {
        if (condition instanceof IntervalCondition) {
            return getResourceIcon("time_icon.png");
        } else if (condition instanceof SkillLevelCondition || condition instanceof SkillXpCondition) {
            return getResourceIcon("skill_icon.png");
        } else if (condition instanceof LootItemCondition) {
            return getResourceIcon("item_icon.png");
        } else if (condition instanceof TimeWindowCondition) {
            return getResourceIcon("calendar_icon.png");
        } else if (condition instanceof DayOfWeekCondition) {
            return getResourceIcon("day_icon.png");
        }
        
        return UIManager.getIcon("Tree.leafIcon");
    }

    /**
     * Loads an icon from resources
     */
    private Icon getResourceIcon(String name) {
        try {
            BufferedImage image = ImageIO.read(getClass().getResource("/net/runelite/client/plugins/microbot/icons/" + name));
            return new ImageIcon(image);
        } catch (Exception e) {
            // Fallback to default icon
            return UIManager.getIcon("Tree.leafIcon");
        }
    }
}
