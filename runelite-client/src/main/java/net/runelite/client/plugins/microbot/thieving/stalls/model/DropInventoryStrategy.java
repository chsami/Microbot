package net.runelite.client.plugins.microbot.thieving.stalls.model;

import lombok.AllArgsConstructor;

import javax.inject.Inject;
import java.util.Arrays;

@AllArgsConstructor(onConstructor_ = @Inject)
public class DropInventoryStrategy implements IInventoryStrategy {
    private BotApi botApi;

    @Override
    public void execute(final IStallThievingSpot stallThievingSpot) {
        if (!botApi.isInventoryFull())
        {
            return;
        }

        botApi.dropAll(Arrays.stream(stallThievingSpot.getItemIdsToDrop()).mapToInt(i -> i).toArray());
    }
}
