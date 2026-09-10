package strategy.buildorder.terran;

import org.junit.jupiter.api.Test;
import strategy.buildorder.SunkenTargets;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerranBaseTest {

    private static final int DRONES_AT_POOL_COMPLETION = 10;
    private static final int UNSCOUTED_ZERGLING_FLOOR = TerranBase.UNSCOUTED_ZERGLINGS;
    private static final int SIEGE_TANKS = 6;
    private static final int VULTURES = 3;
    private static final int FACTORIES = 2;
    private static final int MARINES = 3;
    private static final int FIREBATS = 4;
    private static final int MECH_TERM = 8;
    private static final int HUGE_MECH_TERM = 200;
    private static final int THREE_RAX = SunkenTargets.BARRACKS_PRESSURE_COUNT;
    private static final int TWO_RAX = SunkenTargets.BARRACKS_PRESSURE_COUNT - 1;
    private static final int NO_BARRACKS_SEEN = 0;
    private static final int BIO_BALL = 14;
    private static final int SMALL_BIO = TerranBase.EARLY_BIO_PRESSURE_BIO;
    private static final boolean TWO_RAX_ACADEMY = true;
    private static final boolean NO_TWO_RAX_ACADEMY = false;
    private static final boolean ONE_BASE = true;
    private static final Time BEFORE_THE_OLD_BIO_CLIFF = new Time(4, 30);
    private static final Time AFTER_THE_OLD_BIO_CLIFF = new Time(5, 1);
    private static final Time AFTER_THE_TWO_RAX_WINDOW = new Time(8, 1);
    private static final Time INSIDE_THE_TWO_RAX_WINDOW = new Time(5, 0);
    private static final Time BEFORE_THE_TWO_RAX_WINDOW = new Time(3, 30);

    @Test
    void yieldsTheLarvaToZerglingsOnThePoolCompletionFrame() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 0, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void keepsYieldingWhileTheZerglingFloorIsUnmet() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 2, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void resumesDroningOnceTheZerglingFloorIsMet() {
        assertTrue(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void dronesFreelyWhenNoZerglingsAreOwed() {
        assertTrue(TerranBase.shouldDroneBeforeZerglings(DRONES_AT_POOL_COMPLETION, 0, 0));
    }

    @Test
    void stopsTheEarlyDroneGateAtTheTarget() {
        assertFalse(TerranBase.shouldDroneBeforeZerglings(TerranBase.EARLY_DRONE_TARGET, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void keepsDroningWhenParkedDronesInflateTheLivingCount() {
        int gatheringDrones = TerranBase.EARLY_DRONE_TARGET - 1;
        int livingDrones = gatheringDrones + 6;

        assertFalse(TerranBase.shouldDroneBeforeZerglings(livingDrones, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
        assertTrue(TerranBase.shouldDroneBeforeZerglings(gatheringDrones, UNSCOUTED_ZERGLING_FLOOR, UNSCOUTED_ZERGLING_FLOOR));
    }

    @Test
    void asksForNothingExtraAgainstAnEnemyWithNoMech() {
        assertEquals(0, TerranBase.mechDrivenZerglings(0, 0, 0, 0));
    }

    @Test
    void growsWithEachMechUnitTypeItCanSee() {
        int tanks = TerranBase.mechDrivenZerglings(1, 0, 0, 0);
        int vultures = TerranBase.mechDrivenZerglings(0, 1, 0, 0);
        int goliaths = TerranBase.mechDrivenZerglings(0, 0, 1, 0);

        assertTrue(tanks > 0);
        assertTrue(vultures > 0);
        assertTrue(goliaths > 0);
        assertTrue(TerranBase.mechDrivenZerglings(2, 2, 2, 0) > TerranBase.mechDrivenZerglings(1, 1, 1, 0));
    }

    @Test
    void asksForZerglingsOffAScoutedFactoryBeforeItsUnitsAreSeen() {
        assertTrue(TerranBase.mechDrivenZerglings(0, 0, 0, FACTORIES) > 0);
        assertTrue(TerranBase.mechDrivenZerglings(0, 0, 0, FACTORIES + 1)
                > TerranBase.mechDrivenZerglings(0, 0, 0, FACTORIES));
    }

    @Test
    void doesNotCountAFactoryOnTopOfTheUnitsItAlreadyBuilt() {
        int fromUnits = TerranBase.mechDrivenZerglings(SIEGE_TANKS, VULTURES, 0, 0);

        assertEquals(fromUnits, TerranBase.mechDrivenZerglings(SIEGE_TANKS, VULTURES, 0, FACTORIES));
    }

    @Test
    void scalesTheTargetWithMechWhenEveryBioTermIsZero() {
        int mechDriven = TerranBase.mechDrivenZerglings(SIEGE_TANKS, VULTURES, 0, FACTORIES);
        int target = TerranBase.zerglingTarget(0, 0, 0, 0, 0, mechDriven, true);

        assertTrue(target > UNSCOUTED_ZERGLING_FLOOR);
        assertTrue(target > TerranBase.MECH_ZERGLING_FLOOR);
    }

    @Test
    void neverSitsAtTheUnscoutedFloorOnceTheArmyReadsAsMech() {
        assertEquals(TerranBase.MECH_ZERGLING_FLOOR, TerranBase.zerglingTarget(0, 0, 0, 0, 0, 0, true));
    }

    @Test
    void staysAtTheUnscoutedFloorAgainstAnEnemyThatIsNotMech() {
        assertEquals(UNSCOUTED_ZERGLING_FLOOR, TerranBase.zerglingTarget(0, 0, 0, 0, 0, 0, false));
        assertEquals(UNSCOUTED_ZERGLING_FLOOR + 2 * MARINES,
                TerranBase.zerglingTarget(0, 0, 0, MARINES, 0, 0, false));
    }

    @Test
    void addsTheBioAndMechTermsTogetherAgainstAnEnemyHoldingBoth() {
        int bioOnly = TerranBase.zerglingTarget(0, 0, 0, MARINES, 0, 0, false);
        int both = TerranBase.zerglingTarget(0, 0, 0, MARINES, 0, MECH_TERM, true);

        assertEquals(bioOnly + MECH_TERM, both);
    }

    @Test
    void neverAsksForANegativeTarget() {
        assertEquals(0, TerranBase.zerglingTarget(0, 0, FIREBATS, 0, 0, 0, false));
    }

    @Test
    void capsTheTargetAtTheArmySizeTheBuildWillCommitTo() {
        assertEquals(TerranBase.MAX_ZERGLINGS, TerranBase.zerglingTarget(0, 0, 0, 0, 0, HUGE_MECH_TERM, true));
    }

    @Test
    void reportsZeroOnceTheTargetIsMetSoTheBuildStopsQueueing() {
        int target = TerranBase.zerglingTarget(0, 0, 0, MARINES, 0, MECH_TERM, true);

        assertEquals(0, TerranBase.zerglingTarget(target, 0, 0, MARINES, 0, MECH_TERM, true));
        assertTrue(TerranBase.zerglingTarget(target - 1, 0, 0, MARINES, 0, MECH_TERM, true) > 0);
    }

    @Test
    void asksForThreeSunkensOffThreeObservedBarracks() {
        assertEquals(TerranBase.BARRACKS_PRESSURE_SUNKENS,
                TerranBase.bioPressureSunkens(THREE_RAX, 0, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF));
    }

    @Test
    void keepsTheBarracksAnswerForAsLongAsTheBarracksStand() {
        assertEquals(TerranBase.BARRACKS_PRESSURE_SUNKENS,
                TerranBase.bioPressureSunkens(THREE_RAX, BIO_BALL, NO_TWO_RAX_ACADEMY, AFTER_THE_TWO_RAX_WINDOW));
    }

    @Test
    void lowersTheBarracksAnswerOnlyWhenTheBarracksThemselvesAreGone() {
        assertEquals(TerranBase.BARRACKS_PRESSURE_SUNKENS,
                TerranBase.bioPressureSunkens(THREE_RAX, 0, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF));
        assertEquals(0, TerranBase.bioPressureSunkens(TWO_RAX, 0, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF));
    }

    @Test
    void letsTheBarracksReadWinOverTheLowerBioBranches() {
        int barracksAndBio = TerranBase.bioPressureSunkens(THREE_RAX, BIO_BALL, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF);
        int bioAlone = TerranBase.bioPressureSunkens(NO_BARRACKS_SEEN, BIO_BALL, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF);

        assertEquals(TerranBase.BARRACKS_PRESSURE_SUNKENS, barracksAndBio);
        assertTrue(barracksAndBio > bioAlone);
    }

    @Test
    void doesNotDropTheBioAnswerToZeroAtFiveMinutes() {
        assertTrue(TerranBase.bioPressureSunkens(NO_BARRACKS_SEEN, BIO_BALL, NO_TWO_RAX_ACADEMY, BEFORE_THE_OLD_BIO_CLIFF) > 0);
        assertTrue(TerranBase.bioPressureSunkens(NO_BARRACKS_SEEN, BIO_BALL, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF) > 0);
        assertTrue(TerranBase.bioPressureSunkens(NO_BARRACKS_SEEN, BIO_BALL, NO_TWO_RAX_ACADEMY, AFTER_THE_TWO_RAX_WINDOW) > 0);
    }

    @Test
    void asksForNothingUntilTheBioItCanSeeClearsTheStep() {
        assertEquals(0, TerranBase.bioPressureSunkens(NO_BARRACKS_SEEN, SMALL_BIO, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF));
        assertEquals(TerranBase.EARLY_BIO_PRESSURE_SUNKENS,
                TerranBase.bioPressureSunkens(NO_BARRACKS_SEEN, SMALL_BIO + 1, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF));
    }

    @Test
    void answersADetectedTwoRaxAcademyInsideItsWindow() {
        assertEquals(TerranBase.TWO_RAX_ACADEMY_SUNKENS,
                TerranBase.bioPressureSunkens(TWO_RAX, 0, TWO_RAX_ACADEMY, INSIDE_THE_TWO_RAX_WINDOW));
        assertEquals(0, TerranBase.bioPressureSunkens(TWO_RAX, 0, TWO_RAX_ACADEMY, BEFORE_THE_TWO_RAX_WINDOW));
        assertEquals(0, TerranBase.bioPressureSunkens(TWO_RAX, 0, TWO_RAX_ACADEMY, AFTER_THE_TWO_RAX_WINDOW));
    }

    @Test
    void holdsTheBarracksAnswerAboveTheOneBaseFloorWhenBothFire() {
        int matchup = TerranBase.bioPressureSunkens(THREE_RAX, BIO_BALL, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF);

        assertEquals(TerranBase.BARRACKS_PRESSURE_SUNKENS,
                SunkenTargets.sunkenTarget(matchup, ONE_BASE, AFTER_THE_OLD_BIO_CLIFF));
    }

    @Test
    void raisesAQuietMatchupToTheOneBaseFloorWhenBothFire() {
        int matchup = TerranBase.bioPressureSunkens(TWO_RAX, 0, NO_TWO_RAX_ACADEMY, AFTER_THE_OLD_BIO_CLIFF);

        assertEquals(0, matchup);
        assertTrue(SunkenTargets.sunkenTarget(matchup, ONE_BASE, AFTER_THE_OLD_BIO_CLIFF) >= 2);
    }
}
