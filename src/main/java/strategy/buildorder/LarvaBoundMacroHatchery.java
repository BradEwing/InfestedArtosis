package strategy.buildorder;

import bwapi.UnitType;
import info.TechProgression;

/**
 * The macro hatchery request shared by the builds that go larva bound once their tech is up.
 *
 * <p>Each build supplies its own tech condition: a finished Spire for the Mutalisk builds, a
 * finished Lair with a Hydralisk Den or Lurker Aspect for 3HatchLurker. Every other gate is the
 * same for all of them.
 */
public final class LarvaBoundMacroHatchery {

    /**
     * Unreserved minerals that count as floating: the hatchery itself, so buying one does not
     * take the bank below zero.
     */
    public static final int FLOAT_MINERALS = UnitType.Zerg_Hatchery.mineralPrice();

    /**
     * Unreserved gas that counts as floating: the gas of two Mutalisks.
     */
    public static final int FLOAT_GAS = 2 * UnitType.Zerg_Mutalisk.gasPrice();

    /**
     * The first gate a request stops on, in the order {@link #evaluate} reads them.
     */
    public enum Gate {
        /** Free larva are not fewer than the hatcheries that make them. */
        LARVA_NOT_SHORT,
        /** One of the unreserved banks is below its float bar. */
        NOT_FLOATING,
        /** The build's tech is not finished, so the bank may be waiting on its first tech units. */
        TECH_NOT_READY,
        /** Enemy ground combat units are known at our bases. */
        THREAT,
        /** A macro hatchery is already planned or under construction. */
        OUTSTANDING,
        /**
         * Every gate is open but no plan could be created: no base we hold had room for a
         * hatchery, or the hatchery enqueue had not re-armed.
         *
         * <p>Not returned by {@link #evaluate}. The request reaches it only after the gates pass
         * and the plan the request asks for comes back null, so a request that produces nothing
         * is still a row rather than silence.
         */
        PLACEMENT_UNAVAILABLE,
        /** Every gate is open. */
        TRIGGER;

        /**
         * Whether the build is larva bound and floating both banks, so the request exists and
         * this gate either answers it or withholds it.
         */
        public boolean isRequest() {
            return this != LARVA_NOT_SHORT && this != NOT_FLOATING;
        }
    }

    private LarvaBoundMacroHatchery() {
    }

    /**
     * The tech condition for the Mutalisk builds: a finished Spire.
     *
     * <p>A Spire still morphing leaves the gas banked for the first Mutalisks looking like float,
     * so a hatchery bought on it spends minerals those Mutalisks are about to need.
     *
     * @param techProgression the bot's tech state
     * @return true once a Spire is finished
     */
    public static boolean isSpireReady(TechProgression techProgression) {
        return techProgression.isSpire();
    }

    /**
     * The tech condition for the Hydralisk builds: a finished Hydralisk Den.
     *
     * <p>No Lair term, because the Den alone is what the build spends its larva and gas on. A
     * Lair follows for the Lurker or the upgrades, and waiting for it would leave the build
     * larva bound through the whole first Hydralisk wave.
     *
     * @param techProgression the bot's tech state
     * @return true once a Hydralisk Den is finished
     */
    public static boolean isHydraliskTechReady(TechProgression techProgression) {
        return techProgression.isHydraliskDen();
    }

    /**
     * The tech condition for the Lurker builds: a finished Lair with a finished Hydralisk Den or
     * Lurker Aspect researched.
     *
     * @param techProgression the bot's tech state
     * @return true once the Lair and the Den or Lurker Aspect are finished
     */
    public static boolean isLurkerTechReady(TechProgression techProgression) {
        return techProgression.isLair() && (techProgression.isHydraliskDen() || techProgression.isLurker());
    }

    /**
     * The gate the macro hatchery request stops on.
     *
     * <p>The signal is the one a larva limit produces: fewer free larva than hatcheries to make
     * them, while both unreserved banks sit above what the next purchases need. Minerals alone
     * cannot see this state, because a build that cannot find larva for its gas units floats gas
     * as well, and a bar scaled to hatchery count waits for a mineral pile the build never
     * reaches.
     *
     * <p>The banks are read after reservations, so resources a queued plan has already claimed do
     * not count as floating. The tech condition reads finished tech, so gas banked for the first
     * units the tech unlocks is not read as float while the tech is still building.
     *
     * @param techReady the build's tech condition, read from finished structures and research
     * @param larva larva not yet handed to a plan, from {@link info.GameState#numLarva()}
     * @param hatcheries completed larva-producing hatcheries
     * @param availableMinerals minerals mined and not reserved by a queued plan
     * @param availableGas gas mined and not reserved by a queued plan
     * @param enemiesAtBases enemy mobile ground combat units last known at our bases
     * @param outstandingMacroHatcheries macro hatchery plans in flight plus macro hatcheries
     *     under construction
     * @return {@link Gate#TRIGGER} when a macro hatchery should be requested, else the first
     *     gate that is shut
     */
    public static Gate evaluate(boolean techReady, int larva, int hatcheries, int availableMinerals,
                                int availableGas, int enemiesAtBases, int outstandingMacroHatcheries) {
        if (larva >= hatcheries) {
            return Gate.LARVA_NOT_SHORT;
        }
        if (availableMinerals < FLOAT_MINERALS || availableGas < FLOAT_GAS) {
            return Gate.NOT_FLOATING;
        }
        if (!techReady) {
            return Gate.TECH_NOT_READY;
        }
        if (enemiesAtBases > 0) {
            return Gate.THREAT;
        }
        if (outstandingMacroHatcheries > 0) {
            return Gate.OUTSTANDING;
        }
        return Gate.TRIGGER;
    }

    /**
     * Whether the build should add a macro hatchery.
     *
     * @return true when {@link #evaluate} opens every gate
     * @see #evaluate
     */
    public static boolean shouldPlan(boolean techReady, int larva, int hatcheries, int availableMinerals,
                                     int availableGas, int enemiesAtBases, int outstandingMacroHatcheries) {
        return evaluate(techReady, larva, hatcheries, availableMinerals, availableGas, enemiesAtBases,
                outstandingMacroHatcheries) == Gate.TRIGGER;
    }
}
