package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.UnitStrength;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirApproachPricingTest {

    private static final Position FLOCK = new Position(500, 2000);
    private static final Position STRIKE = new Position(2500, 2000);
    private static final Predicate<Position> ANYWHERE = point -> true;
    private static final int MUTAS = 8;

    private static AirHarassTargeting.AirThreat threat(int id, UnitType type, Position position) {
        return AirHarassTargeting.AirThreat.of(id, type, position,
                AirHarassTargeting.airRange(type, weapon -> weapon.maxRange()));
    }

    private static Position onPath(int x) {
        return new Position(x, 2000);
    }

    private static double goliath() {
        return UnitStrength.antiAirStrength(UnitType.Terran_Goliath);
    }

    private static AirApproachPricing.Result price(List<AirHarassTargeting.AirThreat> threats, double tolerance) {
        return AirApproachPricing.price(threats, FLOCK, STRIKE, tolerance, MUTAS, ANYWHERE);
    }

    @Test
    void anApproachWithNoMobileAntiAirIsEntered() {
        AirApproachPricing.Result result = price(Collections.emptyList(), 0);

        assertEquals(AirApproachPricing.Decision.ENTER, result.getDecision());
        assertEquals(AirApproachPricing.Reason.CLEAR, result.getReason());
        assertEquals(0, result.units());
        assertEquals(MUTAS * UnitStrength.airToGround(UnitType.Zerg_Mutalisk), result.getFlockStrength(), 1e-9);
    }

    @Test
    void structuresAreNotPricedHere() {
        AirHarassTargeting.AirThreat turret = threat(1, UnitType.Terran_Missile_Turret, onPath(1500));

        AirApproachPricing.Result result = price(Collections.singletonList(turret), 0);

        assertEquals(AirApproachPricing.Decision.ENTER, result.getDecision());
        assertEquals(0, result.units());
    }

    @Test
    void mobileAntiAirWithinTheToleranceIsEnteredAndCounted() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, onPath(1500));

        AirApproachPricing.Result result = price(Collections.singletonList(goliath), goliath() * 2);

        assertEquals(AirApproachPricing.Decision.ENTER, result.getDecision());
        assertEquals(1, result.units());
        assertEquals(goliath(), result.getMobileStrength(), 1e-9);
        assertTrue(result.routeAround().isEmpty());
    }

    @Test
    void mobileAntiAirOnThePathAboveTheToleranceIsFlownAround() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, onPath(1500));

        AirApproachPricing.Result result = price(Collections.singletonList(goliath), goliath() / 2);

        assertEquals(AirApproachPricing.Decision.REROUTE, result.getDecision());
        assertEquals(AirApproachPricing.Reason.DETOUR, result.getReason());
        assertTrue(result.flies());
        assertEquals(1, result.routeAround().size());
        assertSame(goliath, result.routeAround().get(0));
    }

    @Test
    void mobileAntiAirAwayFromThePathAndTheTargetIsNotPriced() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, new Position(1500, 3500));

        AirApproachPricing.Result result = price(Collections.singletonList(goliath), 0);

        assertEquals(AirApproachPricing.Decision.ENTER, result.getDecision());
        assertEquals(0, result.units());
    }

    @Test
    void mobileAntiAirAtTheTargetAboveTheToleranceAbortsTheApproach() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, STRIKE);

        AirApproachPricing.Result result = price(Collections.singletonList(goliath), goliath() / 2);

        assertEquals(AirApproachPricing.Decision.ABORT, result.getDecision());
        assertEquals(AirApproachPricing.Reason.TARGET_DEFENDED, result.getReason());
        assertFalse(result.flies());
        assertTrue(result.routeAround().isEmpty());
    }

    @Test
    void aUnitOnThePathAndAtTheTargetIsPricedOnce() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, onPath(2400));

        AirApproachPricing.Result result = price(Collections.singletonList(goliath), goliath() * 2);

        assertEquals(1, result.units());
        assertEquals(goliath(), result.getMobileStrength(), 1e-9);
    }

    @Test
    void theTargetsDefendersAreNotFlownAroundWhenOnlyThePathMakesTheTotalTooHigh() {
        AirHarassTargeting.AirThreat atTarget = threat(1, UnitType.Terran_Goliath, STRIKE);
        AirHarassTargeting.AirThreat onTheWay = threat(2, UnitType.Terran_Goliath, onPath(1200));

        AirApproachPricing.Result result = price(Arrays.asList(atTarget, onTheWay), goliath() * 1.5);

        assertEquals(AirApproachPricing.Decision.REROUTE, result.getDecision());
        assertEquals(2, result.units());
        assertEquals(1, result.routeAround().size());
        assertSame(onTheWay, result.routeAround().get(0));
    }

    @Test
    void anApproachWithNowhereToFlyAroundIsAborted() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, onPath(1500));

        AirApproachPricing.Result result = AirApproachPricing.price(Collections.singletonList(goliath), FLOCK,
                STRIKE, goliath() / 2, MUTAS, point -> false);

        assertEquals(AirApproachPricing.Decision.ABORT, result.getDecision());
        assertEquals(AirApproachPricing.Reason.NO_DETOUR, result.getReason());
    }

    @Test
    void interceptorsAreNotMobileAntiAir() {
        AirHarassTargeting.AirThreat interceptor = threat(1, UnitType.Protoss_Interceptor, onPath(1500));
        AirHarassTargeting.AirThreat goliath = threat(2, UnitType.Terran_Goliath, onPath(1500));
        AirHarassTargeting.AirThreat turret = threat(3, UnitType.Terran_Missile_Turret, onPath(1500));

        assertFalse(AirApproachPricing.isMobile(interceptor));
        assertTrue(AirApproachPricing.isMobile(goliath));
        assertFalse(AirApproachPricing.isMobile(turret));
        assertEquals(Collections.singletonList(goliath),
                AirApproachPricing.mobile(Arrays.asList(interceptor, goliath, turret)));
        assertEquals(Arrays.asList(interceptor, turret),
                AirApproachPricing.structures(Arrays.asList(interceptor, goliath, turret)));
    }

    @Test
    void takingAnotherTargetReadsAsARerouteForThatReason() {
        AirHarassTargeting.AirThreat goliath = threat(1, UnitType.Terran_Goliath, STRIKE);
        AirApproachPricing.Result aborted = price(Collections.singletonList(goliath), 0);

        AirApproachPricing.Result swapped = aborted.toOtherTarget();

        assertEquals(AirApproachPricing.Decision.REROUTE, swapped.getDecision());
        assertEquals(AirApproachPricing.Reason.OTHER_TARGET, swapped.getReason());
        assertEquals(aborted.getMobileStrength(), swapped.getMobileStrength(), 1e-9);
        assertTrue(swapped.flies());
        assertTrue(swapped.routeAround().isEmpty());
        assertEquals("REROUTE/OTHER_TARGET", swapped.key());
    }
}
