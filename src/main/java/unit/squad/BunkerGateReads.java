package unit.squad;

import bwapi.Position;
import telemetry.BunkerAdvanceEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * What the repeat-advance gate weighed when it read each ground squad, see {@link BunkerAdvanceGate}.
 *
 * <p>A read stands for one Bunker's loss only when it weighed that Bunker's record after the loss was booked, and,
 * when the squad was not mostly melee at the time, only for a squad that is still not mostly melee. A squad, or a
 * squad formed from it, is read again once a loss is booked after its read or at a Bunker the read never weighed, so
 * a lineage the gate let through before a loss is gated like any other squad after it.
 */
public final class BunkerGateReads {

    private static final class Mark {
        private final int frame;
        private final boolean melee;

        private Mark(int frame, boolean melee) {
            this.frame = frame;
            this.melee = melee;
        }
    }

    /**
     * One squad's latest read.
     */
    public static final class Read {
        private BunkerAdvanceEntry entry;
        private int frame;
        private final Map<Position, Mark> weighed = new HashMap<>();

        private Read(BunkerAdvanceEntry entry, int frame) {
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

        private boolean weighedSince(Position bunker, int lossFrame, boolean squadMelee, boolean inclusive) {
            Mark mark = weighed.get(bunker);
            if (mark == null) {
                return false;
            }
            boolean later = inclusive ? mark.frame >= lossFrame : mark.frame > lossFrame;
            return later && (mark.melee || !squadMelee);
        }

        private void weigh(Position bunker, int markFrame, boolean melee) {
            Mark existing = weighed.get(bunker);
            if (existing == null || markFrame > existing.frame
                    || markFrame == existing.frame && existing.melee && !melee) {
                weighed.put(bunker, new Mark(markFrame, melee));
            }
        }
    }

    private final Map<String, Read> reads = new HashMap<>();

    /**
     * Records a read of a squad, replacing what the gate weighed for the Bunkers it weighed this time and keeping the
     * rest.
     *
     * @param squadId the squad's id
     * @param entry the branch that read it
     * @param frame the frame of the read
     * @param weighed where the Bunkers stand whose records the read weighed
     * @param melee whether the squad was mostly melee, so the read weighed the records against it
     */
    public void record(String squadId, BunkerAdvanceEntry entry, int frame, Collection<Position> weighed,
                       boolean melee) {
        Read read = reads.get(squadId);
        if (read == null) {
            read = new Read(entry, frame);
            reads.put(squadId, read);
        }
        read.entry = entry;
        read.frame = frame;
        for (Position bunker : weighed) {
            read.weigh(bunker, frame, melee);
        }
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
     * @param losses where each Bunker with a loss on record stands, mapped to the frame of its latest loss
     * @param squadMelee whether the squad is mostly melee now
     * @return whether the squad was read, and the read weighed every one of those records after its loss was booked
     */
    public boolean readSince(String squadId, Map<Position, Integer> losses, boolean squadMelee) {
        Read read = reads.get(squadId);
        if (read == null) {
            return false;
        }
        for (Map.Entry<Position, Integer> loss : losses.entrySet()) {
            if (!read.weighedSince(loss.getKey(), loss.getValue(), squadMelee, false)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param squadId a squad's id
     * @param bunker where a Bunker stands
     * @param lossFrame the frame of its latest loss on record
     * @param squadMelee whether the squad is mostly melee now
     * @return whether the squad's read weighed that record on or after the frame the loss was booked
     */
    public boolean weighedAtOrAfter(String squadId, Position bunker, int lossFrame, boolean squadMelee) {
        Read read = reads.get(squadId);
        return read != null && read.weighedSince(bunker, lossFrame, squadMelee, true);
    }

    /**
     * Gives a squad formed from others the latest read of its sources and, for each Bunker, the latest weighing of
     * it by any source. A merge retires its sources; a split keeps the parent.
     *
     * @param sourceIds the ids of the squads the new squad was formed from
     * @param targetId the id of the new squad
     * @param retire whether the sources are gone, so their reads are dropped
     */
    public void carry(Collection<String> sourceIds, String targetId, boolean retire) {
        Read carried = null;
        for (String sourceId : sourceIds) {
            Read source = reads.get(sourceId);
            if (source != null) {
                if (carried == null) {
                    carried = new Read(source.entry, source.frame);
                } else if (source.frame > carried.frame) {
                    carried.entry = source.entry;
                    carried.frame = source.frame;
                }
                for (Map.Entry<Position, Mark> mark : source.weighed.entrySet()) {
                    carried.weigh(mark.getKey(), mark.getValue().frame, mark.getValue().melee);
                }
            }
            if (retire) {
                reads.remove(sourceId);
            }
        }
        if (carried != null) {
            reads.put(targetId, carried);
        }
    }
}
