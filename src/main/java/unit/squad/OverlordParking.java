package unit.squad;

import bwapi.Position;

import java.util.List;

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

    private OverlordParking() {
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
