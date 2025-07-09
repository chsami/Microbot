package net.runelite.client.plugins.microbot.blastoisefurnace.enums;



import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

import static net.runelite.api.gameval.ItemID.*;
import static net.runelite.api.gameval.VarbitID.*;

@Getter
@RequiredArgsConstructor

public enum Bars {

    STEEL_BAR(
            ItemID.STEEL_BAR,
            IRON_ORE,
            1,
            COAL,
            1,
            BLAST_FURNACE_STEEL_BARS,
            BLAST_FURNACE_IRON_ORE,
            BLAST_FURNACE_COAL,
            true,
            false
    ),
    GOLD_BAR(
            ItemID.GOLD_BAR,
            GOLD_ORE,
            1,
            GOLD_ORE,
            1,
            BLAST_FURNACE_GOLD_BARS,
            BLAST_FURNACE_GOLD_ORE,
            null,
            false,
            true
    ),
    MITHRIL_BAR(
            ItemID.MITHRIL_BAR,
            MITHRIL_ORE,
            1,
            COAL,
            2,//TODO what about when we get to mithril or higher? I wonder if I could use a while loop for the secondary ore to multiply it
            BLAST_FURNACE_MITHRIL_BARS,
            BLAST_FURNACE_MITHRIL_ORE,
            BLAST_FURNACE_COAL,
            true,
            false
    ),
    ADAMANTITE_BAR(
            ItemID.ADAMANTITE_BAR,
            ADAMANTITE_ORE,
            1,
            COAL,
            6,
            BLAST_FURNACE_ADAMANTITE_BARS,
            BLAST_FURNACE_ADAMANTITE_ORE,
            BLAST_FURNACE_COAL,
            true,
            false
    ),
    RUNITE_BAR(
            ItemID.RUNITE_BAR,
            RUNITE_ORE,
            1,
            COAL,
            8,
            BLAST_FURNACE_RUNITE_BARS,
            BLAST_FURNACE_RUNITE_ORE,
            BLAST_FURNACE_COAL,
            true,
            false
    ),
    HYBRID_MITHRIL_BAR(
            ItemID.MITHRIL_BAR,
            MITHRIL_ORE,
            1,
            COAL,
            2,
            BLAST_FURNACE_MITHRIL_BARS,
            BLAST_FURNACE_MITHRIL_ORE,
            BLAST_FURNACE_COAL,
            true,
            true
    ),
    HYBRID_ADAMANTITE_BAR(
            ItemID.ADAMANTITE_BAR,
            ADAMANTITE_ORE,
            1,
            COAL,
            6,
            BLAST_FURNACE_ADAMANTITE_BARS,
            BLAST_FURNACE_ADAMANTITE_ORE,
            BLAST_FURNACE_COAL,
            true,
            true
    ),
    HYBRID_RUNITE_BAR(
            ItemID.RUNITE_BAR,
            RUNITE_ORE,
            1,
            COAL,
            8,
            BLAST_FURNACE_RUNITE_BARS,
            BLAST_FURNACE_RUNITE_ORE,
            BLAST_FURNACE_COAL,
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
