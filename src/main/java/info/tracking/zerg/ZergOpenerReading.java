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
 *     <li>12Hatch starts the natural Hatchery from frame 2333, the earliest 12 pool starts it at 3426.</li>
 * </ul>
 * Drone equivalents are a lower bound that runs several Drones short by 3:00, so a count alone never reads an
 * opener: each label needs a timing sign, and a Pool seen with no timing sign reads as nothing at any count. A 4 or
 * 5 pool shows the early Pool sign too, so it reads 9Pool once its Drones resume past
 * {@link #NINE_POOL_MIN_EQUIVALENTS}.
 */
public class ZergOpenerReading {

    static final Time DECISION_CUTOFF = new Time(3, 0);
    static final Time EARLY_POOL_SEEN_BY = new Time(2050);
    static final Time EARLY_POOL_COMPLETED_BY = new Time(3250);
    static final Time EARLY_ZERGLING_BY = new Time(3700);
    static final Time LATE_POOL_MORPHING_FROM = new Time(3350);
    static final Time HATCH_BEFORE_ANY_POOL_FIRST_BY = new Time(3400);
    static final int NINE_POOL_MIN_EQUIVALENTS = 7;
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
            evidenceLabel = label(opener, equivalents);
        }
        return opener;
    }

    /**
     * What telemetry records for the frozen opener: its name, the timing sign it was read on and the drone
     * equivalents as living drones, structures and lost drones.
     */
    public String getEvidenceLabel() {
        return evidenceLabel;
    }

    /**
     * The first frame a depot was seen at the enemy natural, or null if none has been.
     */
    Time getNaturalDepotFirstSeen() {
        return naturalDepotFirstSeen;
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
     * The opener the evidence supports, or null when it supports none. 12Pool is checked first, so a Pool seen
     * morphing late is read as 12Pool whatever else was seen.
     * <ul>
     *     <li>12Pool: at least {@link #TWELVE_MIN_EQUIVALENTS} equivalents, no natural depot, a Pool still morphing
     *     after every 9 pool has finished its own, and the natural seen empty at that time or later.</li>
     *     <li>9Pool: a sign the Pool was started before any 12 pool starts one, with at least
     *     {@link #NINE_POOL_MIN_EQUIVALENTS} equivalents.</li>
     *     <li>12Hatch: at least {@link #TWELVE_MIN_EQUIVALENTS} equivalents and a natural depot that went down
     *     before its Pool, shown by the depot being seen before any 12 pool expands or by a scouted main with no
     *     Pool.</li>
     * </ul>
     */
    static ZergOpener classify(ZergOpenerEvidence evidence) {
        if (evidence.getTime().greaterThan(DECISION_CUTOFF)) {
            return null;
        }
        if (isTwelvePool(evidence)) {
            return ZergOpener.TWELVE_POOL;
        }
        int equivalents = evidence.getEquivalents();
        if (evidence.isEarlyPool()) {
            return equivalents >= NINE_POOL_MIN_EQUIVALENTS ? ZergOpener.NINE_POOL : null;
        }
        Time naturalDepotFirstSeen = evidence.getNaturalDepotFirstSeen();
        if (equivalents < TWELVE_MIN_EQUIVALENTS || naturalDepotFirstSeen == null) {
            return null;
        }
        boolean hatchFirst = naturalDepotFirstSeen.lessThanOrEqual(HATCH_BEFORE_ANY_POOL_FIRST_BY)
                || evidence.isMainScouted() && !evidence.isPoolSeen();
        return hatchFirst ? ZergOpener.TWELVE_HATCH : null;
    }

    private static boolean isTwelvePool(ZergOpenerEvidence evidence) {
        Time naturalLastSeen = evidence.getNaturalLastSeen();
        return evidence.getEquivalents() >= TWELVE_MIN_EQUIVALENTS
                && evidence.getNaturalDepotFirstSeen() == null
                && evidence.isLatePool()
                && naturalLastSeen != null
                && LATE_POOL_MORPHING_FROM.lessThanOrEqual(naturalLastSeen);
    }

    static String label(ZergOpener opener, DroneEquivalents equivalents) {
        return opener.getStrategyName() + ":" + opener.getSign() + ":" + equivalents.getDrones() + "d+"
                + equivalents.getStructures() + "s+" + equivalents.getLostDrones() + "k";
    }
}
