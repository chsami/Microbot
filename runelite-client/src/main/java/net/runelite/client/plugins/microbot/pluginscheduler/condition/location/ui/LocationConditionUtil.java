package net.runelite.client.plugins.microbot.pluginscheduler.condition.location.ui;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.AreaCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.PositionCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.RegionCondition;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Set;
import java.util.function.Consumer;


/**
 * Utility class for creating panels for location-based conditions
 */
public class LocationConditionUtil {
    public static final Color BRAND_BLUE = new Color(25, 130, 196);
    /**
     * Creates a unified location condition panel with tab selection for different location condition types
     */
    public static void createLocationConditionPanel(JPanel panel, GridBagConstraints gbc) {
        // Main label
        JLabel titleLabel = new JLabel("Location Condition:");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Create tabbed pane for different location condition types
        gbc.gridy++;
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tabbedPane.setForeground(Color.WHITE);
        
        // Create panels for each condition type
        JPanel positionPanel = new JPanel(new GridBagLayout());
        positionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JPanel areaPanel = new JPanel(new GridBagLayout());
        areaPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JPanel regionPanel = new JPanel(new GridBagLayout());
        regionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Add tabs
        tabbedPane.addTab("Position", positionPanel);
        tabbedPane.addTab("Area", areaPanel);
        tabbedPane.addTab("Region", regionPanel);
        
        // Create condition panels in each tab
        GridBagConstraints tabGbc = new GridBagConstraints();
        tabGbc.gridx = 0;
        tabGbc.gridy = 0;
        tabGbc.weightx = 1;
        tabGbc.fill = GridBagConstraints.HORIZONTAL;
        tabGbc.anchor = GridBagConstraints.NORTHWEST;
        
        createPositionConditionPanel(positionPanel, tabGbc);
        createAreaConditionPanel(areaPanel, tabGbc);
        createRegionConditionPanel(regionPanel, tabGbc);
        
        // Add the tabbed pane to the main panel
        panel.add(tabbedPane, gbc);
        
        // Store the tabbed pane for later access
        panel.putClientProperty("locationTabbedPane", tabbedPane);
    }

    /**
     * Sets up panel with values from an existing location condition
     * 
     * @param panel The panel containing the UI components 
     * @param condition The location condition to read values from
     */
    public static void setupLocationCondition(JPanel panel, Condition condition) {
        if (condition == null) {
            return;
        }

        if (condition instanceof PositionCondition) {
            setupPositionCondition(panel, (PositionCondition) condition);
        } else if (condition instanceof AreaCondition) {
            setupAreaCondition(panel, (AreaCondition) condition);
        } else if (condition instanceof RegionCondition) {
            setupRegionCondition(panel, (RegionCondition) condition);
        }
    }

    /**
     * Sets up a position condition panel with values from an existing condition
     */
    private static void setupPositionCondition(JPanel panel, PositionCondition condition) {
        JSpinner xSpinner = (JSpinner) panel.getClientProperty("posXSpinner");
        JSpinner ySpinner = (JSpinner) panel.getClientProperty("posYSpinner");
        JSpinner zSpinner = (JSpinner) panel.getClientProperty("posZSpinner");
        JSpinner radiusSpinner = (JSpinner) panel.getClientProperty("posRadiusSpinner");
        
        if (xSpinner != null && ySpinner != null && zSpinner != null && radiusSpinner != null) {
            WorldPoint position = condition.getTargetPosition();
            if (position != null) {
                xSpinner.setValue(position.getX());
                ySpinner.setValue(position.getY());
                zSpinner.setValue(position.getPlane());
            }
            radiusSpinner.setValue(condition.getMaxDistance());
        }
    }

