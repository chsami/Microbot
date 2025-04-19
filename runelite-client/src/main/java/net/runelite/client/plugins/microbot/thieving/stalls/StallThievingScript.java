package net.runelite.client.plugins.microbot.thieving.stalls;

import lombok.AllArgsConstructor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.thieving.stalls.constants.InventoryStrategyFetcher;
import net.runelite.client.plugins.microbot.thieving.stalls.constants.ThievingSpotMapper;
import net.runelite.client.plugins.microbot.thieving.stalls.model.BotApi;
import net.runelite.client.plugins.microbot.thieving.stalls.model.IStallThievingSpot;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;


@AllArgsConstructor(onConstructor_ = @Inject)
public class StallThievingScript extends Script {
    public static double version = 1.0;

    private BotApi botApi;
    private ThievingSpotMapper thievingSpotMapper;
    private InventoryStrategyFetcher inventoryStrategyMapper;

    public boolean run(StallThievingConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                execute(config);

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void execute(final StallThievingConfig config)
    {
        final IStallThievingSpot thievingSpot = thievingSpotMapper.getThievingSpot(config.THIEVING_SPOT());
        if (botApi.isInventoryFull()) {
            inventoryStrategyMapper.getInventoryStrategy(config).execute(thievingSpot);
            return;
        }

        thievingSpot.thieve();
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
