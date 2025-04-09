package net.runelite.client.plugins.microbot.github;

import lombok.SneakyThrows;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.github.models.FileInfo;
import net.runelite.client.plugins.microbot.sideloading.MicrobotPluginManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static javax.swing.JOptionPane.showMessageDialog;

public class GithubPanel extends PluginPanel {

    private final JComboBox<String> repoDropdown = new JComboBox();
    private final JTextField folderField = new JTextField("", 10);
    private final JTextField tokenField = new JTextField("", 10);

    private final DefaultListModel<FileInfo> listModel = new DefaultListModel<FileInfo>();
    private final JList<FileInfo> fileList = new JList<>(listModel);

    @Inject
    MicrobotPluginManager microbotPluginManager;

    @Inject
    ConfigManager configManager;

    GithubPlugin plugin;

    @Inject
    public GithubPanel(GithubPlugin plugin) {
        this.plugin = plugin;

        // Top panel for inputs
        // Keep BoxLayout
        JPanel inputPanel = new JPanel(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();


        gbc.insets = new Insets(2, 2, 2, 2); // Add some padding
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.WEST;

        GridBagConstraints gbci = new GridBagConstraints();


        gbci.insets = new Insets(10, 2, 10, 2); // Add some padding
        gbci.fill = GridBagConstraints.HORIZONTAL;
        gbci.weightx = 1.0;
        gbci.gridwidth = GridBagConstraints.REMAINDER;
        gbci.anchor = GridBagConstraints.WEST;

        inputPanel.add(new JLabel("Repo Url:*"), gbc);
        repoDropdown.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        inputPanel.add(repoDropdown, gbci);
        for (String option : getOptionsList()) {
            repoDropdown.addItem(option);
        }


        inputPanel.add(new JLabel("Folder: (empty = root folder)"), gbc);
        folderField.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));

        inputPanel.add(folderField, gbci);

        inputPanel.add(new JLabel("Token:"), gbc);
        tokenField.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        inputPanel.add(tokenField, gbci);
        inputPanel.add(new JLabel(""), gbci);


        // Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(5, 1, 10, 10));
        JButton addRepoButton = new JButton("Add Repo Url");
        addRepoButton.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        JButton deleteRepoButton = new JButton("Delete Repo Url");
        deleteRepoButton.setBorder(BorderFactory.createLineBorder(ColorScheme.PROGRESS_ERROR_COLOR));
        JButton fetchButton = new JButton("Fetch from GitHub");
        fetchButton.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        JButton downloadButton = new JButton("Download Selected");
        downloadButton.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        JButton downloadAllButton = new JButton("Download All");
        downloadAllButton.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        JButton openMicrobotSideLoadPluginFolder = new JButton("Open folder");
        openMicrobotSideLoadPluginFolder.setBorder(BorderFactory.createLineBorder(ColorScheme.BRAND_ORANGE));
        buttonPanel.add(addRepoButton);
        buttonPanel.add(deleteRepoButton);
        buttonPanel.add(fetchButton);
        buttonPanel.add(downloadButton);
        buttonPanel.add(downloadAllButton);
        buttonPanel.add(openMicrobotSideLoadPluginFolder);
        buttonPanel.add(new JLabel(""));

