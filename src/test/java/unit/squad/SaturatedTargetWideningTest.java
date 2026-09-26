package unit.squad;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.managed.UnitRole;
import util.MeleeOverflowGate;
import util.TargetLedger;
import util.TargetScorer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaturatedTargetWideningTest {

    private static final int LING = 5;
    private static final int MARINE = 42;

    private static final class Enemy {
        private final int id;
        private final UnitType type;
        private final int distance;
        private final boolean admitted;

        Enemy(int id, UnitType type, int distance, boolean admitted) {
            this.id = id;
            this.type = type;
            this.distance = distance;
            this.admitted = admitted;
        }
    }

    private static TargetScorer.Selection selection(boolean saturated) {
        return new TargetScorer.Selection(null, TargetScorer.Priority.CRITICAL, 1, TargetScorer.Reason.THREAT,
                saturated ? 8 : 0, saturated, "squad-1", false);
    }

    private static List<Enemy> widen(List<Enemy> enemies) {
        return SquadManager.widenCandidates(enemies, e -> e.distance, e -> e.admitted);
    }

    @Test
    void wideningKeepsTheNearbyCandidatesAndAddsAdmittedOnesOutToTwiceTheTargetingRadius() {
        Enemy near = new Enemy(1, UnitType.Terran_Marine, 100, false);
        Enemy edge = new Enemy(2, UnitType.Terran_Marine, 256, false);
        Enemy wide = new Enemy(3, UnitType.Terran_Marine, 512, true);
        Enemy wideButRefused = new Enemy(4, UnitType.Terran_Marine, 400, false);
        Enemy tooFar = new Enemy(5, UnitType.Terran_Marine, 513, true);

        assertEquals(Arrays.asList(near, edge, wide), widen(Arrays.asList(near, edge, wide, wideButRefused, tooFar)));
    }

    @Test
    void anUnsaturatedPickStandsWithoutBuildingTheWidenedCandidates() {
        TargetScorer.Selection open = selection(false);

        TargetScorer.Selection kept = SquadManager.<Enemy>widenWhenSaturated(open, 1, () -> {
            throw new AssertionError("widened candidates built");
        }, candidates -> {
            throw new AssertionError("selected again");
        });

        assertSame(open, kept);
        assertNull(SquadManager.<Enemy>widenWhenSaturated(null, 0, Collections::emptyList, c -> selection(false)));
    }

    @Test
    void aSaturatedPickIsReplacedByAnOpenOneFromTheWidenedCandidates() {
        TargetScorer.Selection full = selection(true);
        List<Enemy> widened = Arrays.asList(new Enemy(1, UnitType.Terran_Marine, 30, true),
                new Enemy(2, UnitType.Terran_Marine, 400, true));

        TargetScorer.Selection kept = SquadManager.widenWhenSaturated(full, 1, () -> widened,
                candidates -> selection(false));

        assertFalse(kept.isSaturated());
        assertTrue(kept.isWidened());
        assertFalse(full.isWidened());
    }

    @Test
    void aSaturatedPickStandsWhenTheWidenedPickIsSaturatedToo() {
        TargetScorer.Selection full = selection(true);
        List<Enemy> widened = Arrays.asList(new Enemy(1, UnitType.Terran_Marine, 30, true),
                new Enemy(2, UnitType.Terran_Marine, 400, true));

        assertSame(full, SquadManager.widenWhenSaturated(full, 1, () -> widened, candidates -> selection(true)));
    }

    @Test
    void aSaturatedPickStandsWhenWideningAddsNoCandidate() {
        TargetScorer.Selection full = selection(true);
        List<Enemy> widened = Collections.singletonList(new Enemy(1, UnitType.Terran_Marine, 30, true));

        assertSame(full, SquadManager.widenWhenSaturated(full, 1, () -> widened, candidates -> {
            throw new AssertionError("selected again");
        }));
    }

    @Test
    void onlyAFightingMemberWithALiveTargetIsSeededIntoTheFrameLedger() {
        assertTrue(SquadManager.seedsFightTarget(UnitRole.FIGHT, true, false));
        assertFalse(SquadManager.seedsFightTarget(UnitRole.FIGHT, false, false));
        for (UnitRole role : Arrays.asList(UnitRole.RETREAT, UnitRole.RALLY, UnitRole.CONTAIN, UnitRole.RUNBY)) {
            assertFalse(SquadManager.seedsFightTarget(role, true, false), role.toString());
        }
    }

    @Test
    void aMemberAttackMovingInOverflowIsNotSeededIntoTheFrameLedger() {
        assertFalse(SquadManager.seedsFightTarget(UnitRole.FIGHT, true, true));
    }

    private static TargetScorer.Selection commit(TargetLedger ledger, MeleeOverflowGate gate, boolean saturated,
                                                 int frame) {
        return SquadManager.commitPick(ledger, gate, LING, UnitType.Zerg_Zergling, selection(saturated), MARINE,
                MARINE, frame);
    }

    @Test
    void aSaturatedReTargetIsAnAttackMoveOnItsFirstFrame() {
        for (int held : new int[] {SquadManager.NO_TARGET_ID, MARINE + 1}) {
            TargetLedger ledger = TargetLedger.empty();

            TargetScorer.Selection issued = SquadManager.commitPick(ledger, new MeleeOverflowGate(), LING,
                    UnitType.Zerg_Zergling, selection(true), MARINE, held, 100);

            assertTrue(issued.isAttackMove(), "held " + held);
            assertEquals(0, ledger.meleeAssigned(MARINE));
        }
    }

    @Test
    void aPackReTargetingOntoOneMarineFillsItsCapAndOverflowsTheRestOnTheSameFrame() {
        TargetLedger ledger = TargetLedger.empty();
        int cap = TargetScorer.meleeCap(UnitType.Terran_Marine, UnitType.Zerg_Zergling);
        int pack = 30;
        int attackMoves = 0;

        for (int ling = 0; ling < pack; ling++) {
            boolean saturated = ledger.meleeAssigned(MARINE) >= cap;
            TargetScorer.Selection issued = SquadManager.commitPick(ledger, new MeleeOverflowGate(), ling,
                    UnitType.Zerg_Zergling, selection(saturated), MARINE, SquadManager.NO_TARGET_ID, 100);
            attackMoves += issued.isAttackMove() ? 1 : 0;
        }

        assertEquals(cap, ledger.meleeAssigned(MARINE));
        assertEquals(pack - cap, attackMoves);
    }

    @Test
    void anUnsaturatedPickIsADirectAttackHeldInTheLedger() {
        TargetLedger ledger = TargetLedger.empty();

        TargetScorer.Selection issued = commit(ledger, new MeleeOverflowGate(), false, 100);

        assertFalse(issued.isAttackMove());
        assertEquals(1, ledger.meleeAssigned(MARINE));
    }

    @Test
    void aSaturatedPickStaysADirectAttackUntilTheGateEnters() {
        TargetLedger ledger = TargetLedger.empty();
        MeleeOverflowGate gate = new MeleeOverflowGate();

        for (int frame = 100; frame < 100 + MeleeOverflowGate.ENTER_FRAMES - 1; frame++) {
            assertFalse(commit(ledger, gate, true, frame).isAttackMove());
            assertEquals(1, ledger.meleeAssigned(MARINE));
        }
    }

    @Test
    void aSaturatedPickBecomesAnAttackMoveThatTheLedgerDoesNotHold() {
        TargetLedger ledger = TargetLedger.empty();
        MeleeOverflowGate gate = new MeleeOverflowGate();
        TargetScorer.Selection issued = null;

        for (int frame = 100; frame < 100 + MeleeOverflowGate.ENTER_FRAMES; frame++) {
            ledger.release(LING);
            issued = commit(ledger, gate, true, frame);
        }

        assertTrue(issued.isAttackMove());
        assertTrue(issued.isSaturated());
        assertEquals(0, ledger.meleeAssigned(MARINE));
    }
}
