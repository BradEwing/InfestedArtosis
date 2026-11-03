package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import unit.squad.AirFlock;
import unit.squad.CombatSimulator;
import util.TargetScorer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How much of each enemy Missile Turret an air squad faces, priced by what the Turret's fire covers rather than by
 * its distance from the flock centre alone.
 *
 * <p>A Turret weighs in full while a member of the flock could be hit by it or could be hitting a target inside its
 * reach, read from where each member stands rather than from the squad's smoothed centre (see {@link #flockDistance}),
 * and while an enemy the flock would fight (see {@link #coverCandidate}) stands near the flock and where an attacker
 * hitting it could be inside that reach. Past both its weight falls linearly to nothing over {@link #TAPER}.
 *
 * <p>The weight is continuous in the members, the flock centre, the Turret and every candidate's position: each term
 * is a sum of clamped distances, a candidate's cover fades over {@link #TAPER} as it moves from {@link #TARGET_RADIUS}
 * to {@link #TARGET_WINDOW} from the centre, where candidates stop being read, and the simulator samples a Turret
 * while {@link #sampled}, out to where the weight is already nothing for any candidate. Only a candidate or a member
 * appearing or vanishing (sight, freshness, death) changes the weight in one step.
 *
 * <p>Several Turrets are not summed by their distance from the flock alone: a Turret that covers neither the flock
 * nor what it would fight weighs little however many stand beside it, and every Turret that does cover the fight
 * counts in full.
 *
 * <p>The taper is a price, not a band: a flock that backs off past a Turret it left a fight under would read it
 * lighter and turn straight back in. {@link #reenter} is the band's other half: a squad that left FIGHT goes back in,
 * for a while, only on a read it wins with the Turrets it left priced in full.
 */
final class AirTurretPricing {

    /**
     * Pixels past a Turret's reach plus {@link #ATTACKER_RANGE} within which the member nearest it still weighs it in
     * full: the radius inside which a regrouping member rejoins the flock, {@link AirFlock#REGROUP_JOIN_RADIUS}, the
     * distance the flock's orders close on the Turret before the next read moves it.
     */
    static final int FLOCK_EXTENT = AirFlock.REGROUP_JOIN_RADIUS;

    /**
     * Range at which the flock's attacker hits what it attacks: the Mutalisk's weapon range.
     */
    static final int ATTACKER_RANGE = UnitType.Zerg_Mutalisk.groundWeapon().maxRange();

    /**
     * Distance past full cover over which a Turret's weight falls from full to nothing. A tuning constant equal to the
     * width of the band over which {@link HorizonCombatSimulator#distanceWeight} falls off, not a claimed game fact.
     */
    static final double TAPER = 256;

    /**
     * Pixels from the flock centre within which a candidate's cover counts in full: the simulator's engagement radius,
     * {@link HorizonCombatSimulator#MAX_ENGAGEMENT_RADIUS}.
     */
    static final double TARGET_RADIUS = HorizonCombatSimulator.MAX_ENGAGEMENT_RADIUS;

    /**
     * Pixels from the flock centre out to which candidates are read: {@link #TARGET_RADIUS} plus {@link #TAPER}, where
     * a candidate's cover has faded to nothing.
     */
    static final double TARGET_WINDOW = TARGET_RADIUS + TAPER;

    private AirTurretPricing() {
    }

    /**
     * An enemy Turret sampled for an air squad, waiting to be weighed once every cover candidate is known.
     */
    static final class Turret {
        private final UnitType type;
        private final Position position;
        private final int reach;
        private final double flockDistance;
        private final double ground;
        private final double antiAir;
        private final boolean visible;

        /**
         * @param type the Turret's type
         * @param position where the Turret stands or was last seen
         * @param reach the Turret's air reach, see {@link #reach}
         * @param flockDistance pixels from the member nearest the Turret to it, see {@link #flockDistance}
         * @param ground the Turret's ground strength at full weight
         * @param antiAir the Turret's anti-air strength at full weight
         * @param visible whether the Turret is in sight
         */
        Turret(UnitType type, Position position, int reach, double flockDistance, double ground, double antiAir,
               boolean visible) {
            this.type = type;
            this.position = position;
            this.reach = reach;
            this.flockDistance = flockDistance;
            this.ground = ground;
            this.antiAir = antiAir;
            this.visible = visible;
        }
    }

    /**
     * An enemy the flock would fight, read within {@link #TARGET_WINDOW} of the flock centre.
     */
    static final class Target {
        private final Position position;
        private final double flockDistance;

        /**
         * @param position where the enemy stands or was last seen
         * @param flockDistance pixels from the flock centre to the enemy
         */
        Target(Position position, double flockDistance) {
            this.position = position;
            this.flockDistance = flockDistance;
        }
    }

    /**
     * Whether an enemy can make a Turret cover the flock's fight: one the target scorer ranks a threat to an air
     * attacker ({@link TargetScorer#canAttackType} against a flyer, which counts a Bunker), other than a worker or a
     * Turret, in sight or seen within the freshness the simulator gives a mobile unit. A worker, an unarmed building or
     * a stale position never does.
     *
     * @param type the enemy's type
     * @param visible whether it is in sight
     * @param framesSinceObserved frames since it was last seen
     * @param freshFrames frames a position out of sight stays fresh
     * @return true if the enemy is a cover candidate
     */
    static boolean coverCandidate(UnitType type, boolean visible, int framesSinceObserved, int freshFrames) {
        if (type == UnitType.Terran_Missile_Turret || type.isWorker()) return false;
        if (!visible && framesSinceObserved > freshFrames) return false;
        return TargetScorer.canAttackType(type, true);
    }

    /**
     * The reach a Turret is priced at for an air squad: its air weapon's range, no more than
     * {@link HorizonCombatSimulator#MAX_POSITIONAL_REACH}.
     *
     * @param type the Turret's type
     * @return reach in pixels
     */
    static int reach(UnitType type) {
        return Math.min(HorizonCombatSimulator.MAX_POSITIONAL_REACH, type.airWeapon().maxRange());
    }

    /**
     * Pixels from the flock to a Turret: from the member nearest it, or from the flock centre when the squad has no
     * member to read.
     *
     * @param members where the flock's members stand
     * @param turret where the Turret stands
     * @param centreDistance pixels from the flock centre to the Turret
     * @return the distance in pixels
     */
    static double flockDistance(List<Position> members, Position turret, double centreDistance) {
        if (members.isEmpty()) return centreDistance;
        double nearest = Double.POSITIVE_INFINITY;
        for (Position member : members) {
            nearest = Math.min(nearest, member.getDistance(turret));
        }
        return nearest;
    }

    /**
     * Whether a Turret is sampled: within {@link #sampleRadius} of the flock centre, or close enough to the member
     * nearest it that the flock term of {@link #weight} gives it any weight.
     *
     * @param centreDistance pixels from the flock centre to the Turret
     * @param flockDistance pixels from the member nearest the Turret to it, see {@link #flockDistance}
     * @param reach the Turret's air reach
     * @return true if the Turret is weighed
     */
    static boolean sampled(double centreDistance, double flockDistance, int reach) {
        return centreDistance <= sampleRadius(reach) || flockDistance < reach + FLOCK_EXTENT + ATTACKER_RANGE + TAPER;
    }

    /**
     * How far from the flock centre a Turret is sampled: {@link #TARGET_RADIUS} plus the Turret's reach, plus
     * {@link #ATTACKER_RANGE}, plus {@link #TAPER}. A Turret this far out stands at least this radius less a from a
     * candidate a pixels from the centre, so the candidate's cover gap there is at least {@link #TAPER} for any a up
     * to {@link #TARGET_WINDOW}, as is the flock term: from this radius out {@link #weight} is nothing whatever the
     * candidates.
     *
     * @param reach the Turret's air reach
     * @return the radius in pixels
     */
    static double sampleRadius(int reach) {
        return TARGET_RADIUS + reach + ATTACKER_RANGE + TAPER;
    }

    /**
     * How far a candidate is from making a Turret cover it in full: how far past the Turret's reach plus
     * {@link #ATTACKER_RANGE} it stands from the Turret, where an attacker on it could be hit, plus how far past
     * {@link #TARGET_RADIUS} it stands from the flock centre. The smallest over all candidates.
     *
     * @param turret where the Turret stands
     * @param reach the Turret's air reach
     * @param targets the cover candidates
     * @return the gap in pixels, positive infinity with no candidate
     */
    static double coverGap(Position turret, int reach, List<Target> targets) {
        double best = Double.POSITIVE_INFINITY;
        for (Target target : targets) {
            best = Math.min(best, targetGap(turret, reach, target));
        }
        return best;
    }

    private static double targetGap(Position turret, int reach, Target target) {
        return Math.max(0, turret.getDistance(target.position) - (reach + ATTACKER_RANGE))
                + Math.max(0, target.flockDistance - TARGET_RADIUS);
    }

    /**
     * How much of a Turret bears on the flock's fight. It weighs in full while any member is within its reach plus
     * {@link #FLOCK_EXTENT} plus {@link #ATTACKER_RANGE}, where a member could be hit or be hitting a target inside
     * the reach, or while the cover gap is nothing. Past the smaller of the two gaps its weight falls linearly to
     * nothing over {@link #TAPER}.
     *
     * @param flockDistance pixels from the member nearest the Turret to it, see {@link #flockDistance}
     * @param coverGap the Turret's cover gap, see {@link #coverGap}
     * @param reach the Turret's air reach
     * @return the weight, between 0 and 1
     */
    static double weight(double flockDistance, double coverGap, int reach) {
        double gap = Math.min(flockDistance - (reach + FLOCK_EXTENT + ATTACKER_RANGE), coverGap);
        if (gap <= 0) return 1.0;
        if (gap >= TAPER) return 0;
        return 1.0 - gap / TAPER;
    }

    /**
     * Weighs each sampled Turret by {@link #weight} and adds every one that weighs anything to the sample and the
     * snapshot, with the candidate that covers it most where one brings it under {@link #TAPER}.
     *
     * @param turrets the Turrets the simulator sampled for an air squad
     * @param targets the cover candidates read within {@link #TARGET_WINDOW}, see {@link #coverCandidate}
     * @param sample the enemy sample to add to
     * @param snapshot the snapshot to record the priced Turrets and their cover on
     * @return for each Turret priced at a weight under full, where it stands and the anti-air its taper left out
     */
    static Map<Position, Double> price(List<Turret> turrets, List<Target> targets,
                                       HorizonCombatSimulator.EnemySample sample,
                                       HorizonCombatSimulator.DebugSnapshot snapshot) {
        Map<Position, Double> tapered = new LinkedHashMap<>();
        for (Turret turret : turrets) {
            Target cover = null;
            double coverGap = Double.POSITIVE_INFINITY;
            for (Target target : targets) {
                double gap = targetGap(turret.position, turret.reach, target);
                if (gap < coverGap) {
                    coverGap = gap;
                    cover = target;
                }
            }
            double weight = weight(turret.flockDistance, coverGap, turret.reach);
            if (weight <= 0) continue;
            double antiAir = turret.antiAir * weight;
            sample.add(turret.type, turret.ground * weight, antiAir);
            snapshot.getEnemyUnits().add(new HorizonCombatSimulator.UnitDebugEntry(turret.position, turret.type,
                    antiAir, false, !turret.visible));
            snapshot.getAirEdgeOfFire().put(turret.position, turret.reach);
            if (cover != null && coverGap < TAPER) {
                snapshot.getAirCoverTargets().put(turret.position, cover.position);
            }
            if (weight < 1.0) {
                tapered.merge(turret.position, turret.antiAir - antiAir, Double::sum);
            }
        }
        return tapered;
    }

    /**
     * The anti-air a squad's re-entry band adds back: what the taper left out of each Turret the band holds.
     *
     * @param tapered what the taper left out of each Turret priced under full weight, see {@link #price}
     * @param band where each Turret the band holds stands
     * @return the anti-air to add, 0 when no held Turret is tapered
     */
    static double bandShortfall(Map<Position, Double> tapered, Set<Position> band) {
        double shortfall = 0;
        for (Map.Entry<Position, Double> entry : tapered.entrySet()) {
            if (band.contains(entry.getKey())) {
                shortfall += entry.getValue();
            }
        }
        return shortfall;
    }

    /**
     * The verdict a retreating air squad acts on inside its re-entry band. An ENGAGE read stands only when our air
     * strength still clears the engage threshold against the enemy's anti-air with the band's tapered Turrets priced
     * at full weight, see {@link #bandShortfall}; otherwise the squad keeps retreating. Every other read, and every
     * read outside the band, stands. A Turret the flock has drawn far enough from that it weighs nothing adds nothing
     * back, so the flock still turns in on a target away from the Turrets it left.
     *
     * @param applies whether the band is on for the squad: both switches on, status RETREAT, the band open
     * @param raw the verdict for this frame's strengths
     * @param friendlyAir our air strength
     * @param enemyAntiAir the enemy's anti-air strength as priced
     * @param shortfall the anti-air the band adds back, see {@link #bandShortfall}
     * @param engageThreshold the engage threshold the read is judged against
     * @return RETREAT when the band refuses the ENGAGE, otherwise the raw verdict
     */
    static CombatSimulator.CombatResult reenter(boolean applies, CombatSimulator.CombatResult raw, double friendlyAir,
                                                double enemyAntiAir, double shortfall, double engageThreshold) {
        if (!applies || raw != CombatSimulator.CombatResult.ENGAGE || shortfall <= 0) return raw;
        if (friendlyAir / (enemyAntiAir + shortfall) < engageThreshold) return CombatSimulator.CombatResult.RETREAT;
        return raw;
    }
}
