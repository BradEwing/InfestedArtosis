package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import bwapi.WeaponType;
import org.junit.jupiter.api.Test;
import telemetry.HarassRow;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirHarassControllerExitRowTest {

    private static final Position ANCHOR = new Position(2000, 2000);

    private static ExposedTargets.Group group(Position anchor) {
        return new ExposedTargets.Group(anchor, 1, 1, Collections.emptySet());
    }

    private static List<AirHarassTargeting.AirThreat> turretAt(int x, int y) {
        UnitType turret = UnitType.Terran_Missile_Turret;
        return Collections.singletonList(AirHarassTargeting.AirThreat.of(20, turret, new Position(x, y),
                AirHarassTargeting.airRange(turret, WeaponType::maxRange)));
    }

    @Test
    void theExitRowCarriesTheAntiAirAtTheStrikePointAndAtTheFlock() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(group(ANCHOR), 12000);
        List<AirHarassTargeting.AirThreat> threats = turretAt(2100, 2000);
        Position center = new Position(2050, 2000);

        HarassRow row = AirHarassController.exitRow("squad-1", state,
                AirHarassEvaluator.ExitReason.STRIKE_DEFENDED, 12300, center, 900, threats).build();

        assertEquals(HarassRow.Event.EXIT, row.getEvent());
        assertEquals(AirHarassEvaluator.ExitReason.STRIKE_DEFENDED, row.getExitReason());
        assertTrue(row.getAirDefense() > 0);
        assertEquals(AirHarassTargeting.defenseAt(threats, ANCHOR, AirHarassEvaluator.STRIKE_RADIUS),
                row.getAirDefense(), 1e-9);
        assertEquals(AirHarassTargeting.defenseAt(threats, center, 0), row.getFlockDefense(), 1e-9);
        assertEquals(HarassRow.TargetKind.EXPOSED, row.getTargetKind());
        assertNull(row.getBase());
        assertEquals(ANCHOR, row.getStrikePoint());
    }

    @Test
    void anExposedExitRowMeasuresTheGroupWhereItWasLastFollowedNotTheStaleStrikePoint() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(group(ANCHOR), 12000);
        ExposedTargets.Group moved = group(new Position(2600, 2000));
        state.follow(moved);
        List<AirHarassTargeting.AirThreat> threats = turretAt(2900, 2000);

        HarassRow row = AirHarassController.exitRow("squad-1", state,
                AirHarassEvaluator.ExitReason.STRIKE_DEFENDED, 12300, moved.getAnchor(), 900, threats).build();

        assertEquals(ANCHOR, state.getStrikePoint());
        assertEquals(0, AirHarassTargeting.defenseAt(threats, ANCHOR, AirHarassEvaluator.STRIKE_RADIUS), 1e-9);
        assertTrue(row.getAirDefense() > 0);
        assertEquals(ExposedTargets.defenseAt(moved, threats), row.getAirDefense(), 1e-9);
    }

    @Test
    void anExposedExitRowReadsTheExposureMeasureTheExitCompared() {
        int flock = 10;
        AirHarassTargeting.Contact goliath = new AirHarassTargeting.Contact(5, UnitType.Terran_Goliath, ANCHOR,
                UnitType.Terran_Goliath.maxHitPoints(), 1.0);
        ExposedTargets.Group group = ExposedTargets.groups(Collections.singletonList(goliath), flock,
                Collections.emptyList()).get(0);
        List<AirHarassTargeting.AirThreat> threats = Collections.singletonList(AirHarassTargeting.AirThreat.of(5,
                UnitType.Terran_Goliath, ANCHOR, AirHarassTargeting.airRange(UnitType.Terran_Goliath,
                        WeaponType::maxRange)));
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(group, 12000);

        HarassRow row = AirHarassController.exitRow("squad-1", state,
                AirHarassEvaluator.ExitReason.NO_TARGET, 12300, ANCHOR, 900, threats).build();

        assertTrue(AirHarassTargeting.defenseAt(threats, ANCHOR, AirHarassEvaluator.STRIKE_RADIUS) > 0);
        assertEquals(ExposedTargets.defenseAt(group, threats), row.getAirDefense(), 1e-9);
        assertEquals(0, row.getAirDefense(), 1e-9);
    }

    @Test
    void aBaseExitRowStillMeasuresAtTheStrikePoint() {
        AirHarassState state = new AirHarassState(12000, 1080);
        Position strike = new Position(2000, 2000);
        state.target(null, strike, 12000);
        List<AirHarassTargeting.AirThreat> threats = turretAt(2100, 2000);

        HarassRow row = AirHarassController.exitRow("squad-1", state,
                AirHarassEvaluator.ExitReason.STRIKE_DEFENDED, 12300, strike, 900, threats).build();

        assertTrue(row.getAirDefense() > 0);
        assertEquals(AirHarassTargeting.defenseAt(threats, strike, AirHarassEvaluator.STRIKE_RADIUS),
                row.getAirDefense(), 1e-9);
    }

    @Test
    void anExitRowWithNoAntiAirNearReadsZeroNotUnevaluated() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(group(ANCHOR), 12000);

        HarassRow row = AirHarassController.exitRow("squad-1", state, AirHarassEvaluator.ExitReason.NO_TARGET, 12300,
                ANCHOR, 1080, Collections.emptyList()).build();

        assertEquals(0, row.getAirDefense(), 1e-9);
        assertEquals(0, row.getFlockDefense(), 1e-9);
    }

    @Test
    void anExitRowWithNoStateOrNoFlockLeavesItsMeasuresUnevaluated() {
        HarassRow row = AirHarassController.exitRow("squad-1", null, AirHarassEvaluator.ExitReason.WIPED_OUT, 12300,
                null, 0, turretAt(2100, 2000)).build();

        assertEquals(-1, row.getAirDefense(), 1e-9);
        assertEquals(-1, row.getFlockDefense(), 1e-9);
    }
}
