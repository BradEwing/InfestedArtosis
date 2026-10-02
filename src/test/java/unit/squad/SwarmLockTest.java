package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.DarkSwarm;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.SwarmLock.Verdict.COMMIT;
import static unit.squad.SwarmLock.Verdict.HOLD;
import static unit.squad.SwarmLock.Verdict.NONE;
import static unit.squad.SwarmLock.Verdict.RELEASE;

class SwarmLockTest {

    private static final int HORIZON = SwarmLock.MIN_REMAINING_FRAMES;
    private static final DarkSwarm S1 = new DarkSwarm(382, new Position(1232, 3520), 900);

    private static SwarmLock.Verdict decide(boolean locked, boolean melee, boolean eligible, int remaining,
                                            boolean baseThreatened, boolean inStorm) {
        return decide(locked, melee, eligible, remaining, baseThreatened, inStorm, false);
    }

    private static SwarmLock.Verdict decide(boolean locked, boolean melee, boolean eligible, int remaining,
                                            boolean baseThreatened, boolean inStorm, boolean simRetreat) {
        return SwarmLock.verdict(locked, eligible,
                SwarmLock.releaseReason(melee, false, remaining, baseThreatened, inStorm, simRetreat));
    }

    private static SwarmLock.Verdict unlocked(int remaining) {
        return decide(false, true, true, remaining, false, false);
    }

    private static SwarmLock.Verdict locked(int remaining) {
        return decide(true, true, false, remaining, false, false);
    }

    @Test
    void anEligibleMeleeSquadCommitsWhileTheSwarmHasTheSimHorizonLeft() {
        assertEquals(150, HORIZON);
        assertEquals(COMMIT, unlocked(900));
        assertEquals(COMMIT, unlocked(HORIZON));
        assertEquals(NONE, unlocked(HORIZON - 1));
        assertEquals(NONE, decide(false, true, false, 900, false, false));
    }

    @Test
    void aHeldLockHoldsUntilTheSwarmDropsBelowTheHorizonWhetherOrNotItStillCoversEnemies() {
        assertEquals(HOLD, locked(373));
        assertEquals(HOLD, locked(HORIZON));
        assertEquals(RELEASE, locked(HORIZON - 1));
        assertEquals(RELEASE, locked(0));
    }

    @Test
    void baseDefenceAndPsiStormEscapeOutrankTheLock() {
        assertEquals(RELEASE, decide(true, true, true, 900, true, false));
        assertEquals(RELEASE, decide(true, true, true, 900, false, true));
        assertEquals(NONE, decide(false, true, true, 900, true, false));
        assertEquals(NONE, decide(false, true, true, 900, false, true));
    }

    @Test
    void aSquadThatIsNoLongerMeleeDropsTheLock() {
        assertEquals(RELEASE, decide(true, false, true, 900, false, false));
        assertEquals(NONE, decide(false, false, true, 900, false, false));
    }

    @Test
    void aSwarmLockedContainingSquadSkipsItsContainEvaluationAndTimeout() {
        assertEquals(SwarmLock.Route.SWARM, SwarmLock.route(SquadStatus.CONTAIN, COMMIT));
        assertEquals(SwarmLock.Route.SWARM, SwarmLock.route(SquadStatus.CONTAIN, HOLD));
        assertEquals(SwarmLock.Route.CONTAIN, SwarmLock.route(SquadStatus.CONTAIN, RELEASE));
        assertEquals(SwarmLock.Route.CONTAIN, SwarmLock.route(SquadStatus.CONTAIN, NONE));
    }

