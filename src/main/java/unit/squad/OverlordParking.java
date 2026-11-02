package unit.squad;

import bwapi.Position;
import info.BaseData;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;

/**
 * Picks where a parked Overlord waits. It rallies to the center of a completed Spore Colony we own that is within
 * {@link #MAX_ANCHOR_DISTANCE} of it, preferring one at the natural, then the nearest, and to the main base position
 * when there is none. An Overlord is never sent to a Spore Colony beyond that distance.
 */
public final class OverlordParking {

    /** Why a parked Overlord's anchor changed. */
    public enum Reason {
        /** The Overlord had no anchor yet. */
        ASSIGNED,
        /** The anchor was the main base and the new anchor's Spore Colony completed since the last frame. */
        SPORE_COMPLETED,
        /** The anchor was the main base and a Spore Colony that already stood came within reach. */
        IN_REACH,
        /** The previous Spore Colony still stands and another is now preferred, by reach or by distance. */
        NEARER_SPORE,
        /** The previous Spore Colony no longer stands. */
        SPORE_LOST,
        /** The previous Spore Colony still stands and is now beyond reach, so the anchor is the main base. */
        OUT_OF_REACH
    }

    /**
     * Pixels from a parked Overlord beyond which a Spore Colony is not an anchor. It equals
     * {@link SquadManager#AIR_SPLIT_DISTANCE}, the range inside which the squad code treats air units as one group;
     * reroutes of more than 1500 pixels were the ones that lost Overlords in transit, and this is about half that.
     * The natural's Spore Colony is also within reach of an Overlord this close to the main or the natural.
     */
    public static final double MAX_ANCHOR_DISTANCE = SquadManager.AIR_SPLIT_DISTANCE;

    /** Pixels from the natural's center within which a Spore Colony counts as the natural's. */
    public static final double NATURAL_SPORE_RADIUS = BaseData.NATURAL_DEFENSE_TILE_RADIUS * 32;

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
     * @param role an Overlord's role
     * @return true when the role is one a parked Overlord holds, so the Overlord may be given an anchor
     */
    public static boolean isParkedRole(UnitRole role) {
        return role == UnitRole.IDLE || role == UnitRole.RALLY;
    }

    /**
     * @param inOverlordSquad whether the Overlord is a member of the Overlord squad
     * @param role the Overlord's role
     * @return true when the Overlord counts as parked: in the Overlord squad and holding a parked role
     */
    public static boolean isParked(boolean inOverlordSquad, UnitRole role) {
        return inOverlordSquad && isParkedRole(role);
    }

    /**
     * Decides every parked Overlord anchor for a frame. The anchor map is updated in place: it gains the new
     * anchors and loses the entries of Overlords that are no longer parked.
     *
     * @param parked position of each parked Overlord by unit id
     * @param anchors the anchor each Overlord had last frame by unit id
     * @param spores centers of our completed Spore Colonies
     * @param previousSpores centers of our completed Spore Colonies last frame
     * @param main the main base position
     * @param natural the natural's center, or null when it is not known
     * @param distanceToAnchor the distance from the Overlord with the given id to a position
     * @return one decision per parked Overlord, in the order of the parked map
     */
    public static List<Decision> plan(Map<Integer, Position> parked, Map<Integer, Position> anchors,
                                      List<Position> spores, List<Position> previousSpores,
                                      Position main, Position natural, ToDoubleBiFunction<Integer, Position> distanceToAnchor) {
        List<Decision> decisions = new ArrayList<>();
        for (Map.Entry<Integer, Position> entry : parked.entrySet()) {
            int id = entry.getKey();
            Position anchor = pickAnchor(entry.getValue(), spores, main, natural);
            Position previous = anchors.put(id, anchor);
            Reason reason = anchor.equals(previous) ? null : reason(previous, anchor, spores, previousSpores, main);
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
     * @param natural the natural's center, or null when it is not known
     * @return the center of a Spore Colony at the natural within reach of the Overlord, else the nearest Spore
     *     Colony within {@link #MAX_ANCHOR_DISTANCE}, the first listed on a tie, else main
     */
    public static Position pickAnchor(Position overlord, List<Position> spores, Position main, Position natural) {
        boolean nearBase = overlord.getDistance(main) <= MAX_ANCHOR_DISTANCE
                || natural != null && overlord.getDistance(natural) <= MAX_ANCHOR_DISTANCE;
        Position best = null;
        double bestDistance = Double.MAX_VALUE;
        Position bestNatural = null;
        double bestNaturalDistance = Double.MAX_VALUE;
        for (Position spore : spores) {
            double distance = overlord.getDistance(spore);
            boolean atNatural = natural != null && spore.getDistance(natural) <= NATURAL_SPORE_RADIUS;
            if (atNatural && (nearBase || distance <= MAX_ANCHOR_DISTANCE) && distance < bestNaturalDistance) {
                bestNatural = spore;
                bestNaturalDistance = distance;
            }
            if (distance <= MAX_ANCHOR_DISTANCE && distance < bestDistance) {
                best = spore;
                bestDistance = distance;
            }
        }
        if (bestNatural != null) {
            return bestNatural;
        }
        return best == null ? main : best;
    }

    /**
     * @param previous the anchor the Overlord had, or null for none
     * @param anchor the anchor it has now
     * @param spores centers of our completed Spore Colonies
     * @param previousSpores centers of our completed Spore Colonies last frame
     * @param main the main base position
     * @return why the anchor changed
     */
    public static Reason reason(Position previous, Position anchor, List<Position> spores,
                                List<Position> previousSpores, Position main) {
        if (previous == null) {
            return Reason.ASSIGNED;
        }
        if (spores.contains(previous)) {
            return anchor.equals(main) ? Reason.OUT_OF_REACH : Reason.NEARER_SPORE;
        }
        if (previous.equals(main)) {
            return previousSpores.contains(anchor) ? Reason.IN_REACH : Reason.SPORE_COMPLETED;
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
