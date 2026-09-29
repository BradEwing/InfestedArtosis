package unit.squad.horizon;

import bwapi.Position;
import bwapi.TilePosition;
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
 * squad is the position of each member. Its path is the straight leg from each member to the unit it is fighting, and
 * for a ground member with no such unit, the first {@link #MARCH_LOOKAHEAD} of the straight leg toward where it
 * marches. A Bunker weighs in full when any leg passes within its reach, falls off linearly over
 * {@link #APPROACH_FALLOFF} past it, and adds nothing beyond that. That weight is scaled down to nothing over the
 * last {@link #RADIUS_TAPER} of the radius out to which the simulator samples the Bunker from the squad centre.
 *
 * <p>Garrison: across the Bunkers one evaluation prices, the occupants, each weighed by its Bunker's weight, never
 * exceed the larger of the unseen infantry known to be alive and the occupants the Bunkers' own fire has shown. A
 * Bunker whose garrison was measured within the trust window keeps its measured occupants; the rest of the pool is
 * shared among the other Bunkers in proportion to the occupants each would otherwise be priced at, and never above
 * that.
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

    /**
     * How far ahead of a ground member its march leg runs toward its destination: the distance past a Bunker's reach
     * out to which the simulator samples it, {@link HorizonCombatSimulator#FALLOFF_EXTENT}. A Bunker the squad samples
     * at all is then priced at its {@link #radiusTaper} while the squad's march passes within its reach, wherever
     * inside that radius the squad stands.
     */
    static final double MARCH_LOOKAHEAD = HorizonCombatSimulator.FALLOFF_EXTENT;

    /**
     * Width of the outer band of the simulator's sample radius, {@link HorizonCombatSimulator#edgeOfFireRadius}, over
     * which a Bunker's weight falls from full to nothing as the squad centre moves out. A tuning constant, the part of
     * {@link HorizonCombatSimulator#FALLOFF_EXTENT} past the 256 px out to which other positional enemies weigh in
     * full, not a claimed game fact.
     */
    static final double RADIUS_TAPER = 256;

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
     * The share of a Bunker the simulator keeps for a squad centre the given distance away, so that a Bunker leaves
     * the sample without a step at the edge of the sample radius.
     *
     * @param distance pixels from the squad centre to the Bunker's centre
     * @param reach the Bunker's priced reach, see {@link #reach}
     * @return 1 out to {@link #RADIUS_TAPER} inside {@link HorizonCombatSimulator#edgeOfFireRadius}, falling linearly
     *     to 0 at that radius, 0 beyond
     */
    static double radiusTaper(double distance, int reach) {
        double edge = HorizonCombatSimulator.edgeOfFireRadius(reach);
        if (distance >= edge) return 0;
        if (distance <= edge - RADIUS_TAPER) return 1.0;
        return (edge - distance) / RADIUS_TAPER;
    }

    /**
     * The weight a Bunker is priced at for a squad: its fire weight against the squad and its path, see
     * {@link #fireWeight}, scaled by its place in the sample radius, see {@link #radiusTaper}.
     *
     * @param bunker the Bunker's centre
     * @param squadCenter the squad's centre
     * @param legs the squad and its path, see {@link #legs}
     * @param reach the Bunker's priced reach, see {@link #reach}
     * @return the weight, between 0 and 1
     */
    static double weight(Position bunker, Position squadCenter, List<Leg> legs, int reach) {
        return fireWeight(nearestGap(bunker, legs), reach) * radiusTaper(squadCenter.getDistance(bunker), reach);
    }

    /**
     * The squad and its path as legs: for each member whose unit still exists, see {@link #memberLegs}, its position,
     * the leg to the visible unit it is fighting, and for a ground member fighting none, its march leg toward its own
     * movement target or, without one, the squad's destination. Overlords are left out, as the simulator leaves them
     * out of our strength.
     *
     * @param members the squad's members
     * @param airSquad whether the squad is judged on its air arm, in which case no member marches
     * @param squadDestination where a member with no movement target marches, or null when none is known
     * @return the legs
     */
    static List<Leg> legs(Collection<ManagedUnit> members, boolean airSquad, Position squadDestination) {
        List<Leg> legs = new ArrayList<>();
        for (ManagedUnit mu : members) {
            if (mu.getUnitType() == UnitType.Zerg_Overlord) continue;
            Unit unit = mu.getUnit();
            if (unit == null || !unit.exists()) continue;
            Unit target = mu.fightTarget;
            Position fightTarget = target != null && target.exists() && target.isVisible()
                    ? target.getPosition()
                    : null;
            Position destination = airSquad ? null : marchDestination(mu.getMovementTargetPosition(), squadDestination);
            legs.addAll(memberLegs(unit.getPosition(), fightTarget, destination));
        }
        return legs;
    }

    /**
     * One member's legs: a point leg at its position, the leg to the unit it is fighting when there is one, and
     * otherwise its march leg toward its destination, see {@link #marchLeg}.
     *
     * @param from the member's position
     * @param fightTarget the position of the visible unit it is fighting, or null
     * @param marchDestination where it marches when it fights nothing, or null
     * @return the member's legs
     */
    static List<Leg> memberLegs(Position from, Position fightTarget, Position marchDestination) {
        List<Leg> legs = new ArrayList<>();
        legs.add(new Leg(from, from));
        if (fightTarget != null) {
            legs.add(new Leg(from, fightTarget));
        } else if (marchDestination != null) {
            legs.add(marchLeg(from, marchDestination));
        }
        return legs;
    }

    /**
     * Where a ground member marches when it fights nothing: its own movement target when it holds one, which it keeps
     * on the march, and otherwise the squad's destination.
     *
     * @param movementTarget the member's movement target, or null
     * @param squadDestination the squad's destination, or null
     * @return the destination, or null when neither is known
     */
    static Position marchDestination(TilePosition movementTarget, Position squadDestination) {
        return movementTarget != null ? movementTarget.toPosition() : squadDestination;
    }

    /**
     * The straight leg from a member toward its destination, cut at {@link #MARCH_LOOKAHEAD}.
     *
     * @param from the member's position
     * @param destination where it marches
     * @return the leg
     */
    static Leg marchLeg(Position from, Position destination) {
        double length = from.getDistance(destination);
        if (length <= MARCH_LOOKAHEAD) return new Leg(from, destination);
        double t = MARCH_LOOKAHEAD / length;
        return new Leg(from, new Position((int) Math.round(from.getX() + t * (destination.getX() - from.getX())),
                (int) Math.round(from.getY() + t * (destination.getY() - from.getY()))));
    }

    /**
     * The squad's destination when a member has no movement target of its own: the known enemy building closest to
     * the squad centre, the first place a fight squad with no visible enemy marches to. When no enemy building is
     * known the squad marches to the enemy main or a scout target instead, and no march leg is drawn.
     *
     * @param squadCenter the squad's centre
     * @param enemyBuildings last known positions of the enemy's buildings
     * @return the closest, or null when none is known
     */
    static Position squadDestination(Position squadCenter, Collection<Position> enemyBuildings) {
        Position closest = null;
        double best = Double.MAX_VALUE;
        for (Position building : enemyBuildings) {
            double distance = squadCenter.getDistance(building);
            if (distance < best) {
                best = distance;
                closest = building;
            }
        }
        return closest;
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
     * in proportion to their demand, never above it. Each candidate draws on the pool at its demand times its fire
     * weight, so a Bunker whose weight fades toward nothing gives its share back to the others as it fades. The
     * weighted total never exceeds the larger of the pool and the weighted measured occupants.
     *
     * @param candidates the Bunkers this evaluation prices
     * @param pool unseen infantry known to be alive, see {@link #garrisonPool}
     */
    static void allocate(List<Candidate> candidates, int pool) {
        double measured = 0;
        double unmeasured = 0;
        for (Candidate candidate : candidates) {
            if (candidate.measured) {
                measured += candidate.demand * candidate.fireWeight;
            } else {
                unmeasured += candidate.demand * candidate.fireWeight;
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
