package net.runelite.client.plugins.microbot.prayer.burybones;

import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.prayer.burybones.enums.Activity;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Ashes;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Bones;
import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Rollogi + " Bury Bones",
        description = "Bury bones plugin",
        tags = {"prayer", "microbot", "skilling", "training"},
        enabledByDefault = false
)
public class BuryBonesPlugin extends Plugin {
    @Inject
    private BuryBonesConfig config;

    @Inject
    private BuryBonesScript buryBonesScript;

    @Provides
    BuryBonesConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(BuryBonesConfig.class);
    }

    @Getter
    private Activity activity;
    @Getter
    private Rs2Bones bones;
    @Getter
    private Rs2Ashes ashes;
    @Getter
    private boolean afk;
    @Getter
    private int afkMin;
    @Getter
    private int afkMax;

    @Override
    protected void startUp() throws AWTException {
        activity = config.activity();
        bones = config.bones();
        ashes = config.ashes();
        afk = config.Afk();
        afkMin = config.AfkMin();
        afkMax = config.AfkMax();

        buryBonesScript.run();
    }

    @Override
    protected void shutDown() {
        buryBonesScript.shutdown();
    }

    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(BuryBonesConfig.configGroup)) {
            return;
        }

        if (event.getKey().equals(BuryBonesConfig.activityKeyName)) {
            activity = config.activity();
        }

        if (event.getKey().equals(BuryBonesConfig.bonesKeyName)) {
            bones = config.bones();
        }

        if (event.getKey().equals(BuryBonesConfig.ashesKeyName)) {
            ashes = config.ashes();
        }

        if (event.getKey().equals(BuryBonesConfig.afkKeyName)) {
            afk = config.Afk();
        }

        if (event.getKey().equals(BuryBonesConfig.afkMinKeyName)) {
            afkMin = config.AfkMin();
        }

        if (event.getKey().equals(BuryBonesConfig.afkMaxKeyName)) {
            afkMax = config.AfkMax();
        }
    }
}
