package net.runelite.client.plugins.microbot.kittentracker;

import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import javax.inject.Inject;

public class KittenAttentionEvent implements BlockingEvent
{
    private final KittenPlugin kittenPlugin;
    @Inject
    public KittenAttentionEvent(KittenPlugin kittenPlugin)
    {
        this.kittenPlugin = kittenPlugin;
    }

    @Override
    public boolean validate()
    {
        return (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) >= kittenPlugin.getTimeBeforeNeedingAttention();
    }

    @Override
    public boolean execute()
    {
        Rs2Npc.getNpcs("Kitten").findFirst().ifPresent(kitten -> Rs2Npc.interact(kitten, "Interact"));
        if (Rs2Dialogue.sleepUntilHasDialogueOption("Stroke")) {
            Rs2Dialogue.clickOption("Stroke");
        }
        Global.sleepUntil(() -> (KittenPlugin.ATTENTION_FIRST_WARNING_TIME_LEFT_IN_SECONDS * 1000) < kittenPlugin.getTimeBeforeNeedingAttention(),10000);
        return true;
    }

    @Override
    public BlockingEventPriority priority()
    {
        return BlockingEventPriority.NORMAL;
    }
}
