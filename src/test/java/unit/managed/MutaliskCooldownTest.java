package unit.managed;

import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MutaliskCooldownTest {

    private final TestUnits units = new TestUnits();

    @Test
    void theKiteReadsTheCooldownTheUnitReports() {
        Unit unit = units.unit(UnitType.Zerg_Mutalisk);
        Mutalisk mutalisk = new Mutalisk(units.game(), unit, UnitRole.FIGHT, null);

        units.setWeaponCooldowns(unit, 0, 17);
        assertEquals(17, mutalisk.cooldownFramesLeft());

        units.setWeaponCooldowns(unit, 0, 0);
        assertEquals(0, mutalisk.cooldownFramesLeft());
    }

    @Test
    void theFiringFrameIsTheFrameTheCooldownRose() {
        Unit unit = units.unit(UnitType.Zerg_Mutalisk);
        Mutalisk mutalisk = new Mutalisk(units.game(), unit, UnitRole.FIGHT, null);

        units.setWeaponCooldowns(unit, 0, 0);
        mutalisk.observeAttack(99);
        units.setWeaponCooldowns(unit, 0, 30);
        mutalisk.observeAttack(100);
        units.setWeaponCooldowns(unit, 0, 29);
        mutalisk.observeAttack(101);

        assertEquals(100, mutalisk.getLastWeaponFireFrame());
    }

    @Test
    void withTheKiteOffAnAttackFrameOpensAThirtyFrameFleeWhateverTheCooldown() {
        int attackFrame = 500;
        Time until = new Time(attackFrame + Mutalisk.RETREAT_DURATION_FRAMES);

        assertEquals(30, Mutalisk.RETREAT_DURATION_FRAMES);
        assertEquals(Mutalisk.MainStep.START_RETREAT, Mutalisk.mainStep(true, attackFrame, null));
        assertEquals(Mutalisk.MainStep.FLEE, Mutalisk.mainStep(false, attackFrame + 1, until));
        assertEquals(Mutalisk.MainStep.FLEE, Mutalisk.mainStep(false, attackFrame + 29, until));
        assertEquals(Mutalisk.MainStep.ENGAGE, Mutalisk.mainStep(false, attackFrame + 30, until));
    }

    @Test
    void withTheKiteOffAMutaliskThatNeverFiredEngages() {
        assertEquals(Mutalisk.MainStep.ENGAGE, Mutalisk.mainStep(false, 10, null));
    }
}
