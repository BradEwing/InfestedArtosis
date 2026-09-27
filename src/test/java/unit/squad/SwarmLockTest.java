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
    void aSquadReleasedOnASimRetreatDoesNotRecommitToThatSwarmButMayTakeAnother() {
        assertFalse(SwarmLock.mayRecommit(382, 382));
        assertTrue(SwarmLock.mayRecommit(397, 382));
        assertTrue(SwarmLock.mayRecommit(382, -1));

        Squad refused = new GroundSquad();
        assertEquals(-1, refused.getRefusedSwarmId());
        refused.setRefusedSwarmId(382);
        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(new GroundSquad(), refused));
        assertEquals(382, merged.getRefusedSwarmId());
    }
}
