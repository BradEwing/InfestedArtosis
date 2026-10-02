package unit.squad;

import bwapi.TestUnits;
import bwapi.Unit;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import util.MeleeOverflowGate;
import util.TargetLedger;
import util.TargetScorer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class SwarmTargetLedgerTest {

    private static final int FRAME = 20160;
    private static final int NEAR_MARINE = 40;
    private static final int FAR_MARINE = 41;
    private static final int FIRST_LING = 1;

    private final TestUnits units = new TestUnits();
    private final Unit nearMarine = placed(UnitType.Terran_Marine, NEAR_MARINE, 1040, 1000);
    private final Unit farMarine = placed(UnitType.Terran_Marine, FAR_MARINE, 1100, 1000);
    private final int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);

    private Unit placed(UnitType type, int id, int x, int y) {
        Unit unit = units.unit(type, id);
        units.setExists(unit, true);
        units.setPosition(unit, x, y);
        return unit;
    }

    private Unit ling(int id) {
        return placed(UnitType.Zerg_Zergling, id, 1000, 1000);
    }

    private TargetScorer.Selection pick(Unit ling, TargetLedger ledger) {
        return SquadManager.swarmPick(ling, Arrays.asList(nearMarine, farMarine), null, ledger, "swarm-squad",
                new MeleeOverflowGate(), FRAME);
    }

    @Test
    void anUnloadedPickTakesTheNearerCoveredEnemyAndIsRecordedInTheLedger() {
        TargetLedger ledger = TargetLedger.empty();

        TargetScorer.Selection issued = pick(ling(FIRST_LING), ledger);

        assertSame(nearMarine, issued.getTarget());
        assertFalse(issued.isAttackMove());
        assertEquals(1, ledger.meleeAssigned(NEAR_MARINE));
        assertEquals("swarm-squad", issued.getSquadId());
    }

    @Test
    void aPickSkipsAnEnemyAnotherSquadAlreadyHoldsAtItsCap() {
        TargetLedger ledger = TargetLedger.empty();
        for (int i = 0; i < cap; i++) {
            ledger.record(20 + i, UnitType.Zerg_Zergling, NEAR_MARINE);
        }

        TargetScorer.Selection issued = pick(ling(FIRST_LING), ledger);

        assertSame(farMarine, issued.getTarget());
        assertEquals(cap, ledger.meleeAssigned(NEAR_MARINE));
        assertEquals(1, ledger.meleeAssigned(FAR_MARINE));
    }

    @Test
    void lingsPickedInTurnUnderOneSwarmNeverStackPastTheCapAndTheOverflowAttackMoves() {
        TargetLedger ledger = TargetLedger.empty();
        List<TargetScorer.Selection> picks = new ArrayList<>();
        for (int i = 0; i < 2 * cap + 3; i++) {
            picks.add(pick(ling(FIRST_LING + i), ledger));
        }

        assertEquals(cap, ledger.meleeAssigned(NEAR_MARINE));
        assertEquals(cap, ledger.meleeAssigned(FAR_MARINE));
        long attackMoves = picks.stream().filter(TargetScorer.Selection::isAttackMove).count();
        assertEquals(3, attackMoves);
    }

    @Test
    void theAttackersEarlierEntryIsReleasedBeforeItPicksAgain() {
        TargetLedger ledger = TargetLedger.empty();
        Unit ling = ling(FIRST_LING);
        ledger.record(FIRST_LING, UnitType.Zerg_Zergling, NEAR_MARINE);
        for (int i = 0; i < cap - 1; i++) {
            ledger.record(20 + i, UnitType.Zerg_Zergling, NEAR_MARINE);
        }

        TargetScorer.Selection issued = pick(ling, ledger);

        assertSame(nearMarine, issued.getTarget());
        assertEquals(cap, ledger.meleeAssigned(NEAR_MARINE));
    }

    @Test
    void withNothingToAttackThereIsNoPickAndTheAttackerLeavesTheLedger() {
        TargetLedger ledger = TargetLedger.empty();
        Unit ling = ling(FIRST_LING);
        ledger.record(FIRST_LING, UnitType.Zerg_Zergling, NEAR_MARINE);

        TargetScorer.Selection issued = SquadManager.swarmPick(ling, Collections.emptyList(), nearMarine, ledger,
                "swarm-squad", new MeleeOverflowGate(), FRAME);

        assertNull(issued);
        assertEquals(0, ledger.meleeAssigned(NEAR_MARINE));
    }
}
