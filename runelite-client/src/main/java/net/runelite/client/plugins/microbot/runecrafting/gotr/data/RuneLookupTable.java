package net.runelite.client.plugins.microbot.runecrafting.gotr.data;

import net.runelite.api.gameval.ItemID;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuneLookupTable {
    private final Map<Integer, List<LevelMultiplier>> lookupTable;

    public RuneLookupTable() {
        this.lookupTable = new HashMap<Integer, List<LevelMultiplier>>() {{
            put(ItemID.AIRRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(11, 2));
                add(new LevelMultiplier(22, 3));
                add(new LevelMultiplier(33, 4));
                add(new LevelMultiplier(44, 5));
                add(new LevelMultiplier(55, 6));
                add(new LevelMultiplier(66, 7));
                add(new LevelMultiplier(77, 8));
                add(new LevelMultiplier(88, 9));
                add(new LevelMultiplier(99, 10));
            }});
            put(ItemID.MINDRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(14, 2));
                add(new LevelMultiplier(28, 3));
                add(new LevelMultiplier(42, 4));
                add(new LevelMultiplier(56, 5));
                add(new LevelMultiplier(70, 6));
                add(new LevelMultiplier(84, 7));
                add(new LevelMultiplier(98, 8));
            }});
            put(ItemID.WATERRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(19, 2));
                add(new LevelMultiplier(38, 3));
                add(new LevelMultiplier(57, 4));
                add(new LevelMultiplier(76, 5));
                add(new LevelMultiplier(95, 6));
            }});
            put(ItemID.EARTHRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(26, 2));
                add(new LevelMultiplier(52, 3));
                add(new LevelMultiplier(78, 4));
                add(new LevelMultiplier(104, 5));
            }});
            put(ItemID.FIRERUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(35, 2));
                add(new LevelMultiplier(70, 3));
            }});
            put(ItemID.BODYRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(46, 2));
                add(new LevelMultiplier(92, 3));
            }});
            put(ItemID.COSMICRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(59, 2));
            }});
            put(ItemID.CHAOSRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(74, 2));
            }});
            put(ItemID.NATURERUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(91, 2));
            }});
            put(ItemID.LAWRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(95, 2));
            }});
            put(ItemID.DEATHRUNE, new ArrayList<LevelMultiplier>() {{
                add(new LevelMultiplier(99, 2));
            }});
        }};
    }

    public int getHighestMultiplier(int runeId, int level) {
        List<LevelMultiplier> runeMultipliers = lookupTable.get(runeId);
        if (runeMultipliers == null) {
            // Rune ID not found in the lookup table
            return 1;
        }

        int highestMultiplier = 1;
        for (LevelMultiplier levelMultiplier : runeMultipliers) {
            if (levelMultiplier.getLevel() <= level && levelMultiplier.getMultiplier() > highestMultiplier) {
                highestMultiplier = levelMultiplier.getMultiplier();
            }
        }

        return highestMultiplier;
    }

    private static class LevelMultiplier {
        private final int level;
        private final int multiplier;

        public LevelMultiplier(int level, int multiplier) {
            this.level = level;
            this.multiplier = multiplier;
        }

        public int getLevel() {
            return level;
        }

        public int getMultiplier() {
            return multiplier;
        }
    }
}