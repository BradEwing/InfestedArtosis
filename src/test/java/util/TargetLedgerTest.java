package util;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetLedgerTest {

    private static final Position BUNKER = new Position(1000, 1000);
    private static final Position TURRET = new Position(2000, 2000);

    private static TargetLedger ledgerWithBunkerAndTurret() {
        return new TargetLedger(Arrays.asList(
                new StaticDefenseZone(UnitType.Terran_Bunker, BUNKER, 160),
                new StaticDefenseZone(UnitType.Terran_Missile_Turret, TURRET, 224)));
    }

    @Test
    void meleePicksAccumulatePerTarget() {
        TargetLedger ledger = TargetLedger.empty();

        ledger.record(1, UnitType.Zerg_Zergling, 7);
        ledger.record(2, UnitType.Zerg_Zergling, 7);
        ledger.record(3, UnitType.Zerg_Zergling, 9);

        assertEquals(2, ledger.meleeAssigned(7));
        assertEquals(1, ledger.meleeAssigned(9));
        assertEquals(0, ledger.meleeAssigned(11));
    }

    @Test
    void onlyMeleePicksAreCounted() {
        TargetLedger ledger = TargetLedger.empty();

        ledger.record(1, UnitType.Zerg_Zergling, 7);
        ledger.record(2, UnitType.Zerg_Hydralisk, 7);
        ledger.record(3, UnitType.Zerg_Mutalisk, 7);

        assertEquals(1, ledger.meleeAssigned(7));
    }

    @Test
    void anAttackerRecordedTwiceOnAFrameIsCountedOnceOnItsLatestTarget() {
        TargetLedger ledger = TargetLedger.empty();

        ledger.record(1, UnitType.Zerg_Zergling, 7);
        ledger.record(1, UnitType.Zerg_Zergling, 7);
        ledger.record(1, UnitType.Zerg_Zergling, 9);

        assertEquals(0, ledger.meleeAssigned(7));
        assertEquals(1, ledger.meleeAssigned(9));
    }

    @Test
    void theLoadAnAttackerSeesLeavesOutItsOwnEntry() {
        TargetLedger ledger = TargetLedger.empty();
        ledger.record(1, UnitType.Zerg_Zergling, 7);
        ledger.record(2, UnitType.Zerg_Zergling, 7);

        assertEquals(1, ledger.meleeAssignedExcept(7, 1));
        assertEquals(2, ledger.meleeAssignedExcept(7, 3));
        assertEquals(0, ledger.meleeAssignedExcept(9, 1));
    }

    @Test
    void releasingAnAttackerDropsItsEntryAndReleasingAnUnknownOneIsHarmless() {
        TargetLedger ledger = TargetLedger.empty();
        ledger.record(1, UnitType.Zerg_Zergling, 7);
        ledger.record(2, UnitType.Zerg_Zergling, 7);

        ledger.release(1);
        ledger.release(1);
        ledger.release(42);

        assertEquals(1, ledger.meleeAssigned(7));
        ledger.release(2);
        assertEquals(0, ledger.meleeAssigned(7));
    }

    @Test
    void aRangedPickDoesNotReleaseAnEarlierMeleeEntryOfAnotherAttacker() {
        TargetLedger ledger = TargetLedger.empty();
        ledger.record(1, UnitType.Zerg_Zergling, 7);

        ledger.record(2, UnitType.Zerg_Hydralisk, 7);

        assertEquals(1, ledger.meleeAssigned(7));
    }

    @Test
    void aLedgerWithOnlyAntiAirDefenceHasNoGroundDefence() {
        TargetLedger turretOnly = new TargetLedger(Arrays.asList(
                new StaticDefenseZone(UnitType.Terran_Missile_Turret, TURRET, 224)));

        assertFalse(turretOnly.hasGroundDefense());
        assertTrue(ledgerWithBunkerAndTurret().hasGroundDefense());
    }

    @Test
    void aBunkerCountsAsGroundDefenceAndATurretDoesNot() {
        TargetLedger ledger = ledgerWithBunkerAndTurret();

        assertTrue(ledger.insideGroundDefense(new Position(1100, 1000)));
        assertFalse(ledger.insideGroundDefense(new Position(1400, 1000)));
        assertFalse(ledger.insideGroundDefense(new Position(2050, 2000)));
    }
}