    /**
     * Sets up an area condition panel with values from an existing condition
     */
    private static void setupAreaCondition(JPanel panel, AreaCondition condition) {
        JSpinner x1Spinner = (JSpinner) panel.getClientProperty("areaX1Spinner");
        JSpinner y1Spinner = (JSpinner) panel.getClientProperty("areaY1Spinner");
        JSpinner z1Spinner = (JSpinner) panel.getClientProperty("areaZ1Spinner");
        JSpinner x2Spinner = (JSpinner) panel.getClientProperty("areaX2Spinner");
        JSpinner y2Spinner = (JSpinner) panel.getClientProperty("areaY2Spinner");
        JSpinner z2Spinner = (JSpinner) panel.getClientProperty("areaZ2Spinner");
        
        if (x1Spinner != null && y1Spinner != null && z1Spinner != null &&
            x2Spinner != null && y2Spinner != null && z2Spinner != null) {
            
            WorldArea area = condition.getArea();
            if (area != null) {
                x1Spinner.setValue(area.getX());
                y1Spinner.setValue(area.getY());
                z1Spinner.setValue(area.getPlane());
                x2Spinner.setValue(area.getX() + area.getWidth() - 1);
                y2Spinner.setValue(area.getY() + area.getHeight() - 1);
                z2Spinner.setValue(area.getPlane());
            }
        }
    }

    /**
     * Sets up a region condition panel with values from an existing condition
     */
    private static void setupRegionCondition(JPanel panel, RegionCondition condition) {
        JTextField regionIdsField = (JTextField) panel.getClientProperty("regionIdsField");
        JTextField nameField = (JTextField) panel.getClientProperty("regionNameField");
        
        if (regionIdsField != null && condition != null) {
            // Format the region IDs as a comma-separated string
            Set<Integer> regionIds = condition.getTargetRegions();
            if (regionIds != null && !regionIds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Integer regionId : regionIds) {
                    if (sb.length() > 0) {
                        sb.append(",");
                    }
                    sb.append(regionId);
                }
                regionIdsField.setText(sb.toString());
            }
        }
        
