package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Graphics2D;
import javax.swing.SwingUtilities;

public class InputSelector {

    private static final BufferedImage ENABLED_IMAGE, DISABLED_IMAGE;

    static {
        ENABLED_IMAGE = ImageUtil.loadImageResource(Microbot.class, "enabled_small.png");
        DISABLED_IMAGE = ImageUtil.loadImageResource(Microbot.class, "disabled_small.png");
    }

    private final ClientToolbar clientToolbar;
    private NavigationButton enableButton;
    private NavigationButton disableButton;
    private NavigationButton pauseButton;
    private NavigationButton resumeButton;
    private boolean transitionPending;
    private boolean inputEnabledBeforePause;
    private boolean inputChangedDuringPause;
    private volatile boolean closed;

    public InputSelector(ClientToolbar clientToolbar) {
        this.clientToolbar = clientToolbar;
        startUp();
    }

    public void startUp() {
        enableButton = NavigationButton.builder().tab(false).icon(ENABLED_IMAGE).tooltip("Enable Input").onClick(this::enableClick).build();
        disableButton = NavigationButton.builder().tab(false).icon(DISABLED_IMAGE).tooltip("Disable Input").onClick(this::disableClick).build();
        pauseButton = NavigationButton.builder().tab(false).priority(1).icon(pauseIcon(false))
                .tooltip("Pause all scripts and enable manual input").onClick(this::togglePause).build();
        resumeButton = NavigationButton.builder().tab(false).priority(1).icon(pauseIcon(true))
                .tooltip("Scripts paused — resume all scripts").onClick(this::togglePause).build();
        addAndRemoveButtons();
        updatePauseButton();
    }

    private void addAndRemoveButtons() {
        clientToolbar.removeNavigation(enableButton);
        clientToolbar.removeNavigation(disableButton);
        clientToolbar.addNavigation(!ClientUI.getClient().isEnabled() ? enableButton : disableButton);
    }

    public void enableClick() {
        if (transitionPending) return;
        if (ScriptPauseController.isPaused()) inputChangedDuringPause = true;
        ClientUI.getClient().setEnabled(true);
        Microbot.getClient().getCanvas().setFocusable(true);
        addAndRemoveButtons();
    }

    public void disableClick() {
        if (transitionPending) return;
        if (ScriptPauseController.isPaused()) inputChangedDuringPause = true;
        ClientUI.getClient().setEnabled(false);
        Microbot.getClient().getCanvas().setFocusable(false);
        addAndRemoveButtons();
    }

    private void togglePause() {
        if (transitionPending || closed) return;
        transitionPending = true;
        boolean pause = !ScriptPauseController.isPaused();
        if (pause) {
            inputEnabledBeforePause = ClientUI.getClient().isEnabled();
            inputChangedDuringPause = false;
        }
        java.util.concurrent.CompletableFuture<Void> transition = ScriptPauseController.setPaused(pause);
        if (pause) {
            // Keep user input fenced until any bot-held keys/buttons have been released.
            ClientUI.getClient().setEnabled(false);
            Microbot.getClient().getCanvas().setFocusable(false);
            addAndRemoveButtons();
        } else if (!inputChangedDuringPause) {
            ClientUI.getClient().setEnabled(inputEnabledBeforePause);
            Microbot.getClient().getCanvas().setFocusable(inputEnabledBeforePause);
            addAndRemoveButtons();
        }
        updatePauseButton();
        transition.whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            transitionPending = false;
            if (closed) return;
            if (error != null) {
                Microbot.log("Script input release failed; scripts remain paused. Click Resume to retry.");
            }
            if (pause && error == null && ScriptPauseController.isPaused()) {
                ClientUI.getClient().setEnabled(true);
                Microbot.getClient().getCanvas().setFocusable(true);
                Microbot.getClient().getCanvas().requestFocusInWindow();
                addAndRemoveButtons();
            }
            updatePauseButton();
        }));
    }

    private void updatePauseButton() {
        clientToolbar.removeNavigation(pauseButton);
        clientToolbar.removeNavigation(resumeButton);
        clientToolbar.addNavigation(ScriptPauseController.isPaused() ? resumeButton : pauseButton);
    }

    public void shutDown() {
        closed = true;
        clientToolbar.removeNavigation(enableButton);
        clientToolbar.removeNavigation(disableButton);
        clientToolbar.removeNavigation(pauseButton);
        clientToolbar.removeNavigation(resumeButton);
    }

    private static BufferedImage pauseIcon(boolean paused) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(paused ? new Color(255, 180, 60) : new Color(220, 220, 220));
            if (paused) {
                graphics.fillPolygon(new int[]{4, 4, 13}, new int[]{2, 14, 8}, 3);
            } else {
                graphics.fillRect(3, 2, 4, 12);
                graphics.fillRect(10, 2, 4, 12);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
