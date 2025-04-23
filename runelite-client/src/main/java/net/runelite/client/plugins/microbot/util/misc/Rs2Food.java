package net.runelite.client.plugins.microbot.util.misc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum Rs2Food {
    // Format: (id, heal, name, tickdelay)
    Dark_Crab(11936, 27, "Dark Crab", 3),
    ROCKTAIL(15272, 23, "Rocktail", 3),
    MANTA(391, 22, "Manta Ray",3),
    SHARK(385, 20, "Shark",3),
    KARAMBWAN(3144, 18, "Cooked karambwan",3),
    LOBSTER(379, 12, "Lobster",3),
    TROUT(333, 7, "Trout",3 ),
    SALMON(329, 9, "Salmon", 3),
    SWORDFISH(373, 14, "Swordfish",3),
    TUNA(361, 10, "Tuna",3),
    MONKFISH(7946, 16, "Monkfish",3),
    SEA_TURTLE(397, 21, "Sea Turtle",3),
    CAKE(1891, 4, "Cake",1),
    BASS(365, 13, "Bass",3),
    COD(339, 7, "Cod",3),
    POTATO(1942, 1, "Potato",3),
    BAKED_POTATO(6701, 4, "Baked Potato",3),
    POTATO_WITH_CHEESE(6705, 16, "Potato with Cheese",3),
    EGG_POTATO(7056, 16, "Egg Potato",3),
    CHILLI_POTATO(7054, 14, "Chilli Potato",3),
    MUSHROOM_POTATO(7058, 20, "Mushroom Potato",3),
    TUNA_POTATO(7060, 22, "Tuna Potato",3),
    SHRIMPS(315, 3, "Shrimps",3),
    HERRING(347, 5, "Herring",3),
    SARDINE(325, 4, "Sardine",3),
    CHOCOLATE_CAKE(1897, 5, "Chocolate Cake",1),
    ANCHOVIES(319, 1, "Anchovies",3),
    PLAIN_PIZZA(2289, 7, "Plain Pizza",1),
    MEAT_PIZZA(2293, 8, "Meat Pizza",1),
    ANCHOVY_PIZZA(2297, 9, "Anchovy Pizza",1),
    PINEAPPLE_PIZZA(2301, 11, "Pineapple Pizza",1),
    BREAD(2309, 5, "Bread",3),
    APPLE_PIE(2323, 7, "Apple Pie",1),
    REDBERRY_PIE(2325, 5, "Redberry Pie",1),
    MEAT_PIE(2327, 6, "Meat Pie",1),
    PIKE(351, 8, "Pike",3),
    POTATO_WITH_BUTTER(6703, 14, "Potato with Butter",3),
    BANANA(1963, 2, "Banana",3),
    PEACH(6883, 8, "Peach",3),
    ORANGE(2108, 2, "Orange",3),
    PINEAPPLE_RINGS(2118, 2, "Pineapple Rings",3),
    PINEAPPLE_CHUNKS(2116, 2, "Pineapple Chunks",3),
    JUG_OF_WINE(1993, 11, "Jug of wine",3),
    COOKED_LARUPIA(29146, 11, "Cooked larupia",3),
    COOKED_BARBTAILED_KEBBIT(29131, 12, "Cooked barb-tailed kebbit",3),
    COOKED_GRAAHK(29149, 14, "Cooked graahk",3),
    COOKED_KYATT(29152, 17, "Cooked kyatt",3),
    COOKED_PYRE_FOX(29137, 19, "Cooked pyre fox",3),
    COOKED_SUNLIGHT_ANTELOPE(29140, 21, "Cooked sunlight antelope",3),
    COOKED_DASHING_KEBBIT(29134, 23, "Cooked dashing kebbit",3),
    COOKED_MOONLIGHT_ANTELOPE(29143, 26, "Cooked moonlight antelope",3),
    PURPLE_SWEETS(10476, 3, "Purple Sweets",3),
    CABBAGE(ItemID.CABBAGE, 1, "Cabbage",3);

    private int id;
    private int heal;
    private String name;
    private final int tickdelay;

    Rs2Food(int id, int heal, String name, int tickdelay) {
        this.id = id;
        this.heal = heal;
        this.name = name;
        this.tickdelay = tickdelay;
    }

    @Override
    public String toString() {
        return name + " (+" + getHeal() + ")";
    }

    public int getId() {
        return id;
    }

    public int getHeal() {
        return heal;
    }

    public String getName() {
        return name;
    }

    // get all ids as a set
    public static Set<Integer> getIds() {
        return Arrays.stream(values()).map(Rs2Food::getId).collect(Collectors.toSet());
    }

}
