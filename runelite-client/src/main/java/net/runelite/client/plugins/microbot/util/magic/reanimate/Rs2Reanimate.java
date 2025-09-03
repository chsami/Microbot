package net.runelite.client.plugins.microbot.util.magic.reanimate;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Rs2Reanimate class contains methods that handle the reanimation process
 * based on specific Ensouled heads.
 */
public class Rs2Reanimate {

    public static boolean isReanimated(Rs2NpcModel npc) {
        if (npc == null) return false;
        String name = npc.getName();
        if (name == null) return false;
        return name.contains("Reanimated");
    }

    public static boolean reanimate() {
        Rs2ItemModel head = getHead();
        if (head == null) return false;
        return reanimate(head);
    }

    public static boolean reanimate(Rs2ItemModel head) {
        HeadType type = HeadType.getHeadType(head);
        if (type == null) return false;
        return type.reanimate(head);
    }

    public static Rs2ItemModel getHead() {
        return Rs2Inventory.get(i ->
                HeadType.getHeadType(i) != null
        );
    }

    /**
     * Retrieves a List of items in the inventory that can be reanimated.
     * The method filters items based on their availability as a valid Ensouled head type,
     * checking if the player can cast the associated reanimation spell.
     *
     * @return a List with entries where the keys are reanimatable Ensouled heads,
     * and the values are the corresponding HeadType objects that provide reanimation spell information.
     */
    public static List<Map.Entry<Rs2ItemModel, HeadType>> getReanimatableHeads() {
        return Rs2Inventory.items()
                .map(i -> new AbstractMap.SimpleImmutableEntry<>(i, HeadType.getHeadType(i)))
                .filter(e -> {
                    HeadType ht = e.getValue();
                    return ht != null
                            && ht.getSpell().hasRequirements()
                            && Rs2Magic.hasRequiredRunes(ht.getSpell());
                })
                .collect(Collectors.toList());
    }

    public static Map.Entry<Rs2ItemModel, HeadType> getReanimatableHead() {
        return getReanimatableHeads().stream().findFirst().orElse(null);
    }

}
