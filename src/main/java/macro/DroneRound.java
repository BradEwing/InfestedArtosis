package macro;

import bwapi.UnitType;
import lombok.Builder;
import lombok.Getter;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import telemetry.PlanEvents;
import unit.squad.ContainHeldTimer;

/**
 * A window in which Drones go ahead of the advanced unit stream.
 *
 * <p>Advanced units queue at {@link UnitPlan#ADVANCED_UNIT_PRIORITY}, ahead of every Drone plan
 * numbered by the frame it was derived on, and a blocked one claims the next larva against the
 * plans behind it. A round opens for one of two {@link OpenReason reasons}. While it is open the
 * build withholds new advanced unit plans, production moves the oldest queued Drones to
 * {@link UnitPlan#DRONE_ROUND_PRIORITY} up to the round's target, the build queues a new Drone at
 * that priority only when too few are queued to reach it, and a queued advanced unit no longer
 * claims larva against the plans behind it. Only one round is open at a time.
 *
 * <p>An {@link OpenReason#ARMY_MILESTONE} round opens once the build's army reaches a milestone of
 * living units, and only while the worker gates still want Drones. It closes once
 * {@link #DRONES_PER_ROUND} more Drones are hatched or in an egg, once the build's Drone cap is met,
 * once the worker gates stop wanting Drones, or after {@link #MAX_ROUND_FRAMES}. The next milestone is then
 * {@link #ARMY_UNITS_PER_ROUND} living army units past the count the round closed on. A threat
 * closes an open round without moving the milestone, so the round reopens once the threat clears,
 * and no round opens while one is present.
 *
 * <p>A {@link OpenReason#CONTAIN_HELD} round opens once our ground squads have held a contain for
 * {@link ContainHeldTimer#HELD_FRAMES}, in a matchup and build that allow it, while the workers are
 * under both the hard cap and the soft cap, and no sooner than {@link #CONTAIN_HELD_COOLDOWN_FRAMES}
 * after the last such round closed. Its size is one Drone per hatchery and at least
 * {@link #CONTAIN_HELD_MIN_ROUND_SIZE}; the build's Drone cap does not bound it. It closes on a
 * threat, when the contain it opened on ends or is broken, when the workers reach either cap, once
 * its Drones are hatched or in an egg, or after {@link #MAX_ROUND_FRAMES}. It never reads or moves
 * the army milestone.
 *
 * <p>Every open and close is reported through {@link PlanEvents} with its reason.
 */
public class DroneRound {

    /** Living army units of the build's target types that open the first round. Tuning constant. */
    public static final int FIRST_ROUND_ARMY_UNITS = 6;

    /** Living army units past the last round's close that open the next round. Tuning constant. */
    public static final int ARMY_UNITS_PER_ROUND = 6;

    /** Drones a round adds before it closes. Tuning constant. */
    public static final int DRONES_PER_ROUND = 4;

    /** Longest a round withholds the advanced unit stream, in frames. Tuning constant. */
    public static final int MAX_ROUND_FRAMES = 1440;

    /** Frames after a contain-held round closes before another may open: 10 seconds. */
    public static final int CONTAIN_HELD_COOLDOWN_FRAMES = 240;

    /** Fewest Drones a contain-held round adds, whatever the hatchery count. */
    public static final int CONTAIN_HELD_MIN_ROUND_SIZE = 3;

    private static final int NEVER = Integer.MIN_VALUE / 2;

    /** Why a round opened. */
    public enum OpenReason {
        ARMY_MILESTONE,
        CONTAIN_HELD
    }

    /** Why a round closed. BUILD_CAP is the build's own Drone cap, which only an army milestone round reads. */
    public enum CloseReason {
        SIZE,
        BUILD_CAP,
        SOFT_CAP,
        HARD_CAP,
        THREAT,
        CONTAIN_ENDED,
        TIMEOUT
    }

    /**
     * The held contain and the worker caps a contain-held round reads.
     */
    @Getter
    @Builder
    public static final class ContainHeld {
        /** Inputs under which no contain-held round opens. */
        public static final ContainHeld NONE = ContainHeld.builder().build();

        /** Whether the matchup and the build allow contain-held rounds. */
        private final boolean eligible;

        /** The start of the running contain chain, or {@link ContainHeldTimer#NO_CHAIN}. */
        @Builder.Default
        private final int chainStartFrame = ContainHeldTimer.NO_CHAIN;

