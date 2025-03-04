package net.runelite.client.plugins.microbot.gboc;

import java.awt.event.MouseEvent;
import java.util.Objects;

import net.runelite.client.input.MouseAdapter;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;

class GbocMouseListener extends MouseAdapter {
    private final GbocConfig config; //FIX this might come in handy later on.

    @Inject
    public GbocMouseListener(GbocConfig config) {
        this.config = config;
    }

    @Override
    public MouseEvent mouseClicked(MouseEvent event) {
        if (!Microbot.isLoggedIn()) return event;
        if (!event.getSource().toString().equals("Microbot")) event.consume();

        if (!Objects.equals(GbocPlugin.currentAction.getName(), "Idle") && !GbocPlugin.currentAction.isRunning()) {
            GbocPlugin.currentAction.run();
        }

        if (event.getSource().toString().equals("gboc")) event.consume();

        return event;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event) {
        if (!Microbot.isLoggedIn()) return event;
        if (!event.getSource().toString().equals("Microbot")) event.consume();
        return event;
    }
}
