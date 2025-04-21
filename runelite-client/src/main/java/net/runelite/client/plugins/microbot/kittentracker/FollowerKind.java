package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.api.NpcID;

public enum FollowerKind {
    NORMAL_CAT, LAZY_CAT, WILY_CAT, KITTEN, OVERGROWN_CAT, NON_FELINE;

    public static FollowerKind getFromFollowerId(int followerId) {
        if (NpcID.CAT_1619 <= followerId && NpcID.HELLCAT >= followerId) {
            return NORMAL_CAT;
        } else if (NpcID.LAZY_CAT <= followerId && NpcID.LAZY_HELLCAT >= followerId) {
            return LAZY_CAT;
        } else if (NpcID.WILY_CAT <= followerId && NpcID.WILY_HELLCAT >= followerId) {
            return WILY_CAT;
        } else if (NpcID.KITTEN_5591 <= followerId && NpcID.HELLKITTEN >= followerId) {
            return KITTEN;
        } else if (NpcID.OVERGROWN_CAT <= followerId && NpcID.OVERGROWN_HELLCAT >= followerId) {
            return OVERGROWN_CAT;
        } else {
            return NON_FELINE;
        }
    }
}
