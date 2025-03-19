package net.runelite.client.plugins.microbot.crafting.jewelry.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ObjectID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

@Getter
@RequiredArgsConstructor
public enum CraftingLocation {
    
    EDGEVILLE(new WorldPoint(3109, 3499, 0), ObjectID.FURNACE_16469, BankLocation.EDGEVILLE),
    PORT_PHASMATYS(new WorldPoint(3687, 3479, 0), ObjectID.FURNACE_24009, BankLocation.PORT_PHASMATYS),
    MOUNT_KARUULM(new WorldPoint(1324, 3808, 0), ObjectID.VOLCANIC_FURNACE, BankLocation.MOUNT_KARUULM),
    ZANARIS(new WorldPoint(2401, 4473, 0), ObjectID.FURNACE_12100, BankLocation.ZANARIS),
    FALADOR(new WorldPoint(2975, 3369, 0), ObjectID.FURNACE_24009, BankLocation.FALADOR_WEST),
    PRIFDDINAS(new WorldPoint(3287, 6053, 0), ObjectID.FURNACE_36555, BankLocation.PRIFDDINAS_EAST);
    
    private final WorldPoint furnaceLocation;
    private final int furnanceObjectID;
    private final BankLocation bankLocation;
    
    public boolean hasRequirements() {
        switch (this) {
            case PORT_PHASMATYS:
                return Rs2Player.isMember() && Rs2Player.getQuestState(Quest.GHOSTS_AHOY) == QuestState.FINISHED;
            case PRIFDDINAS:
                return Rs2Player.isMember() && Rs2Player.getQuestState(Quest.SONG_OF_THE_ELVES) == QuestState.FINISHED;
            default:
                return true;
        }
    }
}
