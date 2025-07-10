package net.runelite.client.plugins.microbot.smelting;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Vince + "Auto Smelting",
        description = "Smelt ores/coal into bars",
        tags = {"smithing", "smelting", "microbot", "skilling"},
        enabledByDefault = false
)
@Slf4j
public class AutoSmeltingPlugin extends Plugin {
    @Inject
    private AutoSmeltingConfig config;
    @Provides
    AutoSmeltingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoSmeltingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private AutoSmeltingOverlay autoSmeltingOverlay;

    @Inject
	AutoSmeltingScript autoSmeltingScript;

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() == ChatMessageType.GAMEMESSAGE) {
            if(chatMessage.getMessage().contains("The coal bag is now empty.")){
                if(!AutoSmeltingScript.coalBagEmpty) AutoSmeltingScript.coalBagEmpty=true;
            }

            if(chatMessage.getMessage().contains("The coal bag contains")){
                if(AutoSmeltingScript.coalBagEmpty) AutoSmeltingScript.coalBagEmpty=false;
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged inventory){
        if(inventory.getItemContainer().getId()==93) {
            if (!inventory.getItemContainer().contains(ItemID.COAL)) {
                if (!AutoSmeltingScript.coalBagEmpty) AutoSmeltingScript.coalBagEmpty = true;//TODO this sets the bag to empty when we're smithing and coal is added to our inventory.
            }
            if (inventory.getItemContainer().contains(ItemID.COAL)) {
                if (AutoSmeltingScript.coalBagEmpty) AutoSmeltingScript.coalBagEmpty = false;
            }
        }
    }

    @Override
    protected void startUp() throws AWTException {
        autoSmeltingScript.hasBeenFilled = false;
        if (overlayManager != null) {
            overlayManager.add(autoSmeltingOverlay);
        }
        autoSmeltingScript.run(config);
    }

    protected void shutDown() {
        autoSmeltingScript.hasBeenFilled = false;
        autoSmeltingScript.shutdown();
        overlayManager.remove(autoSmeltingOverlay);
    }
}
