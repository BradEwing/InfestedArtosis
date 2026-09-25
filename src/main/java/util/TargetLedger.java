package util;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The melee target assignments of every fight squad on one frame, with the enemy static defence that can fire on
 * ground units. Each melee attacker holds at most one entry: recording a new pick replaces the attacker's previous
 * one and releasing the attacker drops it, so an attacker targeted twice on a frame is counted once. The load an
 * attacker sees on a target leaves out its own entry.
 */
public final class TargetLedger {

    private final List<StaticDefenseZone> groundDefenseZones;
    private final Map<Integer, Integer> meleeTargetByAttacker = new HashMap<>();
    private final Map<Integer, Integer> meleeAssigned = new HashMap<>();

    /**
     * @param staticDefenseZones enemy static defence zones; those whose structure cannot fire on ground units
     *     are dropped
     */
    public TargetLedger(Collection<StaticDefenseZone> staticDefenseZones) {
        this.groundDefenseZones = new ArrayList<>();
        for (StaticDefenseZone zone : staticDefenseZones) {
            if (TargetScorer.canAttackType(zone.getStructure(), false)) {
                groundDefenseZones.add(zone);
            }
        }
    }

    /**
     * @return a ledger with no assignments and no static defence
     */
    public static TargetLedger empty() {
        return new TargetLedger(Collections.emptyList());
    }

    /**
     * @return melee attackers holding the target
     */
    public int meleeAssigned(int targetId) {
        return meleeAssigned.getOrDefault(targetId, 0);
    }

    /**
     * @return melee attackers holding the target, not counting the given attacker
     */
    public int meleeAssignedExcept(int targetId, int attackerId) {
        Integer own = meleeTargetByAttacker.get(attackerId);
        int assigned = meleeAssigned(targetId);
        return own != null && own == targetId ? assigned - 1 : assigned;
    }

    /**
     * Makes the target the attacker's entry when it fights in melee; ranged picks are not counted.
     */
    public void recordPick(Unit attacker, Unit target) {
        record(attacker.getID(), attacker.getType(), target.getID());
    }

    /**
     * Makes the target the attacker's entry when it fights in melee, replacing any entry it held.
     */
    public void record(int attackerId, UnitType attackerType, int targetId) {
        if (!TargetScorer.isMelee(attackerType)) {
            return;
        }
        release(attackerId);
        meleeTargetByAttacker.put(attackerId, targetId);
        meleeAssigned.merge(targetId, 1, Integer::sum);
    }

    /**
     * Drops the attacker's entry, if it holds one.
     */
    public void release(int attackerId) {
        Integer previous = meleeTargetByAttacker.remove(attackerId);
        if (previous == null) {
            return;
        }
        int remaining = meleeAssigned.get(previous) - 1;
        if (remaining > 0) {
            meleeAssigned.put(previous, remaining);
        } else {
            meleeAssigned.remove(previous);
        }
    }

    /**
     * @return true when any enemy static defence that fires on ground units is known
     */
    public boolean hasGroundDefense() {
        return !groundDefenseZones.isEmpty();
    }

    /**
     * @return true when the position is within reach of enemy static defence that fires on ground units
     */
    public boolean insideGroundDefense(Position position) {
        for (StaticDefenseZone zone : groundDefenseZones) {
            if (zone.covers(position, 0)) {
                return true;
            }
        }
        return false;
    }
}
