package net.runelite.client.plugins.microbot.pluginscheduler.ui.util;

import net.runelite.client.plugins.microbot.pluginscheduler.ui.layout.DynamicFlowLayout;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Utility class for creating dynamic, responsive UI panels and components.
 * Provides static methods for creating adaptive layouts and components that
 * automatically adjust to content length and window size.
 * 
 * Features:
 * - Dynamic section creation with adaptive sizing
 * - Text-aware component sizing
 * - Responsive layouts with automatic wrapping
 * - Consistent styling across components
 * 
 * @author Vox
 */
public class UIUtils {
    
    // Default dimensions - further reduced heights for more compact layout
    private static final Dimension DEFAULT_SECTION_MIN_SIZE = new Dimension(100, 60);
    private static final Dimension DEFAULT_SECTION_PREF_SIZE = new Dimension(120, 70);
    private static final int DEFAULT_HORIZONTAL_GAP = 6;
    
    /**
     * Creates a dynamic plugin information panel that adapts to content and window size
     * 
     * @param title the title for the panel
     * @param sections array of section panels to include
     * @param bottomPanel optional bottom panel (can be null)
     * @return configured wrapper panel with scrolling and dynamic sizing
     */
    public static JPanel createDynamicInfoPanel(String title, JPanel[] sections, JPanel bottomPanel) {
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(2, 2, 2, 2) // Reduced from 5,5,5,5 for tighter spacing
            ),
            title,
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            FontManager.getRunescapeBoldFont(),
            Color.WHITE
        ));
        wrapperPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Create main content panel with dynamic layout
        JPanel contentPanel = new JPanel();
        DynamicFlowLayout layout = new DynamicFlowLayout();
        layout.setMinRowHeight(60);  // Further reduced from 80 to match smaller sections
        layout.setPreferredRowHeight(70);  // Further reduced from 90 to match smaller sections
        layout.setAdaptiveSpacing(true);
        contentPanel.setLayout(layout);
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Add sections to content panel
        if (sections != null) {
            for (JPanel section : sections) {
                if (section != null) {
                    contentPanel.add(section);
                }
            }
        }

        // Create main container with proper layout
        JPanel mainContainer = new JPanel(new BorderLayout());
        mainContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        mainContainer.add(contentPanel, BorderLayout.CENTER);
        
        if (bottomPanel != null) {
            mainContainer.add(bottomPanel, BorderLayout.SOUTH);
        }

        // Wrap in scroll pane with both scrollbars as needed
        JScrollPane scrollPane = new JScrollPane(mainContainer);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Add component listener for dynamic resizing
        wrapperPanel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    adjustSectionSizes(contentPanel);
                    contentPanel.revalidate();
                    contentPanel.repaint();
                });
            }
        });

        wrapperPanel.add(scrollPane, BorderLayout.CENTER);
        return wrapperPanel;
    }
    
    /**
     * Creates an adaptive section panel that resizes based on content length
     * 
     * @param title the title for the section
     * @return configured section panel
     */
    public static JPanel createAdaptiveSection(String title) {
        return createAdaptiveSection(title, DEFAULT_SECTION_MIN_SIZE, DEFAULT_SECTION_PREF_SIZE);
    }
    
    /**
     * Creates an adaptive section panel with custom dimensions
     * 
     * @param title the title for the section
     * @param minSize minimum size for the section
     * @param prefSize preferred size for the section
     * @return configured section panel
     */
    public static JPanel createAdaptiveSection(String title, Dimension minSize, Dimension prefSize) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(2, 2, 2, 2) // Further reduced padding from 4,4,4,4
            ),
            title,
            TitledBorder.CENTER,
            TitledBorder.TOP,
            FontManager.getRunescapeSmallFont(),
            Color.LIGHT_GRAY
        ));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Set sizing constraints - allow the section to size to its content
        panel.setMinimumSize(minSize);
        // Don't enforce preferred size, let it size naturally to content
        
        return panel;
    }
    
    /**
     * Creates an adaptive label that adjusts its size based on content
     * 
     * @param text the label text
     * @return configured label
     */
    public static JLabel createAdaptiveLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }
    
    /**
     * Creates an adaptive value label with HTML support for text wrapping
     * 
     * @param text the label text
     * @return configured label with HTML support
     */
    public static JLabel createAdaptiveValueLabel(String text) {
        JLabel label = new JLabel("<html>" + text + "</html>");
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setVerticalAlignment(SwingConstants.TOP);
        return label;
    }
    
    /**
     * Creates an adaptive text area for multi-line content
     * 
     * @param text initial text content
     * @return configured text area
     */
    public static JTextArea createAdaptiveTextArea(String text) {
        JTextArea textArea = new JTextArea(text);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        textArea.setForeground(Color.WHITE);
        textArea.setFont(FontManager.getRunescapeSmallFont());
        textArea.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1)); // Reduced from 2,2,2,2
        textArea.setRows(1); // Reduced from 2 rows to 1 for more compact display
        return textArea;
    }
    
    /**
     * Creates a compact value label with smaller font
     * 
     * @param text the initial text for the label
     * @return configured compact value label
     */
    public static JLabel createCompactValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }
    
    /**
     * Creates a standard value label with regular font
     * 
     * @param text the initial text for the label
     * @return configured value label
     */
    public static JLabel createValueLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Color.WHITE);
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setFont(FontManager.getRunescapeFont());
        return label;
    }
    
    /**
     * Creates an info panel with titled border for status displays
     * 
     * @param title the title for the panel border
     * @return configured info panel with GridBagLayout
     */
    public static JPanel createInfoPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 1, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            ),
            title,
            TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION,
            FontManager.getRunescapeBoldFont(),
            Color.WHITE
        ));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        return panel;
    }
    
    /**
     * Creates GridBagConstraints for label-value layouts
     * 
     * @param x grid x position (0 for labels, 1 for values)
     * @param y grid y position
     * @return configured GridBagConstraints
     */
    public static GridBagConstraints createGbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = 1;
        gbc.gridheight = 1;
        gbc.weightx = (x == 0) ? 0.0 : 1.0; // Labels (x=0) don't expand, values (x=1) do
        gbc.weighty = 0.0; // Changed to 0.0 to prevent vertical compression
        gbc.anchor = (x == 0) ? GridBagConstraints.WEST : GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 4, 8, 4); // Increased vertical spacing
        return gbc;
    }
    
    /**
     * Creates a label-value row with proper alignment
     * 
     * @param labelText the text for the label
     * @param valueLabel the value component
     * @return configured row panel
     */
    public static JPanel createLabelValueRow(String labelText, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.WHITE);
        label.setFont(FontManager.getRunescapeSmallFont());
        
        row.add(label, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        
        return row;
    }
    
    /**
     * Adds content to a section using standard GridBagLayout configuration
     * 
     * @param section the section panel to add content to
     * @param rows array of LabelValuePair objects representing the rows
     */
    public static void addContentToSection(JPanel section, LabelValuePair[] rows) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(1, 1, 1, 1); // Further reduced from 2,2,2,2 for tighter spacing
        gbc.anchor = GridBagConstraints.WEST;
        
        for (int i = 0; i < rows.length; i++) {
            LabelValuePair row = rows[i];
            
            // Label column
            gbc.gridx = 0; 
            gbc.gridy = i; 
            gbc.weightx = 0.3;
            gbc.fill = GridBagConstraints.NONE;
            section.add(createAdaptiveLabel(row.getLabel()), gbc);
            
            // Value column
            gbc.gridx = 1; 
            gbc.weightx = 0.7; 
            gbc.fill = GridBagConstraints.HORIZONTAL;
            section.add(row.getValueComponent(), gbc);
        }
        
        // No vertical glue - let the section size naturally to its content
    }
    
    /**
     * Creates a dynamic bottom panel for progress bars and status information
     * 
     * @param progressBar the progress bar component (can be null)
     * @param statusTextArea the status text area component (can be null)
     * @return configured bottom panel
     */
    public static JPanel createDynamicBottomPanel(JProgressBar progressBar, JTextArea statusTextArea) {
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        bottomPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        bottomPanel.setBorder(new EmptyBorder(0, 0, 0, 0)); // Removed all padding for tighter spacing

        if (progressBar != null) {
            // Progress panel
            JPanel progressPanel = new JPanel(new BorderLayout());
            progressPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            progressPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

            JLabel progressLabel = new JLabel("Progress:");
            progressLabel.setForeground(Color.WHITE);
            progressLabel.setFont(FontManager.getRunescapeSmallFont());

            progressPanel.add(progressLabel, BorderLayout.WEST);
            progressPanel.add(Box.createHorizontalStrut(5), BorderLayout.CENTER);
            progressPanel.add(progressBar, BorderLayout.CENTER);
            
            bottomPanel.add(progressPanel);
            bottomPanel.add(Box.createVerticalStrut(1)); // Further reduced spacing from 2 to 1
        }

        if (statusTextArea != null) {
            // Status panel
            JPanel statusPanel = new JPanel(new BorderLayout());
            statusPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            statusPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

            JLabel statusLabel = new JLabel("Status:");
            statusLabel.setForeground(Color.WHITE);
            statusLabel.setFont(FontManager.getRunescapeSmallFont());

            statusPanel.add(statusLabel, BorderLayout.NORTH);
            statusPanel.add(Box.createVerticalStrut(3), BorderLayout.CENTER);
            statusPanel.add(statusTextArea, BorderLayout.CENTER);
            
            bottomPanel.add(statusPanel);
        }

        return bottomPanel;
    }
    
    /**
     * Dynamically adjusts section sizes based on content and available space
     * 
     * @param contentPanel the panel containing the sections
     */
    public static void adjustSectionSizes(JPanel contentPanel) {
        if (contentPanel.getComponentCount() == 0) return;

        int availableWidth = contentPanel.getWidth();
        if (availableWidth <= 0) return;

        int minSectionWidth = 140;
        int componentCount = contentPanel.getComponentCount();
        int totalMinWidth = minSectionWidth * componentCount + (DEFAULT_HORIZONTAL_GAP * (componentCount + 1));

        if (availableWidth >= totalMinWidth) {
            // Enough space for all sections horizontally
            int sectionWidth = Math.max(minSectionWidth, (availableWidth - (DEFAULT_HORIZONTAL_GAP * (componentCount + 1))) / componentCount);
            
            for (int i = 0; i < componentCount; i++) {
                Component comp = contentPanel.getComponent(i);
                if (comp instanceof JPanel) {
                    JPanel section = (JPanel) comp;
                    // Calculate content-based height
                    int contentHeight = calculateOptimalSectionHeight(section);
                    Dimension sectionSize = new Dimension(sectionWidth, contentHeight);
                    section.setPreferredSize(sectionSize);
                    
                    // Adjust content based on component's content
                    adjustSectionContentSize(section);
                }
            }
        } else {
            // Not enough space, use minimum widths and let layout handle wrapping
            for (int i = 0; i < componentCount; i++) {
                Component comp = contentPanel.getComponent(i);
                if (comp instanceof JPanel) {
                    JPanel section = (JPanel) comp;
                    // Calculate content-based height
                    int contentHeight = calculateOptimalSectionHeight(section);
                    Dimension sectionSize = new Dimension(minSectionWidth, contentHeight);
                    section.setPreferredSize(sectionSize);
                    
                    adjustSectionContentSize(section);
                }
            }
        }
    }
    
    /**
     * Adjusts individual section size based on its content length
     * 
     * @param section the section panel to adjust
     */
    private static void adjustSectionContentSize(JPanel section) {
        // Find labels in the section and check their text length
        Component[] components = section.getComponents();
        int maxTextLength = 0;
        
        for (Component comp : components) {
            if (comp instanceof JLabel) {
                JLabel label = (JLabel) comp;
                String text = label.getText();
                if (text != null) {
                    // Remove HTML tags for length calculation
                    String plainText = text.replaceAll("<[^>]*>", "");
                    maxTextLength = Math.max(maxTextLength, plainText.length());
                }
            }
        }

        Dimension currentSize = section.getPreferredSize();
        int newWidth = currentSize.width;

        // Adjust width based on longest text length
        if (maxTextLength > 25) {
            newWidth = Math.max(currentSize.width, 200);
        } else if (maxTextLength > 20) {
            newWidth = Math.max(currentSize.width, 180);
        } else if (maxTextLength > 15) {
            newWidth = Math.max(currentSize.width, 160);
        } else {
            newWidth = Math.max(140, currentSize.width);
        }

        section.setPreferredSize(new Dimension(newWidth, currentSize.height));
    }
    
    /**
     * Calculates the optimal height for a section based on its content
     * 
     * @param section the section panel to calculate height for
     * @return optimal height in pixels
     */
    private static int calculateOptimalSectionHeight(JPanel section) {
        // Count components and calculate based on content
        Component[] components = section.getComponents();
        int rows = 0;
        int textAreaRows = 0;
        
        for (Component comp : components) {
            if (comp instanceof JLabel) {
                rows++;
            } else if (comp instanceof JTextArea) {
                JTextArea textArea = (JTextArea) comp;
                textAreaRows += Math.max(1, textArea.getRows());
            }
        }
        
        // Base height for the titled border and padding
        int baseHeight = 25; // Reduced title bar space from 30
        
        // Height per row of content (reduced for tighter layout)
        int rowHeight = 16; // Further reduced from 18 for even tighter spacing
        
        // Calculate content height
        int contentHeight = baseHeight + (rows * rowHeight) + (textAreaRows * rowHeight);
        
        // Minimum height to ensure usability, maximum to prevent excessive height
        return Math.max(60, Math.min(contentHeight, 120));
    }
    
    /**
     * Simple data class to hold label-value pairs for section content
     */
    public static class LabelValuePair {
        private final String label;
        private final JComponent valueComponent;
        
        public LabelValuePair(String label, JComponent valueComponent) {
            this.label = label;
            this.valueComponent = valueComponent;
        }
        
        public String getLabel() {
            return label;
        }
        
        public JComponent getValueComponent() {
            return valueComponent;
        }
    }
}
