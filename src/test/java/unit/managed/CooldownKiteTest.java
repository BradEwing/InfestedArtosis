package unit.managed;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.MapEdge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownKiteTest {

    private static final int MAP = 4096;
    private static final Position SELF = new Position(2000, 2000);
    private static final Position TARGET = new Position(2000, 1900);
    private static final int TARGET_REACH = 110;
    private static final double MUTA_SPEED = UnitType.Zerg_Mutalisk.topSpeed();

    private static CooldownKite.Plan plan(Position self, Position target, double budget,
                                          CooldownKite.Threat... threats) {
        return CooldownKite.plan(self, target, TARGET_REACH, Arrays.asList(threats), MUTA_SPEED, budget, MAP, MAP);
    }

    private static CooldownKite.Threat mobile(int x, int y, int reach, UnitType type) {
        return new CooldownKite.Threat(new Position(x, y), reach, true, type.topSpeed());
    }

    @Test
    void withNoAntiAirThreatNearTheMutaliskStaysOnItsTarget() {
        CooldownKite.Plan plan = plan(SELF, TARGET, 500);

        assertEquals(CooldownKite.Outcome.NO_THREAT, plan.getOutcome());
        assertNull(plan.getPoint());
        assertEquals(CooldownKite.Step.ATTACK, CooldownKite.step(false, 20, null, false, false));
    }

    @Test
    void aThreatWhoseReachDoesNotCoverTheMutaliskIsNotNear() {
        CooldownKite.Threat far = new CooldownKite.Threat(new Position(2300, 2000), 200);

        assertTrue(CooldownKite.near(Collections.singletonList(far), SELF, MUTA_SPEED).isEmpty());
        assertEquals(CooldownKite.Outcome.NO_THREAT, plan(SELF, TARGET, 500, far).getOutcome());
    }

    @Test
    void theBackOffPointLiesJustOutsideTheReachAndCanBeFlownBackFromInTime() {
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(2150, 2000), 200);
        double budget = 200;

        CooldownKite.Plan plan = plan(SELF, TARGET, budget, spore);

        assertEquals(CooldownKite.Outcome.BACK_OFF, plan.getOutcome());
        Position point = plan.getPoint();
        double fromThreat = point.getDistance(spore.getPosition());
        assertTrue(fromThreat > 200, "outside the reach: " + fromThreat);
        assertTrue(fromThreat <= 200 + CooldownKite.MARGIN + 2, "just outside: " + fromThreat);
        assertTrue(CooldownKite.outAndBack(SELF, point, TARGET, TARGET_REACH) <= budget);
        assertFalse(plan.isCommitted());
    }

    @Test
    void aStaticThreatsClearingPointIsTakenEvenWhenTheFlightBackRunsPastTheCooldown() {
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(2100, 2000), 200);

        CooldownKite.Plan plan = plan(SELF, TARGET, 40, spore);

        assertEquals(CooldownKite.Outcome.BACK_OFF, plan.getOutcome());
        assertTrue(spore.margin(plan.getPoint()) > 0);
        assertTrue(plan.isCommitted());
    }

    @Test
    void aMobileThreatsBackOffThatDoesNotFitTheCooldownIsNotFlown() {
        CooldownKite.Threat marine = mobile(2100, 2000, 160, UnitType.Terran_Marine);

        CooldownKite.Plan plan = plan(SELF, TARGET, 20, marine);

        assertEquals(CooldownKite.Outcome.NO_CLEAR, plan.getOutcome());
        assertNull(plan.getPoint());
    }

    @Test
    void aMobileThreatsBackOffThatFitsTheCooldownClearsItsReach() {
        CooldownKite.Threat marine = mobile(2100, 2000, 160, UnitType.Terran_Marine);

        CooldownKite.Plan plan = plan(SELF, TARGET, 400, marine);

        assertEquals(CooldownKite.Outcome.BACK_OFF, plan.getOutcome());
        assertTrue(marine.margin(plan.getPoint()) > 0);
        assertFalse(plan.isCommitted());
    }

    @Test
    void aThreatAsFastAsTheMutaliskIsNotCounted() {
        CooldownKite.Threat enemyMuta = mobile(2050, 2000, 140, UnitType.Zerg_Mutalisk);
        CooldownKite.Threat scourge = mobile(1980, 2000, 37, UnitType.Zerg_Scourge);

        assertFalse(enemyMuta.counts(MUTA_SPEED));
        assertFalse(scourge.counts(MUTA_SPEED));
        assertEquals(CooldownKite.Outcome.NO_THREAT, plan(SELF, TARGET, 400, enemyMuta, scourge).getOutcome());
    }

    @Test
    void opposingThreatsWithNoClearingPointWithinTheLimitMeanNoBackOff() {
        CooldownKite.Threat west = new CooldownKite.Threat(new Position(1800, 2000), 400);
        CooldownKite.Threat east = new CooldownKite.Threat(new Position(2200, 2000), 400);

        CooldownKite.Plan plan = plan(SELF, TARGET, 1000, west, east);

        assertEquals(CooldownKite.Outcome.NO_CLEAR, plan.getOutcome());
        assertNull(plan.getPoint());
    }

    @Test
    void opposingThreatsThatLeaveAGapAreClearedThroughIt() {
        CooldownKite.Threat west = new CooldownKite.Threat(new Position(1800, 2000), 250);
        CooldownKite.Threat east = new CooldownKite.Threat(new Position(2200, 2000), 250);

        CooldownKite.Plan plan = plan(SELF, TARGET, 1000, west, east);

        assertEquals(CooldownKite.Outcome.BACK_OFF, plan.getOutcome());
        assertTrue(CooldownKite.clearsAll(plan.getPoint(), Arrays.asList(west, east)));
    }

    @Test
    void aBackOffTheMapEdgeLeavesInsideTheReachIsNotFlown() {
        Position corner = new Position(100, 100);
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(300, 300), 420);

        CooldownKite.Plan plan = plan(corner, new Position(150, 150), 1000, spore);

        assertEquals(CooldownKite.Outcome.NO_CLEAR, plan.getOutcome());
        assertNull(plan.getPoint());
    }

    @Test
    void aBackOffPointIsKeptInsideTheMap() {
        Position self = new Position(150, 2000);
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(250, 2000), 200);

        CooldownKite.Plan plan = plan(self, new Position(150, 1900), 1000, spore);

        assertEquals(CooldownKite.Outcome.BACK_OFF, plan.getOutcome());
        assertTrue(MapEdge.inside(plan.getPoint(), MAP, MAP, MapEdge.INSET));
        assertTrue(spore.margin(plan.getPoint()) > 0);
    }

    @Test
    void theBackOffLeavesEveryNearReachAndReportsTheWidest() {
        CooldownKite.Threat hydra = mobile(2060, 2000, 140, UnitType.Zerg_Hydralisk);
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(1950, 2100), 240);

        CooldownKite.Plan plan = plan(SELF, TARGET, 1000, hydra, spore);

        assertEquals(CooldownKite.Outcome.BACK_OFF, plan.getOutcome());
        assertTrue(spore.margin(plan.getPoint()) > 0);
        assertTrue(hydra.margin(plan.getPoint()) > 0);
        assertSame(spore, plan.getWidest());
    }

    @Test
    void theMutaliskTurnsBackWhenTheCooldownLeftOnlyCoversTheFlightBack() {
        Position out = new Position(2000, 2100);
        double back = out.getDistance(TARGET) - TARGET_REACH;
        int framesForFlightBack = (int) Math.ceil(back / (MUTA_SPEED * CooldownKite.SPEED_SHARE));

        assertFalse(CooldownKite.mustTurnBack(out, TARGET, TARGET_REACH, MUTA_SPEED, framesForFlightBack + 2));
        assertTrue(CooldownKite.mustTurnBack(out, TARGET, TARGET_REACH, MUTA_SPEED, framesForFlightBack - 1));
    }

    @Test
    void theStepFollowsTheAttackFrameTheCooldownAndThePlan() {
        Position point = new Position(1, 1);

        assertEquals(CooldownKite.Step.WAIT, CooldownKite.step(true, 20, point, false, false));
        assertEquals(CooldownKite.Step.ATTACK, CooldownKite.step(false, 0, point, false, false));
        assertEquals(CooldownKite.Step.ATTACK, CooldownKite.step(false, 20, point, false, true));
        assertEquals(CooldownKite.Step.HOLD, CooldownKite.step(false, 20, point, true, false));
        assertEquals(CooldownKite.Step.BACK_OFF, CooldownKite.step(false, 20, point, false, false));
    }

    @Test
    void theBackOffIsPlannedAgainForEveryShotRoleAndTarget() {
        CooldownKite kite = new CooldownKite();
        CooldownKite.Plan backOff = plan(SELF, TARGET, 1000, new CooldownKite.Threat(new Position(2100, 2000), 200));

        assertTrue(kite.begin(100, UnitRole.FIGHT, 7));
        kite.accept(backOff);
        assertFalse(kite.begin(100, UnitRole.FIGHT, 7));
        assertTrue(kite.isPlanned());
        assertNotNull(kite.getPoint());

        assertTrue(kite.begin(131, UnitRole.FIGHT, 7));
        assertFalse(kite.isPlanned());
        assertNull(kite.getPoint());
        assertNull(kite.getThreat());

        kite.accept(backOff);
        assertTrue(kite.begin(131, UnitRole.FIGHT, 8));
        assertFalse(kite.isPlanned());

        kite.accept(backOff);
        assertTrue(kite.begin(131, UnitRole.HARASS, 8));
        assertFalse(kite.isPlanned());
    }

    @Test
    void turningBackDropsThePointForTheRestOfTheCooldown() {
        CooldownKite kite = new CooldownKite();
        kite.begin(100, UnitRole.FIGHT, 7);
        kite.accept(plan(SELF, TARGET, 1000, new CooldownKite.Threat(new Position(2100, 2000), 200)));

        kite.turnBack();

        assertNull(kite.getPoint());
        assertTrue(kite.isPlanned());
    }

    @Test
    void aThreatsReachAddsBothUnitsLargestHalfExtentsToItsRange() {
        int muta = CooldownKite.largestHalfExtent(UnitType.Zerg_Mutalisk);
        int spore = CooldownKite.largestHalfExtent(UnitType.Zerg_Spore_Colony);

        assertEquals(224 + spore + muta, CooldownKite.reach(224, spore, muta));
    }

    @Test
    void nearIgnoresThreatsThatDoNotCount() {
        List<CooldownKite.Threat> near = CooldownKite.near(Arrays.asList(
                mobile(2050, 2000, 140, UnitType.Zerg_Mutalisk),
                mobile(2050, 2000, 140, UnitType.Zerg_Hydralisk)), SELF, MUTA_SPEED);

        assertEquals(1, near.size());
    }

    @Test
    void onlyAStaticThreatNearTheMutaliskCallsForAReplan() {
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(2100, 2000), 200);
        CooldownKite.Threat farSpore = new CooldownKite.Threat(new Position(2400, 2000), 200);
        CooldownKite.Threat hydra = mobile(2050, 2000, 150, UnitType.Zerg_Hydralisk);

        assertTrue(CooldownKite.staticThreatNear(Collections.singletonList(spore), SELF));
        assertFalse(CooldownKite.staticThreatNear(Collections.singletonList(farSpore), SELF));
        assertFalse(CooldownKite.staticThreatNear(Collections.singletonList(hydra), SELF));
        assertFalse(CooldownKite.staticThreatNear(Collections.emptyList(), SELF));
    }

    @Test
    void theAcceptedPlansOutcomeIsKeptUntilTheCooldownIsDropped() {
        CooldownKite kite = new CooldownKite();
        kite.begin(100, "FIGHT", 7);
        assertNull(kite.getOutcome());

        kite.accept(plan(SELF, TARGET, 500));
        assertEquals(CooldownKite.Outcome.NO_THREAT, kite.getOutcome());

        kite.reset();
        assertNull(kite.getOutcome());
    }
}
