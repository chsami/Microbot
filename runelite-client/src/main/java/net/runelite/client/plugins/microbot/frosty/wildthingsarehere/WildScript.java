package net.runelite.client.plugins.microbot.frosty.wildthingsarehere;

import net.runelite.api.NpcID;
import net.runelite.client.plugins.bosstimer.Boss;
import net.runelite.client.plugins.bosstimer.RespawnTimer;
import net.runelite.client.plugins.bosstimer.BossTimersPlugin;

import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.api.NpcID;
import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

public class WildScript extends Script{
    private ArtioState currentState = ArtioState.IDLE;

    @Override
    public void start() {
        System.out.println("Artio Fighter Script Started!");
    }

    @Override
    public void stop() {
        System.out.println("Artio Fighter Script Stopped!");
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        handleState();
    }

    private boolean isArtioNearby() {
        return getNearestNpc(NpcID.ARTIO) != null;
    }

    private boolean isLowHealth() {
        return Microbot.getClient().getLocalPlayer().getHealth() < 30; // Example threshold
    }

    private boolean isSafe() {
        return Microbot.getClient().getLocalPlayer().getHealth() > 50;
    }

    private boolean isFullHealth() {
        return Microbot.getClient().getLocalPlayer().getHealth() > 90;
    }

    private void attackArtio() {
        Microbot.getRs2Npc().attack(NpcID.ARTIO);
    }

    private void eatFood() {
        Microbot.getRs2Inventory().eatFood();
    }
}
