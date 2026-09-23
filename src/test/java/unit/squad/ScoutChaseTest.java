package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoutChaseTest {

    private static final int PROBE = 24;

    private static final int OTHER_SCOUT = 25;

    private static final double DETECTION_RADIUS = 512.0;

    private static final int ZERGLING_CAP = ScoutChase.chaserCap(UnitType.Zerg_Zergling.topSpeed(),
            UnitType.Protoss_Probe.topSpeed());

    /**
     * A candidate as the target filter sees it: an id, its type, and the attacker's distance to it.
     */
    private static final class Enemy {
        private final int id;
        private final UnitType type;
        private final double distance;

        Enemy(int id, UnitType type, double distance) {
            this.id = id;
            this.type = type;
            this.distance = distance;
        }

        boolean isScout() {
            return ScoutChase.isScout(type, false, false, false);
        }
    }

    private static ScoutChase.Claim claim(int attackerId, double distance) {
        return new ScoutChase.Claim(PROBE, attackerId, distance, ZERGLING_CAP);
    }

    private static List<Enemy> candidatesFor(ScoutChase ledger, int attackerId, List<Enemy> visible) {
        return ScoutChase.withoutCappedScouts(visible,
                enemy -> enemy.isScout() && !ledger.admits(enemy.id, attackerId, ZERGLING_CAP));
    }

    @Test
    void aProbeAwayFromEveryEnemyBaseIsAScout() {
        assertTrue(ScoutChase.isScout(UnitType.Protoss_Probe, false, false, false));
    }

    @Test
    void anScvAndADroneAwayFromEveryEnemyBaseAreScouts() {
        assertTrue(ScoutChase.isScout(UnitType.Terran_SCV, false, false, false));
        assertTrue(ScoutChase.isScout(UnitType.Zerg_Drone, false, false, false));
    }

    @Test
    void anAttackingProbeIsNotAScout() {
        assertFalse(ScoutChase.isScout(UnitType.Protoss_Probe, true, false, false));
    }

    @Test
    void aConstructingScvIsNotAScout() {
        assertFalse(ScoutChase.isScout(UnitType.Terran_SCV, false, true, false));
    }

    @Test
    void aProbeNearAnEnemyBaseIsNotAScout() {
        assertFalse(ScoutChase.isScout(UnitType.Protoss_Probe, false, false, true));
    }

    @Test
    void anOverlordAndAnObserverAreScouts() {
        assertTrue(ScoutChase.isScout(UnitType.Zerg_Overlord, false, false, false));
        assertTrue(ScoutChase.isScout(UnitType.Protoss_Observer, false, false, false));
    }

    @Test
    void anOverlordNearAnEnemyBaseIsNotAScout() {
        assertFalse(ScoutChase.isScout(UnitType.Zerg_Overlord, false, false, true));
    }

    @Test
    void aZealotIsNotAScout() {
        assertFalse(ScoutChase.isScout(UnitType.Protoss_Zealot, false, false, false));
    }

    @Test
    void aZerglingMayBringTwoOntoAProbe() {
        assertEquals(2, ScoutChase.chaserCap(UnitType.Zerg_Zergling.topSpeed(), UnitType.Protoss_Probe.topSpeed()));
    }

    @Test
    void aHydraliskBringsOnlyOneOntoAProbe() {
        assertEquals(1, ScoutChase.chaserCap(UnitType.Zerg_Hydralisk.topSpeed(), UnitType.Protoss_Probe.topSpeed()));
    }

    @Test
    void theLedgerKeepsTheClosestChasers() {
        ScoutChase ledger = new ScoutChase();

        ledger.beginFrame(Arrays.asList(claim(1, 256), claim(2, 240), claim(3, 211), claim(4, 230), claim(5, 250),
                claim(6, 217)));

        assertEquals(2, ledger.holderCount(PROBE));
        assertTrue(ledger.admits(PROBE, 3, ZERGLING_CAP));
        assertTrue(ledger.admits(PROBE, 6, ZERGLING_CAP));
        assertFalse(ledger.admits(PROBE, 4, ZERGLING_CAP));
        assertFalse(ledger.admits(PROBE, 1, ZERGLING_CAP));
    }

    @Test
    void aSlowChaserFirstInLineLeavesRoomForOneFastChaser() {
        ScoutChase ledger = new ScoutChase();

        ledger.beginFrame(Arrays.asList(new ScoutChase.Claim(PROBE, 1, 100, 1), claim(2, 150), claim(3, 200)));

        assertEquals(2, ledger.holderCount(PROBE));
        assertFalse(ledger.admits(PROBE, 3, ZERGLING_CAP));
    }

    @Test
    void theCapHoldsAcrossSquads() {
        ScoutChase ledger = new ScoutChase();
        ledger.beginFrame(Collections.emptyList());
        int firstSquadLing = 1;
        int secondFirstSquadLing = 2;
        int secondSquadLing = 3;

        ledger.claim(PROBE, firstSquadLing);
        ledger.claim(PROBE, secondFirstSquadLing);

        assertFalse(ledger.admits(PROBE, secondSquadLing, ZERGLING_CAP));
        assertTrue(ledger.admits(PROBE, firstSquadLing, ZERGLING_CAP));
    }

    @Test
    void claimsFromTwoSquadsShareOneRowAtTheFrameStart() {
        ScoutChase ledger = new ScoutChase();

        ledger.beginFrame(Arrays.asList(claim(1, 211), claim(2, 256), claim(7, 220), claim(8, 240)));

        assertTrue(ledger.admits(PROBE, 1, ZERGLING_CAP));
        assertTrue(ledger.admits(PROBE, 7, ZERGLING_CAP));
        assertFalse(ledger.admits(PROBE, 2, ZERGLING_CAP));
        assertFalse(ledger.admits(PROBE, 8, ZERGLING_CAP));
    }

    @Test
    void claimingAnotherScoutReleasesTheFirst() {
        ScoutChase ledger = new ScoutChase();
        ledger.claim(PROBE, 1);

        ledger.claim(OTHER_SCOUT, 1);

        assertEquals(0, ledger.holderCount(PROBE));
        assertEquals(1, ledger.holderCount(OTHER_SCOUT));
    }

    @Test
    void releasingAChaserFreesItsSlot() {
        ScoutChase ledger = new ScoutChase();
        ledger.claim(PROBE, 1);
        ledger.claim(PROBE, 2);

        ledger.release(1);

        assertTrue(ledger.admits(PROBE, 3, ZERGLING_CAP));
    }

    @Test
    void aDeadScoutClearsItsRow() {
        ScoutChase ledger = new ScoutChase();
        ledger.claim(PROBE, 1);
        ledger.claim(PROBE, 2);

        ledger.releaseScout(PROBE);

        assertEquals(0, ledger.holderCount(PROBE));
        assertTrue(ledger.admits(PROBE, 3, ZERGLING_CAP));
    }

    @Test
    void aCappedProbeNoLongerHidesAZealotBeyondTheTargetingRadius() {
        ScoutChase ledger = new ScoutChase();
        ledger.beginFrame(Arrays.asList(claim(1, 211), claim(2, 215)));
        Enemy probe = new Enemy(PROBE, UnitType.Protoss_Probe, 217);
        Enemy zealot = new Enemy(179, UnitType.Protoss_Zealot, 1117);
        List<Enemy> visible = Arrays.asList(probe, zealot);

        List<Enemy> candidates = SquadManager.filterByProximity(candidatesFor(ledger, 3, visible), e -> e.distance);

        assertEquals(Collections.singletonList(zealot), candidates);
    }

    @Test
    void withoutTheCapTheProbeHidesTheZealot() {
        Enemy probe = new Enemy(PROBE, UnitType.Protoss_Probe, 217);
        Enemy zealot = new Enemy(179, UnitType.Protoss_Zealot, 1117);

        List<Enemy> candidates = SquadManager.filterByProximity(Arrays.asList(probe, zealot), e -> e.distance);

        assertEquals(Collections.singletonList(probe), candidates);
    }

    @Test
    void aHolderKeepsTheProbeAmongItsCandidates() {
        ScoutChase ledger = new ScoutChase();
        ledger.beginFrame(Arrays.asList(claim(1, 211), claim(2, 215)));
        Enemy probe = new Enemy(PROBE, UnitType.Protoss_Probe, 211);
        Enemy zealot = new Enemy(179, UnitType.Protoss_Zealot, 1117);

        List<Enemy> candidates = SquadManager.filterByProximity(candidatesFor(ledger, 1, Arrays.asList(probe, zealot)),
                e -> e.distance);

        assertEquals(Collections.singletonList(probe), candidates);
    }

    @Test
    void aCappedUnitWithOnlyADistantThreatLeftDefends() {
        assertTrue(ScoutChase.shouldDefend(1, 1117, DETECTION_RADIUS));
    }

    @Test
    void aCappedUnitWithNothingLeftDefends() {
        assertTrue(ScoutChase.shouldDefend(0, Double.MAX_VALUE, DETECTION_RADIUS));
    }

    @Test
    void aCappedUnitWithANearbyThreatFightsIt() {
        assertFalse(ScoutChase.shouldDefend(1, 300, DETECTION_RADIUS));
    }
}
