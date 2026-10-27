package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import lombok.Getter;
import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Standoff staging for ground squads: where a squad gathers before it engages an enemy it has measured, when it
 * stops gathering, and where a reinforcement joins an army that is already fighting or gathering.
 *
 * <p>A squad that finds a measured enemy farther than the standoff distance holds at a point that distance from the
 * enemy, on the line toward the squad, until {@link #GATHER_SHARE} of its supply stands within
 * {@link #GATHER_RADIUS} of the squad centre, or the timeout passes. An enemy that comes inside the standoff
 * distance ends the standoff at once and the ordinary verdict acts.
 *
 * <p>A reinforcement squad that would launch while a larger army is fighting or gathering joins the rear of that
 * army, the side away from its enemy, instead of walking to the enemy on its own.
 */
public final class Standoff {
    /** Tuning value: pixels from the squad centre within which a member counts as gathered. */
    static final int GATHER_RADIUS = 128;

    /** Tuning value: share of the squad's supply that must be gathered for the standoff to end. */
    static final double GATHER_SHARE = 0.75;

    /** Tuning value: frames a squad gathers for before it engages whatever its spread. */
    static final int TIMEOUT_FRAMES = 240;

    /** Tuning value: frames a squad of a rush or an all-in gathers for, so its attack timing is kept. */
    static final int RUSH_TIMEOUT_FRAMES = 72;

    /** Tuning value: pixels past the enemy's longest ground reach that the standoff point lies. */
    static final int REACH_MARGIN = 96;

    /** Tuning value: nearest the standoff point lies to the enemy, in pixels. */
    static final int MIN_DISTANCE = 224;

    /** Tuning value: farthest the standoff point lies from the enemy, in pixels, inside the squad's detection radius. */
    static final int MAX_DISTANCE = 416;

    /** Tuning value: pixels inside the standoff distance an enemy must come for the standoff to end. */
    static final int ARRIVAL_SLACK = 48;

    /** Tuning value: pixels behind an army's centre, away from its enemy, that a reinforcement rallies to. */
    static final int REAR_OFFSET = 128;

    /** Tuning value: frames after a standoff ends during which the squad is not staged again. */
    static final int RELEASE_HOLD_FRAMES = 240;

    /** Tuning value: frames without a reading after which an open standoff is treated as abandoned. */
    static final int STALE_FRAMES = 48;

    /** Tuning value: pixels between the steps that pull a point back toward a squad until it is walkable. */
    private static final int WALKABLE_STEP = 32;

    private Standoff() {
    }

    /** What the standoff reads for a squad this frame. */
    public enum Verdict {
        /** The standoff does not apply: the squad acts on the ordinary verdict. */
        NOT_APPLICABLE,
        /** The squad gathers at the standoff point. */
        HOLD,
        /** Enough of the squad is gathered: the standoff ends and the squad engages. */
        GATHERED,
        /** The timeout passed: the standoff ends and the squad engages. */
        TIMEOUT,
        /** The enemy came inside the standoff distance: the standoff ends and the ordinary verdict acts. */
        ENEMY_ARRIVED
    }

    /** A friendly ground squad that is fighting or gathering, as a reinforcement sees it. */
    @Value
    public static class Army {
        Position center;
        Position enemyCenter;
        int strength;
    }

    /** The open standoff of one squad. */
    @Getter
    public static class Episode {
        private int startFrame = -1;
        private int touchedFrame = -1;
        private int releasedUntilFrame = -1;
        private Position point;

        /**
         * @return true while a standoff is open
         */
        public boolean isActive() {
            return startFrame >= 0;
        }

        /**
         * @param frame current frame
         * @return true when the standoff was last read longer ago than {@link #STALE_FRAMES}
         */
        boolean isStale(int frame) {
            return isActive() && frame - touchedFrame > STALE_FRAMES;
        }

        /**
         * @param frame current frame
         * @return true while the squad is inside the hold that follows the end of a standoff
         */
        boolean isReleased(int frame) {
            return frame < releasedUntilFrame;
        }

        /**
         * @param frame current frame
         * @return frames the open standoff has lasted
         */
        int framesStaged(int frame) {
            return isActive() ? frame - startFrame : 0;
        }

        void begin(int frame, Position standoffPoint) {
            startFrame = frame;
            touchedFrame = frame;
            point = standoffPoint;
        }

        void touch(int frame) {
            touchedFrame = frame;
        }

        void end() {
            startFrame = -1;
            touchedFrame = -1;
            point = null;
        }

        void foldFrom(Collection<Episode> sources) {
            for (Episode source : sources) {
                releasedUntilFrame = Math.max(releasedUntilFrame, source.releasedUntilFrame);
                if (source.isActive() && (!isActive() || source.startFrame < startFrame)) {
                    startFrame = source.startFrame;
                    point = source.point;
                }
                if (source.isActive()) {
                    touchedFrame = Math.max(touchedFrame, source.touchedFrame);
                }
            }
        }

        void release(int frame) {
            end();
            releasedUntilFrame = frame + RELEASE_HOLD_FRAMES;
        }
    }

    /**
     * Distance from the enemy at which a squad gathers: past the enemy's longest ground reach by
     * {@link #REACH_MARGIN}, and never nearer than {@link #MIN_DISTANCE} or farther than {@link #MAX_DISTANCE}.
     *
     * @param enemyReach longest ground weapon range among the measured enemies, in pixels
     * @return the distance in pixels
     */
    static int distance(int enemyReach) {
        return Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, enemyReach + REACH_MARGIN));
    }

    /**
     * @param type an enemy unit type
     * @return its ground weapon range in pixels, 0 when it cannot attack ground
     */
    static int reachOf(UnitType type) {
        return ContainmentGate.groundRange(type);
    }

    /**
     * Frames a squad gathers for before it engages.
     *
     * @param allInOrRush true when the active build is an all-in or a rush
     * @return {@link #RUSH_TIMEOUT_FRAMES} for a rush or an all-in, {@link #TIMEOUT_FRAMES} otherwise
     */
    static int timeoutFrames(boolean allInOrRush) {
        return allInOrRush ? RUSH_TIMEOUT_FRAMES : TIMEOUT_FRAMES;
    }

    /**
     * Whether the standoff may stage a squad at all.
     *
     * @param ground true for a ground squad
     * @param enemyMeasured true when the sim measured a real enemy this frame
     * @param turningBack true when the squad is retreating or inside a retreat lock
     * @param exempt true when the squad is defending home, wrapping a collapse, or otherwise out of scope
     * @param released true when the squad is inside the hold after a standoff ended
     * @return true when the squad may be staged
     */
    static boolean applies(boolean ground, boolean enemyMeasured, boolean turningBack, boolean exempt,
                           boolean released) {
        return ground && enemyMeasured && !turningBack && !exempt && !released;
    }

    /**
     * Reads the standoff for a squad that may be staged.
     *
     * @param applicable result of {@link #applies}
     * @param nearestEnemy pixels from the nearest squad member to the nearest measured enemy
     * @param standoffDistance result of {@link #distance}
     * @param gatheredShare share of the squad's supply within {@link #GATHER_RADIUS} of the squad centre
     * @param framesStaged frames the open standoff has lasted, 0 when none is open
     * @param timeoutFrames result of {@link #timeoutFrames}
     * @return the reading
     */
    static Verdict read(boolean applicable, double nearestEnemy, int standoffDistance, double gatheredShare,
                        int framesStaged, int timeoutFrames) {
        if (!applicable) {
            return Verdict.NOT_APPLICABLE;
        }
        if (nearestEnemy < standoffDistance - ARRIVAL_SLACK) {
            return Verdict.ENEMY_ARRIVED;
        }
        if (gatheredShare >= GATHER_SHARE) {
            return Verdict.GATHERED;
        }
        if (framesStaged >= timeoutFrames) {
            return Verdict.TIMEOUT;
        }
        return Verdict.HOLD;
    }

    /**
     * Whether the squad is held at the standoff point. With the switch off nothing is held, so the squad acts as it
     * does without staging.
     *
     * @param enabled {@link config.Config#staging}
     * @param verdict result of {@link #read}
     * @return true when the squad gathers
     */
    static boolean holds(boolean enabled, Verdict verdict) {
        return enabled && verdict == Verdict.HOLD;
    }

    /**
     * Share of a squad's supply that stands within {@link #GATHER_RADIUS} of its centre.
     *
     * @param positions member positions
     * @param supplies member supplies, in the order of positions
     * @param center the squad centre
     * @return the share, 1 for a squad with no supply
     */
    static double gatheredShare(List<Position> positions, List<Integer> supplies, Position center) {
        int total = 0;
        int gathered = 0;
        for (int i = 0; i < positions.size(); i++) {
            int supply = supplies.get(i);
            total += supply;
            if (positions.get(i).getDistance(center) <= GATHER_RADIUS) {
                gathered += supply;
            }
        }
        return total == 0 ? 1 : (double) gathered / total;
    }

    /**
     * The point a squad gathers at: the standoff distance from the nearest enemy, on the line from the enemy toward
     * the squad centre, pulled back toward the centre until it is walkable.
     *
     * @param enemy position of the nearest measured enemy
     * @param squadCenter the squad centre
     * @param standoffDistance result of {@link #distance}
     * @param walkable whether a position can be stood on
     * @return the point, or the squad centre when the squad is already inside the distance or no step is walkable
     */
    static Position point(Position enemy, Position squadCenter, int standoffDistance, Predicate<Position> walkable) {
        double gap = enemy.getDistance(squadCenter);
        if (gap <= standoffDistance) {
            return squadCenter;
        }
        return along(enemy, squadCenter, standoffDistance, gap, gap, walkable);
    }

    /**
     * The point a reinforcement rallies to: behind an army's centre, away from its enemy, pulled back toward the
     * centre until it is walkable.
     *
     * @param armyCenter the army's centre
     * @param enemyCenter the centre of the army's measured enemy
     * @param walkable whether a position can be stood on
     * @return the point, or the army's centre when the two centres coincide or no step is walkable
     */
    static Position rearPoint(Position armyCenter, Position enemyCenter, Predicate<Position> walkable) {
        double gap = enemyCenter.getDistance(armyCenter);
        if (gap == 0) {
            return armyCenter;
        }
        return along(enemyCenter, armyCenter, gap + REAR_OFFSET, gap, gap, walkable);
    }

    private static Position along(Position from, Position toward, double start, double end, double gap,
                                  Predicate<Position> walkable) {
        double dx = (toward.getX() - from.getX()) / gap;
        double dy = (toward.getY() - from.getY()) / gap;
        int steps = Math.max(1, (int) Math.ceil(Math.abs(end - start) / WALKABLE_STEP));
        for (int i = 0; i <= steps; i++) {
            double reach = start + (end - start) * i / steps;
            Position candidate = new Position((int) (from.getX() + dx * reach), (int) (from.getY() + dy * reach));
            if (walkable.test(candidate)) {
                return candidate;
            }
        }
        return toward;
    }

    /**
     * Where a reinforcement squad that would launch rallies instead: the rear of the nearest army at least as strong
     * as it is.
     *
     * @param squadStrength strength of the reinforcement squad
     * @param squadCenter the reinforcement squad's centre
     * @param armies friendly ground squads that are fighting or gathering and have a measured enemy
     * @param walkable whether a position can be stood on
     * @return the rear point, or null when the squad launches as it would without staging
     */
    static Position reinforcementPoint(int squadStrength, Position squadCenter, List<Army> armies,
                                       Predicate<Position> walkable) {
        Army nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Army army : armies) {
            if (army.getStrength() < squadStrength || army.getEnemyCenter() == null) {
                continue;
            }
            double distance = army.getCenter().getDistance(squadCenter);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = army;
            }
        }
        return nearest == null ? null : rearPoint(nearest.getCenter(), nearest.getEnemyCenter(), walkable);
    }

    /**
     * Whether a reinforcement squad is sent to an army's rear. With the switch off nothing is routed, so the squad
     * launches as it does without staging.
     *
     * @param enabled {@link config.Config#staging}
     * @param point result of {@link #reinforcementPoint}
     * @return true when the squad rallies to the point
     */
    static boolean routes(boolean enabled, Position point) {
        return enabled && point != null;
    }
}
