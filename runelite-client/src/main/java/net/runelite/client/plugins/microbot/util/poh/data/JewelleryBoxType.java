package net.runelite.client.plugins.microbot.util.poh.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum JewelleryBoxType {
    NONE(-1),
    BASIC(ObjectID.POH_JEWELLERY_BOX_1),
    FANCY(ObjectID.POH_JEWELLERY_BOX_2),
    ORNATE(ObjectID.POH_JEWELLERY_BOX_3);

    private final int objectId;

    /**
     * Gets all teleports available for this jewellery box type
     */
    public List<JewelleryBox> getAvailableTeleports() {
        if (this == NONE) {
            return List.of();
        }
        return JewelleryBox.getAvailableTeleports(this);
    }

    /**
     * Gets all jewellery box object IDs (excluding NONE)
     */
    public static Integer[] getJewelleryBoxIds() {
        return Arrays.stream(values())
                .filter(box -> box != NONE)
                .map(JewelleryBoxType::getObjectId)
                .toArray(Integer[]::new);
    }

    /**
     * Gets the jewellery box GameObject if present in POH
     */
    public static GameObject getObject() {
        return Rs2GameObject.getGameObject(getJewelleryBoxIds());
    }

    /**
     * Determines the current jewellery box type in the POH
     */
    public static JewelleryBoxType getCurrentJewelleryBoxType() {
        GameObject go = getObject();
        if (go == null) {
            return NONE;
        }

        for (JewelleryBoxType boxType : values()) {
            if (boxType.objectId == go.getId()) {
                return boxType;
            }
        }
        return NONE;
    }


    /**
     * Gets the jewellery box type for the specified GameObject
     * @param go GameObject to check. If null, returns null.
     * @return JewelleryBoxType or null if not found.
     */
    public static JewelleryBoxType getJewelleryBoxType(GameObject go) {
        if (go == null) {
            return null;
        }
        int objId = go.getId();
        for (JewelleryBoxType jewelleryBoxType : JewelleryBoxType.values()) {
            if (jewelleryBoxType.objectId == objId) {
                return jewelleryBoxType;
            }
        }
        return null;
    }

}
