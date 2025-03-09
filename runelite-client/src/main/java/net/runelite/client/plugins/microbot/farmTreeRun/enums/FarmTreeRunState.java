package net.runelite.client.plugins.microbot.farmTreeRun.enums;

// This script does handle tree patches in the order below:
// 1.  Gnome stronghold -> Fruit tree
// 2.  Gnome stronghold -> Tree
// 3.  Tree gnome village -> Fruit tree
// 4.  Farming guild -> Tree
// 5.  Farming guild -> Fruit tree
// 6.  Taverley -> Tree
// 7.  Falador -> Tree
// 8.  Lumbridge -> Tree
// 9.  Varrock -> Tree
// 10. Brimhaven -> Fruit tree
// 11. Catherby -> Fruit tree
// 12. Lletya -> Fruit tree

public enum FarmTreeRunState {
    BANKING,

    HANDLE_GNOME_STRONGHOLD_FRUIT_PATCH,
    HANDLE_GNOME_STRONGHOLD_TREE_PATCH,

    HANDLE_TREE_GNOME_VILLAGE_FRUIT_TREE_PATCH,

    HANDLE_FARMING_GUILD_TREE_PATCH,
    HANDLE_FARMING_GUILD_FRUIT_PATCH,

    HANDLE_TAVERLEY_TREE_PATCH,

    HANDLE_FALADOR_TREE_PATCH,

    HANDLE_LUMBRIDGE_TREE_PATCH,

    HANDLE_VARROCK_TREE_PATCH,

    HANDLE_BRIMHAVEN_FRUIT_TREE_PATCH,

    HANDLE_CATHERBY_FRUIT_TREE_PATCH,

    HANDLE_LLETYA_FRUIT_TREE_PATCH,

    FINISHED
}
