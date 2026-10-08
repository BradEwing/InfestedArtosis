package telemetry;

import bwapi.Position;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks our engagements at enemy Bunkers for the BUNKER_ENGAGEMENT rows.
 *
 * <p>An engagement at a Bunker opens when at least {@link #MIN_UNITS} of our ground combat units stand within
 * {@link #RADIUS} of it. It closes broken when the Bunker stops being a living observed Bunker, and otherwise
 * {@link #GAP_FRAMES} after the last frame one of our units stood within the radius. Our units that die within the
 * radius of an open engagement count as lost by us, Zerglings among them counted again as lings, and enemy units
 * that are not buildings and die within it count as lost by the enemy. A death is credited to the nearest open
 * engagement whose Bunker is within the radius.
 */
public final class BunkerEngagements {

    /**
     * Pixels from a Bunker within which our units count as in its engagement.
     */
    static final int RADIUS = 288;

    /**
     * Units within {@link #RADIUS} that open an engagement.
     */
    static final int MIN_UNITS = 4;

    /**
     * Frames without a unit of ours within {@link #RADIUS} that close an engagement.
     */
    static final int GAP_FRAMES = 240;

    /**
     * A living observed Bunker.
     */
    public static final class BunkerSample {
        private final int id;
        private final Position position;
        private final int hitPoints;

        public BunkerSample(int id, Position position, int hitPoints) {
            this.id = id;
            this.position = position;
            this.hitPoints = hitPoints;
        }
    }

    /**
     * One of our ground combat units.
     */
    public static final class OurUnit {
        private final Position position;

        public OurUnit(Position position) {
            this.position = position;
        }
    }

    /**
     * A closed engagement.
     */
    public static final class Closed {
        private final int id;
        private final int bunkerId;
        private final Position bunker;
        private final int startFrame;
        private final int endFrame;
        private final int ourLost;
        private final int lingsLost;
        private final int enemyLost;
        private final int hitPointsStart;
        private final int hitPointsEnd;
        private final boolean broken;

        private Closed(Open open, int endFrame, boolean broken) {
            this.id = open.id;
            this.bunkerId = open.bunkerId;
            this.bunker = open.bunker;
            this.startFrame = open.startFrame;
            this.endFrame = endFrame;
            this.ourLost = open.ourLost;
            this.lingsLost = open.lingsLost;
            this.enemyLost = open.enemyLost;
            this.hitPointsStart = open.hitPointsStart;
            this.hitPointsEnd = broken ? 0 : open.hitPointsLast;
            this.broken = broken;
        }

        public int getId() {
            return id;
        }

        public int getBunkerId() {
            return bunkerId;
        }

        public Position getBunker() {
            return bunker;
        }

        public int getStartFrame() {
            return startFrame;
        }

        public int getEndFrame() {
            return endFrame;
        }

        public int getOurLost() {
            return ourLost;
        }

        public int getLingsLost() {
            return lingsLost;
        }

        public int getEnemyLost() {
            return enemyLost;
        }

        public int getHitPointsStart() {
            return hitPointsStart;
        }

        public int getHitPointsEnd() {
            return hitPointsEnd;
        }

        public boolean isBroken() {
            return broken;
        }
    }

    private static final class Open {
        private final int id;
        private final int bunkerId;
        private final Position bunker;
        private final int startFrame;
        private final int hitPointsStart;
        private int lastActiveFrame;
        private int hitPointsLast;
        private int ourLost;
        private int lingsLost;
        private int enemyLost;

        private Open(int id, BunkerSample sample, int frame) {
            this.id = id;
            this.bunkerId = sample.id;
            this.bunker = sample.position;
            this.startFrame = frame;
            this.lastActiveFrame = frame;
            this.hitPointsStart = sample.hitPoints;
            this.hitPointsLast = sample.hitPoints;
        }
    }

    private final Map<Integer, Open> open = new LinkedHashMap<>();
    private int nextId = 1;

    /**
     * Advances the engagements one sample.
     *
     * @param frame the current frame
     * @param living the living observed Bunkers
     * @param ours our ground combat units
     * @return the engagements that closed this sample
     */
    public List<Closed> onSample(int frame, Collection<BunkerSample> living, Collection<OurUnit> ours) {
        List<Closed> closed = new ArrayList<>();
        Map<Integer, BunkerSample> byId = new LinkedHashMap<>();
        for (BunkerSample sample : living) {
            byId.put(sample.id, sample);
        }
        for (BunkerSample sample : living) {
            int near = 0;
            for (OurUnit unit : ours) {
                if (unit.position.getDistance(sample.position) <= RADIUS) {
                    near++;
                }
            }
            Open engagement = open.get(sample.id);
            if (engagement == null) {
                if (near >= MIN_UNITS) {
                    open.put(sample.id, new Open(nextId++, sample, frame));
                }
                continue;
            }
            engagement.hitPointsLast = sample.hitPoints;
            if (near > 0) {
                engagement.lastActiveFrame = frame;
            }
        }
        for (Iterator<Open> it = open.values().iterator(); it.hasNext(); ) {
            Open engagement = it.next();
            if (!byId.containsKey(engagement.bunkerId)) {
                closed.add(new Closed(engagement, frame, true));
                it.remove();
            } else if (frame - engagement.lastActiveFrame >= GAP_FRAMES) {
                closed.add(new Closed(engagement, frame, false));
                it.remove();
            }
        }
        return closed;
    }

    /**
     * Credits the death of one of our units to the nearest open engagement within {@link #RADIUS}.
     *
     * @param where where the unit died
     * @param ling whether it was a Zergling
     */
    public void onOurDeath(Position where, boolean ling) {
        Open engagement = nearest(where);
        if (engagement == null) {
            return;
        }
        engagement.ourLost++;
        if (ling) {
            engagement.lingsLost++;
        }
    }

    /**
     * Credits the death of an enemy unit that is not a building to the nearest open engagement within
     * {@link #RADIUS}.
     *
     * @param where where the unit died
     */
    public void onEnemyDeath(Position where) {
        Open engagement = nearest(where);
        if (engagement != null) {
            engagement.enemyLost++;
        }
    }

    /**
     * Closes every open engagement, as the game ends.
     *
     * @param frame the current frame
     * @return the engagements that were open, none of them broken
     */
    public List<Closed> finish(int frame) {
        List<Closed> closed = new ArrayList<>();
        for (Open engagement : open.values()) {
            closed.add(new Closed(engagement, frame, false));
        }
        open.clear();
        return closed;
    }

    private Open nearest(Position where) {
        Open nearest = null;
        double best = RADIUS;
        for (Open engagement : open.values()) {
            double distance = where.getDistance(engagement.bunker);
            if (distance <= best) {
                best = distance;
                nearest = engagement;
            }
        }
        return nearest;
    }
}
