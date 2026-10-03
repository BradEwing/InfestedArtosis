package strategy.buildorder.terran;

import info.TechProgression;
import telemetry.PlanEvents;

/**
 * The capped Guardian branch of {@link LurkerDefilerGuardian}, the terminal ZvT build the learning
 * module may select in place of {@link LurkerDefilerUltra}.
 *
 * <p>A Guardian outranges a Bunker and shells a sieged Tank, which cannot fire upward. The branch
 * opens only on a funded economy: the Hive stands, three bases are held and three geysers are
 * mined. It never reads the enemy; whether it is worth taking is the learning module's call.
 *
 * <p>While open the branch plans a Spire, Mutalisks to morph from, a Greater Spire and then
 * Guardians, for at most {@value #WAVE_CAP} Guardians over the game, lost ones included.
 *
 * <p>An instance carries the entered flag, and writes a GUARDIAN_BRANCH telemetry row on each change.
 */
final class GuardianBranch {

    /** Guardians the branch fields in the whole game, those lost included. */
    static final int WAVE_CAP = 3;

    /** Bases held before the branch opens. */
    static final int MIN_BASES = 3;

    /** Geysers being mined before the branch opens. */
    static final int MIN_MINING_GEYSERS = 3;

    /** What stands between the branch and opening, or {@link #OPEN}. */
    enum Gate {
        OPEN,
        NO_HIVE,
        FEW_BASES,
        FEW_GEYSERS
    }

    /** The structure or unit the branch plans next. */
    enum Step {
        SPIRE,
        MUTALISK,
        GREATER_SPIRE,
        GUARDIAN,
        NONE
    }

    private boolean entered = false;

    boolean isEntered() {
        return entered;
    }

    /**
     * The gate for one frame.
     *
     * @param hive whether the Hive stands
     * @param bases bases with a hatchery of ours
     * @param miningGeysers geysers we are mining
     * @return {@link Gate#OPEN}, or the first term that fails
     */
    static Gate gate(boolean hive, int bases, int miningGeysers) {
        if (!hive) {
            return Gate.NO_HIVE;
        }
        if (bases < MIN_BASES) {
            return Gate.FEW_BASES;
        }
        if (miningGeysers < MIN_MINING_GEYSERS) {
            return Gate.FEW_GEYSERS;
        }
        return Gate.OPEN;
    }

    /**
     * Guardians the branch may still field.
     *
     * @param guardians Guardians alive, in a Cocoon or planned
     * @param guardiansLost Guardians lost so far
     * @return the room left under {@value #WAVE_CAP}, never negative
     */
    static int guardiansRemaining(int guardians, int guardiansLost) {
        return Math.max(0, WAVE_CAP - guardians - guardiansLost);
    }

    /**
     * What the open branch plans next, one step a frame.
     *
     * <p>The Spire comes first, then Mutalisks until one stands for each Guardian still to come, then
     * the Greater Spire. No Mutalisk is planned while the Greater Spire morphs. Once it stands, a
     * Guardian is planned whenever a living Mutalisk is not already claimed by a Guardian plan, and
     * a Mutalisk when fewer stand than Guardians remain.
     *
     * @param techProgression the bot's tech state
     * @param remaining Guardians the branch may still field, from {@link #guardiansRemaining}
     * @param livingMutalisks Mutalisks alive
     * @param mutalisks Mutalisks alive or planned
     * @param guardianPlans Guardian plans still in flight
     * @return the step, or {@link Step#NONE}
     */
    static Step nextStep(TechProgression techProgression, int remaining, int livingMutalisks, int mutalisks,
                         int guardianPlans) {
        if (remaining <= 0) {
            return Step.NONE;
        }
        if (techProgression.canPlanSpire()) {
            return Step.SPIRE;
        }
        if (!techProgression.isSpire() || techProgression.isPlannedGreaterSpire()) {
            return Step.NONE;
        }
        if (techProgression.isGreaterSpire()) {
            if (livingMutalisks > guardianPlans) {
                return Step.GUARDIAN;
            }
            return mutalisks < remaining ? Step.MUTALISK : Step.NONE;
        }
        if (mutalisks < remaining) {
            return Step.MUTALISK;
        }
        if (livingMutalisks >= remaining && techProgression.canPlanGreaterSpire()) {
            return Step.GREATER_SPIRE;
        }
        return Step.NONE;
    }

    /**
     * Updates the entered flag for this frame and writes a telemetry row for each change.
     *
     * @param hive whether the Hive stands
     * @param bases bases with a hatchery of ours
     * @param miningGeysers geysers we are mining
     * @return the gate for this frame
     */
    Gate evaluate(boolean hive, int bases, int miningGeysers) {
        Gate gate = gate(hive, bases, miningGeysers);
        boolean open = gate == Gate.OPEN;
        if (open && !entered) {
            entered = true;
            PlanEvents.guardianBranch("ENTER");
        } else if (!open && entered) {
            entered = false;
            PlanEvents.guardianBranch("EXIT:" + gate);
        }
        return gate;
    }
}
