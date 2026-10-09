package unit.squad;

import bwapi.Position;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;

/**
 * Picks where a parked Overlord waits. It rallies to the center of the nearest completed Spore Colony we own,
 * measured from the Overlord, and to the main base position when there is none.
 */
public final class OverlordParking {

    /** Why a parked Overlord's anchor changed. */
    public enum Reason {
        /** The Overlord had no anchor yet. */
        ASSIGNED,
        /** The anchor was the main base and a Spore Colony completed. */
        SPORE_COMPLETED,
        /** The previous Spore Colony still stands and another is now nearer. */
        NEARER_SPORE,
        /** The previous Spore Colony no longer stands. */
        SPORE_LOST
    }

    /** Pixels from its anchor within which a parked Overlord idles instead of moving. */
    public static final double ARRIVE_DISTANCE = 16;

    /** One parked Overlord decision for a frame. */
    public static final class Decision {
        public final int unitId;
        public final Position position;
        public final Position anchor;
        public final Position previous;
        public final Reason reason;
        public final boolean idle;

        Decision(int unitId, Position position, Position anchor, Position previous, Reason reason, boolean idle) {
            this.unitId = unitId;
            this.position = position;
            this.anchor = anchor;
            this.previous = previous;
            this.reason = reason;
            this.idle = idle;
        }

        /**
         * @return true when the anchor differs from the one the Overlord had
         */
        public boolean changed() {
            return reason != null;
        }
    }

    private OverlordParking() {
    }

    /**
     * Decides every parked Overlord anchor for a frame. The anchor map is updated in place: it gains the new
     * anchors and loses the entries of Overlords that are no longer parked.
     *
     * @param parked position of each parked Overlord by unit id
     * @param anchors the anchor each Overlord had last frame by unit id
     * @param spores centers of our completed Spore Colonies
     * @param main the main base position
     * @param distanceToAnchor the distance from the Overlord with the given id to a position
     * @return one decision per parked Overlord, in the order of the parked map
     */
    public static List<Decision> plan(Map<Integer, Position> parked, Map<Integer, Position> anchors,
                                      List<Position> spores, Position main,
                                      ToDoubleBiFunction<Integer, Position> distanceToAnchor) {
        List<Decision> decisions = new ArrayList<>();
        for (Map.Entry<Integer, Position> entry : parked.entrySet()) {
            int id = entry.getKey();
            Position anchor = pickAnchor(entry.getValue(), spores, main);
            Position previous = anchors.put(id, anchor);
            Reason reason = anchor.equals(previous) ? null : reason(previous, spores, main);
            boolean idle = distanceToAnchor.applyAsDouble(id, anchor) < ARRIVE_DISTANCE;
            decisions.add(new Decision(id, entry.getValue(), anchor, previous, reason, idle));
        }
        anchors.keySet().retainAll(parked.keySet());
        return decisions;
    }

    /**
     * @param overlord the parked Overlord's position
     * @param spores centers of our completed Spore Colonies
     * @param main the main base position
     * @return the center of the Spore Colony nearest the Overlord, the first listed on a tie, or main with none
     */
    public static Position pickAnchor(Position overlord, List<Position> spores, Position main) {
        Position best = main;
        double bestDistance = Double.MAX_VALUE;
        for (Position spore : spores) {
            double distance = overlord.getDistance(spore);
            if (distance < bestDistance) {
                best = spore;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * @param previous the anchor the Overlord had, or null for none
     * @param spores centers of our completed Spore Colonies
     * @param main the main base position
     * @return why the anchor changed
     */
    public static Reason reason(Position previous, List<Position> spores, Position main) {
        if (previous == null) {
            return Reason.ASSIGNED;
        }
        if (spores.contains(previous)) {
            return Reason.NEARER_SPORE;
        }
        if (previous.equals(main)) {
            return Reason.SPORE_COMPLETED;
        }
        return Reason.SPORE_LOST;
    }

    /**
     * @param overlord a position
     * @param spores centers of our completed Spore Colonies
     * @return the distance to the nearest Spore Colony, or -1 with none
     */
    public static double nearestSporeDistance(Position overlord, List<Position> spores) {
        double best = -1;
        for (Position spore : spores) {
            double distance = overlord.getDistance(spore);
            if (best < 0 || distance < best) {
                best = distance;
            }
        }
        return best;
    }
}
