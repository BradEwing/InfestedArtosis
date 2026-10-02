package unit.squad;

import bwapi.UnitType;
import telemetry.CommitmentRelease;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;
import util.Time;

import java.util.Collection;

public class AirSquad extends Squad {

    /**
     * Frames a sim-backed ENGAGE commits the flock to FIGHT against later RETREAT verdicts.
     */
    static final int ENGAGE_COMMITMENT_FRAMES = 72;

    /**
     * Fraction of the flock's peak hit points, lost since the commitment was armed, that
     * releases the commitment early.
     */
    static final double COMMITMENT_HP_LOSS_THRESHOLD = 0.1;

    /**
     * Fraction of the engage threshold below which a RETREAT verdict's ratio releases the commitment: a read this
     * far under the threshold is taken as a lost fight rather than a sim wobble.
     */
    static final double COMMITMENT_RELEASE_RATIO = 0.8;

    private int commitmentStartFrame = 0;
    private int commitmentPeakHitPoints = 0;
    private boolean commitmentBarred = false;
    private final AirStallDetector stallDetector = new AirStallDetector();

    public AirSquad() {
        super();
        this.setCombatSimulator(new HorizonCombatSimulator());
        this.fightHysteresis = new Time(12);
        this.retreatHysteresis = new Time(36);
    }

    @Override
    public boolean isAirSquad() {
        return true;
    }

    /**
     * @return the detector that reads this squad's FIGHT and RETREAT crossings
     */
    public AirStallDetector getStallDetector() {
        return stallDetector;
    }

    @Override
    public void addUnit(ManagedUnit managedUnit) {
        super.addUnit(managedUnit);
        updateCombatSimulator();
    }

    @Override
    public void removeUnit(ManagedUnit managedUnit) {
        super.removeUnit(managedUnit);
        updateCombatSimulator();
    }

    /**
     * Sets the squad's status. Any status other than FIGHT ends the current engage commitment and lifts a bar on
     * arming one, so the next sim-backed ENGAGE arms a fresh one.
     *
     * @param status new status
     */
    @Override
    public void setStatus(SquadStatus status) {
        super.setStatus(status);
        if (status != SquadStatus.FIGHT) {
            commitmentStartFrame = 0;
            commitmentPeakHitPoints = 0;
            commitmentBarred = false;
        }
    }

    /**
     * Arms the engage commitment unless one was already armed since the squad last left FIGHT, or the FIGHT
     * episode was opened by a retreat lock yield (see {@link #barEngageCommitment}). A commitment is armed at
     * most once per FIGHT episode, so it bounds how early the episode can end and does not extend itself.
     *
     * @param currentFrame frame of the sim-backed ENGAGE verdict
     * @param flockHitPoints summed hit points of the flock on that frame
     */
    public void armEngageCommitment(int currentFrame, int flockHitPoints) {
        if (commitmentBarred || commitmentStartFrame > 0) {
            return;
        }
        commitmentStartFrame = currentFrame;
        commitmentPeakHitPoints = flockHitPoints;
    }

    /**
     * Whether the engage commitment still holds the flock in FIGHT against a RETREAT verdict.
     *
     * <p>The commitment holds for {@link #ENGAGE_COMMITMENT_FRAMES} after it was armed, unless the flock
     * has lost at least {@link #COMMITMENT_HP_LOSS_THRESHOLD} of the peak hit points it has held since.
     * The peak absorbs members that join mid commitment, and a member that dies counts its whole hit
     * points as lost.
     *
     * @param currentFrame current frame
     * @param flockHitPoints summed hit points of the flock on this frame
     * @return true if the flock stays committed to the fight
     */
    public boolean engageCommitmentHolds(int currentFrame, int flockHitPoints) {
        if (commitmentStartFrame == 0 || currentFrame >= commitmentStartFrame + ENGAGE_COMMITMENT_FRAMES) {
            return false;
        }
        commitmentPeakHitPoints = Math.max(commitmentPeakHitPoints, flockHitPoints);
        return commitmentPeakHitPoints > 0 && !hitPointsLost(commitmentPeakHitPoints, flockHitPoints);
    }

    /**
     * @param peakHitPoints the peak hit points the flock held since the commitment was armed, above zero
     * @param flockHitPoints summed hit points of the flock on this frame
     * @return true if the flock has lost at least {@link #COMMITMENT_HP_LOSS_THRESHOLD} of its peak hit points
     */
    static boolean hitPointsLost(int peakHitPoints, int flockHitPoints) {
        return (double) (peakHitPoints - flockHitPoints) / peakHitPoints >= COMMITMENT_HP_LOSS_THRESHOLD;
    }

    /**
     * Whether a RETREAT verdict ends the engage commitment whatever the flock's hit points: its ratio is below
     * {@link #COMMITMENT_RELEASE_RATIO} of the engage threshold, the verdict has no threshold to judge it by, or
     * the enemy it sampled includes a building that can shoot the flock.
     *
     * @param ratio the verdict's overall strength ratio
     * @param engageThreshold the engage threshold the verdict was judged against
     * @param staticAntiAir whether the verdict sampled a building that can attack air
     * @return true if the verdict is let through and the flock retreats
     */
    static boolean retreatReleasesCommitment(double ratio, double engageThreshold, boolean staticAntiAir) {
        return staticAntiAir || engageThreshold <= 0 || ratio < engageThreshold * COMMITMENT_RELEASE_RATIO;
    }

