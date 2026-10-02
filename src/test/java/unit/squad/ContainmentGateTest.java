package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.ContainmentGate.MIN_RANGED_SHARE;
import static unit.squad.ContainmentGate.compositionAllows;
import static unit.squad.ContainmentGate.enemyOutranges;
import static unit.squad.ContainmentGate.groundRange;
import static unit.squad.ContainmentGate.isRanged;
import static unit.squad.ContainmentGate.longestRange;
import static unit.squad.ContainmentGate.rangedShare;
import static unit.squad.ContainmentGate.safeToHold;

class ContainmentGateTest {

    private static Map<UnitType, Integer> army(Object... typesAndCounts) {
        Map<UnitType, Integer> counts = new HashMap<>();
        for (int i = 0; i < typesAndCounts.length; i += 2) {
            counts.put((UnitType) typesAndCounts[i], (Integer) typesAndCounts[i + 1]);
        }
        return counts;
    }

    private static final Map<UnitType, Integer> LINGS = army(UnitType.Zerg_Zergling, 10);
    private static final Map<UnitType, Integer> LINGS_AND_HYDRAS = army(UnitType.Zerg_Zergling, 8,
            UnitType.Zerg_Hydralisk, 4);
    private static final Map<UnitType, Integer> VULTURES_AND_GOLIATHS = army(UnitType.Terran_Vulture, 2,
            UnitType.Terran_Goliath, 1);
    private static final Map<UnitType, Integer> BUNKER_AND_MARINE = army(UnitType.Terran_Bunker, 1,
            UnitType.Terran_Marine, 1);

    @Test
    void zerglingsAreMeleeAndTerranRangedUnitsAreNot() {
        assertFalse(isRanged(UnitType.Zerg_Zergling));
        assertFalse(isRanged(UnitType.Terran_Firebat));
        assertTrue(isRanged(UnitType.Terran_Marine));
        assertTrue(isRanged(UnitType.Terran_Vulture));
        assertTrue(isRanged(UnitType.Terran_Goliath));
        assertTrue(isRanged(UnitType.Zerg_Hydralisk));
        assertEquals(0, groundRange(UnitType.Terran_Medic));
    }

    @Test
    void anAllZerglingSquadIsNotEligibleAgainstRememberedVulturesAndGoliaths() {
        assertFalse(compositionAllows(true, false, LINGS, VULTURES_AND_GOLIATHS));
    }

    @Test
    void anAllZerglingSquadIsNotEligibleAgainstABunkerAndAMarine() {
        assertFalse(compositionAllows(true, false, LINGS, BUNKER_AND_MARINE));
    }

    @Test
    void aSquadWithEnoughRangedSupplyIsStillEligible() {
        assertTrue(rangedShare(LINGS_AND_HYDRAS) >= MIN_RANGED_SHARE);
        assertTrue(compositionAllows(true, false, LINGS_AND_HYDRAS, VULTURES_AND_GOLIATHS));
    }

    @Test
    void aSquadWithTooLittleRangedSupplyIsNotEligible() {
        Map<UnitType, Integer> oneHydra = army(UnitType.Zerg_Zergling, 20, UnitType.Zerg_Hydralisk, 1);
        assertTrue(rangedShare(oneHydra) < MIN_RANGED_SHARE);
        assertFalse(compositionAllows(true, false, oneHydra, VULTURES_AND_GOLIATHS));
    }

    @Test
    void anAllZerglingSquadIsEligibleAgainstMeleeOnlyTerranOrNothingKnown() {
        assertTrue(compositionAllows(true, false, LINGS, army(UnitType.Terran_Firebat, 3)));
        assertTrue(compositionAllows(true, false, LINGS, Collections.emptyMap()));
    }

    @Test
    void aDetectedMechOpponentGatesAMeleeSquadBeforeAnyRangedUnitIsSeen() {
        assertFalse(compositionAllows(true, true, LINGS, army(UnitType.Terran_Firebat, 3)));
        assertTrue(compositionAllows(true, true, LINGS_AND_HYDRAS, army(UnitType.Terran_Firebat, 3)));
    }

    @Test
    void theGateNeverAppliesAgainstOtherRaces() {
        assertTrue(compositionAllows(false, false, LINGS, VULTURES_AND_GOLIATHS));
        assertTrue(compositionAllows(false, true, LINGS, VULTURES_AND_GOLIATHS));
        assertTrue(safeToHold(false, true, LINGS, VULTURES_AND_GOLIATHS));
    }

    @Test
    void aUnitWithNoGroundWeaponIsLeftOutOfTheRangedShare() {
        Map<UnitType, Integer> withDefilers = army(UnitType.Zerg_Zergling, 2, UnitType.Zerg_Hydralisk, 2,
                UnitType.Zerg_Defiler, 4);
        assertEquals(2.0 / 3.0, rangedShare(withDefilers), 0.01);
    }

    @Test
    void aBuildingNeverCountsAsAnOutrangingEnemy() {
        assertFalse(enemyOutranges(0, army(UnitType.Terran_Bunker, 2, UnitType.Terran_Missile_Turret, 1)));
        assertTrue(enemyOutranges(0, BUNKER_AND_MARINE));
        assertFalse(enemyOutranges(0, army(UnitType.Terran_Marine, 0)));
    }

    @Test
    void aSimRetreatDoesNotHoldAnArcWhenTheSafeToHoldTestFails() {
        assertFalse(safeToHold(true, false, LINGS, VULTURES_AND_GOLIATHS));
        assertFalse(safeToHold(true, false, LINGS, BUNKER_AND_MARINE));
        assertFalse(safeToHold(true, true, LINGS_AND_HYDRAS, Collections.emptyMap()));
    }

    @Test
    void aSimRetreatHoldsAnArcWhenNothingKnownOutrangesTheSquad() {
        assertTrue(safeToHold(true, false, LINGS, army(UnitType.Terran_Firebat, 3)));
        assertTrue(safeToHold(true, false, LINGS_AND_HYDRAS, BUNKER_AND_MARINE));
    }

    @Test
    void aSquadWithHydrasIsOutrangedByVulturesAndGoliathsOnASimRetreat() {
        assertEquals(UnitType.Zerg_Hydralisk.groundWeapon().maxRange(), longestRange(LINGS_AND_HYDRAS));
        assertFalse(safeToHold(true, false, LINGS_AND_HYDRAS, VULTURES_AND_GOLIATHS));
    }
}
