package net.runelite.client.plugins.microbot.runecrafting.gotr;

import com.google.inject.Provides;
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
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.qualityoflife.scripts.pouch.PouchOverlay;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Matcher;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "GuardiansOfTheRift",
        description = "Guardians of the rift plugin",
        tags = {"runecrafting", "guardians of the rift", "gotr", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class GotrPlugin extends Plugin {
    @Inject
    private GotrConfig config;

    @Provides
    GotrConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(GotrConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private GotrOverlay gotrOverlay;
    @Inject
    private PouchOverlay pouchOverlay;
    @Inject
    GotrScript gotrScript;

    public GotrConfig getConfig() {
        return config;
    }


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(pouchOverlay);
            overlayManager.add(gotrOverlay);
        }
        gotrScript.run(config);
    }

    protected void shutDown() {
        gotrScript.shutdown();
        overlayManager.remove(gotrOverlay);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOADING) {
            GotrScript.resetPlugin();
        } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
            GotrScript.isInMiniGame = false;
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned) {
        NPC npc = npcSpawned.getNpc();
        if (npc.getId() == GotrScript.greatGuardianId) {
            GotrScript.greatGuardian = npc;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        NPC npc = npcDespawned.getNpc();
        if (npc.getId() == GotrScript.greatGuardianId) {
            GotrScript.greatGuardian = null;
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
            GotrScript.nextGameStart = Optional.empty();
        }

        if (msg.contains("The rift becomes active!")) {
            if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
                BreakHandlerScript.setLockState(true);
            }
            GotrScript.nextGameStart = Optional.empty();
            GotrScript.timeSincePortal = Optional.of(Instant.now());
            GotrScript.isFirstPortal = true;
            GotrScript.state = GotrState.ENTER_GAME;
        } else if (msg.contains("The rift will become active in 30 seconds.")) {
            if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
                BreakHandlerScript.setLockState(true);
            }
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(30));
        } else if (msg.contains("The rift will become active in 10 seconds.")) {
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(10));
        } else if (msg.contains("The rift will become active in 5 seconds.")) {
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(5));
        } else if (msg.contains("The Portal Guardians will keep their rifts open for another 30 seconds.")) {
            GotrScript.shouldMineGuardianRemains = true;
            GotrScript.nextGameStart = Optional.of(Instant.now().plusSeconds(60));
        }else if (msg.toLowerCase().contains("closed the rift!") || msg.toLowerCase().contains("The great guardian was defeated!")) {
            if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
            Global.sleep(Rs2Random.randomGaussian(2000, 300));
            BreakHandlerScript.setLockState(false);
            }
            GotrScript.shouldMineGuardianRemains = true;
        }

        Matcher rewardPointMatcher = GotrScript.rewardPointPattern.matcher(msg);
        if (rewardPointMatcher.find()) {
            GotrScript.elementalRewardPoints = Integer.parseInt(rewardPointMatcher.group(1).replaceAll(",", ""));
            GotrScript.catalyticRewardPoints = Integer.parseInt(rewardPointMatcher.group(2).replaceAll(",", ""));
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        if (GotrScript.isGuardianPortal(gameObject)) {
            GotrScript.guardians.add(gameObject);
        }

        if (gameObject.getId() == GotrScript.portalId) {
            Microbot.getClient().setHintArrow(gameObject.getWorldLocation());
            if(GotrScript.isFirstPortal) {
                GotrScript.isFirstPortal = false;
            }
            GotrScript.timeSincePortal = Optional.of(Instant.now());
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject gameObject = event.getGameObject();

        GotrScript.guardians.remove(gameObject);
        GotrScript.activeGuardianPortals.remove(gameObject);

        if (gameObject.getId() == GotrScript.portalId) {
            Microbot.getClient().clearHintArrow();
            GotrScript.timeSincePortal = Optional.of(Instant.now());
        }
    }
    

}
