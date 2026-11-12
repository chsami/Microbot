package net.runelite.client.plugins.microbot.shortestpath.pathfinder.policy;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

public final class TransportRequirementPolicy {
    private TransportRequirementPolicy() {
    }

    public static boolean completedQuests(Transport transport, List<QuestState> questStateOrder) {
        return transport.getQuests().entrySet().stream()
                .allMatch(entry -> meetsQuestRequirement(entry.getKey(), entry.getValue(), questStateOrder));
    }

    public static boolean meetsQuestRequirement(Quest quest, QuestState requiredState, List<QuestState> questStateOrder) {
        int playerIndex = questStateOrder.indexOf(Rs2Player.getQuestState(quest));
        int requiredIndex = questStateOrder.indexOf(requiredState);
        return requiredIndex >= 0 && playerIndex >= 0 && playerIndex >= requiredIndex;
    }

    public static boolean varbitChecks(Transport transport) {
        return transport.getVarbits().isEmpty()
                || transport.getVarbits().stream()
                .allMatch(varbitCheck -> varbitCheck.matches(Microbot.getVarbitValue(varbitCheck.getVarbitId())));
    }

    public static boolean varplayerChecks(Transport transport) {
        return transport.getVarplayers().isEmpty()
                || transport.getVarplayers().stream()
                .allMatch(varplayerCheck -> varplayerCheck.matches(Microbot.getVarbitPlayerValue(varplayerCheck.getVarplayerId())));
    }
}
