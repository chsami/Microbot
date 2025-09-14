package net.runelite.client.plugins.microbot.util.poh;

import lombok.Getter;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.poh.data.HouseStyle;
import net.runelite.client.plugins.microbot.util.poh.data.PohTeleport;

/**
 * Represents a transport mechanism using the Player-Owned House (POH) teleportation system.
 * This class extends the base Transport class and provides specific implementations
 * for POH teleportation.
 */
public class PohTransport extends Transport {

    @Getter
    private final PohTeleport teleport;

    public PohTransport(WorldPoint exitPortalPoint, PohTeleport teleport) {
        super(
                java.util.Objects.requireNonNull(exitPortalPoint, "exitPortalPoint is null"),
                java.util.Objects.requireNonNull(teleport, "teleport is null").getDestination(),
                teleport.displayInfo(), TransportType.POH, true, teleport.getDuration()
        );
        this.teleport = teleport;
    }

    /**
     * Executes the Transport's PoH teleportation action.
     *
     * @return true on successful teleportation
     */
    public boolean execute() {
        return teleport.execute();
    }

}
