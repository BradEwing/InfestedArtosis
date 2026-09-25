package unit.squad.horizon;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitSizeType;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class FriendlyDomainPricingTest {

    private static final double TOLERANCE = 1e-9;
    private static final Position HERE = new Position(1000, 1000);
    private static final Map<UnitSizeType, Double> ALL_SMALL =
            Collections.singletonMap(UnitSizeType.Small, 1.0);
    private static final Map<UnitSizeType, Double> ALL_MEDIUM =
            Collections.singletonMap(UnitSizeType.Medium, 1.0);
    private static final double PROTOSS_ENGAGE_THRESHOLD = HorizonCombatSimulator.engageThreshold(Race.Protoss);
    private static final double LU01I000_ONE_DOMAIN_RATIO = 1.225;
    private static final double LU01I000_DOUBLE_COUNTED_RATIO = 2.45;
    private static final double ADJACENT_FALLOFF = 0.5;

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

    private static HorizonCombatSimulator.FriendlyForce forceOf(UnitType... units) {
        HorizonCombatSimulator.FriendlyForce force = new HorizonCombatSimulator.FriendlyForce();
        for (UnitType unit : units) {
            force.add(unit, HERE, 1.0, false);
        }
        return force;
    }

    private static double groundOf(UnitType enemy, Map<UnitSizeType, Double> ourSizes) {
        return HorizonCombatSimulator.weightedGroundStrength(enemy, ourSizes);
    }

    private static double antiAirOf(UnitType enemy, Map<UnitSizeType, Double> ourSizes) {
        return HorizonCombatSimulator.weightedAntiAirStrength(enemy, ourSizes);
    }

    @Test
    void aLoneMutaliskIsPricedOnItsGroundEngagingStrengthAgainstADragoonAndRetreats() {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(forceOf(UnitType.Zerg_Mutalisk),
                sampleOf(ALL_SMALL, HorizonCombatSimulator.HEIGHT_BONUS, UnitType.Protoss_Dragoon));

        assertEquals(UnitStrength.airToGround(UnitType.Zerg_Mutalisk), priced.getFriendlyAir(), TOLERANCE);
        assertEquals(LU01I000_ONE_DOMAIN_RATIO, priced.getFriendlyAir() / priced.getEnemyAntiAir(), 0.005);
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(priced.getFriendlyGround(), priced.getFriendlyAir(),
                priced.getEnemyGround(), priced.getEnemyAntiAir(), priced.getEnemyEngaged(), true,
                PROTOSS_ENGAGE_THRESHOLD));
    }

    @Test
    void summingBothOfTheMutalisksDomainsIsTheDoubleCountThatEngagedADragoon() {
        HorizonCombatSimulator.EnemySample dragoon =
                sampleOf(ALL_SMALL, HorizonCombatSimulator.HEIGHT_BONUS, UnitType.Protoss_Dragoon);
        double doubleCounted = UnitStrength.airToGround(UnitType.Zerg_Mutalisk)
                + UnitStrength.airToAir(UnitType.Zerg_Mutalisk);

        assertEquals(LU01I000_DOUBLE_COUNTED_RATIO, doubleCounted / dragoon.antiAirTotal(), 0.005);
        assertEquals(ENGAGE, HorizonCombatSimulator.selectResult(0, doubleCounted, dragoon.groundTotal(),
                dragoon.antiAirTotal(), 0, true, PROTOSS_ENGAGE_THRESHOLD));
    }

    @Test
    void aMutaliskAgainstDragoonsAndCorsairsIsSplitByTheEnemyAndNeverExceedsOneDomain() {
        double dragoon = Math.max(groundOf(UnitType.Protoss_Dragoon, ALL_SMALL),
                antiAirOf(UnitType.Protoss_Dragoon, ALL_SMALL));
        double corsair = antiAirOf(UnitType.Protoss_Corsair, ALL_SMALL);
        double airToGround = UnitStrength.airToGround(UnitType.Zerg_Mutalisk);
        double airToAir = UnitStrength.airToAir(UnitType.Zerg_Mutalisk);

        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(forceOf(UnitType.Zerg_Mutalisk),
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon, UnitType.Protoss_Corsair));

        assertEquals(corsair / (dragoon + corsair), priced.getEnemyAirShare(), TOLERANCE);
        assertEquals((dragoon * airToGround + corsair * airToAir) / (dragoon + corsair), priced.getFriendlyAir(),
                TOLERANCE);
        assertTrue(priced.getFriendlyAir() <= Math.max(airToGround, airToAir) + TOLERANCE);
        assertTrue(priced.getFriendlyAir() < airToGround + airToAir);
    }

    @Test
    void corsairsDoNotDiluteZerglingsFightingZealots() {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(
                forceOf(UnitType.Zerg_Zergling, UnitType.Zerg_Zergling),
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Zealot, UnitType.Protoss_Corsair,
                        UnitType.Protoss_Corsair, UnitType.Protoss_Corsair));

        assertTrue(priced.getEnemyAirShare() > 0.5);
        assertEquals(2 * UnitStrength.groundToGround(UnitType.Zerg_Zergling), priced.getFriendlyGround(), TOLERANCE);
        assertEquals(groundOf(UnitType.Protoss_Zealot, ALL_SMALL), priced.getEnemyGround(), TOLERANCE);
        assertEquals(priced.getEnemyGround(), priced.getEnemyEngaged(), TOLERANCE);
    }

    @Test
    void aSingleDomainUnitIsPricedOverTheTargetsItCanShootAndAtZeroWithNone() {
        HorizonCombatSimulator.PricedEngagement mixed = HorizonCombatSimulator.price(forceOf(UnitType.Zerg_Scourge),
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon, UnitType.Protoss_Corsair));
        HorizonCombatSimulator.PricedEngagement groundOnly = HorizonCombatSimulator.price(
                forceOf(UnitType.Zerg_Scourge), sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon));

        assertEquals(UnitStrength.airToAir(UnitType.Zerg_Scourge), mixed.getFriendlyAir(), TOLERANCE);
        assertEquals(0.0, groundOnly.getFriendlyAir(), TOLERANCE);
    }

    @Test
    void aHydraliskAgainstPureGroundCountsItsGroundWeaponOnly() {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(forceOf(UnitType.Zerg_Hydralisk),
                sampleOf(ALL_MEDIUM, 1.0, UnitType.Protoss_Zealot, UnitType.Protoss_Zealot));

        assertEquals(UnitStrength.groundToGround(UnitType.Zerg_Hydralisk), priced.getFriendlyGround(), TOLERANCE);
        assertTrue(UnitStrength.groundToAir(UnitType.Zerg_Hydralisk) > 0);
        assertTrue(priced.getFriendlyGround() < UnitStrength.groundToGround(UnitType.Zerg_Hydralisk)
                + UnitStrength.groundToAir(UnitType.Zerg_Hydralisk));
    }

    @Test
    void aDragoonAgainstAnAllGroundSquadCountsItsGroundWeaponOnly() {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(
                forceOf(UnitType.Zerg_Hydralisk, UnitType.Zerg_Hydralisk),
                sampleOf(ALL_MEDIUM, 1.0, UnitType.Protoss_Dragoon));

        assertTrue(antiAirOf(UnitType.Protoss_Dragoon, ALL_MEDIUM) > 0);
        assertEquals(groundOf(UnitType.Protoss_Dragoon, ALL_MEDIUM), priced.getEnemyEngaged(), TOLERANCE);
        assertEquals(0.0, priced.getOurAirShare(), TOLERANCE);
    }

    @Test
    void aDragoonAgainstAMixedSquadSplitsItsOneWeaponByOurComposition() {
        HorizonCombatSimulator.FriendlyForce mixed = forceOf(UnitType.Zerg_Hydralisk, UnitType.Zerg_Mutalisk);
        double ourGround = UnitStrength.strongerDomain(UnitType.Zerg_Hydralisk);
        double ourAir = UnitStrength.strongerDomain(UnitType.Zerg_Mutalisk);
        double ground = groundOf(UnitType.Protoss_Dragoon, ALL_MEDIUM);
        double antiAir = antiAirOf(UnitType.Protoss_Dragoon, ALL_MEDIUM);

        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(mixed,
                sampleOf(ALL_MEDIUM, 1.0, UnitType.Protoss_Dragoon));

        assertEquals(ourAir / (ourGround + ourAir), priced.getOurAirShare(), TOLERANCE);
        assertEquals((ourGround * ground + ourAir * antiAir) / (ourGround + ourAir), priced.getEnemyEngaged(),
                TOLERANCE);
        assertTrue(priced.getEnemyEngaged() < ground + antiAir);
    }

    @Test
    void aZealotIsNotDilutedByMutalisksItCannotHit() {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(
                forceOf(UnitType.Zerg_Zergling, UnitType.Zerg_Mutalisk),
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Zealot));

        assertEquals(groundOf(UnitType.Protoss_Zealot, ALL_SMALL), priced.getEnemyEngaged(), TOLERANCE);
    }

    @Test
    void anUnarmedFlyerDoesNotShiftPricingTowardsTheAir() {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(forceOf(UnitType.Zerg_Mutalisk),
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon, UnitType.Protoss_Observer));

        assertEquals(0.0, priced.getEnemyAirShare(), TOLERANCE);
        assertEquals(UnitStrength.airToGround(UnitType.Zerg_Mutalisk), priced.getFriendlyAir(), TOLERANCE);
    }

    @Test
    void withNothingArmedMeasuredEachUnitIsPricedAtItsStrongerDomain() {
        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(
                forceOf(UnitType.Zerg_Mutalisk, UnitType.Zerg_Zergling),
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Observer));

        assertEquals(UnitStrength.UNMEASURED_AIR_SHARE, priced.getEnemyAirShare(), TOLERANCE);
        assertEquals(UnitStrength.strongerDomain(UnitType.Zerg_Mutalisk), priced.getFriendlyAir(), TOLERANCE);
        assertEquals(UnitStrength.groundToGround(UnitType.Zerg_Zergling), priced.getFriendlyGround(), TOLERANCE);
    }

    @Test
    void ownAndAdjacentFlyersAreBothPricedAgainstTheEnemyTheyFaceAndReachTheVerdict() {
        HorizonCombatSimulator.FriendlyForce force = new HorizonCombatSimulator.FriendlyForce();
        force.add(UnitType.Zerg_Mutalisk, HERE, 1.0, false);
        force.add(UnitType.Zerg_Mutalisk, HERE, ADJACENT_FALLOFF, true);
        double airToGround = UnitStrength.airToGround(UnitType.Zerg_Mutalisk);

        HorizonCombatSimulator.PricedEngagement priced = HorizonCombatSimulator.price(force,
                sampleOf(ALL_SMALL, 1.0, UnitType.Protoss_Dragoon, UnitType.Protoss_Dragoon));
        List<HorizonCombatSimulator.UnitDebugEntry> entries = priced.getFriendlyEntries();

        assertEquals(2, entries.size());
        assertFalse(entries.get(0).isAdjacent());
        assertEquals(airToGround, entries.get(0).getStrength(), TOLERANCE);
        assertTrue(entries.get(1).isAdjacent());
        assertEquals(airToGround * ADJACENT_FALLOFF, entries.get(1).getStrength(), TOLERANCE);
        assertEquals(airToGround * (1 + ADJACENT_FALLOFF), priced.getFriendlyAir(), TOLERANCE);
        assertEquals(0.0, priced.getFriendlyGround(), TOLERANCE);
        assertEquals(RETREAT, HorizonCombatSimulator.selectResult(priced.getFriendlyGround(), priced.getFriendlyAir(),
                priced.getEnemyGround(), priced.getEnemyAntiAir(), priced.getEnemyEngaged(), true,
                PROTOSS_ENGAGE_THRESHOLD));
    }
}
