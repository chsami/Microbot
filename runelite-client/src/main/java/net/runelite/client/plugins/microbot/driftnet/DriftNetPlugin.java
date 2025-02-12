package net.runelite.client.plugins.microbot.driftnet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.PluginDescriptor.Mocrosoft;

@PluginDescriptor(
        name = Mocrosoft + "Drifnet",
        description = "Microbot drifnet plugin",
        tags = {"fishing", "microbot", "hunter", "drift", "net"},
        enabledByDefault = false
)
@Slf4j
public class DriftNetPlugin extends Plugin {
    static final String CONFIG_GROUP = "driftnet";
    private static final int UNDERWATER_REGION = 15008;
    private static final String CHAT_PRODDING_FISH  = "You prod at the shoal of fish to scare it.";

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private DriftNetConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private DriftNetOverlay overlay;

    @Getter
    private static final Set<Rs2NpcModel> fish = new HashSet<>();
    @Getter
    private static final Map<Rs2NpcModel, Integer> taggedFish = new HashMap<>();
    @Getter
    private static final List<DriftNet> NETS = ImmutableList.of(
            new DriftNet(NullObjectID.NULL_31433, Varbits.NORTH_NET_STATUS, Varbits.NORTH_NET_CATCH_COUNT, ImmutableSet.of(
                    new WorldPoint(3746, 10297, 1),
                    new WorldPoint(3747, 10297, 1),
                    new WorldPoint(3748, 10297, 1),
                    new WorldPoint(3749, 10297, 1)
            )),
            new DriftNet(NullObjectID.NULL_31434, Varbits.SOUTH_NET_STATUS, Varbits.SOUTH_NET_CATCH_COUNT, ImmutableSet.of(
                    new WorldPoint(3742, 10288, 1),
                    new WorldPoint(3742, 10289, 1),
                    new WorldPoint(3742, 10290, 1),
                    new WorldPoint(3742, 10291, 1),
                    new WorldPoint(3742, 10292, 1)
            )));

    @Getter
    private boolean inDriftNetArea;
    private boolean armInteraction;

    @Getter
    private boolean driftNetsInInventory;

    @Getter
    private GameObject annette;

    @Provides
    DriftNetConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(DriftNetConfig.class);
    }

    @Inject
    private DriftNetOverlay driftnetOverlay;

    @Inject
    DriftNetScript driftnetScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(driftnetOverlay);
        }
        if (client.getGameState() == GameState.LOGGED_IN)
        {
            clientThread.invokeLater(() ->
            {
                inDriftNetArea = checkArea();
                updateDriftNetVarbits();
            });
        }
        driftnetScript.run(config);
    }

    protected void shutDown() {
        driftnetScript.shutdown();
        reset();
        overlayManager.remove(driftnetOverlay);
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() != GameState.LOGGED_IN)
        {
            annette = null;
        }
        switch (event.getGameState())
        {
            case LOGIN_SCREEN:
            case HOPPING:
            case LOADING:
                reset();
                break;
            case LOGGED_IN:
                inDriftNetArea = checkArea();
                updateDriftNetVarbits();
                break;
        }
    }

    private void reset()
    {
        fish.clear();
        taggedFish.clear();
        armInteraction = false;
        inDriftNetArea = false;
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        updateDriftNetVarbits();
    }

    private void updateDriftNetVarbits()
    {
        if (!inDriftNetArea)
        {
            return;
        }

        for (DriftNet net : NETS)
        {
            DriftNetStatus status = DriftNetStatus.of(client.getVarbitValue(net.getStatusVarbit()));
            int count = client.getVarbitValue(net.getCountVarbit());

            net.setStatus(status);
            net.setCount(count);
        }
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (armInteraction
                && event.getSource() == client.getLocalPlayer()
                && event.getTarget() instanceof NPC
                && ((NPC) event.getTarget()).getId() == NpcID.FISH_SHOAL)
        {
            tagFish(event.getTarget());
            armInteraction = false;
        }
    }

    private boolean isFishNextToNet(NPC fish, Collection<DriftNet> nets)
    {
        final WorldPoint fishTile = WorldPoint.fromLocalInstance(client, fish.getLocalLocation());
        return nets.stream().anyMatch(net -> net.getAdjacentTiles().contains(fishTile));
    }

    private boolean isTagExpired(Integer tick)
    {
        return tick + config.timeoutDelay() < client.getTickCount();
    }

    @Subscribe
    public void onGameTick(GameTick tick)
    {
        if (!inDriftNetArea)
        {
            return;
        }

        List<DriftNet> closedNets = NETS.stream()
                .filter(DriftNet::isNotAcceptingFish)
                .collect(Collectors.toList());

        taggedFish.entrySet().removeIf(entry ->
                isTagExpired(entry.getValue()) ||
                        isFishNextToNet(entry.getKey(), closedNets)
        );

        NETS.forEach(net -> net.setPrevTickStatus(net.getStatus()));

        armInteraction = false;
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!inDriftNetArea)
        {
            return;
        }

        if (event.getType() == ChatMessageType.SPAM && event.getMessage().equals(CHAT_PRODDING_FISH))
        {
            Actor target = client.getLocalPlayer().getInteracting();

            if (target instanceof NPC && ((NPC) target).getId() == NpcID.FISH_SHOAL)
            {
                tagFish(target);
            }
            else
            {
                // If the fish is on an adjacent tile, the interaction change happens after
                // the chat message is sent, so we arm it
                armInteraction = true;
            }
        }
    }

    private void tagFish(Actor fish)
    {
        Rs2NpcModel fishTarget = (Rs2NpcModel) fish;
        taggedFish.put(fishTarget, client.getTickCount());
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        final Rs2NpcModel npc = new Rs2NpcModel(event.getNpc());
        if (npc.getId() == NpcID.FISH_SHOAL)
        {
            fish.add(npc);
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        final Rs2NpcModel npc = new Rs2NpcModel(event.getNpc());
        fish.remove(npc);
        taggedFish.remove(npc);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event)
    {
        GameObject object = event.getGameObject();
        if (object.getId() == ObjectID.ANNETTE)
        {
            annette = object;
        }

        for (DriftNet net : NETS)
        {
            if (net.getObjectId() == object.getId())
            {
                net.setNet(object);
            }
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event)
    {
        GameObject object = event.getGameObject();
        if (object == annette)
        {
            annette = null;
        }

        for (DriftNet net : NETS)
        {
            if (net.getObjectId() == object.getId())
            {
                net.setNet(null);
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(final ItemContainerChanged event)
    {
        final ItemContainer itemContainer = event.getItemContainer();
        if (itemContainer != client.getItemContainer(InventoryID.INVENTORY))
        {
            return;
        }

        driftNetsInInventory = itemContainer.contains(ItemID.DRIFT_NET);
    }

    private boolean checkArea()
    {
        final Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || !client.isInInstancedRegion())
        {
            return false;
        }

        final WorldPoint point = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation());
        return point.getRegionID() == UNDERWATER_REGION;
    }
}
