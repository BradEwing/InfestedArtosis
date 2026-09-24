package unit.squad.horizon;

import bwapi.Race;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class FriendlyDomainPricingTest {

    private static final double TOLERANCE = 1e-9;
    private static final Map<UnitSizeType, Double> ALL_SMALL =
            Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final Map<UnitSizeType, Double> ALL_MEDIUM =
            Collections.singletonMap(UnitSizeType.Medium, 1.0);
    private static final double PROTOSS_ENGAGE_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Protoss);
    private static final double LU01I000_ONE_DOMAIN_RATIO = 1.225;
    private static final double LU01I000_DOUBLE_COUNTED_RATIO = 2.45;

    private static HorizonCombatSimulator.EnemySample sampleOf(Map<UnitSizeType, Double> ourSizes,
                                                               double heightMod, UnitType... enemies) {
        HorizonCombatSimulator.EnemySample sample = new HorizonCombatSimulator.EnemySample();
        for (UnitType enemy : enemies) {
            sample.add(enemy,
                    HorizonCombatSimulator.weightedGroundStrength(enemy, ourSizes) * heightMod,
                    HorizonCombatSimulator.weightedAntiAirStrength(enemy, ourSizes) * heightMod);
        }
        return sample;
    }

    @Test
    void aLoneMutaliskIsPricedOnItsGroundEngagingStrengthAgainstADragoonAndRetreats() {
        HorizonCombatSimulator.EnemySample dragoon =
                sampleOf(ALL_SMALL, HorizonCombatSimulator.HEIGHT_BONUS, UnitType.Protoss_Dragoon);
        double mutalisk = UnitStrength.engagedStrength(UnitType.Zerg_Mutalisk, dragoon.airShare());

        assertEquals(0.0, dragoon.airShare(), TOLERANCE);
        assertEquals(UnitStrength.airToGround(UnitType.Zerg_Mutalisk), mutalisk, TOLERANCE);
        assertEquals(LU01I000_ONE_DOMAIN_RATIO, mutalisk / dragoon.antiAirTotal(), 0.005);
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(0, mutalisk, dragoon.groundTotal(),
                dragoon.antiAirTotal(), true, PROTOSS_ENGAGE_THRESHOLD));
    }

    @Test
    void summingBothOfTheMutalisksDomainsIsTheDoubleCountThatEngagedADragoon() {
        HorizonCombatSimulator.EnemySample dragoon =
                sampleOf(ALL_SMALL, HorizonCombatSimulator.HEIGHT_BONUS, UnitType.Protoss_Dragoon);
        double doubleCounted = UnitStrength.airToGround(UnitType.Zerg_Mutalisk)
                + UnitStrength.airToAir(UnitType.Zerg_Mutalisk);

        assertEquals(LU01I000_DOUBLE_COUNTED_RATIO, doubleCounted / dragoon.antiAirTotal(), 0.005);
        assertEquals(ENGAGE, HorizonCombatSimulator.selectResult(0, doubleCounted, dragoon.groundTotal(),
                dragoon.antiAirTotal(), true, PROTOSS_ENGAGE_THRESHOLD));
    }

    @Test
    void aMutaliskAgainstDragoonsAndCorsairsIsSplitByTheEnemyAndNeverExceedsOneDomain() {
        HorizonCombatSimulator.EnemySample mixed =
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon, UnitType.Protoss_Corsair);
        double dragoonWeight = Math.max(
                HorizonCombatSimulator.weightedGroundStrength(UnitType.Protoss_Dragoon, ALL_SMALL),
                HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Protoss_Dragoon, ALL_SMALL));
        double corsairWeight = HorizonCombatSimulator.weightedAntiAirStrength(UnitType.Protoss_Corsair, ALL_SMALL);
        double share = mixed.airShare();
        double airToGround = UnitStrength.airToGround(UnitType.Zerg_Mutalisk);
        double airToAir = UnitStrength.airToAir(UnitType.Zerg_Mutalisk);
        double mutalisk = UnitStrength.engagedStrength(UnitType.Zerg_Mutalisk, share);

        assertEquals(corsairWeight / (dragoonWeight + corsairWeight), share, TOLERANCE);
        assertTrue(share > 0 && share < 1);
        assertEquals((1 - share) * airToGround + share * airToAir, mutalisk, TOLERANCE);
        assertTrue(mutalisk <= Math.max(airToGround, airToAir) + TOLERANCE);
        assertTrue(mutalisk < airToGround + airToAir);
    }

    @Test
    void aSingleDomainUnitAgainstAMixedEnemyKeepsOnlyTheShareItCanShoot() {
        HorizonCombatSimulator.EnemySample mixed =
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon, UnitType.Protoss_Corsair);
        double share = mixed.airShare();

        assertEquals(share * UnitStrength.airToAir(UnitType.Zerg_Scourge),
                UnitStrength.engagedStrength(UnitType.Zerg_Scourge, share), TOLERANCE);
        assertEquals((1 - share) * UnitStrength.groundToGround(UnitType.Zerg_Zergling),
                UnitStrength.engagedStrength(UnitType.Zerg_Zergling, share), TOLERANCE);
    }

    @Test
    void aMutaliskAgainstOnlyCorsairsIsPricedOnItsAirEngagingStrength() {
        HorizonCombatSimulator.EnemySample corsairs =
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Corsair, UnitType.Protoss_Corsair);

        assertEquals(1.0, corsairs.airShare(), TOLERANCE);
        assertEquals(UnitStrength.airToAir(UnitType.Zerg_Mutalisk),
                UnitStrength.engagedStrength(UnitType.Zerg_Mutalisk, corsairs.airShare()), TOLERANCE);
    }

    @Test
    void aHydraliskAgainstPureGroundCountsItsGroundWeaponOnly() {
        HorizonCombatSimulator.EnemySample zealots =
                sampleOf(ALL_MEDIUM, 1.0, UnitType.Protoss_Zealot, UnitType.Protoss_Zealot);
        double hydralisk = UnitStrength.engagedStrength(UnitType.Zerg_Hydralisk, zealots.airShare());

        assertEquals(0.0, zealots.airShare(), TOLERANCE);
        assertEquals(UnitStrength.groundToGround(UnitType.Zerg_Hydralisk), hydralisk, TOLERANCE);
        assertTrue(UnitStrength.groundToAir(UnitType.Zerg_Hydralisk) > 0);
        assertTrue(hydralisk < UnitStrength.groundToGround(UnitType.Zerg_Hydralisk)
                + UnitStrength.groundToAir(UnitType.Zerg_Hydralisk));
    }

    @Test
    void anUnarmedFlyerDoesNotPullOurPricingTowardsTheAir() {
        HorizonCombatSimulator.EnemySample dragoonAndObserver =
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon, UnitType.Protoss_Observer);

        assertEquals(0.0, dragoonAndObserver.airShare(), TOLERANCE);
    }

    @Test
    void withNothingArmedMeasuredEachUnitIsPricedAtItsStrongerDomain() {
        HorizonCombatSimulator.EnemySample empty = new HorizonCombatSimulator.EnemySample();
        HorizonCombatSimulator.EnemySample observer = sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Observer);

        assertEquals(UnitStrength.UNMEASURED_AIR_SHARE, empty.airShare(), TOLERANCE);
        assertEquals(UnitStrength.UNMEASURED_AIR_SHARE, observer.airShare(), TOLERANCE);
        assertEquals(Math.max(UnitStrength.airToGround(UnitType.Zerg_Mutalisk),
                        UnitStrength.airToAir(UnitType.Zerg_Mutalisk)),
                UnitStrength.engagedStrength(UnitType.Zerg_Mutalisk, empty.airShare()), TOLERANCE);
        assertEquals(UnitStrength.groundToGround(UnitType.Zerg_Zergling),
                UnitStrength.engagedStrength(UnitType.Zerg_Zergling, empty.airShare()), TOLERANCE);
    }
}
