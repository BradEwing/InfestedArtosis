package unit.squad;

import bwapi.Race;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuardianMoveOutTest {

    private static final double AT_HOME = 70;

    private static Map<UnitType, Integer> composition(UnitType type, int count) {
        Map<UnitType, Integer> composition = new HashMap<>();
        composition.put(type, count);
        return composition;
    }

    private static SquadManager.SquadAction launchFromRally(Map<UnitType, Integer> composition, Race opponent) {
        return SquadManager.chooseSquadAction(false, SquadManager.airMoveOutUnits(composition),
                SquadManager.airMoveOutThreshold(composition, opponent), SquadStatus.RALLY, false, AT_HOME);
    }

    @Test
    void threeGuardiansLaunchAgainstTerran() {
        assertEquals(SquadManager.SquadAction.RALLY,
                launchFromRally(composition(UnitType.Zerg_Guardian, 2), Race.Terran));
        assertEquals(SquadManager.SquadAction.LAUNCH,
                launchFromRally(composition(UnitType.Zerg_Guardian, 3), Race.Terran));
    }

    @Test
    void aSquadWithoutAGuardianKeepsItsThreshold() {
        assertEquals(SquadManager.AIR_MOVE_OUT_UNITS,
                SquadManager.airMoveOutThreshold(composition(UnitType.Zerg_Mutalisk, 4), Race.Terran));
        assertEquals(SquadManager.SCOURGE_MOVE_OUT_UNITS,
                SquadManager.airMoveOutThreshold(composition(UnitType.Zerg_Scourge, 2), Race.Terran));
    }

    @Test
    void aGuardianNeverRaisesAThresholdAlreadyBelowItsOwn() {
        assertEquals(SquadManager.AIR_MOVE_OUT_UNITS_VS_ZERG,
                SquadManager.airMoveOutThreshold(composition(UnitType.Zerg_Guardian, 3), Race.Zerg));
    }
}
