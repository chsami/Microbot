package net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet.util.Rs2GauntletUtil;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.Rs2PvMEventManager;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.AttackAnimationRegistry;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.registry.HazardRegistry;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;

import static net.runelite.client.plugins.PluginDescriptor.VOX;

@PluginDescriptor(
    name = VOX + "Gauntlet",
    description = "automation for the gauntlet hunllef boss fight",
    tags = {"gauntlet", "corrupted", "hunllef", "pvm", "combat", "vox"},
    enabledByDefault = false
)
@Slf4j
public class MicroGauntletPlugin extends Plugin {

    @Inject
    private MicroGauntletConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private MicroGauntletOverlay overlay;

    @Inject
    private Rs2PvMEventManager pvmEventManager;

    @Inject
    private AttackAnimationRegistry attackAnimationRegistry;

    @Inject
    private HazardRegistry hazardRegistry;

    @Inject
    private net.runelite.client.eventbus.EventBus eventBus;

    private MicroGauntletScript script;

    // framework trackers (injected via event manager)
    private net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2BossPatternTracker bossPatternTracker;
    private net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2PlayerAttackTracker playerAttackTracker;
    private net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2PrayerHandler prayerHandler;

    @Override
    protected void startUp() throws Exception {
        // start PvM event manager
        pvmEventManager.start();

        // get framework trackers from event manager
        bossPatternTracker = pvmEventManager.getBossPatternTracker();
        playerAttackTracker = pvmEventManager.getPlayerAttackTracker();
        prayerHandler = net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.handlers.Rs2PrayerHandler.getInstance();

        // register Hunllef animations
        attackAnimationRegistry.registerAnimationId(Rs2GauntletUtil.BOSS_ATTACK_ANIMATION, "Hunllef generic attack");
        attackAnimationRegistry.registerAnimationId(Rs2GauntletUtil.BOSS_STOMP_ANIMATION, "Hunllef stomp");
        attackAnimationRegistry.registerAnimationId(Rs2GauntletUtil.BOSS_SWITCH_TO_MAGE_ANIMATION, "Hunllef using mage");
        attackAnimationRegistry.registerAnimationId(Rs2GauntletUtil.BOSS_SWITCH_TO_RANGE_ANIMATION, "Hunllef using range");

        // register hazards (tornadoes + floor tiles)
        // Tornadoes: 20 tick TTL (12 seconds), radius 2, chase player at ~1 tile/tick
        long tornadoTTL = 20 * net.runelite.api.Constants.GAME_TICK_LENGTH; // 20 ticks = 12000ms
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.TORNADO_NORMAL, tornadoTTL, 2,
            "Normal tornado", true, 1.0);
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.TORNADO_CORRUPTED, tornadoTTL, 2,
            "Corrupted tornado", true, 1.0);
        hazardRegistry.registerHazardousObject(Rs2GauntletUtil.DAMAGE_TILE_ID, 1, "Dangerous floor tile");

        // register prayer disable projectile (Hunllef's prayer-disabling attack)
        // projectile ID 1707 disables prayers for ~6 seconds (6000ms)
        if (prayerHandler != null) {
            prayerHandler.registerPrayerDisableProjectile(1707, 6000);
            log.debug("Registered Hunllef prayer disable projectile");
        }

        // register Hunllef NPC IDs as hazardous (for animation tracking)
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.CRYSTALLINE_HUNLLEF, 10, "Crystalline Hunllef");
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.CRYSTALLINE_HUNLLEF_9022, 10, "Hunllef (range pray)");
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.CRYSTALLINE_HUNLLEF_9023, 10, "Hunllef (mage pray)");
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.CORRUPTED_HUNLLEF, 10, "Corrupted Hunllef");
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.CORRUPTED_HUNLLEF_9036, 10, "Corrupted Hunllef (range pray)");
        hazardRegistry.registerHazardousNpc(Rs2GauntletUtil.CORRUPTED_HUNLLEF_9037, 10, "Corrupted Hunllef (mage pray)");

        if (overlayManager != null) {
            overlayManager.add(overlay);
        }

        script = new MicroGauntletScript(bossPatternTracker, playerAttackTracker, prayerHandler, eventBus);
        script.run(config);

        log.info("gauntlet plugin started - event-driven mode with framework trackers");
    }

    @Override
    protected void shutDown() throws Exception {
        // unregister animations
        attackAnimationRegistry.unregisterAnimationId(Rs2GauntletUtil.BOSS_ATTACK_ANIMATION);
        attackAnimationRegistry.unregisterAnimationId(Rs2GauntletUtil.BOSS_STOMP_ANIMATION);
        attackAnimationRegistry.unregisterAnimationId(Rs2GauntletUtil.BOSS_SWITCH_TO_MAGE_ANIMATION);
        attackAnimationRegistry.unregisterAnimationId(Rs2GauntletUtil.BOSS_SWITCH_TO_RANGE_ANIMATION);

        // unregister hazards
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.TORNADO_NORMAL);
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.TORNADO_CORRUPTED);
        hazardRegistry.unregisterObject(Rs2GauntletUtil.DAMAGE_TILE_ID);

        // unregister Hunllef NPC IDs
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.CRYSTALLINE_HUNLLEF);
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.CRYSTALLINE_HUNLLEF_9022);
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.CRYSTALLINE_HUNLLEF_9023);
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.CORRUPTED_HUNLLEF);
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.CORRUPTED_HUNLLEF_9036);
        hazardRegistry.unregisterNpc(Rs2GauntletUtil.CORRUPTED_HUNLLEF_9037);

        if (script != null) {
            script.shutdown();
            script = null;
        }

        if (overlayManager != null) {
            overlayManager.remove(overlay);
        }

        log.info("gauntlet plugin stopped - unregistered from PvM framework");
    }

    @Provides
    MicroGauntletConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MicroGauntletConfig.class);
    }
}
