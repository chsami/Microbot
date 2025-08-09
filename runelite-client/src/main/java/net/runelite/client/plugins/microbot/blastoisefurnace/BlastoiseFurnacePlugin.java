package net.runelite.client.plugins.microbot.blastoisefurnace;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.*;
import net.runelite.api.gameval.*;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.client.plugins.microbot.blastoisefurnace.enums.State;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;

import static net.runelite.client.plugins.microbot.blastoisefurnace.BlastoiseFurnaceScript.*;


@PluginDescriptor(
        name = "<html>[<font color=#00ffff>§</font>] " + "BlastoiseFurnace",
        description = "Storm's test plugin",
        tags = {"testing", "microbot", "smithing", "bar", "ore", "blast", "furnace"},
        enabledByDefault = false
)
@Slf4j
public class BlastoiseFurnacePlugin extends Plugin {
    @Inject
    private BlastoiseFurnaceConfig config;
    @Provides
     BlastoiseFurnaceConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BlastoiseFurnaceConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private BlastoiseFurnaceOverlay blastoiseFurnaceOverlay;

    @Inject
    BlastoiseFurnaceScript blastoiseFurnaceScript;

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(blastoiseFurnaceOverlay);
        }
        blastoiseFurnaceScript.run();
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            if(chatMessage.getMessage().contains("The coal bag is now empty.")){
                if(!coalBagEmpty) coalBagEmpty=true;
            }

            if(chatMessage.getMessage().contains("The coal bag contains")){
                if(coalBagEmpty) coalBagEmpty=false;
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged inventory) {
        if (inventory.getItemContainer().getId() != 93) return;

        final ItemContainer inv = inventory.getItemContainer();
        final boolean hasCoal = inv.contains(ItemID.COAL);
        final boolean hasPrimary = inv.contains(config.getBars().getPrimaryOre());
        final boolean hasSecondary = inv.contains(config.getBars().getSecondaryOre());

        // Coal bag tracking
        if (state != State.BANKING && !hasCoal && !coalBagEmpty) coalBagEmpty = true;
        if (state != State.SMITHING && hasCoal && coalBagEmpty) coalBagEmpty = false;

        // Primary ore tracking
        if (state != State.SMITHING && hasPrimary && primaryOreEmpty) primaryOreEmpty = false;
        if (state != State.BANKING && !hasPrimary && !primaryOreEmpty) primaryOreEmpty = true;

        // Secondary ore tracking
        if (state != State.SMITHING && hasSecondary && secondaryOreEmpty) {
            secondaryOreEmpty = false;
            System.out.println("secondary set to not empty"); // Optional debug
        }
        if (state != State.BANKING && !hasSecondary && !secondaryOreEmpty) secondaryOreEmpty = true;
    }

    protected void shutDown() {
        blastoiseFurnaceScript.shutdown();
        overlayManager.remove(blastoiseFurnaceOverlay);
    }
}
