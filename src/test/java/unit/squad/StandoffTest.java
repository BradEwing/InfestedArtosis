package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.squad.Standoff.Army;
import unit.squad.Standoff.Episode;
import unit.squad.Standoff.Verdict;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandoffTest {
    private static final Position CENTER = new Position(1000, 1000);
    private static final Position ENEMY = new Position(1500, 1000);

    private static List<Position> spreadSquad() {
        return Arrays.asList(new Position(700, 1000), new Position(1000, 1000), new Position(1300, 1000),
                new Position(1000, 700), new Position(1000, 1300), new Position(850, 1000));
    }

    private static List<Integer> ones(int count) {
        return Collections.nCopies(count, 1);
    }

    @Test
    void aSpreadSquadApproachingAMeasuredEnemyHoldsAtAStandoffPoint() {
        double share = Standoff.gatheredShare(spreadSquad(), ones(6), CENTER);
        int distance = Standoff.distance(0);

        Verdict verdict = Standoff.read(true, 500, distance, share, 0, Standoff.timeoutFrames(false));
        Position point = Standoff.point(ENEMY, CENTER, distance, position -> true);

        assertTrue(share < Standoff.GATHER_SHARE);
        assertEquals(Verdict.HOLD, verdict);
        assertTrue(Standoff.holds(true, verdict));
        assertEquals(distance, point.getDistance(ENEMY), 2);
        assertTrue(point.getDistance(CENTER) < ENEMY.getDistance(CENTER));
    }

    @Test
    void aLeadMemberJustPastSightRangeOfTheEnemyStillHolds() {
        int distance = Standoff.distance(Standoff.reachOf(UnitType.Terran_Marine));

        assertEquals(Verdict.HOLD, Standoff.read(true, 190, distance, 0.4, 0, 240));
        assertEquals(Verdict.ENEMY_ARRIVED, Standoff.read(true, 150, distance, 0.4, 0, 240));
    }

    @Test
    void aMergedEpisodeKeepsTheEarliestStartAndTheLatestRelease() {
        Episode first = new Episode();
        Episode second = new Episode();
        Episode released = new Episode();
        first.begin(300, CENTER);
        second.begin(200, ENEMY);
        released.release(100);
        Episode merged = new Episode();

        merged.foldFrom(Arrays.asList(first, second, released));

        assertTrue(merged.isActive());
        assertEquals(200, merged.getStartFrame());
        assertEquals(ENEMY, merged.getPoint());
        assertTrue(merged.isReleased(100 + Standoff.RELEASE_HOLD_FRAMES - 1));
    }

    @Test
    void aGatheredSquadEngagesAtOnce() {
        List<Position> gathered = Arrays.asList(new Position(1000, 1000), new Position(1020, 1000),
                new Position(1000, 1030), new Position(1040, 1040));

        double share = Standoff.gatheredShare(gathered, ones(4), CENTER);

        assertEquals(1.0, share, 1e-9);
        assertEquals(Verdict.GATHERED, Standoff.read(true, 500, Standoff.distance(0), share, 0, 240));
    }

    @Test
    void theShareIsWeightedBySupply() {
        List<Position> positions = Arrays.asList(new Position(1000, 1000), new Position(1400, 1000));

        double share = Standoff.gatheredShare(positions, Arrays.asList(3, 1), CENTER);

        assertEquals(0.75, share, 1e-9);
        assertEquals(1.0, Standoff.gatheredShare(Collections.emptyList(), Collections.emptyList(), CENTER), 1e-9);
    }

    @Test
    void theTimeoutEndsTheStandoff() {
        int distance = Standoff.distance(0);

        assertEquals(Verdict.HOLD, Standoff.read(true, 500, distance, 0.2, 239, Standoff.TIMEOUT_FRAMES));
        assertEquals(Verdict.TIMEOUT, Standoff.read(true, 500, distance, 0.2, 240, Standoff.TIMEOUT_FRAMES));
    }

    @Test
    void aRushOrAllInKeepsItsAttackTimingWithAShortTimeout() {
        int distance = Standoff.distance(0);

        assertTrue(Standoff.timeoutFrames(true) < Standoff.timeoutFrames(false));
        assertEquals(Verdict.HOLD, Standoff.read(true, 500, distance, 0.2, 71, Standoff.timeoutFrames(true)));
        assertEquals(Verdict.TIMEOUT, Standoff.read(true, 500, distance, 0.2, 72, Standoff.timeoutFrames(true)));
        assertEquals(Verdict.HOLD, Standoff.read(true, 500, distance, 0.2, 72, Standoff.timeoutFrames(false)));
    }

    @Test
    void anEnemyInsideTheStandoffDistanceEndsItAtOnce() {
        int distance = Standoff.distance(0);

        assertEquals(Verdict.ENEMY_ARRIVED, Standoff.read(true, distance - Standoff.ARRIVAL_SLACK - 1, distance, 0.2,
                10, 240));
        assertEquals(Verdict.HOLD, Standoff.read(true, distance - Standoff.ARRIVAL_SLACK, distance, 0.2, 10, 240));
    }

    @Test
    void theEnemyArrivingBeatsAGatheredReading() {
        int distance = Standoff.distance(0);

        assertEquals(Verdict.ENEMY_ARRIVED, Standoff.read(true, 100, distance, 1.0, 0, 240));
    }

    @Test
    void theStandoffDistanceFollowsTheEnemyReachWithinBounds() {
        assertEquals(Standoff.MIN_DISTANCE, Standoff.distance(0));
        assertEquals(256, Standoff.distance(256 - Standoff.REACH_MARGIN));
        assertEquals(Standoff.MAX_DISTANCE, Standoff.distance(1000));
        assertEquals(0, Standoff.reachOf(UnitType.Zerg_Overlord));
        assertTrue(Standoff.reachOf(UnitType.Terran_Marine) > 0);
    }

    @Test
    void theStandoffDoesNotApplyToWhatItExempts() {
        assertTrue(Standoff.applies(true, true, false, false, false));
        assertFalse(Standoff.applies(false, true, false, false, false));
        assertFalse(Standoff.applies(true, false, false, false, false));
        assertFalse(Standoff.applies(true, true, true, false, false));
        assertFalse(Standoff.applies(true, true, false, true, false));
        assertFalse(Standoff.applies(true, true, false, false, true));
        assertEquals(Verdict.NOT_APPLICABLE, Standoff.read(false, 500, 224, 0, 0, 240));
    }

    @Test
    void thePointIsPulledBackUntilItIsWalkable() {
        Position point = Standoff.point(ENEMY, CENTER, 224, position -> position.getX() <= 1200);

        assertTrue(point.getX() <= 1200);
        assertTrue(point.getX() > CENTER.getX());
        assertEquals(CENTER, Standoff.point(ENEMY, CENTER, 224, position -> false));
        assertEquals(CENTER, Standoff.point(new Position(1100, 1000), CENTER, 224, position -> true));
    }

    @Test
    void aFourLingReinforcementIsRoutedToTheRearOfTheEngagedSquad() {
        Army engaged = new Army(new Position(2000, 1000), new Position(2400, 1000), 12);

        Position point = Standoff.reinforcementPoint(4, CENTER, Collections.singletonList(engaged), position -> true);

        assertNotNull(point);
        assertEquals(2000 - Standoff.REAR_OFFSET, point.getX());
        assertEquals(1000, point.getY());
        assertTrue(point.getDistance(engaged.getCenter()) < SquadManager.SQUAD_MERGE_DISTANCE);
        assertTrue(point.getDistance(engaged.getEnemyCenter())
                > engaged.getCenter().getDistance(engaged.getEnemyCenter()));
    }

    @Test
    void aReinforcementJoinsTheNearestArmyAtLeastAsStrongAsItself() {
        Army far = new Army(new Position(3000, 1000), new Position(3400, 1000), 20);
        Army near = new Army(new Position(1800, 1000), new Position(1800, 1400), 8);
        Army weak = new Army(new Position(1100, 1000), new Position(1500, 1000), 3);

        Position point = Standoff.reinforcementPoint(4, CENTER, Arrays.asList(far, weak, near), position -> true);

        assertEquals(1800, point.getX());
        assertEquals(1000 - Standoff.REAR_OFFSET, point.getY());
    }

    @Test
    void aReinforcementWithNoArmyToJoinLaunchesAsBefore() {
        assertNull(Standoff.reinforcementPoint(4, CENTER, Collections.emptyList(), position -> true));
        assertNull(Standoff.reinforcementPoint(9, CENTER,
                Collections.singletonList(new Army(CENTER, ENEMY, 8)), position -> true));
        assertNull(Standoff.reinforcementPoint(4, CENTER,
                Collections.singletonList(new Army(CENTER, null, 8)), position -> true));
    }

    @Test
    void withTheSwitchOffNothingIsHeldOrRouted() {
        Position rear = Standoff.rearPoint(CENTER, ENEMY, position -> true);

        assertTrue(Standoff.holds(true, Verdict.HOLD));
        assertFalse(Standoff.holds(false, Verdict.HOLD));
        assertFalse(Standoff.holds(true, Verdict.GATHERED));
        assertTrue(Standoff.routes(true, rear));
        assertFalse(Standoff.routes(false, rear));
        assertFalse(Standoff.routes(true, null));
    }

    @Test
    void theEpisodeOpensTouchesAndReleases() {
        Episode episode = new Episode();
        assertFalse(episode.isActive());
        assertEquals(0, episode.framesStaged(500));

        episode.begin(100, CENTER);
        episode.touch(130);

        assertTrue(episode.isActive());
        assertEquals(40, episode.framesStaged(140));
        assertFalse(episode.isStale(130 + Standoff.STALE_FRAMES));
        assertTrue(episode.isStale(130 + Standoff.STALE_FRAMES + 1));

        episode.release(150);

        assertFalse(episode.isActive());
        assertNull(episode.getPoint());
        assertTrue(episode.isReleased(150 + Standoff.RELEASE_HOLD_FRAMES - 1));
        assertFalse(episode.isReleased(150 + Standoff.RELEASE_HOLD_FRAMES));
    }
}
