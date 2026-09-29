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
 * {@link #MIN_REMAINING_FRAMES} left on it, takes the lock and fights, pathing into the footprint, provided the combat
 * sim, pricing the swarm's cover, does not read RETREAT. The swarm negates ordinary ranged direct attacks only, so a
 * RETREAT read under that pricing is carried by what the swarm leaves whole, such as sieged-tank splash. While the lock
 * holds the squad skips the containment arc, the containment timeout and retreat locks: SquadManager routes a locked
 * squad before any of them are read, see {@link #route}. A base under attack, a member standing in a Psionic Storm and a
 * RETREAT read of the swarm-priced sim well enough under the engage threshold, see {@link #releasesOnRead}, release the
 * lock, as does the swarm dropping below the horizon or expiring; see {@link Release} for the reasons in the order
 * they are checked. A squad released on such a read commits to no swarm for {@link #RECOMMIT_COOLDOWN_FRAMES}.
 */
@Getter
public final class SwarmLock {

    /**
     * Frames a swarm must have left for a squad to take or keep the lock, the horizon the combat sim judges over.
     */
    public static final int MIN_REMAINING_FRAMES = SwarmCover.HORIZON_FRAMES;

    /**
     * Tuning value: the cover share, see {@link SwarmCover#coverShare}, above which a squad's sim read counts as
     * covered for a commit. Zero, so any cover counts.
     */
    public static final double MIN_COMMIT_COVER = 0;

    /**
     * Tuning value: the multiple of the engage threshold a sim read with no cover for the squad must reach for it to
     * take a lock, see {@link #commitsOnRead}.
     */
    public static final double UNCOVERED_COMMIT_MARGIN = 1.25;

    /**
     * Tuning value: frames a base threat keeps standing against the lock after it was last seen, see
     * {@link #baseThreatStands}, so a threat that flickers at a base does not commit and release a squad each time.
     */
    public static final int BASE_THREAT_HOLD_FRAMES = 48;

    /**
     * Tuning value: frames after a squad drops a lock on a RETREAT read of the sim before it may commit to any swarm,
     * see {@link #mayCommit}. Two seconds of game time, long enough to outlast a read that sits either side of the
     * engage threshold for a few frames, or a squad turning between two nearby swarms, and short beside a swarm's
     * {@link #MIN_REMAINING_FRAMES}, so a squad the sim would send back in still gets most of the swarm's life.
     */
    public static final int RECOMMIT_COOLDOWN_FRAMES = 48;

    /**
     * Tuning value: the multiple of the engage threshold below which a RETREAT read releases a held lock, see
     * {@link #releasesOnRead}. A held lock survives a read just under the threshold, so a squad that committed on a
     * read just over it is not released on the next.
     */
    public static final double RELEASE_HYSTERESIS = 0.9;

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
     * Why a squad may not hold, or take, a swarm lock this frame, in the order the reasons are checked.
     */
    public enum Release {
        /** Nothing stands against the lock. */
        NONE,
        /** The squad is no longer a melee squad, see {@link #isMeleeSquad}. */
        NOT_MELEE,
        /** An enemy threatens one of our bases, which keeps priority as base defence. */
        BASE_THREAT,
        /** A member stands in an active Psionic Storm, which keeps priority as storm escape. */
        STORM,
        /** The swarm has been removed. */
        GONE,
        /** The swarm has fewer than {@link #MIN_REMAINING_FRAMES} left. */
        HORIZON,
        /** The combat sim, pricing the swarm's cover, reads RETREAT, see {@link #releasesOnRead}. */
        SIM_RETREAT,
        /**
         * The squad merged into one that took another source's lock, see {@link #droppedByMerge}. Not returned by
         * {@link #releaseReason}.
         */
        MERGED
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
     * The first reason, in the order of {@link Release}, that a squad may not hold or take the lock on a swarm.
     *
     * @param melee whether the squad is a melee squad, see {@link #isMeleeSquad}
     * @param swarmGone whether the swarm has been removed
     * @param remainingFrames frames left on the swarm
     * @param baseThreatened whether an enemy threatens one of our bases
     * @param inStorm whether a member stands in an active Psionic Storm
     * @param simRetreat whether the combat sim's read, pricing the swarm's cover, refuses or releases the lock, see
     *                   {@link #releasesOnRead}; false when it has not run
     * @return the reason, or {@link Release#NONE}
     */
    public static Release releaseReason(boolean melee, boolean swarmGone, int remainingFrames, boolean baseThreatened,
                                        boolean inStorm, boolean simRetreat) {
        if (!melee) {
            return Release.NOT_MELEE;
        }
        if (baseThreatened) {
            return Release.BASE_THREAT;
        }
        if (inStorm) {
            return Release.STORM;
        }
        if (swarmGone) {
            return Release.GONE;
        }
        if (remainingFrames < MIN_REMAINING_FRAMES) {
            return Release.HORIZON;
        }
        return simRetreat ? Release.SIM_RETREAT : Release.NONE;
    }

    /**
     * Decides the lock for one squad: a held lock holds while nothing stands against it and is released otherwise,
     * and an unlocked squad commits when it is eligible and nothing stands against the lock.
     *
     * @param locked whether the squad holds a lock entering the frame
     * @param eligible whether an unlocked squad has a swarm to commit to, see {@link #isEligible}
     * @param reason what stands against the lock, see {@link #releaseReason}
     * @return the verdict
     */
    public static Verdict verdict(boolean locked, boolean eligible, Release reason) {
        if (locked) {
            return reason == Release.NONE ? Verdict.HOLD : Verdict.RELEASE;
        }
        return eligible && reason == Release.NONE ? Verdict.COMMIT : Verdict.NONE;
    }

    /**
     * Whether a base threat stands against the lock this frame: one threatens a base now, or did within
     * {@link #BASE_THREAT_HOLD_FRAMES}.
     *
     * @param threatenedNow whether an enemy threatens one of our bases this frame
     * @param frame the current frame
     * @param lastThreatFrame the last frame a base threat was seen, or a negative value for never
     * @return true when the threat stands
     */
    public static boolean baseThreatStands(boolean threatenedNow, int frame, int lastThreatFrame) {
        return threatenedNow || lastThreatFrame >= 0 && frame - lastThreatFrame < BASE_THREAT_HOLD_FRAMES;
    }

    /**
     * Whether an unlocked squad eligible for a swarm may take the lock on this frame's sim read, which already is not
     * RETREAT. A read that prices cover above {@link #MIN_COMMIT_COVER} for the squad's own members, see
     * {@link SwarmCover#coverShare}, may commit. A read with no cover, from a squad still too far from the footprint to
     * be priced into it, takes no part of its verdict from the swarm, so it commits only when its ratio reaches
     * {@link #UNCOVERED_COMMIT_MARGIN} times the engage threshold; a read just over the threshold is left to the
     * ordinary fight path. A held lock is not read this way, see {@link #releasesOnRead}.
     *
     * @param eligible whether the squad has a swarm to commit to, see {@link #isEligible}
     * @param swarmCover the squad's cover share in the sim read, or a negative value when the read carried none
     * @param ratio the read's strength ratio
     * @param engageThreshold the ratio at which the read engages
     * @return true when the squad may commit
     */
    public static boolean commitsOnRead(boolean eligible, double swarmCover, double ratio, double engageThreshold) {
        return eligible && (swarmCover > MIN_COMMIT_COVER || ratio >= engageThreshold * UNCOVERED_COMMIT_MARGIN);
    }

    /**
     * Whether this frame's sim read stands against the lock as {@link Release#SIM_RETREAT}. An unlocked squad is
     * refused on any RETREAT read. A held lock is released only on a RETREAT read whose ratio falls below
     * {@link #RELEASE_HYSTERESIS} times the engage threshold.
     *
     * @param locked whether the squad holds a lock entering the frame
     * @param simRetreat whether the combat sim, pricing the swarm's cover, reads RETREAT
     * @param ratio the read's strength ratio, 0 when the read left no snapshot
     * @param engageThreshold the ratio at which the read engages
     * @return true when the read refuses the commit or releases the held lock
     */
    public static boolean releasesOnRead(boolean locked, boolean simRetreat, double ratio, double engageThreshold) {
        if (!simRetreat) {
            return false;
        }
        return !locked || ratio < engageThreshold * RELEASE_HYSTERESIS;
    }

    /**
     * Whether an unlocked squad may commit to any swarm this frame: it has not dropped a lock on a RETREAT read of the
     * sim within {@link #RECOMMIT_COOLDOWN_FRAMES}, so a sim that reads either side of its threshold cannot pull the
     * squad in and out of one swarm, or between two, frame by frame.
     *
     * @param frame the current frame
     * @param simRetreatReleaseFrame the frame the squad last dropped a lock on {@link Release#SIM_RETREAT}, or a
     *                               negative value for never
     * @return true when the squad may commit
     */
    public static boolean mayCommit(int frame, int simRetreatReleaseFrame) {
        return simRetreatReleaseFrame < 0 || frame - simRetreatReleaseFrame >= RECOMMIT_COOLDOWN_FRAMES;
    }

    /**
     * Whether the combat sim must run before the lock is decided: the lock would be held or taken on every other
     * ground, so only the sim's read can still refuse it.
     *
     * @param locked whether the squad holds a lock entering the frame
     * @param eligible whether an unlocked squad has a swarm to commit to
     * @param reason what stands against the lock before the sim is read
     * @return true when the sim decides the lock
     */
    public static boolean simDecides(boolean locked, boolean eligible, Release reason) {
        return reason == Release.NONE && (locked || eligible);
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
     * Whether a merge drops a source squad's lock: the source held one and the merged squad holds none, or holds the
     * lock on another swarm.
     *
     * @param source the source squad's lock, or null
     * @param merged the merged squad's lock, or null
     * @return true when the source's lock is gone after the merge
     */
    public static boolean droppedByMerge(SwarmLock source, SwarmLock merged) {
        return source != null && (merged == null || merged.getSwarmId() != source.getSwarmId());
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
