package net.runelite.client.plugins.microbot.nmz;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.aiofighter.combat.PrayerPotionScript;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Nmz",
        description = "Microbot NMZ",
        tags = {"nmz", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class NmzPlugin extends Plugin {
    @Inject
    private NmzConfig config;

    @Provides
    NmzConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(NmzConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private NmzOverlay nmzOverlay;

    @Inject
    NmzScript nmzScript;
    @Inject
    PrayerPotionScript prayerPotionScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(nmzOverlay);
        }
        nmzScript.run(config);
        if (config.togglePrayerPotions()) {
            prayerPotionScript.run(config);
        }
    }

    protected void shutDown() {
        nmzScript.shutdown();
        overlayManager.remove(nmzOverlay);
        NmzScript.setHasSurge(false);
    }

    @Subscribe
    public void onActorDeath(ActorDeath actorDeath) {
        if (config.stopAfterDeath() && actorDeath.getActor() == Microbot.getClient().getLocalPlayer()) {
            Rs2Player.logout();
            shutDown();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE) {
            if (event.getMessage().equalsIgnoreCase("you feel a surge of special attack power!")) {
                NmzScript.setHasSurge(true);
            } else if (event.getMessage().equalsIgnoreCase("your surge of special attack power has ended.")) {
                NmzScript.setHasSurge(false);
            }
        }
    }
}
