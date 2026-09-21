package unit.squad.horizon;

import bwapi.DamageType;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitStrengthTest {

    private static final double SUPERSEDED_ANTI_AIR_LITERAL = 2.0;
    private static final double TOLERANCE = 1e-9;

    private static final double ZERGLING_GROUND_BEFORE_DURABILITY = 1.8644709320919568;
    private static final double MARINE_GROUND_BEFORE_DURABILITY = 1.5484804043631566;

    private static final double SUNKEN_GROUND_FORMULA = 92.5925375643027;
    private static final double PHOTON_CANNON_FORMULA = 54.98290206594559;
    private static final double BUNKER_GARRISON = 115.8776628652021;
    private static final double SUPERSEDED_BUNKER_LITERAL = 224.4994432064365;

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
    void supersededAntiAirLiteralsUnderstatedBothBuildings() {
        assertTrue(UnitStrength.antiAirStrength(UnitType.Zerg_Spore_Colony)
                > SUPERSEDED_ANTI_AIR_LITERAL * 2);
        assertTrue(UnitStrength.antiAirStrength(UnitType.Terran_Missile_Turret)
                > SUPERSEDED_ANTI_AIR_LITERAL * 2.5);
    }

    @Test
    void everyTypeScoresExactlyItsFormulaValue() {
        for (UnitType type : UnitType.values()) {
            if (type == UnitType.Unknown || type == UnitType.None) continue;
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
    void durabilityIsTheRootOfTheHitPointAndShieldPool() {
        assertEquals(Math.sqrt(35), UnitStrength.durabilityFactor(UnitType.Zerg_Zergling), TOLERANCE);
        assertEquals(Math.sqrt(200), UnitStrength.durabilityFactor(UnitType.Protoss_Photon_Cannon), TOLERANCE);
    }

    @Test
    void aTypeWithNoHitPointPoolKeepsItsWeaponStrength() {
        assertEquals(1.0, UnitStrength.durabilityFactor(UnitType.Terran_Goliath_Turret), TOLERANCE);
        assertTrue(UnitStrength.groundToAir(UnitType.Terran_Goliath_Turret) > 0);
    }

    @Test
    void durabilityDoesNotCancelFromAMarineAgainstAZergling() {
        double before = MARINE_GROUND_BEFORE_DURABILITY / ZERGLING_GROUND_BEFORE_DURABILITY;
        double after = UnitStrength.groundToGround(UnitType.Terran_Marine)
                / UnitStrength.groundToGround(UnitType.Zerg_Zergling);
        assertEquals(0.8305200031, before, 1e-9);
        assertEquals(0.8878632299, after, 1e-9);
        assertTrue(after > before);
    }

    @Test
    void durabilityMovesEveryPairingByADifferentAmount() {
        double marineToZergling = UnitStrength.durabilityFactor(UnitType.Terran_Marine)
                / UnitStrength.durabilityFactor(UnitType.Zerg_Zergling);
        double zealotToHydralisk = UnitStrength.durabilityFactor(UnitType.Protoss_Zealot)
                / UnitStrength.durabilityFactor(UnitType.Zerg_Hydralisk);
        assertEquals(1.0690, marineToZergling, 1e-4);
        assertEquals(1.4142, zealotToHydralisk, 1e-4);
        assertTrue(zealotToHydralisk > marineToZergling);
    }

    @Test
    void ultraliskNoLongerCarriesAStandInForDurability() {
        double weaponOnly = UnitStrength.groundToGround(UnitType.Zerg_Ultralisk)
                / UnitStrength.durabilityFactor(UnitType.Zerg_Ultralisk);
        assertEquals(rawWeaponStrength(UnitType.Zerg_Ultralisk), weaponOnly, TOLERANCE);
    }

    @Test
    void lurkerKeepsItsAttackShapeMultiplier() {
        double weaponOnly = UnitStrength.groundToGround(UnitType.Zerg_Lurker)
                / UnitStrength.durabilityFactor(UnitType.Zerg_Lurker);
        assertEquals(rawWeaponStrength(UnitType.Zerg_Lurker) * 2.5, weaponOnly, TOLERANCE);
    }

    @Test
    void armedStaticDefenceScoresItsFormulaValue() {
        assertEquals(SUNKEN_GROUND_FORMULA,
                UnitStrength.groundToGround(UnitType.Zerg_Sunken_Colony), TOLERANCE);
        assertEquals(PHOTON_CANNON_FORMULA,
                UnitStrength.groundToGround(UnitType.Protoss_Photon_Cannon), TOLERANCE);
        assertEquals(PHOTON_CANNON_FORMULA,
                UnitStrength.groundToAir(UnitType.Protoss_Photon_Cannon), TOLERANCE);
    }

    @Test
    void aBunkerScoresFourMarinesBehindItsOwnHitPoints() {
        double pool = UnitStrength.durabilityFactor(UnitType.Terran_Bunker)
                / UnitStrength.durabilityFactor(UnitType.Terran_Marine);
        double expected = 4 * UnitStrength.groundToGround(UnitType.Terran_Marine) * pool;
        assertEquals(expected, UnitStrength.groundToGround(UnitType.Terran_Bunker), TOLERANCE);
        assertEquals(BUNKER_GARRISON, UnitStrength.groundToGround(UnitType.Terran_Bunker), TOLERANCE);
        assertEquals(BUNKER_GARRISON, UnitStrength.groundToAir(UnitType.Terran_Bunker), TOLERANCE);
        assertEquals(0.0, UnitStrength.airToGround(UnitType.Terran_Bunker), TOLERANCE);
        assertEquals(0.0, UnitStrength.airToAir(UnitType.Terran_Bunker), TOLERANCE);
    }

    @Test
    void aBunkerIsPricedBelowTheLiteralItReplaces() {
        double zerglings = UnitStrength.groundToGround(UnitType.Terran_Bunker)
                / UnitStrength.groundToGround(UnitType.Zerg_Zergling);
        double supersededZerglings = SUPERSEDED_BUNKER_LITERAL
                / UnitStrength.groundToGround(UnitType.Zerg_Zergling);
        assertEquals(10.5053, zerglings, 1e-4);
        assertEquals(20.3529, supersededZerglings, 1e-4);
    }

    @Test
    void aMedicIsStillPricedAtZeroByTheStrengthTable() {
        assertEquals(0.0, UnitStrength.totalStrength(UnitType.Terran_Medic), TOLERANCE);
    }

    private static double rawWeaponStrength(UnitType type) {
        double dps = (double) type.groundWeapon().damageAmount() * type.groundWeapon().damageFactor()
                * type.maxGroundHits() / type.groundWeapon().damageCooldown();
        return dps * Math.log(type.groundWeapon().maxRange() / 4.0 + 16.0);
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
