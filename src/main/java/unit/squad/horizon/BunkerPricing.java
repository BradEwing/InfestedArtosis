package unit.squad.horizon;

import bwapi.Position;
import bwapi.Unit;
import bwapi.UnitType;
import info.tracking.EnemyReachMemory;
import info.tracking.ObservedUnit;
import unit.managed.ManagedUnit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * How much of each enemy Bunker a squad faces, priced by where the Bunker's fire reaches and by the infantry known
 * to exist to fill it.
 *
 * <p>Reach: a Bunker counts only when its fire, for the squad's domain, touches the squad or the squad's path. The
 * squad is the position of each member, and its path is the straight leg from each member to the unit it is fighting.
 * A Bunker weighs in full when any leg passes within its reach, falls off linearly over
 * {@link #APPROACH_FALLOFF} past it, and adds nothing beyond that.
 *
 * <p>Garrison: across the Bunkers one evaluation prices, the occupants never exceed the larger of the unseen infantry
 * known to be alive and the occupants the Bunkers' own fire has shown. A Bunker whose garrison was measured within
 * the trust window keeps its measured occupants; the rest of the pool is shared among the other Bunkers in
 * proportion to the occupants each would otherwise be priced at, and never above that.
 */
final class BunkerPricing {

    /**
     * Distance past a Bunker's reach over which its weight falls from full to nothing. A tuning constant equal to the
     * approach buffer the simulator gives positional enemies, not a claimed game fact.
     */
    static final int APPROACH_FALLOFF = 64;

    /**
     * Infantry that fires from inside a Bunker, and so fills the garrison pool. A Medic loads but has no weapon.
     */
    static final Set<UnitType> SHOOTERS = EnumSet.of(UnitType.Terran_Marine, UnitType.Terran_Firebat,
            UnitType.Terran_Ghost);

    private BunkerPricing() {
    }

    /**
     * The reach a Bunker is priced at for a squad's domain. A ground squad reads the positional reach the simulator
     * gives every positional enemy. An air squad reads the Marine's air range plus
     * {@link EnemyReachMemory#BUNKER_ALLOWANCE}, raised to the learned ground reach when that is larger, and never past
     * {@link HorizonCombatSimulator#MAX_POSITIONAL_REACH}.
     *
     * @param airSquad whether the squad is judged on its air arm
     * @param learnedGroundReach the Bunker's ground reach learned over the game
     * @return reach in pixels, measured from the Bunker's footprint
     */
    static int reach(boolean airSquad, int learnedGroundReach) {
        if (!airSquad) return HorizonCombatSimulator.positionalReach(UnitType.Terran_Bunker, learnedGroundReach);
        int airReach = UnitType.Terran_Marine.airWeapon().maxRange() + EnemyReachMemory.BUNKER_ALLOWANCE;
        return Math.min(HorizonCombatSimulator.MAX_POSITIONAL_REACH, Math.max(airReach, learnedGroundReach));
    }

    /**
     * The share of a Bunker's fire that bears on a squad whose nearest leg lies the given gap from the Bunker.
     *
     * @param gap pixels from the Bunker's footprint to the nearest point of the squad or its path
     * @param reach the Bunker's priced reach, see {@link #reach}
     * @return 1 within reach, falling linearly to 0 at {@link #APPROACH_FALLOFF} past it, 0 beyond
     */
    static double fireWeight(double gap, int reach) {
        if (gap <= reach) return 1.0;
        if (gap >= reach + APPROACH_FALLOFF) return 0;
        return 1.0 - (gap - reach) / APPROACH_FALLOFF;
    }

    /**
     * The squad and its path as legs: one point leg per member at its position, and one leg from each member to the
     * visible unit it is fighting. Overlords are left out, as the simulator leaves them out of our strength.
     *
     * @param members the squad's members
     * @return the legs
     */
    static List<Leg> legs(Collection<ManagedUnit> members) {
        List<Leg> legs = new ArrayList<>();
        for (ManagedUnit mu : members) {
            if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
            Unit unit = mu.getUnit();
            if (unit == null) continue;
            Position from = unit.getPosition();
            legs.add(new Leg(from, from));
            Unit target = mu.fightTarget;
            if (target != null && target.exists() && target.isVisible()) {
                legs.add(new Leg(from, target.getPosition()));
            }
        }
        return legs;
    }

    /**
     * The gap from a Bunker's footprint to the nearest of the legs.
     *
     * @param bunker the Bunker's centre
     * @param legs the squad and its path, see {@link #legs}
     * @return the smallest gap in pixels, or {@link Double#MAX_VALUE} when there are no legs
     */
    static double nearestGap(Position bunker, List<Leg> legs) {
        double best = Double.MAX_VALUE;
        for (Leg leg : legs) {
            best = Math.min(best, gapToPoint(bunker, leg.nearestPointTo(bunker)));
        }
        return best;
    }

    /**
     * Euclidean gap from a Bunker's footprint, a box around its centre by the type's dimensions, to a point.
     *
     * @param bunker the Bunker's centre
     * @param point the point
     * @return gap in pixels, 0 inside the footprint
     */
    static double gapToPoint(Position bunker, Position point) {
        UnitType type = UnitType.Terran_Bunker;
        int dx = Math.max(0, Math.max(bunker.getX() - type.dimensionLeft() - point.getX(),
                point.getX() - bunker.getX() - type.dimensionRight()));
        int dy = Math.max(0, Math.max(bunker.getY() - type.dimensionUp() - point.getY(),
                point.getY() - bunker.getY() - type.dimensionDown()));
        return Math.hypot(dx, dy);
    }

    /**
     * Unseen infantry known to be alive: every living {@link #SHOOTERS shooter} the tracker holds that is neither
     * visible nor priced in the sample at its last known position.
     *
     * @param living the tracker's living observed units
     * @param pricedLoose unseen shooters the evaluation already priced where they were last seen
     * @return the garrison pool, never below 0
     */
    static int garrisonPool(Collection<ObservedUnit> living, int pricedLoose) {
        int unseen = 0;
        for (ObservedUnit ou : living) {
            if (!SHOOTERS.contains(ou.getUnitType())) continue;
            Unit unit = ou.getUnit();
            if (unit != null && unit.isVisible()) continue;
            unseen++;
        }
        return Math.max(0, unseen - pricedLoose);
    }

    /**
     * Sets each candidate's occupants. Measured candidates keep their demand; the others share what the pool has left
     * in proportion to their demand, never above it. The total never exceeds the larger of the pool and the measured
     * occupants.
     *
     * @param candidates the Bunkers this evaluation prices
     * @param pool unseen infantry known to be alive, see {@link #garrisonPool}
     */
    static void allocate(List<Candidate> candidates, int pool) {
        double measured = 0;
        double unmeasured = 0;
        for (Candidate candidate : candidates) {
            if (candidate.measured) {
                measured += candidate.demand;
            } else {
                unmeasured += candidate.demand;
            }
        }
        double available = Math.max(0, pool - measured);
        double share = unmeasured <= available ? 1.0 : available / unmeasured;
        for (Candidate candidate : candidates) {
            candidate.occupants = candidate.measured ? candidate.demand : candidate.demand * share;
        }
    }

    /**
     * A straight leg of the squad or its path. A member's own position is a leg with both ends the same.
     */
    static final class Leg {
        private final Position from;
        private final Position to;

        Leg(Position from, Position to) {
            this.from = from;
            this.to = to;
        }

        /**
         * The point of the leg nearest a position.
         *
         * @param p the position
         * @return that point
         */
        Position nearestPointTo(Position p) {
            double lx = to.getX() - from.getX();
            double ly = to.getY() - from.getY();
            double lengthSquared = lx * lx + ly * ly;
            if (lengthSquared == 0) return from;
            double t = ((p.getX() - from.getX()) * lx + (p.getY() - from.getY()) * ly) / lengthSquared;
            t = Math.max(0, Math.min(1, t));
            return new Position((int) Math.round(from.getX() + t * lx), (int) Math.round(from.getY() + t * ly));
        }
    }

    /**
     * One Bunker the evaluation prices: its fire weight, the occupants it would be priced at without the pool, and
     * whether that figure was measured within the trust window.
     */
    static final class Candidate {
        private final Position position;
        private final boolean fogOfWar;
        private final double fireWeight;
        private final double demand;
        private final boolean measured;
        private final double fullGround;
        private final double fullAntiAir;
        private double occupants;

        Candidate(Position position, boolean fogOfWar, double fireWeight, double demand, boolean measured,
                  double fullGround, double fullAntiAir) {
            this.position = position;
            this.fogOfWar = fogOfWar;
            this.fireWeight = fireWeight;
            this.demand = demand;
            this.measured = measured;
            this.fullGround = fullGround;
            this.fullAntiAir = fullAntiAir;
        }

        Position getPosition() {
            return position;
        }

        boolean isFogOfWar() {
            return fogOfWar;
        }

        double getOccupants() {
            return occupants;
        }

        /**
         * Ground strength at the allocated garrison and fire weight.
         *
         * @return ground strength
         */
        double ground() {
            return fullGround * garrisonShare() * fireWeight;
        }

        /**
         * Anti-air strength at the allocated garrison and fire weight.
         *
         * @return anti-air strength
         */
        double antiAir() {
            return fullAntiAir * garrisonShare() * fireWeight;
        }

        private double garrisonShare() {
            return occupants / HorizonCombatSimulator.BUNKER_MAX_GARRISON;
        }
    }
}
