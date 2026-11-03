package unit.squad.horizon;

import bwapi.Position;
import bwapi.UnitType;
import unit.squad.AirFlock;

import java.util.List;

/**
 * How much of each enemy Missile Turret an air squad faces, priced by what the Turret's fire covers rather than by
 * its distance from the flock centre alone.
 *
 * <p>A Turret weighs in full while a member at the flock could be hit by it or could be hitting a target inside its
 * reach, and while the nearest enemy to the flock centre, the one the flock would fight first, sits where an attacker
 * hitting it could be inside that reach. Past both its weight falls linearly to nothing over {@link #TAPER}. The
 * simulator samples a Turret out to {@link #sampleRadius}, the distance from the flock centre past which neither
 * term can be under {@link #TAPER}, so the weight is already nothing where the sample ends and a Turret never steps
 * into or out of the sample.
 *
 * <p>Several Turrets are not summed by their distance from the flock alone: a Turret that covers neither the flock
 * nor what it would fight weighs little however many stand beside it, and every Turret that does cover the fight
 * counts in full.
 */
final class AirTurretPricing {

    /**
     * Pixels from the flock centre within which a member taking the flock's orders stands, the radius inside which a
     * regrouping member rejoins, {@link AirFlock#REGROUP_JOIN_RADIUS}.
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

    private AirTurretPricing() {
    }

    /**
     * An enemy Turret sampled for an air squad, waiting to be weighed once the nearest target is known.
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
         * @param flockDistance pixels from the flock centre to the Turret
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
     * How far from the flock centre a Turret is sampled: the farthest a target the flock would fight can stand,
     * {@link HorizonCombatSimulator#MAX_ENGAGEMENT_RADIUS}, plus the reach of cover around that target, plus
     * {@link #TAPER}. Past it {@link #weight} is nothing whatever the target.
     *
     * @param reach the Turret's air reach
     * @return the radius in pixels
     */
    static double sampleRadius(int reach) {
        return HorizonCombatSimulator.MAX_ENGAGEMENT_RADIUS + reach + ATTACKER_RANGE + TAPER;
    }

    /**
     * How much of a Turret bears on the flock's fight. It weighs in full while the flock centre is within its reach
     * plus {@link #FLOCK_EXTENT} plus {@link #ATTACKER_RANGE}, where a member could be hit or be hitting a target
     * inside the reach, or while the nearest target is within its reach plus {@link #ATTACKER_RANGE}, where an
     * attacker on that target could be hit. Past the nearer of the two its weight falls linearly to nothing over
     * {@link #TAPER}.
     *
     * @param flockDistance pixels from the flock centre to the Turret
     * @param coverDistance pixels from the nearest target to the Turret, positive infinity with no target
     * @param reach the Turret's air reach
     * @return the weight, between 0 and 1
     */
    static double weight(double flockDistance, double coverDistance, int reach) {
        double gap = Math.min(flockDistance - (reach + FLOCK_EXTENT + ATTACKER_RANGE),
                coverDistance - (reach + ATTACKER_RANGE));
        if (gap <= 0) return 1.0;
        if (gap >= TAPER) return 0;
        return 1.0 - gap / TAPER;
    }

    /**
     * Weighs each sampled Turret by {@link #weight} and adds every one that weighs anything to the sample and the
     * snapshot.
     *
     * @param turrets the Turrets the simulator sampled for an air squad
     * @param target the nearest sampled enemy to the flock centre within
     *               {@link HorizonCombatSimulator#MAX_ENGAGEMENT_RADIUS}, other than a Turret, or null with none
     * @param sample the enemy sample to add to
     * @param snapshot the snapshot to record the priced Turrets and the target on
     */
    static void price(List<Turret> turrets, Position target, HorizonCombatSimulator.EnemySample sample,
                      HorizonCombatSimulator.DebugSnapshot snapshot) {
        if (turrets.isEmpty()) return;
        snapshot.setAirCoverTarget(target);
        for (Turret turret : turrets) {
            double cover = target != null ? turret.position.getDistance(target) : Double.POSITIVE_INFINITY;
            double weight = weight(turret.flockDistance, cover, turret.reach);
            if (weight <= 0) continue;
            double antiAir = turret.antiAir * weight;
            sample.add(turret.type, turret.ground * weight, antiAir);
            snapshot.getEnemyUnits().add(new HorizonCombatSimulator.UnitDebugEntry(turret.position, turret.type,
                    antiAir, false, !turret.visible));
            snapshot.getAirEdgeOfFire().put(turret.position, turret.reach);
        }
    }
}
