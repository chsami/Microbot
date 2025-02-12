package net.runelite.client.plugins.microbot.virewatch;

import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@PluginDescriptor(
        name = PluginDescriptor.Pumster + "Vyrewatch Killer",
        description = "Pumsters vyrewatch killer",
        tags = {"pumster"},
        enabledByDefault = false
)
public class PVirewatchKillerPlugin extends Plugin {

    public static String version = "1.0";

    private PLooter looterScript = new PLooter();
    private PAlcher alchScript = new PAlcher();
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private ClientThread clientThread;
    @Inject
    private Client client;
    @Inject
    private PVirewatchKillerPlugin plugin;
    @Inject
    private PVirewatchKillerOverlayPanel infoOverlay;
    @Inject
    private PVirewatchKillerConfig config;
    @Inject
    private PVirewatchKillerOverlay overlay;
    @Inject
    private  PVirewatchScript script;


    public boolean alchingDrop = false;

    public int alchedItems = 0;

    public boolean rechargingPrayer = false;
    public WorldArea fightArea;

    public PVirewatchKillerPlugin() {
    }

    @Provides
    PVirewatchKillerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PVirewatchKillerConfig.class);
    }

    public WorldPoint startingLocation = null;
    public int countedTicks = 0;

    public int ticksOutOfArea = 0;

    @Inject
    private ItemManager itemManager;

    // Total value of picked up items
    private int totalItemValue = 0;

    // Previous inventory state
    private Map<Integer, Integer> previousInventory = new HashMap<>();


    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if(startingLocation != null) {
            int radius = config.radius() / 2;
            int x1 = (startingLocation.getX() - radius);
            int y1 = (startingLocation.getY() - radius);
            int x2 = (startingLocation.getX() + radius + 1);
            int y2 = (startingLocation.getY() + radius + 1) ;

            // Create a WorldArea from these coordinates
            fightArea = new WorldArea(x1, y1, x2 - x1, y2 - y1, startingLocation.getPlane());
        }
    }

    @Subscribe
    private void onGameTick(GameTick event) {
        if (Rs2Combat.inCombat()) {
            countedTicks = 0;
        } else if (!plugin.rechargingPrayer) {
            countedTicks++;
        }

        if(fightArea != null) {
            if(fightArea.contains(Microbot.getClient().getLocalPlayer().getWorldLocation())) {
                ticksOutOfArea = 0;
            } else if (!plugin.rechargingPrayer) {
                ticksOutOfArea++;
            }
}

        if(startingLocation == null) {
            startingLocation = client.getLocalPlayer().getWorldLocation();

            int radius = config.radius() / 2;
            int x1 = (startingLocation.getX() - radius);
            int y1 = (startingLocation.getY() - radius);
            int x2 = (startingLocation.getX() + radius + 1);
            int y2 = (startingLocation.getY() + radius + 1) ;

            // Create a WorldArea from these coordinates
            fightArea = new WorldArea(x1, y1, x2 - x1, y2 - y1, startingLocation.getPlane());
        }



        if(Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER) > 0) {
            if(Rs2Player.hasPrayerPoints()) {
                if(!Rs2Prayer.isQuickPrayerEnabled()) {
                    Rs2Prayer.toggleQuickPrayer(true);
                }
            } else {
                Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE, true);
                if(config.piety()) {
                    Rs2Prayer.toggle(Rs2PrayerEnum.PIETY, true);
                }
            }

        }

    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event.getContainerId() == Microbot.getClient().getItemContainer(InventoryID.INVENTORY).getId())
        {
            if(alchingDrop) return;

            Map<Integer, Integer> currentInventory = new HashMap<>();
            for (Item item : event.getItemContainer().getItems())
            {
                if (item != null)
                {
                    currentInventory.put(item.getId(), currentInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
                }
            }

            // Calculate newly picked up items
            for (Map.Entry<Integer, Integer> entry : currentInventory.entrySet())
            {
                int itemId = entry.getKey();
                int currentQuantity = entry.getValue();
                int previousQuantity = previousInventory.getOrDefault(itemId, 0);

                if (currentQuantity > previousQuantity)
                {
                    int newQuantity = currentQuantity - previousQuantity;
                    int itemValue = itemManager.getItemPrice(itemId);
                    totalItemValue += itemValue * newQuantity;
                }
            }

            // Update the previous inventory state
            previousInventory = currentInventory;
        }
    }

    private void updateInventoryState()
    {
        List<Rs2ItemModel> items = Rs2Inventory.items();
        if (items != null)
        {
            for (Rs2ItemModel item : items)
            {
                if (item != null)
                {
                    previousInventory.put(item.getId(), item.quantity);
                }
            }
        }
    }

    public String getTotalItemValue()
    {
        return formatNumber(totalItemValue);
    }

    public static String formatNumber(int value)
    {
        if (value >= 1_000_000)
        {
            return (value / 1_000_000) + "m";
        }
        else if (value >= 1_000)
        {
            return (value / 1_000) + "k";
        }
        else
        {
            return String.valueOf(value);
        }
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(infoOverlay);
            overlayManager.add(overlay);

        }
        script.run(config, plugin);
        alchScript.run(config, plugin);
        looterScript.run(config);

        previousInventory.clear();
        updateInventoryState();
        totalItemValue = 0;


    }

    protected void shutDown() {
        script.shutdown();
        looterScript.shutdown();
        alchScript.shutdown();
        overlayManager.remove(overlay);
        overlayManager.remove(infoOverlay);
        startingLocation = null;
        fightArea = null;
        ticksOutOfArea = 0;
        countedTicks = 0;
        alchedItems = 0;
    }
}
