package unit.squad;

import bwapi.UnitType;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;
import util.Time;

public class AirSquad extends Squad {

    /**
     * Frames a sim-backed ENGAGE commits the flock to FIGHT against later RETREAT verdicts.
     */
    static final int ENGAGE_COMMITMENT_FRAMES = 72;

    /**
     * Fraction of the flock's peak hit points, lost since the commitment was armed, that
     * releases the commitment early.
     */
    static final double COMMITMENT_HP_LOSS_THRESHOLD = 0.2;

    private int commitmentStartFrame = 0;
    private int commitmentPeakHitPoints = 0;

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
     * Sets the squad's status. Any status other than FIGHT ends the current engage commitment, so the
     * next sim-backed ENGAGE arms a fresh one.
     *
     * @param status new status
     */
    @Override
    public void setStatus(SquadStatus status) {
        super.setStatus(status);
        if (status != SquadStatus.FIGHT) {
            commitmentStartFrame = 0;
            commitmentPeakHitPoints = 0;
        }
    }

    /**
     * Arms the engage commitment unless one was already armed since the squad last left FIGHT. A
     * commitment is armed at most once per FIGHT episode, so it bounds how early the episode can end
     * and does not extend itself.
     *
     * @param currentFrame frame of the sim-backed ENGAGE verdict
     * @param flockHitPoints summed hit points of the flock on that frame
     */
    public void armEngageCommitment(int currentFrame, int flockHitPoints) {
        if (commitmentStartFrame > 0) {
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
        if (commitmentPeakHitPoints <= 0) {
            return false;
        }
        double lost = (double) (commitmentPeakHitPoints - flockHitPoints) / commitmentPeakHitPoints;
        return lost < COMMITMENT_HP_LOSS_THRESHOLD;
    }

    /**
     * Drops the retreat lock so this frame's verdict decides the squad's status.
     */
    public void releaseRetreatLock() {
        retreatLockedUntilFrame = 0;
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
