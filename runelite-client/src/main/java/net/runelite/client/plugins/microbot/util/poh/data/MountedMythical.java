package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.DecorativeObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.worldmap.TeleportLocationData;


@Getter
@RequiredArgsConstructor
public enum MountedMythical implements PohTeleport {
    MYTHS_GUILD(ObjectID.POH_TROPHY_MYTHICAL_CAPE, "Myths guild", TeleportLocationData.MYTHS_GUILD.getLocation()),
    ;

    private final int objectId;
    private final String destinationName;
    private final WorldPoint destination;

    private final int duration = 4;

    @Override
    public boolean execute() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(MYTHS_GUILD.getObjectId())
                .interact("Teleport");
    }

    public static Rs2TileObjectModel getObject() {
        return Microbot.getRs2TileObjectCache().query()
                .withId(MYTHS_GUILD.getObjectId())
                .first();
    }

    public static boolean isMountedMythsCape(DecorativeObject go) {
        if (go == null) return false;
        return go.getId() == MYTHS_GUILD.getObjectId();
    }

    @Override
    public String displayInfo() {
        return getClass().getSimpleName() + " -> " + destinationName;
    }

}
