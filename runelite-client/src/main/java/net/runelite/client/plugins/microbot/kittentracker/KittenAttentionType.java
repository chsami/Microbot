package net.runelite.client.plugins.microbot.kittentracker;

public enum KittenAttentionType {
    NEW_KITTEN(0, KittenPlugin.ATTENTION_TIME_NEW_KITTEN_IN_SECONDS),
    SINGLE_STROKE(1, KittenPlugin.ATTENTION_TIME_SINGLE_STROKE_IN_SECONDS),
    MULTIPLE_STROKES(2, KittenPlugin.ATTENTION_TIME_MULTIPLE_STROKES_IN_SECONDS),
    BALL_OF_WOOL(3, KittenPlugin.ATTENTION_TIME_BALL_OF_WOOL_IN_SECONDS);

    private final int id;
    private final int attentionTime;

    KittenAttentionType(int id, int attentionTime) {
        this.id = id;
        this.attentionTime = attentionTime;
    }

    int getId() {
        return id;
    }

    int getAttentionTime() {
        return attentionTime;
    }

    int getTimeBeforeKittenRunsAway() {
        return attentionTime + KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS;
    }
}
