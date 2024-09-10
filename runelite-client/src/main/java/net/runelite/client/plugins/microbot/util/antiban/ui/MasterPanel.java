package net.runelite.client.plugins.microbot.util.antiban.ui;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;

/**
 * The MasterPanel is a user interface panel for configuring anti-ban settings.
 *
 * <p>
 * This panel allows users to adjust settings related to the anti-ban system, such as enabling micro-breaks,
 * adjusting action cooldown probabilities, and configuring behavioral simulations like fatigue or attention span.
 * The panel is divided into different categories, each focusing on specific aspects of anti-ban behavior,
 * including activity settings, mouse behavior, and cooldown management.
 * </p>
 *
 * <p>
 * Users can interact with various checkboxes and sliders to tailor the bot's anti-ban features to their preferences,
 * making it behave more like a human player during automated tasks.
 * </p>
 *
 * <h3>Main Features:</h3>
 * <ul>
 *   <li>Enable or disable anti-ban features like action cooldowns and micro-breaks.</li>
 *   <li>Customize the bot's behavior with random intervals, dynamic activity, and simulated fatigue.</li>
 *   <li>Adjust the duration and probability of micro-breaks and action cooldowns.</li>
 *   <li>Fine-tune mouse behavior, including natural movements and random actions.</li>
 *   <li>View real-time information about the current play style, activity, and bot status.</li>
 * </ul>
 *
 * <p>
 * This panel is automatically integrated into the bot's user interface and does not require manual initialization by the user.
 * </p>
 */
public class MasterPanel extends PluginPanel {
    private static final int BOUNDARY_RIGHT = 150; // Adjust for how far you want the GIF to move
    private static final int BOUNDARY_LEFT = 0;
    // Additional Info Panel
    private final JLabel playStyleLabel = new JLabel("Play Style: " + (Rs2Antiban.getPlayStyle() != null ? Rs2Antiban.getPlayStyle().getName() : "null"));
    private final JLabel playStyleChangeLabel = new JLabel("Play Style Change: " + (Rs2Antiban.getPlayStyle() != null ? Rs2Antiban.getPlayStyle().getTimeLeftUntilNextSwitch() : "null"));
    private final JLabel profileLabel = new JLabel("Category: " + (Rs2Antiban.getCategory() != null ? Rs2Antiban.getCategory().getName() : "null"));
    private final JLabel activityLabel = new JLabel("Activity: " + (Rs2Antiban.getActivity() != null ? Rs2Antiban.getActivity().getMethod() : "null"));
    private final JLabel activityIntensityLabel = new JLabel("Activity Intensity: " + (Rs2Antiban.getActivityIntensity() != null ? Rs2Antiban.getActivityIntensity().getName() : "null"));
    private final JLabel busyLabel = new JLabel("Busy: " + (Rs2Antiban.getCategory() != null ? Rs2Antiban.getCategory().isBusy() : "null"));
    private final boolean isFlipped = false; // Track if the image is flipped
    private final FlippableLabel label;
    private final JLayeredPane layeredPane; // Use a layered pane for positioning the GIF
    GeneralPanel generalPanel = new GeneralPanel();
    ActivityPanel activityPanel = new ActivityPanel();
    ProfilePanel profilePanel = new ProfilePanel();
    MousePanel mousePanel = new MousePanel();
    MicroBreakPanel microBreakPanel = new MicroBreakPanel();
    CooldownPanel cooldownPanel = new CooldownPanel();
    JButton resetButton = new JButton("Reset");
    private int xPosition = 0;
    private int xVelocity = 1; // Change this value to control the speed of movement

    public MasterPanel() {
        setLayout(new BorderLayout());

        // Create the CardPanel (which contains the CardLayout)
        CardPanel cardPanel = new CardPanel();

        // Add panels to the CardPanel with unique names
        cardPanel.addPanel(generalPanel, "General");
        cardPanel.addPanel(activityPanel, "Activity");
        cardPanel.addPanel(profilePanel, "Profile");
        cardPanel.addPanel(mousePanel, "Mouse");
        cardPanel.addPanel(microBreakPanel, "MicroBreak");
        cardPanel.addPanel(cooldownPanel, "Cooldown");

        // Create the NavigationPanel and pass the CardPanel to it
        NavigationPanel navigationPanel = new NavigationPanel(cardPanel);

        JPanel headerPanel = createHeaderPanel(navigationPanel);
        JPanel mainDisplayPanel = new JPanel();

        mainDisplayPanel.add(cardPanel);
        mainDisplayPanel.setLayout(new BoxLayout(mainDisplayPanel, BoxLayout.Y_AXIS));
        mainDisplayPanel.add(createInfoPanel());
        mainDisplayPanel.add(Box.createVerticalStrut(100));
        mainDisplayPanel.add(new Box(BoxLayout.Y_AXIS));
        layeredPane = new JLayeredPane();
        layeredPane.setPreferredSize(new Dimension(250, 32));

        // Create and position the GIF JLabel
        ImageIcon icon = new ImageIcon(Rs2Antiban.class.getResource("walkingduckdark.gif"));
        label = new FlippableLabel(icon);
        label.setBounds(xPosition, 0, icon.getIconWidth(), icon.getIconHeight()); // Initial position
        layeredPane.add(label, JLayeredPane.DEFAULT_LAYER); // Add label to the default layer
        mainDisplayPanel.add(layeredPane);

        // Timer to move the GIF back and forth
        Timer timer = new Timer(80, new ActionListener() { // Update every 20ms (50fps)
            @Override
            public void actionPerformed(ActionEvent e) {
                // Update the position
                xPosition += xVelocity;

                // Check boundaries and reverse direction if needed
                if (xPosition >= 250 || xPosition <= BOUNDARY_LEFT) {
                    xVelocity = -xVelocity; // Reverse direction
                    label.flip(); // Flip the image when direction changes
                }

                // Update the label's position
                label.updatePosition(xPosition);
            }
        });
        timer.start(); // Start the movement timer

        add(headerPanel, BorderLayout.NORTH);
        add(mainDisplayPanel, BorderLayout.CENTER);
        add(resetButton, BorderLayout.SOUTH);

        cardPanel.showPanel("General");
        setupResetButton();

    }

