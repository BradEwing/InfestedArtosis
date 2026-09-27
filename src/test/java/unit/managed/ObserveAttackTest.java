package unit.managed;

import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserveAttackTest {

    private static final int DAMAGE_PER_HIT = 5;

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

    private ManagedUnit zerglingHitting(Unit unit, Unit target) {
        return new Zergling(units.game(), unit, UnitRole.FIGHT, null) {
            @Override
            protected Unit weaponTarget() {
                return target;
            }

            @Override
            protected int damagePerHit(Unit hit) {
                return hit == target ? DAMAGE_PER_HIT : 0;
            }
        };
    }

    private static void swingEvery(TestUnits units, Unit unit, ManagedUnit ling, int cooldown, int fromFrame,
                                   int frames) {
        for (int i = 0; i < frames; i++) {
            units.setWeaponCooldowns(unit, i % (cooldown + 1) == 0 ? cooldown : cooldown - i % (cooldown + 1), 0);
            ling.observeAttack(fromFrame + i);
        }
    }

    @Test
    void everyWeaponCooldownResetAddsOneHitOfDamageWithoutTheStartingAttackFlag() {
        Unit unit = units.unit(UnitType.Zerg_Zergling);
        Unit marine = units.unit(UnitType.Terran_Marine);
        units.setExists(marine, true);
        ManagedUnit ling = zerglingHitting(unit, marine);
        units.setWeaponCooldowns(unit, 0, 0);
        ling.observeAttack(99);

        swingEvery(units, unit, ling, 8, 100, 27);

        assertEquals(3 * DAMAGE_PER_HIT, ling.getDamageDealt());
        assertEquals(0, ling.getAttacksStarted());
    }

    @Test
    void aCooldownAlreadyRunningOnTheFirstReadIsNotAHit() {
        Unit unit = units.unit(UnitType.Zerg_Zergling);
        Unit marine = units.unit(UnitType.Terran_Marine);
        units.setExists(marine, true);
        ManagedUnit ling = zerglingHitting(unit, marine);

        for (int cooldown = 8; cooldown >= 0; cooldown--) {
            units.setWeaponCooldowns(unit, cooldown, 0);
            ling.observeAttack(108 - cooldown);
        }

        assertEquals(0, ling.getDamageDealt());
    }

    @Test
    void aHitWithNoLiveTargetAddsNoDamage() {
        Unit unit = units.unit(UnitType.Zerg_Zergling);
        Unit marine = units.unit(UnitType.Terran_Marine);
        ManagedUnit gone = zerglingHitting(unit, marine);
        units.setWeaponCooldowns(unit, 0, 0);
        gone.observeAttack(99);
        units.setWeaponCooldowns(unit, 8, 0);
        gone.observeAttack(100);

        assertEquals(0, gone.getDamageDealt());
    }

    @Test
    void onlyARisingCooldownIsAHit() {
        assertTrue(ManagedUnit.weaponFired(0, 8));
        assertTrue(ManagedUnit.weaponFired(1, 8));
        assertFalse(ManagedUnit.weaponFired(8, 7));
        assertFalse(ManagedUnit.weaponFired(0, 0));
        assertFalse(ManagedUnit.weaponFired(-1, 8));
    }
}
