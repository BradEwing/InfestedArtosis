package info.tracking;

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
 */
public class DarkSwarmTracker {

    public static final int SWARM_DURATION_FRAMES = 900;

    private final Map<Integer, DarkSwarm> activeSwarms = new LinkedHashMap<>();

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
}
