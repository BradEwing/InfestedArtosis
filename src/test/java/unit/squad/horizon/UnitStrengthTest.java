package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitStrengthTest {

    private static final double SUPERSEDED_ANTI_AIR_LITERAL = 2.0;
    private static final double TOLERANCE = 1e-9;

    private static double tableEntry(UnitType type, int domain) {
        switch (domain) {
            case 0:  return UnitStrength.groundToGround(type);
            case 1:  return UnitStrength.groundToAir(type);
            case 2:  return UnitStrength.airToGround(type);
            default: return UnitStrength.airToAir(type);
        }
    }

    @Test
    void sporeColonyScoresItsFormulaValue() {
        double formula = UnitStrength.formulaStrength(UnitType.Zerg_Spore_Colony)[1];
        assertEquals(formula, UnitStrength.antiAirStrength(UnitType.Zerg_Spore_Colony), TOLERANCE);
    }

    @Test
    void missileTurretScoresItsFormulaValue() {
        double formula = UnitStrength.formulaStrength(UnitType.Terran_Missile_Turret)[1];
        assertEquals(formula, UnitStrength.antiAirStrength(UnitType.Terran_Missile_Turret), TOLERANCE);
    }

    @Test
    void sporeColonyIsWithinTwentyPercentOfItsFormulaValue() {
        double formula = UnitStrength.formulaStrength(UnitType.Zerg_Spore_Colony)[1];
        double scored = UnitStrength.antiAirStrength(UnitType.Zerg_Spore_Colony);
        assertTrue(Math.abs(scored - formula) / formula <= 0.20);
    }

    @Test
    void missileTurretIsWithinTwentyPercentOfItsFormulaValue() {
        double formula = UnitStrength.formulaStrength(UnitType.Terran_Missile_Turret)[1];
        double scored = UnitStrength.antiAirStrength(UnitType.Terran_Missile_Turret);
        assertTrue(Math.abs(scored - formula) / formula <= 0.20);
    }

    @Test
    void supersededAntiAirLiteralsUnderstatedBothBuildings() {
        assertTrue(UnitStrength.antiAirStrength(UnitType.Zerg_Spore_Colony)
                > SUPERSEDED_ANTI_AIR_LITERAL * 2);
        assertTrue(UnitStrength.antiAirStrength(UnitType.Terran_Missile_Turret)
                > SUPERSEDED_ANTI_AIR_LITERAL * 2.5);
    }

    @Test
    void antiAirOnlyBuildingsAreNoLongerHandTuned() {
        assertFalse(UnitStrength.isHandTuned(UnitType.Zerg_Spore_Colony));
        assertFalse(UnitStrength.isHandTuned(UnitType.Terran_Missile_Turret));
    }

    @Test
    void onlyThreeTypesKeepAHandTunedLiteral() {
        assertTrue(UnitStrength.isHandTuned(UnitType.Zerg_Sunken_Colony));
        assertTrue(UnitStrength.isHandTuned(UnitType.Protoss_Photon_Cannon));
        assertTrue(UnitStrength.isHandTuned(UnitType.Terran_Bunker));
        int handTuned = 0;
        for (UnitType type : UnitType.values()) {
            if (UnitStrength.isHandTuned(type)) handTuned++;
        }
        assertEquals(3, handTuned);
    }

    @Test
    void noHandTunedLiteralSitsBelowItsFormulaValue() {
        for (UnitType type : UnitType.values()) {
            if (!UnitStrength.isHandTuned(type)) continue;
            double[] formula = UnitStrength.formulaStrength(type);
            for (int domain = 0; domain < formula.length; domain++) {
                assertTrue(tableEntry(type, domain) >= formula[domain] - TOLERANCE,
                        type + " domain " + domain);
            }
        }
    }

    @Test
    void everyOtherTypeScoresExactlyItsFormulaValue() {
        for (UnitType type : UnitType.values()) {
            if (type == UnitType.Unknown || type == UnitType.None) continue;
            if (UnitStrength.isHandTuned(type)) continue;
            double[] formula = UnitStrength.formulaStrength(type);
            for (int domain = 0; domain < formula.length; domain++) {
                assertEquals(formula[domain], tableEntry(type, domain), TOLERANCE, type.toString());
            }
        }
    }

    @Test
    void mutaliskKeepsItsAirStrengthBonus() {
        double[] formula = UnitStrength.formulaStrength(UnitType.Zerg_Mutalisk);
        assertEquals(formula[2], UnitStrength.airToGround(UnitType.Zerg_Mutalisk), TOLERANCE);
        assertEquals(formula[3], UnitStrength.airToAir(UnitType.Zerg_Mutalisk), TOLERANCE);
        assertTrue(UnitStrength.airToAir(UnitType.Zerg_Mutalisk) > 0);
    }

    @Test
    void sunkenColonyExplosiveDamageComesFromGameData() {
        assertEquals(DamageType.Explosive,
                UnitType.Zerg_Sunken_Colony.groundWeapon().damageType());
    }

    @Test
    void antiAirBuildingDamageTypesComeFromGameData() {
        assertEquals(DamageType.Normal, UnitType.Zerg_Spore_Colony.airWeapon().damageType());
        assertEquals(DamageType.Explosive, UnitType.Terran_Missile_Turret.airWeapon().damageType());
        assertEquals(UnitSizeType.Small, UnitType.Zerg_Mutalisk.size());
    }
}
