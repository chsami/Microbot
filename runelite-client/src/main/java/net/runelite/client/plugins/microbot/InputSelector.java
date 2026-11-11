package net.runelite.client.plugins.microbot;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.input.AwtEmitter;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import org.slf4j.event.Level;

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
                .tooltip("Pause all scripts and enable manual input (actions already started in game continue)")
                .onClick(this::togglePause).build();
        resumeButton = NavigationButton.builder().tab(false).priority(1).icon(pauseIcon(true))
                .tooltip("Scripts paused - click to resume all scripts (input goes back to its previous setting)")
                .onClick(this::togglePause).build();
        if (ScriptPauseController.isPaused()) {
            inputEnabledBeforePause = ClientUI.getClient().isEnabled();
        }
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
        AwtEmitter.setCanvasFocusable(true);
        addAndRemoveButtons();
    }

    public void disableClick() {
        if (transitionPending) return;
        if (ScriptPauseController.isPaused()) inputChangedDuringPause = true;
        ClientUI.getClient().setEnabled(false);
        AwtEmitter.setCanvasFocusable(false);
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
            AwtEmitter.setCanvasFocusable(false);
            addAndRemoveButtons();
        }
        updatePauseButton();
        transition.whenComplete((ignored, error) -> SwingUtilities.invokeLater(() -> {
            transitionPending = false;
            if (closed) return;
            if (error != null) {
                String message = pause
                        ? "Pause could not release a key or mouse button held by a script. Scripts are still paused, but manual input is off. Click the Enable Input button to play yourself, or the amber play button to resume scripts."
                        : "Scripts could not resume because a key or mouse button held by a script could not be released. Scripts are still paused, and manual input remains at its current setting. Click the amber play button to retry.";
                Microbot.log(message, Level.WARN);
            }
            if (pause && error == null && ScriptPauseController.isPaused()) {
                ClientUI.getClient().setEnabled(true);
                AwtEmitter.setCanvasFocusable(true);
                Microbot.getClient().getCanvas().requestFocusInWindow();
                addAndRemoveButtons();
                Microbot.log("All scripts paused. Manual input is on. Click the amber play button next to the Enable/Disable Input button to resume.", Level.WARN);
            }
            if (!pause && error == null && !ScriptPauseController.isPaused()) {
                if (!inputChangedDuringPause) {
                    ClientUI.getClient().setEnabled(inputEnabledBeforePause);
                    AwtEmitter.setCanvasFocusable(inputEnabledBeforePause);
                    addAndRemoveButtons();
                }
                Microbot.log("All scripts resumed. If Break Handler had a break due while paused, it starts now.", Level.WARN);
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
