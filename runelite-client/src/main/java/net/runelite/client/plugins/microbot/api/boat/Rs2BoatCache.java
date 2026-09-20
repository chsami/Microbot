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

    @Inject
    public Rs2BoatCache(Client client, ClientThread clientThread) {
        this.client = client;
        this.clientThread = clientThread;
    }

    public Rs2BoatModel getLocalBoat() {
        return clientThread.invoke(() -> resolve(client.getLocalPlayer()));
    }

    public Rs2BoatModel getBoat(Rs2PlayerModel player) {
        return player == null ? getLocalBoat() : clientThread.invoke(() -> resolve(player.getPlayer()));
    }

    // Resolution is a single indexed lookup; do not share a result between players.
    private Rs2BoatModel resolve(Player player) {
        assert client.isClientThread() : "Client-thread-only helper";
        if (player == null) return null;
        WorldView view = player.getWorldView();
        WorldView top = client.getTopLevelWorldView();
        if (view == null || top == null || view.isTopLevel()) return null;
        LocalPoint local = player.getLocalLocation();
        if (local == null || client.getWorldView(view.getId()) != view) return null;
        WorldEntity entity = top.worldEntities().byIndex(local.getWorldView());
        return entity == null ? null : new Rs2BoatModel(entity);
    }
}