    @Test
    void aSwarmLockedSquadNeverReachesTheRetreatLockOrContainEntry() {
        for (SquadStatus status : new SquadStatus[]{SquadStatus.RETREAT, SquadStatus.FIGHT, SquadStatus.RALLY}) {
            assertEquals(SwarmLock.Route.SWARM, SwarmLock.route(status, COMMIT));
            assertEquals(SwarmLock.Route.SWARM, SwarmLock.route(status, HOLD));
            assertEquals(SwarmLock.Route.FIGHT_SQUAD, SwarmLock.route(status, NONE));
            assertEquals(SwarmLock.Route.FIGHT_SQUAD, SwarmLock.route(status, RELEASE));
        }
    }

    @Test
    void aRunbyKeepsItsOwnBranch() {
        assertEquals(SwarmLock.Route.RUNBY, SwarmLock.route(SquadStatus.RUNBY, HOLD));
        assertEquals(SwarmLock.Route.RUNBY, SwarmLock.route(SquadStatus.RUNBY, NONE));
    }

    @Test
    void aSquadIsMeleeWhenZerglingsAndUltralisksAreAtLeastHalfItsSupply() {
        Map<UnitType, Integer> lings = new HashMap<>();
        lings.put(UnitType.Zerg_Zergling, 6);
        lings.put(UnitType.Zerg_Overlord, 1);
        assertTrue(SwarmLock.isMeleeSquad(lings));

        Map<UnitType, Integer> mixed = new HashMap<>();
        mixed.put(UnitType.Zerg_Ultralisk, 1);
        mixed.put(UnitType.Zerg_Hydralisk, 5);
        assertFalse(SwarmLock.isMeleeSquad(mixed));
        mixed.put(UnitType.Zerg_Ultralisk, 2);
        assertTrue(SwarmLock.isMeleeSquad(mixed));

        Map<UnitType, Integer> hydras = new EnumMap<>(UnitType.class);
        hydras.put(UnitType.Zerg_Hydralisk, 6);
        assertFalse(SwarmLock.isMeleeSquad(hydras));
        assertFalse(SwarmLock.isMeleeSquad(Collections.singletonMap(UnitType.Zerg_Overlord, 1)));
    }

    @Test
    void aSquadWithinTheCommitRadiusOfACoveringSwarmIsEligible() {
        Position atRadius = new Position(S1.right() + SwarmLock.COMMIT_RADIUS, 3520);
        Position beyond = new Position(S1.right() + SwarmLock.COMMIT_RADIUS + 1, 3520);

        assertTrue(SwarmLock.isEligible(S1, atRadius, true));
        assertFalse(SwarmLock.isEligible(S1, beyond, true));
        assertFalse(SwarmLock.isEligible(S1, new Position(1232, 3520), false));
    }

    @Test
    void theSwarmCoversAnEnemyWithinTheMarginOfItsFootprint() {
        UnitType marine = UnitType.Terran_Marine;
        Position atMargin = new Position(S1.right() + SwarmLock.COVER_MARGIN + marine.dimensionLeft(), 3520);
        Position beyond = new Position(S1.right() + SwarmLock.COVER_MARGIN + marine.dimensionLeft() + 1, 3520);

        assertTrue(SwarmLock.coversEnemy(S1, new Position(1232, 3520), UnitType.Terran_Supply_Depot));
        assertTrue(SwarmLock.coversEnemy(S1, atMargin, marine));
        assertFalse(SwarmLock.coversEnemy(S1, beyond, marine));
    }

    @Test
    void anUnlockedSquadCommitsToTheNearestEligibleSwarmWithTheHorizonLeft() {
        Position center = new Position(1000, 3520);
        DarkSwarm near = new DarkSwarm(1, new Position(1100, 3520), 900);
        DarkSwarm nearButExpiring = new DarkSwarm(2, new Position(1090, 3520), HORIZON - 1);
        DarkSwarm far = new DarkSwarm(3, new Position(1300, 3520), 900);
        List<DarkSwarm> swarms = Arrays.asList(far, nearButExpiring, near);

        assertSame(near, SwarmLock.choose(swarms, center, Arrays.asList(true, true, true)));
        assertSame(far, SwarmLock.choose(swarms, center, Arrays.asList(true, true, false)));
        assertNull(SwarmLock.choose(swarms, center, Arrays.asList(false, true, false)));
    }

