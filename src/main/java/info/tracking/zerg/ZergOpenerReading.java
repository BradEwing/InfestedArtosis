package info.tracking.zerg;

import bwapi.UnitType;
import info.tracking.DroneEquivalents;
import info.tracking.ObservedUnitTracker;
import info.tracking.StrategyDetectionContext;
import util.Time;

/**
 * Reads a Zerg opponent's opener as 9Pool, 12Pool or 12Hatch from its drone equivalents and the timing of its
 * Spawning Pool and natural Hatchery. One reading is shared by the opener recognizers, so at most one opener is
 * read per game: the first label the evidence supports is frozen.
 * <p>
 * Drone equivalents rise as Drones come out, so every threshold is keyed to the frame of the observation, and no
 * label is decided after {@link #DECISION_CUTOFF}. The frames are taken from our own 9PoolSpeed, Overpool, 9Hatch,
 * 12Pool and 12Hatch games, where the true opener is known (local games to 2026-09-27, 690 to 1114 per opener):
 * <ul>
 *     <li>9PoolSpeed and Overpool start the Pool by frame 2111 and finish it by 3319; the earliest 12 pool starts
 *     it at 2113, finishes it at 3321 and hatches its first Zergling at 3778. No Hatchery-first opener finishes a
 *     Pool before 3655 or hatches a Zergling before 4159.</li>
 *     <li>By 2:00, 12Pool and 12Hatch have 12 drone equivalents, 9PoolSpeed and Overpool 9 to 11.</li>
 *     <li>12Hatch starts the natural Hatchery from frame 2333, the earliest 12 pool starts it at 3426.</li>
 * </ul>
 * Drone equivalents are a lower bound, so a label needs its band met, and 10 to 11 equivalents with no timing
 * sign reads as nothing. A 4 pool, at 5 equivalents, never reaches the 9Pool band.
 */
public class ZergOpenerReading {

    static final Time DECISION_CUTOFF = new Time(3, 0);
    static final Time EARLY_POOL_SEEN_BY = new Time(2050);
    static final Time EARLY_POOL_COMPLETED_BY = new Time(3250);
    static final Time EARLY_ZERGLING_BY = new Time(3700);
    static final Time LATE_POOL_MORPHING_FROM = new Time(3350);
    static final Time HATCH_BEFORE_ANY_POOL_FIRST_BY = new Time(3400);
    static final Time NINE_POOL_BAND_FROM = new Time(2, 0);
    static final int NINE_POOL_MIN_EQUIVALENTS = 7;
    static final int NINE_POOL_MAX_EQUIVALENTS = 10;
    static final int TWELVE_MIN_EQUIVALENTS = 12;

    private ZergOpener opener;
    private String evidenceLabel;
    private Time naturalDepotFirstSeen;
    private int lastReadFrame = -1;

    /**
     * The opener read so far, updating the reading once per frame until it is frozen.
     *
     * @return the frozen opener, or null while none has been read
     */
    public ZergOpener read(StrategyDetectionContext context) {
        Time time = context.getTime();
        if (time.getFrames() == lastReadFrame) {
            return opener;
        }
        lastReadFrame = time.getFrames();
        observeNaturalDepot(context.enemyNaturalHasDepot(), time);
        if (opener != null || time.greaterThan(DECISION_CUTOFF)) {
            return opener;
        }
        DroneEquivalents equivalents = context.enemyDroneEquivalents();
        if (equivalents == null) {
            return null;
        }
        return decide(evidence(context, equivalents.total()), equivalents);
    }

    /**
     * Freezes the first opener the evidence supports.
     *
     * @return the frozen opener, or null while none has been read
     */
    ZergOpener decide(ZergOpenerEvidence evidence, DroneEquivalents equivalents) {
        if (opener != null) {
            return opener;
        }
        opener = classify(evidence);
        if (opener != null) {
            evidenceLabel = label(opener, evidence, equivalents);
        }
        return opener;
    }

    /**
     * What telemetry records for the frozen opener: its name, the arm it was read on and the drone equivalents
     * as living drones, structures and lost drones.
     */
    public String getEvidenceLabel() {
        return evidenceLabel;
    }

    /**
     * Whether a depot has been seen at the enemy natural on any frame so far.
     */
    public boolean naturalDepotSeen() {
        return naturalDepotFirstSeen != null;
    }

