package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import info.tracking.DarkSwarm;
import lombok.Getter;
import unit.squad.horizon.SwarmCover;
import util.Filter;

import java.util.List;
import java.util.Map;

/**
 * Commits a melee squad to fight under one of our active Dark Swarms.
 *
 * <p>A melee squad within {@link #COMMIT_RADIUS} of a swarm that covers enemies, with at least
 * {@link #MIN_REMAINING_FRAMES} left on it, takes the lock and fights, pathing into the footprint. While the lock holds
 * the squad skips the containment arc, the containment timeout, retreat locks and every sim verdict: SquadManager
 * routes a locked squad before any of them are read, see {@link #route}. A base under attack and a member standing in
 * a Psionic Storm keep their priority and release the lock, as does the swarm dropping below the horizon or expiring.
 */
@Getter
public final class SwarmLock {

    /**
     * Frames a swarm must have left for a squad to take or keep the lock, the horizon the combat sim judges over.
     */
    public static final int MIN_REMAINING_FRAMES = SwarmCover.HORIZON_FRAMES;

    /**
     * Tuning value: gap in pixels from the squad centre to the footprint within which a melee squad commits. It
     * covers any containment arc, whose radius is capped at {@link ContainmentPushback#MAX_RADIUS}.
     */
    public static final int COMMIT_RADIUS = 512;

    /**
     * Tuning value: gap in pixels from an enemy's box to the footprint within which the swarm counts as covering that
     * enemy. It lets a swarm that landed just short of a unit, or on a wall the enemy stands beside, still commit.
     */
    public static final int COVER_MARGIN = 64;

    /**
     * Tuning value: share of a squad's supply, Overlords aside, that must be Zerglings or Ultralisks for the squad to
     * count as melee.
     */
    static final double MIN_MELEE_SHARE = 0.5;

    private final int swarmId;
    private final int startFrame;

    public SwarmLock(int swarmId, int startFrame) {
        this.swarmId = swarmId;
        this.startFrame = startFrame;
    }

    /**
     * What the swarm lock does for a squad this frame.
     */
    public enum Verdict {
        /** No lock is held and none is taken. */
        NONE,
        /** The squad takes the lock this frame. */
        COMMIT,
        /** The squad holds the lock it already had. */
        HOLD,
        /** The squad drops the lock it held. */
        RELEASE
    }

    /**
     * The branch SquadManager takes for a squad before anything else about it is read.
     */
    public enum Route {
        RUNBY,
        SWARM,
        CONTAIN,
        FIGHT_SQUAD
    }

    /**
     * Decides the lock for one squad.
     *
     * <p>A squad that is no longer melee, a base under attack and a member in a Psionic Storm answer first and release
     * a held lock. A held lock then holds for as long as its swarm has {@link #MIN_REMAINING_FRAMES} left, and is
     * released once it has less or is gone. An unlocked squad commits when it is eligible and the swarm has the
     * horizon left.
     *
     * @param locked whether the squad holds a lock entering the frame
     * @param melee whether the squad is a melee squad, see {@link #isMeleeSquad}
     * @param eligible whether an unlocked squad has a swarm to commit to, see {@link #isEligible}
     * @param remainingFrames frames left on the held swarm, or on the swarm it would commit to; 0 when there is none
     * @param baseThreatened whether an enemy threatens one of our bases
     * @param inStorm whether a member stands in an active Psionic Storm
     * @return the verdict
     */
    public static Verdict verdict(boolean locked, boolean melee, boolean eligible, int remainingFrames,
                                  boolean baseThreatened, boolean inStorm) {
        if (!melee || baseThreatened || inStorm) {
            return locked ? Verdict.RELEASE : Verdict.NONE;
        }
        if (locked) {
            return remainingFrames >= MIN_REMAINING_FRAMES ? Verdict.HOLD : Verdict.RELEASE;
        }
        return eligible && remainingFrames >= MIN_REMAINING_FRAMES ? Verdict.COMMIT : Verdict.NONE;
    }

    /**
     * The branch for a squad, read before containment, the retreat lock or the sim. A runby keeps its own branch; a
     * squad committing to or holding a swarm lock fights under it whatever status it held, so a containing squad
     * leaves its arc and its containment timeout is never read, and a retreating squad's retreat lock is never read.
     *
     * @param status the squad's status entering the frame
     * @param swarm this frame's swarm lock verdict
     * @return the branch
     */
    public static Route route(SquadStatus status, Verdict swarm) {
        if (status == SquadStatus.RUNBY) {
            return Route.RUNBY;
        }
        if (swarm == Verdict.COMMIT || swarm == Verdict.HOLD) {
            return Route.SWARM;
        }
        if (status == SquadStatus.CONTAIN) {
            return Route.CONTAIN;
        }
        return Route.FIGHT_SQUAD;
    }