    @Test
    void onlyArmedUnitsAndBuildingsThatHitGroundDrawACommit() {
        assertTrue(SwarmLock.isCommitTarget(UnitType.Terran_Goliath));
        assertTrue(SwarmLock.isCommitTarget(UnitType.Terran_Siege_Tank_Siege_Mode));
        assertTrue(SwarmLock.isCommitTarget(UnitType.Terran_Bunker));
        assertFalse(SwarmLock.isCommitTarget(UnitType.Terran_SCV));
        assertFalse(SwarmLock.isCommitTarget(UnitType.Terran_Medic));
        assertFalse(SwarmLock.isCommitTarget(UnitType.Terran_Supply_Depot));
        assertFalse(SwarmLock.isCommitTarget(UnitType.Terran_Missile_Turret));
    }

    @Test
    void theSampleStillFindsASwarmBelowTheHorizonThatTheCommitWouldSkip() {
        Position center = new Position(1000, 3520);
        DarkSwarm expiring = new DarkSwarm(2, new Position(1090, 3520), HORIZON - 1);
        List<DarkSwarm> swarms = Collections.singletonList(expiring);
        List<Boolean> eligible = Collections.singletonList(true);

        assertNull(SwarmLock.choose(swarms, center, eligible));
        assertSame(expiring, SwarmLock.nearest(swarms, center, eligible, 1));
    }

    @Test
    void aSwarmLockClearsTheRetreatLockAndSurvivesAMerge() {
        Squad locked = new GroundSquad();
        locked.setSwarmLock(new SwarmLock(382, 17942));
        locked.setStatus(SquadStatus.FIGHT);
        Squad retreating = new GroundSquad();
        retreating.setStatus(SquadStatus.RETREAT);
        retreating.startRetreatLock(18000);

        assertTrue(retreating.isRetreatLocked(18001));
        retreating.clearRetreatLock();
        assertFalse(retreating.isRetreatLocked(18001));

        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(retreating, locked));
        assertEquals(382, merged.getSwarmLock().getSwarmId());

