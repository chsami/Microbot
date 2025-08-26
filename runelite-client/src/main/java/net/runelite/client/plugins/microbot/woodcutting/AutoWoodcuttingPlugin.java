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
import net.runelite.client.plugins.microbot.woodcutting.enums.ForestryEvents;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
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
    @Getter(AccessLevel.MODULE)
    public AutoWoodcuttingScript autoWoodcuttingScript;
    @Inject
    public AutoWoodcuttingConfig config;
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

    // Forestry event variables
    public final List<NPC> ritualCircles = new ArrayList<>();
    public ForestryEvents currentForestryEvent = ForestryEvents.NONE;
    public final GameObject[] saplingOrder = new GameObject[3];
    public final List<GameObject> saplingIngredients = new ArrayList<>(5);

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
        ritualCircles.clear();
        currentForestryEvent = ForestryEvents.NONE;
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
            int ingredientNum = msg.contains("first") ? 1 : (msg.contains("second") ? 2 : (msg.contains("third") ? 3 : -1));
            if (ingredientNum == -1) {
                log.debug("unable to find ingredient index from message: {}", msg);
                return;
            }

            GameObject ingredientObj = this.saplingIngredients.stream()
                    .filter(obj -> {
                        String compositionName = Rs2GameObject.getCompositionName(obj).orElse(null);
                        return compositionName != null && msg.contains(compositionName.toLowerCase());
                    })
                    .findAny()
                    .orElse(null);
            if (ingredientObj == null) {
                log.debug("unable to find ingredient from message: {}", msg);
                return;
            }

            this.saplingOrder[ingredientNum - 1] = ingredientObj;
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            this.ritualCircles.add(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();
        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1 && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            this.ritualCircles.remove(npc);
        }
    }

    @Subscribe
    public void onGameObjectSpawned(final GameObjectSpawned event) {
        GameObject gameObject = event.getGameObject();
        switch (gameObject.getId()) {
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C:
            case ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5:
                this.saplingIngredients.add(gameObject);
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
                this.saplingIngredients.remove(object);
                break;
        }
    }

    private void addEvents() {
        var eventManager = Microbot.getBlockingEventManager();

        if (config.eggEvent()) {
            eggEvent = new EggEvent(this);
            eventManager.add(eggEvent);
        }

        if (config.entlingsEvent()) {
            entlingsEvent = new EntlingsEvent(this);
            eventManager.add(entlingsEvent);
        }

        if (config.flowersEvent()) {
            flowersEvent = new FlowersEvent(this);
            eventManager.add(flowersEvent);
        }

        if (config.foxEvent()) {
            foxEvent = new FoxEvent(this);
            eventManager.add(foxEvent);
        }

        if (config.hivesEvent()) {
            hivesEvent = new HivesEvent(this);
            eventManager.add(hivesEvent);
        }

        if (config.leprechaunEvent()) {
            leprechaunEvent = new LeprechaunEvent(this);
            eventManager.add(leprechaunEvent);
        }

        if (config.ritualEvent()) {
            ritualEvent = new RitualEvent(this);
            eventManager.add(ritualEvent);
        }

        if (config.rootEvent()) {
            rootEvent = new RootEvent(this);
            eventManager.add(rootEvent);
        }

        if (config.saplingEvent()) {
            saplingEvent = new StrugglingSaplingEvent(this);
            eventManager.add(saplingEvent);
        }
    }

    private void removeEvents() {
        var eventManager = Microbot.getBlockingEventManager();

        if (eggEvent != null) {
            eventManager.remove(eggEvent);
            eggEvent = null;
        }

        if (entlingsEvent != null) {
            eventManager.remove(entlingsEvent);
            entlingsEvent = null;
        }

        if (flowersEvent != null) {
            eventManager.remove(flowersEvent);
            flowersEvent = null;
        }

        if (foxEvent != null) {
            eventManager.remove(foxEvent);
            foxEvent = null;
        }

        if (hivesEvent != null) {
            eventManager.remove(hivesEvent);
            hivesEvent = null;
        }

        if (leprechaunEvent != null) {
            eventManager.remove(leprechaunEvent);
            leprechaunEvent = null;
        }

        if (ritualEvent != null) {
            eventManager.remove(ritualEvent);
            ritualEvent = null;
        }

        if (rootEvent != null) {
            eventManager.remove(rootEvent);
            rootEvent = null;
        }

        if (saplingEvent != null) {
            eventManager.remove(saplingEvent);
            saplingEvent = null;
        }
    }

    @Subscribe
    public void onConfigChanged (ConfigChanged ev){
        if (ev.getGroup().equals(AutoWoodcuttingConfig.configGroup)) {
            if (ev.getKey().equals("enableForestry")) {
                if (config.enableForestry()) {
                    this.addEvents();
                } else {
                    this.removeEvents();
                }
            } else {
                var key = ev.getKey();
                var value = ev.getNewValue();
                if (value != null && value.equals("true")) {
                    this.addEvent(key);
                }
                else if (value != null && value.equals("false")) {
                    this.removeEvent(key);
                }
            }
        }
    }

    private void addEvent(String key){
        var eventManager = Microbot.getBlockingEventManager();
        switch (key) {
            case "eggEvent":
                eggEvent = new EggEvent(this);
                eventManager.add(eggEvent);
                break;
            case "entlingsEvent":
                entlingsEvent = new EntlingsEvent(this);
                eventManager.add(entlingsEvent);
                break;
            case "flowersEvent":
                flowersEvent = new FlowersEvent(this);
                eventManager.add(flowersEvent);
                break;
            case "foxEvent":
                foxEvent = new FoxEvent(this);
                eventManager.add(foxEvent);
                break;
            case "hivesEvent":
                hivesEvent = new HivesEvent(this);
                eventManager.add(hivesEvent);
                break;
            case "leprechaunEvent":
                leprechaunEvent = new LeprechaunEvent(this);
                eventManager.add(leprechaunEvent);
                break;
            case "ritualEvent":
                ritualEvent = new RitualEvent(this);
                eventManager.add(ritualEvent);
                break;
            case "rootEvent":
                rootEvent = new RootEvent(this);
                eventManager.add(rootEvent);
                break;
            case "saplingEvent":
                saplingEvent = new StrugglingSaplingEvent(this);
                eventManager.add(saplingEvent);
                break;
        }
    }

    private void removeEvent(String key) {
        var eventManager = Microbot.getBlockingEventManager();
        switch (key) {
            case "eggEvent":
                if (eggEvent != null) {
                    eventManager.remove(eggEvent);
                    eggEvent = null;
                }
                break;
            case "entlingsEvent":
                if (entlingsEvent != null) {
                    eventManager.remove(entlingsEvent);
                    entlingsEvent = null;
                }
                break;
            case "flowersEvent":
                if (flowersEvent != null) {
                    eventManager.remove(flowersEvent);
                    flowersEvent = null;
                }
                break;
            case "foxEvent":
                if (foxEvent != null) {
                    eventManager.remove(foxEvent);
                    foxEvent = null;
                }
                break;
            case "hivesEvent":
                if (hivesEvent != null) {
                    eventManager.remove(hivesEvent);
                    hivesEvent = null;
                }
                break;
            case "leprechaunEvent":
                if (leprechaunEvent != null) {
                    eventManager.remove(leprechaunEvent);
                    leprechaunEvent = null;
                }
                break;
            case "ritualEvent":
                if (ritualEvent != null) {
                    eventManager.remove(ritualEvent);
                    ritualEvent = null;
                }
                break;
            case "rootEvent":
                if (rootEvent != null) {
                    eventManager.remove(rootEvent);
                    rootEvent = null;
                }
                break;
            case "saplingEvent":
                if (saplingEvent != null) {
                    eventManager.remove(saplingEvent);
                    saplingEvent = null;
                }
                break;
        }
    }
}
