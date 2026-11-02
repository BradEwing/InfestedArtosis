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

    @Test
    void withNoAntiAirThreatNearTheMutaliskStaysOnItsTarget() {
        List<CooldownKite.Threat> near = CooldownKite.near(Collections.emptyList(), SELF);

        assertTrue(near.isEmpty());
        assertNull(CooldownKite.backOffPoint(SELF, TARGET, TARGET_REACH, near, 500, MAP, MAP));
        assertEquals(CooldownKite.Step.ATTACK, CooldownKite.step(false, 20, null, false, false));
    }

    @Test
    void aThreatWhoseReachDoesNotCoverTheMutaliskIsNotNear() {
        CooldownKite.Threat far = new CooldownKite.Threat(new Position(2300, 2000), 200);

        assertTrue(CooldownKite.near(Collections.singletonList(far), SELF).isEmpty());
    }

    @Test
    void theBackOffPointLiesJustOutsideTheReachAndCanBeFlownBackFromInTime() {
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(2150, 2000), 200);
        List<CooldownKite.Threat> near = CooldownKite.near(Collections.singletonList(spore), SELF);
        double budget = 200;

        Position point = CooldownKite.backOffPoint(SELF, TARGET, TARGET_REACH, near, budget, MAP, MAP);

        assertNotNull(point);
        double fromThreat = point.getDistance(spore.getPosition());
        assertTrue(fromThreat >= 200 + CooldownKite.MARGIN - 1, "outside the reach plus margin: " + fromThreat);
        assertTrue(fromThreat <= 200 + CooldownKite.MARGIN + CooldownKite.SHORTEN_STEP + 1, "just outside: " + fromThreat);
        assertTrue(CooldownKite.outAndBack(SELF, point, TARGET, TARGET_REACH) <= budget);
    }

    @Test
    void aBackOffTooLongToFlyBackFromInTheCooldownIsShortened() {
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(2100, 2000), 200);
        List<CooldownKite.Threat> near = CooldownKite.near(Collections.singletonList(spore), SELF);
        double budget = 40;

        Position point = CooldownKite.backOffPoint(SELF, TARGET, TARGET_REACH, near, budget, MAP, MAP);

        assertNotNull(point);
        assertTrue(CooldownKite.outAndBack(SELF, point, TARGET, TARGET_REACH) <= budget);
        assertTrue(point.getDistance(spore.getPosition()) > SELF.getDistance(spore.getPosition()));
    }

    @Test
    void withNoCooldownLeftToFlyThereIsNoBackOff() {
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(2100, 2000), 200);
        List<CooldownKite.Threat> near = CooldownKite.near(Collections.singletonList(spore), SELF);

        assertNull(CooldownKite.backOffPoint(SELF, TARGET, TARGET_REACH, near, 0, MAP, MAP));
    }

    @Test
    void theBackOffLeavesTheWidestReach() {
        CooldownKite.Threat hydra = new CooldownKite.Threat(new Position(2060, 2000), 140);
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(1950, 2100), 240);
        List<CooldownKite.Threat> near = CooldownKite.near(Arrays.asList(hydra, spore), SELF);

        Position point = CooldownKite.backOffPoint(SELF, TARGET, TARGET_REACH, near, 1000, MAP, MAP);

        assertNotNull(point);
        assertTrue(spore.margin(point) >= CooldownKite.MARGIN - 1);
        assertTrue(hydra.margin(point) >= CooldownKite.MARGIN - 1);
        assertSame(spore, CooldownKite.widest(near));
    }

    @Test
    void aBackOffPointIsKeptInsideTheMap() {
        Position self = new Position(150, 2000);
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(250, 2000), 200);
        List<CooldownKite.Threat> near = CooldownKite.near(Collections.singletonList(spore), self);

        Position point = CooldownKite.backOffPoint(self, new Position(150, 1900), TARGET_REACH, near, 1000, MAP, MAP);

        assertNotNull(point);
        assertTrue(MapEdge.inside(point, MAP, MAP, MapEdge.INSET));
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
    void theBackOffIsPlannedAgainForEveryShot() {
        CooldownKite kite = new CooldownKite();
        CooldownKite.Threat spore = new CooldownKite.Threat(new Position(2100, 2000), 200);

        assertTrue(kite.begin(100));
        kite.plan(new Position(1800, 2000), spore);
        assertFalse(kite.begin(100));
        assertTrue(kite.isPlanned());
        assertEquals(new Position(1800, 2000), kite.getPoint());

        assertTrue(kite.begin(131));
        assertFalse(kite.isPlanned());
        assertNull(kite.getPoint());
        assertNull(kite.getThreat());
    }

    @Test
    void turningBackDropsThePointForTheRestOfTheCooldown() {
        CooldownKite kite = new CooldownKite();
        kite.begin(100);
        kite.plan(new Position(1800, 2000), null);

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
}
