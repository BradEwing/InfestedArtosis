package util;

import bwapi.Position;
import bwapi.Unit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One squad's melee target assignments during a single targeting pass, with the enemy static defence that can
 * fire on ground units. A fresh ledger is built each time a squad's fight targets are assigned; members are
 * scored in turn and each melee pick is recorded, so a later member sees the load earlier members put on a
 * target.
 */
public final class TargetLedger {

    private final String squadId;
    private final List<StaticDefenseZone> groundDefenseZones;
    private final Map<Integer, Integer> meleeAssigned = new HashMap<>();

    /**
     * @param squadId id of the squad whose members are being targeted
     * @param staticDefenseZones enemy static defence zones; those whose structure cannot fire on ground units
     *     are dropped
     */
    public TargetLedger(String squadId, Collection<StaticDefenseZone> staticDefenseZones) {
        this.squadId = squadId;
        this.groundDefenseZones = new ArrayList<>();
        for (StaticDefenseZone zone : staticDefenseZones) {
            if (TargetScorer.canAttackType(zone.getStructure(), false)) {
                groundDefenseZones.add(zone);
            }
        }
    }

    /**
     * @return a ledger with no squad, no assignments and no static defence
     */
    public static TargetLedger empty() {
        return new TargetLedger("", Collections.emptyList());
    }

    public String getSquadId() {
        return squadId;
    }

    /**
     * @return melee attackers recorded against the target so far in this pass
     */
    public int meleeAssigned(int targetId) {
        return meleeAssigned.getOrDefault(targetId, 0);
    }

    /**
     * Counts the attacker against its target when it fights in melee; ranged picks are not counted.
     */
    public void recordPick(Unit attacker, Unit target) {
        if (TargetScorer.isMelee(attacker.getType())) {
            recordMelee(target.getID());
        }
    }

    void recordMelee(int targetId) {
        meleeAssigned.merge(targetId, 1, Integer::sum);
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
