package net.runelite.client.plugins.microbot.pluginscheduler.ui.Antiban;

import javax.swing.*;

import net.runelite.client.plugins.microbot.util.antiban.ui.MasterPanel;

import java.awt.*;

/**
 * A dialog window for displaying the Antiban Master Panel in a separate window.
 * This allows users to configure antiban settings without having to switch to the Antiban plugin tab.
 */
public class AntibanDialogWindow extends JDialog {
    
    /**
     * Creates a new dialog window containing the Antiban MasterPanel
     * 
     * @param owner The parent frame for the dialog
     */
    public AntibanDialogWindow(Frame owner) {
        super(owner, "Antiban Settings", false);
        
        // Create a new master panel
        MasterPanel masterPanel = new MasterPanel();
        
        // Add the panel to the dialog
        add(masterPanel);
        
        // Set dialog properties
        setSize(new Dimension(320, 600));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // Load initial settings
        masterPanel.loadSettings();
    }
    
    /**
     * Static utility method to show the Antiban settings in a new window
     * 
     * @param parent The parent component (used to find the owner frame)
     * @return The created dialog window
     */
    public static AntibanDialogWindow showAntibanSettings(Component parent) {
        // Find the parent frame
        Frame parentFrame = JOptionPane.getFrameForComponent(parent);
        
        // Create and show the dialog
        AntibanDialogWindow dialog = new AntibanDialogWindow(parentFrame);
        dialog.setVisible(true);
        return dialog;
    }
}