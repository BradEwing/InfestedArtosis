package unit.managed;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.DarkSwarm;
import info.tracking.DarkSwarmTracker;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefilerCastTest {

    private static final int HALF_WIDTH = UnitType.Spell_Dark_Swarm.dimensionLeft();
    private static final Position FRONT = new Position(1000, 3300);

    @Test
    void aDistantTargetPutsTheFootprintsNearEdgeOnOurFront() {
        Position target = new Position(1300, 3300);

        Position cast = Defiler.castPoint(FRONT, target);

        assertEquals(new Position(1000 + HALF_WIDTH, 3300), cast);
        assertEquals(0, new DarkSwarm(0, cast, 900).gap(FRONT), 1e-9);
    }

    @Test
    void theCastIsAimedAlongTheLineToTheTarget() {
        Position target = new Position(1300, 3700);

        Position cast = Defiler.castPoint(FRONT, target);

        assertEquals(1048, cast.getX());
        assertEquals(3364, cast.getY());
    }

    @Test
    void aTargetWithinHalfTheFootprintIsCastOnDirectly() {
        Position target = new Position(1040, 3330);

        assertEquals(target, Defiler.castPoint(FRONT, target));
        assertEquals(FRONT, Defiler.castPoint(FRONT, FRONT));
    }

    @Test
    void buildingsThatCannotHitGroundUnitsAreLeftOutOfTheAim() {
        assertFalse(Defiler.isAimTarget(UnitType.Terran_Supply_Depot));
        assertFalse(Defiler.isAimTarget(UnitType.Terran_Barracks));
        assertFalse(Defiler.isAimTarget(UnitType.Terran_Missile_Turret));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Bunker));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Goliath));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Siege_Tank_Siege_Mode));
        assertTrue(Defiler.isAimTarget(UnitType.Terran_Medic));
    }

    @Test
    void aLiveSwarmOverThePointRefusesTheCast() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);

        assertTrue(Defiler.blocksCast(existing, false, new Position(1232, 3520)));
        assertTrue(Defiler.blocksCast(existing, true, new Position(1300, 3590)));
    }

    @Test
    void aCastWhoseFootprintWouldOverlapALiveSwarmIsRefused() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);

        assertTrue(Defiler.blocksCast(existing, false, new Position(1232 + 100, 3520)));
        assertTrue(Defiler.blocksCast(existing, true, new Position(existing.right() + HALF_WIDTH, 3520)));
    }

    @Test
    void aCastWhoseFootprintClearsEveryLiveSwarmGoesAhead() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);

        assertFalse(Defiler.blocksCast(existing, false, new Position(existing.right() + HALF_WIDTH + 1, 3520)));
        assertFalse(Defiler.blocksCast(existing, false, new Position(1232 + 200, 3520)));
    }

    @Test
    void aLapsingSwarmOverCommittedMeleeIsRecast() {
        int recast = Defiler.RECAST_REMAINING_FRAMES;
        Position point = new Position(1232, 3520);

        assertFalse(Defiler.blocksCast(new DarkSwarm(382, point, recast - 1), true, point));
        assertTrue(Defiler.blocksCast(new DarkSwarm(382, point, recast), true, point));
        assertTrue(Defiler.blocksCast(new DarkSwarm(382, point, recast - 1), false, point));
    }

    @Test
    void aPendingCastBlocksASecondCastOnTheSameSpotBeforeItsSwarmAppears() {
        DarkSwarmTracker tracker = new DarkSwarmTracker();
        Position point = new Position(3631, 1928);
        tracker.recordCast(point, 20112);

        List<DarkSwarm> pending = tracker.getPendingCasts(20112);

        assertNull(Defiler.openCastPoint(Collections.singletonList(point), pending, Collections.emptySet()));
        assertNull(Defiler.openCastPoint(Collections.singletonList(new Position(3631 + 100, 1928)), pending,
                Collections.emptySet()));
    }

    @Test
    void aPendingCastIsNeverRecastOverEvenWithMeleeUnderIt() {
        DarkSwarmTracker tracker = new DarkSwarmTracker();
        Position point = new Position(3631, 1928);
        tracker.recordCast(point, 20112);

        List<DarkSwarm> pending = tracker.getPendingCasts(20113);

        assertNull(Defiler.openCastPoint(Collections.singletonList(point), pending,
                Collections.singleton(DarkSwarmTracker.PENDING_CAST_ID)));
    }

    @Test
    void aBlockedClosestSpotFallsBackToTheNextOpenSpot() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);
        Position covered = new Position(1250, 3520);
        Position clear = new Position(existing.right() + HALF_WIDTH + 1, 3520);

        assertEquals(clear, Defiler.openCastPoint(Arrays.asList(covered, clear),
                Collections.singletonList(existing), Collections.emptySet()));
        assertEquals(covered, Defiler.openCastPoint(Arrays.asList(covered, clear), Collections.emptyList(),
                Collections.emptySet()));
    }

    @Test
    void theEnergyIsHeldWhenEverySpotIsBlocked() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);

        assertNull(Defiler.openCastPoint(Arrays.asList(new Position(1232, 3520), new Position(1280, 3560)),
                Collections.singletonList(existing), Collections.emptySet()));
        assertNull(Defiler.openCastPoint(Collections.emptyList(), Collections.emptyList(), Collections.emptySet()));
    }

    @Test
    void castCandidatesTryTheClosestPairFirstAndSkipPairsBeyondTheSafeDistance() {
        Position near = new Position(1100, 3300);
        Position mid = new Position(1200, 3300);
        Position far = new Position(1300, 3300);
        List<Defiler.CastPair> pairs = Arrays.asList(
                new Defiler.CastPair(FRONT, mid, 200),
                new Defiler.CastPair(FRONT, far, 257),
                new Defiler.CastPair(FRONT, near, 100),
                new Defiler.CastPair(FRONT, far, 256));

        List<Position> candidates = Defiler.castCandidates(pairs, FRONT);

        assertEquals(Arrays.asList(Defiler.castPoint(FRONT, near), Defiler.castPoint(FRONT, mid),
                Defiler.castPoint(FRONT, far)), candidates);
    }

    @Test
    void aCastPointBeyondSpellRangeOfTheDefilerIsLeftOut() {
        Position target = new Position(1300, 3300);
        Position cast = Defiler.castPoint(FRONT, target);
        List<Defiler.CastPair> pairs = Collections.singletonList(new Defiler.CastPair(FRONT, target, 100));

        assertEquals(Collections.singletonList(cast),
                Defiler.castCandidates(pairs, new Position(cast.getX() - 288, 3300)));
        assertTrue(Defiler.castCandidates(pairs, new Position(cast.getX() - 289, 3300)).isEmpty());
    }

    @Test
    void aCastWhoseFootprintMeetsALiveSwarmOnlyAtTheCornerIsRefused() {
        DarkSwarm existing = new DarkSwarm(382, new Position(1232, 3520), 900);
        Position corner = new Position(1232 + 150, 3520 + 150);
        Position clearCorner = new Position(1232 + 170, 3520 + 170);

        assertTrue(existing.gap(corner) > HALF_WIDTH);
        assertTrue(Defiler.blocksCast(existing, false, corner));
        assertFalse(Defiler.blocksCast(existing, false, clearCorner));
    }
}
