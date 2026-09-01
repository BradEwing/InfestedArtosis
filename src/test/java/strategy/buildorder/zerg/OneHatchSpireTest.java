package strategy.buildorder.zerg;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OneHatchSpireTest {

    @Test
    void derivesTheDroneAlongsideTheMutalisk() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(false, true, false, true);
        assertEquals(Arrays.asList(UnitType.Zerg_Mutalisk, UnitType.Zerg_Drone), unitTypes);
    }

    @Test
    void derivesTheZerglingAlongsideTheScourge() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(true, false, true, false);
        assertEquals(Arrays.asList(UnitType.Zerg_Scourge, UnitType.Zerg_Zergling), unitTypes);
    }

    @Test
    void derivesTheZerglingAheadOfTheDrone() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(false, true, true, true);
        assertEquals(Arrays.asList(UnitType.Zerg_Mutalisk, UnitType.Zerg_Zergling), unitTypes);
    }

    @Test
    void derivesTheDroneWithoutAnySpireUnit() {
        List<UnitType> unitTypes = OneHatchSpire.unitsToPlan(false, false, false, true);
        assertEquals(Collections.singletonList(UnitType.Zerg_Drone), unitTypes);
    }

    @Test
    void derivesNothingOnceEveryCountIsMet() {
        assertEquals(Collections.emptyList(), OneHatchSpire.unitsToPlan(false, false, false, false));
    }
}
