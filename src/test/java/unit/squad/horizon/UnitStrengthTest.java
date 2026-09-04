package unit.squad.horizon;

import bwapi.UnitType;
import bwapi.WeaponType;
import org.junit.jupiter.api.Test;
import unit.squad.CombatSimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitStrengthTest {

    private static final double EPSILON = 1e-9;
    private static final double PROTOSS_ENGAGE_THRESHOLD = 1.4;
    private static final double[] ENGAGE_THRESHOLDS = {1.0, 1.3, 1.4};

    /**
     * The ground strength a type carried before the effective hit point term existed: weapon damage
     * per frame scaled by the log-of-range factor, with no durability anywhere in it.
     */
    private static double damageOnlyStrength(UnitType type) {
        WeaponType weapon = type.groundWeapon();
        double dps = (double) weapon.damageAmount() * weapon.damageFactor() * type.maxGroundHits()
                / weapon.damageCooldown();
        return dps * Math.log(weapon.maxRange() / 4.0 + 16.0);
    }

    private static double effectiveHitPoints(UnitType type) {
        return type.maxHitPoints() + type.maxShields();
    }

    private static double damageOnlyRatioToZergling(UnitType type) {
        return damageOnlyStrength(type) / damageOnlyStrength(UnitType.Zerg_Zergling);
    }

    private static double durabilityRatioToZergling(UnitType type) {
        return effectiveHitPoints(type) / effectiveHitPoints(UnitType.Zerg_Zergling);
    }

    private static double strengthRatioToZergling(UnitType type) {
        return UnitStrength.groundToGround(type) / UnitStrength.groundToGround(UnitType.Zerg_Zergling);
    }

    @Test
    void zealotOutweighsZerglingByItsDurabilityGap() {
        double ratio = strengthRatioToZergling(UnitType.Protoss_Zealot);
        double damageOnly = damageOnlyRatioToZergling(UnitType.Protoss_Zealot);
        double durability = durabilityRatioToZergling(UnitType.Protoss_Zealot);

        assertTrue(ratio > 2.0 * damageOnly,
                "expected the Zealot to gain most of its durability advantage, got " + ratio);
        assertTrue(ratio >= damageOnly * Math.sqrt(durability) - EPSILON,
                "expected at least the root of the durability gap, got " + ratio);
        assertTrue(ratio < damageOnly * durability,
                "expected less than the full durability product, got " + ratio);
    }

    @Test
    void dragoonOutweighsZerglingByItsDurabilityGap() {
        double ratio = strengthRatioToZergling(UnitType.Protoss_Dragoon);
        double damageOnly = damageOnlyRatioToZergling(UnitType.Protoss_Dragoon);

        assertTrue(ratio > 2.0 * damageOnly,
                "expected the Dragoon to gain most of its durability advantage, got " + ratio);
    }

    @Test
    void zerglingMirrorRatiosAndVerdictsAreUnchanged() {
        double before = damageOnlyStrength(UnitType.Zerg_Zergling);
        double after = UnitStrength.groundToGround(UnitType.Zerg_Zergling);

        for (int ours = 1; ours <= 12; ours++) {
            for (int theirs = 1; theirs <= 12; theirs++) {
                assertEquals((double) ours / theirs, ours * after / (theirs * after), EPSILON);
                for (double threshold : ENGAGE_THRESHOLDS) {
                    CombatSimulator.CombatResult expected = HorizonCombatSimulator.selectResult(
                            ours * before, 0, theirs * before, 0, false, threshold);
                    CombatSimulator.CombatResult actual = HorizonCombatSimulator.selectResult(
                            ours * after, 0, theirs * after, 0, false, threshold);
                    assertEquals(expected, actual,
                            "mirror verdict moved at " + ours + " vs " + theirs + " at " + threshold);
                }
            }
        }
    }

    @Test
    void marineToZerglingRatioBarelyMoves() {
        double moved = strengthRatioToZergling(UnitType.Terran_Marine)
                / damageOnlyRatioToZergling(UnitType.Terran_Marine);

        assertTrue(Math.abs(moved - 1.0) < 0.15,
                "expected the Marine to move within variance, moved by " + (moved - 1.0));
    }

    @Test
    void threeZerglingsNoLongerEngageALoneZealot() {
        double zergling = UnitStrength.groundToGround(UnitType.Zerg_Zergling);
        double zealot = UnitStrength.groundToGround(UnitType.Protoss_Zealot);

        assertEquals(CombatSimulator.CombatResult.RETREAT, HorizonCombatSimulator.selectResult(
                3 * zergling, 0, zealot, 0, false, PROTOSS_ENGAGE_THRESHOLD));
        assertEquals(CombatSimulator.CombatResult.ENGAGE, HorizonCombatSimulator.selectResult(
                4 * zergling, 0, zealot, 0, false, PROTOSS_ENGAGE_THRESHOLD));
    }

    @Test
    void ultraliskNoLongerCarriesAHardcodedDurabilityMultiplier() {
        UnitType ultralisk = UnitType.Zerg_Ultralisk;
        double expected = damageOnlyRatioToZergling(ultralisk)
                * Math.sqrt(durabilityRatioToZergling(ultralisk));

        assertEquals(expected, strengthRatioToZergling(ultralisk), EPSILON);
    }

    @Test
    void lurkerAndMutaliskKeepTheirDamageShapeMultipliers() {
        UnitType lurker = UnitType.Zerg_Lurker;
        double expectedLurker = 2.5 * damageOnlyRatioToZergling(lurker)
                * Math.sqrt(durabilityRatioToZergling(lurker));
        assertEquals(expectedLurker, strengthRatioToZergling(lurker), EPSILON);

        UnitType mutalisk = UnitType.Zerg_Mutalisk;
        double expectedMutalisk = 1.5 * damageOnlyRatioToZergling(mutalisk)
                * Math.sqrt(durabilityRatioToZergling(mutalisk));
        double actualMutalisk = UnitStrength.airToGround(mutalisk)
                / UnitStrength.groundToGround(UnitType.Zerg_Zergling);
        assertEquals(expectedMutalisk, actualMutalisk, EPSILON);
    }

    @Test
    void staticDefenseOverridesKeepTheirZerglingEquivalence() {
        double before = 6.0 / damageOnlyStrength(UnitType.Zerg_Zergling);
        double after = UnitStrength.groundToGround(UnitType.Zerg_Sunken_Colony)
                / UnitStrength.groundToGround(UnitType.Zerg_Zergling);

        assertTrue(Math.abs(after / before - 1.0) < 0.10,
                "expected the Sunken override to stay on its tuned scale, moved to " + after);
    }
}
