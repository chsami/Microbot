package net.runelite.client.plugins.microbot.aiofighter.shop;

public enum ShopType {
    // General stores, Grant Exchange, Slayer shop
    GENERAL_STORE("General Store"),
    GRAND_EXCHANGE("Grand Exchange"),
    SLAYER_SHOP("Slayer Shop")
    ;
    private final String name;
    ShopType(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

}
