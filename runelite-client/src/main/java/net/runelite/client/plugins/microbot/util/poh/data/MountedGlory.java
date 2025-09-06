package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.GameObject;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.worldmap.TeleportLocationData;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum MountedGlory implements PohTeleport {
    EDGEVILLE("Edgeville", TeleportLocationData.EDGEVILLE.getLocation()),
    KARAMJA("Karamja", TeleportLocationData.KARAMJA.getLocation()),
    DRAYNOR_VILLAGE("Draynor Village", TeleportLocationData.DRAYNOR_VILLAGE.getLocation()),
    AL_KHARID("Al Kharid", TeleportLocationData.AL_KHARID.getLocation()),
    ;

    private final String destinationName;
    private final WorldPoint destination;

    private final int duration = 4;


    @Override
    public boolean execute() {
        return Rs2GameObject.interact(getObject(), destinationName);
    }

    public static TileObject getObject() {
        return Rs2GameObject.getTileObject(ObjectID.POH_TROPHY_AMULETOFGLORY_4);
    }

    public static List<PohTeleport> getTransports() {
        return Arrays.stream(values()).collect(Collectors.toList());
    }

    public static boolean isMountedGlory(GameObject go) {
        if (go == null) return false;
        return ObjectID.POH_TROPHY_AMULETOFGLORY_4 == go.getId();
    }

    @Override
    public String displayInfo() {
        return "MountedGlory -> " + destinationName;
    }
}
