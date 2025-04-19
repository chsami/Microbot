package net.runelite.client.plugins.microbot.aiofighter.combat;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.item.Rs2ExplorersRing;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.settings.Rs2Settings;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class HighAlchScript extends Script {

    private int timeUntilCheckHighAlch = 60000;

    public boolean run(AIOFighterConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run() || !config.toggleHighAlchProfitable()) return;
                List<Rs2ItemModel> items = Rs2Inventory.getList(Rs2ItemModel::isHaProfitable);

                if (items.isEmpty()) return;

                if (Rs2ExplorersRing.hasRing() && Rs2ExplorersRing.hasCharges()) {
                    for (Rs2ItemModel item: items) {
                        Rs2ExplorersRing.highAlch(item);
                    }
                    Rs2ExplorersRing.closeInterface();
                } else  if (Rs2Magic.canCast(MagicAction.HIGH_LEVEL_ALCHEMY)) {
                    for (Rs2ItemModel item: items) {
                        Rs2Magic.alch(item);
                        if (item.getHaPrice() > Rs2Settings.getMinimumItemValueAlchemyWarning()) {
                            sleepUntil(() -> Rs2Widget.hasWidget("Proceed to cast High Alchemy on it"));
                            if (Rs2Widget.hasWidget("Proceed to cast High Alchemy on it")) {
                                Rs2Keyboard.keyPress('1');
                            }
                        }
                    }
                } else {
                    Rs2Tab.switchToInventoryTab();
                    sleep(timeUntilCheckHighAlch);
                    timeUntilCheckHighAlch = timeUntilCheckHighAlch * 5;
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }


    public void shutdown() {
        super.shutdown();
    }
}
