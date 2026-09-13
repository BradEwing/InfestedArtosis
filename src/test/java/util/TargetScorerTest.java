package util;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetScorerTest {

    private static final boolean MUTALISK = UnitType.Zerg_Mutalisk.isFlyer();
    private static final boolean ZERGLING = UnitType.Zerg_Zergling.isFlyer();

    private static TargetScorer.Candidate at(UnitType type, int distance) {
        return new TargetScorer.Candidate(type, distance, 1.0, false, false);
    }

    private static TargetScorer.Candidate currentAt(UnitType type, int distance) {
        return new TargetScorer.Candidate(type, distance, 1.0, true, false);
    }

    private static UnitType chosen(boolean attackerIsFlying, TargetScorer.Candidate... candidates) {
        List<TargetScorer.Candidate> list = Arrays.asList(candidates);
        return list.get(TargetScorer.selectIndex(attackerIsFlying, list)).type();
    }

    @Test
    void theAttackersAreClassifiedByTheirOwnUnitTypes() {
        assertTrue(MUTALISK);
        assertFalse(ZERGLING);
    }

    @Test
    void aMutaliskPrefersAnOverlordOverANearerSunkenColony() {
        assertEquals(UnitType.Zerg_Overlord,
                chosen(MUTALISK, at(UnitType.Zerg_Sunken_Colony, 50), at(UnitType.Zerg_Overlord, 250)));
    }

    @Test
    void aMutaliskLeavesTheSunkenColonyItIsAlreadyShootingForAFartherOverlord() {
        assertEquals(UnitType.Zerg_Overlord,
                chosen(MUTALISK, currentAt(UnitType.Zerg_Sunken_Colony, 20), at(UnitType.Zerg_Overlord, 256)));
    }

    @Test
    void aMutaliskRanksDroneThenOverlordThenSunkenColony() {
        assertEquals(TargetScorer.Priority.ELEVATED,
                TargetScorer.assignPriority(UnitType.Zerg_Drone, MUTALISK, false));
        assertEquals(TargetScorer.Priority.NORMAL,
                TargetScorer.assignPriority(UnitType.Zerg_Overlord, MUTALISK, false));
        assertEquals(TargetScorer.Priority.LOW,
                TargetScorer.assignPriority(UnitType.Zerg_Sunken_Colony, MUTALISK, false));
        assertEquals(UnitType.Zerg_Drone, chosen(MUTALISK,
                at(UnitType.Zerg_Sunken_Colony, 30), at(UnitType.Zerg_Overlord, 60), at(UnitType.Zerg_Drone, 250)));
    }

    @Test
    void aMutaliskRanksASunkenColonyLevelWithAHatchery() {
        assertEquals(TargetScorer.assignPriority(UnitType.Zerg_Hatchery, MUTALISK, false),
                TargetScorer.assignPriority(UnitType.Zerg_Sunken_Colony, MUTALISK, false));
    }

    @Test
    void aMutaliskStillPrefersAnythingThatCanShootIt() {
        List<UnitType> shooters = Arrays.asList(
                UnitType.Zerg_Spore_Colony,
                UnitType.Terran_Missile_Turret,
                UnitType.Protoss_Photon_Cannon,
                UnitType.Terran_Bunker,
                UnitType.Zerg_Hydralisk,
                UnitType.Zerg_Mutalisk,
                UnitType.Terran_Marine,
                UnitType.Terran_Goliath,
                UnitType.Protoss_Dragoon,
                UnitType.Protoss_Corsair,
                UnitType.Protoss_Carrier);
        List<UnitType> harmless = Arrays.asList(
                UnitType.Zerg_Drone,
                UnitType.Zerg_Overlord,
                UnitType.Zerg_Sunken_Colony,
                UnitType.Zerg_Hatchery,
                UnitType.Zerg_Zergling);

        for (UnitType shooter : shooters) {
            assertEquals(TargetScorer.Priority.CRITICAL, TargetScorer.assignPriority(shooter, MUTALISK, false),
                    shooter.toString());
            for (UnitType other : harmless) {
                assertEquals(shooter, chosen(MUTALISK, at(other, 10), at(shooter, 250)),
                        shooter + " vs " + other);
            }
        }
    }

    @Test
    void aMutaliskStillPrefersAnAttackingWorker() {
        TargetScorer.Candidate meanDrone = new TargetScorer.Candidate(UnitType.Zerg_Drone, 250, 1.0, false, true);
        List<TargetScorer.Candidate> list = Arrays.asList(at(UnitType.Zerg_Drone, 10), meanDrone);

        assertEquals(1, TargetScorer.selectIndex(MUTALISK, list));
    }

    @Test
    void aZerglingStillPrefersAGroundThreatOverAWorkerAndAWorkerOverABuilding() {
        assertEquals(UnitType.Zerg_Sunken_Colony,
                chosen(ZERGLING, at(UnitType.Zerg_Drone, 10), at(UnitType.Zerg_Sunken_Colony, 250)));
        assertEquals(UnitType.Protoss_Photon_Cannon,
                chosen(ZERGLING, at(UnitType.Protoss_Probe, 10), at(UnitType.Protoss_Photon_Cannon, 250)));
        assertEquals(UnitType.Terran_Bunker,
                chosen(ZERGLING, at(UnitType.Terran_SCV, 10), at(UnitType.Terran_Bunker, 250)));
        assertEquals(UnitType.Zerg_Zergling,
                chosen(ZERGLING, at(UnitType.Zerg_Drone, 10), at(UnitType.Zerg_Zergling, 250)));
        assertEquals(UnitType.Zerg_Drone,
                chosen(ZERGLING, at(UnitType.Zerg_Hatchery, 10), at(UnitType.Zerg_Drone, 250)));
        assertEquals(UnitType.Zerg_Sunken_Colony,
                chosen(ZERGLING, at(UnitType.Zerg_Spore_Colony, 10), at(UnitType.Zerg_Sunken_Colony, 250)));
    }

    @Test
    void aZerglingKeepsTheTiersItHadForGroundThreatsWorkersAndBuildings() {
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Zerg_Sunken_Colony, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Protoss_Photon_Cannon, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Terran_Bunker, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Terran_Marine, ZERGLING, false));
        assertEquals(TargetScorer.Priority.CRITICAL,
                TargetScorer.assignPriority(UnitType.Zerg_Drone, ZERGLING, true));
        assertEquals(TargetScorer.Priority.ELEVATED,
                TargetScorer.assignPriority(UnitType.Zerg_Drone, ZERGLING, false));
        assertEquals(TargetScorer.Priority.LOW,
                TargetScorer.assignPriority(UnitType.Zerg_Hatchery, ZERGLING, false));
        assertEquals(TargetScorer.Priority.LOW,
                TargetScorer.assignPriority(UnitType.Terran_Supply_Depot, ZERGLING, false));
    }

    @Test
    void withinATierTheNearerCandidateWins() {
        assertEquals(UnitType.Zerg_Hatchery,
                chosen(ZERGLING, at(UnitType.Zerg_Lair, 200), at(UnitType.Zerg_Hatchery, 100)));
    }

    @Test
    void withinATierTheCurrentTargetHoldsUntilAnotherIsMoreThanTwentyPercentCloser() {
        assertEquals(UnitType.Zerg_Lair,
                chosen(ZERGLING, currentAt(UnitType.Zerg_Lair, 115), at(UnitType.Zerg_Hatchery, 100)));
        assertEquals(UnitType.Zerg_Hatchery,
                chosen(ZERGLING, currentAt(UnitType.Zerg_Lair, 130), at(UnitType.Zerg_Hatchery, 100)));
    }

    @Test
    void aZeroDistanceCandidateScoresAsOnePixelAway() {
        assertEquals(at(UnitType.Zerg_Drone, 1).score(), at(UnitType.Zerg_Drone, 0).score());
    }
}
