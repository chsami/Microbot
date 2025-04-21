package net.runelite.client.plugins.microbot.tempoross;

import com.google.inject.Inject;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NpcID;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.tempoross.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginDescriptor.See1Duck + "Tempoross",
        description = "Tempoross Plugin",
        tags = {"Tempoross", "minigame", "s1d","see1duck","microbot", "fishing","skilling"},
        enabledByDefault = false
)
@Slf4j
public class TemporossPlugin extends Plugin {
    @Inject
    private TemporossConfig config;

    @Inject
    private TemporossOverlay temporossOverlay;

    @Inject
    private TemporossProgressionOverlay temporossProgressionOverlay;

    @Inject
    private TemporossScript temporossScript;

    @Inject
    private static ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private Client client;


    public static int waves = 0;
    public static int fireClouds = 0;
    public static boolean incomingWave = false;
    public static boolean isTethered = false;

    private static final int VARB_IS_TETHERED = 11895;

    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");


    @Provides
    TemporossConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(TemporossConfig.class);
    }


    protected void startUp() throws Exception {
        if (overlayManager != null) {
            overlayManager.add(temporossOverlay);
            overlayManager.add(temporossProgressionOverlay);
        }
        temporossScript.run(config);
    }

    @Override
    protected void shutDown() throws Exception {
        super.shutDown();
        temporossScript.shutdown();
        overlayManager.remove(temporossOverlay);
        overlayManager.remove(temporossProgressionOverlay);
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event) {

        if(!TemporossScript.isInMinigame())
            return;
        if(TemporossScript.workArea == null)
            return;
        TemporossScript.handleWidgetInfo();
        TemporossScript.updateFireData();
        TemporossScript.updateFishSpotData();
        TemporossScript.updateCloudData();
        TemporossScript.updateAmmoCrateData();
    }

    @Subscribe
    public void onGameTick(GameTick e)
    {
        if(!TemporossScript.isInMinigame())
            return;
        if(TemporossScript.workArea == null)
            return;
        TemporossScript.handleWidgetInfo();
        TemporossScript.updateFireData();
        TemporossScript.updateFishSpotData();
        TemporossScript.updateCloudData();
        TemporossScript.updateAmmoCrateData();
        TemporossScript.updateLastWalkPath();

        Rs2NpcModel doubleFishingSpot = Rs2Npc.getNpc(NpcID.FISHING_SPOT_10569);

        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot != null) {
            TemporossScript.state = TemporossScript.state.next;
        }

        if (TemporossScript.INTENSITY >= 94 && TemporossScript.state == State.THIRD_COOK)
        {
            return;
        }

        if (TemporossScript.state == null)
        {
            TemporossScript.state = State.THIRD_CATCH;
        }
    }

    @Subscribe
    public void onVarbitChanged(VarbitChanged event)
    {
        if (event.getVarbitId() == VARB_IS_TETHERED)
        {
            log.info("Tethered: {}", event.getValue());
            isTethered = event.getValue() > 0;
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        ChatMessageType type = event.getType();
        String message = event.getMessage();

        if (type == ChatMessageType.GAMEMESSAGE)
        {
            if (message.contains("A colossal wave closes in"))
            {
                waves++;
                incomingWave = true;
                log.info("Wave {}", waves);
            }

            if (message.contains("the rope keeps you securely") || message.contains("the wave slams into you"))
            {
                incomingWave = false;
                log.info("Wave passed");
            }
            if (message.contains("A strong wind blows as clouds roll in"))
            {
                fireClouds++;
                log.info("Clouds {}", fireClouds);
            }
            {

            }
        }
    }

    // Set harpoon type config
    public static void setHarpoonType(HarpoonType harpoonType) {
        Microbot.getConfigManager().setConfiguration("microbot-tempoross", "harpoonType", harpoonType);
    }

    // Set rope config
    public static void setRope(boolean rope) {
        Microbot.getConfigManager().setConfiguration("microbot-tempoross", "rope", rope);
    }
}
