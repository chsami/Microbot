package net.runelite.client.plugins.microbot.util.dialogues;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2DialogueTest {
    @Test
    public void isContinuePromptTextAcceptsContinuePrompt() {
        assertTrue(Rs2Dialogue.isContinuePromptText("<col=ffffff>Click here to continue</col>"));
    }

    @Test
    public void isContinuePromptTextRejectsChatboxInputs() {
        assertFalse(Rs2Dialogue.isContinuePromptText("Enter amount:"));
        assertFalse(Rs2Dialogue.isContinuePromptText("Search"));
        assertFalse(Rs2Dialogue.isContinuePromptText("abyssal whip"));
    }

    @Test
    public void isQuestStartQuestionAcceptsBothPromptWordings() {
        assertTrue(Rs2Dialogue.isQuestStartQuestion("Start the Family Crest quest?"));
        assertTrue(Rs2Dialogue.isQuestStartQuestion("Start the Romeo & Juliet quest?"));
        assertTrue(Rs2Dialogue.isQuestStartQuestion("Would you like to start the Cook's Assistant quest?"));
    }

    @Test
    public void isQuestStartQuestionRejectsOtherPrompts() {
        assertFalse(Rs2Dialogue.isQuestStartQuestion(null));
        assertFalse(Rs2Dialogue.isQuestStartQuestion("Select an Option"));
        assertFalse(Rs2Dialogue.isQuestStartQuestion("Would you like to start a fire?"));
        assertFalse(Rs2Dialogue.isQuestStartQuestion("Start the fire?"));
        assertFalse(Rs2Dialogue.isQuestStartQuestion("Restart the quest?"));
    }
}
