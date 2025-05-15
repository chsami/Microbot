package net.runelite.client.plugins.microbot.bee.MossKiller.Enums;

import lombok.Getter;
import net.runelite.api.ItemID;

import static net.runelite.api.ItemID.*;

public class GearEnums {

    public enum RangedAmulet {
        ACCURACY("Amulet of accuracy", 1478),
        POWER("Amulet of power", 1731);

        private final String name;
        @Getter
        private final int itemId;

        RangedAmulet(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum RangedTorso {
        LEATHER("Leather body", 1129),
        HARDLEATHER("Hardleather body", 1131),
        STUDDED("Studded body", 1133),
        GREEN_DHIDE("Green d'hide body", 1135);

        private final String name;
        @Getter
        private final int itemId;

        RangedTorso(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum RangedChaps {
        LEATHER("Leather chaps", 1095),
        STUDDED("Studded chaps", 1097),
        GREEN_DHIDE("Green d'hide chaps", 1099);

        private final String name;
        @Getter
        private final int itemId;

        RangedChaps(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public enum Cape {
        BLACK_CAPE("Black cape", 1019),
        BLUE_CAPE("Blue cape", 1021),
        YELLOW_CAPE("Yellow cape", 1023),
        RED_CAPE("Red cape", 1007),
        GREEN_CAPE("Green cape", 1027),
        PURPLE_CAPE("Purple cape", 1029),
        ORANGE_CAPE("Orange cape", 1031),

        // Team capes (1-50)
        TEAM_CAPE_1("Team-1 Cape", TEAM1_CAPE),
        TEAM_CAPE_2("Team-2 Cape", TEAM2_CAPE),
        TEAM_CAPE_3("Team-3 Cape", TEAM3_CAPE),
        TEAM_CAPE_4("Team-4 Cape", TEAM4_CAPE),
        TEAM_CAPE_5("Team-5 Cape", TEAM5_CAPE),
        TEAM_CAPE_6("Team-6 Cape", TEAM6_CAPE),
        TEAM_CAPE_7("Team-7 Cape", TEAM7_CAPE),
        TEAM_CAPE_8("Team-8 Cape", TEAM8_CAPE),
        TEAM_CAPE_9("Team-9 Cape", TEAM9_CAPE),
        TEAM_CAPE_10("Team-10 Cape", TEAM10_CAPE),
        TEAM_CAPE_11("Team-11 Cape", TEAM11_CAPE),
        TEAM_CAPE_12("Team-12 Cape", TEAM12_CAPE),
        TEAM_CAPE_13("Team-13 Cape", TEAM13_CAPE),
        TEAM_CAPE_14("Team-14 Cape", TEAM14_CAPE),
        TEAM_CAPE_15("Team-15 Cape", TEAM15_CAPE),
        TEAM_CAPE_16("Team-16 Cape", TEAM16_CAPE),
        TEAM_CAPE_17("Team-17 Cape", TEAM17_CAPE),
        TEAM_CAPE_18("Team-18 Cape", TEAM18_CAPE),
        TEAM_CAPE_19("Team-19 Cape", TEAM19_CAPE),
        TEAM_CAPE_20("Team-20 Cape", TEAM20_CAPE),
        TEAM_CAPE_21("Team-21 Cape", TEAM21_CAPE),
        TEAM_CAPE_22("Team-22 Cape", TEAM22_CAPE),
        TEAM_CAPE_23("Team-23 Cape", TEAM23_CAPE),
        TEAM_CAPE_24("Team-24 Cape", TEAM24_CAPE),
        TEAM_CAPE_25("Team-25 Cape", TEAM25_CAPE),
        TEAM_CAPE_26("Team-26 Cape", TEAM26_CAPE),
        TEAM_CAPE_27("Team-27 Cape", TEAM27_CAPE),
        TEAM_CAPE_28("Team-28 Cape", TEAM28_CAPE),
        TEAM_CAPE_29("Team-29 Cape", TEAM29_CAPE),
        TEAM_CAPE_30("Team-30 Cape", TEAM30_CAPE),
        TEAM_CAPE_31("Team-31 Cape", TEAM31_CAPE),
        TEAM_CAPE_32("Team-32 Cape", TEAM32_CAPE),
        TEAM_CAPE_33("Team-33 Cape", TEAM33_CAPE),
        TEAM_CAPE_34("Team-34 Cape", TEAM34_CAPE),
        TEAM_CAPE_35("Team-35 Cape", TEAM35_CAPE),
        TEAM_CAPE_36("Team-36 Cape", TEAM36_CAPE),
        TEAM_CAPE_37("Team-37 Cape", TEAM37_CAPE),
        TEAM_CAPE_38("Team-38 Cape", TEAM38_CAPE),
        TEAM_CAPE_39("Team-39 Cape", TEAM39_CAPE),
        TEAM_CAPE_40("Team-40 Cape", TEAM40_CAPE),
        TEAM_CAPE_41("Team-41 Cape", TEAM41_CAPE),
        TEAM_CAPE_42("Team-42 Cape", TEAM42_CAPE),
        TEAM_CAPE_43("Team-43 Cape", TEAM43_CAPE),
        TEAM_CAPE_44("Team-44 Cape", TEAM44_CAPE),
        TEAM_CAPE_45("Team-45 Cape", TEAM45_CAPE),
        TEAM_CAPE_46("Team-46 Cape", TEAM46_CAPE),
        TEAM_CAPE_47("Team-47 Cape", TEAM47_CAPE),
        TEAM_CAPE_48("Team-48 Cape", TEAM48_CAPE),
        TEAM_CAPE_49("Team-49 Cape", TEAM49_CAPE),
        TEAM_CAPE_50("Team-50 Cape", TEAM50_CAPE),


        // Team capes (i, x, zero)
        TEAM_CAPE_I("Team cape i", ItemID.TEAM_CAPE_I),
        TEAM_CAPE_X("Team cape x", ItemID.TEAM_CAPE_X),
        TEAM_CAPE_ZERO("Team cape zero", ItemID.TEAM_CAPE_ZERO);

        private final String name;
        @Getter
        private final int itemId;

        Cape(String name, int itemId) {
            this.name = name;
            this.itemId = itemId;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
