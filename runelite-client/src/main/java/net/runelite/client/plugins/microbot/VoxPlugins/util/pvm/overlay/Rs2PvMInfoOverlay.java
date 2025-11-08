package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.overlay;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.Rs2PvMCombat;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2CombatHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2PrayerHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.TickLossState;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2HazardTracker;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2ProjectileTracker;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.List;
import java.util.Optional;

/**
 * info panel overlay for PvM combat system
 * displays combat stats, action queue, and system status
 * uses TOP_LEFT position for minimal screen clutter
 */
@Slf4j
@Singleton
public class Rs2PvMInfoOverlay extends OverlayPanel {

    private final Rs2PvMCombat pvmCombat;
    private final Rs2ProjectileTracker projectileTracker;
    private final Rs2HazardTracker hazardTracker;
    private final Rs2PrayerHandler prayerHandler;
    private final Rs2CombatHandler combatHandler;

    @Inject
    public Rs2PvMInfoOverlay(
        Rs2PvMCombat pvmCombat,
        Rs2ProjectileTracker projectileTracker,
        Rs2HazardTracker hazardTracker,
        Rs2PrayerHandler prayerHandler,
        Rs2CombatHandler combatHandler
    ) {
        this.pvmCombat = pvmCombat;
        this.projectileTracker = projectileTracker;
        this.hazardTracker = hazardTracker;
        this.prayerHandler = prayerHandler;
        this.combatHandler = combatHandler;

        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        if (!config.isEnableOverlay() || !config.isShowInfoPanel()) {
            return null;
        }

        try {
            panelComponent.getChildren().clear();

            // title
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("PvM Combat")
                .color(Color.CYAN)
                .build());

            // tracking stats
            renderTrackingStats();

            // prayer suggestion
            if (config.isShowPrayerSuggestion()) {
                renderPrayerSuggestion();
            }

            // tick loss state
            if (config.isShowTickLoss()) {
                renderTickLossState();
            }

            // action queue
            if (config.isShowActionQueue()) {
                renderActionQueue();
            }

            // action history
            if (config.isShowActionHistory()) {
                renderActionHistory();
            }

        } catch (Exception ex) {
            log.error("Error rendering PvM info overlay: {}", ex.getMessage(), ex);
        }

        return super.render(graphics);
    }

    /**
     * render tracking statistics (projectiles, hazards)
     */
    private void renderTrackingStats() {
        int projectileCount = projectileTracker != null ? projectileTracker.size() : 0;
        int hazardCount = hazardTracker != null ? hazardTracker.size() : 0;

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Projectiles:")
            .right(String.valueOf(projectileCount))
            .rightColor(projectileCount > 0 ? Color.YELLOW : Color.WHITE)
            .build());

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Hazards:")
            .right(String.valueOf(hazardCount))
            .rightColor(hazardCount > 0 ? Color.RED : Color.WHITE)
            .build());
    }

    /**
     * render suggested prayer switch
     */
    private void renderPrayerSuggestion() {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        if (prayerHandler == null) {
            return;
        }

        // get recommended prayer from handler
        Optional<Rs2PrayerEnum> suggested = prayerHandler.getRecommendedPrayer();

        if (suggested.isPresent()) {
            Rs2PrayerEnum prayerEnum = suggested.get();
            boolean isActive = Rs2Prayer.isPrayerActive(prayerEnum);
            Color suggestedColor = isActive ? Color.GREEN : Color.ORANGE;
            
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Suggested:")
                .right(getPrayerShortName(prayerEnum))
                .rightColor(suggestedColor)
                .build());
        }

        // show currently active protection prayer
        if (config.isHighlightActivePrayer()) {
            Rs2PrayerEnum active = null;
            
            if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC)) {
                active = Rs2PrayerEnum.PROTECT_MAGIC;
            } else if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE)) {
                active = Rs2PrayerEnum.PROTECT_RANGE;
            } else if (Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE)) {
                active = Rs2PrayerEnum.PROTECT_MELEE;
            }
            
            if (active != null) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Active:")
                    .right(getPrayerShortName(active))
                    .rightColor(Color.CYAN)
                    .build());
            }
        }
    }    /**
     * render tick loss state
     */
    private void renderTickLossState() {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        if (combatHandler == null) {
            return;
        }

        TickLossState tickLoss = combatHandler.getTickLossState();
        
        if (tickLoss != TickLossState.NONE) {
            Color warningColor = config.isWarnOnTickLoss() ? Color.RED : Color.YELLOW;
            
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Tick Loss:")
                .right(tickLoss.toString())
                .rightColor(warningColor)
                .build());
        }
    }

    /**
     * render queued actions for current tick
     */
    private void renderActionQueue() {
        if (pvmCombat == null) {
            return;
        }

        int currentTick = Microbot.getClient().getTickCount();
        List<Rs2PvMCombat.PvMAction> actions = pvmCombat.getActionsForTick(currentTick);

        if (!actions.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Queued:")
                .right(String.valueOf(actions.size()))
                .rightColor(Color.CYAN)
                .build());

            // show next 3 actions
            for (int i = 0; i < Math.min(3, actions.size()); i++) {
                Rs2PvMCombat.PvMAction action = actions.get(i);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  " + (i + 1) + ".")
                    .right(action.getDescription())
                    .rightColor(getPriorityColor(action.getPriority()))
                    .build());
            }
        }
    }

    /**
     * render recent action history
     */
    private void renderActionHistory() {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        if (pvmCombat == null) {
            return;
        }

        List<Rs2PvMCombat.ExecutedAction> history = pvmCombat.getActionHistory();
        
        if (!history.isEmpty()) {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("History:")
                .build());

            int maxEntries = config.getMaxHistoryEntries();
            for (int i = 0; i < Math.min(maxEntries, history.size()); i++) {
                Rs2PvMCombat.ExecutedAction action = history.get(i);
                Color statusColor = action.isSuccess() ? Color.GREEN : Color.RED;
                
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("  " + action.getDescription())
                    .right(action.isSuccess() ? "✓" : "✗")
                    .rightColor(statusColor)
                    .build());
            }
        }
    }

    /**
     * get short prayer name for display
     */
    private String getPrayerShortName(Rs2PrayerEnum prayer) {
        if (prayer == null) {
            return "None";
        }

        switch (prayer) {
            case PROTECT_MAGIC:
                return "Mage";
            case PROTECT_RANGE:
                return "Range";
            case PROTECT_MELEE:
                return "Melee";
            default:
                return prayer.getName();
        }
    }

    /**
     * get color for action priority
     */
    private Color getPriorityColor(int priority) {
        switch (priority) {
            case Rs2PvMCombat.Priority.PRAYER:
                return Color.RED;
            case Rs2PvMCombat.Priority.DODGE:
                return Color.ORANGE;
            case Rs2PvMCombat.Priority.WEAPON_SWITCH:
                return Color.YELLOW;
            case Rs2PvMCombat.Priority.ATTACK:
                return Color.GREEN;
            case Rs2PvMCombat.Priority.CONSUME:
                return Color.CYAN;
            default:
                return Color.WHITE;
        }
    }
}
