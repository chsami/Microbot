package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.DecorativeObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.worldmap.TeleportLocationData;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;


@Getter
@RequiredArgsConstructor
public enum MountedXerics implements PohTeleport {
    LOOKOUT(ObjectID.POH_AMULET_XERIC_LOOKOUT, "Xeric's Lookout", TeleportLocationData.XERICS_LOOKOUT.getLocation()),
    GLADE(ObjectID.POH_AMULET_XERIC_GLADE, "Xeric's Glade", TeleportLocationData.XERICS_GLADE.getLocation()),
    INFERNO(ObjectID.POH_AMULET_XERIC_INFERNO, "Xeric's Inferno", TeleportLocationData.XERICS_INFERNO.getLocation()),
    HEART(ObjectID.POH_AMULET_XERIC_HEART, "Xeric's Heart", TeleportLocationData.XERICS_HEART.getLocation()),
//    HONOUR(ObjectID.POH_AMULET_XERIC_GLADE, "Xeric's Honour", TeleportLocationData.XERICS_HONOUR.getLocation()), //Require's Ancient Tablet to be used on Xeric's Talisman
    ;

    private final int objectId;
    private final String destinationName;
    private final WorldPoint destination;

    private final int duration = 4;

    @Override
    public boolean execute() {
        Rs2TileObjectModel talisman = getObject();
        if (talisman == null) {
            return false;
        }
        if (talisman.getId() == objectId) {
            //The correct id for the object means it has the right left click option, so we can just use that.
            return Microbot.getRs2TileObjectCache().query()
                    .withIds(IDS)
                    .interact(destinationName);
        }
        Widget widget = getWidget();
        if (widget == null) {
            boolean clicked = Microbot.getRs2TileObjectCache().query()
                    .withIds(IDS)
                    .interact("Teleport menu");
            if (clicked) {
                if (!sleepUntil(() -> getWidget() != null, 10000)) {
                    return false;
                }
            }
        }
        return Rs2Widget.clickWidget(destinationName);
    }

    public static final int[] IDS = {ObjectID.POH_AMULET_XERIC, ObjectID.POH_AMULET_XERIC_LOOKOUT, ObjectID.POH_AMULET_XERIC_GLADE, ObjectID.POH_AMULET_XERIC_INFERNO, ObjectID.POH_AMULET_XERIC_HEART, ObjectID.POH_AMULET_XERIC_HONOUR};

    public static Rs2TileObjectModel getObject() {
        return Microbot.getRs2TileObjectCache().query()
                .withIds(IDS)
                .first();
    }

    public static boolean isMountedXerics(DecorativeObject go) {
        if (go == null) return false;
        for (int objId : IDS) {
            if (objId == go.getId()) return true;
        }
        return false;
    }

    private static Widget getWidget() {
        return Rs2Widget.getWidget(InterfaceID.MENU, 3);
    }

    @Override
    public String displayInfo() {
        return getClass().getSimpleName() + " -> " + destinationName;
    }

}
