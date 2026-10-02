package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import unit.managed.ManagedUnit;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Bunkers a squad retreated from, kept so the squad does not march back in on them once they fall out of the
 * simulator's sample.
 *
 * <p>A Bunker is remembered from the retreat that priced it. While it is remembered and the squad has not grown, the
 * simulator reports it as a threat beyond its sample radius wherever it stands, so a blind ADVANCE is held at the
 * standoff instead of walking back into its fire. The memory of every Bunker ends when the squad's composition
 * grows, see {@link #releaseIfGrown}, and the memory of one Bunker ends when the Bunker is no longer a living
 * observed Bunker, see {@link #retain}.
 */
public final class BunkerRetreatMemory {

    private final Set<Position> bunkers = new HashSet<>();
    private final Map<UnitType, Integer> recorded = new EnumMap<>(UnitType.class);

    /**
     * The unit counts of a squad by type, Overlords left out as the simulator leaves them out of our strength.
     *
     * @param members the squad's members
     * @return the count of each type among them
     */
    static Map<UnitType, Integer> composition(Collection<ManagedUnit> members) {
        Map<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
        for (ManagedUnit mu : members) {
            UnitType type = mu.getUnitType();
            if (type == UnitType.Zerg_Overlord) continue;
            counts.merge(type, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Records a retreat of the given squad members from the Bunkers the sample priced, see {@link #record}.
     *
     * @param pricedBunkers the Bunkers the sample priced when the squad retreated
     * @param members the squad's members at the retreat
     */
    public void recordRetreat(Collection<Position> pricedBunkers, Collection<ManagedUnit> members) {
        record(pricedBunkers, composition(members));
    }

    /**
     * Whether a squad's composition has grown past a recorded one: more of any type, or a type the record lacks.
     * Losses alone never count as growth.
     *
     * @param recorded the composition when the squad retreated
     * @param current the composition now
     * @return true when the squad was reinforced since
     */
    static boolean grew(Map<UnitType, Integer> recorded, Map<UnitType, Integer> current) {
        for (Map.Entry<UnitType, Integer> entry : current.entrySet()) {
            if (entry.getValue() > recorded.getOrDefault(entry.getKey(), 0)) return true;
        }
        return false;
    }

    /**
     * Replaces the remembered Bunkers with those a retreat priced, recording the squad's composition at that moment. A
     * retreat that priced no Bunker leaves the memory as it was. A retreat from a Bunker already remembered keeps the
     * larger count of each type, so losses do not lower the bar for release.
     *
     * @param pricedBunkers the Bunkers the sample priced when the squad retreated
     * @param composition the squad's composition at the retreat, see {@link #composition}
     */
    void record(Collection<Position> pricedBunkers, Map<UnitType, Integer> composition) {
        if (pricedBunkers.isEmpty()) return;
        boolean sameBunkers = !Collections.disjoint(bunkers, pricedBunkers);
        bunkers.clear();
        bunkers.addAll(pricedBunkers);
        if (!sameBunkers) {
            recorded.clear();
        }
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            recorded.merge(entry.getKey(), entry.getValue(), Math::max);
        }
    }

    /**
     * Forgets every Bunker when the squad's composition has grown since it retreated.
     *
     * @param composition the squad's composition now, see {@link #composition}
     */
    void releaseIfGrown(Map<UnitType, Integer> composition) {
        if (grew(recorded, composition)) {
            bunkers.clear();
            recorded.clear();
        }
    }

    /**
     * @param bunker where a Bunker stands or was last seen
     * @return whether the squad retreated from that Bunker and still holds off it
     */
    boolean holds(Position bunker) {
        return bunkers.contains(bunker);
    }

    /**
     * Forgets the Bunkers that are no longer living observed Bunkers.
     *
     * @param livingBunkers where each living observed Bunker stands or was last seen
     */
    void retain(Collection<Position> livingBunkers) {
        bunkers.retainAll(livingBunkers);
        if (bunkers.isEmpty()) {
            recorded.clear();
        }
    }
}
