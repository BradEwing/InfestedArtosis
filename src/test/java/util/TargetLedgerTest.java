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
        return new TargetLedger("squad-1", Arrays.asList(
                new StaticDefenseZone(UnitType.Terran_Bunker, BUNKER, 160),
                new StaticDefenseZone(UnitType.Terran_Missile_Turret, TURRET, 224)));
    }

    @Test
    void meleePicksAccumulatePerTarget() {
        TargetLedger ledger = TargetLedger.empty();

        ledger.recordMelee(7);
        ledger.recordMelee(7);
        ledger.recordMelee(9);

        assertEquals(2, ledger.meleeAssigned(7));
        assertEquals(1, ledger.meleeAssigned(9));
        assertEquals(0, ledger.meleeAssigned(11));
    }

    @Test
    void aBunkerCountsAsGroundDefenceAndATurretDoesNot() {
        TargetLedger ledger = ledgerWithBunkerAndTurret();

        assertTrue(ledger.insideGroundDefense(new Position(1100, 1000)));
        assertFalse(ledger.insideGroundDefense(new Position(1400, 1000)));
        assertFalse(ledger.insideGroundDefense(new Position(2050, 2000)));
    }

    @Test
    void theLedgerCarriesItsSquadId() {
        assertEquals("squad-1", ledgerWithBunkerAndTurret().getSquadId());
        assertEquals("", TargetLedger.empty().getSquadId());
    }
}
