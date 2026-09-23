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
    private static final int MARINE_CAP = MARINE_RANGE + EnemyReachMemory.MEASUREMENT_MARGIN;
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

        assertTrue(memory.raise(UnitType.Terran_Marine, MARINE_RANGE + 12, EnemyReachMemory.Source.VISIBLE, VICTIM,
                100));
        assertFalse(memory.raise(UnitType.Terran_Marine, MARINE_RANGE + 6, EnemyReachMemory.Source.VISIBLE, VICTIM,
                200));
        memory.seed(UnitType.Terran_Marine, MARINE_RANGE, 300);

        assertEquals(MARINE_RANGE + 12, memory.groundReach(UnitType.Terran_Marine),
                "both values sit under the Marine's cap, so the test shows only the ratchet");
    }

    @Test
    void groundReachIsTheLargerOfTheApiRangeAndTheLearnedReach() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Marine));
        assertEquals(MARINE_RANGE + 32, memory.groundReach(UnitType.Terran_Marine, MARINE_RANGE + 32));

        memory.raise(UnitType.Terran_Marine, MARINE_RANGE + 12, EnemyReachMemory.Source.VISIBLE, VICTIM, 100);

        assertEquals(MARINE_RANGE + 12, memory.groundReach(UnitType.Terran_Marine, MARINE_RANGE + 8),
                "learned 140 used to be 184, which is now past the 144 cap of a Marine with no reported upgrade");
        assertEquals(200, memory.groundReach(UnitType.Terran_Marine, 200));
    }

    @Test
    void aMeleeTypeNeverLearnsPastItsWeaponRangePlusTheMargin() {
        EnemyReachMemory memory = new EnemyReachMemory();
        int zealotCap = UnitType.Protoss_Zealot.groundWeapon().maxRange() + EnemyReachMemory.MEASUREMENT_MARGIN;
        int lingCap = UnitType.Zerg_Zergling.groundWeapon().maxRange() + EnemyReachMemory.MEASUREMENT_MARGIN;

        memory.raise(UnitType.Protoss_Zealot, 584, EnemyReachMemory.Source.VISIBLE, VICTIM, 100);
        assertTrue(memory.learnFromHit(UnitType.Zerg_Zergling, 70, EnemyReachMemory.Source.VISIBLE, VICTIM, 100));

        assertEquals(zealotCap, memory.reachCap(UnitType.Protoss_Zealot));
        assertEquals(zealotCap, memory.groundReach(UnitType.Protoss_Zealot));
        assertEquals(lingCap, memory.groundReach(UnitType.Zerg_Zergling));
    }

    @Test
    void aRangedTypeIsCappedAtItsReportedRangePlusTheMargin() {
        EnemyReachMemory memory = new EnemyReachMemory();

        memory.raise(UnitType.Terran_Marine, 411, EnemyReachMemory.Source.VISIBLE, VICTIM, 100);
        assertEquals(MARINE_CAP, memory.groundReach(UnitType.Terran_Marine));

        memory.seed(UnitType.Terran_Marine, 160, 200);
        memory.raise(UnitType.Terran_Marine, 411, EnemyReachMemory.Source.VISIBLE, VICTIM, 300);
        assertEquals(160 + EnemyReachMemory.MEASUREMENT_MARGIN, memory.groundReach(UnitType.Terran_Marine));

        memory.seed(UnitType.Protoss_Dragoon, 192, 400);
        memory.raise(UnitType.Protoss_Dragoon, 385, EnemyReachMemory.Source.VISIBLE, VICTIM, 500);
        assertEquals(192 + EnemyReachMemory.MEASUREMENT_MARGIN, memory.groundReach(UnitType.Protoss_Dragoon));
    }

    @Test
    void aBunkerIsCappedAtTheMarineRangePlusTheAllowance() {
        EnemyReachMemory memory = new EnemyReachMemory();
        int bunkerCap = MARINE_CAP + EnemyReachMemory.BUNKER_ALLOWANCE;

        memory.raise(UnitType.Terran_Bunker, 287, EnemyReachMemory.Source.BULLET, VICTIM, 100);
        assertEquals(bunkerCap, memory.groundReach(UnitType.Terran_Bunker));

        memory.seed(UnitType.Terran_Marine, 160, 200);
        assertEquals(bunkerCap + 160 - MARINE_RANGE, memory.reachCap(UnitType.Terran_Bunker),
                "a longer Marine range reported by the owner raises the cap of the Bunker it garrisons");
        memory.raise(UnitType.Terran_Bunker, 287, EnemyReachMemory.Source.BULLET, VICTIM, 300);
        assertEquals(bunkerCap + 160 - MARINE_RANGE, memory.groundReach(UnitType.Terran_Bunker));
    }

    @Test
    void aTypeWithNoGroundWeaponNeverLearns() {
        EnemyReachMemory memory = new EnemyReachMemory();

        memory.seed(UnitType.Terran_Medic, 96, 100);
        assertFalse(memory.raise(UnitType.Terran_Medic, 96, EnemyReachMemory.Source.VISIBLE, VICTIM, 100));
        assertFalse(memory.learnFromBystanders(Collections.singletonList(
                new EnemyReachMemory.Bystander(UnitType.Terran_Medic, 40)), VICTIM, 100));

        assertEquals(0, memory.reachCap(UnitType.Terran_Medic));
        assertEquals(0, memory.groundReach(UnitType.Terran_Medic));
        assertEquals(224, memory.groundReach(UnitType.Terran_Missile_Turret, 224),
                "a table reach for a structure with no ground weapon still passes through");
    }

    @Test
    void aRiseCutToTheCapIsReportedAsCapped() {
        List<String> rows = new ArrayList<>();
        ReachTelemetry.register((frame, type, oldReach, newReach, source, victim, capped) ->
                rows.add(newReach + ":" + capped));
        EnemyReachMemory memory = new EnemyReachMemory();

        memory.raise(UnitType.Terran_Marine, MARINE_RANGE + 8, EnemyReachMemory.Source.VISIBLE, VICTIM, 100);
        memory.raise(UnitType.Terran_Marine, 411, EnemyReachMemory.Source.VISIBLE, VICTIM, 200);
        memory.raise(UnitType.Terran_Marine, 500, EnemyReachMemory.Source.VISIBLE, VICTIM, 300);

        assertEquals(2, rows.size());
        assertEquals((MARINE_RANGE + 8) + ":false", rows.get(0));
        assertEquals(MARINE_CAP + ":true", rows.get(1));
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

        assertTrue(memory.learnFromHit(UnitType.Terran_Marine, MARINE_RANGE + 10,
                EnemyReachMemory.Source.VISIBLE, VICTIM, 100));
        assertEquals(MARINE_RANGE + 10, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void anAttributableHitPastTheCapRaisesReachOnlyToTheCap() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertTrue(memory.learnFromHit(UnitType.Terran_Marine, MARINE_RANGE + 40,
                EnemyReachMemory.Source.VISIBLE, VICTIM, 100));
        assertEquals(MARINE_CAP, memory.groundReach(UnitType.Terran_Marine),
                "the hit is still explained, so no hurt mark is left, but reach stops at the cap");
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

    private static EnemyReachMemory.Bystander marineAt(int distance) {
        return new EnemyReachMemory.Bystander(UnitType.Terran_Marine, distance);
    }

    @Test
    void aSoleBystanderBeyondItsReachIsCreditedWithTheHit() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertTrue(memory.learnFromBystanders(Collections.singletonList(marineAt(MARINE_RANGE + 10)), VICTIM, 100));
        assertEquals(MARINE_RANGE + 10, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void twoBystandersBeyondTheirReachTeachNothing() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertFalse(memory.learnFromBystanders(Arrays.asList(marineAt(MARINE_RANGE + 40),
                marineAt(MARINE_RANGE + 57)), VICTIM, 100));
        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Marine),
                "with two candidates the shooter is unknown, so a hurt mark is recorded instead");
    }

    @Test
    void aBystanderFarBeyondItsReachIsNotACandidate() {
        EnemyReachMemory memory = new EnemyReachMemory();
        int tooFar = MARINE_RANGE + EnemyReachMemory.MAX_LEARN_STEP + 1;

        assertFalse(memory.learnFromBystanders(Collections.singletonList(marineAt(tooFar)), VICTIM, 100));
        assertTrue(memory.learnFromBystanders(Arrays.asList(marineAt(tooFar), marineAt(MARINE_RANGE + 10)),
                VICTIM, 100));
        assertEquals(MARINE_RANGE + 10, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void aBystanderWithinItsReachExplainsTheHitWithoutTeaching() {
        EnemyReachMemory memory = new EnemyReachMemory();

        assertTrue(memory.learnFromBystanders(Arrays.asList(marineAt(MARINE_RANGE - 20),
                marineAt(MARINE_RANGE + 40)), VICTIM, 100));
        assertEquals(MARINE_RANGE, memory.groundReach(UnitType.Terran_Marine));
    }

    @Test
    void noBystanderLeavesTheHitUnexplained() {
        assertFalse(new EnemyReachMemory().learnFromBystanders(Collections.emptyList(), VICTIM, 100));
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
        ReachTelemetry.register((frame, type, oldReach, newReach, source, victim, capped) ->
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
