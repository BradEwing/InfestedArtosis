package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import telemetry.BunkerHoldRelease;
import unit.managed.ManagedUnit;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Bunkers a squad retreated from, kept so the squad does not march back in on them once they fall out of the
 * simulator's sample.
 *
 * <p>A Bunker is remembered from the retreat that priced it. While it is remembered and the squad has not grown, the
 * simulator reports it as a threat beyond its sample radius wherever it stands, so a blind ADVANCE is held at the
 * standoff instead of walking back into its fire. The memory of every Bunker ends when the squad's composition
 * grows, see {@link #releaseIfGrown}, when reinforcements that joined through merges add up to more than
 * {@link #MERGE_GROWTH_FRACTION} of the army that retreated, see {@link #absorb}, and when {@link #HOLD_CAP_FRAMES}
 * pass since the retreat, see {@link #releaseIfExpired}. The memory of one Bunker ends when the Bunker is no longer a
 * living observed Bunker, see {@link #retain}.
 */
public final class BunkerRetreatMemory {

    /**
     * The share of the supply that retreated which reinforcements joining through merges may add before the hold ends.
     * Half as much again is the point at which the squad is a different force from the one the Bunker was priced
     * against, while a trickle of fresh lings that rejoin a squad right after a retreat does not end the hold.
     */
    static final double MERGE_GROWTH_FRACTION = 0.5;

    /**
     * The frames a retreat holds off a Bunker at most, whatever the squad's composition does.
     */
    static final int HOLD_CAP_FRAMES = 1440;

    private final Set<Position> bunkers = new HashSet<>();
    private final Map<UnitType, Integer> recorded = new EnumMap<>(UnitType.class);
    private Object lineage;
    private int retreatFrame;
    private int retreatSupply;
    private int broughtSupply;
    private BunkerHoldRelease releaseReason = BunkerHoldRelease.NONE;

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
     * The supply a composition costs.
     *
     * @param composition unit counts by type
     * @return the summed supply of the units
     */
    static int supply(Map<UnitType, Integer> composition) {
        int supply = 0;
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            supply += entry.getKey().supplyRequired() * entry.getValue();
        }
        return supply;
    }

    /**
     * Records a retreat of the given squad members from the Bunkers the sample priced, see {@link #record}.
     *
     * @param pricedBunkers the Bunkers the sample priced when the squad retreated
     * @param members the squad's members at the retreat
     * @param frame the frame of the retreat
     */
    public void recordRetreat(Collection<Position> pricedBunkers, Collection<ManagedUnit> members, int frame) {
        record(pricedBunkers, composition(members), frame);
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
     * @param frame the frame of the retreat
     */
    void record(Collection<Position> pricedBunkers, Map<UnitType, Integer> composition, int frame) {
        if (pricedBunkers.isEmpty()) return;
        boolean sameBunkers = !Collections.disjoint(bunkers, pricedBunkers);
        bunkers.clear();
        bunkers.addAll(pricedBunkers);
        if (!sameBunkers || lineage == null) {
            recorded.clear();
            lineage = new Object();
            retreatSupply = 0;
            broughtSupply = 0;
        }
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            recorded.merge(entry.getKey(), entry.getValue(), Math::max);
        }
        retreatFrame = frame;
        retreatSupply = Math.max(retreatSupply, supply(composition));
        releaseReason = BunkerHoldRelease.NONE;
    }

    /**
     * Forgets every Bunker when the squad's composition has grown since it retreated.
     *
     * @param composition the squad's composition now, see {@link #composition}
     */
    void releaseIfGrown(Map<UnitType, Integer> composition) {
        if (!bunkers.isEmpty() && grew(recorded, composition)) {
            release(BunkerHoldRelease.GROWTH);
        }
    }

    /**
     * Forgets every Bunker once {@link #HOLD_CAP_FRAMES} have passed since the retreat.
     *
     * @param frame the current frame
     */
    void releaseIfExpired(int frame) {
        if (!bunkers.isEmpty() && frame - retreatFrame >= HOLD_CAP_FRAMES) {
            release(BunkerHoldRelease.TIME_CAP);
        }
    }

    private void release(BunkerHoldRelease reason) {
        bunkers.clear();
        recorded.clear();
        lineage = null;
        retreatSupply = 0;
        broughtSupply = 0;
        releaseReason = reason;
    }

    /**
     * Returns why the hold ended and resets it, so a hold end is reported once.
     *
     * @return the reason of the last release, NONE when no release happened since the last call
     */
    public BunkerHoldRelease takeReleaseReason() {
        BunkerHoldRelease reason = releaseReason;
        releaseReason = BunkerHoldRelease.NONE;
        return reason;
    }

    /**
     * Folds the memories of the squads of a merge, or of the squad a split carves a sibling off, into this one: the
     * remembered Bunkers are the union of the sources' and the recorded composition is the sum of theirs. Sources
     * that carry the same retreat, such as the halves of a squad that split, count once, at the larger of their
     * records of each type. A source that remembers no Bunker adds the composition it brings to the record, so a
     * trickle of reinforcements does not release the hold at once, and adds its supply to the supply brought since
     * the retreat. The hold ends when the supply brought through merges exceeds {@link #MERGE_GROWTH_FRACTION} of the
     * supply that retreated. A source's memory is first released if the source grew since it retreated. Nothing is
     * folded when no source remembers a Bunker. The earliest retreat among the sources starts the hold's time cap.
     *
     * @param sources each source's memory with the source's composition now, see {@link #composition}
     */
    public void absorb(Collection<Source> sources) {
        boolean anyHolds = false;
        for (Source source : sources) {
            source.memory.releaseIfGrown(source.composition);
            anyHolds |= !source.memory.bunkers.isEmpty();
        }
        if (!anyHolds) return;
        Map<Object, Lineage> byLineage = new LinkedHashMap<>();
        int earliestRetreat = Integer.MAX_VALUE;
        for (Source source : sources) {
            if (source.memory.bunkers.isEmpty()) {
                byLineage.put(new Object(), Lineage.brought(source.composition));
                continue;
            }
            bunkers.addAll(source.memory.bunkers);
            earliestRetreat = Math.min(earliestRetreat, source.memory.retreatFrame);
            byLineage.computeIfAbsent(source.memory.lineage, key -> new Lineage()).fold(source.memory);
        }
        int retreated = 0;
        int brought = 0;
        for (Lineage entry : byLineage.values()) {
            addCounts(entry.counts);
            retreated += entry.retreatSupply;
            brought += entry.broughtSupply;
        }
        lineage = byLineage.size() == 1 ? byLineage.keySet().iterator().next() : new Object();
        retreatFrame = earliestRetreat;
        retreatSupply = retreated;
        broughtSupply = brought;
        if (brought > retreated * MERGE_GROWTH_FRACTION) {
            release(BunkerHoldRelease.MERGE_GROWTH);
        }
    }

    private void addCounts(Map<UnitType, Integer> counts) {
        for (Map.Entry<UnitType, Integer> entry : counts.entrySet()) {
            recorded.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private static final class Lineage {
        private final Map<UnitType, Integer> counts = new EnumMap<>(UnitType.class);
        private int retreatSupply;
        private int broughtSupply;

        private static Lineage brought(Map<UnitType, Integer> composition) {
            Lineage lineage = new Lineage();
            lineage.counts.putAll(composition);
            lineage.broughtSupply = supply(composition);
            return lineage;
        }

        private void fold(BunkerRetreatMemory memory) {
            for (Map.Entry<UnitType, Integer> entry : memory.recorded.entrySet()) {
                counts.merge(entry.getKey(), entry.getValue(), Math::max);
            }
            retreatSupply = Math.max(retreatSupply, memory.retreatSupply);
            broughtSupply = Math.max(broughtSupply, memory.broughtSupply);
        }
    }

    /**
     * A squad's memory with its composition now, as one source of {@link #absorb}.
     */
    public static final class Source {
        private final BunkerRetreatMemory memory;
        private final Map<UnitType, Integer> composition;

        /**
         * @param memory the source squad's retreat memory
         * @param members the source squad's members
         */
        public Source(BunkerRetreatMemory memory, Collection<ManagedUnit> members) {
            this(memory, composition(members));
        }

        Source(BunkerRetreatMemory memory, Map<UnitType, Integer> composition) {
            this.memory = memory;
            this.composition = composition;
        }
    }

    /**
     * @param bunker where a Bunker stands or was last seen
     * @return whether the squad retreated from that Bunker and still holds off it
     */
    public boolean holds(Position bunker) {
        return bunkers.contains(bunker);
    }

    /**
     * Forgets the Bunkers that are no longer living observed Bunkers.
     *
     * @param livingBunkers where each living observed Bunker stands or was last seen
     */
    void retain(Collection<Position> livingBunkers) {
        if (bunkers.isEmpty()) return;
        bunkers.retainAll(livingBunkers);
        if (bunkers.isEmpty()) {
            release(BunkerHoldRelease.BUNKER_DIED);
        }
    }
}
