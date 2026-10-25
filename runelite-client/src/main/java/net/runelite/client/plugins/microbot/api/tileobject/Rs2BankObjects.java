package net.runelite.client.plugins.microbot.api.tileobject;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.TileObjectType;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class Rs2BankObjects {

    public static final int DEFAULT_SEARCH_RADIUS = 20;

    private static final int[] BANK_IDS = Rs2BankID.BANK_ID_SET.stream().mapToInt(Integer::intValue).toArray();
    private static final int[] GRAND_EXCHANGE_BOOTH_IDS = {10060, 30389};

    private static final Set<WorldPoint> COOKS_GUILD_BANKS = Set.of(new WorldPoint(3147, 3449, 0), new WorldPoint(3148, 3449, 0));
    private static final Set<WorldPoint> FARMING_GUILD_BANKS = Set.of(new WorldPoint(1248, 3759, 0), new WorldPoint(1249, 3759, 0));
    private static final WorldPoint LUNAR_ISLE_DREAM_MENTOR_BANK = new WorldPoint(2099, 3920, 0);
    private static final Set<WorldPoint> LUNAR_ISLE_SEAL_BANKS = Set.of(new WorldPoint(2098, 3920, 0), new WorldPoint(2097, 3920, 0));

    private Rs2BankObjects() {
    }

    public static Rs2TileObjectModel findBank() {
        return findBank(DEFAULT_SEARCH_RADIUS);
    }

    public static Rs2TileObjectModel findBank(int maxSearchRadius) {
        List<Rs2TileObjectModel> candidates = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .ofType(TileObjectType.GAME)
                .withIds(BANK_IDS)
                .within(maxSearchRadius)
                .where(o -> o.hasActionContaining("Bank") || o.hasActionContaining("Collect"))
                .toListOnClientThread();
        return nearest(candidates, o -> isUsableBank(o.getRuneLiteWorldLocation()));
    }

    public static Rs2TileObjectModel findDepositBox() {
        return findDepositBox(DEFAULT_SEARCH_RADIUS);
    }

    public static Rs2TileObjectModel findDepositBox(int maxSearchRadius) {
        List<Rs2TileObjectModel> candidates = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .ofType(TileObjectType.GAME)
                .withIds(BANK_IDS)
                .within(maxSearchRadius)
                .withActionContains("Deposit")
                .toListOnClientThread();
        return nearest(candidates, o -> true);
    }

    public static Rs2TileObjectModel findGrandExchangeBooth() {
        return findGrandExchangeBooth(DEFAULT_SEARCH_RADIUS);
    }

    public static Rs2TileObjectModel findGrandExchangeBooth(int maxSearchRadius) {
        List<Rs2TileObjectModel> candidates = Microbot.getRs2TileObjectCache().query()
                .fromWorldView()
                .ofType(TileObjectType.WALL)
                .withIds(GRAND_EXCHANGE_BOOTH_IDS)
                .within(maxSearchRadius)
                .toListOnClientThread();
        return nearest(candidates, o -> Rs2Tile.isTileReachable(o.getRuneLiteWorldLocation()));
    }

    public static String actionFor(Rs2TileObjectModel object, String action) {
        String own = object.findAction(action);
        if (own != null) {
            return own;
        }
        return object.hasAction("Use") ? "Use" : "";
    }

    public static boolean isUsableBank(WorldPoint location) {
        if (location == null) {
            return false;
        }
        if (COOKS_GUILD_BANKS.contains(location)) {
            return BankLocation.COOKS_GUILD.hasRequirements();
        }
        if (FARMING_GUILD_BANKS.contains(location)) {
            return Rs2Player.getSkillRequirement(Skill.FARMING, 85, true);
        }
        if (LUNAR_ISLE_DREAM_MENTOR_BANK.equals(location)) {
            return Rs2Player.getQuestState(Quest.DREAM_MENTOR) == QuestState.FINISHED;
        }
        if (LUNAR_ISLE_SEAL_BANKS.contains(location)) {
            return Rs2Inventory.hasItem(ItemID.LUNAR_SEAL_OF_PASSAGE) || Rs2Equipment.isWearing(ItemID.LUNAR_SEAL_OF_PASSAGE);
        }
        return true;
    }

    private static Rs2TileObjectModel nearest(List<Rs2TileObjectModel> candidates, Predicate<Rs2TileObjectModel> usable) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        WorldPoint playerLocation = new Rs2PlayerModel().getWorldLocation();
        if (playerLocation == null) {
            return null;
        }
        return candidates.stream()
                .filter(o -> o.getWorldLocation() != null)
                .sorted(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo(playerLocation)))
                .filter(usable)
                .findFirst()
                .orElse(null);
    }
}
