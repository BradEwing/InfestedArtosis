package info.tracking.zerg;

import bwapi.Race;
import info.tracking.DroneEquivalents;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZergOpenerReadingTest {

    private static final Time BEFORE_BAND = new Time(1, 50);
    private static final Time IN_BAND = new Time(2, 10);
    private static final Time LATE_POOL_SCOUT = new Time(2, 25);

    @Test
    void earlyPoolWithNineEquivalentsReadsNinePool() {
        assertEquals(ZergOpener.NINE_POOL, ZergOpenerReading.classify(early(BEFORE_BAND, 9).build()));
    }

    @Test
    void earlyPoolReadsNinePoolEvenAfterDronesResume() {
        assertEquals(ZergOpener.NINE_POOL, ZergOpenerReading.classify(early(IN_BAND, 13).build()));
    }

    @Test
    void fourPoolNeverReachesTheNinePoolBand() {
        assertNull(ZergOpenerReading.classify(early(BEFORE_BAND, 5).build()));
    }

    @Test
    void poolWithTenEquivalentsReadsNinePoolFromTheBandTime() {
        assertEquals(ZergOpener.NINE_POOL, ZergOpenerReading.classify(poolSeen(IN_BAND, 10).build()));
        assertEquals(ZergOpener.NINE_POOL, ZergOpenerReading.classify(
                poolSeen(ZergOpenerReading.NINE_POOL_BAND_FROM, 9).build()));
    }

    @Test
    void poolWithFewEquivalentsBeforeTheBandTimeReadsNothing() {
        assertNull(ZergOpenerReading.classify(poolSeen(BEFORE_BAND, 9).build()));
    }

    @Test
    void elevenEquivalentsWithoutATimingSignIsAmbiguous() {
        assertNull(ZergOpenerReading.classify(poolSeen(IN_BAND, 11).build()));
        assertNull(ZergOpenerReading.classify(poolSeen(IN_BAND, 11).mainScouted(true)
                .naturalLastSeen(IN_BAND).build()));
    }

    @Test
    void naturalDepotKeepsTheBandFromReadingNinePool() {
        assertNull(ZergOpenerReading.classify(poolSeen(IN_BAND, 10).naturalDepotFirstSeen(IN_BAND).build()));
    }

    @Test
    void naturalDepotSeenBeforeAnyPoolFirstExpandsReadsTwelveHatch() {
        ZergOpenerEvidence evidence = poolSeen(IN_BAND, 12)
                .naturalDepotFirstSeen(ZergOpenerReading.HATCH_BEFORE_ANY_POOL_FIRST_BY)
                .build();

        assertEquals(ZergOpener.TWELVE_HATCH, ZergOpenerReading.classify(evidence));
    }

    @Test
    void naturalDepotWithoutAPoolInAScoutedMainReadsTwelveHatch() {
        Time late = new Time(2, 50);
        ZergOpenerEvidence evidence = base(late, 12).mainScouted(true).naturalDepotFirstSeen(late).build();

        assertEquals(ZergOpener.TWELVE_HATCH, ZergOpenerReading.classify(evidence));
    }

    @Test
    void lateNaturalDepotBesideAPoolIsAmbiguous() {
        Time late = new Time(2, 50);
        ZergOpenerEvidence evidence = poolSeen(late, 14).mainScouted(true).naturalDepotFirstSeen(late).build();

        assertNull(ZergOpenerReading.classify(evidence));
    }

    @Test
    void naturalDepotWithTooFewEquivalentsReadsNothing() {
        ZergOpenerEvidence evidence = base(IN_BAND, 11).mainScouted(true).naturalDepotFirstSeen(IN_BAND).build();

        assertNull(ZergOpenerReading.classify(evidence));
    }

    @Test
    void latePoolWithAnEmptyNaturalReadsTwelvePool() {
        assertEquals(ZergOpener.TWELVE_POOL, ZergOpenerReading.classify(twelvePool().build()));
    }

    @Test
    void twelvePoolNeedsTheNaturalSeenEmptyAfterEveryNinePoolFinishes() {
        assertNull(ZergOpenerReading.classify(twelvePool().naturalLastSeen(null).build()));
        assertNull(ZergOpenerReading.classify(twelvePool().naturalLastSeen(new Time(3000)).build()));
    }

    @Test
    void twelvePoolNeedsTheLatePoolAndTwelveEquivalents() {
        assertNull(ZergOpenerReading.classify(twelvePool().latePool(false).build()));
        assertNull(ZergOpenerReading.classify(twelvePool().equivalents(11).build()));
    }

    @Test
    void nothingIsReadAfterTheCutoff() {
        Time late = new Time(ZergOpenerReading.DECISION_CUTOFF.getFrames() + 1);

        assertNull(ZergOpenerReading.classify(early(late, 9).build()));
        assertNull(ZergOpenerReading.classify(twelvePool().time(late).build()));
    }

    @Test
    void microwaveTimelineReadsNinePool() {
        assertNull(ZergOpenerReading.classify(poolSeen(new Time(2500), 10).build()));
        assertEquals(ZergOpener.NINE_POOL, ZergOpenerReading.classify(poolSeen(new Time(2923), 10).build()));
        assertEquals(ZergOpener.NINE_POOL, ZergOpenerReading.classify(early(new Time(3500), 11).build()));
    }

    @Test
    void theFirstOpenerReadIsFrozen() {
        ZergOpenerReading reading = new ZergOpenerReading();
        DroneEquivalents equivalents = new DroneEquivalents(8, 1, 0);

        assertNull(reading.decide(poolSeen(BEFORE_BAND, 9).build(), equivalents));
        assertEquals(ZergOpener.NINE_POOL, reading.decide(early(BEFORE_BAND, 9).build(), equivalents));
        assertEquals(ZergOpener.NINE_POOL, reading.decide(twelvePool().build(), new DroneEquivalents(11, 1, 0)));
        assertEquals("9Pool:EARLY_POOL:8d+1s+0k", reading.getEvidenceLabel());
    }

    @Test
    void naturalDepotLatchKeepsItsFirstFrame() {
        ZergOpenerReading reading = new ZergOpenerReading();

        reading.observeNaturalDepot(false, BEFORE_BAND);
        assertFalse(reading.naturalDepotSeen());
        reading.observeNaturalDepot(true, IN_BAND);
        reading.observeNaturalDepot(false, LATE_POOL_SCOUT);
        assertTrue(reading.naturalDepotSeen());
    }

    @Test
    void recognizersAreZergOnlyAndNamedForTheirOpener() {
        ZergOpenerReading reading = new ZergOpenerReading();
        for (ZergOpener opener : ZergOpener.values()) {
            ZergOpenerRecognizer recognizer = new ZergOpenerRecognizer(opener, reading);
            assertEquals(opener.getStrategyName(), recognizer.getName());
            assertEquals(opener.getStrategyName(), recognizer.getDetectionLabel());
            assertEquals(Race.Zerg, recognizer.getRace());
        }
    }

    private static ZergOpenerEvidence.ZergOpenerEvidenceBuilder base(Time time, int equivalents) {
        return ZergOpenerEvidence.builder().time(time).equivalents(equivalents);
    }

    private static ZergOpenerEvidence.ZergOpenerEvidenceBuilder poolSeen(Time time, int equivalents) {
        return base(time, equivalents).poolSeen(true);
    }

    private static ZergOpenerEvidence.ZergOpenerEvidenceBuilder early(Time time, int equivalents) {
        return poolSeen(time, equivalents).earlyPool(true);
    }

    private static ZergOpenerEvidence.ZergOpenerEvidenceBuilder twelvePool() {
        return poolSeen(LATE_POOL_SCOUT, 12)
                .latePool(true)
                .mainScouted(true)
                .naturalLastSeen(ZergOpenerReading.LATE_POOL_MORPHING_FROM);
    }
}
