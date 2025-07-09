package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.util.concurrent.TimeUnit;

public class ShortestPathScript extends Script {

    @Setter
    @Getter
    // used for calling the walker from a mainthread
    // running the walker on a seperate thread is a lot easier for debugging
    private WorldPoint triggerWalker;

    public boolean run(ShortestPathConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                if (getTriggerWalker() != null) {
                    WalkerState state  = WalkerState.UNREACHABLE;
                    if (config.walkWithBankedTransports()){
                        state= Rs2Walker.walkWithBankedTransportsAndState(getTriggerWalker(),10,false);
                    }else {
                        state= Rs2Walker.walkWithState(getTriggerWalker());
                    }
                    if (state == WalkerState.ARRIVED || state == WalkerState.UNREACHABLE) {
                        setTriggerWalker(null);
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                Microbot.log(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
