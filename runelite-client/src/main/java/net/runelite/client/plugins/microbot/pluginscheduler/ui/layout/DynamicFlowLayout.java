package net.runelite.client.plugins.microbot.pluginscheduler.ui.layout;

import java.awt.*;
import javax.swing.JComponent;

/**
 * A custom FlowLayout that provides better control over wrapping, spacing, and dynamic sizing.
 * This layout automatically adjusts component arrangements based on available space and content.
 * 
 * Features:
 * - Intelligent wrapping when space is limited
 * - Dynamic spacing based on container size
 * - Better minimum size calculations
 * - Content-aware layout decisions
 * 
 * @author Vox
 */
public class DynamicFlowLayout extends FlowLayout {
    
    private int minRowHeight = 120;
    private int preferredRowHeight = 140;
    private boolean adaptiveSpacing = true;
    
    /**
     * Creates a new DynamicFlowLayout with default settings
     */
    public DynamicFlowLayout() {
        super(FlowLayout.CENTER, 8, 5);
    }
    
    /**
     * Creates a new DynamicFlowLayout with specified alignment and spacing
     * 
     * @param align the alignment value
     * @param hgap the horizontal gap between components
     * @param vgap the vertical gap between components
     */
    public DynamicFlowLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }
    
    /**
     * Sets the minimum row height for components
     * 
     * @param minRowHeight minimum height in pixels
     */
    public void setMinRowHeight(int minRowHeight) {
        this.minRowHeight = minRowHeight;
    }
    
    /**
     * Sets the preferred row height for components
     * 
     * @param preferredRowHeight preferred height in pixels
     */
    public void setPreferredRowHeight(int preferredRowHeight) {
        this.preferredRowHeight = preferredRowHeight;
    }
    
    /**
     * Enables or disables adaptive spacing based on container size
     * 
     * @param adaptiveSpacing true to enable adaptive spacing
     */
    public void setAdaptiveSpacing(boolean adaptiveSpacing) {
        this.adaptiveSpacing = adaptiveSpacing;
    }
    
    @Override
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            int nmembers = target.getComponentCount();
            boolean firstVisibleComponent = true;
            
            int maxWidth = 0;
            int currentRowWidth = 0;
            int currentRowHeight = 0;
            int totalHeight = 0;
            
            // Calculate container width for wrapping decisions
            int containerWidth = target.getWidth();
            if (containerWidth <= 0) {
                containerWidth = Integer.MAX_VALUE; // No wrapping if width unknown
            }
            
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();
                    
                    // Check if we need to wrap to next row
                    int neededWidth = currentRowWidth + (firstVisibleComponent ? 0 : getHgap()) + d.width;
                    
                    if (!firstVisibleComponent && neededWidth > containerWidth - getHgap() * 2) {
                        // Wrap to next row
                        maxWidth = Math.max(maxWidth, currentRowWidth);
                        totalHeight += currentRowHeight + getVgap();
                        currentRowWidth = d.width;
                        currentRowHeight = d.height;
                    } else {
                        // Add to current row
                        if (!firstVisibleComponent) {
                            currentRowWidth += getHgap();
                        }
                        currentRowWidth += d.width;
                        currentRowHeight = Math.max(currentRowHeight, d.height);
                        firstVisibleComponent = false;
                    }
                }
            }
            
            // Add the last row
            if (currentRowWidth > 0) {
                maxWidth = Math.max(maxWidth, currentRowWidth);
                totalHeight += currentRowHeight;
            }
            
            Insets insets = target.getInsets();
            dim.width = maxWidth + insets.left + insets.right + getHgap() * 2;
            dim.height = Math.max(totalHeight + insets.top + insets.bottom + getVgap() * 2, 
                                 preferredRowHeight);
            
            return dim;
        }
    }
    
    @Override
    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            int nmembers = target.getComponentCount();
            
            if (nmembers > 0) {
                // Find the widest component for minimum width
                int maxComponentWidth = 0;
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = m.getMinimumSize();
                        maxComponentWidth = Math.max(maxComponentWidth, d.width);
                    }
                }
                
                Insets insets = target.getInsets();
                dim.width = maxComponentWidth + insets.left + insets.right + getHgap() * 2;
                dim.height = minRowHeight + insets.top + insets.bottom + getVgap() * 2;
            }
            
            return dim;
        }
    }
    
    @Override
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();
            int maxwidth = target.getWidth() - (insets.left + insets.right + getHgap() * 2);
            int nmembers = target.getComponentCount();
            int x = 0, y = insets.top + getVgap();
            int rowh = 0, start = 0;
            
            boolean ltr = target.getComponentOrientation().isLeftToRight();
            
            // Adaptive spacing based on container width
            int effectiveHgap = getHgap();
            if (adaptiveSpacing && maxwidth > 0) {
                int totalComponentWidth = 0;
                int visibleComponents = 0;
                
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        totalComponentWidth += m.getPreferredSize().width;
                        visibleComponents++;
                    }
                }
                
                if (visibleComponents > 1) {
                    int availableSpaceForGaps = maxwidth - totalComponentWidth;
                    int optimalGap = Math.max(5, availableSpaceForGaps / (visibleComponents + 1));
                    effectiveHgap = Math.min(effectiveHgap, optimalGap);
                }
            }
            
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();
                    m.setSize(d.width, d.height);
                    
                    if ((x == 0) || ((x + d.width) <= maxwidth)) {
                        if (x > 0) {
                            x += effectiveHgap;
                        }
                        x += d.width;
                        rowh = Math.max(rowh, d.height);
                    } else {
                        rowh = moveComponents(target, insets.left + effectiveHgap, y, 
                                            maxwidth - x, rowh, start, i, ltr);
                        x = d.width;
                        y += getVgap() + rowh;
                        rowh = d.height;
                        start = i;
                    }
                }
            }
            
            moveComponents(target, insets.left + effectiveHgap, y, maxwidth - x, 
                          rowh, start, nmembers, ltr);
        }
    }
    
    /**
     * Centers the elements in the specified row, if there is any slack.
     * 
     * @param target the component which needs to be moved
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width dimensions
     * @param height the height dimensions
     * @param rowStart the beginning of the row
     * @param rowEnd the the ending of the row
     * @param useBaseline Whether or not to align on baseline.
     * @param ascent Ascent for the components. This is only valid if useBaseline is true.
     * @param descent Ascent for the components. This is only valid if useBaseline is true.
     * @return actual row height
     */
    private int moveComponents(Container target, int x, int y, int width, int height,
                              int rowStart, int rowEnd, boolean ltr) {
        switch (getAlignment()) {
        case LEFT:
            x += ltr ? 0 : width;
            break;
        case CENTER:
            x += width / 2;
            break;
        case RIGHT:
            x += ltr ? width : 0;
            break;
        case LEADING:
            break;
        case TRAILING:
            x += width;
            break;
        }
        
        int maxAscent = 0;
        int maxDescent = 0;
        boolean useBaseline = getAlignOnBaseline();
        int[] ascent = null;
        int[] descent = null;
        
        if (useBaseline) {
            ascent = new int[rowEnd - rowStart];
            descent = new int[rowEnd - rowStart];
            for (int i = rowStart; i < rowEnd; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    if (m instanceof JComponent) {
                        JComponent jc = (JComponent) m;
                        int baseline = jc.getBaseline(m.getWidth(), m.getHeight());
                        if (baseline >= 0) {
                            ascent[i - rowStart] = baseline;
                            descent[i - rowStart] = m.getHeight() - baseline;
                        } else {
                            ascent[i - rowStart] = -1;
                        }
                    } else {
                        ascent[i - rowStart] = -1;
                    }
                    if (ascent[i - rowStart] >= 0) {
                        maxAscent = Math.max(maxAscent, ascent[i - rowStart]);
                        maxDescent = Math.max(maxDescent, descent[i - rowStart]);
                    }
                }
            }
            height = Math.max(maxAscent + maxDescent, height);
        }
        
        for (int i = rowStart; i < rowEnd; i++) {
            Component m = target.getComponent(i);
            if (m.isVisible()) {
                int cy;
                if (useBaseline && ascent != null && ascent[i - rowStart] >= 0) {
                    cy = y + maxAscent - ascent[i - rowStart];
                } else {
                    cy = y + (height - m.getHeight()) / 2;
                }
                if (ltr) {
                    m.setLocation(x, cy);
                } else {
                    m.setLocation(target.getWidth() - x - m.getWidth(), cy);
                }
                x += m.getWidth() + getHgap();
            }
        }
        return height;
    }
}