        /** Frames the running contain chain has lasted. */
        private final int heldFrames;

        /** Larva-producing hatcheries we hold. */
        private final int hatcheries;

        /** Workers gathering minerals or gas. */
        private final int workers;

        /** Workers our remaining mineral patches and mining geysers can use. */
        private final int softCap;

        /** Workers past which the worker gates want no Drone. */
        private final int hardCap;

        boolean isHeld() {
            return chainStartFrame != ContainHeldTimer.NO_CHAIN && heldFrames >= ContainHeldTimer.HELD_FRAMES;
        }

        boolean underCaps() {
            return workers < hardCap && workers < softCap;
        }
    }

    /** What a DRONE_ROUND_OPEN or DRONE_ROUND_CLOSE row reports. */
    @Getter
    public static final class Report {
        private final OpenReason kind;
        private final String reason;
        private final int drones;
        private final int size;
        private final int containHeldFrames;
        private final int workers;
        private final int softCap;
        private final int hardCap;

        Report(OpenReason kind, String reason, int drones, int size, ContainHeld containHeld) {
            this.kind = kind;
            this.reason = reason;
            this.drones = drones;
            this.size = size;
            this.containHeldFrames = containHeld.getHeldFrames();
            this.workers = containHeld.getWorkers();
            this.softCap = containHeld.getSoftCap();
            this.hardCap = containHeld.getHardCap();
        }
    }

    @Getter
    private boolean active = false;

    /** Why the open round opened, or null while no round is open. */
    @Getter
    private OpenReason reason;

    @Getter
    private int armyMilestone = FIRST_ROUND_ARMY_UNITS;

    @Getter
    private int droneTarget = 0;

    /** Drones the open round adds past the count it opened on. */
    @Getter
    private int roundSize = 0;

    /** Drones hatched plus Drones in an egg, as of the last update. */
    @Getter
    private int drones = 0;

    private int startFrame = 0;

    private int containChainStartFrame = ContainHeldTimer.NO_CHAIN;

    @Getter
    private int lastContainHeldCloseFrame = NEVER;

    /**
     * Opens or closes the round for this frame, with no contain-held round possible.
     *
     * @param frame the current frame
     * @param livingArmy living units of the build's target army types
     * @param drones Drones hatched plus Drones in an egg
     * @param droneCap the build's Drone target; zero for a build that runs no army milestone rounds
     * @param workersWanted whether the worker count is still below what the bases can use
     * @param threatened whether a threat must put the army first
     */
    public void update(int frame, int livingArmy, int drones, int droneCap, boolean workersWanted,
                       boolean threatened) {
        update(frame, livingArmy, drones, droneCap, workersWanted, threatened, ContainHeld.NONE);
    }

    /**
     * Opens or closes the round for this frame.
     *
     * @param frame the current frame
     * @param livingArmy living units of the build's target army types
     * @param drones Drones hatched plus Drones in an egg
     * @param droneCap the build's Drone target; zero for a build that runs no army milestone rounds
     * @param workersWanted whether the worker count is still below what the bases can use
     * @param threatened whether a threat must put the army first
     * @param containHeld the held contain and worker caps a contain-held round reads
     */
    public void update(int frame, int livingArmy, int drones, int droneCap, boolean workersWanted,
                       boolean threatened, ContainHeld containHeld) {
        this.drones = drones;
        if (active) {
            CloseReason close = reason == OpenReason.CONTAIN_HELD
                    ? containHeldClose(frame, drones, threatened, containHeld)
                    : armyMilestoneClose(frame, drones, droneCap, workersWanted, threatened);
            if (close != null) {
                close(frame, close, livingArmy, containHeld);
            }
            return;
        }
        if (threatened) {
            return;
        }
        if (workersWanted && livingArmy >= armyMilestone && drones < droneCap) {
            open(frame, OpenReason.ARMY_MILESTONE, Math.min(drones + DRONES_PER_ROUND, droneCap), containHeld);
            return;
        }
        if (opensContainHeldRound(frame, containHeld)) {
            open(frame, OpenReason.CONTAIN_HELD, drones + containHeldRoundSize(containHeld.getHatcheries()),
                    containHeld);
        }
    }

