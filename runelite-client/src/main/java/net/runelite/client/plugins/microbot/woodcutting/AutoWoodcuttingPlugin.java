package net.runelite.client.plugins.microbot.woodcutting;

import com.google.inject.Provides;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.*;
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
    @Getter(AccessLevel.PACKAGE)
    public AutoWoodcuttingScript autoWoodcuttingScript;
    @Inject
    @Getter(AccessLevel.PACKAGE)
    private AutoWoodcuttingConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoWoodcuttingOverlay woodcuttingOverlay;

    private EggEvent eggEvent;
    private EntlingsEvent entlingsEvent;
    private FlowersEvent flowersEvent;
    private FoxEvent foxEvent;
    private HivesEvent hivesEvent;
    private LeprechaunEvent leprechaunEvent;
    private RitualEvent ritualEvent;
    private RootEvent rootEvent;
    private StrugglingSaplingEvent saplingEvent;

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
        if (config.enableForestry())
            this.addEvents();
        autoWoodcuttingScript.run(config);
    }

    protected void shutDown() {
        autoWoodcuttingScript.shutdown();
        this.removeEvents();
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

        if (msg.startsWith("The sapling seems to love"))
        {
            int ingredientNum = msg.contains("first") ? 1 : (msg.contains("second") ? 2 : (msg.contains("third") ? 3 : -1));
            if (ingredientNum == -1)
            {
                log.debug("unable to find ingredient index from message: {}", msg);
                return;
            }

            GameObject ingredientObj = autoWoodcuttingScript.getSaplingIngredients().stream()
                    .filter(obj -> {
                        String compositionName = Rs2GameObject.getCompositionName(obj).orElse(null);
                        return compositionName != null && msg.contains(compositionName.toLowerCase());
                    })
                    .findAny()
                    .orElse(null);
            if (ingredientObj == null)
            {
                log.debug("unable to find ingredient from message: {}", msg);
                return;
            }

            autoWoodcuttingScript.saplingOrder[ingredientNum - 1] = ingredientObj;
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

    @Subscribe
    public void onGameObjectSpawned(final GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        switch (gameObject.getId())
        {
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5:
                autoWoodcuttingScript.getSaplingIngredients().add(gameObject);
                break;
        }
    }

    @Subscribe
    public void onGameObjectDespawned(final GameObjectDespawned event) {
        final GameObject object = event.getGameObject();

        switch (object.getId()) {
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5:
                autoWoodcuttingScript.getSaplingIngredients().remove(object);
                break;
        }
    }

    private void addEvents() {
        var eventManager = Microbot.getBlockingEventManager();

        eggEvent = new EggEvent();
        entlingsEvent = new EntlingsEvent();
        flowersEvent = new FlowersEvent();
        foxEvent = new FoxEvent();
        hivesEvent = new HivesEvent(config);
        leprechaunEvent = new LeprechaunEvent();
        ritualEvent = new RitualEvent(autoWoodcuttingScript);
        rootEvent = new RootEvent();
        saplingEvent = new StrugglingSaplingEvent(this);

        if (config.eggEvent()) {
            eventManager.add(eggEvent);
        }
        if (config.entlingsEvent()) {
            eventManager.add(entlingsEvent);
        }
        if (config.flowersEvent()) {
            eventManager.add(flowersEvent);
        }
        if (config.foxEvent()) {
            eventManager.add(foxEvent);
        }
        if (config.hivesEvent()) {
            eventManager.add(hivesEvent);
        }
        if (config.leprechaunEvent()) {
            eventManager.add(leprechaunEvent);
        }
        if (config.ritualEvent()) {
            eventManager.add(ritualEvent);
        }
        if (config.rootEvent()) {
            eventManager.add(rootEvent);
        }
        if (config.saplingEvent()) {
            eventManager.add(saplingEvent);
    }
    }
    private void removeEvents() {
        var eventManager = Microbot.getBlockingEventManager();
        eventManager.remove(eggEvent);
        eventManager.remove(entlingsEvent);
        eventManager.remove(flowersEvent);
        eventManager.remove(foxEvent);
        eventManager.remove(hivesEvent);
        eventManager.remove(leprechaunEvent);
        eventManager.remove(ritualEvent);
        eventManager.remove(rootEvent);
        eventManager.remove(saplingEvent);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged ev) {
        if (ev.getGroup().equals(AutoWoodcuttingConfig.configGroup)) {
            if (ev.getKey().equals("enableForestry")) {
                if (config.enableForestry()) {
                    this.addEvents();
                } else {
                    this.removeEvents();
                }
            } else {
                // If any other config changes, we need to re-add the events
                this.removeEvents();
                this.addEvents();
            }
        }
    }
}
