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
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.screenmarkers.ScreenMarkerPlugin;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.DragAndDropReorderPane;
import net.runelite.client.ui.components.MouseDragEventForwarder;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.SwingUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.*;
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

    private static final ImageIcon ADD_ICON = new ImageIcon(ImageUtil.loadImageResource(ScreenMarkerPlugin.class, "add_icon.png"));
    private static final ImageIcon DELETE_ICON = new ImageIcon(ImageUtil.loadImageResource(MicrobotProfilePanel.class, "mdi_delete.png"));
    private static final ImageIcon EXPORT_ICON = new ImageIcon(ImageUtil.loadImageResource(MicrobotProfilePanel.class, "mdi_export.png"));
    private static final ImageIcon RENAME_ICON;
    private static final ImageIcon RENAME_ACTIVE_ICON;
    private static final ImageIcon CLONE_ICON = new ImageIcon(ImageUtil.loadImageResource(MicrobotProfilePanel.class, "mdi_content-duplicate.png"));
    private static final ImageIcon LINK_ICON;
    private static final ImageIcon LINK_ACTIVE_ICON;
    private static final ImageIcon ARROW_RIGHT_ICON = new ImageIcon(ImageUtil.loadImageResource(MicrobotProfilePanel.class, "/util/arrow_right.png"));
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

            log.info("Renaming profile {} ({}) to {}", profile, profile.getId(), name);

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

            log.info("Creating new profile: {}", name);
            lock.createProfile(name);

            reload(lock.getProfiles());
        }
    }

    private void deleteProfile(ConfigProfile profile) {
        log.info("Deleting profile {}", profile.getName());

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
        log.info("Exporting profile {} to {}", profile.getName(), file);

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
        private final ConfigProfile profile;
        private final JButton delete;
        private final JTextField name;
        private final JTextField password;
        private final JTextField bankPin;
        private final JTextField discordWebhookUrl;
        private final JCheckBox member;

        private final JButton activate;
        private final JPanel detailsPanel;
        private final JPanel buttonPanel;
        private final JToggleButton rename;

        private boolean expanded;
        private boolean active;

        private ProfileCard(ConfigProfile profile, boolean isActive, boolean rsProfileDefault, boolean limited, MicrobotProfilePanel.ProfileCard prev) {
            this.profile = profile;

            setLayout(new BorderLayout(0, 8));
            setBackground(CARD_BACKGROUND);
            setBorder(new CompoundBorder(
                    new LineBorder(new Color(60, 60, 60), 1),
                    new EmptyBorder(12, 12, 12, 12)
            ));

            // Top section with name and activate button
            JPanel topPanel = new JPanel(new BorderLayout(8, 0));
            topPanel.setOpaque(false);

            name = new JTextField();
            name.setText(profile.getName());
            name.setEditable(false);
            name.setEnabled(false);
            name.setOpaque(false);
            name.setBorder(null);
            name.setFont(new Font("roboto", Font.BOLD, 12));
            name.setForeground(TEXT_COLOR);
            name.addActionListener(ev -> stopRenaming(true));
            name.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopRenaming(false);
                    }
                }
            });

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

            activate = new JButton(ARROW_RIGHT_ICON);
            activate.setDisabledIcon(ARROW_RIGHT_ICON);
            activate.addActionListener(ev -> switchToProfile(profile.getId(), true));
            SwingUtil.removeButtonDecorations(activate);
            activate.setPreferredSize(new Dimension(32, 32));

            topPanel.add(name, BorderLayout.CENTER);
            topPanel.add(activate, BorderLayout.EAST);

            add(topPanel, BorderLayout.NORTH);

            // Details Panel (collapsible)
            detailsPanel = new JPanel();
            detailsPanel.setLayout(new GridBagLayout());
            detailsPanel.setOpaque(false);
            detailsPanel.setVisible(false);

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.insets = new Insets(4, 0, 4, 0);
            gbc.weightx = 1.0;
            gbc.gridx = 0;

            // Password field
            password = createDetailField("Password", profile.getPassword());
            password.addActionListener(ev -> stopRenamingPassword(true));
            password.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopRenamingPassword(true);
                    }
                }
            });
            detailsPanel.add(createLabeledField("Password:", password), gbc);

            // Bank PIN field
            bankPin = createDetailField("Bank PIN", profile.getBankPin());
            bankPin.addActionListener(ev -> stopRenamingBankPin(true));
            bankPin.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopRenamingBankPin(true);
                    }
                }
            });
            gbc.gridy = 1;
            detailsPanel.add(createLabeledField("Bank PIN:", bankPin), gbc);

            // Discord Webhook field
            discordWebhookUrl = createDetailField("Discord Webhook", profile.getDiscordWebhookUrl());
            discordWebhookUrl.addActionListener(ev -> stopRenamingDiscordWebhookUrl(true));
            discordWebhookUrl.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopRenamingDiscordWebhookUrl(true);
                    }
                }
            });
            gbc.gridy = 2;
            detailsPanel.add(createLabeledField("Webhook:", discordWebhookUrl), gbc);

            // Member checkbox (kept for backward compatibility)
            member = new JCheckBox("Is Member");
            member.setSelected(profile.isMember());
            member.setOpaque(false);
            member.setForeground(TEXT_COLOR);
            member.addActionListener(e -> {
                configManager.setMember(profile, member.isSelected());
            });
            gbc.gridy = 3;
            detailsPanel.add(member, gbc);

            // World selector button
            JPanel worldPanel = new JPanel(new BorderLayout(4, 0));
            worldPanel.setOpaque(false);

            JLabel worldLabel = new JLabel("World:");
            worldLabel.setForeground(new Color(180, 180, 180));
            worldLabel.setFont(new Font("Roboto", Font.PLAIN, 11));
            worldLabel.setPreferredSize(new Dimension(80, 20));

            JButton worldSelector = createWorldSelectorButton(profile);
            worldPanel.add(worldLabel, BorderLayout.WEST);
            worldPanel.add(worldSelector, BorderLayout.CENTER);

            gbc.gridy = 4;
            detailsPanel.add(worldPanel, gbc);

            add(detailsPanel, BorderLayout.CENTER);

            // Button Panel
            buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            buttonPanel.setOpaque(false);
            buttonPanel.setVisible(false);

            rename = new JToggleButton(RENAME_ICON);
            rename.setSelectedIcon(RENAME_ACTIVE_ICON);
            rename.setToolTipText("Edit profile");
            SwingUtil.removeButtonDecorations(rename);
            rename.addActionListener(ev -> {
                if (rename.isSelected()) {
                    startRenaming();
                } else {
                    stopRenaming(true);
                }
            });
            buttonPanel.add(rename);

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

            // Mouse listeners for card interaction
            MouseAdapter cardListener = new MouseDragEventForwarder(profilesList) {
                @Override
                public void mouseClicked(MouseEvent ev) {
                    if (disabled(ev)) {
                        if (ev.getClickCount() == 2) {
                            if (!active) {
                                switchToProfile(profile.getId(), true);
                            } else {
                                initiateLogin();
                            }
                        } else {
                            setExpanded(!expanded);
                        }
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
                    if (target instanceof JButton) {
                        return !target.isEnabled();
                    }
                    if (target instanceof JTextField) {
                        return !((JTextField) target).isEditable();
                    }
                    return true;
                }
            };

            addMouseListener(cardListener);
            addMouseMotionListener(cardListener);
            name.addMouseListener(cardListener);
            name.addMouseMotionListener(cardListener);
            password.addMouseListener(cardListener);
            password.addMouseMotionListener(cardListener);
            discordWebhookUrl.addMouseListener(cardListener);
            discordWebhookUrl.addMouseMotionListener(cardListener);
            bankPin.addMouseListener(cardListener);
            bankPin.addMouseMotionListener(cardListener);
            activate.addMouseListener(cardListener);
            activate.addMouseMotionListener(cardListener);

            setActive(isActive);
            setExpanded(prev != null && prev.expanded);
        }

        private JPanel createLabeledField(String label, JTextField field) {
            JPanel panel = new JPanel(new BorderLayout(4, 0));
            panel.setOpaque(false);

            JLabel lblComponent = new JLabel(label);
            lblComponent.setForeground(new Color(180, 180, 180));
            lblComponent.setFont(new Font("Roboto", Font.PLAIN, 11));
            lblComponent.setPreferredSize(new Dimension(80, 20));

            panel.add(lblComponent, BorderLayout.WEST);
            panel.add(field, BorderLayout.CENTER);

            return panel;
        }

        private JTextField createDetailField(String placeholder, String value) {
            JTextField field = new JTextField();
            if (value == null || value.isEmpty()) {
                field.setText("**" + placeholder.toLowerCase() + "**");
                field.setForeground(new Color(120, 120, 120));
            } else {
                field.setText(value);
                field.setForeground(TEXT_COLOR);
            }
            field.setEditable(false);
            field.setEnabled(false);
            field.setOpaque(false);
            field.setBorder(new CompoundBorder(
                    new LineBorder(new Color(60, 60, 60), 1),
                    new EmptyBorder(4, 8, 4, 8)
            ));
            field.setFont(new Font("Monospaced", Font.PLAIN, 11));
            return field;
        }

        private JButton createWorldSelectorButton(net.runelite.client.config.ConfigProfile profile) {
            JButton button = new JButton(getWorldDisplayText(profile.getSelectedWorld()));
            button.setBackground(new Color(60, 60, 60));
            button.setForeground(TEXT_COLOR);
            button.setFocusPainted(false);
            button.setBorder(new CompoundBorder(
                    new LineBorder(new Color(60, 60, 60), 1),
                    new EmptyBorder(4, 8, 4, 8)
            ));
            button.setFont(new Font("Roboto", Font.PLAIN, 11));
            button.setCursor(new Cursor(Cursor.HAND_CURSOR));
            button.setHorizontalAlignment(SwingConstants.LEFT);

            button.addActionListener(e -> {
                WorldSelectorDialog dialog = new WorldSelectorDialog((JFrame) SwingUtilities.getWindowAncestor(this));
                dialog.setVisible(true);

                Integer selectedWorld = dialog.getSelectedWorld();
                if (selectedWorld != null) {
                    configManager.setSelectedWorld(profile, selectedWorld);
                    button.setText(getWorldDisplayText(selectedWorld));
                    // Update member status based on world selection if needed
                    if (selectedWorld == -1) {
                        configManager.setMember(profile, true);
                        member.setSelected(true);
                    } else if (selectedWorld == -2) {
                        configManager.setMember(profile, false);
                        member.setSelected(false);
                    }
                }
            });

            button.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    button.setBackground(new Color(80, 80, 80));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    button.setBackground(new Color(60, 60, 60));
                }
            });

            return button;
        }

        private String getWorldDisplayText(Integer worldId) {
            if (worldId == null) {
                return "Click to select world...";
            } else if (worldId == -1) {
                return "[Random] Members World";
            } else if (worldId == -2) {
                return "[Random] F2P World";
            } else {
                return "World " + worldId;
            }
        }

        void setActive(boolean active) {
            this.active = active;
            if (active) {
                setBorder(new CompoundBorder(
                        new LineBorder(ACCENT_COLOR, 2),
                        new EmptyBorder(11, 11, 11, 11)
                ));
            } else {
                setBorder(new CompoundBorder(
                        new LineBorder(new Color(60, 60, 60), 1),
                        new EmptyBorder(12, 12, 12, 12)
                ));
            }
            delete.setEnabled(!active);
            activate.setEnabled(expanded && !active);
        }

        void setExpanded(boolean expanded) {
            this.expanded = expanded;
            detailsPanel.setVisible(expanded);
            buttonPanel.setVisible(expanded);
            activate.setEnabled(expanded && !active);
            if (rename.isSelected()) {
                stopRenaming(true);
            }
            revalidate();
        }

        private void startRenaming() {
            name.setEnabled(true);
            name.setEditable(true);
            name.setOpaque(true);
            name.setBackground(new Color(100, 100, 100));
            name.requestFocusInWindow();
            name.selectAll();
            startRenamingPassword();
            startRenamingBankPin();
            startRenamingDiscordWebhookUrl();
        }

        private void stopRenaming(boolean save) {
            name.setEditable(false);
            name.setEnabled(false);
            name.setOpaque(false);

            rename.setSelected(false);

            if (save) {
                renameProfile(profile.getId(), name.getText().trim());
            } else {
                name.setText(profile.getName());
            }
            stopRenamingPassword(save);
            stopRenamingBankPin(save);
            stopRenamingDiscordWebhookUrl(save);
        }

        private void startRenamingPassword() {
            password.setEnabled(true);
            password.setEditable(true);
            password.setOpaque(true);
            password.setBackground(new Color(100, 100, 100));
            password.setForeground(TEXT_COLOR);
            password.requestFocusInWindow();
            password.selectAll();
        }

        private void startRenamingBankPin() {
            bankPin.setEnabled(true);
            bankPin.setEditable(true);
            bankPin.setOpaque(true);
            bankPin.setBackground(new Color(100, 100, 100));
            bankPin.setForeground(TEXT_COLOR);
            bankPin.requestFocusInWindow();
            bankPin.selectAll();
        }

        private void startRenamingDiscordWebhookUrl() {
            discordWebhookUrl.setEnabled(true);
            discordWebhookUrl.setEditable(true);
            discordWebhookUrl.setOpaque(true);
            discordWebhookUrl.setBackground(new Color(100, 100, 100));
            discordWebhookUrl.setForeground(TEXT_COLOR);
            discordWebhookUrl.requestFocusInWindow();
            discordWebhookUrl.selectAll();
        }

        private void stopRenamingDiscordWebhookUrl(boolean save) {
            discordWebhookUrl.setEditable(false);
            discordWebhookUrl.setEnabled(false);
            discordWebhookUrl.setOpaque(false);

            if (discordWebhookUrl.getText() == null || discordWebhookUrl.getText().isEmpty()) {
                return;
            }

            rename.setSelected(false);

            if (save) {
                configManager.setDiscordWebhookUrl(profile, discordWebhookUrl.getText());
            } else {
                String currentWebhookUrl = profile.getDiscordWebhookUrl();
                discordWebhookUrl.setText(currentWebhookUrl != null ? currentWebhookUrl : "**discord webhook**");
            }
        }

        private void stopRenamingPassword(boolean save) {
            password.setEditable(false);
            password.setEnabled(false);
            password.setOpaque(false);

            rename.setSelected(false);

            if (password.getText().isEmpty()) {
                return;
            }

            try {
                configManager.setPassword(profile, net.runelite.client.plugins.microbot.util.security.Encryption.encrypt(password.getText()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (save) {
                renameProfile(profile.getId(), name.getText().trim());
            } else {
                password.setText(profile.getPassword());
            }
        }

        private void stopRenamingBankPin(boolean save) {
            bankPin.setEditable(false);
            bankPin.setEnabled(false);
            bankPin.setOpaque(false);

            rename.setSelected(false);

            try {
                if (bankPin.getText().isBlank()) {
                    configManager.setBankPin(profile,"");
                } else {
                    configManager.setBankPin(profile, net.runelite.client.plugins.microbot.util.security.Encryption.encrypt(bankPin.getText()));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (save) {
                renameProfile(profile.getId(), name.getText().trim());
            } else {
                bankPin.setText(profile.getBankPin());
            }
        }
    }

    private void importProfile(File file) {
        log.info("Importing profile from {}", file);

        executor.execute(() -> {
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

                log.info("Cloning profile {} to {}", profile.getName(), name);

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
        log.info("{} sync for: {}", sync ? "Enabling" : "Disabling", profile.getName());
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
