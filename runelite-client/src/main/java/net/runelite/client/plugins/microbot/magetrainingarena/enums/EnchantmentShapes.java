package net.runelite.client.plugins.microbot.magetrainingarena.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;

@Getter
@AllArgsConstructor
public enum EnchantmentShapes {
    PENTAMID(ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE4, ItemID.MAGICTRAINING_ENCHAN_PENTAMID, 195, 14),
    ICOSAHEDRON(ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE3, ItemID.MAGICTRAINING_ENCHAN_ICOSAHENDRON, 195, 16),
    CUBE(ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE1, ItemID.MAGICTRAINING_ENCHAN_CUBE, 195, 10),
    CYLINDER(ObjectID.MAGICTRAINING_ENCHAN_SHAPEPILE2, ItemID.MAGICTRAINING_ENCHAN_CYLINDER, 195, 12);

    private final int objectId;
    private final int itemId;
    private final int widgetId;
    private final int widgetChildId;
}
