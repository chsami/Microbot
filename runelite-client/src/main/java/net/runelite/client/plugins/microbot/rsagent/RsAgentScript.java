package net.runelite.client.plugins.microbot.rsagent;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.rsagent.agent.RsAgentTools;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class RsAgentScript extends Script {

    public static boolean test = false;

    public boolean run(RsAgentConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn())
                    return;
                if (!super.run())
                    return;

                System.out.println("Looping RsAgentScript");
                var dialogue = RsAgentTools.handleDialogue();
                System.out.println(dialogue.dialogueTexts);
                System.out.println(dialogue.options);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}