    /**
     * Latches the first frame a living depot is seen at the enemy natural. The tracker forgets a destroyed
     * depot's position, so the latch is what keeps a natural that later died counting as taken.
     */
    void observeNaturalDepot(boolean naturalHasDepotNow, Time time) {
        if (naturalHasDepotNow && naturalDepotFirstSeen == null) {
            naturalDepotFirstSeen = time;
        }
    }

    private ZergOpenerEvidence evidence(StrategyDetectionContext context, int equivalents) {
        ObservedUnitTracker tracker = context.getTracker();
        return ZergOpenerEvidence.builder()
                .time(context.getTime())
                .equivalents(equivalents)
                .earlyPool(tracker.hasObservedAsTypeBy(UnitType.Zerg_Spawning_Pool, EARLY_POOL_SEEN_BY)
                        || tracker.getUnitTypeCountCompletedBeforeTime(UnitType.Zerg_Spawning_Pool,
                        EARLY_POOL_COMPLETED_BY) > 0
                        || tracker.hasObservedAsTypeBy(UnitType.Zerg_Zergling, EARLY_ZERGLING_BY))
                .latePool(tracker.hasObservedIncompleteSince(UnitType.Zerg_Spawning_Pool, LATE_POOL_MORPHING_FROM))
                .poolSeen(tracker.hasObservedAsTypeBy(UnitType.Zerg_Spawning_Pool, context.getTime()))
                .mainScouted(context.enemyMainScoutedFrame() != null)
                .naturalLastSeen(context.enemyNaturalLastSeenFrame())
                .naturalDepotFirstSeen(naturalDepotFirstSeen)
                .build();
    }

    /**
     * The opener the evidence supports, or null when it supports none.
     * <ul>
     *     <li>9Pool: a sign the Pool was started before any 12 pool starts one, with at least
     *     {@link #NINE_POOL_MIN_EQUIVALENTS} equivalents; or, from {@link #NINE_POOL_BAND_FROM}, a Pool with
     *     {@link #NINE_POOL_MIN_EQUIVALENTS} to {@link #NINE_POOL_MAX_EQUIVALENTS} equivalents and no natural
     *     depot.</li>
     *     <li>12Hatch: at least {@link #TWELVE_MIN_EQUIVALENTS} equivalents and a natural depot that went down
     *     before its Pool, shown by the depot being seen before any 12 pool expands or by a scouted main with no
     *     Pool.</li>
     *     <li>12Pool: at least {@link #TWELVE_MIN_EQUIVALENTS} equivalents, a Pool still morphing after every 9
     *     pool has finished its own, and the natural seen empty at that time or later.</li>
     * </ul>
     */
    static ZergOpener classify(ZergOpenerEvidence evidence) {
        Time time = evidence.getTime();
        if (time.greaterThan(DECISION_CUTOFF)) {
            return null;
        }
        int equivalents = evidence.getEquivalents();
        Time naturalDepotFirstSeen = evidence.getNaturalDepotFirstSeen();
        if (evidence.isEarlyPool()) {
            return equivalents >= NINE_POOL_MIN_EQUIVALENTS ? ZergOpener.NINE_POOL : null;
        }
        if (evidence.isPoolSeen() && naturalDepotFirstSeen == null && NINE_POOL_BAND_FROM.lessThanOrEqual(time)
                && equivalents >= NINE_POOL_MIN_EQUIVALENTS && equivalents <= NINE_POOL_MAX_EQUIVALENTS) {
            return ZergOpener.NINE_POOL;
        }
        if (equivalents < TWELVE_MIN_EQUIVALENTS) {
            return null;
        }
        if (naturalDepotFirstSeen != null) {
            boolean hatchFirst = naturalDepotFirstSeen.lessThanOrEqual(HATCH_BEFORE_ANY_POOL_FIRST_BY)
                    || evidence.isMainScouted() && !evidence.isPoolSeen();
            return hatchFirst ? ZergOpener.TWELVE_HATCH : null;
        }
        Time naturalLastSeen = evidence.getNaturalLastSeen();
        if (evidence.isLatePool() && naturalLastSeen != null
                && LATE_POOL_MORPHING_FROM.lessThanOrEqual(naturalLastSeen)) {
            return ZergOpener.TWELVE_POOL;
        }
        return null;
    }

    static String label(ZergOpener opener, ZergOpenerEvidence evidence, DroneEquivalents equivalents) {
        String arm = evidence.isEarlyPool() ? "EARLY_POOL" : "BAND";
        return opener.getStrategyName() + ":" + arm + ":" + equivalents.getDrones() + "d+"
                + equivalents.getStructures() + "s+" + equivalents.getLostDrones() + "k";
    }
}
