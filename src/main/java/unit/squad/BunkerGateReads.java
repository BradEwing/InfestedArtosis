package unit.squad;

import telemetry.BunkerAdvanceEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The frame and branch of the latest read the repeat-advance gate made of each ground squad, see
 * {@link BunkerAdvanceGate}.
 *
 * <p>A read stands only for the Bunker losses on record when it was made. A squad, or a squad formed from it, is read
 * again once a loss is booked after its read, so a lineage the gate let through before a loss is gated like any other
 * squad after it.
 */
public final class BunkerGateReads {

    /**
     * One read of a squad.
     */
    public static final class Read {
        private final BunkerAdvanceEntry entry;
        private final int frame;

        Read(BunkerAdvanceEntry entry, int frame) {
            this.entry = entry;
            this.frame = frame;
        }

        /**
         * @return the branch that read the squad
         */
        public BunkerAdvanceEntry getEntry() {
            return entry;
        }

        /**
         * @return the frame of the read
         */
        public int getFrame() {
            return frame;
        }
    }

    private final Map<String, Read> reads = new HashMap<>();

    /**
     * Records a read of a squad.
     *
     * @param squadId the squad's id
     * @param entry the branch that read it
     * @param frame the frame of the read
     */
    public void record(String squadId, BunkerAdvanceEntry entry, int frame) {
        reads.put(squadId, new Read(entry, frame));
    }

    /**
     * @param squadId a squad's id
     * @return the latest read of the squad, or null when the gate never read it or a squad it was formed from
     */
    public Read get(String squadId) {
        return reads.get(squadId);
    }

    /**
     * @param squadId a squad's id
     * @param latestLossFrame the frame of the latest loss on record in range of the squad, or
     *     {@link BunkerLossLedger#NONE} when none is
     * @return whether the squad was read after that loss
     */
    public boolean readSince(String squadId, int latestLossFrame) {
        Read read = reads.get(squadId);
        return read != null && read.frame > latestLossFrame;
    }

    /**
     * Gives a squad formed from others the latest read of its sources. A merge retires its sources; a split keeps
     * the parent.
     *
     * @param sourceIds the ids of the squads the new squad was formed from
     * @param targetId the id of the new squad
     * @param retire whether the sources are gone, so their reads are dropped
     */
    public void carry(Collection<String> sourceIds, String targetId, boolean retire) {
        Read latest = null;
        for (String sourceId : sourceIds) {
            Read read = reads.get(sourceId);
            if (read != null && (latest == null || read.frame > latest.frame)) {
                latest = read;
            }
            if (retire) {
                reads.remove(sourceId);
            }
        }
        if (latest != null) {
            reads.put(targetId, latest);
        }
    }
}
