package net.runelite.client.plugins.microbot.frosty.artioprayer;

import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.Skill;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2Potion;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.frosty.artioprayer.BossData;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Random;

import static net.runelite.client.plugins.microbot.Microbot.log;

@PluginDescriptor(
        name = PluginDescriptor.FrostyX + "Artio Prayer",
        description = "Handles prayer at Artio",
        tags = {"Artio", "pvm", "prayer"},
        enabledByDefault = false
)
public class ArtioPrayerPlugin extends Plugin {
    @Getter
    private final BossData boss = BossData.ARTIO;

    @Getter
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ArtioPrayerOverlay overlay;

    @Getter
    private Rs2PrayerEnum currentPrayer = Rs2PrayerEnum.PROTECT_RANGE;
    private int lastAttackTick = -1;
    private int lastMageProjectileTick = -1;
    private WorldPoint lastNpcPosition = null;
    private int movingForTicks = 0;

    @Override
    protected void startUp() {
        overlayManager.add(overlay);
    }
    @Override
    protected void shutDown() {
        Rs2Prayer.disableAllPrayers();
        overlayManager.remove(overlay);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        int maxPrayer = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
        int currentPrayerLvl = Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER);
        int randomThreshold = (int) (maxPrayer * (0.2 + new Random().nextDouble() * 0.2));
        int currentTick = client.getTickCount();

        NPC artio = Rs2Npc.getNpc(boss.getNpcId());
        if (artio == null || artio.isDead()) {
            Rs2Prayer.disableAllPrayers();
            lastNpcPosition = null;
            movingForTicks = 0;
            return;
        }

        if (!Rs2Prayer.isQuickPrayerEnabled()) {
            Rs2Prayer.toggleQuickPrayer(true);
        }
        if (lastMageProjectileTick != -1 && currentTick - lastMageProjectileTick >=2) {
            switchPrayer(Rs2PrayerEnum.PROTECT_RANGE);
            lastMageProjectileTick = -1;
        }
        if (currentPrayerLvl <= randomThreshold) {
            if (Rs2Inventory.interact(Rs2Potion.getPrayerPotionsVariants(), "Drink")) {
                log("Drank");
            }
        }
    }
    
    @Subscribe
    public void onProjectileMoved(ProjectileMoved event) {
        int currentTick = client.getTickCount();
        Projectile projectile = event.getProjectile();
        Player player = client.getLocalPlayer();
        if (player == null) return;

        if (projectile.getId() == boss.getRangeProjectile()) {
            switchPrayer(Rs2PrayerEnum.PROTECT_RANGE);
        }
        if (projectile.getId() == boss.getMageProjectile()) {
            switchPrayer(Rs2PrayerEnum.PROTECT_MAGIC);
            lastMageProjectileTick = currentTick;
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!(event.getActor() instanceof NPC)) return;
        NPC npc = (NPC) event.getActor();
        if (npc.getId() != boss.getNpcId()) return;
        int animationID = npc.getAnimation();
        int currentTick = client.getTickCount();

        if (lastAttackTick != -1 && currentTick - lastAttackTick < 5) {
            return;
        }
        lastAttackTick = currentTick;

        if (animationID == boss.getMageAnimation()) {
            switchPrayer(Rs2PrayerEnum.PROTECT_MAGIC);
        } else if (animationID == boss.getRangeAnimation()) {
            switchPrayer(Rs2PrayerEnum.PROTECT_RANGE);
        }
    }

    private void switchPrayer(Rs2PrayerEnum activate) {
        if (currentPrayer != activate) {
            Rs2Prayer.toggle(activate, true);
            int attempts = 0;
            while (attempts < 2 && !Rs2Prayer.isPrayerActive(activate)) {
                Rs2Random.randomGaussian(100, 50);
                Rs2Prayer.toggle(activate, true);
                attempts ++;
            }
            if (Rs2Prayer.isPrayerActive(activate)) {
                currentPrayer = activate;
                log("Successfully switched to" + activate.name());
            } else {
                log("Faield to switch to" + activate.name());
            }
        }
    }
}
