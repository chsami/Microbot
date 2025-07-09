package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import net.runelite.client.plugins.microbot.moonsOfPeril.enums.State;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilConfig;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;

public class IdleHandler implements BaseHandler {

    private final BossHandler boss;

    public IdleHandler(moonsOfPerilConfig cfg) {
        this.boss = new BossHandler(cfg);
    }

    @Override
    public boolean validate() {
        Rs2Prayer.disableAllPrayers();
        boss.eatIfNeeded();
        boss.drinkIfNeeded();
        return false;
    }

    @Override
    public State execute() {
        return null;
    }
}
