package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ExternalPluginsChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager.OperationResult;
import net.runelite.client.plugins.microbot.externalplugins.LivePluginManager.PluginEntry;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Controls external plugin artifacts without restarting the client. */
@Singleton
public class LocalPluginsPanel extends JPanel {
    private final LivePluginManager livePluginManager;
    private final EventBus eventBus;
    private final PluginTableModel tableModel = new PluginTableModel();
    private final JTable table = new JTable(tableModel);
    private final JButton addButton = new JButton("Add JAR");
    private final JButton updateButton = new JButton("Update JAR");
    private final JButton reloadButton = new JButton("Reload");
    private final JButton removeButton = new JButton("Remove");
    private final JButton rescanButton = new JButton("Rescan folder");
    private final JButton changeFolderButton = new JButton("Change folder...");
    private final JCheckBox watchFolder = new JCheckBox("Watch folder for changes");
    private final JTextArea status = new JTextArea(3, 50);
    private final JTextArea folder = new JTextArea();
    private JDialog dialog;
    private boolean busy;
    private String shownWatchStatus = "";

    @Inject
    LocalPluginsPanel(LivePluginManager livePluginManager, EventBus eventBus) {
        super(new BorderLayout(8, 8));
        this.livePluginManager = livePluginManager;
        this.eventBus = eventBus;
        setBorder(new EmptyBorder(12, 12, 12, 12));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.add(new JLabel("Manage installed plugin JARs. Settings are retained when a plugin is removed."), BorderLayout.NORTH);
        folder.setText(livePluginManager.getPluginDirectory().toString());
        folder.setEditable(false);
        folder.setOpaque(false);
        folder.setLineWrap(true);
        folder.setWrapStyleWord(true);
        JPanel folderRow = new JPanel(new BorderLayout(8, 0));
        folderRow.setOpaque(false);
        folderRow.add(folder, BorderLayout.CENTER);
        folderRow.add(changeFolderButton, BorderLayout.EAST);
        header.add(folderRow, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getSelectionModel().addListSelectionListener(e -> updateControls());
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(65);
        table.getColumnModel().getColumn(2).setPreferredWidth(65);
        table.getColumnModel().getColumn(3).setPreferredWidth(130);
        table.getColumnModel().getColumn(4).setPreferredWidth(260);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.add(addButton);
        actions.add(updateButton);
        actions.add(reloadButton);
        actions.add(removeButton);
        actions.add(rescanButton);
        addButton.addActionListener(e -> chooseJar(null));
        updateButton.addActionListener(e -> chooseJar(selectedPlugin()));
        reloadButton.addActionListener(e -> {
            PluginEntry entry = selectedPlugin();
            if (entry != null) {
                runOperation("Reloading " + displayName(entry) + "...", () -> livePluginManager.reload(entry.getId()));
            }
        });
        removeButton.addActionListener(e -> {
            PluginEntry entry = selectedPlugin();
            if (entry != null && confirmRemove(entry)) {
                runOperation("Removing " + displayName(entry) + "...", () -> livePluginManager.remove(entry.getId()));
            }
        });
        rescanButton.addActionListener(e -> runOperation("Scanning plugin folder...", livePluginManager::rescan));
        changeFolderButton.addActionListener(e -> chooseFolder());
        watchFolder.setOpaque(false);
        watchFolder.addActionListener(e -> {
            shownWatchStatus = "";
            try {
                livePluginManager.setWatching(watchFolder.isSelected());
                setStatus(livePluginManager.isWatching() ? "Watching the plugin folder for changes." : "Folder watching is off.");
            } catch (RuntimeException ex) {
                setStatus("Unable to change folder watching: " + failureMessage(ex));
            }
            watchFolder.setSelected(livePluginManager.isWatching());
        });

        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        setStatus("Select a plugin to reload, update or remove it. Add JAR imports a plugin into the folder above.");
        JScrollPane statusScroll = new JScrollPane(status, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        // Injector diagnostics can contain many lines. Keep them scrollable instead of allowing
        // an error to expand the footer over the plugin table and action controls.
        statusScroll.setPreferredSize(new Dimension(0, 96));
        statusScroll.setMinimumSize(new Dimension(0, 72));
        statusScroll.setOpaque(false);
        statusScroll.getViewport().setOpaque(false);
        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.setOpaque(false);
        footer.add(actions, BorderLayout.NORTH);
        footer.add(watchFolder, BorderLayout.CENTER);
        footer.add(statusScroll, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);
        updateControls();
    }

    public void open(Component parent) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> open(parent));
            return;
        }
        if (dialog == null) {
            dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Local Plugins", Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            dialog.setContentPane(this);
            dialog.setMinimumSize(new Dimension(730, 380));
            dialog.setSize(850, 470);
            dialog.setLocationRelativeTo(parent);
            dialog.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent event) {
                    eventBus.unregister(LocalPluginsPanel.this);
                    dialog = null;
                }
            });
            eventBus.register(this);
        }
        refreshEntries();
        dialog.setVisible(true);
        dialog.toFront();
    }

    private void chooseJar(PluginEntry entry) {
        JFileChooser chooser = new JFileChooser(livePluginManager.getPluginDirectory().toFile());
        chooser.setDialogTitle(entry == null ? "Add plugin JAR" : "Update " + displayName(entry));
        chooser.setFileFilter(new FileNameExtensionFilter("Plugin JAR files", "jar"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (entry != null) {
            chooser.setSelectedFile(entry.getSourcePath().toFile());
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path source = chooser.getSelectedFile().toPath();
            runOperation("Loading " + source.getFileName() + "...", () -> entry == null
                    ? livePluginManager.install(source) : livePluginManager.install(source, entry.getId()));
        }
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(livePluginManager.getPluginDirectory().toFile());
        chooser.setDialogTitle("Choose the managed plugin folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        Path selected = chooser.getSelectedFile().toPath();
        if (selected.toAbsolutePath().normalize().equals(livePluginManager.getPluginDirectory())) {
            return;
        }
        if (!tableModel.entries.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this,
                    folderChangeWarning(tableModel.entries.size(),
                            (int) tableModel.entries.stream().filter(entry -> entry.getHubId() != null).count(),
                            livePluginManager.getPluginDirectory()),
                    "Change plugin folder", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
        }
        runOperation("Changing the managed plugin folder...",
                () -> livePluginManager.changePluginDirectory(selected));
    }

    static String folderChangeWarning(int plugins, int hubPlugins, Path current) {
        String count = plugins == 1 ? "the 1 plugin" : "all " + plugins + " plugins";
        String hub = hubPlugins == 0 ? "" : hubPlugins == plugins
                ? ", all installed from the Plugin Hub"
                : ", including " + hubPlugins + " installed from the Plugin Hub";
        return "Switching folders turns off and unloads " + count + " in the current folder" + hub + ".\n"
                + "Their files stay in " + current + ".\n"
                + "Plugin Hub installs will go to the new folder from now on. Continue?";
    }

    private boolean confirmRemove(PluginEntry entry) {
        Path source = entry.getSourcePath();
        String message = "Remove " + displayName(entry) + "?\n"
                + "This turns the plugin off and deletes " + source.getFileName() + " from " + source.getParent() + ".\n"
                + "Your settings are kept.";
        return JOptionPane.showConfirmDialog(this, message, "Remove plugin",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    private void runOperation(String message, Supplier<CompletableFuture<OperationResult>> operation) {
        if (busy) {
            return;
        }
        busy = true;
        shownWatchStatus = "";
        setStatus(message);
        updateControls();
        try {
            operation.get().whenComplete((result, error) -> SwingUtilities.invokeLater(() -> finishOperation(result, error)));
        } catch (RuntimeException error) {
            finishOperation(null, error);
        }
    }

    private void finishOperation(OperationResult result, Throwable error) {
        busy = false;
        if (error != null) {
            setStatus("Plugin operation failed: " + failureMessage(error));
        } else if (result == null) {
            setStatus("The plugin operation did not return a result.");
        } else {
            setStatus((result.isSuccess() ? "" : "Unable to complete: ") + result.getMessage());
        }
        refreshEntries();
    }

    private void setStatus(String message) {
        status.setText(message);
        status.setCaretPosition(0);
    }

    private static String failureMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static String displayName(PluginEntry entry) {
        return Text.removeTags(entry.getName());
    }

    private PluginEntry selectedPlugin() {
        int row = table.getSelectedRow();
        return row < 0 || row >= tableModel.entries.size() ? null : tableModel.entries.get(row);
    }

    private void refreshEntries() {
        folder.setText(livePluginManager.getPluginDirectory().toString());
        PluginEntry selected = selectedPlugin();
        String selectedId = selected == null ? null : selected.getId();
        tableModel.entries = new ArrayList<>(livePluginManager.getInstalledPlugins());
        tableModel.entries.sort(Comparator.comparing(LocalPluginsPanel::displayName, String.CASE_INSENSITIVE_ORDER));
        tableModel.fireTableDataChanged();
        for (int i = 0; i < tableModel.entries.size(); i++) {
            if (tableModel.entries.get(i).getId().equals(selectedId)) {
                table.setRowSelectionInterval(i, i);
                break;
            }
        }
        watchFolder.setSelected(livePluginManager.isWatching());
        String watchStatus = livePluginManager.getWatchStatus();
        if (watchStatus == null) {
            watchStatus = "";
        }
        if (!busy && !watchStatus.isEmpty() && !watchStatus.equals(shownWatchStatus)) {
            setStatus(watchStatus);
        }
        if (!busy) {
            shownWatchStatus = watchStatus;
        }
        updateControls();
    }

    private void updateControls() {
        boolean selected = selectedPlugin() != null;
        addButton.setEnabled(!busy);
        changeFolderButton.setEnabled(!busy);
        updateButton.setEnabled(!busy && selected);
        reloadButton.setEnabled(!busy && selected);
        removeButton.setEnabled(!busy && selected);
        rescanButton.setEnabled(!busy);
        watchFolder.setEnabled(!busy);
        table.setEnabled(!busy);
    }

    @Subscribe
    public void onExternalPluginsChanged(ExternalPluginsChanged event) {
        SwingUtilities.invokeLater(this::refreshEntries);
    }

    @Subscribe
    public void onPluginChanged(PluginChanged event) {
        SwingUtilities.invokeLater(this::refreshEntries);
    }

    private static final class PluginTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Plugin", "Version", "Running", "Status", "Source JAR"};
        private List<PluginEntry> entries = new ArrayList<>();

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            PluginEntry entry = entries.get(row);
            switch (column) {
                case 0: return displayName(entry);
                case 1: return entry.getVersion();
                case 2: return entry.isRunning() ? "Yes" : "No";
                case 3: return entry.getStatus();
                case 4: return entry.getSourcePath().toString();
                default: throw new IllegalArgumentException("Unknown plugin column: " + column);
            }
        }
    }
}
