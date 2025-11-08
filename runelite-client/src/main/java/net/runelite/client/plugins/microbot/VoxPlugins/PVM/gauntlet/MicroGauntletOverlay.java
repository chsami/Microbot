package net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet.util.Rs2GauntletUtil;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.Rs2PvMEventManager;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2OverheadPrayerHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2PrayerHandler;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2BossPatternTracker;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2PlayerAttackTracker;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;

/**
 * overlay for Gauntlet automation - displays framework tracker data
 *
 * Author: Voxslyvae
 */
@Slf4j
public class MicroGauntletOverlay extends OverlayPanel {

    private final MicroGauntletConfig config;

    // framework trackers
    private Rs2BossPatternTracker bossPatternTracker;
    private Rs2PlayerAttackTracker playerAttackTracker;
    private Rs2PrayerHandler prayerHandler;
    private Rs2OverheadPrayerHandler overheadPrayerHandler;

    @Inject
    public MicroGauntletOverlay(MicroGauntletConfig config) {
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) return null;
        if (!Rs2GauntletUtil.isBossFightActive()) return null;

        // get framework trackers (lazy initialization)
        if (bossPatternTracker == null) {
            initializeTrackers();
        }

        // find Hunllef
        NPC hunllef = Rs2Npc.getNpcs()
            .filter(Rs2GauntletUtil::isHunllef)
            .findFirst()
            .orElse(null);

        if (hunllef == null) {
            return null; // no hunllef found
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Gauntlet Helper v2.0")
            .color(Color.CYAN)
            .build());

        // display attack counter data
        if (config.showAttackCounter()) {
            displayAttackCounters(hunllef);
        }

        // display prayer information
        displayPrayerInfo(hunllef);

        // display weapon recommendation
        displayWeaponInfo(hunllef);

        return super.render(graphics);
    }

    /**
     * initialize framework trackers
     */
    private void initializeTrackers() {
        Rs2PvMEventManager eventManager = Rs2PvMEventManager.getInstance();
        if (eventManager != null && eventManager.isActive()) {
            bossPatternTracker = eventManager.getBossPatternTracker();
            playerAttackTracker = eventManager.getPlayerAttackTracker();
            prayerHandler = Rs2PrayerHandler.getInstance();
            overheadPrayerHandler = Rs2OverheadPrayerHandler.getInstance();
            log.debug("Gauntlet overlay initialized with framework trackers");
        }
    }

    /**
     * display boss and player attack counters
     */
    private void displayAttackCounters(NPC hunllef) {
        if (bossPatternTracker == null || playerAttackTracker == null) return;

        int npcIndex = hunllef.getIndex();

        // boss attack tracking
        if (bossPatternTracker.isTrackingBoss(npcIndex)) {
            int bossAttacks = bossPatternTracker.getTotalAttacks(npcIndex);
            int cyclePosition = bossPatternTracker.getCurrentAttackPosition(npcIndex);
            int attacksUntilSwitch = bossPatternTracker.getAttacksUntilSwitch(npcIndex);

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Boss attacks:")
                .right(String.format("%d (cycle: %d/4)", bossAttacks, cyclePosition))
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Switch in:")
                .right(String.format("%d attacks", attacksUntilSwitch))
                .rightColor(attacksUntilSwitch <= 1 ? Color.YELLOW : Color.WHITE)
                .build());
        }

        // player attack tracking
        if (playerAttackTracker.isTracking(npcIndex)) {
            int playerHits = playerAttackTracker.getHitCount(npcIndex);
            int cyclePos = playerAttackTracker.getCyclePosition(npcIndex);
            int hitsRemaining = playerAttackTracker.getHitsRemaining(npcIndex);
            double accuracy = playerAttackTracker.getAccuracy(npcIndex);

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Player hits:")
                .right(String.format("%d (cycle: %d/6)", playerHits, cyclePos))
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Weapon swap in:")
                .right(String.format("%d hits", hitsRemaining))
                .rightColor(hitsRemaining <= 1 ? Color.YELLOW : Color.WHITE)
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Accuracy:")
                .right(String.format("%.1f%%", accuracy))
                .rightColor(accuracy >= 80 ? Color.GREEN : accuracy >= 60 ? Color.YELLOW : Color.RED)
                .build());
        }
    }

    /**
     * display prayer information (both player and Hunllef's defensive prayer)
     */
    private void displayPrayerInfo(NPC hunllef) {
        if (bossPatternTracker == null || overheadPrayerHandler == null || prayerHandler == null) return;

        // player's recommended prayer (for Hunllef's attacks)
        bossPatternTracker.getNextPrayer(hunllef.getIndex()).ifPresent(nextPrayer -> {
            String prayerText = nextPrayer == Rs2PrayerEnum.PROTECT_RANGE ? "Protect Ranged" : "Protect Magic";
            Color prayerColor = nextPrayer == Rs2PrayerEnum.PROTECT_RANGE ? Color.GREEN : Color.CYAN;

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Player prayer:")
                .right(prayerText)
                .rightColor(prayerColor)
                .build());
        });

        // Hunllef's defensive prayer
        HeadIcon hunllefPrayer = overheadPrayerHandler.getOverheadPrayer(hunllef);
        if (hunllefPrayer != null) {
            String defPrayerText = hunllefPrayer == HeadIcon.MELEE ? "Protect Melee" :
                                  hunllefPrayer == HeadIcon.RANGED ? "Protect Ranged" :
                                  hunllefPrayer == HeadIcon.MAGIC ? "Protect Magic" : "Unknown";
            Color defPrayerColor = hunllefPrayer == HeadIcon.RANGED ? Color.GREEN :
                                  hunllefPrayer == HeadIcon.MAGIC ? Color.CYAN : Color.WHITE;

            panelComponent.getChildren().add(LineComponent.builder()
                .left("Hunllef protects:")
                .right(defPrayerText)
                .rightColor(defPrayerColor)
                .build());
        }

        // prayer disable warning
        if (prayerHandler.isPrayerDisabled()) {
            int ticksRemaining = prayerHandler.getPrayerDisableTicksRemaining();
            panelComponent.getChildren().add(LineComponent.builder()
                .left("PRAYER DISABLED!")
                .right(String.format("%d ticks", ticksRemaining))
                .leftColor(Color.RED)
                .rightColor(Color.RED)
                .build());
        }
    }

    /**
     * display recommended weapon based on Hunllef's prayer
     */
    private void displayWeaponInfo(NPC hunllef) {
        if (overheadPrayerHandler == null) return;

        HeadIcon hunllefPrayer = overheadPrayerHandler.getOverheadPrayer(hunllef);
        if (hunllefPrayer != null) {
            String recommendation = "";
            Color recommendColor = Color.WHITE;

            if (hunllefPrayer == HeadIcon.MELEE) {
                recommendation = "Use Ranged/Magic";
                recommendColor = Color.GREEN;
            } else if (hunllefPrayer == HeadIcon.RANGED) {
                recommendation = "Use Magic";
                recommendColor = Color.CYAN;
            } else if (hunllefPrayer == HeadIcon.MAGIC) {
                recommendation = "Use Ranged";
                recommendColor = Color.GREEN;
            }

            if (!recommendation.isEmpty()) {
                panelComponent.getChildren().add(LineComponent.builder()
                    .left("Weapon:")
                    .right(recommendation)
                    .rightColor(recommendColor)
                    .build());
            }
        }
    }
}
