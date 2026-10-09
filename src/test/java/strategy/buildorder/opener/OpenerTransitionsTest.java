package strategy.buildorder.opener;

import bwapi.Race;
import config.Config;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyTracker;
import info.tracking.terran.BunkerMain;
import info.tracking.terran.BunkerNatural;
import info.tracking.terran.TerranMech;
import org.junit.jupiter.api.Test;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void terranTransitionsOfferSpeedlingTWithoutAWall() {
        Set<String> names = transitionNames(Race.Terran, false);
        assertTrue(names.contains("SpeedlingT"), "Terran without a wall must offer SpeedlingT: " + names);
    }

    @Test
    void aTerranWallBarsSpeedlingTAndKeepsTheOtherTransitions() {
        Set<String> names = transitionNames(Race.Terran, true);
        assertFalse(names.contains("SpeedlingT"), "a Terran wall must bar SpeedlingT: " + names);
        assertEquals(new HashSet<>(Arrays.asList("CrazyZerg", "3HatchLurker", "2HatchMuta")), names);
    }

    @Test
    void eachRaceIsOfferedItsOwnSpeedlingVariantOnly() {
        assertEquals(1, speedlingNames(Race.Protoss).size());
        assertTrue(speedlingNames(Race.Protoss).contains("SpeedlingP"));
        assertEquals(1, speedlingNames(Race.Zerg).size());
        assertTrue(speedlingNames(Race.Zerg).contains("SpeedlingZ"));
        assertEquals(1, speedlingNames(Race.Terran).size());
        assertTrue(speedlingNames(Race.Terran).contains("SpeedlingT"));
    }

    @Test
    void aRandomOpponentWhoseRaceIsUnknownIsOfferedSpeedlingR() {
        assertEquals(new HashSet<>(Arrays.asList("SpeedlingR")), names(OpenerTransitions.forRace(Race.Unknown)));
    }

    @Test
    void terranWithoutSpeedlingOffersNoSpeedlingVariant() {
        Set<String> names = names(OpenerTransitions.forRace(Race.Terran, true));
        assertTrue(speedlingOnly(names).isEmpty());
        assertFalse(names.isEmpty());
    }

    @Test
    void aDetectedBunkerMainWallOrPersistedWallBarsSpeedlingT() {
        StrategyTracker cold = terranTracker();
        StrategyTracker bunkerMain = terranTracker();
        bunkerMain.getDetectedStrategies().add(new BunkerMain());
        StrategyTracker bunkerNatural = terranTracker();
        bunkerNatural.getDetectedStrategies().add(new BunkerNatural());
        StrategyTracker persistedWall = terranTracker();
        persistedWall.setTerranWallPersists(true);

        assertFalse(OpenerTransitions.barsSpeedlingT(null));
        assertFalse(OpenerTransitions.barsSpeedlingT(cold));
        assertTrue(OpenerTransitions.barsSpeedlingT(bunkerMain));
        assertFalse(OpenerTransitions.barsSpeedlingT(bunkerNatural));
        StrategyTracker both = terranTracker();
        both.getDetectedStrategies().add(new BunkerMain());
        both.getDetectedStrategies().add(new BunkerNatural());
        assertFalse(OpenerTransitions.barsSpeedlingT(both));
        assertTrue(OpenerTransitions.barsSpeedlingT(persistedWall));
    }

    private static Set<String> speedlingNames(Race race) {
        return speedlingOnly(names(OpenerTransitions.forRace(race)));
    }

    private static Set<String> speedlingOnly(Set<String> names) {
        return names.stream().filter(n -> n.startsWith("Speedling")).collect(Collectors.toSet());
    }

    @Test
    void aColdOrBioTerranIsNotOfferedTheHydraBuild() {
        assertFalse(transitionNames(Race.Terran).contains("2HatchHydraZvT"));
        assertFalse(transitionNames(Race.Terran, false).contains("2HatchHydraZvT"));
        assertFalse(transitionNames(Race.Terran, true).contains("2HatchHydraZvT"));
    }

    @Test
    void aTerranWithAPersistedMechPriorIsOfferedTheHydraBuild() {
        assertTrue(names(OpenerTransitions.forRace(Race.Terran, false, true)).contains("2HatchHydraZvT"));
        assertTrue(names(OpenerTransitions.forRace(Race.Terran, true, true)).contains("2HatchHydraZvT"));
    }

    @Test
    void theHydraBuildIsNeverOfferedAgainstOtherRaces() {
        assertFalse(names(OpenerTransitions.forRace(Race.Protoss, false, true)).contains("2HatchHydraZvT"));
        assertFalse(names(OpenerTransitions.forRace(Race.Zerg, false, true)).contains("2HatchHydraZvT"));
    }

    @Test
    void theOfferGateReadsThePersistedPriorOrTheOverrideOnly() {
        StrategyTracker prior = terranTracker();
        prior.setTerranMechPersists(true);
        StrategyTracker cold = terranTracker();
        StrategyTracker detected = terranTracker();
        detected.getDetectedStrategies().add(new TerranMech());
        Config forced = new Config();
        forced.strategyOverride = "2HatchHydraZvT";
        Config none = new Config();
        none.strategyOverride = null;

        assertTrue(OpenerTransitions.offersHydraBuild(prior, none));
        assertFalse(OpenerTransitions.offersHydraBuild(cold, none));
        assertFalse(OpenerTransitions.offersHydraBuild(detected, none));
        assertTrue(OpenerTransitions.offersHydraBuild(cold, forced));
        assertTrue(OpenerTransitions.offersHydraBuild(null, forced));
        assertFalse(OpenerTransitions.offersHydraBuild(null, null));
        assertFalse(OpenerTransitions.offersHydraBuild(null, none));
    }

    private static StrategyTracker terranTracker() {
        return new StrategyTracker(null, Race.Terran, new ObservedUnitTracker(), null, null, null, null);
    }

    @Test
    void theStrategyOverrideForcesTheHydraBuildWhateverThePrior() {
        Config forced = new Config();
        forced.strategyOverride = "2HatchHydraZvT";
        Config other = new Config();
        other.strategyOverride = "2HatchMuta";
        Config none = new Config();
        none.strategyOverride = null;

        assertTrue(OpenerTransitions.isHydraBuildForced(forced));
        assertFalse(OpenerTransitions.isHydraBuildForced(other));
        assertFalse(OpenerTransitions.isHydraBuildForced(none));
        assertFalse(OpenerTransitions.isHydraBuildForced(null));
    }

    @Test
    void theHydraBuildIsSeededAgainstTerranOnly() {
        assertTrue(new BuildOrderFactory(4, Race.Terran).getPlayableNonOpenerNames().contains("2HatchHydraZvT"));
        assertFalse(new BuildOrderFactory(4, Race.Protoss).getPlayableNonOpenerNames().contains("2HatchHydraZvT"));
        assertFalse(new BuildOrderFactory(4, Race.Zerg).getPlayableNonOpenerNames().contains("2HatchHydraZvT"));
    }

    @Test
    void aWallReadingLeavesTheOtherRacesAlone() {
        for (Race race : new Race[] {Race.Protoss, Race.Zerg}) {
            assertEquals(transitionNames(race, false), transitionNames(race, true), race + " transitions changed");
        }
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

    @Test
    void noOpenerHandsOverStraightToLurkerDefilerUltra() {
        for (Race race : Race.values()) {
            Set<String> names = transitionNames(race);
            assertFalse(names.contains("LurkerDefilerUltra"), race + " offers LurkerDefilerUltra: " + names);
        }
    }

    private static Set<String> transitionNames(Race race) {
        return names(OpenerTransitions.forRace(race));
    }

    private static Set<String> transitionNames(Race race, boolean terranWall) {
        return names(OpenerTransitions.forRace(race, terranWall));
    }

    private static Set<String> names(Set<BuildOrder> buildOrders) {
        return buildOrders.stream()
                .map(BuildOrder::getName)
                .collect(Collectors.toSet());
    }
}
