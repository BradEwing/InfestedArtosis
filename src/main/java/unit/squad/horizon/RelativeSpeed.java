package unit.squad.horizon;

import bwapi.UnitType;
import info.tracking.EnemySpeedTracker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Speed against the enemy it is priced against: the ling and hydra speed penalty applies only to a unit the enemy
 * out-runs, and a ground squad that cannot out-run what is on it keeps its fight.
 *
 * <p>A unit's speed is its type's top speed, raised by {@link EnemySpeedTracker#SEEN_SPEED_MARGIN} when it is a
 * Zergling or Hydralisk with its speed upgrade. The margin is the least a seen upgrade moves a unit above the base
 * speed, so two upgraded units of a type compare equal and an upgraded unit out-runs an unupgraded one of its type.
 * Other types run at their top speed.
 *
 * <p>A squad whose strength share in the {@link Mix} is at least as fast as it is, within
 * {@link #ESCAPE_DISTANCE} of it, and more than half of the enemy strength, cannot escape: a retreat gives the
 * chasers free hits, so its retreat threshold drops by {@link #ESCAPE_RETREAT_FACTOR}.
 */
public final class RelativeSpeed {
    /** What a unit missing its speed upgrade is priced at against an enemy that is faster, per unit of strength. */
    public static final double UPGRADE_PENALTY = 0.75;

    /** The distance in pixels within which a chaser at least as fast as the squad denies it a retreat. */
    public static final double ESCAPE_DISTANCE = 192;

    /** The share of the enemy strength that must be chasers within {@link #ESCAPE_DISTANCE} to block a retreat. */
    public static final double ESCAPE_SHARE = 0.5;

    /** The factor a blocked retreat scales the retreat threshold by. */
    public static final double ESCAPE_RETREAT_FACTOR = 0.75;

    private static final double SPEED_EPSILON = 1e-9;

    private RelativeSpeed() {
    }

    /**
     * The speed of a unit of a type, in pixels per frame.
     *
     * @param type the unit type
     * @param upgraded whether the unit has its speed upgrade; it only counts for a Zergling or Hydralisk
     * @return the speed
     */
    public static double speed(UnitType type, boolean upgraded) {
        double base = type.topSpeed();
        if (upgraded && EnemySpeedTracker.upgradeFor(type) != null) {
            return base * EnemySpeedTracker.SEEN_SPEED_MARGIN;
        }
        return base;
    }

    /**
     * The price multiplier of a unit missing its speed upgrade, scaled by the strength share that out-runs it.
     *
     * @param fasterShare the share of the opposing ground strength that is faster than the unit, from 0 to 1
     * @return 1 for a share of 0, {@link #UPGRADE_PENALTY} for a share of 1
     */
    public static double penalty(double fasterShare) {
        return 1.0 - (1.0 - UPGRADE_PENALTY) * Math.max(0, Math.min(1, fasterShare));
    }

    /**
     * The share of own units that are faster than a reference speed.
     *
     * @param ownSpeeds the speeds of the units
     * @param reference the speed to beat
     * @return the share, 0 when there are no units
     */
    public static double shareFasterThan(Collection<Double> ownSpeeds, double reference) {
        if (ownSpeeds.isEmpty()) return 0;
        int faster = 0;
        for (double ownSpeed : ownSpeeds) {
            if (ownSpeed > reference + SPEED_EPSILON) faster++;
        }
        return (double) faster / ownSpeeds.size();
    }

    /**
     * The retreat threshold of a ground squad: scaled by {@link #ESCAPE_RETREAT_FACTOR} when the switch is on and
     * the squad cannot escape, unchanged otherwise.
     *
     * @param enabled whether IA_RELATIVE_SPEED is on
     * @param escapeBlocked whether the squad cannot escape, see {@link Mix#canEscape}
     * @param retreatThreshold the retreat threshold the sim judges a squad against
     * @return the threshold to judge the squad against
     */
    public static double retreatThreshold(boolean enabled, boolean escapeBlocked, double retreatThreshold) {
        return enabled && escapeBlocked ? retreatThreshold * ESCAPE_RETREAT_FACTOR : retreatThreshold;
    }

    /**
     * The enemy mix our units are priced against.
     *
     * @param enabled whether IA_RELATIVE_SPEED is on
     * @param mix the enemy mix
     * @return the mix when the switch is on, null when off, which prices the speed penalty absolutely
     */
    public static Mix pricedAgainst(boolean enabled, Mix mix) {
        return enabled ? mix : null;
    }

    /**
     * The enemy a squad is priced against, by speed, strength and distance.
     */
    public static final class Mix {
        private final List<double[]> ground = new ArrayList<>();
        private double otherStrength;

        /**
         * Adds a ground unit that can move.
         *
         * @param speed the unit's speed, see {@link RelativeSpeed#speed}
         * @param strength the unit's ground strength
         * @param distance the unit's distance in pixels from the squad
         */
        public void addGround(double speed, double strength, double distance) {
            if (strength <= 0) return;
            ground.add(new double[] {speed, strength, distance});
        }

        /**
         * Adds strength that cannot chase a squad: buildings and flyers.
         *
         * @param strength the strength
         */
        public void addOther(double strength) {
            if (strength > 0) otherStrength += strength;
        }

        /**
         * The share of the enemy ground strength faster than a speed.
         *
         * @param ownSpeed the speed to beat
         * @return the share from 0 to 1, 0 when the mix holds no ground strength
         */
        public double fasterShare(double ownSpeed) {
            double total = 0;
            double faster = 0;
            for (double[] unit : ground) {
                total += unit[1];
                if (unit[0] > ownSpeed + SPEED_EPSILON) faster += unit[1];
            }
            return total > 0 ? faster / total : 0;
        }

        /**
         * Whether a ground squad can get away from the enemy: false when more than {@link #ESCAPE_SHARE} of the
         * enemy strength is ground units at least as fast as the squad within {@link #ESCAPE_DISTANCE} of it.
         *
         * @param squadSpeed the squad's speed, that of its slowest ground member
         * @return true when a retreat can leave the enemy behind
         */
        public boolean canEscape(double squadSpeed) {
            double total = otherStrength;
            double chasers = 0;
            for (double[] unit : ground) {
                total += unit[1];
                if (unit[0] >= squadSpeed - SPEED_EPSILON && unit[2] <= ESCAPE_DISTANCE) chasers += unit[1];
            }
            return total <= 0 || chasers <= ESCAPE_SHARE * total;
        }
    }
}
