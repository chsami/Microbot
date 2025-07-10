package net.runelite.client.plugins.microbot.piebaker;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@PluginDescriptor(
        name = PluginDescriptor.Bttqjs + "Pie Baker",
        description = "Automatically bakes pies using the Lunar spellbook",
        tags = {"pie", "baking", "cooking", "magic"},
        enabledByDefault = false
)
@Slf4j
public class PieBakerPlugin extends Plugin {
    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PieBakerOverlay pieBakerOverlay;

    @Inject
    private PieBakerConfig config;

    private static final Map<String, Integer> RAW_PIES = new HashMap<>();

    static {
        RAW_PIES.put("uncooked berry pie", ItemID.UNCOOKED_REDBERRY_PIE);
        RAW_PIES.put("uncooked meat pie", ItemID.UNCOOKED_MEAT_PIE);
        RAW_PIES.put("raw mud pie", ItemID.UNCOOKED_MUD_PIE);
        RAW_PIES.put("uncooked apple pie", ItemID.UNCOOKED_APPLE_PIE);
        RAW_PIES.put("raw garden pie", ItemID.UNCOOKED_GARDEN_PIE);
        RAW_PIES.put("raw fish pie", ItemID.UNCOOKED_FISH_PIE);
        RAW_PIES.put("uncooked botanical pie", ItemID.UNCOOKED_BOTANICAL_PIE);
        RAW_PIES.put("uncooked mushroom pie", ItemID.UNCOOKED_MUSHROOM_PIE);
        RAW_PIES.put("raw admiral pie", ItemID.UNCOOKED_ADMIRAL_PIE);
        RAW_PIES.put("uncooked dragonfruit pie", ItemID.UNCOOKED_DRAGONFRUIT_PIE);
        RAW_PIES.put("raw wild pie", ItemID.UNCOOKED_WILD_PIE);
        RAW_PIES.put("raw summer pie", ItemID.UNCOOKED_SUMMER_PIE);
    }

    private Instant startTime;
    private int startMagicXP;
    private int startCookingXP;

    @Provides
    PieBakerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(PieBakerConfig.class);
    }

    @Override
    protected void startUp() {
        overlayManager.add(pieBakerOverlay);
        startTime = Instant.now();
        startMagicXP = Microbot.getClient().getSkillExperience(Skill.MAGIC);
        startCookingXP = Microbot.getClient().getSkillExperience(Skill.COOKING);
    }

    @Override
    protected void shutDown() {
        overlayManager.remove(pieBakerOverlay);
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() == InventoryID.BANK.getId()) {
            if (Rs2Inventory.isEmpty() && shouldWithdrawPies()) {
                withdrawPies();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        String[] rawPiesArray = RAW_PIES.keySet().toArray(new String[0]);
        if (Rs2Inventory.hasItem(rawPiesArray)) {
            castBakePieSpell();
        } else if (Rs2Inventory.isEmpty() && shouldWithdrawPies()) {
            withdrawPies();
        } else {
            Rs2Bank.depositAll();
        }
    }

    private boolean shouldWithdrawPies() {
        return Rs2Bank.hasItem(getSelectedRawPieId());
    }

    private void withdrawPies() {
        Rs2Bank.withdrawAll(getSelectedRawPieId());
    }

    private void castBakePieSpell() {
        if (config.tickPerfect()) {
            String[] rawPiesArray = RAW_PIES.keySet().toArray(new String[0]);
            while (Rs2Inventory.hasItem(rawPiesArray)) {
                Rs2Magic.cast(MagicAction.BAKE_PIE);
            }
        } else {
            Rs2Magic.cast(MagicAction.BAKE_PIE);
        }
    }

    private int getSelectedRawPieId() {
        return RAW_PIES.getOrDefault(config.selectedPie().toLowerCase(), -1);
    }

    public String getSelectedPie() {
        return config.selectedPie();
    }

    public int getMagicXPPerHour() {
        int currentXP = Microbot.getClient().getSkillExperience(Skill.MAGIC);
        return calculateXPPerHour(startMagicXP, currentXP);
    }

    public int getCookingXPPerHour() {
        int currentXP = Microbot.getClient().getSkillExperience(Skill.COOKING);
        return calculateXPPerHour(startCookingXP, currentXP);
    }

    private int calculateXPPerHour(int startXP, int currentXP) {
        long elapsedTime = Instant.now().getEpochSecond() - startTime.getEpochSecond();
        int xpGained = currentXP - startXP;
        return (int) (xpGained * 3600 / elapsedTime);
    }
}
