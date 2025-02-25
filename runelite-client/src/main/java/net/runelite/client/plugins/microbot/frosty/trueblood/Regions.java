package net.runelite.client.plugins.microbot.frosty.trueblood;

public enum Regions {
    CRAFTING_GUILD(11571);

    private final int regionId;

    Regions(int regionId) {
        this.regionId = regionId;
    }

    public int getRegionId() {
        return regionId;
    }

    public static Regions fromId(int id) {
        for (Regions region : values()) {
            if (region.getRegionId() == id) {
                return region;
            }
        }
        return null;
    }
}
