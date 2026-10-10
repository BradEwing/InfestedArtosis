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
 * <p>An {@link OpenReason#ARMY_MILESTONE} round opens once the build's army produced reaches a milestone,
 * and only while the worker gates still want Drones. Army produced counts every unit that joined the
 * living army, so losses do not lower it. It closes once
 * {@link #DRONES_PER_ROUND} more Drones are hatched or in an egg, once the build's Drone cap is met,
 * once the worker gates stop wanting Drones, or after {@link #MAX_ROUND_FRAMES}. The next milestone is then
 * {@link #ARMY_UNITS_PER_ROUND} army units past the army produced when the round closed. A threat
 * closes an open round without moving the milestone, so the round reopens once the threat clears,
 * and no round opens while one is present.
 *
 * <p>A {@link OpenReason#CONTAIN_HELD} round opens once our ground squads have held a contain for
 * {@link ContainHeldTimer#HELD_FRAMES}, in a matchup and build that allow it, while the workers are
 * under both the hard cap and the soft cap, and no sooner than {@link #CONTAIN_HELD_COOLDOWN_FRAMES}
 * after the last such round closed. At most {@link #MAX_CONTAIN_HELD_ROUNDS_PER_PERIOD} open in one contain
 * period ({@link ContainHeldTimer}), however many chains an enemy break splits it into; a new period allows
 * as many again. Its size is one Drone per hatchery and at least
 * {@link #CONTAIN_HELD_MIN_ROUND_SIZE}, cut to the workers still under the lower of the two caps; the
 * build's Drone cap does not bound it. It closes on a
 * threat, once the matchup or build no longer allows it, when the contain it opened on ends or is
 * broken, when the workers reach either cap, once
 * its Drones are hatched or in an egg, or after {@link #MAX_ROUND_FRAMES}. It never reads or moves
 * the army milestone.
 *
 * <p>Army produced counts a Hydralisk morphing into a Lurker as one more unit, because the Hydralisk
 * leaves the living count when the morph starts and the Lurker joins it when the morph completes.
 *
 * <p>A {@link OpenReason#CALM_ECONOMY} round opens once no threat has stood for
 * {@link #CALM_ECONOMY_FRAMES} while the build's Drone cap is unmet, the worker gates want Drones, and the
 * workers are at least {@link #CALM_ECONOMY_WORKER_DEFICIT} under the soft cap and under the hard cap. It
 * needs neither an army milestone nor a contain, adds up to {@link #DRONES_PER_ROUND} Drones within the
 * build's cap and the room under both worker caps, and no sooner than {@link #CALM_ECONOMY_COOLDOWN_FRAMES}
 * after the last such round closed. It never opens while the build holds it back
 * ({@link ContainHeld#isCalmEconomyHeld()}). It closes like an army milestone round, on SIZE, BUILD_CAP, HARD_CAP, THREAT or
 * TIMEOUT, and never reads or moves the army milestone.
 *
 * <p>A {@link OpenReason#BUNKER_STANCE} round opens while an enemy Bunker stance stands, see {@link BunkerStance}, and
 * the workers are under both caps, at most once per stance. It adds {@link #BUNKER_STANCE_ROUND_SIZE} Drones, cut to
 * the room under the caps, and the build's Drone cap does not bound it. It closes on a threat, when the stance stops
 * standing, when the workers reach either cap, once its Drones are hatched or in an egg, or after
 * {@link #MAX_ROUND_FRAMES}, and never reads or moves the army milestone.
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

    /**
     * Contain-held rounds one contain period may open. The later rounds of a long contain were followed by an
     * enemy break more often than the first, and each round takes larva from the army holding the line.
     * Tuning constant.
     */
    public static final int MAX_CONTAIN_HELD_ROUNDS_PER_PERIOD = 2;

    /** Frames without a threat before a calm-economy round may open: one minute. Tuning constant. */
    public static final int CALM_ECONOMY_FRAMES = 1440;

    /** Frames after a calm-economy round closes before another may open: 30 seconds. */
    public static final int CALM_ECONOMY_COOLDOWN_FRAMES = 720;

    /** Workers under the soft cap that a calm-economy round needs. Tuning constant. */
    public static final int CALM_ECONOMY_WORKER_DEFICIT = 6;

    /** Drones a Bunker stance round adds, cut to the workers still under the lower of the two caps. Tuning constant. */
    public static final int BUNKER_STANCE_ROUND_SIZE = 2;

    private static final int NEVER = Integer.MIN_VALUE / 2;

    /** Why a round opened. */
    public enum OpenReason {
        ARMY_MILESTONE,
        CONTAIN_HELD,
        CALM_ECONOMY,
        BUNKER_STANCE
    }

    /**
     * Why a round closed. BUILD_CAP is the build's own Drone cap, which army milestone and calm-economy rounds read.
     * INELIGIBLE is a contain-held round whose matchup or build no longer allows it, such as a switch to a
     * build that runs none or too few Zerglings left alive for Speedling. BUNKER_STANCE_ENDED is a Bunker stance
     * round whose stance stopped standing: the Bunker hold cleared, a Bunker was broken, the army attacks or the build
     * no longer takes the round.
     */
    public enum CloseReason {
        SIZE,
        BUILD_CAP,
        SOFT_CAP,
        HARD_CAP,
        THREAT,
        CONTAIN_ENDED,
        TIMEOUT,
        INELIGIBLE,
        BUNKER_STANCE_ENDED
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

        /** The start of the running contain period, or {@link ContainHeldTimer#NO_CHAIN}. */
        @Builder.Default
        private final int periodStartFrame = ContainHeldTimer.NO_CHAIN;

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

        /** Whether the build holds back a calm-economy round, such as while its first wave is still to come. */
        private final boolean calmEconomyHeld;

        /** The number of the enemy Bunker stance that stands, see {@link BunkerStance}, or 0 when none does. */
        private final int bunkerStanceId;

        boolean isBunkerStance() {
            return bunkerStanceId > 0;
        }

        boolean isHeld() {
            return chainStartFrame != ContainHeldTimer.NO_CHAIN && heldFrames >= ContainHeldTimer.HELD_FRAMES;
        }

        boolean underCaps() {
            return workers < hardCap && workers < softCap;
        }

        /**
         * @return workers still to add before the lower of the soft cap and the hard cap
         */
        int capRoom() {
            return Math.min(softCap, hardCap) - workers;
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
        private final int containPeriodStartFrame;
        private final int workers;
        private final int softCap;
        private final int hardCap;

        Report(OpenReason kind, String reason, int drones, int size, ContainHeld containHeld) {
            this.kind = kind;
            this.reason = reason;
            this.drones = drones;
            this.size = size;
            this.containHeldFrames = containHeld.getHeldFrames();
            this.containPeriodStartFrame = containHeld.getPeriodStartFrame();
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

    /** Army units seen to join the living army, which a loss does not take back. */
    @Getter
    private int armyProduced = 0;

    private int lastLivingArmy = 0;

    private int lastThreatFrame = 0;

    @Getter
    private int lastCalmEconomyCloseFrame = NEVER;

    /** The number of the Bunker stance the last Bunker stance round opened for, or 0 before one has. */
    @Getter
    private int lastBunkerStanceRoundId = 0;

    /** Why the last round closed, or null before one has. */
    @Getter
    private CloseReason lastCloseReason;

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

    private int countedPeriodStartFrame = ContainHeldTimer.NO_CHAIN;

    private int countedPeriodRounds = 0;

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
        trackArmy(livingArmy);
        if (threatened) {
            lastThreatFrame = frame;
        }
        if (active) {
            CloseReason close = closeReason(frame, drones, droneCap, workersWanted, threatened, containHeld);
            if (close != null) {
                close(frame, close, containHeld);
            }
            return;
        }
        if (threatened) {
            return;
        }
        if (workersWanted && armyProduced >= armyMilestone && drones < droneCap) {
            open(frame, OpenReason.ARMY_MILESTONE, Math.min(drones + DRONES_PER_ROUND, droneCap), containHeld);
            return;
        }
        if (opensContainHeldRound(frame, containHeld)) {
            countContainHeldRound(containHeld.getPeriodStartFrame());
            open(frame, OpenReason.CONTAIN_HELD, drones + containHeldRoundSize(containHeld), containHeld);
            return;
        }
        if (opensBunkerStanceRound(frame, containHeld)) {
            lastBunkerStanceRoundId = containHeld.getBunkerStanceId();
            open(frame, OpenReason.BUNKER_STANCE, drones + bunkerStanceRoundSize(containHeld), containHeld);
            return;
        }
        if (opensCalmEconomyRound(frame, drones, droneCap, workersWanted, containHeld)) {
            int size = Math.min(Math.min(DRONES_PER_ROUND, droneCap - drones), containHeld.capRoom());
            open(frame, OpenReason.CALM_ECONOMY, drones + size, containHeld);
        }
    }

    private CloseReason closeReason(int frame, int drones, int droneCap, boolean workersWanted, boolean threatened,
                                    ContainHeld containHeld) {
        switch (reason) {
            case CONTAIN_HELD:
                return containHeldClose(frame, drones, threatened, containHeld);
            case BUNKER_STANCE:
                return bunkerStanceClose(frame, drones, threatened, containHeld);
            default:
                return armyMilestoneClose(frame, drones, droneCap, workersWanted, threatened);
        }
    }

    private void trackArmy(int livingArmy) {
        if (livingArmy > lastLivingArmy) {
            armyProduced += livingArmy - lastLivingArmy;
        }
        lastLivingArmy = livingArmy;
    }

    private boolean opensBunkerStanceRound(int frame, ContainHeld containHeld) {
        return containHeld.isBunkerStance()
                && containHeld.getBunkerStanceId() != lastBunkerStanceRoundId
                && containHeld.underCaps();
    }

    /**
     * @param containHeld the workers and caps the round opens on
     * @return {@link #BUNKER_STANCE_ROUND_SIZE}, cut to the workers still under the lower of the two caps
     */
    static int bunkerStanceRoundSize(ContainHeld containHeld) {
        return Math.min(BUNKER_STANCE_ROUND_SIZE, containHeld.capRoom());
    }

    private boolean opensCalmEconomyRound(int frame, int drones, int droneCap, boolean workersWanted,
                                          ContainHeld containHeld) {
        return workersWanted
                && !containHeld.isCalmEconomyHeld()
                && drones < droneCap
                && frame - lastThreatFrame >= CALM_ECONOMY_FRAMES
                && frame - lastCalmEconomyCloseFrame >= CALM_ECONOMY_COOLDOWN_FRAMES
                && containHeld.getSoftCap() - containHeld.getWorkers() >= CALM_ECONOMY_WORKER_DEFICIT
                && containHeld.underCaps();
    }

    /**
     * @param hatcheries larva-producing hatcheries we hold
     * @return one Drone per hatchery, and at least {@link #CONTAIN_HELD_MIN_ROUND_SIZE}
     */
    public static int containHeldRoundSize(int hatcheries) {
        return Math.max(CONTAIN_HELD_MIN_ROUND_SIZE, hatcheries);
    }

    /**
     * @param containHeld the hatcheries, workers and caps the round opens on
     * @return {@link #containHeldRoundSize(int)}, cut to the workers still under the lower of the two caps
     */
    static int containHeldRoundSize(ContainHeld containHeld) {
        return Math.min(containHeldRoundSize(containHeld.getHatcheries()), containHeld.capRoom());
    }

    /**
     * @param periodStartFrame the start of a contain period
     * @return contain-held rounds opened in that period
     */
    public int containHeldRoundsInPeriod(int periodStartFrame) {
        return periodStartFrame == countedPeriodStartFrame ? countedPeriodRounds : 0;
    }

    private void countContainHeldRound(int periodStartFrame) {
        countedPeriodRounds = containHeldRoundsInPeriod(periodStartFrame) + 1;
        countedPeriodStartFrame = periodStartFrame;
    }

    private boolean opensContainHeldRound(int frame, ContainHeld containHeld) {
        return containHeld.isEligible()
                && containHeld.isHeld()
                && frame - lastContainHeldCloseFrame >= CONTAIN_HELD_COOLDOWN_FRAMES
                && containHeldRoundsInPeriod(containHeld.getPeriodStartFrame()) < MAX_CONTAIN_HELD_ROUNDS_PER_PERIOD
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
        if (!containHeld.isEligible()) {
            return CloseReason.INELIGIBLE;
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

    private CloseReason bunkerStanceClose(int frame, int drones, boolean threatened, ContainHeld containHeld) {
        if (threatened) {
            return CloseReason.THREAT;
        }
        if (!containHeld.isBunkerStance()) {
            return CloseReason.BUNKER_STANCE_ENDED;
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

    private void close(int frame, CloseReason closeReason, ContainHeld containHeld) {
        OpenReason kind = reason;
        active = false;
        reason = null;
        lastCloseReason = closeReason;
        if (kind == OpenReason.CONTAIN_HELD) {
            lastContainHeldCloseFrame = frame;
        } else if (kind == OpenReason.CALM_ECONOMY) {
            lastCalmEconomyCloseFrame = frame;
        } else if (kind != OpenReason.BUNKER_STANCE && closeReason != CloseReason.THREAT) {
            armyMilestone = armyProduced + ARMY_UNITS_PER_ROUND;
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
