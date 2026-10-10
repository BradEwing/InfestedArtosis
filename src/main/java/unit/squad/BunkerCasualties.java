package unit.squad;

import bwapi.Position;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ground combat units of ours that died within {@link #RADIUS} of an enemy Bunker, kept per Bunker for the game
 * so a retreat is booked as a loss at a Bunker only after units were actually lost there, see
 * {@link BunkerLossLedger}.
 */
public final class BunkerCasualties {

    /**
     * The farthest, in pixels, from a Bunker that a death is taken for a death at that Bunker.
     */
    public static final int RADIUS = 288;

    private static final class Death {
        private final int frame;
        private final String squadId;

        private Death(int frame, String squadId) {
            this.frame = frame;
            this.squadId = squadId;
        }
    }

    private final Map<Position, List<Death>> deaths = new HashMap<>();

    /**
     * Books a death of a unit that was in no squad, see {@link #recordDeath(Position, Collection, int, String)}.
     *
     * @param death where the unit died
     * @param bunkers where the living Bunkers stand
     * @param frame the frame of the death
     */
    public void recordDeath(Position death, Collection<Position> bunkers, int frame) {
        recordDeath(death, bunkers, frame, null);
    }

    /**
     * Books a death at the nearest Bunker within {@link #RADIUS} of it, if any stands.
     *
     * @param death where the unit died
     * @param bunkers where the living Bunkers stand
     * @param frame the frame of the death
     * @param squadId the id of the squad the unit was a member of, or null when it was in none
     */
    public void recordDeath(Position death, Collection<Position> bunkers, int frame, String squadId) {
        Position nearest = null;
        double best = RADIUS;
        for (Position bunker : bunkers) {
            double distance = death.getDistance(bunker);
            if (distance <= best) {
                best = distance;
                nearest = bunker;
            }
        }
        if (nearest != null) {
            deaths.computeIfAbsent(nearest, key -> new ArrayList<>()).add(new Death(frame, squadId));
        }
    }

    /**
     * @param bunker where a Bunker stands
     * @param sinceFrame the first frame that counts
     * @return the units of ours that died at the Bunker on or after the frame
     */
    public int lostSince(Position bunker, int sinceFrame) {
        int lost = 0;
        for (Death death : deaths.getOrDefault(bunker, new ArrayList<>())) {
            if (death.frame >= sinceFrame) {
                lost++;
            }
        }
        return lost;
    }

    /**
     * @param bunker where a Bunker stands
     * @param sinceFrame the first frame that counts
     * @param squadId the id of a squad
     * @return the units that died at the Bunker on or after the frame while members of that squad
     */
    public int lostSince(Position bunker, int sinceFrame, String squadId) {
        int lost = 0;
        for (Death death : deaths.getOrDefault(bunker, new ArrayList<>())) {
            if (death.frame >= sinceFrame && squadId.equals(death.squadId)) {
                lost++;
            }
        }
        return lost;
    }
}
