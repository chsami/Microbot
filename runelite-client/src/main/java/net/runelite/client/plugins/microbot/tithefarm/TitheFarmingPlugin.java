package net.runelite.client.plugins.microbot.tithefarm;


import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.tithefarm.enums.TitheFarmMaterial;
import net.runelite.client.plugins.microbot.tithefarm.enums.TitheFarmState;
import net.runelite.client.plugins.microbot.tithefarm.models.TitheFarmPlant;
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginDescriptor.Mocrosoft + "Tithe Farm",
        description = "Plays the Tithe farm minigame for you!",
        tags = {"tithe farming", "microbot", "skills", "minigame"},
        enabledByDefault = false
)
@Slf4j
public class TitheFarmingPlugin extends Plugin {

    @Inject
    public TitheFarmingConfig config;

    @Provides
    TitheFarmingConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TitheFarmingConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private TitheFarmingOverlay titheFarmOverlay;

    private final TitheFarmingScript titheFarmScript = new TitheFarmingScript();

    @Override
    protected void startUp() throws AWTException {
		Microbot.pauseAllScripts.compareAndSet(true, false);

        if (overlayManager != null) {
            overlayManager.add(titheFarmOverlay);
        }
        titheFarmScript.run(config);
    }

    protected void shutDown() {
        titheFarmScript.shutdown();
        overlayManager.remove(titheFarmOverlay);
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        for (TitheFarmPlant plant : TitheFarmingScript.plants) {
            if (event.getGameObject().getWorldLocation().equals(plant.getGameObject().getWorldLocation())) {
                plant.setGameObject(event.getGameObject());
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (TitheFarmMaterial.getSeedForLevel() != null) {
            Item fruit = Arrays.stream(event.getItemContainer().getItems()).filter(x -> x.getId() == TitheFarmMaterial.getSeedForLevel().getFruitId()).findFirst().orElse(null);
            if (fruit != null) {
                TitheFarmingScript.fruits = fruit.getQuantity() - TitheFarmingScript.initialFruit;
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage)
    {
        String message = chatMessage.getMessage();
        if (message.contains("%")) {
            Pattern pattern = Pattern.compile("(\\d+(\\.\\d+)?)%");
            Matcher matcher = pattern.matcher(message);

            if (matcher.find()) {
                String percentage = matcher.group(1);
                TitheFarmingScript.gricollerCanCharges = (int) (Float.parseFloat(percentage));
            }
        } else if (message.equalsIgnoreCase("Gricoller's can is already full.")) {
            TitheFarmingScript.gricollerCanCharges = 100;
        } else if (message.equalsIgnoreCase("You don't have a suitable vessel of water for watering the plant.")) {
            TitheFarmingScript.state = TitheFarmState.REFILL_WATERCANS;
        }
    }
}
