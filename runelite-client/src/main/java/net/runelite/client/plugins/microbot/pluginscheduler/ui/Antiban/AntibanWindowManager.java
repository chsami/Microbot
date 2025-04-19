package net.runelite.client.plugins.microbot.pluginscheduler.ui.Antiban;


import javax.swing.*;

import net.runelite.client.plugins.microbot.util.antiban.ui.MasterPanel;

/**
 * A utility class that manages opening the Antiban MasterPanel in a separate window.
 * This allows the MasterPanel to be displayed outside of the normal RuneLite sidebar.
 */
public class AntibanWindowManager {
    
    private static JFrame antibanWindow;
    private static MasterPanel masterPanel;
    
    /**
     * Opens the Antiban MasterPanel in a new window
     * 
     * @param injector The injector to use for creating the MasterPanel
     * @return The created window
     */
    public static JFrame openAntibanWindow(Object injector) {
        // If the window is already open, just bring it to front
        if (antibanWindow != null && antibanWindow.isDisplayable()) {
            antibanWindow.toFront();
            antibanWindow.requestFocus();
            return antibanWindow;
        }
        
        // Create a new MasterPanel
        if (masterPanel == null) {
            try {
                masterPanel = new MasterPanel();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        
        // Create and set up the window
        antibanWindow = new JFrame("Antiban Configuration");
        antibanWindow.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        antibanWindow.setContentPane(masterPanel);
        antibanWindow.pack();
        antibanWindow.setSize(400, 600); // Set an appropriate size
        antibanWindow.setLocationRelativeTo(null); // Center on screen
        
        // Show the window
        antibanWindow.setVisible(true);
        
        // Load the latest settings
        masterPanel.loadSettings();
        
        return antibanWindow;
    }
    
    /**
     * Checks if the Antiban window is currently open
     * 
     * @return true if the window is open, false otherwise
     */
    public static boolean isWindowOpen() {
        return antibanWindow != null && antibanWindow.isDisplayable();
    }
    
    /**
     * Closes the Antiban window if it's open
     */
    public static void closeWindow() {
        if (antibanWindow != null) {
            antibanWindow.dispose();
            antibanWindow = null;
        }
    }
}