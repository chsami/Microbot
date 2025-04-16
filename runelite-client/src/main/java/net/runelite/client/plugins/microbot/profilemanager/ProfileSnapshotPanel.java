package net.runelite.client.plugins.microbot.profilemanager;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Properties;

public class ProfileSnapshotPanel extends PluginPanel {
    private final ConfigManager configManager;
    private final PluginManager pluginManager;

    @Inject
    public ProfileSnapshotPanel(ConfigManager configManager, PluginManager pluginManager) {
        this.configManager = configManager;
        this.pluginManager = pluginManager;

        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Profile Snapshot Manager", SwingConstants.CENTER);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new GridLayout(2, 1, 5, 5));

        JButton saveButton = new JButton("💾 Save Snapshot");
        JButton loadButton = new JButton("📂 Load Snapshot");

        buttons.add(saveButton);
        buttons.add(loadButton);
        add(buttons, BorderLayout.CENTER);

        loadButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Load Profile Snapshot");

            int selection = fileChooser.showOpenDialog(this);
            if (selection != JFileChooser.APPROVE_OPTION)
                return;

            File file = fileChooser.getSelectedFile();
            loadSnapshotFromFile(file);
        });

        saveButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Profile Snapshot");

            int selection = fileChooser.showSaveDialog(this);
            if (selection != JFileChooser.APPROVE_OPTION)
                return;

            File file = fileChooser.getSelectedFile();
            saveSnapshotToFile(file);
        });
    }

    private void saveSnapshotToFile(File file)
    {
        try (FileOutputStream fos = new FileOutputStream(file))
        {
            Properties properties = configManager.getAllProfileProperties(); // or whatever source you use
            properties.store(fos, "Plugin Config Snapshot");
            Microbot.showMessage("Snapshot saved to: " + file.getName());
        }
        catch (IOException e)
        {
            Microbot.showMessage("Failed to save snapshot: " + e.getMessage());
        }
    }

    public void loadSnapshotFromFile(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);

            // Load configuration properties
            configManager.loadProfileProperties(props); // loads regular config

            // Restore plugin activation states
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("pluginState.")) {
                    String pluginName = key.replace("pluginState.", "");
                    boolean shouldEnable = Boolean.parseBoolean(props.getProperty(key));

                    for (Plugin plugin : pluginManager.getPlugins()) {
                        if (plugin.getClass().getSimpleName().equals(pluginName)) {
                            try {
                                if (shouldEnable)
                                    pluginManager.startPlugin(plugin);
                                else
                                    pluginManager.stopPlugin(plugin);
                            } catch (PluginInstantiationException e) {
                                Microbot.showMessage("Failed to toggle plugin: " + pluginName);
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            Microbot.showMessage("Snapshot loaded: " + file.getName());
        } catch (IOException ex) {
            Microbot.showMessage("Failed to load snapshot: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Override
    public void onActivate()
    {
        super.onActivate();
        refreshPluginToggleStates();
    }

    private void refreshPluginToggleStates()
    {
        for (var plugin : pluginManager.getPlugins())
        {
            boolean isRunning = pluginManager.isPluginEnabled(plugin);
            pluginManager.setPluginEnabled(plugin, isRunning); // Triggers UI refresh
        }
    }

}