        if (nameField != null) {
            nameField.setText(condition.getName());
        }
    }

    /**
     * Creates a location condition based on the selected tab and configuration
     */
    public static Condition createLocationCondition(JPanel configPanel) {
        JTabbedPane tabbedPane = (JTabbedPane) configPanel.getClientProperty("locationTabbedPane");
        
        if (tabbedPane == null) {
            throw new IllegalStateException("Location condition panel not properly configured");
        }
        
        int selectedIndex = tabbedPane.getSelectedIndex();
        
        switch (selectedIndex) {
            case 0: // Position
                return createPositionCondition(configPanel);
            case 1: // Area
                return createAreaCondition(configPanel);
            case 2: // Region
                return createRegionCondition(configPanel);
            default:
                throw new IllegalStateException("Unknown location condition type");
        }
    }

        /**
     * Creates a panel for configuring PositionCondition
     */
    private static void createPositionConditionPanel(JPanel panel, GridBagConstraints gbc) {
        // Section title
        JLabel titleLabel = new JLabel("Position Condition (Specific Location):");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Position coordinates
        gbc.gridy++;
        JPanel positionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        positionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel positionLabel = new JLabel("Target Position:");
        positionLabel.setForeground(Color.WHITE);
        positionPanel.add(positionLabel);
        
        // X input
        SpinnerNumberModel xModel = new SpinnerNumberModel(3000, 0, 20000, 1);
        JSpinner xSpinner = new JSpinner(xModel);
        xSpinner.setPreferredSize(new Dimension(70, xSpinner.getPreferredSize().height));
        positionPanel.add(xSpinner);
        
        JLabel xLabel = new JLabel("X");
        xLabel.setForeground(Color.WHITE);
        positionPanel.add(xLabel);
        
        // Y input
        SpinnerNumberModel yModel = new SpinnerNumberModel(3000, 0, 20000, 1);
        JSpinner ySpinner = new JSpinner(yModel);
        ySpinner.setPreferredSize(new Dimension(70, ySpinner.getPreferredSize().height));
        positionPanel.add(ySpinner);
        
        JLabel yLabel = new JLabel("Y");
        yLabel.setForeground(Color.WHITE);
        positionPanel.add(yLabel);
        
        panel.add(positionPanel, gbc);
        
        // Plane selection
        gbc.gridy++;
        JPanel planePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        planePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel planeLabel = new JLabel("Plane:");
        planeLabel.setForeground(Color.WHITE);
        planePanel.add(planeLabel);
        
        SpinnerNumberModel planeModel = new SpinnerNumberModel(0, 0, 3, 1);
        JSpinner planeSpinner = new JSpinner(planeModel);
        planePanel.add(planeSpinner);
        
        panel.add(planePanel, gbc);
        
        // Distance range
        gbc.gridy++;
        JPanel distancePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        distancePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel distanceLabel = new JLabel("Max Distance (tiles):");
        distanceLabel.setForeground(Color.WHITE);
        distancePanel.add(distanceLabel);
        
        SpinnerNumberModel distanceModel = new SpinnerNumberModel(5, 0, 104, 1);
        JSpinner distanceSpinner = new JSpinner(distanceModel);
        distancePanel.add(distanceSpinner);
        
        // Add info about distance=0 meaning exact location
        JLabel exactLabel = new JLabel("(0 = exact position)");
        exactLabel.setForeground(Color.LIGHT_GRAY);
        distancePanel.add(exactLabel);
        
        panel.add(distancePanel, gbc);
        
        // Current location getter
        gbc.gridy++;
        JPanel currentLocPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentLocPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton useCurrentLocationButton = new JButton("Use Current Location");
        useCurrentLocationButton.setBackground(ColorScheme.BRAND_ORANGE);
        useCurrentLocationButton.setForeground(Color.WHITE);
        useCurrentLocationButton.setFocusPainted(false);
        useCurrentLocationButton.setToolTipText("Use your character's current position");
        useCurrentLocationButton.addActionListener(e -> {
            // Get the current player location
            if (!Microbot.isLoggedIn() || Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                return;
            }
            WorldPoint currentPoint = Rs2Player.getWorldLocation();
            if (currentPoint != null) {
                xSpinner.setValue(currentPoint.getX());
                ySpinner.setValue(currentPoint.getY());
                planeSpinner.setValue(currentPoint.getPlane());
            }
        });
        currentLocPanel.add(useCurrentLocationButton);
              
        JTextField nameField = new JTextField(20);
        // Bank location selector
        JButton selectBankButton = new JButton("Select Bank Location");
        selectBankButton.setBackground(BRAND_BLUE);  // Using consistent color scheme
        selectBankButton.setForeground(Color.WHITE);
        selectBankButton.setToolTipText("Choose from common bank locations in the game");
        selectBankButton.setFocusPainted(false);  // More consistent with other UI elements
        selectBankButton.addActionListener(e -> {
            // Show bank location selector
            showBankLocationSelector(panel, (location) -> {
                if (location != null) {
                    WorldPoint point = location.getWorldPoint();
                    xSpinner.setValue(point.getX());
                    ySpinner.setValue(point.getY());
                    planeSpinner.setValue(point.getPlane());
                    
                    // Update name field to include bank name
                    nameField.setText("At " + location.name() + " Bank");
                }
            });
        });
        currentLocPanel.add(selectBankButton);
        
        panel.add(currentLocPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Condition is met when player is within specified distance of target");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Name field for the condition
        gbc.gridy++;
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel nameLabel = new JLabel("Condition Name:");
        nameLabel.setForeground(Color.WHITE);
        namePanel.add(nameLabel);
        
        
        nameField.setText("Position Condition");
        namePanel.add(nameField);
        
        panel.add(namePanel, gbc);
        
        // Store components for later access
        panel.putClientProperty("positionXSpinner", xSpinner);
        panel.putClientProperty("positionYSpinner", ySpinner);
        panel.putClientProperty("positionPlaneSpinner", planeSpinner);
        panel.putClientProperty("positionDistanceSpinner", distanceSpinner);
        panel.putClientProperty("positionNameField", nameField);
    }
        /**
     * Creates a panel for configuring AreaCondition
     */
    private static void createAreaConditionPanel(JPanel panel, GridBagConstraints gbc) {
        // Section title
        JLabel titleLabel = new JLabel("Area Condition (Rectangular Area):");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // First corner coordinates
        gbc.gridy++;
        JPanel corner1Panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        corner1Panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel corner1Label = new JLabel("Southwest Corner:");
        corner1Label.setForeground(Color.WHITE);
        corner1Panel.add(corner1Label);
        
        // X1 input
        SpinnerNumberModel x1Model = new SpinnerNumberModel(3000, 0, 20000, 1);
        JSpinner x1Spinner = new JSpinner(x1Model);
        x1Spinner.setPreferredSize(new Dimension(70, x1Spinner.getPreferredSize().height));
        corner1Panel.add(x1Spinner);
        
        JLabel x1Label = new JLabel("X");
        x1Label.setForeground(Color.WHITE);
        corner1Panel.add(x1Label);
        
        // Y1 input
        SpinnerNumberModel y1Model = new SpinnerNumberModel(3000, 0, 20000, 1);
        JSpinner y1Spinner = new JSpinner(y1Model);
        y1Spinner.setPreferredSize(new Dimension(70, y1Spinner.getPreferredSize().height));
        corner1Panel.add(y1Spinner);
        
        JLabel y1Label = new JLabel("Y");
        y1Label.setForeground(Color.WHITE);
        corner1Panel.add(y1Label);
        
        panel.add(corner1Panel, gbc);
        
        // Second corner coordinates
        gbc.gridy++;
        JPanel corner2Panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        corner2Panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel corner2Label = new JLabel("Northeast Corner:");
        corner2Label.setForeground(Color.WHITE);
        corner2Panel.add(corner2Label);
        
        // X2 input
        SpinnerNumberModel x2Model = new SpinnerNumberModel(3010, 0, 20000, 1);
        JSpinner x2Spinner = new JSpinner(x2Model);
        x2Spinner.setPreferredSize(new Dimension(70, x2Spinner.getPreferredSize().height));
        corner2Panel.add(x2Spinner);
        
        JLabel x2Label = new JLabel("X");
        x2Label.setForeground(Color.WHITE);
        corner2Panel.add(x2Label);
        
        // Y2 input
        SpinnerNumberModel y2Model = new SpinnerNumberModel(3010, 0, 20000, 1);
        JSpinner y2Spinner = new JSpinner(y2Model);
        y2Spinner.setPreferredSize(new Dimension(70, y2Spinner.getPreferredSize().height));
        corner2Panel.add(y2Spinner);
        
        JLabel y2Label = new JLabel("Y");
        y2Label.setForeground(Color.WHITE);
        corner2Panel.add(y2Label);
        
        panel.add(corner2Panel, gbc);
        
        // Plane selection
        gbc.gridy++;
        JPanel planePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        planePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel planeLabel = new JLabel("Plane:");
        planeLabel.setForeground(Color.WHITE);
        planePanel.add(planeLabel);
        
        SpinnerNumberModel planeModel = new SpinnerNumberModel(0, 0, 3, 1);
        JSpinner planeSpinner = new JSpinner(planeModel);
        planePanel.add(planeSpinner);
        
        panel.add(planePanel, gbc);
        
        // Current location getter
        gbc.gridy++;
        JPanel currentLocPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentLocPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton useCurrentLocationButton = new JButton("Create Area Around Current Location");
        useCurrentLocationButton.setBackground(ColorScheme.BRAND_ORANGE);
        useCurrentLocationButton.setForeground(Color.WHITE);
        useCurrentLocationButton.addActionListener(e -> {
            // Get current player location and create area around it
            WorldPoint currentPoint = Rs2Player.getWorldLocation();
            if (currentPoint != null) {
                // Set corner1 to 5 tiles southwest
                x1Spinner.setValue(currentPoint.getX() - 5);
                y1Spinner.setValue(currentPoint.getY() - 5);
                
                // Set corner2 to 5 tiles northeast
                x2Spinner.setValue(currentPoint.getX() + 5);
                y2Spinner.setValue(currentPoint.getY() + 5);
                
                // Set plane
                planeSpinner.setValue(currentPoint.getPlane());
            }
        });
        currentLocPanel.add(useCurrentLocationButton);
        
        panel.add(currentLocPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Condition is met when player is inside the rectangular area");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Name field for the condition
        gbc.gridy++;
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel nameLabel = new JLabel("Condition Name:");
        nameLabel.setForeground(Color.WHITE);
        namePanel.add(nameLabel);
        
        JTextField nameField = new JTextField(20);
        nameField.setText("Area Condition");
        namePanel.add(nameField);
        
        panel.add(namePanel, gbc);
        
        // Store components for later access
        panel.putClientProperty("areaX1Spinner", x1Spinner);
        panel.putClientProperty("areaY1Spinner", y1Spinner);
        panel.putClientProperty("areaX2Spinner", x2Spinner);
        panel.putClientProperty("areaY2Spinner", y2Spinner);
        panel.putClientProperty("areaPlaneSpinner", planeSpinner);
        panel.putClientProperty("areaNameField", nameField);
    }
        /**
     * Creates a panel for configuring RegionCondition
     */
    private static void createRegionConditionPanel(JPanel panel, GridBagConstraints gbc) {
        // Section title
        JLabel titleLabel = new JLabel("Region Condition (Game Region):");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);
        
        // Region IDs input
        gbc.gridy++;
        JPanel regionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        regionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel regionsLabel = new JLabel("Region IDs (comma-separated):");
        regionsLabel.setForeground(Color.WHITE);
        regionPanel.add(regionsLabel);
        
        JTextField regionIdsField = new JTextField(20);
        regionIdsField.setToolTipText("Enter region IDs separated by commas (e.g., 12850,12851)");
        regionPanel.add(regionIdsField);
        
        panel.add(regionPanel, gbc);
        
        // Current region getter
        gbc.gridy++;
        JPanel currentRegionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        currentRegionPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JButton useCurrentRegionButton = new JButton("Use Current Region");
        useCurrentRegionButton.setBackground(ColorScheme.BRAND_ORANGE);
        useCurrentRegionButton.setForeground(Color.WHITE);
        useCurrentRegionButton.addActionListener(e -> {
            // Get the current player region
            WorldPoint currentPoint = Rs2Player.getWorldLocation();
            if (currentPoint != null) {
                int regionId = currentPoint.getRegionID();
                regionIdsField.setText(String.valueOf(regionId));
            }
        });
        currentRegionPanel.add(useCurrentRegionButton);
        
        panel.add(currentRegionPanel, gbc);
        
        // Region presets panel
        gbc.gridy++;
        JPanel presetsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        presetsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel presetsLabel = new JLabel("Common Regions:");
        presetsLabel.setForeground(Color.WHITE);
        presetsPanel.add(presetsLabel);
        
        // Add example regions as presets
        String[][] regionPresets = {
            {"Lumbridge", "12850"},
            {"Varrock", "12853"},
            {"Grand Exchange", "12598"},
            {"Falador", "12084"},
            {"Edgeville", "12342"}
        };
        
        JComboBox<String> regionPresetsCombo = new JComboBox<>();
        regionPresetsCombo.addItem("Select a region...");
        for (String[] preset : regionPresets) {
            regionPresetsCombo.addItem(preset[0]);
        }
        
        regionPresetsCombo.addActionListener(e -> {
            int selectedIndex = regionPresetsCombo.getSelectedIndex();
            if (selectedIndex > 0) {
                regionIdsField.setText(regionPresets[selectedIndex - 1][1]);
            }
        });
        
        presetsPanel.add(regionPresetsCombo);
        
        panel.add(presetsPanel, gbc);
        
        // Add a helpful description
        gbc.gridy++;
        JLabel descriptionLabel = new JLabel("Condition is met when player is in any of the specified regions");
        descriptionLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        descriptionLabel.setFont(FontManager.getRunescapeSmallFont());
        panel.add(descriptionLabel, gbc);
        
        // Name field for the condition
        gbc.gridy++;
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        JLabel nameLabel = new JLabel("Condition Name:");
        nameLabel.setForeground(Color.WHITE);
        namePanel.add(nameLabel);
        
        JTextField nameField = new JTextField(20);
        nameField.setText("Region Condition");
        namePanel.add(nameField);
        
        panel.add(namePanel, gbc);
        
        // Store components for later access
        panel.putClientProperty("regionIdsField", regionIdsField);
        panel.putClientProperty("regionNameField", nameField);
    }
        /**
     * Creates a PositionCondition from the panel configuration
     */
    public static PositionCondition createPositionCondition(JPanel configPanel) {
        JSpinner xSpinner = (JSpinner) configPanel.getClientProperty("positionXSpinner");
        JSpinner ySpinner = (JSpinner) configPanel.getClientProperty("positionYSpinner");
        JSpinner planeSpinner = (JSpinner) configPanel.getClientProperty("positionPlaneSpinner");
        JSpinner distanceSpinner = (JSpinner) configPanel.getClientProperty("positionDistanceSpinner");
        JTextField nameField = (JTextField) configPanel.getClientProperty("positionNameField");
        
        if (xSpinner == null || ySpinner == null || planeSpinner == null || distanceSpinner == null) {
            throw new IllegalStateException("Position condition panel not properly configured");
        }
        
        int x = (Integer) xSpinner.getValue();
        int y = (Integer) ySpinner.getValue();
        int plane = (Integer) planeSpinner.getValue();
        int distance = (Integer) distanceSpinner.getValue();
        
        String name = nameField != null ? nameField.getText() : "Position Condition";
        if (name.isEmpty()) {
            name = "Position Condition";
        }
        
        return new PositionCondition(name, x, y, plane, distance);
    }
  

        /**
     * Creates an AreaCondition from the panel configuration
     */
    public static AreaCondition createAreaCondition(JPanel configPanel) {
        JSpinner x1Spinner = (JSpinner) configPanel.getClientProperty("areaX1Spinner");
        JSpinner y1Spinner = (JSpinner) configPanel.getClientProperty("areaY1Spinner");
        JSpinner x2Spinner = (JSpinner) configPanel.getClientProperty("areaX2Spinner");
        JSpinner y2Spinner = (JSpinner) configPanel.getClientProperty("areaY2Spinner");
        JSpinner planeSpinner = (JSpinner) configPanel.getClientProperty("areaPlaneSpinner");
        JTextField nameField = (JTextField) configPanel.getClientProperty("areaNameField");
        
        if (x1Spinner == null || y1Spinner == null || x2Spinner == null || y2Spinner == null || planeSpinner == null) {
            throw new IllegalStateException("Area condition panel not properly configured");
        }
        
        int x1 = (Integer) x1Spinner.getValue();
        int y1 = (Integer) y1Spinner.getValue();
        int x2 = (Integer) x2Spinner.getValue();
        int y2 = (Integer) y2Spinner.getValue();
        int plane = (Integer) planeSpinner.getValue();
        
        String name = nameField != null ? nameField.getText() : "Area Condition";
        if (name.isEmpty()) {
            name = "Area Condition";
        }
        
        return new AreaCondition(name, x1, y1, x2, y2, plane);
    }
        /**
     * Creates a RegionCondition from the panel configuration
     */
    public static RegionCondition createRegionCondition(JPanel configPanel) {
        JTextField regionIdsField = (JTextField) configPanel.getClientProperty("regionIdsField");
        JTextField nameField = (JTextField) configPanel.getClientProperty("regionNameField");
        
        if (regionIdsField == null) {
            throw new IllegalStateException("Region condition panel not properly configured");
        }
        
        String regionIdsText = regionIdsField.getText().trim();
        if (regionIdsText.isEmpty()) {
            throw new IllegalArgumentException("Region IDs cannot be empty");
        }
        
        // Parse comma-separated region IDs
        String[] regionIdStrings = regionIdsText.split(",");
        int[] regionIds = new int[regionIdStrings.length];
        
        try {
            for (int i = 0; i < regionIdStrings.length; i++) {
                regionIds[i] = Integer.parseInt(regionIdStrings[i].trim());
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid region ID format. Must be comma-separated integers.");
        }
        
        String name = nameField != null ? nameField.getText() : "Region Condition";
        if (name.isEmpty()) {
            name = "Region Condition";
        }
        
        return new RegionCondition(name, regionIds);
    }
    /**
     * Shows a dialog to select a bank location
     * 
     * @param parentComponent The parent component for the dialog
     * @param callback Callback function to handle the selected bank location
     */
    private static void showBankLocationSelector(Component parentComponent, Consumer<BankLocation> callback) {
        // Create a dialog for bank location selection
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parentComponent), "Select Bank Location", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(400, 500);
        dialog.setLocationRelativeTo(parentComponent);
        
        // Create a panel for the dialog content
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        
        // Search field at the top
        JTextField searchField = new JTextField();
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        searchField.setForeground(Color.WHITE);
        searchField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));
        
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(Color.WHITE);
        searchLabel.setFont(FontManager.getRunescapeSmallFont());
        
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        
        // List of bank locations
        DefaultListModel<BankLocation> bankListModel = new DefaultListModel<>();
        for (BankLocation location : BankLocation.values()) {
            bankListModel.addElement(location);
        }
        
        JList<BankLocation> bankList = new JList<>(bankListModel);
        bankList.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        bankList.setForeground(Color.WHITE);
        bankList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bankList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BankLocation) {
                    setText(((BankLocation) value).name());
                }
                return this;
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(bankList);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        // Filter the list when search text changes
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filterList();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filterList();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filterList();
            }
            
            private void filterList() {
                String searchText = searchField.getText().toLowerCase();
                DefaultListModel<BankLocation> filteredModel = new DefaultListModel<>();
                
                for (BankLocation location : BankLocation.values()) {
                    if (location.name().toLowerCase().contains(searchText)) {
                        filteredModel.addElement(location);
                    }
                }
                
                bankList.setModel(filteredModel);
            }
        });
        
        // Buttons at the bottom
        JButton selectButton = new JButton("Select");
        selectButton.setBackground(ColorScheme.BRAND_ORANGE);
        selectButton.setForeground(Color.WHITE);
        selectButton.setFocusPainted(false);
        selectButton.addActionListener(e -> {
            BankLocation selectedLocation = bankList.getSelectedValue();
            callback.accept(selectedLocation);
            dialog.dispose();
        });
        
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setBackground(ColorScheme.LIGHT_GRAY_COLOR);
        cancelButton.setForeground(Color.BLACK);
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> dialog.dispose());
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        buttonPanel.add(cancelButton);
        buttonPanel.add(selectButton);
        
        // Add everything to the content panel
        contentPanel.add(searchPanel, BorderLayout.NORTH);
        contentPanel.add(scrollPane, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // Add content panel to dialog and show it
        dialog.add(contentPanel);
        dialog.setVisible(true);
    }

}