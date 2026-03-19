package net.runelite.client.plugins.microbot.commandcenter.scripts.woodcutting;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WoodcuttingTree {
    TREE("Tree", "Logs", "Chop down"),
    OAK("Oak", "Oak logs", "Chop down"),
    WILLOW("Willow", "Willow logs", "Chop down"),
    MAPLE("Maple tree", "Maple logs", "Chop down"),
    YEW("Yew", "Yew logs", "Chop down");

    private final String objectName;
    private final String logName;
    private final String action;
}
