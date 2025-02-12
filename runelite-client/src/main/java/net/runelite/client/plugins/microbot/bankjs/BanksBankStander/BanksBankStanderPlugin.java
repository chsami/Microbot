package net.runelite.client.plugins.microbot.bankjs.BanksBankStander;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Arrays;

@PluginDescriptor(
        name = PluginDescriptor.Bank + "Bank's BankStander",
        description = "For Skilling at the Bank",
        tags = {"bankstander", "bank.js", "bank", "eXioStorm", "storm"},
        enabledByDefault = false
)
@Slf4j
public class BanksBankStanderPlugin extends Plugin {
    @Inject
    private BanksBankStanderConfig config;

    @Provides
    BanksBankStanderConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BanksBankStanderConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BanksBankStanderOverlay banksBankStanderOverlay;

    @Inject
    BanksBankStanderScript banksBankStanderScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(banksBankStanderOverlay);
        }
        banksBankStanderScript.run(config);
    }
    ///* Added by Storm
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged inventory){
        if(inventory.getContainerId()==93){
            if (!Rs2Bank.isOpen()) {
                BanksBankStanderScript.itemsProcessed++;
            }
            if (BanksBankStanderScript.secondItemId != null) { // Use secondItemId if it's available
                if (Arrays.stream(inventory.getItemContainer().getItems())
                        .anyMatch(x -> x.getId() == BanksBankStanderScript.secondItemId)) {
                    // average is 1800, max is 2400~
                    BanksBankStanderScript.previousItemChange = System.currentTimeMillis();
                    //System.out.println("still processing items");
                } else {
                    BanksBankStanderScript.previousItemChange = (System.currentTimeMillis() - 2500);
                }
            } else { // Use secondItemIdentifier if secondItemId is null
                Rs2ItemModel item = Rs2Inventory.get(config.secondItemIdentifier());
                if (item != null) {
                    // average is 1800, max is 2400~
                    BanksBankStanderScript.previousItemChange = System.currentTimeMillis();
                    //System.out.println("still processing items");
                } else {
                    BanksBankStanderScript.previousItemChange = (System.currentTimeMillis() - 2500);
                }
            }
        }
    }
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widget){
        if (widget.getGroupId()==270) {
            if(BanksBankStanderScript.isWaitingForPrompt) {
                BanksBankStanderScript.isWaitingForPrompt = false;
            }
        }
    }
    //*/ Added by Storm
    protected void shutDown() {
        banksBankStanderScript.shutdown();
        overlayManager.remove(banksBankStanderOverlay);
    }
}
