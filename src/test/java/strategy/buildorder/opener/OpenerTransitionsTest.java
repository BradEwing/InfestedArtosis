package strategy.buildorder.opener;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenerTransitionsTest {

    private static final Set<String> RETIRED = new HashSet<>(Arrays.asList("3HatchHydra", "2HatchMuta"));

    @Test
    void noOpenerTransitionOffersARetiredBuildOrder() {
        for (Race race : Race.values()) {
            Set<String> names = OpenerTransitions.forRace(race)
                    .stream()
                    .map(BuildOrder::getName)
                    .collect(Collectors.toSet());
            assertTrue(Collections.disjoint(RETIRED, names), race + " offers a retired build order: " + names);
        }
    }

    @Test
    void everyResolvedRaceKeepsATransition() {
        for (Race race : new Race[] {Race.Protoss, Race.Terran, Race.Zerg}) {
            assertFalse(OpenerTransitions.forRace(race).isEmpty(), race + " has no transition candidates");
        }
    }

    @Test
    void retiredBuildOrdersStillResolveByName() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        for (String retired : RETIRED) {
            assertNotNull(factory.getByName(retired), retired + " must still resolve for old learning rows");
        }
    }
}
