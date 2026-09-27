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

    private static List<AirHarassTargeting.AirThreat> turretAt(int x, int y) {
        UnitType turret = UnitType.Terran_Missile_Turret;
        return Collections.singletonList(AirHarassTargeting.AirThreat.of(20, turret, new Position(x, y),
                AirHarassTargeting.airRange(turret, WeaponType::maxRange)));
    }

    @Test
    void theExitRowCarriesTheAntiAirAtTheStrikePointAndAtTheFlock() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(ANCHOR, 12000);
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
    void anExitRowWithNoAntiAirNearReadsZeroNotUnevaluated() {
        AirHarassState state = new AirHarassState(12000, 1080);
        state.targetExposed(ANCHOR, 12000);

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