        Squad sibling = new GroundSquad();
        sibling.inheritStateFrom(locked);
        assertEquals(382, sibling.getSwarmLock().getSwarmId());
    }

    @Test
    void aSwarmPricedRetreatRefusesTheCommitAndReleasesAHeldLock() {
        assertEquals(NONE, decide(false, true, true, 900, false, false, true));
        assertEquals(RELEASE, decide(true, true, false, 900, false, false, true));
        assertEquals(COMMIT, decide(false, true, true, 900, false, false, false));
        assertEquals(HOLD, decide(true, true, false, 900, false, false, false));
    }

    @Test
    void theReleaseReasonNamesTheFirstThingThatStandsAgainstTheLock() {
        assertEquals(SwarmLock.Release.NOT_MELEE, SwarmLock.releaseReason(false, true, 0, true, true, true));
        assertEquals(SwarmLock.Release.BASE_THREAT, SwarmLock.releaseReason(true, true, 0, true, true, true));
        assertEquals(SwarmLock.Release.STORM, SwarmLock.releaseReason(true, true, 0, false, true, true));
        assertEquals(SwarmLock.Release.GONE, SwarmLock.releaseReason(true, true, 0, false, false, true));
        assertEquals(SwarmLock.Release.HORIZON, SwarmLock.releaseReason(true, false, HORIZON - 1, false, false, true));
        assertEquals(SwarmLock.Release.SIM_RETREAT, SwarmLock.releaseReason(true, false, HORIZON, false, false, true));
        assertEquals(SwarmLock.Release.NONE, SwarmLock.releaseReason(true, false, HORIZON, false, false, false));
    }

    @Test
    void theSimDecidesOnlyALockNothingElseStandsAgainst() {
        assertTrue(SwarmLock.simDecides(true, false, SwarmLock.Release.NONE));
        assertTrue(SwarmLock.simDecides(false, true, SwarmLock.Release.NONE));
        assertFalse(SwarmLock.simDecides(false, false, SwarmLock.Release.NONE));
        assertFalse(SwarmLock.simDecides(true, false, SwarmLock.Release.HORIZON));
        assertFalse(SwarmLock.simDecides(false, true, SwarmLock.Release.BASE_THREAT));
    }

    @Test
    void aSquadReleasedOnASimRetreatCommitsToNoSwarmUntilTheCooldownRunsOut() {
        int released = 29955;
        int cooldown = SwarmLock.RECOMMIT_COOLDOWN_FRAMES;

        assertTrue(SwarmLock.mayCommit(released, -1));
        assertFalse(SwarmLock.mayCommit(released, released));
        assertFalse(SwarmLock.mayCommit(released + 20, released));
        assertFalse(SwarmLock.mayCommit(released + cooldown - 1, released));
        assertTrue(SwarmLock.mayCommit(released + cooldown, released));
    }

    @Test
    void theCooldownBarsTheRecommitsOfASquadTurningBetweenTwoNearbySwarms() {
        int[][] releaseThenRecommit = {{29955, 29975}, {29981, 29988}, {29991, 29993}, {30005, 30008}, {30015, 30020}};

        for (int[] pair : releaseThenRecommit) {
            assertFalse(SwarmLock.mayCommit(pair[1], pair[0]));
        }
        assertTrue(SwarmLock.mayCommit(29955 + SwarmLock.RECOMMIT_COOLDOWN_FRAMES, 29955));
    }

    @Test
    void aMergedSquadKeepsTheLatestSimRetreatReleaseOfItsSources() {
        Squad early = new GroundSquad();
        early.setSimRetreatReleaseFrame(29955);
        Squad late = new GroundSquad();
        late.setSimRetreatReleaseFrame(29981);
        Squad merged = new GroundSquad();

        assertEquals(-1, new GroundSquad().getSimRetreatReleaseFrame());
        merged.inheritStateFrom(Arrays.asList(late, new GroundSquad(), early));
        assertEquals(29981, merged.getSimRetreatReleaseFrame());
    }

    @Test
    void aHeldLockSurvivesARetreatReadUntilItFallsBelowTheReleaseHysteresis() {
        double threshold = 1.44;
        double release = threshold * SwarmLock.RELEASE_HYSTERESIS;

        assertFalse(SwarmLock.releasesOnRead(true, true, 1.436, threshold));
        assertFalse(SwarmLock.releasesOnRead(true, true, release, threshold));
        assertTrue(SwarmLock.releasesOnRead(true, true, release - 0.001, threshold));
        assertTrue(SwarmLock.releasesOnRead(true, true, 0, threshold));
        assertFalse(SwarmLock.releasesOnRead(true, false, 0.5, threshold));
        assertTrue(SwarmLock.releasesOnRead(true, true, SwarmLock.NO_READ, 0));
        assertFalse(SwarmLock.releasesOnRead(true, false, SwarmLock.NO_READ, 0));

        assertEquals(HOLD, SwarmLock.verdict(true, false, SwarmLock.releaseReason(true, false, 600, false, false,
                SwarmLock.releasesOnRead(true, true, 1.41, threshold))));
        assertEquals(RELEASE, SwarmLock.verdict(true, false, SwarmLock.releaseReason(true, false, 600, false, false,
                SwarmLock.releasesOnRead(true, true, 1.20, threshold))));
    }

    @Test
    void anUnlockedSquadIsRefusedOnAnyRetreatRead() {
        double threshold = 1.44;

        assertTrue(SwarmLock.releasesOnRead(false, true, 1.436, threshold));
        assertFalse(SwarmLock.releasesOnRead(false, false, 1.45, threshold));
        assertEquals(NONE, SwarmLock.verdict(false, true, SwarmLock.releaseReason(true, false, 600, false, false,
                SwarmLock.releasesOnRead(false, true, 1.436, threshold))));
    }

    @Test
    void aMergeDropsASourceLockOnlyWhenTheMergedSquadDoesNotHoldThatSwarm() {
        SwarmLock s1 = new SwarmLock(382, 17942);
        SwarmLock s2 = new SwarmLock(397, 18763);

        assertTrue(SwarmLock.droppedByMerge(s2, s1));
        assertTrue(SwarmLock.droppedByMerge(s1, null));
        assertFalse(SwarmLock.droppedByMerge(s1, new SwarmLock(382, 18000)));
        assertFalse(SwarmLock.droppedByMerge(null, s1));
        assertFalse(SwarmLock.droppedByMerge(null, null));
    }

    @Test
    void aReinforcementJoiningASwarmLockedSquadOnlyTakesTheFightRole() {
        for (SquadStatus status : SquadStatus.values()) {
            assertEquals(SquadManager.ReinforcementPath.JOIN_SWARM,
                    SquadManager.reinforcementPath(true, status, true, true));
            assertEquals(SquadManager.reinforcementPath(status, false, false),
                    SquadManager.reinforcementPath(false, status, false, false));
        }
        assertEquals(SquadManager.ReinforcementPath.SIMULATE,
                SquadManager.reinforcementPath(false, SquadStatus.FIGHT, false, false));
    }

    @Test
    void aCoveredReadCommitsButAnUncoveredOneNeedsTheMarginOverTheEngageThreshold() {
        double threshold = 1.44;
        double margin = threshold * SwarmLock.UNCOVERED_COMMIT_MARGIN;

        assertTrue(SwarmLock.commitsOnRead(true, 0.01, threshold, threshold));
        assertFalse(SwarmLock.commitsOnRead(true, 0.0, 1.60, threshold));
        assertFalse(SwarmLock.commitsOnRead(true, 0.0, margin - 0.001, threshold));
        assertTrue(SwarmLock.commitsOnRead(true, 0.0, margin, threshold));
        assertFalse(SwarmLock.commitsOnRead(true, -1, 1.60, threshold));
        assertFalse(SwarmLock.commitsOnRead(false, 1.0, 9.0, threshold));

        assertEquals(NONE, SwarmLock.verdict(false, SwarmLock.commitsOnRead(true, 0.0, 1.60, threshold),
                SwarmLock.Release.NONE));
        assertEquals(COMMIT, SwarmLock.verdict(false, SwarmLock.commitsOnRead(true, 0.25, 1.50, threshold),
                SwarmLock.Release.NONE));
        assertEquals(HOLD, SwarmLock.verdict(true, SwarmLock.commitsOnRead(false, 0.0, 1.0, threshold),
                SwarmLock.Release.NONE));
    }

    @Test
    void aBaseThreatKeepsStandingAgainstTheLockForTheHoldAfterItWasLastSeen() {
        int seen = 26352;
        int hold = SwarmLock.BASE_THREAT_HOLD_FRAMES;

        assertTrue(SwarmLock.baseThreatStands(true, seen, -1));
        assertFalse(SwarmLock.baseThreatStands(false, seen, -1));
        assertTrue(SwarmLock.baseThreatStands(false, seen + hold - 1, seen));
        assertFalse(SwarmLock.baseThreatStands(false, seen + hold, seen));
        assertEquals(SwarmLock.Release.BASE_THREAT, SwarmLock.releaseReason(true, false, 600,
                SwarmLock.baseThreatStands(false, seen + 10, seen), false, false));
        assertEquals(NONE, SwarmLock.verdict(false, true, SwarmLock.releaseReason(true, false, 600,
                SwarmLock.baseThreatStands(false, seen + 10, seen), false, false)));
    }
}
