package unit.squad.horizon;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SectorRatioTest {

    private static final Position HERE = new Position(1000, 1000);
    private static final Map<UnitSizeType, Double> ALL_SMALL = Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final double TERRAN_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Terran);

    private static HorizonCombatSimulator.FriendlyForce lings(int count) {
        HorizonCombatSimulator.FriendlyForce force = new HorizonCombatSimulator.FriendlyForce();
        for (int i = 0; i < count; i++) {
            force.add(UnitType.Zerg_Zergling, HERE, 1.0, false);
        }
        return force;
    }

    private static HorizonCombatSimulator.EnemySample enemies(int marines, int medics) {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        for (int i = 0; i < marines; i++) {
            addTo(sample, UnitType.Terran_Marine);
        }
        for (int i = 0; i < medics; i++) {
            addTo(sample, UnitType.Terran_Medic);
        }
        return sample;
    }

    private static void addTo(HorizonCombatSimulator.EnemySample sample, UnitType type) {
        sample.add(type, HorizonCombatSimulator.weightedGroundStrength(type, ALL_SMALL),
                HorizonCombatSimulator.weightedAntiAirStrength(type, ALL_SMALL));
    }

    @Test
    void aFullSquadOnAFewMarinesInsideTheArcReadsFavourable() {
        double ratio = HorizonCombatSimulator.sectorRatio(lings(24), enemies(4, 1));

        assertTrue(ratio >= TERRAN_THRESHOLD, "24 lings on 4 marines and a medic read " + ratio);
    }

    @Test
    void aSmallSquadOnABioBallReadsUnfavourable() {
        double ratio = HorizonCombatSimulator.sectorRatio(lings(6), enemies(13, 3));

        assertTrue(ratio < TERRAN_THRESHOLD, "6 lings on 13 marines and 3 medics read " + ratio);
    }

    @Test
    void theRatioRisesWithOurSquadAndFallsWithTheirs() {
        double base = HorizonCombatSimulator.sectorRatio(lings(12), enemies(4, 0));

        assertTrue(HorizonCombatSimulator.sectorRatio(lings(16), enemies(4, 0)) > base);
        assertTrue(HorizonCombatSimulator.sectorRatio(lings(12), enemies(6, 0)) < base);
    }

    @Test
    void medicsInTheSectorMakeTheEnemyStronger() {
        assertTrue(HorizonCombatSimulator.sectorRatio(lings(12), enemies(4, 2))
                < HorizonCombatSimulator.sectorRatio(lings(12), enemies(4, 0)));
    }

    @Test
    void enemiesWithNoGroundStrengthReadNothing() {
        assertEquals(0, HorizonCombatSimulator.sectorRatio(lings(12), enemies(0, 3)));
    }
}
