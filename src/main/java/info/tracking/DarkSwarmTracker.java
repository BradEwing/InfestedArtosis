package info.tracking;

import bwapi.Position;
import bwapi.Race;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the Dark Swarms our Defilers have cast, from the Spell_Dark_Swarm units in sight each frame.
 *
 * <p>A swarm lasts {@link #SWARM_DURATION_FRAMES} frames and its unit reports the time it has left. The tracker holds
 * exactly the swarms sighted on the latest frame, so a swarm is dropped on the frame its unit is removed.
 *
 * <p>It also holds each cast a Defiler has ordered for {@link #PENDING_CAST_FRAMES}, so a second Defiler sees a swarm
 * that is on its way before its unit exists and casts elsewhere.
 */
public class DarkSwarmTracker {

    public static final int SWARM_DURATION_FRAMES = 900;

    /**
     * Tuning value: frames an ordered cast is held as pending, covering the time from the order to the swarm's unit
     * appearing. Two casts on one spot were seen from the same frame to 48 frames apart.
     */
    public static final int PENDING_CAST_FRAMES = 48;

    /**
     * Id of the footprint a pending cast is reported as, which no Spell_Dark_Swarm unit carries.
     */
    public static final int PENDING_CAST_ID = -1;

    private final Map<Integer, DarkSwarm> activeSwarms = new LinkedHashMap<>();
    private final List<PendingCast> pendingCasts = new ArrayList<>();

    /**
     * Replaces the tracked swarms with the friendly swarms sighted this frame.
     *
     * @param sighted friendly swarms in sight this frame, see {@link #isFriendly}
     */
    public void update(Collection<DarkSwarm> sighted) {
        activeSwarms.clear();
        for (DarkSwarm swarm : sighted) {
            activeSwarms.put(swarm.getId(), swarm);
        }
    }

    /**
     * Records a Dark Swarm a Defiler has just been ordered to cast, and drops the casts older than
     * {@link #PENDING_CAST_FRAMES}.
     *
     * @param point the cast position
     * @param frame the frame of the order
     */
    public void recordCast(Position point, int frame) {
        pendingCasts.removeIf(cast -> frame - cast.frame >= PENDING_CAST_FRAMES);
        pendingCasts.add(new PendingCast(point, frame));
    }

    /**
     * The casts ordered within the last {@link #PENDING_CAST_FRAMES}, each as the footprint its swarm will take, with
     * id {@link #PENDING_CAST_ID} and a full {@link #SWARM_DURATION_FRAMES} left.
     *
     * @param frame the current frame
     * @return the pending casts' footprints
     */
    public List<DarkSwarm> getPendingCasts(int frame) {
        List<DarkSwarm> footprints = new ArrayList<>();
        for (PendingCast cast : pendingCasts) {
            if (frame - cast.frame < PENDING_CAST_FRAMES) {
                footprints.add(new DarkSwarm(PENDING_CAST_ID, cast.point, SWARM_DURATION_FRAMES));
            }
        }
        return footprints;
    }

    public List<DarkSwarm> getActiveSwarms() {
        return Collections.unmodifiableList(new ArrayList<>(activeSwarms.values()));
    }

    public int getActiveSwarmCount() {
        return activeSwarms.size();
    }

    /**
     * @param id the Spell_Dark_Swarm unit's id
     * @return the active swarm with that id, or null once it has been removed
     */
    public DarkSwarm getSwarm(int id) {
        return activeSwarms.get(id);
    }

    /**
     * Frames a tracked swarm has left.
     *
     * @param id the Spell_Dark_Swarm unit's id
     * @return its remaining frames, or 0 once it has been removed
     */
    public int getRemainingFrames(int id) {
        DarkSwarm swarm = activeSwarms.get(id);
        return swarm == null ? 0 : swarm.getRemainingFrames();
    }

    /**
     * Whether a Spell_Dark_Swarm unit is one of ours.
     *
     * <p>A swarm we own is ours and one the enemy owns is not. A swarm owned by neither is attributed by the
     * opponent's race: only a Zerg can cast one, so against a Terran or Protoss it is ours, and against a Zerg or
     * an opponent whose race is not yet known it is not claimed.
     *
     * @param ownedBySelf whether our player owns the unit
     * @param ownedByEnemy whether an enemy player owns the unit
     * @param opponentRace the opponent's race as currently known
     * @return true when the swarm is ours
     */
    public static boolean isFriendly(boolean ownedBySelf, boolean ownedByEnemy, Race opponentRace) {
        if (ownedBySelf) {
            return true;
        }
        if (ownedByEnemy) {
            return false;
        }
        return opponentRace == Race.Terran || opponentRace == Race.Protoss;
    }

    private static final class PendingCast {
        private final Position point;
        private final int frame;

        private PendingCast(Position point, int frame) {
            this.point = point;
            this.frame = frame;
        }
    }
}
