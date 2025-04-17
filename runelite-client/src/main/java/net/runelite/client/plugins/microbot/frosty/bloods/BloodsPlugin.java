package net.runelite.client.plugins.microbot.frosty.bloods;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.Frosty + "Bloods",
        description = "A plugin to automate Blood Rune crafting",
        tags = {"blood", "rc", "rune", "frosty"},
        enabledByDefault = false
)

public class BloodsPlugin extends Plugin {
    @Inject
    private BloodsConfig config;
    @Inject
    private Client client;
    @Provides
    BloodsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BloodsConfig.class);
    }
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BloodsOverlay bloodsOverlay;
    @Inject
    private BloodsScript bloodsScript;
    @Getter
    private GameObject pool;
    @Getter
    private GameObject pohPortal;
    @Getter
    public Instant startTime;
    @Getter
    private int totalXpGained = 0;
    @Getter
    private int startXp = 0;
    @Getter
    private WorldPoint myWorldPoint;
    @Getter
    public static String version = "v1.0.9";

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {

        GameObject pohPortal = event.getGameObject();
        ObjectComposition portalComposition = client.getObjectDefinition(pohPortal.getId());

        if (portalComposition != null) {
            if (portalComposition.getImpostorIds() != null) {
                portalComposition = portalComposition.getImpostor();
            }
            String name = portalComposition.getName().toLowerCase();
            if (name.contains("portal") && Rs2GameObject.isReachable(pohPortal)) {
                this.pohPortal = pohPortal;
            }
        }

        GameObject pool= event.getGameObject();
        ObjectComposition poolComposition = client.getObjectDefinition(pool.getId());

        if (poolComposition != null) {
            if(poolComposition.getImpostorIds() != null) {
                poolComposition = poolComposition.getImpostor();
            }
            String name = poolComposition.getName().toLowerCase();
            if (name.contains("pool") && Rs2GameObject.isReachable(pool)) {
                this.pool = pool;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (Microbot.isLoggedIn()) {
            myWorldPoint = Microbot.getClient().getLocalPlayer().getWorldLocation();
        }
    }

    @Override
    protected void startUp() throws AWTException {
        startTime = Instant.now();
        if (overlayManager != null) {
            overlayManager.add(bloodsOverlay);
        }
        startXp = client.getSkillExperience(Skill.RUNECRAFT);
        bloodsScript.run();
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(bloodsOverlay);
        bloodsScript.shutdown();
    }

    public void updateXpGained() {
        int currentXp = client.getSkillExperience(Skill.RUNECRAFT);
        totalXpGained = currentXp - startXp;
    }

    public boolean isBreakHandlerEnabled() {
        return Microbot.isPluginEnabled(BreakHandlerPlugin.class);
    }
}
