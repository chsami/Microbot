package net.runelite.client.plugins.microbot.questhelper.helpers.quests.misthalinmystery;

import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class MisthalinMysteryRequirementsTest
{
    private MisthalinMystery quest;

    @Before
    public void setup()
    {
        quest = new MisthalinMystery()
        {
            @Override
            public QuestHelperConfig getConfig()
            {
                return new QuestHelperConfig() {};
            }
        };
        quest.initializeRequirements();
        quest.setupSteps();
    }

    @Test
    public void bucketPickupDoesNotRequireSuppliesBeforeCollectingBucket()
    {
        assertTrue(quest.takeTheBucket.getRequirements().isEmpty());
    }

    @Test
    public void knifePickupDoesNotRequireSuppliesBeforeCollectingKnife()
    {
        assertTrue(quest.takeKnife.getRequirements().isEmpty());
    }

    @Test
    public void barrelStepsStillRequireBucket()
    {
        assertTrue(quest.searchTheBarrel.getRequirements().contains(quest.bucket));
        assertTrue(quest.useBucketOnBarrel.getRequirements().contains(quest.bucket));
    }

    @Test
    public void cuttingStepsStillRequireKnife()
    {
        assertTrue(quest.useKnifeOnPainting.getRequirements().stream()
            .anyMatch(requirement -> requirement instanceof ItemRequirement
                && ((ItemRequirement) requirement).getId() == quest.knife.getId()));
        assertTrue(quest.useKnifeOnFireplace.getRequirements().stream()
            .anyMatch(requirement -> requirement instanceof ItemRequirement
                && ((ItemRequirement) requirement).getId() == quest.knife.getId()));
    }
}
