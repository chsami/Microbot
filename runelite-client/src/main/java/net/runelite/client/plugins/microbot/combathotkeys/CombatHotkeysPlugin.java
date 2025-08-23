package net.runelite.client.plugins.microbot.combathotkeys;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.event.KeyEvent;

@PluginDescriptor(
        name = PluginDescriptor.Cicire + "Combat hotkeys",
        description = "A plugin to bind hotkeys to combat stuff",
        tags = {"combat", "hotkeys", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class CombatHotkeysPlugin extends Plugin implements KeyListener {
    @Inject
    private CombatHotkeysConfig config;

    @Provides
    CombatHotkeysConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CombatHotkeysConfig.class);
    }

    @Inject
    private KeyManager keyManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private CombatHotkeysOverlay overlay;

    @Inject
    private CombatHotkeysScript script;


    @Override
    protected void startUp() throws AWTException {
        keyManager.registerKeyListener(this);

        if (overlayManager != null) {
            overlayManager.add(overlay);
        }
        script.run(config);
    }

    protected void shutDown() {
        script.shutdown();
        keyManager.unregisterKeyListener(this);
        overlayManager.remove(overlay);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (!Microbot.isLoggedIn()){
            return;
        }

        if(config.dance().matches(e)){
            e.consume();
            script.dance = !script.dance;
        }

        if (config.protectFromMagic().matches(e)) {
            e.consume();
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC);
        }

        if (config.protectFromMissles().matches(e)) {
            e.consume();
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_RANGE);
        }

        if (config.protectFromMelee().matches(e)) {
            e.consume();
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MELEE);
        }

        if (config.gear1().matches(e)) {
            e.consume();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                equipGear(config.gearList1());
                return null;
            });
        }

        if (config.gear2().matches(e)) {
            e.consume();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                equipGear(config.gearList2());
                return null;
            });
        }

        if (config.gear3().matches(e)) {
            e.consume();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                equipGear(config.gearList3());
                return null;
            });
        }

        if (config.gear4().matches(e)) {
            e.consume();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                equipGear(config.gearList4());
                return null;
            });
        }

        if (config.gear5().matches(e)) {
            e.consume();
            Microbot.getClientThread().runOnSeperateThread(() -> {
                equipGear(config.gearList5());
                return null;
            });
        }
    }


    private static void equipGear(String gearListConfig) {
        String[] itemIDs = gearListConfig.split(",");

        for (String value : itemIDs) {
            int itemId = Integer.parseInt(value);
            Rs2Inventory.equip(itemId);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event)
    {
        if (event.getOption().equals("Walk here") && config.yesDance())
        {
            Microbot.getClient().getMenu().createMenuEntry(-1)
                    .setOption("Dancing -> mark tile 2")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        final var target = Microbot.getClient().getTopLevelWorldView().getSelectedSceneTile();
                        if (target != null)
                        {
                            final var location = target.getWorldLocation();
                            Microbot.getConfigManager().setConfiguration(
                                    "combathotkeys",
                                    "tile2",
                                    location
                            );
                        }
                    });

            Microbot.getClient().getMenu().createMenuEntry(-1)
                    .setOption("Dancing -> mark tile 1")
                    .setTarget(event.getTarget())
                    .setType(MenuAction.RUNELITE)
                    .onClick(e -> {
                        final var target = Microbot.getClient().getTopLevelWorldView().getSelectedSceneTile();
                        if (target != null)
                        {
                            final var location = target.getWorldLocation();
                            Microbot.getConfigManager().setConfiguration(
                                    "combathotkeys",
                                    "tile1",
                                    location
                            );
                        }
                    });
        }
    }
}
