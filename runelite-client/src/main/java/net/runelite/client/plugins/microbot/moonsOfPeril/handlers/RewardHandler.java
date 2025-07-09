package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.Locations;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.State;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.Widgets;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilConfig;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;

import java.util.concurrent.atomic.AtomicInteger;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

public class RewardHandler implements BaseHandler {

    @Getter
    private static final AtomicInteger rewardChestCount = new AtomicInteger();
    private static final WorldPoint rewardChestLocation = Locations.REWARDS_CHEST_LOBBY.getWorldPoint();
    private static final int lunarChestGameObjectID = 51346;
    private static final int lunarChestBankAllWidgetID = 56885268;
    private boolean eclipseEnabled;
    private boolean blueEnabled;
    private boolean bloodEnabled;
    private final boolean debugLogging;
    private final BossHandler boss;

    @Inject
    public RewardHandler(moonsOfPerilConfig cfg) {
        this.eclipseEnabled = cfg.enableEclipse();
        this.blueEnabled = cfg.enableBlue();
        this.bloodEnabled = cfg.enableBlood();
        this.boss = new BossHandler(cfg);
        this.debugLogging = cfg.debugLogging();
    }

    @Override
    public boolean validate() {
        boolean eclipseDone = !eclipseEnabled || !boss.bossIsAlive("Eclipse Moon", Widgets.ECLIPSE_MOON_ID.getID());
        boolean blueDone = !blueEnabled || !boss.bossIsAlive("Blue Moon", Widgets.BLUE_MOON_ID.getID());
        boolean bloodDone = !bloodEnabled || !boss.bossIsAlive("Blood Moon", Widgets.BLOOD_MOON_ID.getID());
        return eclipseDone && blueDone && bloodDone;
    }

    @Override
    public State execute() {
        BreakHandlerScript.setLockState(true);
        boss.walkToBoss(null, "Rewards Chest", rewardChestLocation);
        if (Rs2GameObject.interact(lunarChestGameObjectID, "Claim")) {
            if (debugLogging) {Microbot.log("Successfully claimed rewards from Lunar Chest");}
            rewardChestCount.incrementAndGet();
            sleep(2_400);
        }
        if (Rs2Widget.clickWidget(lunarChestBankAllWidgetID)) {
            if (debugLogging) {Microbot.log("Successfully banked all rewards");}
            sleep(1_200);
        }
        BreakHandlerScript.setLockState(false);
        return State.IDLE;
    }
}
