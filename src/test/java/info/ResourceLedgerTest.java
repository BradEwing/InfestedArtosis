package info;

import bwapi.TilePosition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResourceLedgerTest {

    private static final TilePosition MAIN = new TilePosition(10, 10);
    private static final TilePosition NATURAL = new TilePosition(30, 12);
    private static final TilePosition MAIN_GEYSER = new TilePosition(14, 6);
    private static final TilePosition NATURAL_GEYSER = new TilePosition(34, 8);
    private static final int MAIN_EXTRACTOR = 501;
    private static final int NATURAL_EXTRACTOR = 502;
    private static final int STARTING_GAS = 5000;
    private static final int COMPLETED_FRAME = 2400;

    private static ResourceLedger ledgerWithTwoBases() {
        ResourceLedger ledger = new ResourceLedger();
        ledger.addBase(MAIN, Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
        ledger.addBase(NATURAL, Arrays.asList(11, 12, 13, 14, 15, 16, 17));
        return ledger;
    }

    private static ResourceLedger ledgerWithMainExtractor() {
        ResourceLedger ledger = new ResourceLedger();
        ledger.addExtractor(MAIN_EXTRACTOR, MAIN_GEYSER, MAIN, STARTING_GAS, COMPLETED_FRAME);
        return ledger;
    }

    @Test
    void everyLivingPatchAtAnOwnedBaseCounts() {
        ResourceLedger ledger = ledgerWithTwoBases();

        assertEquals(15, ledger.remainingMineralPatches(Arrays.asList(MAIN, NATURAL)));
    }

    @Test
    void aPatchDestroyedLowersTheRemainingPatches() {
        ResourceLedger ledger = ledgerWithTwoBases();

        ledger.removeMineralPatch(3);

        assertEquals(14, ledger.remainingMineralPatches(Arrays.asList(MAIN, NATURAL)));
        assertEquals(7, ledger.remainingMineralPatches(Collections.singletonList(MAIN)));
    }

    @Test
    void aDestroyedPatchThatNoBaseRecordedChangesNothing() {
        ResourceLedger ledger = ledgerWithTwoBases();

        ledger.removeMineralPatch(999);

        assertEquals(15, ledger.remainingMineralPatches(Arrays.asList(MAIN, NATURAL)));
    }

    @Test
    void aBaseWeNoLongerHoldIsNotCounted() {
        ResourceLedger ledger = ledgerWithTwoBases();

        assertEquals(8, ledger.remainingMineralPatches(Collections.singletonList(MAIN)));
    }

    @Test
    void aBaseWeNeverClaimedCountsNothing() {
        ResourceLedger ledger = ledgerWithTwoBases();

        assertEquals(0, ledger.remainingMineralPatches(Collections.singletonList(new TilePosition(60, 60))));
    }

    @Test
    void reclaimingABaseReplacesItsPatches() {
        ResourceLedger ledger = ledgerWithTwoBases();

        ledger.addBase(NATURAL, Arrays.asList(11, 12));

        assertEquals(2, ledger.remainingMineralPatches(Collections.singletonList(NATURAL)));
    }

    @Test
    void aCompletedExtractorWithGasLeftIsMining() {
        ResourceLedger ledger = ledgerWithMainExtractor();

        assertNull(ledger.observeResources(MAIN_EXTRACTOR, 8));

        assertEquals(1, ledger.miningGeysers());
        assertEquals(0, ledger.depletedGeysers());
    }

    @Test
    void aGeyserWhoseResourcesHitZeroMovesFromMiningToDepleted() {
        ResourceLedger ledger = ledgerWithMainExtractor();

        ResourceLedger.ExtractorGeyser depleted = ledger.observeResources(MAIN_EXTRACTOR, 0);

        assertNotNull(depleted);
        assertEquals(MAIN_GEYSER, depleted.getGeyser());
        assertEquals(MAIN, depleted.getBase());
        assertEquals(STARTING_GAS, depleted.getInitialResources());
        assertEquals(COMPLETED_FRAME, depleted.getCompletedFrame());
        assertEquals(0, ledger.miningGeysers());
        assertEquals(1, ledger.depletedGeysers());
    }

    @Test
    void aDepletedGeyserIsReportedOnce() {
        ResourceLedger ledger = ledgerWithMainExtractor();

        ledger.observeResources(MAIN_EXTRACTOR, 0);

        assertNull(ledger.observeResources(MAIN_EXTRACTOR, 0));
        assertEquals(1, ledger.depletedGeysers());
    }

    @Test
    void anExtractorRebuiltOnADepletedGeyserIsDepletedButNotReportedAgain() {
        ResourceLedger ledger = ledgerWithMainExtractor();
        ledger.observeResources(MAIN_EXTRACTOR, 0);
        ledger.removeExtractor(MAIN_EXTRACTOR);

        ledger.addExtractor(MAIN_EXTRACTOR + 100, MAIN_GEYSER, MAIN, STARTING_GAS, COMPLETED_FRAME + 5000);

        assertNull(ledger.observeResources(MAIN_EXTRACTOR + 100, 0));
        assertEquals(0, ledger.miningGeysers());
        assertEquals(1, ledger.depletedGeysers());
    }

    @Test
    void onlyTheEmptyGeyserIsDepleted() {
        ResourceLedger ledger = ledgerWithMainExtractor();
        ledger.addExtractor(NATURAL_EXTRACTOR, NATURAL_GEYSER, NATURAL, STARTING_GAS, COMPLETED_FRAME);

        ResourceLedger.ExtractorGeyser depleted = ledger.observeResources(NATURAL_EXTRACTOR, 0);
        ledger.observeResources(MAIN_EXTRACTOR, 1200);

        assertEquals(NATURAL_GEYSER, depleted.getGeyser());
        assertEquals(1, ledger.miningGeysers());
        assertEquals(1, ledger.depletedGeysers());
    }

    @Test
    void aDestroyedExtractorLeavesBothCounts() {
        ResourceLedger ledger = ledgerWithMainExtractor();
        ledger.addExtractor(NATURAL_EXTRACTOR, NATURAL_GEYSER, NATURAL, STARTING_GAS, COMPLETED_FRAME);
        ledger.observeResources(NATURAL_EXTRACTOR, 0);

        ledger.removeExtractor(MAIN_EXTRACTOR);
        ledger.removeExtractor(NATURAL_EXTRACTOR);

        assertEquals(0, ledger.miningGeysers());
        assertEquals(0, ledger.depletedGeysers());
    }

    @Test
    void anExtractorAtABaseWeNoLongerHoldStillCountsAsMining() {
        ResourceLedger ledger = ledgerWithTwoBases();
        ledger.addExtractor(NATURAL_EXTRACTOR, NATURAL_GEYSER, NATURAL, STARTING_GAS, COMPLETED_FRAME);

        assertEquals(8, ledger.remainingMineralPatches(Collections.singletonList(MAIN)));
        assertEquals(1, ledger.miningGeysers());
    }

    @Test
    void aReadingForAnUntrackedExtractorIsIgnored() {
        ResourceLedger ledger = ledgerWithMainExtractor();

        assertNull(ledger.observeResources(NATURAL_EXTRACTOR, 0));
        assertEquals(1, ledger.miningGeysers());
    }

    @Test
    void anExtractorOnAGeyserOutsideAnyBaseCarriesNoBase() {
        ResourceLedger ledger = new ResourceLedger();
        ledger.addExtractor(MAIN_EXTRACTOR, MAIN_GEYSER, null, STARTING_GAS, COMPLETED_FRAME);

        ResourceLedger.ExtractorGeyser depleted = ledger.observeResources(MAIN_EXTRACTOR, 0);

        assertNull(depleted.getBase());
    }

    @Test
    void aBaseAddedWithNoLivingPatchesCountsZero() {
        ResourceLedger ledger = new ResourceLedger();
        List<Integer> none = Collections.emptyList();
        ledger.addBase(MAIN, none);

        assertEquals(0, ledger.remainingMineralPatches(Collections.singletonList(MAIN)));
    }
}
