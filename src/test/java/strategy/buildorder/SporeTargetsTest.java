package strategy.buildorder;

import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.Filter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SporeTargetsTest {

    private static final int NO_AIR_UNITS = 0;

    private static final ToIntFunction<UnitType> NOTHING_OBSERVED = unitType -> 0;

    private static ToIntFunction<UnitType> observed(UnitType... unitTypes) {
        Map<UnitType, Integer> counts = new HashMap<>();
        for (UnitType unitType : unitTypes) {
            counts.merge(unitType, 1, Integer::sum);
        }
        return unitType -> counts.getOrDefault(unitType, 0);
    }

    @Test
    void anObservedStargateAsksEveryBaseForASpore() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Stargate)));
        assertTrue(SporeTargets.AIR_THREAT_SPORES > 0);
    }

    @Test
    void anObservedProtossFlyerAsksEveryBaseForASporeWithoutAScoutedStargate() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Corsair)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Scout)));
    }

    @Test
    void theCloakedAndDroppingProtossUnitsAskForADetector() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Dark_Templar)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Arbiter)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Templar_Archives)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Observer)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Shuttle)));
    }

    @Test
    void aFleetBeaconAsksForMoreThanOneSpore() {
        assertEquals(SporeTargets.HEAVY_AIR_SPORES, SporeTargets.protossSpores(observed(UnitType.Protoss_Fleet_Beacon)));
        assertTrue(SporeTargets.HEAVY_AIR_SPORES > SporeTargets.AIR_THREAT_SPORES);
    }

    @Test
    void theHeavyProtossTermOutranksTheOthersRatherThanAddingToThem() {
        assertEquals(SporeTargets.HEAVY_AIR_SPORES,
                SporeTargets.protossSpores(observed(UnitType.Protoss_Fleet_Beacon, UnitType.Protoss_Stargate,
                        UnitType.Protoss_Corsair, UnitType.Protoss_Observer)));
    }

    @Test
    void noProtossAirTechObservedAsksForNoSpore() {
        assertEquals(0, SporeTargets.protossSpores(NOTHING_OBSERVED));
        assertEquals(0, SporeTargets.protossSpores(observed(UnitType.Protoss_Zealot, UnitType.Protoss_Dragoon)));
    }

    @Test
    void anObservedStarportAsksEveryBaseForASpore() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.terranSpores(observed(UnitType.Terran_Starport)));
    }

    @Test
    void anObservedTerranFlyerAsksEveryBaseForASporeWithoutAScoutedStarport() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.terranSpores(observed(UnitType.Terran_Wraith)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.terranSpores(observed(UnitType.Terran_Valkyrie)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.terranSpores(observed(UnitType.Terran_Science_Vessel)));
    }

    @Test
    void theCloakedTerranUnitsAskForADetector() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.terranSpores(observed(UnitType.Terran_Ghost)));
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.terranSpores(observed(UnitType.Terran_Science_Facility)));
    }

    @Test
    void aBattlecruiserAsksForMoreThanOneSpore() {
        assertEquals(SporeTargets.HEAVY_AIR_SPORES, SporeTargets.terranSpores(observed(UnitType.Terran_Battlecruiser)));
    }

    @Test
    void theHeavyTerranTermOutranksTheOthersRatherThanAddingToThem() {
        assertEquals(SporeTargets.HEAVY_AIR_SPORES,
                SporeTargets.terranSpores(observed(UnitType.Terran_Battlecruiser, UnitType.Terran_Starport,
                        UnitType.Terran_Wraith, UnitType.Terran_Science_Vessel)));
    }

    @Test
    void noTerranAirTechObservedAsksForNoSpore() {
        assertEquals(0, SporeTargets.terranSpores(NOTHING_OBSERVED));
        assertEquals(0, SporeTargets.terranSpores(observed(UnitType.Terran_Marine, UnitType.Terran_Barracks)));
    }

    @Test
    void anObservedSpireAsksEveryBaseForASpore() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES,
                SporeTargets.zergSpores(observed(UnitType.Zerg_Spire), NO_AIR_UNITS));
        assertEquals(SporeTargets.AIR_THREAT_SPORES,
                SporeTargets.zergSpores(observed(UnitType.Zerg_Greater_Spire), NO_AIR_UNITS));
    }

    @Test
    void anObservedArmedFlyerAsksEveryBaseForASporeWithoutAScoutedSpire() {
        assertEquals(SporeTargets.AIR_THREAT_SPORES, SporeTargets.zergSpores(NOTHING_OBSERVED, 3));
    }

    @Test
    void noZergAirTechObservedAsksForNoSpore() {
        assertEquals(0, SporeTargets.zergSpores(NOTHING_OBSERVED, NO_AIR_UNITS));
        assertEquals(0, SporeTargets.zergSpores(observed(UnitType.Zerg_Hatchery), NO_AIR_UNITS));
    }

    /**
     * The flyer term is fed by the armed flyer filter, so the Overlords every Zerg opponent flies
     * do not raise the target on their own while the Mutalisks they precede do.
     */
    @Test
    void theFlyerTermCountsMutalisksButNotOverlords() {
        assertFalse(Filter.isAirCombatUnit(UnitType.Zerg_Overlord));
        assertTrue(Filter.isAirCombatUnit(UnitType.Zerg_Mutalisk));
    }

    @Test
    void dispatchesToTheMatchupTargetOfEachRace() {
        assertEquals(SporeTargets.protossSpores(observed(UnitType.Protoss_Stargate)),
                SporeTargets.sporeTarget(Race.Protoss, observed(UnitType.Protoss_Stargate), NO_AIR_UNITS));
        assertEquals(SporeTargets.terranSpores(observed(UnitType.Terran_Starport)),
                SporeTargets.sporeTarget(Race.Terran, observed(UnitType.Terran_Starport), NO_AIR_UNITS));
        assertEquals(SporeTargets.zergSpores(observed(UnitType.Zerg_Spire), NO_AIR_UNITS),
                SporeTargets.sporeTarget(Race.Zerg, observed(UnitType.Zerg_Spire), NO_AIR_UNITS));
    }

    @Test
    void readsOnlyTheRaceItWasAskedAbout() {
        assertEquals(0, SporeTargets.sporeTarget(Race.Terran, observed(UnitType.Protoss_Stargate), NO_AIR_UNITS));
        assertEquals(0, SporeTargets.sporeTarget(Race.Protoss, observed(UnitType.Terran_Starport), NO_AIR_UNITS));
    }

    @Test
    void asksForNothingWhileTheRaceIsUnknown() {
        assertEquals(0, SporeTargets.sporeTarget(Race.Unknown,
                observed(UnitType.Protoss_Stargate, UnitType.Terran_Starport, UnitType.Zerg_Spire), 3));
    }

    @Test
    void asksForNothingAgainstAnOpponentWithNoRaceAtAll() {
        assertEquals(0, SporeTargets.sporeTarget(Race.None, NOTHING_OBSERVED, 3));
    }
}
