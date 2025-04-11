package net.runelite.client.plugins.microbot.mining.motherloadmine;

import com.google.inject.Provides;

import lombok.extern.slf4j.Slf4j;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.ObjectID;
import net.runelite.api.Varbits;
import net.runelite.api.WallObject;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.Arrays;
import java.time.Instant;

@PluginDescriptor(name = PluginDescriptor.Mocrosoft
        + "MotherlodeMine", description = "A bot that mines paydirt in the motherlode mine", tags = { "paydirt", "mine",
                "motherlode" }, enabledByDefault = false)
@Slf4j
public class MotherloadMinePlugin extends Plugin {
    @Inject
    private MotherloadMineConfig config;
    @Inject
    private MotherloadMineOverlay motherloadMineOverlay;
    @Inject
    private MotherloadMineScript motherloadMineScript;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private Client client;
    @Inject
    private ClientThread clientThread;
    @Inject
    private Notifier notifier;

    @Getter
    private static final List<Integer> MLM_REGIONS = Arrays.asList(14679, 14680, 14681, 14935, 14936, 14937, 15191,
            15192, 15193);

    @Getter
    private static final List<Integer> MLM_ORE_TYPES = Arrays.asList(
            ItemID.RUNITE_ORE,

            ItemID.ADAMANTITE_ORE,
            ItemID.MITHRIL_ORE,
            ItemID.GOLD_ORE,
            ItemID.COAL,
            ItemID.GOLDEN_NUGGET);

    @Getter
    private static final int SACK_LARGE_SIZE = 189;
    @Getter
    private static final int SACK_SMALL_SIZE = 108;
    @Getter
    @Setter
    private static int maxSackSize;
    @Getter
    @Setter
    private static int curSackSize;
    private static Instant scriptStartTime;

    @Provides
    MotherloadMineConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MotherloadMineConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        Microbot.setClient(client);
        Microbot.setClientThread(clientThread);
        Microbot.setNotifier(notifier);
        scriptStartTime = Instant.now();
        overlayManager.add(motherloadMineOverlay);
        motherloadMineScript.run(config);
    }

    @Subscribe
    public void onWallObjectSpawned(WallObjectSpawned event) {
        WallObject wallObject = event.getWallObject();
        try {
            if (wallObject == null || MotherloadMineScript.oreVein == null)
                return;
            if (MotherloadMineScript.status == MLMStatus.MINING && (wallObject.getId() == ObjectID.DEPLETED_VEIN_26665
                    || wallObject.getId() == ObjectID.DEPLETED_VEIN_26666
                    || wallObject.getId() == ObjectID.DEPLETED_VEIN_26667
                    || wallObject.getId() == ObjectID.DEPLETED_VEIN_26668)) {
                if (wallObject.getWorldLocation().equals(MotherloadMineScript.oreVein.getWorldLocation())) {
                    MotherloadMineScript.oreVein = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (!checkInMlm())
            return;
        switch (event.getVarbitId()) {
            case Varbits.SACK_NUMBER:
                curSackSize = event.getValue();
                break;
            case Varbits.SACK_UPGRADED:
                maxSackSize = SACK_LARGE_SIZE;
                break;
            default:
                break;
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (!checkInMlm())
            return;
        final ItemContainer container = event.getItemContainer();
        if (Arrays.stream(container.getItems())
                .filter(item -> MLM_ORE_TYPES.contains(item.getId()))
                .count() > 0) {
            MotherloadMineScript.setStatus(MLMStatus.BANKING);
            motherloadMineScript.bankItems();
        }
        int invPayloadCount = (int) Arrays.stream(container.getItems())
                .filter(item -> ItemID.PAYDIRT == item.getId())
                .count();
        if (invPayloadCount + curSackSize >= maxSackSize && !MotherloadMineScript.getShouldEmptySack()) {
            MotherloadMineScript.setStatus(MLMStatus.DEPOSIT_HOPPER);
            MotherloadMineScript.setShouldEmptySack(true);
            motherloadMineScript.depositHopper();
        }
    }

    public static String getTimeRunning() {
        return scriptStartTime != null
                ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now())
                : "";
    }

    private boolean checkInMlm() {
        GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING) {
            return false;
        }
        int[] currentMapRegions = client.getMapRegions();
        for (int region : currentMapRegions) {
            if (!MLM_REGIONS.contains(region)) {
                return false;
            }
        }
        return true;
    }

    protected void shutDown() {
        scriptStartTime = null;
        curSackSize = 0;
        maxSackSize = 0;
        motherloadMineScript.shutdown();
        overlayManager.remove(motherloadMineOverlay);
    }
}
