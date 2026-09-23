package info.tracking;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WeaponType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import telemetry.ReachTelemetry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyReachMemoryTest {

    private static final int MARINE_RANGE = UnitType.Terran_Marine.groundWeapon().maxRange();
    private static final Position BUNKER = new Position(848, 896);
    private static final Set<Position> BUNKERS = Collections.singleton(BUNKER);
    private static final int BUNKER_SHOT_RADIUS = 224;
    private static final Position VICTIM = new Position(831, 1102);

    @AfterEach
    void clearSink() {
        ReachTelemetry.clear();
    }

    private static Position lingBelowBunkerAtGap(int gap) {
        int bunkerBottom = BUNKER.getY() + UnitType.Terran_Bunker.dimensionDown();
        return new Position(BUNKER.getX(), bunkerBottom + gap + UnitType.Zerg_Zergling.dimensionUp());
    }

    @Test
    void reachOnlyRises() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertTrue(memory.raise(UnitType.Terran_Marine, 160, EnemyReachMemory.Source.VISIBLE, VICTIM, 100));
        assertFalse(memory.raise(UnitType.Terran_Marine, 140, EnemyReachMemory.Source.VISIBLE, VICTIM, 200));
        memory.seed(UnitType.Terran_Marine, MARINE_RANGE, 300);

        assertEquals(160, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void groundReachIsTheLargerOfTheApiRangeAndTheLearnedReach() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Marine));
        assertEquals(MARINE_RANGE + 32, memory.groundReach(UnitType.Terran_Marine, MARINE_RANGE + 32));

        memory.raise(UnitType.Terran_Marine, 184, EnemyReachMemory.Source.VISIBLE, VICTIM, 100);

        assertEquals(184, memory.groundReach(UnitType.Terran_Marine, MARINE_RANGE + 32));
        assertEquals(200, memory.groundReach(UnitType.Terran_Marine, 200));
    }

    @Test
    void aSeedAboveTheBaseRangeIsKept() {
        EnemyReachMemory memory = new EnemyReachMemory();

        memory.seed(UnitType.Terran_Marine, MARINE_RANGE + 32, 100);

        assertEquals(MARINE_RANGE + 32, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void aBunkerStartsAtTheRangeOfTheMarinesInside() {
        assertEquals(UnitType.Terran_Marine.groundWeapon(), EnemyReachMemory.groundWeapon(UnitType.Terran_Bunker));
        assertEquals(MARINE_RANGE, new EnemyReachMemory().groundReach(UnitType.Terran_Bunker));
        assertEquals(WeaponType.None, EnemyReachMemory.groundWeapon(UnitType.Terran_Medic));
        assertEquals(0, EnemyReachMemory.baseGroundRange(UnitType.Terran_Medic));
    }

    @Test
    void aHitFromWithinTheKnownReachIsAttributedWithoutRaisingIt() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertTrue(memory.learnFromHit(UnitType.Terran_Marine, MARINE_RANGE - 20,
                EnemyReachMemory.Source.VISIBLE, VICTIM, 100));
        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void aHitFromPastTheKnownReachRaisesItToTheDistance() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertTrue(memory.learnFromHit(UnitType.Terran_Marine, MARINE_RANGE + 40,
                EnemyReachMemory.Source.VISIBLE, VICTIM, 100));
        assertEquals(MARINE_RANGE + 40, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void aHitFarBeyondTheKnownReachIsNotPinnedOnTheType() {
        EnemyReachMemory memory = new EnemyReachMemory();
        int tooFar = MARINE_RANGE + EnemyReachMemory.MAX_LEARN_STEP + 1;

        assertFalse(memory.learnFromHit(UnitType.Terran_Marine, tooFar, EnemyReachMemory.Source.VISIBLE, VICTIM,
                100));
        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void aBunkerShotOnALing184PixelsBelowItsFootprintRaisesBunkerReachTo184() {
        EnemyReachMemory memory = new EnemyReachMemory();
        Position ling = lingBelowBunkerAtGap(184);

        assertEquals(184, EnemyReachMemory.footprintGap(UnitType.Terran_Bunker, BUNKER, UnitType.Zerg_Zergling,
                ling));
        assertTrue(memory.learnFromBunkerShot(ling, UnitType.Zerg_Zergling, ling, BUNKERS, BUNKER_SHOT_RADIUS,
                7546));
        assertTrue(memory.groundReach(UnitType.Terran_Bunker) >= 184);
    }

    @Test
    void aBunkerShotIsCreditedToTheNearestKnownBunker() {
        EnemyReachMemory memory = new EnemyReachMemory();
        Position farBunker = new Position(BUNKER.getX(), BUNKER.getY() + 180);
        Position ling = lingBelowBunkerAtGap(184);

        memory.learnFromBunkerShot(ling, UnitType.Zerg_Zergling, ling,
                new HashSet<>(Arrays.asList(BUNKER, farBunker)), BUNKER_SHOT_RADIUS, 7546);

        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Bunker),
                "the shot is inside the nearer Bunker's known reach and teaches nothing");
    }

    @Test
    void aShotWithNoBunkerWithinItsRadiusTeachesNothing() {
        EnemyReachMemory memory = new EnemyReachMemory();
        Position farShot = new Position(BUNKER.getX(), BUNKER.getY() + BUNKER_SHOT_RADIUS + 1);

        assertFalse(memory.learnFromBunkerShot(farShot, UnitType.Zerg_Zergling, farShot, BUNKERS,
                BUNKER_SHOT_RADIUS, 7546));
        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Bunker));
    }

    @Test
    void aHurtMarkExpiresAfterItsWindow() {
        EnemyReachMemory memory = new EnemyReachMemory();

        memory.recordHurt(VICTIM, 100);

        assertEquals(1, memory.liveHurtMarks(100 + EnemyReachMemory.HURT_MARK_WINDOW - 1).size());
        assertTrue(memory.liveHurtMarks(100 + EnemyReachMemory.HURT_MARK_WINDOW).isEmpty());
    }

    @Test
    void aRepeatHitRefreshesTheMarkInsteadOfAddingOne() {
        EnemyReachMemory memory = new EnemyReachMemory();

        memory.recordHurt(VICTIM, 100);
        memory.recordHurt(new Position(VICTIM.getX() + 10, VICTIM.getY()), 400);

        List<EnemyReachMemory.HurtMark> marks = memory.liveHurtMarks(100 + EnemyReachMemory.HURT_MARK_WINDOW);
        assertEquals(1, marks.size());
        assertEquals(400, marks.get(0).getFrame());
        assertTrue(memory.liveHurtMarks(400 + EnemyReachMemory.HURT_MARK_WINDOW).isEmpty());
    }

    @Test
    void aHitAwayFromEveryMarkAddsAnother() {
        EnemyReachMemory memory = new EnemyReachMemory();

        memory.recordHurt(VICTIM, 100);
        memory.recordHurt(new Position(VICTIM.getX() + 200, VICTIM.getY()), 110);

        assertEquals(2, memory.liveHurtMarks(120).size());
    }

    @Test
    void aHurtMarkIsADiscAroundWhereTheVictimStood() {
        EnemyReachMemory memory = new EnemyReachMemory();
        memory.recordHurt(VICTIM, 100);

        EnemyReachMemory.HurtMark mark = memory.liveHurtMarks(100).get(0);

        assertTrue(mark.toZone().covers(new Position(VICTIM.getX() + EnemyReachMemory.HURT_MARK_RADIUS,
                VICTIM.getY()), 0));
        assertFalse(mark.toZone().covers(new Position(VICTIM.getX() + EnemyReachMemory.HURT_MARK_RADIUS + 1,
                VICTIM.getY()), 0));
    }

    @Test
    void everyRiseAndEveryNewMarkIsReported() {
        List<String> rows = new ArrayList<>();
        ReachTelemetry.register((frame, type, oldReach, newReach, source, victim) ->
                rows.add(type + ":" + oldReach + ":" + newReach + ":" + source));
        EnemyReachMemory memory = new EnemyReachMemory();
        Position ling = lingBelowBunkerAtGap(184);

        memory.seed(UnitType.Terran_Marine, MARINE_RANGE, 1);
        memory.learnFromBunkerShot(ling, UnitType.Zerg_Zergling, ling, BUNKERS, BUNKER_SHOT_RADIUS, 2);
        memory.learnFromBunkerShot(ling, UnitType.Zerg_Zergling, ling, BUNKERS, BUNKER_SHOT_RADIUS, 3);
        memory.recordHurt(VICTIM, 4);
        memory.recordHurt(VICTIM, 5);

        assertEquals(2, rows.size());
        assertEquals("Terran_Bunker:" + MARINE_RANGE + ":184:BULLET", rows.get(0));
        assertEquals("null:-1:" + EnemyReachMemory.HURT_MARK_RADIUS + ":HURTMARK", rows.get(1));
    }
}