    /**
     * @param hatcheries larva-producing hatcheries we hold
     * @return one Drone per hatchery, and at least {@link #CONTAIN_HELD_MIN_ROUND_SIZE}
     */
    public static int containHeldRoundSize(int hatcheries) {
        return Math.max(CONTAIN_HELD_MIN_ROUND_SIZE, hatcheries);
    }

    private boolean opensContainHeldRound(int frame, ContainHeld containHeld) {
        return containHeld.isEligible()
                && containHeld.isHeld()
                && frame - lastContainHeldCloseFrame >= CONTAIN_HELD_COOLDOWN_FRAMES
                && containHeld.underCaps();
    }

    private CloseReason armyMilestoneClose(int frame, int drones, int droneCap, boolean workersWanted,
                                           boolean threatened) {
        if (threatened) {
            return CloseReason.THREAT;
        }
        if (!workersWanted) {
            return CloseReason.HARD_CAP;
        }
        if (drones >= droneTarget) {
            return CloseReason.SIZE;
        }
        if (drones >= droneCap) {
            return CloseReason.BUILD_CAP;
        }
        if (frame - startFrame >= MAX_ROUND_FRAMES) {
            return CloseReason.TIMEOUT;
        }
        return null;
    }

    private CloseReason containHeldClose(int frame, int drones, boolean threatened, ContainHeld containHeld) {
        if (threatened) {
            return CloseReason.THREAT;
        }
        if (containHeld.getChainStartFrame() != containChainStartFrame) {
            return CloseReason.CONTAIN_ENDED;
        }
        if (containHeld.getWorkers() >= containHeld.getHardCap()) {
            return CloseReason.HARD_CAP;
        }
        if (containHeld.getWorkers() >= containHeld.getSoftCap()) {
            return CloseReason.SOFT_CAP;
        }
        if (drones >= droneTarget) {
            return CloseReason.SIZE;
        }
        if (frame - startFrame >= MAX_ROUND_FRAMES) {
            return CloseReason.TIMEOUT;
        }
        return null;
    }

    private void open(int frame, OpenReason openReason, int target, ContainHeld containHeld) {
        active = true;
        reason = openReason;
        startFrame = frame;
        droneTarget = target;
        roundSize = target - drones;
        containChainStartFrame = containHeld.getChainStartFrame();
        PlanEvents.droneRoundOpened(new Report(openReason, openReason.name(), drones, roundSize, containHeld));
    }

    private void close(int frame, CloseReason closeReason, int livingArmy, ContainHeld containHeld) {
        OpenReason kind = reason;
        active = false;
        reason = null;
        if (kind == OpenReason.CONTAIN_HELD) {
            lastContainHeldCloseFrame = frame;
        } else if (closeReason != CloseReason.THREAT) {
            armyMilestone = livingArmy + ARMY_UNITS_PER_ROUND;
        }
        PlanEvents.droneRoundClosed(new Report(kind, closeReason.name(), drones, roundSize, containHeld));
    }

    /**
     * How many more Drones the open round may hold at {@link UnitPlan#DRONE_ROUND_PRIORITY}, so
     * that Drones hatched, in an egg and held at that priority never pass the round's target.
     *
     * @param roundDrones Drones held at {@link UnitPlan#DRONE_ROUND_PRIORITY} that are not yet in an egg
     * @return the Drones still to promote; zero while the round is closed
     */
    public int openDroneSlots(int roundDrones) {
        if (!active) {
            return 0;
        }
        return Math.max(0, droneTarget - drones - roundDrones);
    }

    /**
     * Whether a threat must close a round and keep the next one shut.
     *
     * @param rushed an early, cannon or SCV rush is detected
     * @param allIn the bot has committed to an all-in
     * @param visibleEnemiesAtBases enemy ground or air combat units visible at our bases
     * @return true when the army must come first
     */
    public static boolean isThreatened(boolean rushed, boolean allIn, int visibleEnemiesAtBases) {
        return rushed || allIn || visibleEnemiesAtBases > 0;
    }

    /**
     * Whether a plan is a Drone the round queued ahead of the advanced unit stream.
     *
     * @param plan a queued plan
     * @return true for a Drone plan at {@link UnitPlan#DRONE_ROUND_PRIORITY}
     */
    public static boolean isRoundDrone(Plan plan) {
        return plan.getPriority() == UnitPlan.DRONE_ROUND_PRIORITY
                && plan.getPlannedUnit() == UnitType.Zerg_Drone;
    }
}
