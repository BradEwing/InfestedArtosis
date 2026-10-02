package strategy.buildorder.terran;

import bwapi.UnitType;
import info.TechProgression;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import telemetry.PlanEvents;
import util.Time;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The capped Guardian branch of {@link LurkerDefilerUltra}, for a Terran that sits behind sieged
 * Tanks or a Bunker and has shown no way to shoot at the air.
 *
 * <p>A Guardian outranges a Bunker and shells a sieged Tank, which cannot fire upward. The branch
 * opens only on a funded economy, once the Hive stands, three bases are held and three geysers are
 * mined, while sieged Tanks or a Bunker have been seen recently. It never opens, and closes for
 * good, once a {@link #ANTI_AIR_TYPES} unit has been seen at any time: a sighting latches the
 * branch off for the rest of the game, even a unit since destroyed. The Defiler and Ultralisk
 * build stays the default and runs the whole time.
 *
 * <p>While open the branch plans a Spire, Mutalisks to morph from, a Greater Spire and then
 * Guardians, for at most {@value #WAVE_CAP} Guardians over the game, lost ones included.
 *
 * <p>An instance carries the latch and the entered flag, and writes a GUARDIAN_BRANCH telemetry row
 * on each change.
 */
final class GuardianBranch {

    /** Guardians the branch fields in the whole game, those lost included. */
    static final int WAVE_CAP = 3;

    /** Bases held before the branch opens. */
    static final int MIN_BASES = 3;

    /** Geysers being mined before the branch opens. */
    static final int MIN_MINING_GEYSERS = 3;

    /** Sieged Tanks seen recently that open the branch on their own. */
    static final int MIN_SIEGED_TANKS = 2;

    /** Bunkers seen recently that open the branch on their own. */
    static final int MIN_BUNKERS = 1;

    /** How long after it was last seen a Tank or Bunker still counts as recent. */
    static final int RECENT_FRAMES = new Time(3, 0).getFrames();

    /** The enemy units whose sighting switches the branch off for the game. */
    static final Set<UnitType> ANTI_AIR_TYPES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            UnitType.Terran_Goliath, UnitType.Terran_Missile_Turret, UnitType.Terran_Wraith,
            UnitType.Terran_Valkyrie)));

    /** What stands between the branch and opening, or {@link #OPEN}. */
    enum Gate {
        OPEN,
        DISABLED,
        LATCHED,
        NO_HIVE,
        FEW_BASES,
        FEW_GEYSERS,
        NOT_ENTRENCHED
    }

    /** What the enemy has dug in with, as far as it has been seen recently. */
    enum Entrenchment {
        NONE,
        SIEGED_TANKS,
        BUNKER,
        SIEGED_TANKS_AND_BUNKER;

        String label() {
            return this == SIEGED_TANKS_AND_BUNKER ? "SIEGED_TANKS+BUNKER" : name();
        }
    }

    /** The structure or unit the branch plans next. */
    enum Step {
        SPIRE,
        MUTALISK,
        GREATER_SPIRE,
        GUARDIAN,
        NONE
    }

    private boolean latched = false;

    private boolean entered = false;

    boolean isLatched() {
        return latched;
    }

    boolean isEntered() {
        return entered;
    }

    /**
     * Reads the entrenchment from what was seen recently.
     *
     * @param recentSiegedTanks sieged Tanks seen within {@value #RECENT_FRAMES} frames
     * @param recentBunkers Bunkers seen within {@value #RECENT_FRAMES} frames
     * @return what the enemy has dug in with
     */
    static Entrenchment entrenchment(int recentSiegedTanks, int recentBunkers) {
        boolean tanks = recentSiegedTanks >= MIN_SIEGED_TANKS;
        boolean bunker = recentBunkers >= MIN_BUNKERS;
        if (tanks && bunker) {
            return Entrenchment.SIEGED_TANKS_AND_BUNKER;
        }
        if (tanks) {
            return Entrenchment.SIEGED_TANKS;
        }
        return bunker ? Entrenchment.BUNKER : Entrenchment.NONE;
    }

    /**
     * Whether a sighting is recent enough to count.
     *
     * @param lastObservedFrame the frame the unit was last shown or hidden
     * @param currentFrame the current frame
     * @return true within {@value #RECENT_FRAMES} frames of the last sighting
     */
    static boolean isRecent(int lastObservedFrame, int currentFrame) {
        return currentFrame - lastObservedFrame <= RECENT_FRAMES;
    }

    /**
     * Living tracked units of one type last seen recently.
     *
     * @param units the living tracked enemy units
     * @param type the type to count
     * @param currentFrame the current frame
     * @return how many were seen within {@value #RECENT_FRAMES} frames
     */
    static int recentCount(Collection<ObservedUnit> units, UnitType type, int currentFrame) {
        int count = 0;
        for (ObservedUnit unit : units) {
            if (unit.getUnitType() == type && isRecent(unit.getLastObservedFrame().getFrames(), currentFrame)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The first anti-air type the enemy has ever been seen with, destroyed units included.
     *
     * @param tracker the enemy sightings
     * @param currentFrame the current frame
     * @return the type, or null while none has been seen
     */
    static UnitType seenAntiAir(ObservedUnitTracker tracker, int currentFrame) {
        Time now = new Time(currentFrame);
        for (UnitType type : ANTI_AIR_TYPES) {
            if (tracker.getUnitTypeCountBeforeTime(type, now) > 0) {
                return type;
            }
        }
        return null;
    }

    /**
     * The gate for one frame.
     *
     * <p>The entrenchment is needed to open the branch but not to keep it open, so Tanks that drop
     * out of sight once the Guardians are on the way do not close it.
     *
     * @param enabled whether the branch is switched on in the configuration
     * @param latched whether an anti-air sighting has switched the branch off
     * @param hive whether the Hive stands
     * @param bases bases with a hatchery of ours
     * @param miningGeysers geysers we are mining
     * @param entrenchment what the enemy has dug in with
     * @param entered whether the branch is already open
     * @return {@link Gate#OPEN}, or the first term that fails
     */
    static Gate gate(boolean enabled, boolean latched, boolean hive, int bases, int miningGeysers,
                     Entrenchment entrenchment, boolean entered) {
        if (!enabled) {
            return Gate.DISABLED;
        }
        if (latched) {
            return Gate.LATCHED;
        }
        if (!hive) {
            return Gate.NO_HIVE;
        }
        if (bases < MIN_BASES) {
            return Gate.FEW_BASES;
        }
        if (miningGeysers < MIN_MINING_GEYSERS) {
            return Gate.FEW_GEYSERS;
        }
        if (!entered && entrenchment == Entrenchment.NONE) {
            return Gate.NOT_ENTRENCHED;
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
     * Updates the latch and the entered flag for this frame and writes a telemetry row for each change.
     *
     * @param enabled whether the branch is switched on in the configuration
     * @param antiAirSeen the anti-air type the enemy has been seen with, or null
     * @param hive whether the Hive stands
     * @param bases bases with a hatchery of ours
     * @param miningGeysers geysers we are mining
     * @param entrenchment what the enemy has dug in with
     * @return the gate for this frame
     */
    Gate evaluate(boolean enabled, UnitType antiAirSeen, boolean hive, int bases, int miningGeysers,
                  Entrenchment entrenchment) {
        if (enabled && antiAirSeen != null && !latched) {
            latched = true;
            PlanEvents.guardianBranch("LATCH:" + antiAirSeen);
        }
        Gate gate = gate(enabled, latched, hive, bases, miningGeysers, entrenchment, entered);
        boolean open = gate == Gate.OPEN;
        if (open && !entered) {
            entered = true;
            PlanEvents.guardianBranch("ENTER:" + entrenchment.label());
        } else if (!open && entered) {
            entered = false;
            PlanEvents.guardianBranch("EXIT:" + gate);
        }
        return gate;
    }

    /**
     * Reads the entrenchment from the living tracked enemy units.
     *
     * @param tracker the enemy sightings
     * @param currentFrame the current frame
     * @return the entrenchment seen recently
     */
    static Entrenchment entrenchment(ObservedUnitTracker tracker, int currentFrame) {
        Set<ObservedUnit> living = tracker.getLivingObservedUnits();
        return entrenchment(recentCount(living, UnitType.Terran_Siege_Tank_Siege_Mode, currentFrame),
                recentCount(living, UnitType.Terran_Bunker, currentFrame));
    }
}
