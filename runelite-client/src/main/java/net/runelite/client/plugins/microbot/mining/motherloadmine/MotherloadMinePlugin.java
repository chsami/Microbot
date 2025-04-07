package net.runelite.client.plugins.microbot.mining.motherloadmine;

import com.google.inject.Provides;
import com.sun.jna.platform.win32.COM.ITypeComp;

import java.awt.*;
import java.util.List;
import java.util.Arrays;
import java.time.Instant;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectID;
import net.runelite.api.Varbits;
import net.runelite.api.WallObject;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(name = PluginDescriptor.Mocrosoft
    + "MotherlodeMine", description = "A bot that mines paydirt in the motherlode mine", tags = { "paydirt", "mine",
        "mining", "minigame", "motherlode" }, enabledByDefault = false)
@Slf4j
public class MotherloadMinePlugin extends Plugin {
  @Getter
  public static final String VERSION = "2.0.0";

  @Inject
  private MotherloadMineConfig config;
  @Inject
  private MotherloadMineOverlay motherloadMineOverlay;
  @Inject
  private MotherloadMineScript motherloadMineScript;
  @Inject
  private Client client;
  @Inject
  private ClientThread clientThread;
  @Inject
  private Notifier notifier;
  @Inject
  private OverlayManager overlayManager;

  @Getter
  private static final List<Integer> MLM_REGIONS = Arrayw.asList(14679, 14680, 14681, 14935, 14936, 14937, 15191, 15192,
      15193);

  @Getter
  private static final List<Integer> MLM_ORE_TYPES = Arrays.asList(
      ItemID.RUNITE_ORE,
      ItemID.ADAMANTITE_ORE,
      ItemID.MITHRIL_ORE,
      ItemID.GOLD_ORE,
      ItemID.COAL,
      ItemID.GOLDEN_NUGGET);

  private static final int SACK_LARGE_SIZE = 189;
  private static final int SACK_SIZE = 108;

  private static final int UPPER_FLOOR_HEIGHT = -490;

  @Getter
  private static final Instant scriptStartTime = null;
  @Getter
  private static boolean shouldEmptySack = false;
  @Getter
  private static int maxSackSize;
  @Getter
  private static int curSackSize;;

  @Provides
  MotherloadMineConfig provideConfig(ConfigManager configManager) {
    return configManager.getConfig(MotherloadMineConfig.class);
  }

  @Override
  protected void startUp() throws AWTException {
    Microbot.pauseAllScripts = false;
    Microbot.setClient(client);
    Microbot.setClientThread(clientThread);
    Microbot.setNotifier(notifier);
    Microbot.setMouse(new VirtualMouse());
    Rs2Antiban.resetAntibanSettings();
    Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
    // Everyone makes mistakes
    Rs2AntibanSettings.simulateMistakes = true;

    scriptStartTime = Instant.now();
    overlayManager.add(motherloadMineOverlay);
    motherloadMineScript.run(config);
  }

  @Subscribe
  public void onWallObjectSpawned(WallObjectSpawned event) {
    if (!checkInMlm())
      return;
    WallObject wallObject = event.getWallObject();
    try {
      if (wallObject == null || motherloadMineScript.oreVein == null)
        return;
      if (motherloadMineScript.status == MLMStatus.MINING
          && (wallObject.getId() == ObjectID.DEPLETED_VEIN_26665
              || wallObject.getId() == ObjectID.DEPLETED_VEIN_26666
              || wallObject.getId() == ObjectID.DEPLETED_VEIN_26667
              || wallObject.getId() == ObjectID.DEPLETED_VEIN_26668)) {
        if (wallObject.getWorldLocation().equals(motherloadMineScript.oreVein.getWorldLocation())) {
          motherloadMineScript.oreVein = null;
        }
      }
    } catch (Exception e) {
      // e.printStackTrace();
      System.err.println(e.getMessage());
    }
  }

  @Subscribe
  public void onVarbitChanged(VarbitChanged event) {
    if (!checkInMlm())
      return;
    if (motherloadMineScript.checkInMlm()) {
      int lastSackValue = curSackSize;
      updateSackValues();
      shouldEmptySack = curSackSize < lastSackValue;
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
      motherloadMineScript.status = MLMStatus.BANKING;
    }
    if (Arrays.stream(container.getItems())
        .filter(item -> ItemID.PAYDIRT == item.getId())
        .count() + curSackSize >= maxSackSize) {
      motherloadMineScript.status = MLMStatus.DEPOSIT_HOPPER;
      shouldEmptySack = true;
    } else {
      shouldEmptySack = false;
    }
  }

  protected String getTimeRunning() {
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

  private void updateSackValues() {
    curSackSize = client.getVarbitValue(Varbits.SACK_NUMBER);
  }

  protected void shutDown() {
    Microbot.pauseAllScripts = true;
    scriptStartTime = null;
    motherloadMineScript.shutdown();
    overlayManager.remove(motherloadMineOverlay);
    Rs2Antiban.resetAntibanSettings();
  }
}
