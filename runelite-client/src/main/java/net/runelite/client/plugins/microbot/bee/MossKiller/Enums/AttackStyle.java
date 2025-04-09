package net.runelite.client.plugins.microbot.bee.MossKiller.Enums;

public enum AttackStyle {
    MAGIC("Magic"),
    RANGE("Range");

    private final String name;

    AttackStyle(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}