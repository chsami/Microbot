package net.runelite.client.plugins.microbot.aiofighter.shop;

public class ShopItem {
    private final String name;
    private final int quantity;
    private final int itemId;
    private final ShopType shopType;

    public ShopItem(String name, int quantity,int itemId , ShopType shopType) {
        this.name = name;
        this.quantity = quantity;
        this.itemId = itemId;
        this.shopType = shopType;
    }

    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getItemId() {
        return itemId;
    }

    public ShopType getShopType() {
        return shopType;
    }
}
