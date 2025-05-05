package net.runelite.client.plugins.microbot.bankjs.BanksShopper;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import java.awt.*;
import java.util.stream.Collectors;


@PluginDescriptor(
        name = PluginDescriptor.Bank + "Bank's Shopper",
        description = "Bank's Auto Shopper",
        tags = {"shopper", "bank.js"},
        enabledByDefault = false
)
@Slf4j
public class BanksShopperPlugin extends Plugin {
    @Inject
    private BanksShopperConfig config;

    @Provides
    BanksShopperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BanksShopperConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BanksShopperOverlay banksShopperOverlay;

    private BanksShopperScript banksShopperScript;
    
    @Getter
    private String npcName;
    @Getter
    private List<String> itemNames;
    @Getter
    private int minStock;
    @Getter
    private Actions selectedAction;
    @Getter
    private Quantities selectedQuantity;
    @Getter
    private boolean useBank;
    @Getter
    private boolean useNextWorld;
    @Getter
    private boolean useLogout;
    @Getter
    private boolean useExactNaming;

    @Override
    protected void startUp() throws AWTException {
        npcName = config.npcName();
        minStock = config.minimumStock();
        selectedAction = config.action();
        selectedQuantity = config.quantity();
        useBank = config.useBank();
        useLogout = config.logout();
        useExactNaming = config.useExactNaming();
        useNextWorld = config.useNextWorld();
        updateItemList(config.itemNames());
        
        if (overlayManager != null) {
            overlayManager.add(banksShopperOverlay);
        }
        
        banksShopperScript = new BanksShopperScript(this);
        banksShopperScript.run(config);
    }

    protected void shutDown() {
        banksShopperScript.shutdown();
        overlayManager.remove(banksShopperOverlay);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.GAMEMESSAGE && !Microbot.pauseAllScripts) {
            if (event.getMessage().toLowerCase().contains("you don't have enough coins.")) {
                Microbot.status = "[Shutting down] - Reason: Not enough coins.";
                Microbot.showMessage(Microbot.status);
                banksShopperScript.shutdown();
            }
        }
    }
    
    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(BanksShopperConfig.configGroup)) return;
        
        if (event.getKey().equals(BanksShopperConfig.npcName)) {
            npcName = config.npcName();
        }
        
        if (event.getKey().equals(BanksShopperConfig.minStock)) {
            minStock = config.minimumStock();
        }

        if (event.getKey().equals(BanksShopperConfig.action)) {
            selectedAction = config.action();
        }

        if (event.getKey().equals(BanksShopperConfig.quantity)) {
            selectedQuantity = config.quantity();
        }

        if (event.getKey().equals(BanksShopperConfig.useBank)) {
            useBank = config.useBank();
        }

        if (event.getKey().equals(BanksShopperConfig.logout)) {
            useLogout = config.logout();
        }

        if (event.getKey().equals(BanksShopperConfig.useNextWorld)) {
            useNextWorld = config.useNextWorld();
        }
        
        if (event.getKey().equals(BanksShopperConfig.itemNames)) {
            updateItemList(config.itemNames());
        }
    }
    
    private void updateItemList(String items) {
        if (items.isBlank() || items.isEmpty()) return;
        
        if (items.contains(",") || items.contains(", ")) {
            itemNames = Arrays.stream(items.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        } else {
            itemNames = Collections.singletonList(items.trim().toLowerCase());
        }
    }
}
