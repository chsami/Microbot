package net.runelite.client.plugins.microbot.api.boat;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.api.boat.models.Rs2BoatModel;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

@Slf4j
@Singleton
public final class Rs2BoatCache {

    private final Client client;
    private final ClientThread clientThread;

    private int lastCheckedOnBoat = 0;
    private Rs2BoatModel boat = null;

    @Inject
    public Rs2BoatCache(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    public Rs2BoatModel getLocalBoat() {
        if (lastCheckedOnBoat + 2 >= client.getTickCount()) {
            return boat;
        }

        WorldEntity worldEntity = clientThread.invoke(() -> {
            lastCheckedOnBoat = client.getTickCount();
            Player player = client.getLocalPlayer();

            if (player == null) {
                return null;
            }

            WorldView playerView = player.getWorldView();

            if (!playerView.isTopLevel()) {
                LocalPoint playerLocal = player.getLocalLocation();
                int worldViewId = playerLocal.getWorldView();

                return client.getTopLevelWorldView()
                        .worldEntities()
                        .byIndex(worldViewId);
            }

            return null;
        });

        boat = worldEntity != null ? new Rs2BoatModel(worldEntity) : null;
        return boat;
    }

    public Rs2BoatModel getBoat(Rs2PlayerModel player) {
        if (player == null) {
            return getLocalBoat();
        }

        if (lastCheckedOnBoat + 2 >= client.getTickCount()) {
            return boat;
        }

        WorldEntity worldEntity = clientThread.invoke(() -> {
            lastCheckedOnBoat = client.getTickCount();

            WorldView playerView = player.getWorldView();

            if (!playerView.isTopLevel()) {
                LocalPoint playerLocal = player.getLocalLocation();
                int worldViewId = playerLocal.getWorldView();

                return client.getTopLevelWorldView()
                        .worldEntities()
                        .byIndex(worldViewId);
            }

            return null;
        });

        boat = worldEntity != null ? new Rs2BoatModel(worldEntity) : null;
        return boat;
    }
}
