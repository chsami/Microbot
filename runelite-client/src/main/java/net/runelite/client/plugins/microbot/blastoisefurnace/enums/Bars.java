package net.runelite.client.plugins.microbot.blastoisefurnace.enums;



import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;
import net.runelite.api.Varbits;
@Getter
@RequiredArgsConstructor

public enum Bars {

    STEEL_BAR(
            ItemID.STEEL_BAR,
            ItemID.IRON_ORE,
            1,
            ItemID.COAL,
            1,
            Varbits.BLAST_FURNACE_STEEL_BAR,
            Varbits.BLAST_FURNACE_IRON_ORE,
            Varbits.BLAST_FURNACE_COAL,
            true,
            false
    ),
    GOLD_BAR(
            ItemID.GOLD_BAR,
            ItemID.GOLD_ORE,
            1,
            ItemID.GOLD_ORE,
            1,
            Varbits.BLAST_FURNACE_GOLD_BAR,
            Varbits.BLAST_FURNACE_GOLD_ORE,
            null,
            false,
            true
    ),
    MITHRIL_BAR(
            ItemID.MITHRIL_BAR,
            ItemID.MITHRIL_ORE,
            1,
            ItemID.COAL,
            2,//TODO what about when we get to mithril or higher? I wonder if I could use a while loop for the secondary ore to multiply it
            Varbits.BLAST_FURNACE_MITHRIL_BAR,
            Varbits.BLAST_FURNACE_MITHRIL_ORE,
            Varbits.BLAST_FURNACE_COAL,
            true,
            false
    ),
    ADAMANTITE_BAR(
            ItemID.ADAMANTITE_BAR,
            ItemID.ADAMANTITE_ORE,
            1,
            ItemID.COAL,
            6,
            Varbits.BLAST_FURNACE_ADAMANTITE_BAR,
            Varbits.BLAST_FURNACE_ADAMANTITE_ORE,
            Varbits.BLAST_FURNACE_COAL,
            true,
            false
    ),
    RUNITE_BAR(
            ItemID.RUNITE_BAR,
            ItemID.RUNITE_ORE,
            1,
            ItemID.COAL,
            8,
            Varbits.BLAST_FURNACE_RUNITE_BAR,
            Varbits.BLAST_FURNACE_RUNITE_ORE,
            Varbits.BLAST_FURNACE_COAL,
            true,
            false
    ),
    HYBRID_MITHRIL_BAR(
            ItemID.MITHRIL_BAR,
            ItemID.MITHRIL_ORE,
            1,
            ItemID.COAL,
            2,
            Varbits.BLAST_FURNACE_MITHRIL_BAR,
            Varbits.BLAST_FURNACE_MITHRIL_ORE,
            Varbits.BLAST_FURNACE_COAL,
            true,
            true
    ),
    HYBRID_ADAMANTITE_BAR(
            ItemID.ADAMANTITE_BAR,
            ItemID.ADAMANTITE_ORE,
            1,
            ItemID.COAL,
            6,
            Varbits.BLAST_FURNACE_ADAMANTITE_BAR,
            Varbits.BLAST_FURNACE_ADAMANTITE_ORE,
            Varbits.BLAST_FURNACE_COAL,
            true,
            true
    ),
    HYBRID_RUNITE_BAR(
            ItemID.RUNITE_BAR,
            ItemID.RUNITE_ORE,
            1,
            ItemID.COAL,
            8,
            Varbits.BLAST_FURNACE_RUNITE_BAR,
            Varbits.BLAST_FURNACE_RUNITE_ORE,
            Varbits.BLAST_FURNACE_COAL,
            true,
            true
    );

    private final int barID;
    private final int PrimaryOre;
    private final int PrimaryOreNeeded;
    private final Integer SecondaryOre;
    private final Integer SecondaryOreNeeded;
    private final int BFBarID;
    private final int BFPrimaryOreID;
    private final Integer BFSecondaryOreID;
    private final boolean requiresCoalBag;
    private final boolean requiresGoldsmithGloves;


}
