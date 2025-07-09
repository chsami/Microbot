package net.runelite.client.plugins.microbot.thieving.stalls.model;

import lombok.AllArgsConstructor;
import net.runelite.client.plugins.microbot.thieving.stalls.StallThievingConfig;

import javax.inject.Inject;
import java.util.Arrays;

@AllArgsConstructor(onConstructor_ = @Inject)
public class BankInventoryStrategy implements IInventoryStrategy {
    private BotApi botApi;
    private DropInventoryStrategy dropInventoryStrategy;
    private StallThievingConfig config;

    @Override
    public void execute(IStallThievingSpot stallThievingSpot) {
        if (!botApi.isInventoryFull())
        {
            return;
        }

        botApi.dropAll(getItemIdsToAlwaysDrop());

        if (!botApi.isInventoryFull())
        {
            return;
        }

        try
        {
            stallThievingSpot.bank();
            return;
        }
        catch (final UnsupportedOperationException ex)
        {
            System.out.println("Banking not supported for this thieving spot. Dropping items");
        }

        dropInventoryStrategy.execute(stallThievingSpot);
    }

    private int[] getItemIdsToAlwaysDrop()
    {
        if (config.alwaysDrop().isEmpty())
        {
            return new int[0];
        }
        return Arrays.stream(config.alwaysDrop().split("\\s*,\\s*")).mapToInt(Integer::parseInt).toArray();
    }
}