        // Main layout
        setLayout(new BorderLayout());
        add(inputPanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.CENTER);
        add(new JScrollPane(fileList), BorderLayout.SOUTH);


        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof FileInfo) {
                    FileInfo fileInfo = (FileInfo) value;
                    File localFile = new File(RuneLite.RUNELITE_DIR, "microbot-plugins/" + fileInfo.getName());
                    boolean exists = localFile.exists();

                    if (exists) {
                        label.setText("✔ " + fileInfo.getName());
                        label.setForeground(Color.GREEN.darker());
                    } else {
                        label.setText(fileInfo.getName());
                        label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
                    }
                }
                return label;
            }
        });

        // Button actions
        addRepoButton.addActionListener(e -> addRepoUrl());
        deleteRepoButton.addActionListener(e -> deleteRepoUrl());
        fetchButton.addActionListener(e -> fetchFiles());
        downloadButton.addActionListener(e -> downloadSelected());
        downloadAllButton.addActionListener(e -> downloadAll());
        openMicrobotSideLoadPluginFolder.addActionListener(e -> openMicrobotSideLoadingFolder());

    }

    /**
     * Deletes a repository URL from the dropdown and saves the updated list to the configuration.
     */
    private void deleteRepoUrl() {
        String selected = (String) repoDropdown.getSelectedItem();
        if (selected != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to delete the selected repository URL?",
                    "Confirm Deletion",
                    JOptionPane.YES_NO_OPTION);

            if (confirm == JOptionPane.YES_OPTION) {
                List<String> currentItems = getOptionsList();
                currentItems.remove(selected);
                String updatedConfig = String.join(",", currentItems);

                configManager.setConfiguration("GithubPlugin", "repoUrls", updatedConfig);
                repoDropdown.removeItem(selected);
            }
        }
    }

    /**
     * Adds a repository URL to the dropdown and saves it to the configuration.
     */
    private void addRepoUrl() {
        String url = JOptionPane.showInputDialog(this, "Enter the repository URL:");
        if (url != null && !url.isEmpty()) {
            repoDropdown.addItem(url);
            repoDropdown.setSelectedItem(url);
            List<String> currentItems = getOptionsList();
            currentItems.add(url);
            String updatedConfig = String.join(",", currentItems);

            configManager.setConfiguration("GithubPlugin", "repoUrls", updatedConfig);
        }
    }

    /**
     * Deletes all files in the downloads directory.
     */
    private void openMicrobotSideLoadingFolder() {
        String userHome = System.getProperty("user.home");
        File folder = new File(userHome, ".runelite/microbot-plugins");

        if (folder.exists()) {
            try {
                Desktop.getDesktop().open(folder);
            } catch (IOException e) {
                System.err.println("Failed to open folder: " + e.getMessage());
            }
        } else {
            System.err.println("Folder does not exist: " + folder.getAbsolutePath());
        }
    }

    /**
     * Downloads all files in the specified GitHub repository folder.
     */
    @SneakyThrows
    private void downloadAll() {
        if (!isRepoSelected()) return;

        if (listModel.isEmpty()) {
            showMessageDialog(this, "No files to download.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (folderField.getText().isEmpty() && GithubDownloader.isLargeRepo(Objects.requireNonNull(repoDropdown.getSelectedItem()).toString(), tokenField.getText())) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "⚠ The repository is over 50MB.\nAre you sure you want to continue?",
                    "Large Repository",
                    JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = createLoadingDialog(parentWindow);

        List<FileInfo> allFiles = GithubDownloader.getAllFilesRecursively(Objects.requireNonNull(repoDropdown.getSelectedItem()).toString(), folderField.getText(), tokenField.getText());

        dialog.setVisible(false);
        parentWindow.remove(dialog);
        // Create progress dialog
        JDialog progressDialog = new JDialog(parentWindow, "loader message...", Dialog.ModalityType.APPLICATION_MODAL);
        JProgressBar progressBar = new JProgressBar(0, allFiles.size());
        progressBar.setStringPainted(true);
        progressDialog.add(progressBar);
        progressDialog.setSize(300, 75);
        progressDialog.setLocationRelativeTo(this);

        // Background task
        SwingWorker<Void, Integer> worker = new SwingWorker<>() {
            @SneakyThrows
            @Override
            protected Void doInBackground() {
                for (int i = 0; i < allFiles.size(); i++) {
                    FileInfo fileInfo = allFiles.get(i);
                    String downloadUrl = fileInfo.getUrl();
                    String fileName = fileInfo.getName();
                    System.out.println("Downloading file: " + fileName);
                    GithubDownloader.downloadFile(downloadUrl);
                    publish(i + 1);
                }
                return null;
            }

            @Override
            protected void process(List<Integer> chunks) {
                int latest = chunks.get(chunks.size() - 1);
                progressBar.setValue(latest);
            }

            @SneakyThrows
            @Override
            protected void done() {
                progressDialog.dispose();
                fileList.repaint(); // update any downloaded indicators
                JOptionPane.showMessageDialog(parentWindow, "All files downloaded.", "Download Successful!",
                        JOptionPane.INFORMATION_MESSAGE);
            }
        };

        worker.execute();
        progressDialog.setVisible(true); // blocks until worker finishes
        microbotPluginManager.loadSideLoadPlugins();

    }

    /**
     * Checks if a repository URL is selected.
     *
     * @return
     */
    private boolean isRepoSelected() {
        if (repoDropdown.getSelectedItem() == null) {
            showMessageDialog(this, "Please select a repository URL.", "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    /**
     * Downloads the selected files in the list.
     */
    @SneakyThrows
    private void downloadSelected() {
        if (!isRepoSelected()) return;

        if (listModel.isEmpty()) {
            showMessageDialog(this, "No files to download.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<FileInfo> selectedFileInfoList = fileList.getSelectedValuesList();
        for (FileInfo fileInfo : selectedFileInfoList) {
            GithubDownloader.downloadFile(fileInfo.getUrl());
        }
        showMessageDialog(this, "Restart the client so the plugin(s) get shown", "Information", JOptionPane.INFORMATION_MESSAGE);
        fileList.repaint();
    }

    /**
     * Fetches the files in the specified GitHub repository folder and adds them to the list.
     */
    private void fetchFiles() {
        if (!isRepoSelected()) return;
        try {
            String json = GithubDownloader.fetchFiles(Objects.requireNonNull(repoDropdown.getSelectedItem()).toString(), folderField.getText(), tokenField.getText());
            JSONArray arr = new JSONArray(json);

            listModel.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.getString("type").equals("file") && obj.getString("name").endsWith(".jar")) {
                    String fileName = obj.getString("name");
                    String downloadUrl = obj.getString("download_url");
                    listModel.addElement(new FileInfo(fileName, downloadUrl));
                }
            }
            if (listModel.isEmpty()) {
                showMessageDialog(this, "No jar files found in repository.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (JSONException ex) {
            // show dialog box with message failed
            showMessageDialog(this, "Failed to fetch files from repository.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Creates a loading dialog with a progress bar.
     *
     * @param parent
     * @return
     */
    private JDialog createLoadingDialog(Window parent) {
        JDialog dialog = new JDialog(parent, "Please wait...", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setSize(300, 100);
        dialog.setLayout(new BorderLayout());

        JLabel label = new JLabel("Scanning Repo...", SwingConstants.CENTER);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        dialog.add(label, BorderLayout.NORTH);
        dialog.add(progressBar, BorderLayout.CENTER);
        dialog.setLocationRelativeTo(parent);
        return dialog;
    }

    /**
     * Gets the list of options from the configuration.
     *
     * @return
     */
    private List<String> getOptionsList() {
        String raw = plugin.config.repoUrls();
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
    }
}
