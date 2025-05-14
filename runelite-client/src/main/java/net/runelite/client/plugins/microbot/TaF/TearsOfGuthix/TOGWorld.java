package net.runelite.client.plugins.microbot.TaF.TearsOfGuthix;

import lombok.Data;

@Data
public class TOGWorld {
    private int world_number;
    private int hits;
    private String stream_order;

    /**
     * Count how many consecutive blue streams appear in the pattern
     *
     * @return The longest sequence of consecutive blue streams
     */
    public int getLongestBlueSequence() {
        int maxSequence = 0;
        int currentSequence = 0;

        for (char c : stream_order.toCharArray()) {
            if (c == 'b') {
                currentSequence++;
                maxSequence = Math.max(maxSequence, currentSequence);
            } else {
                currentSequence = 0;
            }
        }

        return maxSequence;
    }

    /**
     * Count how many blue streams are in the pattern
     *
     * @return The number of blue streams
     */
    public int getBlueCount() {
        return (int) stream_order.chars().filter(c -> c == 'b').count();
    }
}