    /**
     * Why a RETREAT verdict the commitment did not hold against ended the armed engage commitment.
     *
     * @param currentFrame current frame
     * @param flockHitPoints summed hit points of the flock on this frame
     * @param ratio the verdict's overall strength ratio
     * @param engageThreshold the engage threshold the verdict was judged against
     * @param staticAntiAir whether the verdict sampled a building that can attack air
     * @return the release term, or NONE when no commitment is armed
     */
    public CommitmentRelease commitmentRelease(int currentFrame, int flockHitPoints, double ratio,
                                               double engageThreshold, boolean staticAntiAir) {
        if (commitmentStartFrame == 0) {
            return CommitmentRelease.NONE;
        }
        return releaseTerm(staticAntiAir, ratio, engageThreshold,
                currentFrame >= commitmentStartFrame + ENGAGE_COMMITMENT_FRAMES,
                Math.max(commitmentPeakHitPoints, flockHitPoints), flockHitPoints);
    }

    /**
     * The term that let a RETREAT verdict through an armed commitment: static anti-air, then a ratio release (see
     * {@link #retreatReleasesCommitment}), then an expired window, then an empty flock, then the flock's hit point
     * loss (see {@link #hitPointsLost}).
     *
     * @param staticAntiAir whether the verdict sampled a building that can attack air
     * @param ratio the verdict's overall strength ratio
     * @param engageThreshold the engage threshold the verdict was judged against
     * @param expired whether the commitment's window has run out
     * @param peakHitPoints the peak hit points the flock held since the commitment was armed
     * @param flockHitPoints summed hit points of the flock on this frame
     * @return the release term, NONE when none of them applies
     */
    static CommitmentRelease releaseTerm(boolean staticAntiAir, double ratio, double engageThreshold,
                                         boolean expired, int peakHitPoints, int flockHitPoints) {
        if (staticAntiAir) {
            return CommitmentRelease.STATIC_AA;
        }
        if (retreatReleasesCommitment(ratio, engageThreshold, false)) {
            return CommitmentRelease.RATIO;
        }
        if (expired) {
            return CommitmentRelease.EXPIRED;
        }
        if (peakHitPoints <= 0) {
            return CommitmentRelease.EMPTY_FLOCK;
        }
        return hitPointsLost(peakHitPoints, flockHitPoints) ? CommitmentRelease.HP : CommitmentRelease.NONE;
    }

    /**
     * Bars the current FIGHT episode from arming an engage commitment, for a flock whose retreat lock just
     * yielded to a strong ENGAGE: the turn around it makes is on the sim's word alone and is not held against
     * the next RETREAT verdict. Leaving FIGHT lifts the bar.
     */
    public void barEngageCommitment() {
        commitmentBarred = true;
        commitmentStartFrame = 0;
        commitmentPeakHitPoints = 0;
    }

    /**
     * Folds the merge sources' state into this squad. A merged squad still in FIGHT keeps the earliest engage
     * commitment among its air sources, so a merge never lengthens a commitment, and is barred from arming one
     * when any air source was. Its peak hit points restart from the merged flock's hit points on the next check.
     *
     * @param sources squads being merged into this one
     */
    @Override
    public void inheritStateFrom(Collection<Squad> sources) {
        super.inheritStateFrom(sources);
        for (Squad source : sources) {
            if (source instanceof AirSquad) {
                stallDetector.absorb(((AirSquad) source).stallDetector);
            }
        }
        commitmentStartFrame = 0;
        commitmentPeakHitPoints = 0;
        commitmentBarred = false;
        if (getStatus() != SquadStatus.FIGHT) {
            return;
        }
        for (Squad source : sources) {
            if (!(source instanceof AirSquad)) {
                continue;
            }
            commitmentBarred |= ((AirSquad) source).commitmentBarred;
            int sourceStart = ((AirSquad) source).commitmentStartFrame;
            if (sourceStart > 0 && (commitmentStartFrame == 0 || sourceStart < commitmentStartFrame)) {
                commitmentStartFrame = sourceStart;
            }
        }
    }

    /**
     * Restarts the commitment's peak hit points from the flock's hit points on the next check, for a flock that
     * handed living members to another squad, so the members it gave away do not count as lost.
     */
    public void rebaseEngageCommitment() {
        commitmentPeakHitPoints = 0;
    }

    private void updateCombatSimulator() {
        if (hasOnly(UnitType.Zerg_Scourge)) {
            if (!(getCombatSimulator() instanceof ScourgeCombatSimulator)) {
                this.setCombatSimulator(new ScourgeCombatSimulator());
            }
        } else if (!(getCombatSimulator() instanceof HorizonCombatSimulator)) {
            this.setCombatSimulator(new HorizonCombatSimulator());
        }
    }
}
