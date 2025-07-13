package net.runelite.client.plugins.microbot.TaF.stonechests;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.TimeUtils;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;
import java.time.Instant;

@PluginDescriptor(
        name = PluginDescriptor.TaFCat + " Stone Chest Thiever",
        description = "A plugin to automate thieving from stone chests in the Lizardman Temple.",
        tags = {"TaF", "Thieving", "Stone chests"},
        enabledByDefault = false
)
@Slf4j
public class StoneChestThieverPlugin extends Plugin {
    public Instant scriptStartTime;
    public int chestOpenend = 0;
    @Inject
    StoneChestThieverScript stoneChestThieverScript;
    @Inject
    private StoneChestThieverConfig config;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private StoneChestThieverOverlay stoneChestThieverOverlay;

    @Provides
    StoneChestThieverConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(StoneChestThieverConfig.class);
    }

    protected String getTimeRunning() {
        return scriptStartTime != null ? TimeUtils.getFormattedDurationBetween(scriptStartTime, Instant.now()) : "";
    }

    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(stoneChestThieverOverlay);
        }
        stoneChestThieverScript.run(config);
        scriptStartTime = Instant.now();
    }

    protected void shutDown() {
        stoneChestThieverScript.shutdown();
        overlayManager.remove(stoneChestThieverOverlay);
        chestOpenend = 0;
        scriptStartTime = Instant.now();
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        if (event.getVarpId() == VarPlayerID.POISON) {
            final int poison = Microbot.getClient().getVarpValue(VarPlayerID.POISON);
            if (poison > 0) {
                Rs2Player.drinkAntiPoisonPotion();
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged statChanged) {
        final Skill skill = statChanged.getSkill();
        if (skill == Skill.THIEVING) {
            chestOpenend++;
        }
    }
}
