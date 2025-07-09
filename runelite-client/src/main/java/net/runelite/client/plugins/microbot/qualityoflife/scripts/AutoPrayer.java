package net.runelite.client.plugins.microbot.qualityoflife.scripts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.kit.KitType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.qualityoflife.QoLConfig;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponID;
import net.runelite.client.plugins.microbot.qualityoflife.enums.WeaponAnimation;

import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoPrayer extends Script {

    private long lastPkAttackTime = 0;
    private String lastPrayedStyle = null;
    private static final long PRAYER_DISABLE_DELAY_MS = 10_000;
    private Player followedPlayer = null;
    private long followEndTime = 0;

    public boolean run(QoLConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (!config.autoPrayAgainstPlayers()) return;

                handleAntiPkPrayers(config);

            } catch (Exception ex) {
                log.error("Error in AutoPrayer execution: {}", ex.getMessage(), ex);
            }
        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleAntiPkPrayers(QoLConfig config) {
        Player local = Microbot.getClient().getLocalPlayer();
        if (!(local.getInteracting() instanceof Player)) {
            // If we haven't been attacked for 10s, turn off prayers and stop following
            if (lastPrayedStyle != null && System.currentTimeMillis() - lastPkAttackTime > PRAYER_DISABLE_DELAY_MS) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, false);
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, false);
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, false);
                lastPrayedStyle = null;
                followedPlayer = null;
                followEndTime = 0;
            }
            return;
        }
        Player attacker = (Player) local.getInteracting();
        int animationId = attacker.getAnimation();
        int weaponId = attacker.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        String detectedStyle = null;

        // Aggressive mode: follow and swap based on weapon, not just animation
        if (config.aggressiveAntiPkMode()) {
            // Start or refresh follow if attacked
            followedPlayer = attacker;
            followEndTime = System.currentTimeMillis() + PRAYER_DISABLE_DELAY_MS;
        }

        // Get weapon and animation info for detection
        WeaponAnimation anim = WeaponAnimation.getByAnimationId(animationId);
        WeaponID weapon = WeaponID.getByObjectId(weaponId);
        String weaponName = weapon != null ? weapon.getItemName() : "Unknown (" + weaponId + ")";
        String animationName = anim != null ? anim.getAnimationName() : "Unknown (" + animationId + ")";

        // If following a player in aggressive mode and timer is active
        if (config.aggressiveAntiPkMode() && followedPlayer != null && System.currentTimeMillis() < followEndTime) {
            int followedWeaponId = followedPlayer.getPlayerComposition().getEquipmentId(KitType.WEAPON);
            WeaponID followedWeapon = WeaponID.getByObjectId(followedWeaponId);
            WeaponAnimation followedAnim = WeaponAnimation.getByAnimationId(animationId);
            
            if (followedWeapon != null) {
                detectedStyle = followedWeapon.getAttackType().toLowerCase();
            }
            if (followedAnim != null) {
                // Animation takes priority if present
                detectedStyle = followedAnim.getAttackType().toLowerCase();
            }
            
            String followedWeaponName = followedWeapon != null ? followedWeapon.getItemName() : "Unknown (" + followedWeaponId + ")";
            String followedAnimationName = followedAnim != null ? followedAnim.getAnimationName() : "Unknown (" + animationId + ")";
            
            log.info("Aggressive Anti-PK: Following player {} | WeaponID={} ({}) | AnimationID={} ({}) | WeaponType={} | FinalPrayer={}",
                followedPlayer.getName(), followedWeaponId, followedWeaponName, animationId, followedAnimationName, detectedStyle, detectedStyle);
                
            if (detectedStyle != null) {
                prayStyle(detectedStyle, config);
            }
        } else {
            // Normal mode: use animation/weapon detection on attacker
            if (weapon != null) {
                detectedStyle = weapon.getAttackType().toLowerCase();
            }
            if (anim != null) {
                detectedStyle = anim.getAttackType().toLowerCase();
            }
            
            log.info("[Anti-PK Debug] AnimationID={} ({}) | WeaponID={} ({}) | WeaponType={} | FinalPrayer={}",
                animationId, animationName, weaponId, weaponName, detectedStyle, detectedStyle);
                
            if (detectedStyle != null) {
                prayStyle(detectedStyle, config);
            }
        }
    }

    private void prayStyle(String style, QoLConfig config) {
        boolean shouldChange = !style.equals(lastPrayedStyle)
            || (style.equals("melee") && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MELEE))
            || (style.equals("magic") && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_MAGIC))
            || (style.equals("ranged") && !Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_RANGE));

        if (shouldChange) {
            if ("melee".equals(style)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
            } else if ("ranged".equals(style)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE, true);
            } else if ("magic".equals(style)) {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
            }
            lastPrayedStyle = style;
        }

        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, config.enableProtectItemPrayer());

        lastPkAttackTime = System.currentTimeMillis();
    }

    public boolean isFollowingPlayer(Player player) {
        return followedPlayer != null && player != null && player.getName().equals(followedPlayer.getName());
    }

    public void handleAggressivePrayerOnGearChange(Player player, QoLConfig config) {
        int weaponId = player.getPlayerComposition().getEquipmentId(KitType.WEAPON);
        WeaponID weapon = WeaponID.getByObjectId(weaponId);
        if (weapon != null) {
            String detectedStyle = weapon.getAttackType().toLowerCase();
            prayStyle(detectedStyle, config);
            log.info("Aggressive Anti-PK (Immediate): Detected gear swap for {} | WeaponID={} | Style={}", player.getName(), weaponId, detectedStyle);
        }
    }

    public Player getFollowedPlayer() {
        return followedPlayer;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        log.info("AutoPrayer shutdown complete.");
    }
}
