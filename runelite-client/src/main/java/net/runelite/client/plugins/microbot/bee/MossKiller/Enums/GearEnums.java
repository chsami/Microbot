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
        TEAM_CAPE_1("Team-1 Cape", 4315),
        TEAM_CAPE_2("Team-2 Cape", 4316),
        TEAM_CAPE_3("Team-3 Cape", 4317),
        TEAM_CAPE_4("Team-4 Cape", 4318),
        TEAM_CAPE_5("Team-5 Cape", 4319),
        TEAM_CAPE_6("Team-6 Cape", 4320),
        TEAM_CAPE_7("Team-7 Cape", 4321),
        TEAM_CAPE_8("Team-8 Cape", 4322),
        TEAM_CAPE_9("Team-9 Cape", 4323),
        TEAM_CAPE_10("Team-10 Cape", 4324),
        TEAM_CAPE_11("Team-11 Cape", 4325),
        TEAM_CAPE_12("Team-12 Cape", 4326),
        TEAM_CAPE_13("Team-13 Cape", 4327),
        TEAM_CAPE_14("Team-14 Cape", 4328),
        TEAM_CAPE_15("Team-15 Cape", 4329),
        TEAM_CAPE_16("Team-16 Cape", 4330),
        TEAM_CAPE_17("Team-17 Cape", 4331),
        TEAM_CAPE_18("Team-18 Cape", 4332),
        TEAM_CAPE_19("Team-19 Cape", 4333),
        TEAM_CAPE_20("Team-20 Cape", 4334),
        TEAM_CAPE_21("Team-21 Cape", 4335),
        TEAM_CAPE_22("Team-22 Cape", 4336),
        TEAM_CAPE_23("Team-23 Cape", 4337),
        TEAM_CAPE_24("Team-24 Cape", 4338),
        TEAM_CAPE_25("Team-25 Cape", 4339),
        TEAM_CAPE_26("Team-26 Cape", 4340),
        TEAM_CAPE_27("Team-27 Cape", 4341),
        TEAM_CAPE_28("Team-28 Cape", 4342),
        TEAM_CAPE_29("Team-29 Cape", 4343),
        TEAM_CAPE_30("Team-30 Cape", 4344),
        TEAM_CAPE_31("Team-31 Cape", 4345),
        TEAM_CAPE_32("Team-32 Cape", 4346),
        TEAM_CAPE_33("Team-33 Cape", 4347),
        TEAM_CAPE_34("Team-34 Cape", 4348),
        TEAM_CAPE_35("Team-35 Cape", 4349),
        TEAM_CAPE_36("Team-36 Cape", 4350),
        TEAM_CAPE_37("Team-37 Cape", 4351),
        TEAM_CAPE_38("Team-38 Cape", 4352),
        TEAM_CAPE_39("Team-39 Cape", 4353),
        TEAM_CAPE_40("Team-40 Cape", 4354),
        TEAM_CAPE_41("Team-41 Cape", 4355),
        TEAM_CAPE_42("Team-42 Cape", 4356),
        TEAM_CAPE_43("Team-43 Cape", 4357),
        TEAM_CAPE_44("Team-44 Cape", 4358),
        TEAM_CAPE_45("Team-45 Cape", 4359),
        TEAM_CAPE_46("Team-46 Cape", 4360),
        TEAM_CAPE_47("Team-47 Cape", 4361),
        TEAM_CAPE_48("Team-48 Cape", 4362),
        TEAM_CAPE_49("Team-49 Cape", 4363),
        TEAM_CAPE_50("Team-50 Cape", 4364),


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
