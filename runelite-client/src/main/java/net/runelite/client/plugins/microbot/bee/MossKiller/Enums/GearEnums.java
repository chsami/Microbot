package net.runelite.client.plugins.microbot.bee.MossKiller.Enums;

import lombok.Getter;

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
        TEAM_CAPE_1("Team cape 1", 4315),
        TEAM_CAPE_2("Team cape 2", 4316),
        TEAM_CAPE_3("Team cape 3", 4317),
        TEAM_CAPE_4("Team cape 4", 4318),
        TEAM_CAPE_5("Team cape 5", 4319),
        TEAM_CAPE_6("Team cape 6", 4320),
        TEAM_CAPE_7("Team cape 7", 4321),
        TEAM_CAPE_8("Team cape 8", 4322),
        TEAM_CAPE_9("Team cape 9", 4323),
        TEAM_CAPE_10("Team cape 10", 4324),
        TEAM_CAPE_11("Team cape 11", 4325),
        TEAM_CAPE_12("Team cape 12", 4326),
        TEAM_CAPE_13("Team cape 13", 4327),
        TEAM_CAPE_14("Team cape 14", 4328),
        TEAM_CAPE_15("Team cape 15", 4329),
        TEAM_CAPE_16("Team cape 16", 4330),
        TEAM_CAPE_17("Team cape 17", 4331),
        TEAM_CAPE_18("Team cape 18", 4332),
        TEAM_CAPE_19("Team cape 19", 4333),
        TEAM_CAPE_20("Team cape 20", 4334),
        TEAM_CAPE_21("Team cape 21", 4335),
        TEAM_CAPE_22("Team cape 22", 4336),
        TEAM_CAPE_23("Team cape 23", 4337),
        TEAM_CAPE_24("Team cape 24", 4338),
        TEAM_CAPE_25("Team cape 25", 4339),
        TEAM_CAPE_26("Team cape 26", 4340),
        TEAM_CAPE_27("Team cape 27", 4341),
        TEAM_CAPE_28("Team cape 28", 4342),
        TEAM_CAPE_29("Team cape 29", 4343),
        TEAM_CAPE_30("Team cape 30", 4344),
        TEAM_CAPE_31("Team cape 31", 4345),
        TEAM_CAPE_32("Team cape 32", 4346),
        TEAM_CAPE_33("Team cape 33", 4347),
        TEAM_CAPE_34("Team cape 34", 4348),
        TEAM_CAPE_35("Team cape 35", 4349),
        TEAM_CAPE_36("Team cape 36", 4350),
        TEAM_CAPE_37("Team cape 37", 4351),
        TEAM_CAPE_38("Team cape 38", 4352),
        TEAM_CAPE_39("Team cape 39", 4353),
        TEAM_CAPE_40("Team cape 40", 4354),
        TEAM_CAPE_41("Team cape 41", 4355),
        TEAM_CAPE_42("Team cape 42", 4356),
        TEAM_CAPE_43("Team cape 43", 4357),
        TEAM_CAPE_44("Team cape 44", 4358),
        TEAM_CAPE_45("Team cape 45", 4359),
        TEAM_CAPE_46("Team cape 46", 4360),
        TEAM_CAPE_47("Team cape 47", 4361),
        TEAM_CAPE_48("Team cape 48", 4362),
        TEAM_CAPE_49("Team cape 49", 4363),
        TEAM_CAPE_50("Team cape 50", 4364),

        // Team capes (i, x, zero)
        TEAM_CAPE_I("Team cape i", 4369),
        TEAM_CAPE_X("Team cape x", 4370),
        TEAM_CAPE_ZERO("Team cape zero", 4371);

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
