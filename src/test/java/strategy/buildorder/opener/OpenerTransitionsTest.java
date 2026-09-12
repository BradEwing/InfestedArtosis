package strategy.buildorder.opener;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenerTransitionsTest {

    private static final Set<String> RETIRED = new HashSet<>(Collections.singletonList("2HatchMuta"));
    private static final Set<String> REENABLED = new HashSet<>(Collections.singletonList("3HatchHydra"));

    @Test
    void noOpenerTransitionOffersARetiredBuildOrder() {
        for (Race race : Race.values()) {
            for (BuildOrder buildOrder : OpenerTransitions.forRace(race)) {
                assertFalse(buildOrder.isRetired(), race + " offers a retired build order: " + buildOrder.getName());
            }
        }
    }

    @Test
    void everyResolvedRaceKeepsATransition() {
        for (Race race : new Race[] {Race.Protoss, Race.Terran, Race.Zerg}) {
            assertFalse(OpenerTransitions.forRace(race).isEmpty(), race + " has no transition candidates");
        }
    }

    @Test
    void protossTransitionsOfferThreeHatchHydra() {
        Set<String> names = transitionNames(Race.Protoss);
        assertTrue(names.contains("3HatchHydra"), "Protoss must offer 3HatchHydra: " + names);
    }

    @Test
    void retiredBuildOrdersStillResolveByName() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        for (String retired : RETIRED) {
            BuildOrder buildOrder = factory.getByName(retired);
            assertNotNull(buildOrder, retired + " must still resolve for old learning rows");
            assertTrue(buildOrder.isRetired(), retired + " must be flagged retired");
        }
    }

    @Test
    void reEnabledBuildOrdersResolveByNameAndAreNotRetired() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        for (String reEnabled : REENABLED) {
            BuildOrder buildOrder = factory.getByName(reEnabled);
            assertNotNull(buildOrder, reEnabled + " must resolve by name");
            assertFalse(buildOrder.isRetired(), reEnabled + " must not be flagged retired");
        }
    }

    @Test
    void retiredBuildOrdersAreNotSeededAsPlayable() {
        for (Race race : Race.values()) {
            BuildOrderFactory factory = new BuildOrderFactory(4, race);
            for (String seeded : factory.getPlayableNonOpenerNames()) {
                assertFalse(factory.getByName(seeded).isRetired(), race + " seeds a retired build order: " + seeded);
            }
        }
    }

    @Test
    void threeHatchHydraIsSeededAgainstProtoss() {
        Set<String> seeded = new BuildOrderFactory(4, Race.Protoss).getPlayableNonOpenerNames();
        assertTrue(seeded.contains("3HatchHydra"), "3HatchHydra must be seeded against Protoss: " + seeded);
    }

    private static Set<String> transitionNames(Race race) {
        return OpenerTransitions.forRace(race)
                .stream()
                .map(BuildOrder::getName)
                .collect(Collectors.toSet());
    }
}
