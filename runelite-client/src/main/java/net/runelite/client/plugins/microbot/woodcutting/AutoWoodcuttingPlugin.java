package net.runelite.client.plugins.microbot.woodcutting;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.StrugglingSaplingEvent;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Auto Woodcutting",
        description = "Microbot woodcutting plugin",
        tags = {"Woodcutting", "microbot", "skilling"},
        enabledByDefault = false
)
@Slf4j
public class AutoWoodcuttingPlugin extends Plugin {
    @Inject
    public AutoWoodcuttingScript autoWoodcuttingScript;
    @Inject
    private AutoWoodcuttingConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoWoodcuttingOverlay woodcuttingOverlay;

    private static final Pattern WOOD_CUT_PATTERN = Pattern.compile("You get (?:some|an)[\\w ]+(?:logs?|mushrooms)\\.");
    @Provides
    AutoWoodcuttingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoWoodcuttingConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(woodcuttingOverlay);
        }
        Microbot.blockingEventManager.add(new StrugglingSaplingEvent(this));
        autoWoodcuttingScript.run(config);
    }

    protected void shutDown() {
        autoWoodcuttingScript.shutdown();
        overlayManager.remove(woodcuttingOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.SPAM
                && event.getType() != ChatMessageType.GAMEMESSAGE
                && event.getType() != ChatMessageType.MESBOX) {
            return;
        }

        final var msg = event.getMessage();
        if (WOOD_CUT_PATTERN.matcher(msg).matches()) {
            woodcuttingOverlay.incrementLogsChopped();
        }

        if (msg.equals("you can't light a fire here.")) {
            autoWoodcuttingScript.cannotLightFire = true;
        }

        if (msg.startsWith("The sapling seems to love")) {
            int ingredientNum = msg.contains("first") ? 0 : (msg.contains("second") ? 1 : (msg.contains("third") ? 2 : -1));
            if (ingredientNum == -1) {
                return;
            }

            // Find which ingredient this message refers to
            for (TileObject ingredient : Rs2GameObject.getTileObjects(Rs2GameObject.nameMatches("leaves", false))) {
                var ingredientName = Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getObjectDefinition(ingredient.getId()).getName()).orElse(null);

                if (msg.contains(ingredientName)) {
                    autoWoodcuttingScript.saplingOrder[ingredientNum] = (GameObject) ingredient;
                    break;
                }
            }
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            autoWoodcuttingScript.ritualCircles.add(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            autoWoodcuttingScript.ritualCircles.remove(npc);
        }
    }
}
