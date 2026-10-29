package net.runelite.client.plugins.microbot.ui;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.config.ProfileManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.events.SessionClose;
import net.runelite.client.events.SessionOpen;
import net.runelite.client.plugins.microbot.util.security.Encryption;
import net.runelite.client.plugins.microbot.util.security.JagexCredentials;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.security.MembershipRecorder;
import net.runelite.client.plugins.screenmarkers.ScreenMarkerPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.ui.components.MouseDragEventForwarder;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
public class MicrobotProfilePanel extends MicrobotPluginPanel {
    final int MAX_PROFILES = 100;
    private static final Color CARD_BACKGROUND = new Color(40, 40, 40);
    private static final Color CARD_HOVER = new Color(50, 50, 50);
    private static final Color ACCENT_COLOR = new Color(255, 140, 0);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color MUTED_TEXT_COLOR = new Color(160, 160, 160);
    private static final Color CARD_BORDER_COLOR = new Color(60, 60, 60);
    private static final Color FIELD_BACKGROUND = new Color(55, 55, 55);
    private static final Color FIELD_HOVER_BACKGROUND = new Color(70, 70, 70);
    private static final Color LOAD_TEXT_COLOR = new Color(30, 30, 30);
    private static final Border VIEW_FIELD_BORDER = new EmptyBorder(3, 0, 3, 0);
    private static final Border EDIT_FIELD_BORDER = new CompoundBorder(new LineBorder(new Color(90, 90, 90), 1), new EmptyBorder(2, 6, 2, 6));

    private static final ImageIcon ADD_ICON = new ImageIcon(ImageUtil.loadImageResource(ScreenMarkerPlugin.class, "add_icon.png"));
    private static final ImageIcon DELETE_ICON = new ImageIcon(ImageUtil.loadImageResource(MicrobotProfilePanel.class, "mdi_delete.png"));
    private static final ImageIcon EXPORT_ICON = new ImageIcon(ImageUtil.loadImageResource(MicrobotProfilePanel.class, "mdi_export.png"));
    private static final ImageIcon RENAME_ICON;
    private static final ImageIcon RENAME_ACTIVE_ICON;
    private static final ImageIcon CLONE_ICON = new ImageIcon(ImageUtil.loadImageResource(MicrobotProfilePanel.class, "mdi_content-duplicate.png"));
    private static final ImageIcon LINK_ICON;
    private static final ImageIcon LINK_ACTIVE_ICON;
    private static final ImageIcon SYNC_ICON;
    private static final ImageIcon SYNC_ACTIVE_ICON;

    private final ConfigManager configManager;
    private final ProfileManager profileManager;
    private final SessionManager sessionManager;
    private final ScheduledExecutorService executor;

    private final DragAndDropReorderPane profilesList;
    private final JButton addButton;
    private final JButton importButton;

    @Getter
    private Map<Long, MicrobotProfilePanel.ProfileCard> cards = new HashMap<>();

    private File lastFileChooserDirectory = RuneLite.RUNELITE_DIR;

    private boolean active;

    static {
        BufferedImage rename = ImageUtil.loadImageResource(MicrobotProfilePanel.class, "mdi_rename.png");
        RENAME_ICON = new ImageIcon(rename);
        RENAME_ACTIVE_ICON = new ImageIcon(ImageUtil.recolorImage(rename, ACCENT_COLOR));

        BufferedImage link = ImageUtil.loadImageResource(MicrobotProfilePanel.class, "/util/link.png");
        LINK_ICON = new ImageIcon(link);
        LINK_ACTIVE_ICON = new ImageIcon(ImageUtil.recolorImage(link, ACCENT_COLOR));

        BufferedImage sync = ImageUtil.loadImageResource(MicrobotProfilePanel.class, "cloud_sync.png");
        SYNC_ICON = new ImageIcon(sync);
        SYNC_ACTIVE_ICON = new ImageIcon(ImageUtil.recolorImage(sync, ACCENT_COLOR));
    }

    @Inject
    MicrobotProfilePanel(
            ConfigManager configManager,
            ProfileManager profileManager,
            SessionManager sessionManager,
            ScheduledExecutorService executor
    ) {
        this.profileManager = profileManager;
        this.configManager = configManager;
        this.sessionManager = sessionManager;
        this.executor = executor;

        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        setLayout(new BorderLayout(0, 10));

        // Header Panel
        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        // Profiles List
        profilesList = new DragAndDropReorderPane();
        profilesList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        add(profilesList, BorderLayout.CENTER);

        // Bottom Panel with buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
        bottomPanel.setOpaque(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.setOpaque(false);

        addButton = createStyledButton("New Profile", ADD_ICON);
        addButton.addActionListener(ev -> createProfile());

        importButton = createStyledButton("Import", null);
        importButton.addActionListener(ev -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Profile import");
            fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("RuneLite properties", "properties"));
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.setCurrentDirectory(lastFileChooserDirectory);
            int selection = fileChooser.showOpenDialog(this);
            if (selection == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                lastFileChooserDirectory = file.getParentFile();
                importProfile(file);
            }
        });