    /**
     * Whether a squad counts as melee: at least {@link #MIN_MELEE_SHARE} of its supply, Overlords aside, is Zerglings
     * or Ultralisks.
     *
     * @param composition unit counts by type
     * @return true for a melee squad
     */
    public static boolean isMeleeSquad(Map<UnitType, Integer> composition) {
        int melee = 0;
        int total = 0;
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            UnitType type = entry.getKey();
            if (type == UnitType.Zerg_Overlord) {
                continue;
            }
            int supply = type.supplyRequired() * entry.getValue();
            total += supply;
            if (isMelee(type)) {
                melee += supply;
            }
        }
        return total > 0 && melee >= MIN_MELEE_SHARE * total;
    }

    /**
     * @param type our unit's type
     * @return true for a Zergling or an Ultralisk
     */
    public static boolean isMelee(UnitType type) {
        return type == UnitType.Zerg_Zergling || type == UnitType.Zerg_Ultralisk;
    }

    /**
     * Whether a squad may commit to a swarm: the squad centre is within {@link #COMMIT_RADIUS} of the footprint and
     * the swarm covers at least one enemy.
     *
     * @param swarm the swarm
     * @param squadCenter the squad centre
     * @param coversEnemy whether an enemy stands within {@link #COVER_MARGIN} of the footprint, see
     *                    {@link #coversEnemy}
     * @return true when the squad may commit
     */
    public static boolean isEligible(DarkSwarm swarm, Position squadCenter, boolean coversEnemy) {
        return coversEnemy && swarm.gap(squadCenter) <= COMMIT_RADIUS;
    }

    /**
     * Whether a covered enemy of this type is worth committing a squad to: an armed unit other than a worker, or a
     * building that can attack a ground unit. A worker, an unarmed unit or a building such as a Supply Depot may still
     * be attacked once the squad is committed, but never draws the commit.
     *
     * @param type the enemy's type
     * @return true when the enemy can make a swarm eligible
     */
    public static boolean isCommitTarget(UnitType type) {
        if (type.isBuilding()) {
            return Filter.isHostileBuildingToGround(type);
        }
        return type.canAttack() && !type.isWorker();
    }

    /**
     * Whether an enemy's box lies within {@link #COVER_MARGIN} of the footprint.
     *
     * @param swarm the swarm
     * @param position the enemy's position
     * @param type the enemy's type
     * @return true when the swarm covers it for the lock
     */
    public static boolean coversEnemy(DarkSwarm swarm, Position position, UnitType type) {
        return swarm.gap(position, type) <= COVER_MARGIN;
    }

    /**
     * The swarm a squad without a lock commits to: of the swarms it is eligible for with the horizon left, the one
     * nearest its centre.
     *
     * @param swarms our active swarms
     * @param squadCenter the squad centre
     * @param eligible for each swarm, in the same order, whether the squad is eligible for it
     * @return the swarm, or null when there is none
     */
    public static DarkSwarm choose(List<DarkSwarm> swarms, Position squadCenter, List<Boolean> eligible) {
        return nearest(swarms, squadCenter, eligible, MIN_REMAINING_FRAMES);
    }

    /**
     * Of the swarms marked eligible with at least the given frames left, the one nearest the squad centre.
     *
     * @param swarms our active swarms
     * @param squadCenter the squad centre
     * @param eligible for each swarm, in the same order, whether the squad is eligible for it
     * @param minRemainingFrames frames a swarm must have left to be chosen
     * @return the swarm, or null when there is none
     */
    public static DarkSwarm nearest(List<DarkSwarm> swarms, Position squadCenter, List<Boolean> eligible,
                                    int minRemainingFrames) {
        DarkSwarm best = null;
        double bestGap = Double.MAX_VALUE;
        for (int i = 0; i < swarms.size(); i++) {
            DarkSwarm swarm = swarms.get(i);
            if (!eligible.get(i) || swarm.getRemainingFrames() < minRemainingFrames) {
                continue;
            }
            double gap = swarm.gap(squadCenter);
            if (gap < bestGap) {
                bestGap = gap;
                best = swarm;
            }
        }
        return best;
    }
}
