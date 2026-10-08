package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import telemetry.BunkerAdvanceReason;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BunkerAdvanceGateTest {

    private static final Position BUNKER = new Position(1000, 1000);
    private static final Position SQUAD = new Position(600, 1000);
    private static final int BUNKER_ID = 7;
    private static final int FULL_HIT_POINTS = 350;
    private static final int LOSS_FRAME = 5000;
    private static final double PRICE = 100;
    private static final double RELEASE_RATIO = 1.44 + BunkerAdvanceGate.RELEASE_HYSTERESIS;
    private static final double WEAK = 100;
    private static final double STRONG = PRICE * RELEASE_RATIO + 1;

    private static BunkerLossLedger ledgerWithLoss() {
        BunkerLossLedger ledger = new BunkerLossLedger();
        ledger.record(BUNKER, BUNKER_ID, LOSS_FRAME, FULL_HIT_POINTS, PRICE, RELEASE_RATIO);
        return ledger;
    }

    private static List<BunkerAdvanceGate.Bunker> living(int hitPoints) {
        return Collections.singletonList(new BunkerAdvanceGate.Bunker(BUNKER_ID, BUNKER, hitPoints));
    }

    private static BunkerAdvanceGate.Verdict evaluate(BunkerLossLedger ledger, List<BunkerAdvanceGate.Bunker> living,
                                                      double ownStrength, boolean melee, boolean simBreaks,
                                                      int frame) {
        return BunkerAdvanceGate.evaluate(true, ledger, living, SQUAD, ownStrength, melee, simBreaks, frame);
    }

    @Test
    void theGateHoldsAfterAPricedLoss() {
        BunkerAdvanceGate.Verdict verdict = evaluate(ledgerWithLoss(), living(FULL_HIT_POINTS), WEAK, true, false,
                LOSS_FRAME + 100);

        assertTrue(verdict.isHeld());
        assertEquals(BunkerAdvanceReason.HELD_LOSS, verdict.getReason());
        assertEquals(BUNKER_ID, verdict.getBunker().getId());
        assertEquals(PRICE, verdict.getPrice());
        assertEquals(RELEASE_RATIO, verdict.getReleaseRatio());
    }

    @Test
    void theHoldKeepsHoldingFrameAfterFrame() {
        BunkerLossLedger ledger = ledgerWithLoss();

        for (int frame = LOSS_FRAME + 1; frame < LOSS_FRAME + 600; frame += 50) {
            assertTrue(evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, false, frame).isHeld());
        }
    }

    @Test
    void theFirstAttackOnABunkerIsNeverGated() {
        BunkerAdvanceGate.Verdict verdict = evaluate(new BunkerLossLedger(), living(FULL_HIT_POINTS), WEAK, true,
                false, LOSS_FRAME);

        assertFalse(verdict.isHeld());
        assertEquals(BunkerAdvanceReason.NO_LOSS, verdict.getReason());
        assertEquals(BUNKER_ID, verdict.getBunker().getId());
    }

    @Test
    void aSquadWithNoBunkerInRangeIsNotConcerned() {
        BunkerAdvanceGate.Verdict verdict = BunkerAdvanceGate.evaluate(true, ledgerWithLoss(), living(FULL_HIT_POINTS),
                new Position(5000, 5000), WEAK, true, false, LOSS_FRAME + 1);

        assertFalse(verdict.isHeld());
        assertEquals(BunkerAdvanceReason.NO_BUNKER, verdict.getReason());
        assertNull(verdict.getBunker());
    }

    @Test
    void theHoldReleasesOnceTheSquadReachesThePricedStrengthPlusHysteresis() {
        BunkerLossLedger ledger = ledgerWithLoss();

        BunkerAdvanceGate.Verdict justShort = evaluate(ledger, living(FULL_HIT_POINTS),
                PRICE * (RELEASE_RATIO - 0.01), true, false, LOSS_FRAME + 10);
        BunkerAdvanceGate.Verdict released = evaluate(ledger, living(FULL_HIT_POINTS), STRONG, true, false,
                LOSS_FRAME + 11);

        assertTrue(justShort.isHeld());
        assertFalse(released.isHeld());
        assertEquals(BunkerAdvanceReason.RELEASED_STRENGTH, released.getReason());
        assertEquals(0, ledger.size());
    }

    @Test
    void theReleaseNeedsMoreThanTheSimsOwnEngageThreshold() {
        assertTrue(BunkerAdvanceGate.RELEASE_HYSTERESIS > 0);
        BunkerAdvanceGate.Verdict verdict = evaluate(ledgerWithLoss(), living(FULL_HIT_POINTS), PRICE * 1.44, true,
                false, LOSS_FRAME + 10);

        assertTrue(verdict.isHeld());
    }

    @Test
    void aReleasedBunkerIsAFirstAttackAgain() {
        BunkerLossLedger ledger = ledgerWithLoss();
        evaluate(ledger, living(FULL_HIT_POINTS), STRONG, true, false, LOSS_FRAME + 10);

        BunkerAdvanceGate.Verdict next = evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, false, LOSS_FRAME + 11);

        assertFalse(next.isHeld());
        assertEquals(BunkerAdvanceReason.NO_LOSS, next.getReason());
    }

    @Test
    void theHoldReleasesWhenTheBunkerIsSeenDamaged() {
        BunkerLossLedger ledger = ledgerWithLoss();
        int justShort = FULL_HIT_POINTS - BunkerAdvanceGate.DAMAGE_RELEASE_HIT_POINTS + 1;
        int damaged = FULL_HIT_POINTS - BunkerAdvanceGate.DAMAGE_RELEASE_HIT_POINTS;

        assertTrue(evaluate(ledger, living(justShort), WEAK, true, false, LOSS_FRAME + 10).isHeld());
        BunkerAdvanceGate.Verdict released = evaluate(ledger, living(damaged), WEAK, true, false, LOSS_FRAME + 11);

        assertFalse(released.isHeld());
        assertEquals(BunkerAdvanceReason.RELEASED_DAMAGE, released.getReason());
    }

    @Test
    void damageIsReadAgainstTheHitPointsOfTheFirstLoss() {
        BunkerLossLedger ledger = new BunkerLossLedger();
        ledger.record(BUNKER, BUNKER_ID, LOSS_FRAME, 200, PRICE, RELEASE_RATIO);
        ledger.record(BUNKER, BUNKER_ID, LOSS_FRAME + 500, FULL_HIT_POINTS, PRICE, RELEASE_RATIO);

        assertTrue(evaluate(ledger, living(190), WEAK, true, false, LOSS_FRAME + 600).isHeld());
        assertEquals(BunkerAdvanceReason.RELEASED_DAMAGE,
                evaluate(ledger, living(160), WEAK, true, false, LOSS_FRAME + 601).getReason());
    }

    @Test
    void theHoldReleasesWhenTheBunkerIsDead() {
        BunkerLossLedger ledger = ledgerWithLoss();

        BunkerAdvanceGate.Verdict released = evaluate(ledger, Collections.emptyList(), WEAK, true, false,
                LOSS_FRAME + 10);

        assertFalse(released.isHeld());
        assertEquals(BunkerAdvanceReason.RELEASED_DEAD, released.getReason());
        assertNull(released.getBunker());
        assertEquals(0, ledger.size());
    }

    @Test
    void theHoldReleasesWhenTheTimeoutExpires() {
        BunkerLossLedger ledger = ledgerWithLoss();

        assertTrue(evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, false,
                LOSS_FRAME + BunkerAdvanceGate.HOLD_TIMEOUT_FRAMES - 1).isHeld());
        BunkerAdvanceGate.Verdict released = evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, false,
                LOSS_FRAME + BunkerAdvanceGate.HOLD_TIMEOUT_FRAMES);

        assertFalse(released.isHeld());
        assertEquals(BunkerAdvanceReason.RELEASED_TIMEOUT, released.getReason());
    }

    @Test
    void aFreshRetreatRestartsTheTimeout() {
        BunkerLossLedger ledger = ledgerWithLoss();
        ledger.record(BUNKER, BUNKER_ID, LOSS_FRAME + 2000, FULL_HIT_POINTS, PRICE, RELEASE_RATIO);

        assertTrue(evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, false,
                LOSS_FRAME + BunkerAdvanceGate.HOLD_TIMEOUT_FRAMES).isHeld());
    }

    @Test
    void anAdvanceTheSimReadsAsBreakingTheBunkerIsNeverGated() {
        BunkerLossLedger ledger = ledgerWithLoss();

        BunkerAdvanceGate.Verdict breaks = evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, true,
                LOSS_FRAME + 10);
        BunkerAdvanceGate.Verdict later = evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, false,
                LOSS_FRAME + 11);

        assertFalse(breaks.isHeld());
        assertEquals(BunkerAdvanceReason.SIM_BREAKS, breaks.getReason());
        assertTrue(later.isHeld());
    }

    @Test
    void aSquadThatIsNotMostlyMeleeIsNeverGated() {
        BunkerAdvanceGate.Verdict verdict = evaluate(ledgerWithLoss(), living(FULL_HIT_POINTS), WEAK, false, false,
                LOSS_FRAME + 10);

        assertFalse(verdict.isHeld());
        assertEquals(BunkerAdvanceReason.NOT_MELEE, verdict.getReason());
    }

    @Test
    void theNearerBunkersLossDecidesAndAReleasedOneDoesNotLiftAHoldOnAnother() {
        Position farBunker = new Position(1200, 1000);
        BunkerLossLedger ledger = ledgerWithLoss();
        ledger.record(farBunker, 9, LOSS_FRAME, FULL_HIT_POINTS, PRICE, RELEASE_RATIO);
        List<BunkerAdvanceGate.Bunker> both = Arrays.asList(
                new BunkerAdvanceGate.Bunker(BUNKER_ID, BUNKER, FULL_HIT_POINTS - 100),
                new BunkerAdvanceGate.Bunker(9, farBunker, FULL_HIT_POINTS));

        BunkerAdvanceGate.Verdict verdict = evaluate(ledger, both, WEAK, true, false, LOSS_FRAME + 10);

        assertTrue(verdict.isHeld());
        assertEquals(9, verdict.getBunker().getId());
        assertEquals(1, ledger.size());
    }

    @Test
    void aRebuiltBunkerOnTheSameSpotStartsANewRecord() {
        BunkerLossLedger ledger = new BunkerLossLedger();
        ledger.record(BUNKER, BUNKER_ID, LOSS_FRAME, 200, PRICE, RELEASE_RATIO);
        ledger.record(BUNKER, BUNKER_ID + 1, LOSS_FRAME + 900, FULL_HIT_POINTS, PRICE, RELEASE_RATIO);
        List<BunkerAdvanceGate.Bunker> rebuilt = Collections.singletonList(
                new BunkerAdvanceGate.Bunker(BUNKER_ID + 1, BUNKER, 340));

        assertTrue(evaluate(ledger, rebuilt, WEAK, true, false, LOSS_FRAME + 1000).isHeld());
    }

    @Test
    void aRepeatedRetreatKeepsTheLargerPrice() {
        BunkerLossLedger ledger = ledgerWithLoss();
        ledger.record(BUNKER, BUNKER_ID, LOSS_FRAME + 10, FULL_HIT_POINTS, PRICE / 2, RELEASE_RATIO);

        assertEquals(PRICE, evaluate(ledger, living(FULL_HIT_POINTS), WEAK, true, false, LOSS_FRAME + 20).getPrice());
    }

    @Test
    void withTheBunkerGatesSwitchedOffNoAdvanceIsHeld() {
        BunkerLossLedger ledger = ledgerWithLoss();

        BunkerAdvanceGate.Verdict verdict = BunkerAdvanceGate.evaluate(false, ledger, living(FULL_HIT_POINTS), SQUAD,
                WEAK, true, false, LOSS_FRAME + 10);

        assertFalse(verdict.isHeld());
        assertEquals(BunkerAdvanceReason.NO_BUNKER, verdict.getReason());
        assertEquals(1, ledger.size());
    }

    @Test
    void aSquadIsMostlyMeleeUntilItsRangedSupplyReachesAQuarter() {
        Map<UnitType, Integer> lings = new EnumMap<>(UnitType.class);
        lings.put(UnitType.Zerg_Zergling, 30);
        assertTrue(ContainmentGate.isMostlyMelee(lings));

        lings.put(UnitType.Zerg_Hydralisk, 8);
        assertFalse(ContainmentGate.isMostlyMelee(lings));

        lings.put(UnitType.Zerg_Hydralisk, 3);
        assertTrue(ContainmentGate.isMostlyMelee(lings));
    }
}