    private JPanel createHeaderPanel(NavigationPanel navigationPanel) {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BorderLayout());
        headerPanel.setBackground(new Color(27, 27, 27));

//        JLabel headerLabel = new JLabel("<html><font color=#ffff1a>\uD83E\uDD86</font> ANTIBAN <font color=#ffff1a>\uD83E\uDD86</font></html>");
        JLabel headerLabel = new JLabel("ANTIBAN");
        headerLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(24.0F));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        headerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        headerPanel.add(headerLabel, BorderLayout.NORTH);
        headerPanel.add(navigationPanel, BorderLayout.CENTER);

        return headerPanel;
    }

    //set up the reset button to reset all settings
    public void setupResetButton() {
        resetButton.addActionListener(e -> {
            Rs2Antiban.resetAntibanSettings();
            loadSettings();
        });
    }

    public void loadSettings() {
        // Load settings from the settings object and set the checkboxes accordingly
        generalPanel.updateValues();
        activityPanel.updateValues();
        profilePanel.updateValues();
        mousePanel.updateValues();
        microBreakPanel.updateValues();
        cooldownPanel.updateValues();

        if (!Microbot.isLoggedIn())
            return;

        playStyleLabel.setText("Play Style: " + (Rs2Antiban.getPlayStyle() != null ? Rs2Antiban.getPlayStyle().getName() : "null"));
        playStyleChangeLabel.setText("Play Style Change: " + (Rs2Antiban.getPlayStyle() != null ? Rs2Antiban.getPlayStyle().getTimeLeftUntilNextSwitch() : "null"));
        profileLabel.setText("Category: " + (Rs2Antiban.getCategory() != null ? Rs2Antiban.getCategory().getName() : "null"));
        activityLabel.setText("Activity: " + (Rs2Antiban.getActivity() != null ? Rs2Antiban.getActivity().getMethod() : "null"));
        activityIntensityLabel.setText("Activity Intensity: " + (Rs2Antiban.getActivityIntensity() != null ? Rs2Antiban.getActivityIntensity().getName() : "null"));
        busyLabel.setText("Busy: " + (Rs2Antiban.getCategory() != null ? Rs2Antiban.getCategory().isBusy() : "null"));
    }

    private JPanel createInfoPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Additional Info"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 5, 5, 5);

        panel.add(playStyleLabel, gbc);
        panel.add(playStyleChangeLabel, gbc);
        panel.add(profileLabel, gbc);
        panel.add(activityLabel, gbc);
        panel.add(activityIntensityLabel, gbc);
        panel.add(busyLabel, gbc);

        return panel;
    }

    // Custom JLabel class that supports flipping the image
    private class FlippableLabel extends JLabel {
        private boolean isFlipped = false;

        public FlippableLabel(ImageIcon icon) {
            super(icon);
            setDoubleBuffered(true); // Enable double buffering to prevent flickering
        }

        public void flip() {
            isFlipped = !isFlipped;
            repaint(); // Request a repaint to apply the flip
        }

        public void updatePosition(int x) {
            // Safely update the label position without interfering with other UI events
            SwingUtilities.invokeLater(() -> setBounds(x, getY(), getWidth(), getHeight()));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2d = (Graphics2D) g.create(); // Create a copy of Graphics2D to avoid modifying the original

            if (isFlipped) {
                // Apply horizontal flip by flipping the x-axis
                AffineTransform transform = AffineTransform.getScaleInstance(-1, 1);
                transform.translate(-getWidth(), 0);
                g2d.setTransform(transform);
            }

            super.paintComponent(g2d); // Let JLabel handle the image rendering
            g2d.dispose(); // Dispose of the copy to release resources
        }
    }
}