        buttonPanel.add(addButton);
        buttonPanel.add(importButton);
        bottomPanel.add(buttonPanel, BorderLayout.WEST);

        add(bottomPanel, BorderLayout.SOUTH);

        {
            Object refresh = "this could just be a lambda, but no, it has to be abstracted";
            getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), refresh);
            getActionMap().put(refresh, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    reload();
                }
            });
        }
    }

    private JPanel createHeaderPanel() {
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Profile Manager");
        titleLabel.setFont(new Font("Roboto", Font.BOLD, 18));
        titleLabel.setForeground(TEXT_COLOR);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel("Manage your microbot profiles");
        subtitleLabel.setFont(new Font("Roboto", Font.PLAIN, 11));
        subtitleLabel.setForeground(new Color(160, 160, 160));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subtitleLabel);
        headerPanel.add(Box.createVerticalStrut(10));

        JSeparator separator = new JSeparator();
        separator.setForeground(new Color(60, 60, 60));
        separator.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerPanel.add(separator);

        return headerPanel;
    }

    private JButton createStyledButton(String text, ImageIcon icon) {
        JButton button = new JButton(text, icon);
        button.setBackground(CARD_BACKGROUND);
        button.setForeground(TEXT_COLOR);
        button.setFocusPainted(false);
        button.setFont(new Font("Roboto", Font.PLAIN, 12));
        button.setBorder(new CompoundBorder(
                new LineBorder(new Color(80, 80, 80), 1),
                new EmptyBorder(8, 16, 8, 16)
        ));
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (button.isEnabled()) {
                    button.setBackground(CARD_HOVER);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(CARD_BACKGROUND);
            }
        });

        return button;
    }

    @Override
    public void onActivate() {
        active = true;
        reload();
    }

    @Override
    public void onDeactivate() {
        active = false;
        SwingUtil.fastRemoveAll(profilesList);
        cards.clear();
    }

    @Subscribe
    private void onProfileChanged(ProfileChanged ev) {
        if (!active) {
            return;
        }

        SwingUtilities.invokeLater(() -> {
            for (MicrobotProfilePanel.ProfileCard card : cards.values()) {
                card.setActive(false);
            }

            MicrobotProfilePanel.ProfileCard card = cards.get(configManager.getProfile().getId());
            if (card == null) {
                reload();
                return;
            }
            card.setActive(true);
        });
    }

    @Subscribe
    private void onMembershipSaved(MembershipRecorder.MembershipSaved ev) {
        if (active) {
            reload();
        }
    }

    @Subscribe
    private void onRuneScapeProfileChanged(RuneScapeProfileChanged ev) {
        if (!active) {
            return;
        }

        reload();
    }

    @Subscribe
    public void onSessionOpen(SessionOpen sessionOpen) {
        if (!active) {
            return;
        }

        reload();
    }

    @Subscribe
    public void onSessionClose(SessionClose sessionClose) {
        if (!active) {
            return;
        }

        reload();
    }

    private void reload() {
        executor.submit(() -> {
            try (ProfileManager.Lock lock = profileManager.lock()) {
                reload(lock.getProfiles());
            }
        });
    }

    private void reload(List<ConfigProfile> profiles) {
        SwingUtilities.invokeLater(() -> {
            SwingUtil.fastRemoveAll(profilesList);

            Map<Long, MicrobotProfilePanel.ProfileCard> prevCards = cards;
            cards = new HashMap<>();

            long activePanel = configManager.getProfile().getId();
            final String rsProfileKey = configManager.getRSProfileKey();
            boolean limited = profiles.stream().filter(v -> !v.isInternal()).count() >= MAX_PROFILES;

            for (ConfigProfile profile : profiles) {
                if (profile.isInternal()) {
                    continue;
                }

                MicrobotProfilePanel.ProfileCard prev = prevCards.get(profile.getId());
                final long id = profile.getId();
                final List<String> defaultForRsProfiles = profile.getDefaultForRsProfiles();
                MicrobotProfilePanel.ProfileCard pc = new MicrobotProfilePanel.ProfileCard(
                        profile,
                        activePanel == id,
                        defaultForRsProfiles != null && defaultForRsProfiles.contains(rsProfileKey),
                        limited,
                        prev);
                cards.put(profile.getId(), pc);
                profilesList.add(pc);
                profilesList.add(Box.createVerticalStrut(8));
            }

            addButton.setEnabled(!limited);
            importButton.setEnabled(!limited);

            profilesList.revalidate();
        });
    }

    private void renameProfile(long id, String name) {
        try (ProfileManager.Lock lock = profileManager.lock()) {
            ConfigProfile profile = lock.findProfile(id);
            if (profile == null) {
                log.warn("rename for nonexistent profile {}", id);
                reload(lock.getProfiles());
                return;
            }

            log.info("Renaming profile {}", profile.getId());

            lock.renameProfile(profile, name);
            configManager.renameProfile(profile, name);

            reload(lock.getProfiles());
        }
    }

    private void createProfile() {
        try (ProfileManager.Lock lock = profileManager.lock()) {
            String name = "New Profile";
            int number = 1;
            while (lock.findProfile(name) != null) {
                name = "New Profile (" + (number++) + ")";
            }

            log.info("Creating new profile");
            lock.createProfile(name);

            reload(lock.getProfiles());
        }
    }

    private void deleteProfile(ConfigProfile profile) {
        log.info("Deleting profile {}", profile.getId());

        configManager.toggleSync(profile, false);

        try (ProfileManager.Lock lock = profileManager.lock()) {
            lock.removeProfile(profile.getId());

            reload(lock.getProfiles());
        }
    }

    private void unsetRsProfileDefaultProfile() {
        setRsProfileDefaultProfile(-1);
    }

    private void switchToProfile(long id) {
        switchToProfile(id, false);
    }

    private void switchToProfile(long id, boolean loginAfterSwitch) {
        ConfigProfile profile;
        try (ProfileManager.Lock lock = profileManager.lock()) {
            profile = lock.findProfile(id);
            if (profile == null) {
                log.warn("change to nonexistent profile {}", id);
                reload(lock.getProfiles());
                return;
            }

            log.debug("Switching profile to {}", profile.getName());

            lock.getProfiles().forEach(p -> p.setActive(false));
            profile.setActive(true);
            lock.dirty();
        }

        final ConfigProfile selectedProfile = profile;
        executor.submit(() -> {
            configManager.switchProfile(selectedProfile);

            if (loginAfterSwitch) {
                initiateLogin();
            }
        });
    }

    private void initiateLogin() {
        executor.submit(() -> {
            try {
                LoginManager.login();
            } catch (Exception ex) {
                log.error("Unable to login with active profile", ex);
            }
        });
    }

    private void setRsProfileDefaultProfile(long id) {
        executor.submit(() -> {
            boolean switchProfile = false;
            try (ProfileManager.Lock lock = profileManager.lock()) {
                final String rsProfileKey = configManager.getRSProfileKey();
                if (rsProfileKey == null) {
                    return;
                }

                for (final ConfigProfile profile : lock.getProfiles()) {
                    final List<String> defaultForRsProfiles = profile.getDefaultForRsProfiles();
                    if (defaultForRsProfiles == null) {
                        continue;
                    }
                    if (profile.getDefaultForRsProfiles().remove(rsProfileKey)) {
                        lock.dirty();
                    }
                }

                if (id == -1) {
                    log.debug("Unsetting default profile for rsProfile {}", rsProfileKey);
                } else {
                    final ConfigProfile profile = lock.findProfile(id);
                    if (profile == null) {
                        log.warn("setting nonexistent profile {} as default for rsprofile", id);
                        reload(lock.getProfiles());
                        return;
                    }

                    log.debug("Setting profile {} as default for rsProfile {}", profile.getName(), rsProfileKey);

                    if (profile.getDefaultForRsProfiles() == null) {
                        profile.setDefaultForRsProfiles(new ArrayList<>());
                    }
                    profile.getDefaultForRsProfiles().add(rsProfileKey);
                    switchProfile = !profile.isActive();
                    lock.dirty();
                }

                reload(lock.getProfiles());
            }

            if (switchProfile) {
                switchToProfile(id);
            }
        });
    }

    private void exportProfile(ConfigProfile profile, File file) {
        log.info("Exporting profile {}", profile.getId());

        executor.execute(() -> {
            configManager.sendConfig();

            File source = ProfileManager.profileConfigFile(profile);
            if (!source.exists()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Profile '" + profile.getName() + "' can not be exported because it has no settings."));
                return;
            }

            try {
                Files.copy(
                        source.toPath(),
                        file.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (IOException e) {
                log.error("error performing profile export", e);
            }
        });
    }

    private class ProfileCard extends JPanel {
        private static final String HIDDEN_VALUE = "••••••";
        private static final char ECHO_CHAR = '•';

        private final ConfigProfile profile;
        private final boolean jagexAccount;
        private final JTextField name;
        private final JLabel summary;
        private final JLabel activeTag;
        private final JPasswordField password;
        private final JPasswordField bankPin;
        private final JTextField discordWebhookUrl;
        private final JButton load;
        private final JButton delete;
        private final JPanel detailsPanel;
        private final JPanel buttonPanel;
        private final JToggleButton edit;

        private Integer selectedWorld;
        private boolean expanded;
        private boolean active;
        private boolean editing;

        private ProfileCard(ConfigProfile profile, boolean isActive, boolean rsProfileDefault, boolean limited, MicrobotProfilePanel.ProfileCard prev) {
            this.profile = profile;
            this.jagexAccount = profile.getJagexSessionId() != null;
            this.selectedWorld = profile.getSelectedWorld();

            setLayout(new BorderLayout(0, 10));
            setBackground(CARD_BACKGROUND);

            KeyAdapter escapeCancelsEditing = new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopEditing(false);
                    }
                }
            };

            // Header: name, how the profile logs in, and the active tag
            name = new JTextField(profile.getName());
            name.setEditable(false);
            name.setEnabled(false);
            name.setOpaque(false);
            name.setBorder(new EmptyBorder(0, 0, 0, 0));
            name.setFont(new Font("Roboto", Font.BOLD, 12));
            name.setForeground(TEXT_COLOR);
            name.setDisabledTextColor(TEXT_COLOR);
            name.addActionListener(ev -> stopEditing(true));
            name.addKeyListener(escapeCancelsEditing);
            ((AbstractDocument) name.getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
                    super.insertString(fb, offset, filter(string), attr);
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
                    super.replace(fb, offset, length, filter(text), attrs);
                }

                private String filter(String in) {
                    return CharMatcher.noneOf("/\\<>:\"|?*\r\n\0").retainFrom(in);
                }
            });

            summary = new JLabel(summaryText());
            summary.setFont(new Font("Roboto", Font.PLAIN, 11));
            summary.setForeground(MUTED_TEXT_COLOR);

            JPanel titles = new JPanel(new BorderLayout(0, 3));
            titles.setOpaque(false);
            titles.add(name, BorderLayout.NORTH);
            titles.add(summary, BorderLayout.SOUTH);

            activeTag = new JLabel("ACTIVE");
            activeTag.setFont(new Font("Roboto", Font.BOLD, 10));
            activeTag.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
            activeTag.setVerticalAlignment(SwingConstants.TOP);

            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);
            header.add(titles, BorderLayout.CENTER);
            header.add(activeTag, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            // Details (shown when expanded)
            detailsPanel = new JPanel(new GridBagLayout());
            detailsPanel.setOpaque(false);
            detailsPanel.setVisible(false);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(2, 0, 2, 0);
            gbc.weightx = 1.0;
            gbc.gridx = 0;
            gbc.gridy = 0;

            password = createSecretField();
            password.addActionListener(ev -> stopEditing(true));
            password.addKeyListener(escapeCancelsEditing);
            bankPin = createSecretField();
            bankPin.addActionListener(ev -> stopEditing(true));
            bankPin.addKeyListener(escapeCancelsEditing);
            discordWebhookUrl = createDetailField();
            discordWebhookUrl.putClientProperty("JTextField.placeholderText", "Discord webhook URL");
            discordWebhookUrl.addActionListener(ev -> stopEditing(true));
            discordWebhookUrl.addKeyListener(escapeCancelsEditing);

            if (jagexAccount) {
                JTextField login = createDetailField();
                login.setText("Jagex Account");
                detailsPanel.add(createLabeledField("Login", login), gbc);
            } else {
                detailsPanel.add(createLabeledField("Password", password), gbc);
            }
            gbc.gridy++;
            detailsPanel.add(createLabeledField("Bank PIN", bankPin), gbc);
            gbc.gridy++;
            detailsPanel.add(createLabeledField("Webhook", discordWebhookUrl), gbc);
            showValues();

            JPanel worldPanel = new JPanel(new BorderLayout(4, 0));
            worldPanel.setOpaque(false);
            worldPanel.add(createLabel("World"), BorderLayout.WEST);
            worldPanel.add(createWorldSelectorButton(), BorderLayout.CENTER);
            gbc.gridy++;
            gbc.insets = new Insets(6, 0, 2, 0);
            detailsPanel.add(worldPanel, gbc);

            // Read from the game at each login; see MembershipRecorder
            JTextField membership = createDetailField();
            membership.setText(MembershipRecorder.describe(profile, System.currentTimeMillis()));
            membership.setToolTipText("Updated from the game each time this profile logs in");
            gbc.gridy++;
            gbc.insets = new Insets(2, 0, 2, 0);
            detailsPanel.add(createLabeledField("Account", membership), gbc);

            load = new JButton();
            load.setBackground(ACCENT_COLOR);
            load.setForeground(LOAD_TEXT_COLOR);
            load.setFocusPainted(false);
            load.setFont(new Font("Roboto", Font.BOLD, 12));
            load.setBorder(new EmptyBorder(6, 8, 6, 8));
            load.setCursor(new Cursor(Cursor.HAND_CURSOR));
            load.addActionListener(ev -> {
                if (active) {
                    initiateLogin();
                } else {
                    switchToProfile(profile.getId(), true);
                }
            });
            gbc.gridy++;
            gbc.insets = new Insets(8, 0, 0, 0);
            detailsPanel.add(load, gbc);

            add(detailsPanel, BorderLayout.CENTER);

            // Profile actions (shown when expanded)
            buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.setVisible(false);

            edit = new JToggleButton(RENAME_ICON);
            edit.setSelectedIcon(RENAME_ACTIVE_ICON);
            edit.setToolTipText("Edit profile");
            SwingUtil.removeButtonDecorations(edit);
            edit.addActionListener(ev -> {
                if (edit.isSelected()) {
                    startEditing();
                } else {
                    stopEditing(true);
                }
            });
            buttonPanel.add(edit);

            JButton clone = new JButton(CLONE_ICON);
            clone.setToolTipText("Duplicate profile");
            SwingUtil.removeButtonDecorations(clone);
            clone.addActionListener(ev -> cloneProfile(profile));
            clone.setEnabled(!limited);
            buttonPanel.add(clone);

            JButton export = new JButton(EXPORT_ICON);
            export.setToolTipText("Export profile");
            SwingUtil.removeButtonDecorations(export);
            export.addActionListener(ev -> {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("Profile export");
                fileChooser.addChoosableFileFilter(new FileNameExtensionFilter("RuneLite properties", "properties"));
                fileChooser.setAcceptAllFileFilterUsed(false);
                fileChooser.setCurrentDirectory(lastFileChooserDirectory);
                fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), profile.getName() + ".properties"));
                int selection = fileChooser.showSaveDialog(this);
                if (selection == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    lastFileChooserDirectory = file.getParentFile();
                    if (!file.getName().endsWith(".properties")) {
                        file = new File(file.getParentFile(), file.getName() + ".properties");
                    }
                    exportProfile(profile, file);
                }
            });
            buttonPanel.add(export);

            if (configManager.getRSProfileKey() != null) {
                JToggleButton defaultForRsProfile = new JToggleButton(LINK_ICON);
                SwingUtil.removeButtonDecorations(defaultForRsProfile);
                defaultForRsProfile.setSelectedIcon(LINK_ACTIVE_ICON);
                defaultForRsProfile.setSelected(rsProfileDefault);

                final List<String> defaultForRsProfiles = profile.getDefaultForRsProfiles();
                final StringBuilder tooltip = new StringBuilder("<html>");
                if (defaultForRsProfiles == null || defaultForRsProfiles.isEmpty()) {
                    tooltip.append("Set as default for current RS account");
                } else {
                    tooltip.append("Default for RS accounts:");
                    for (final String rsProfileKey : profile.getDefaultForRsProfiles()) {
                        final String ign = configManager.getConfiguration(ConfigManager.RSPROFILE_GROUP, rsProfileKey, ConfigManager.RSPROFILE_DISPLAY_NAME);
                        if (Strings.isNullOrEmpty(ign)) {
                            continue;
                        }

                        final RuneScapeProfileType worldType = configManager.getConfiguration(ConfigManager.RSPROFILE_GROUP, rsProfileKey, ConfigManager.RSPROFILE_TYPE, RuneScapeProfileType.class);

                        tooltip.append("<br>");
                        tooltip.append(ign);
                        if (worldType != RuneScapeProfileType.STANDARD) {
                            tooltip.append(" (").append(Text.titleCase(worldType)).append(')');
                        }
                    }
                }
                tooltip.append("</html>");
                defaultForRsProfile.setToolTipText(tooltip.toString());

                defaultForRsProfile.addActionListener(ev -> {
                    if (rsProfileDefault) {
                        unsetRsProfileDefaultProfile();
                    } else {
                        setRsProfileDefaultProfile(profile.getId());
                    }
                });
                buttonPanel.add(defaultForRsProfile);
            }

            if (sessionManager.getAccountSession() != null) {
                JToggleButton sync = new JToggleButton(SYNC_ICON);
                SwingUtil.removeButtonDecorations(sync);
                sync.setSelectedIcon(SYNC_ACTIVE_ICON);
                sync.setToolTipText(profile.isSync() ? "Disable cloud sync" : "Enable cloud sync");
                sync.setSelected(profile.isSync());
                sync.addActionListener(ev -> toggleSync(ev, profile, sync.isSelected()));
                buttonPanel.add(sync);
            }

            delete = new JButton(DELETE_ICON);
            delete.setToolTipText("Delete profile");
            SwingUtil.removeButtonDecorations(delete);
            delete.addActionListener(ev -> {
                int confirm = JOptionPane.showConfirmDialog(MicrobotProfilePanel.ProfileCard.this,
                        "Are you sure you want to delete this profile?",
                        "Warning", JOptionPane.OK_CANCEL_OPTION);
                if (confirm == 0) {
                    deleteProfile(profile);
                }
            });
            buttonPanel.add(delete);

            add(buttonPanel, BorderLayout.SOUTH);

            // A click on the card opens or closes it; loading is only done with the Load button
            MouseAdapter cardListener = new MouseDragEventForwarder(profilesList) {
                @Override
                public void mouseClicked(MouseEvent ev) {
                    if (disabled(ev) && !editing && ev.getClickCount() == 1) {
                        setExpanded(!expanded);
                    }
                }

                @Override
                public void mouseEntered(MouseEvent ev) {
                    if (disabled(ev)) {
                        setBackground(CARD_HOVER);
                    }
                }

                @Override
                public void mouseExited(MouseEvent ev) {
                    if (disabled(ev)) {
                        setBackground(CARD_BACKGROUND);
                    }
                }

                private boolean disabled(MouseEvent ev) {
                    Component target = ev.getComponent();
                    if (target instanceof JTextField) {
                        return !((JTextField) target).isEditable();
                    }
                    return true;
                }
            };

            addMouseListener(cardListener);
            addMouseMotionListener(cardListener);
            addCardListener(this, cardListener);

            setActive(isActive);
            setExpanded(prev != null && prev.expanded);
        }

        // Text fields take their own mouse events, so they need the card listener too
        private void addCardListener(Container container, MouseAdapter listener) {
            for (Component component : container.getComponents()) {
                if (component instanceof JTextField) {
                    component.addMouseListener(listener);
                    component.addMouseMotionListener(listener);
                } else if (component instanceof JPanel) {
                    addCardListener((Container) component, listener);
                }
            }
        }

                private String summaryText() {
            String login = jagexAccount ? "Jagex Account" : "Legacy account";
            String world;
            if (selectedWorld == null) {
                world = "no world set";
            } else if (selectedWorld == -1) {
                world = "random members";
            } else if (selectedWorld == -2) {
                world = "random F2P";
            } else {
                world = "World " + selectedWorld;
            }
            return login + " · " + world;
        }

        private JLabel createLabel(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(MUTED_TEXT_COLOR);
            label.setFont(new Font("Roboto", Font.PLAIN, 11));
            label.setPreferredSize(new Dimension(64, 20));
            return label;
        }

        private JPanel createLabeledField(String label, JTextField field) {
            JPanel panel = new JPanel(new BorderLayout(4, 0));
            panel.setOpaque(false);
            panel.add(createLabel(label), BorderLayout.WEST);
            panel.add(field, BorderLayout.CENTER);
            return panel;
        }

        private JTextField createDetailField() {
            JTextField field = new JTextField();
            styleDetailField(field);
            return field;
        }

        private JPasswordField createSecretField() {
            JPasswordField field = new JPasswordField();
            styleDetailField(field);
            field.putClientProperty("JTextField.placeholderText", "Keep current");
            return field;
        }

        private void styleDetailField(JTextField field) {
            field.setEditable(false);
            field.setEnabled(false);
            field.setOpaque(false);
            field.setBorder(VIEW_FIELD_BORDER);
            field.setFont(new Font("Roboto", Font.PLAIN, 11));
            field.setForeground(TEXT_COLOR);
            field.setDisabledTextColor(TEXT_COLOR);
        }

        // Read-only view: secrets are never shown, empty values say so
        private void showValues() {
            showSecret(password, profile.getPassword());
            showSecret(bankPin, profile.getBankPin());
            String webhook = profile.getDiscordWebhookUrl();
            boolean set = !Strings.isNullOrEmpty(webhook);
            discordWebhookUrl.setText(set ? webhook : "Not set");
            discordWebhookUrl.setDisabledTextColor(set ? TEXT_COLOR : MUTED_TEXT_COLOR);
            discordWebhookUrl.setCaretPosition(0);
        }

        private void showSecret(JPasswordField field, String stored) {
            boolean set = !Strings.isNullOrEmpty(stored) && !stored.isBlank();
            field.setEchoChar((char) 0);
            field.setText(set ? HIDDEN_VALUE : "Not set");
            field.setDisabledTextColor(set ? TEXT_COLOR : MUTED_TEXT_COLOR);
        }

        private JButton createWorldSelectorButton() {
            JButton button = new JButton(getWorldDisplayText(selectedWorld));
            button.setBackground(FIELD_BACKGROUND);
            button.setForeground(TEXT_COLOR);
            button.setFocusPainted(false);
            button.setBorder(new EmptyBorder(4, 8, 4, 8));
            button.setFont(new Font("Roboto", Font.PLAIN, 11));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            button.setHorizontalAlignment(SwingConstants.LEFT);

            button.addActionListener(e -> {
                WorldSelectorDialog dialog = new WorldSelectorDialog((JFrame) SwingUtilities.getWindowAncestor(this));
                dialog.setVisible(true);

                Integer world = dialog.getSelectedWorld();
                if (world != null) {
                    selectedWorld = world;
                    configManager.setSelectedWorld(profile, world);
                    button.setText(getWorldDisplayText(world));
                    summary.setText(summaryText());
                }
            });

            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    button.setBackground(FIELD_HOVER_BACKGROUND);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    button.setBackground(FIELD_BACKGROUND);
                }
            });

            return button;
        }

        private String getWorldDisplayText(Integer worldId) {
            if (worldId == null) {
                return "Select a world...";
            } else if (worldId == -1) {
                return "Random members";
            } else if (worldId == -2) {
                return "Random F2P";
            } else {
                return "World " + worldId;
            }
        }

        void setActive(boolean active) {
            this.active = active;
            setBorder(new CompoundBorder(
                    new LineBorder(active ? ACCENT_COLOR : CARD_BORDER_COLOR, 1),
                    new EmptyBorder(12, 12, 12, 12)
            ));
            activeTag.setVisible(active);
            load.setText("Load profile");
            load.setToolTipText("Fill in this profile's login and log in");
            delete.setEnabled(!active);
        }

        void setExpanded(boolean expanded) {
            this.expanded = expanded;
            detailsPanel.setVisible(expanded);
            buttonPanel.setVisible(expanded);
            if (editing) {
                stopEditing(true);
            }
            revalidate();
        }

        private void startEditing() {
            editing = true;
            for (JTextField field : editableFields()) {
                field.setEnabled(true);
                field.setEditable(true);
                field.setOpaque(true);
                field.setBackground(FIELD_BACKGROUND);
                field.setBorder(EDIT_FIELD_BORDER);
            }
            // Secrets start empty; leaving them empty keeps the saved value
            password.setText("");
            password.setEchoChar(ECHO_CHAR);
            bankPin.setText("");
            bankPin.setEchoChar(ECHO_CHAR);
            discordWebhookUrl.setText(Strings.nullToEmpty(profile.getDiscordWebhookUrl()));
            name.requestFocusInWindow();
            name.selectAll();
        }

        private void stopEditing(boolean save) {
            if (!editing) {
                return;
            }
            editing = false;
            edit.setSelected(false);
            for (JTextField field : editableFields()) {
                field.setEditable(false);
                field.setEnabled(false);
                field.setOpaque(false);
                field.setBorder(VIEW_FIELD_BORDER);
            }
            name.setBorder(new EmptyBorder(0, 0, 0, 0));

            if (save) {
                save();
            } else {
                name.setText(profile.getName());
                showValues();
            }
        }

        private JTextField[] editableFields() {
            return new JTextField[]{name, password, bankPin, discordWebhookUrl};
        }

        private void save() {
            try {
                String newPassword = new String(password.getPassword());
                if (!newPassword.isEmpty()) {
                    configManager.setPassword(profile, Encryption.encrypt(newPassword));
                }
                String newBankPin = new String(bankPin.getPassword()).trim();
                if (!newBankPin.isEmpty()) {
                    configManager.setBankPin(profile, Encryption.encrypt(newBankPin));
                }
            } catch (Exception e) {
                log.error("Unable to encrypt profile credentials", e);
            }
            String webhook = discordWebhookUrl.getText().trim();
            if (!webhook.equals(Strings.nullToEmpty(profile.getDiscordWebhookUrl()))) {
                configManager.setDiscordWebhookUrl(profile, webhook.isEmpty() ? null : webhook);
            }
            String newName = name.getText().trim();
            // Renaming reloads the cards, which also shows the saved values
            renameProfile(profile.getId(), newName.isEmpty() ? profile.getName() : newName);
        }
    }

    private void importProfile(File file) {
        log.info("Importing profile from {}", file);

        executor.execute(() -> {
            if (importJagexCredentials(file)) {
                return;
            }

            try (ProfileManager.Lock lock = profileManager.lock()) {
                String name = "Imported Profile";
                int number = 1;
                while (lock.findProfile(name) != null) {
                    name = "Imported Profile (" + number++ + ")";
                }

                log.debug("selected new profile name: {}", name);
                ConfigProfile profile = lock.createProfile(name);

                reload(lock.getProfiles());

                configManager.importAndMigrate(lock, file, profile);
            }
        });
    }

    /**
     * Saves the Jagex Account character in a credentials.properties file to the profile named after
     * it, creating that profile if needed. Logging in with the profile then switches to that character.
     *
     * @return false if the file is not a Jagex credentials file
     */
    private boolean importJagexCredentials(File file) {
        JagexCredentials credentials;
        String encryptedSessionId;
        try {
            credentials = JagexCredentials.load(file);
            if (credentials == null) {
                return false;
            }
            encryptedSessionId = Encryption.encrypt(credentials.getSessionId());
        } catch (Exception e) {
            log.error("Unable to read Jagex credentials from {}", file, e);
            return false;
        }

        try (ProfileManager.Lock lock = profileManager.lock()) {
            ConfigProfile profile = lock.findProfile(credentials.getDisplayName());
            if (profile == null) {
                profile = lock.createProfile(credentials.getDisplayName());
            }
            profile.setJagexSessionId(encryptedSessionId);
            profile.setJagexCharacterId(credentials.getCharacterId());
            lock.dirty();
            reload(lock.getProfiles());
        }
        log.info("Imported Jagex Account character into profile {}", credentials.getDisplayName());
        return true;
    }

    private void cloneProfile(ConfigProfile profile) {
        executor.execute(() -> {
            configManager.sendConfig();

            try (ProfileManager.Lock lock = profileManager.lock()) {
                int num = 1;
                String name;
                do {
                    name = profile.getName() + " (" + (num++) + ")";
                }
                while (lock.findProfile(name) != null);

                log.info("Cloning profile {}", profile.getId());

                ConfigProfile clonedProfile = lock.createProfile(name);
                reload(lock.getProfiles());

                File from = ProfileManager.profileConfigFile(profile);
                File to = ProfileManager.profileConfigFile(clonedProfile);

                if (from.exists()) {
                    try {
                        Files.copy(
                                from.toPath(),
                                to.toPath()
                        );
                    } catch (IOException e) {
                        log.error("error cloning profile", e);
                    }
                }
            }
        });
    }

    private void toggleSync(ActionEvent event, ConfigProfile profile, boolean sync) {
        log.info("{} sync for: {}", sync ? "Enabling" : "Disabling", profile.getId());
        configManager.toggleSync(profile, sync);
        ((JToggleButton) event.getSource()).setToolTipText(sync ? "Disable cloud sync" : "Enable cloud sync");
    }

    private void handleDrag(Component component) {
        MicrobotProfilePanel.ProfileCard c = (MicrobotProfilePanel.ProfileCard) component;
        int newPosition = profilesList.getPosition(component);
        log.debug("Drag profile {} to position {}", c.profile.getName(), newPosition);

        try (ProfileManager.Lock lock = profileManager.lock()) {
            List<ConfigProfile> profiles = lock.getProfiles();
            profiles.sort(Comparator.comparing(p -> {
                Component[] components = profilesList.getComponents();
                for (int idx = 0; idx < components.length; ++idx) {
                    MicrobotProfilePanel.ProfileCard card = (MicrobotProfilePanel.ProfileCard) components[idx];
                    if (card.profile.getId() == p.getId()) {
                        return idx;
                    }
                }
                return -1;
            }));

            lock.dirty();

            reload(profiles);
        }
    }
}
