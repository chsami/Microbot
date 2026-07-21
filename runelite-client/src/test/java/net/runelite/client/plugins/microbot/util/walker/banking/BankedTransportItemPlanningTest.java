package net.runelite.client.plugins.microbot.util.walker.banking;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Item-gated plain transports must take part in bank planning.
 *
 * <p>Only currency-bearing {@link TransportType#TRANSPORT} entries used to qualify, so a machete for
 * a jungle bush, a pickaxe for a Motherlode rockfall or a Shantay pass fell through
 * {@code hasRequiredTransportItems} to its catch-all {@code return true}, was never counted as
 * missing, and was never withdrawn.
 *
 * <p>That contradicted the pathfinder, which <em>does</em> count bank contents when
 * {@code useBankItems} is set — so a route was planned through the obstacle on the strength of a
 * banked item the planner then refused to fetch, stranding the walk at the obstacle.
 */
public class BankedTransportItemPlanningTest {

    private static List<Transport> all;

    @BeforeClass
    public static void load() {
        HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
        all = transports.values().stream().flatMap(Set::stream).collect(Collectors.toList());
    }

    /** {@code menuOption;menuTarget} for readable assertion messages. */
    private static String describe(Transport t) {
        String action = t.getAction() == null ? "" : t.getAction();
        String name = t.getName() == null ? "" : t.getName();
        return action + ";" + name;
    }

    private static List<Transport> matching(String menuFragment) {
        return all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> {
                    String target = describe(t);
                    return target.toLowerCase().contains(menuFragment.toLowerCase());
                })
                .collect(Collectors.toList());
    }

    @Test
    public void itemGatedPlainTransportsNowQualifyForPlanning() {
        List<Transport> itemGated = all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> t.getItemIdRequirements() != null && !t.getItemIdRequirements().isEmpty())
                .filter(t -> t.getCurrencyAmount() <= 0)
                .collect(Collectors.toList());

        assertFalse("the data should contain item-gated plain transports (rockfalls, jungle bushes, "
                + "Shantay passes)", itemGated.isEmpty());

        for (Transport t : itemGated) {
            assertTrue("an item-gated plain transport must be eligible for bank planning, otherwise "
                            + "the pathfinder routes through it on a banked item nobody withdraws: " + describe(t),
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }

    /** A transport with no item and no currency requirement must stay out of planning. */
    @Test
    public void unrestrictedTransportsAreStillIgnored() {
        List<Transport> unrestricted = all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> t.getItemIdRequirements() == null || t.getItemIdRequirements().isEmpty())
                .filter(t -> t.getCurrencyAmount() <= 0)
                .collect(Collectors.toList());

        assertFalse("precondition: most doors and stairs require nothing", unrestricted.isEmpty());
        for (Transport t : unrestricted) {
            assertFalse("a transport requiring nothing must never trigger a bank trip: " + describe(t),
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }

    /** Currency-bearing transports kept their existing eligibility. */
    @Test
    public void currencyTransportsRemainEligible() {
        List<Transport> currency = all.stream()
                .filter(t -> t.getType() == TransportType.TRANSPORT)
                .filter(t -> t.getCurrencyAmount() > 0)
                .collect(Collectors.toList());

        for (Transport t : currency) {
            assertTrue("currency transports must keep qualifying: " + describe(t),
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }

    @Test
    public void theMotherlodeRockfallsQualify() {
        List<Transport> rockfalls = matching("Rockfall");
        assertFalse("Motherlode rockfalls should be catalogued", rockfalls.isEmpty());
        for (Transport t : rockfalls) {
            assertTrue("a rockfall needs a pickaxe, so it must be plannable",
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }

    @Test
    public void nullAndNonPlainTransportsAreRejectedSafely() {
        assertFalse("null must not qualify", Rs2WalkerBankingPlanner.planningCoversPlainTransport(null));

        List<Transport> teleports = all.stream()
                .filter(t -> t.getType() == TransportType.TELEPORTATION_ITEM)
                .collect(Collectors.toList());
        for (Transport t : teleports) {
            assertFalse("non-plain types are handled by their own branch and must not match here",
                    Rs2WalkerBankingPlanner.planningCoversPlainTransport(t));
        }
    }
}
