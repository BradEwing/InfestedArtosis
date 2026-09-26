package unit.managed;

import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObserveAttackTest {

    private final TestUnits units = new TestUnits();

    private ManagedUnit zergling(Unit unit) {
        return new Zergling(units.game(), unit, UnitRole.FIGHT, null);
    }

    @Test
    void everyMeleeSwingTheServerFlagsIsCountedOnce() {
        Unit unit = units.unit(UnitType.Zerg_Zergling);
        ManagedUnit ling = zergling(unit);
        int[] swings = {100, 108, 116, 123};

        int swing = 0;
        for (int frame = 96; frame <= 130; frame++) {
            boolean starting = swing < swings.length && swings[swing] == frame;
            units.setStartingAttack(unit, starting);
            ling.observeAttack(frame);
            swing += starting ? 1 : 0;
        }

        assertEquals(swings.length, ling.getAttacksStarted());
        assertEquals(123, ling.getLastAttackStartFrame());
    }

    @Test
    void aMeleeUnitThatNeverSwingsCountsNoAttack() {
        Unit unit = units.unit(UnitType.Zerg_Zergling);
        ManagedUnit ling = zergling(unit);

        for (int frame = 0; frame < 48; frame++) {
            ling.observeAttack(frame);
        }

        assertEquals(0, ling.getAttacksStarted());
        assertEquals(-1, ling.getLastAttackStartFrame());
    }

    @Test
    void aMeleeSwingIsCountedLikeARangedShot() {
        Unit lingUnit = units.unit(UnitType.Zerg_Zergling);
        Unit hydraUnit = units.unit(UnitType.Zerg_Hydralisk);
        ManagedUnit ling = zergling(lingUnit);
        ManagedUnit hydra = new Hydralisk(units.game(), hydraUnit, UnitRole.FIGHT, null);

        units.setStartingAttack(lingUnit, true);
        units.setStartingAttack(hydraUnit, true);
        ling.observeAttack(200);
        hydra.observeAttack(200);

        assertEquals(hydra.getAttacksStarted(), ling.getAttacksStarted());
        assertEquals(1, ling.getAttacksStarted());
    }
}
