package net.runelite.client.plugins.microbot.runecrafting.gotr2;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch.PouchOverlay;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.Gotr2Config;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.Gotr2Overlay;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.Gotr2Script;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.runecrafting.gotr2.Gotr2State;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;

import static net.runelite.client.plugins.microbot.runecrafting.gotr2.Gotr2Script.optimizedEssenceLoop;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "GuardiansOfTheRift2",
        description = "Guardians of the rift plugin",
        tags = {"runecrafting", "guardians of the rift", "gotr2", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class Gotr2Plugin extends Plugin {
    @Getter
    @Inject
    private Gotr2Config config;

    @Provides
    Gotr2Config provideConfig(ConfigManager configManager) {
        return configManager.getConfig(Gotr2Config.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private Gotr2Overlay gotr2Overlay;
    @Inject
    private PouchOverlay pouchOverlay;
    @Inject
    Gotr2Script gotr2Script;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(pouchOverlay);
            overlayManager.add(gotr2Overlay);
        }
        Gotr2Script.state = Gotr2State.INITIALIZE;
        gotr2Script.run(config);
    }

    protected void shutDown() {
        gotr2Script.shutdown();
        Gotr2Script.state = Gotr2State.SHUTDOWN;
        overlayManager.remove(gotr2Overlay);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING) {
            Gotr2Script.resetPlugin();
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            Gotr2Script.isInMiniGame = false;
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned) {
        NPC npc = npcSpawned.getNpc();
        if (npc.getId() == Gotr2Script.greatGuardianId) {
            Gotr2Script.greatGuardian = npc;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        NPC npc = npcDespawned.getNpc();
        if (npc.getId() == Gotr2Script.greatGuardianId) {
            Gotr2Script.greatGuardian = null;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.SPAM && chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        String msg = chatMessage.getMessage();

        if (msg.contains("You step through the portal")) {
            Microbot.getClient().clearHintArrow();
            Gotr2Script.nextGameStart = Optional.empty();
        }

        if (msg.contains("The rift becomes active!")) {
            Gotr2Script.nextGameStart = Optional.empty();
            Gotr2Script.timeSincePortal = Optional.of(Instant.now());
            Gotr2Script.isFirstPortal = true;
            Gotr2Script.state = Gotr2State.ENTER_GAME;
        } else if (msg.contains("The rift will become active in 30 seconds.")) {
            Gotr2Script.shouldMineGuardianRemains = true;
            Gotr2Script.nextGameStart = Optional.of(Instant.now().plusSeconds(30));
        } else if (msg.contains("The rift will become active in 10 seconds.")) {
            Gotr2Script.shouldMineGuardianRemains = true;
            Gotr2Script.nextGameStart = Optional.of(Instant.now().plusSeconds(10));
        } else if (msg.contains("The rift will become active in 5 seconds.")) {
            Gotr2Script.shouldMineGuardianRemains = true;
            Gotr2Script.nextGameStart = Optional.of(Instant.now().plusSeconds(5));
        } else if (msg.contains("The Portal Guardians will keep their rifts open for another 30 seconds.")) {
            Gotr2Script.shouldMineGuardianRemains = true;
            Gotr2Script.nextGameStart = Optional.of(Instant.now().plusSeconds(60));
        }else if (msg.toLowerCase().contains("closed the rift!") || msg.toLowerCase().contains("The great guardian was defeated!")) {
            Gotr2Script.shouldMineGuardianRemains = true;
        }

        Matcher rewardPointMatcher = Gotr2Script.rewardPointPattern.matcher(msg);
        if (rewardPointMatcher.find()) {
            Gotr2Script.elementalRewardPoints = Integer.parseInt(rewardPointMatcher.group(1).replaceAll(",", ""));
            Gotr2Script.catalyticRewardPoints = Integer.parseInt(rewardPointMatcher.group(2).replaceAll(",", ""));
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        if (Gotr2Script.isGuardianPortal(gameObject)) {
            Gotr2Script.guardians.add(gameObject);
        }

        if (gameObject.getId() == Gotr2Script.portalId) {
            optimizedEssenceLoop = true;
            Microbot.getClient().setHintArrow(gameObject.getWorldLocation());
            if(Gotr2Script.isFirstPortal) {
                Gotr2Script.isFirstPortal = false;
            }
            Gotr2Script.timeSincePortal = Optional.of(Instant.now());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject gameObject = event.getGameObject();

        Gotr2Script.guardians.remove(gameObject);
        Gotr2Script.activeGuardianPortals.remove(gameObject);

        if (gameObject.getId() == Gotr2Script.portalId) {
            Microbot.getClient().clearHintArrow();
            Gotr2Script.timeSincePortal = Optional.of(Instant.now());
        }
    }
}
