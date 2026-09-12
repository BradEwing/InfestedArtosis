package strategy.buildorder.opener;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenerTransitionsTest {

    private static final Set<String> REENABLED = new HashSet<>(Arrays.asList("3HatchHydra", "2HatchMuta"));

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
    void terranTransitionsOfferTwoHatchMuta() {
        Set<String> names = transitionNames(Race.Terran);
        assertTrue(names.contains("2HatchMuta"), "Terran must offer 2HatchMuta: " + names);
    }

    @Test
    void reEnabledBuildOrdersResolveByNameAndAreNotRetired() {
        BuildOrderFactory factory = new BuildOrderFactory(4, Race.Protoss);
        for (String reEnabled : REENABLED) {
            BuildOrder buildOrder = factory.getByName(reEnabled);
            assertNotNull(buildOrder, reEnabled + " must resolve for old learning rows");
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
    void reEnabledBuildOrdersAreSeededForTheirMatchup() {
        Set<String> protoss = new BuildOrderFactory(4, Race.Protoss).getPlayableNonOpenerNames();
        assertTrue(protoss.contains("3HatchHydra"), "3HatchHydra must be seeded against Protoss: " + protoss);

        Set<String> terran = new BuildOrderFactory(4, Race.Terran).getPlayableNonOpenerNames();
        assertTrue(terran.contains("2HatchMuta"), "2HatchMuta must be seeded against Terran: " + terran);
    }

    private static Set<String> transitionNames(Race race) {
        return OpenerTransitions.forRace(race)
                .stream()
                .map(BuildOrder::getName)
                .collect(Collectors.toSet());
    }
}
