package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.ObjectID;
import net.runelite.api.annotations.Varp;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeathEvent implements BlockingEvent {

    @Varp
    private final int DEATH_COUNTER_VARP = 4517;
    private final int DEATH_DOMAIN_REGION_ID = 12633;
    private final int DEATHS_PORTAL = ObjectID.PORTAL_39549;

    @Override
    public boolean validate() {
        return Microbot.getVarbitPlayerValue(DEATH_COUNTER_VARP) == 1
                && Rs2Player.getWorldLocation().getRegionID() == DEATH_DOMAIN_REGION_ID;
    }

    @Override
    public boolean execute() {
        if (Rs2Player.isMoving()) return false;

        if (Rs2Dialogue.isInDialogue()) {
            if (Rs2Dialogue.hasContinue()) {
                Rs2Dialogue.clickContinue();
                return true;
            }

            if (Rs2Dialogue.hasSelectAnOption()) {
                List<Widget> completedDialogueOptions = Rs2Dialogue.getDialogueOptions().stream()
                        .filter(opt -> opt.getText() != null && opt.getText().matches("<str>.*</str>"))
                        .collect(Collectors.toList());

                if (completedDialogueOptions.size() >= 4) {
                    Rs2GameObject.interact(DEATHS_PORTAL, "use");
                    return Global.sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() != DEATH_DOMAIN_REGION_ID, 10000);
                }

                Optional<Widget> incompleteDialogOptional = Rs2Dialogue.getDialogueOptions().stream()
                        .filter(opt -> !completedDialogueOptions.contains(opt))
                        .findFirst();

                if (incompleteDialogOptional.isPresent()) {
                    Widget incompleteDialog = incompleteDialogOptional.get();
                    return Rs2Dialogue.keyPressForDialogueOption(Rs2Dialogue.getDialogueOptions().indexOf(incompleteDialog) + 1);
                }
            }
        }
        return false;
    }

    @Override
    public BlockingEventPriority priority() {
        return BlockingEventPriority.HIGH;
    }
}
