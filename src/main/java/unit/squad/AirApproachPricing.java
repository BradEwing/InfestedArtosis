package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;
import unit.squad.horizon.UnitStrength;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Prices a flock's approach to a strike point against the mobile anti-air that can reach the path or the target
 * zone, before the flock enters and again while it flies.
 *
 * <p>Mobile anti-air is every known threat that is not a structure, Interceptors left out; static defense is read by
 * the defense zones and the entry refusal. The anti-air that counts is the mobile threats whose zone, grown by a
 * Mutalisk's padding, covers a point of the straight line from the flock to the strike point, and those within
 * {@link AirHarassScouting#NEW_AA_ZONE} of the strike point or covering it. Its summed strength is held against the
 * flock's tolerance, see {@link AirHarassEvaluator#tolerance}, the anti-air strength the flock engages rather than
 * avoids: within it the flock enters, ENTER. Above it, anti-air that alone out-trades the flock at the target
 * ABORTs the approach, and anti-air on the path alone is flown around when a clear hop around its zones exists,
 * REROUTE, and aborts the approach when none does. A pricing a caller answers by taking another target reads
 * REROUTE with the reason OTHER_TARGET, see {@link Result#toOtherTarget}.
 *
 * <p>Every decision is a static function over plain values.
 */
public final class AirApproachPricing {

    /**
     * What the pricing decides.
     */
    public enum Decision {
        ENTER,
        REROUTE,
        ABORT
    }

    /**
     * Why the pricing decided.
     */
    public enum Reason {
        CLEAR,
        DETOUR,
        TARGET_DEFENDED,
        NO_DETOUR,
        OTHER_TARGET
    }

    private AirApproachPricing() {
    }

    /**
     * The outcome of pricing one approach.
     */
    @Getter
    public static final class Result {
        private final Decision decision;
        private final Reason reason;
        private final double mobileStrength;
        private final double flockStrength;
        private final List<AirHarassTargeting.AirThreat> involved;
        private final List<AirHarassTargeting.AirThreat> around;

        Result(Decision decision, Reason reason, double mobileStrength, double flockStrength,
               List<AirHarassTargeting.AirThreat> involved, List<AirHarassTargeting.AirThreat> around) {
            this.decision = decision;
            this.reason = reason;
            this.mobileStrength = mobileStrength;
            this.flockStrength = flockStrength;
            this.involved = involved;
            this.around = around;
        }

        /**
         * @return this pricing's reading with the decision REROUTE for another target, the one it priced out left
         */
        public Result toOtherTarget() {
            return new Result(Decision.REROUTE, Reason.OTHER_TARGET, mobileStrength, flockStrength, involved,
                    Collections.emptyList());
        }

        /**
         * @return true when the flock may fly the approach, entering it or flying around the anti-air
         */
        public boolean flies() {
            return decision != Decision.ABORT;
        }

        /**
         * @return the mobile anti-air on the path to fly around, or none unless the decision is REROUTE with a detour
         */
        public List<AirHarassTargeting.AirThreat> routeAround() {
            return decision == Decision.REROUTE ? around : Collections.emptyList();
        }

        /**
         * @return how many mobile anti-air units were priced
         */
        public int units() {
            return involved.size();
        }

        /**
         * @return a key that is equal for two results that decide the same way for the same reason
         */
        public String key() {
            return decision + "/" + reason;
        }
    }

    /**
     * The mobile anti-air among known threats: every threat that is not a structure and not an Interceptor.
     *
     * @param threats every known anti-air threat
     * @return the mobile threats, in their original order
     */
    public static List<AirHarassTargeting.AirThreat> mobile(Collection<AirHarassTargeting.AirThreat> threats) {
        List<AirHarassTargeting.AirThreat> mobile = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (isMobile(threat)) {
                mobile.add(threat);
            }
        }
        return mobile;
    }

    /**
     * @param threat an anti-air threat
     * @return true for a threat that is not a structure and not an Interceptor
     */
    public static boolean isMobile(AirHarassTargeting.AirThreat threat) {
        return !threat.getType().isBuilding() && threat.getType() != UnitType.Protoss_Interceptor;
    }

    /**
     * The threats that are not priced as mobile anti-air: the structures and the Interceptors.
     *
     * @param threats every known anti-air threat
     * @return the structures, in their original order
     */
    public static List<AirHarassTargeting.AirThreat> structures(Collection<AirHarassTargeting.AirThreat> threats) {
        List<AirHarassTargeting.AirThreat> structures = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : threats) {
            if (!isMobile(threat)) {
                structures.add(threat);
            }
        }
        return structures;
    }

    /**
     * The mobile threats whose zone, grown by the Mutalisk padding, covers a point of the straight line between two
     * points, sampled every {@link AirHarassTargeting#SEGMENT_STEP} pixels.
     *
     * @param mobile the mobile threats
     * @param from the line's start
     * @param to the line's end
     * @return the threats on the path, in their original order
     */
    static List<AirHarassTargeting.AirThreat> alongPath(Collection<AirHarassTargeting.AirThreat> mobile,
                                                        Position from, Position to) {
        int padding = AirHarassTargeting.padding();
        double length = from.getDistance(to);
        int steps = Math.max(1, (int) Math.ceil(length / AirHarassTargeting.SEGMENT_STEP));
        List<AirHarassTargeting.AirThreat> onPath = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : mobile) {
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                Position sample = new Position(from.getX() + (int) Math.round((to.getX() - from.getX()) * t),
                        from.getY() + (int) Math.round((to.getY() - from.getY()) * t));
                if (threat.margin(sample, padding) <= 0) {
                    onPath.add(threat);
                    break;
                }
            }
        }
        return onPath;
    }

    /**
     * The mobile threats at a strike point: within {@link AirHarassScouting#NEW_AA_ZONE} of it, or with a zone,
     * grown by {@link AirHarassEvaluator#STRIKE_RADIUS}, that covers it.
     *
     * @param mobile the mobile threats
     * @param strike the strike point
     * @return the threats at the target, in their original order
     */
    static List<AirHarassTargeting.AirThreat> atTarget(Collection<AirHarassTargeting.AirThreat> mobile,
                                                       Position strike) {
        List<AirHarassTargeting.AirThreat> atTarget = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : mobile) {
            if (threat.getPosition().getDistance(strike) <= AirHarassScouting.NEW_AA_ZONE
                    || threat.covers(strike, AirHarassEvaluator.STRIKE_RADIUS)) {
                atTarget.add(threat);
            }
        }
        return atTarget;
    }

    /**
     * Prices the approach from the flock to a strike point.
     *
     * @param threats every known anti-air threat, the remembered members of the defense zones among them
     * @param from the flock's center
     * @param strike the strike point
     * @param tolerance anti-air strength the flock accepts, see {@link AirHarassEvaluator#tolerance}
     * @param healthyMutas healthy Mutalisks in the flock
     * @param allowed points the flock may fly to
     * @return the decision and what it read
     */
    public static Result price(Collection<AirHarassTargeting.AirThreat> threats, Position from, Position strike,
                               double tolerance, int healthyMutas, Predicate<Position> allowed) {
        double flockStrength = healthyMutas * UnitStrength.airToGround(UnitType.Zerg_Mutalisk);
        List<AirHarassTargeting.AirThreat> mobile = mobile(threats);
        List<AirHarassTargeting.AirThreat> onPath = alongPath(mobile, from, strike);
        List<AirHarassTargeting.AirThreat> atTarget = atTarget(mobile, strike);
        Set<Integer> seen = new HashSet<>();
        List<AirHarassTargeting.AirThreat> involved = new ArrayList<>();
        for (List<AirHarassTargeting.AirThreat> group : Arrays.asList(onPath, atTarget)) {
            for (AirHarassTargeting.AirThreat threat : group) {
                if (seen.add(threat.getId())) {
                    involved.add(threat);
                }
            }
        }
        double mobileStrength = strength(involved);
        if (mobileStrength <= tolerance) {
            return new Result(Decision.ENTER, Reason.CLEAR, mobileStrength, flockStrength, involved,
                    Collections.emptyList());
        }
        if (strength(atTarget) > tolerance) {
            return new Result(Decision.ABORT, Reason.TARGET_DEFENDED, mobileStrength, flockStrength, involved,
                    Collections.emptyList());
        }
        Set<Integer> targetIds = new HashSet<>();
        for (AirHarassTargeting.AirThreat threat : atTarget) {
            targetIds.add(threat.getId());
        }
        List<AirHarassTargeting.AirThreat> around = new ArrayList<>();
        for (AirHarassTargeting.AirThreat threat : onPath) {
            if (!targetIds.contains(threat.getId())) {
                around.add(threat);
            }
        }
        Position hop = AirHarassTargeting.edgePoint(from, around, strike, allowed, true);
        if (hop != null && AirHarassTargeting.minMargin(hop, around) > 0) {
            return new Result(Decision.REROUTE, Reason.DETOUR, mobileStrength, flockStrength, involved, around);
        }
        return new Result(Decision.ABORT, Reason.NO_DETOUR, mobileStrength, flockStrength, involved,
                Collections.emptyList());
    }

    /**
     * @param threats anti-air threats
     * @return the sum of their strengths
     */
    static double strength(Collection<AirHarassTargeting.AirThreat> threats) {
        double total = 0;
        for (AirHarassTargeting.AirThreat threat : threats) {
            total += threat.getStrength();
        }
        return total;
    }
}
