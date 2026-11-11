package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import config.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.HorizonCombatSimulator;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricedSiegedTanksTest {

    private static final UnitType SIEGED = UnitType.Terran_Siege_Tank_Siege_Mode;
    private static final Position POCKET = new Position(1000, 1000);
    private static final Position COVERING = new Position(1200, 1000);

    private final boolean setting = Config.siegeMinRange;

    @AfterEach
    void restore() {
        Config.siegeMinRange = setting;
    }

    private static HorizonCombatSimulator.DebugSnapshot pocketSnapshot() {
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();
        snapshot.getEnemyUnits().add(new HorizonCombatSimulator.UnitDebugEntry(POCKET, SIEGED, 0, false, false));
        snapshot.getEnemyUnits().add(new HorizonCombatSimulator.UnitDebugEntry(COVERING, SIEGED, 9, false, false));
        snapshot.getSiegeHitShares().put(POCKET, 0.0);
        snapshot.getSiegeHitShares().put(COVERING, 1.0);
        return snapshot;
    }

    @Test
    void aSampledTankPricedToNothingByItsMinimumRangeStaysPriced() {
        Config.siegeMinRange = true;

        assertEquals(Arrays.asList(POCKET, COVERING), SquadManager.pricedSiegedTanks(pocketSnapshot()));
    }

    @Test
    void withTheSwitchOffOnlyATankWithStrengthIsPriced() {
        Config.siegeMinRange = false;

        assertEquals(Collections.singletonList(COVERING), SquadManager.pricedSiegedTanks(pocketSnapshot()));
    }

    @Test
    void aTankOutsideTheGroundSampleCountsByItsStrength() {
        Config.siegeMinRange = true;
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();
        snapshot.getEnemyUnits().add(new HorizonCombatSimulator.UnitDebugEntry(POCKET, SIEGED, 0, false, false));

        assertTrue(SquadManager.pricedSiegedTanks(snapshot).isEmpty());
        assertTrue(SquadManager.pricedSiegedTanks(null).isEmpty());
    }
}
