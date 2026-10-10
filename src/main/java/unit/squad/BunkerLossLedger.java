package unit.squad;

import bwapi.Position;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Bunkers a mostly melee squad retreated from while the combat sim priced them, kept for the game so no squad
 * marches back into one it has been priced as unable to break, see {@link BunkerAdvanceGate}.
 *
 * <p>One entry stands per Bunker, keyed by where it stands. An entry holds the frame of the latest retreat, the
 * Bunker's hit points when the loss was first recorded, the enemy strength the retreat was priced against, and the
 * strength ratio a squad must reach to be priced as breaking it.
 */
public final class BunkerLossLedger {

    /**
     * One remembered loss.
     */
    static final class Entry {
        private final Position position;
        private final int bunkerId;
        private final int baselineHitPoints;
        private int frame;
        private double price;
        private double releaseRatio;

        private Entry(Position position, int bunkerId, int frame, int hitPoints, double price, double releaseRatio) {
            this.position = position;
            this.bunkerId = bunkerId;
            this.baselineHitPoints = hitPoints;
            this.frame = frame;
            this.price = price;
            this.releaseRatio = releaseRatio;
        }

        Position getPosition() {
            return position;
        }

        int getBunkerId() {
            return bunkerId;
        }

        int getBaselineHitPoints() {
            return baselineHitPoints;
        }

        int getFrame() {
            return frame;
        }

        double getPrice() {
            return price;
        }

        double getReleaseRatio() {
            return releaseRatio;
        }
    }

    /**
     * The frame reported when no loss is on record.
     */
    public static final int NONE = Integer.MIN_VALUE;

    private final Map<Position, Entry> entries = new LinkedHashMap<>();

    /**
     * Records a retreat from a priced Bunker. A Bunker already on record keeps the hit points it was first recorded
     * at, so damage is read against the first loss, and the larger of the prices, so a thinner sample does not lower
     * the bar. A Bunker with another id at the same spot is a rebuilt one and starts a new record.
     *
     * @param bunker where the Bunker stands or was last seen
     * @param bunkerId the Bunker's unit id
     * @param frame the frame of the retreat
     * @param hitPoints the Bunker's hit points as last seen
     * @param enemyPrice the enemy strength the sim priced the retreat against
     * @param releaseRatio the strength ratio at which a squad is priced as breaking the Bunker
     */
    public void record(Position bunker, int bunkerId, int frame, int hitPoints, double enemyPrice,
                       double releaseRatio) {
        Entry existing = entries.get(bunker);
        if (existing == null || existing.bunkerId != bunkerId) {
            entries.put(bunker, new Entry(bunker, bunkerId, frame, hitPoints, enemyPrice, releaseRatio));
            return;
        }
        existing.frame = frame;
        existing.price = Math.max(existing.price, enemyPrice);
        existing.releaseRatio = releaseRatio;
    }

    /**
     * @param center a position
     * @param range the farthest distance, in pixels
     * @return the entries within the range of the position, nearest first
     */
    List<Entry> near(Position center, double range) {
        List<Entry> near = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (center.getDistance(entry.position) <= range) {
                near.add(entry);
            }
        }
        near.sort(Comparator.comparingDouble(entry -> center.getDistance(entry.position)));
        return near;
    }

    /**
     * @param center a position
     * @param range the farthest distance, in pixels
     * @return where each Bunker on record within the range of the position stands, mapped to the frame of its latest
     *     retreat
     */
    public Map<Position, Integer> framesNear(Position center, double range) {
        Map<Position, Integer> frames = new LinkedHashMap<>();
        for (Entry entry : entries.values()) {
            if (center.getDistance(entry.position) <= range) {
                frames.put(entry.position, entry.frame);
            }
        }
        return frames;
    }

    /**
     * @param bunker where a Bunker stands or was last seen
     * @return the frame of the latest retreat on record at it, or {@link #NONE} when none is
     */
    public int frameAt(Position bunker) {
        Entry entry = entries.get(bunker);
        return entry == null ? NONE : entry.frame;
    }

    void remove(Entry entry) {
        entries.remove(entry.position);
    }

    /**
     * @param bunker where a Bunker stands or was last seen
     * @return whether a loss against it is on record
     */
    public boolean holds(Position bunker) {
        return entries.containsKey(bunker);
    }

    /**
     * @return the number of Bunkers on record
     */
    public int size() {
        return entries.size();
    }
}
