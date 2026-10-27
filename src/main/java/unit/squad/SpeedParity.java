package unit.squad;

import bwapi.Race;
import bwapi.UnitType;
import info.tracking.EnemySpeedTracker;
import lombok.Value;

import java.util.Map;

/**
 * The speed parity terms of a ground squad's move out decision.
 *
 * <p>A squad that is mostly Zerglings needs {@link #UNUPGRADED_LING_BONUS} more strength to move out while neither
 * Metabolic Boost nor Adrenal Glands has finished, and {@link #ADRENAL_DISCOUNT} less once Adrenal Glands has. The
 * bonus is lifted by Metabolic Boost, and by {@link #VOLUME_ZERGLINGS} Zerglings alive. Against a Zerg opponent that
 * has, or plausibly has, Metabolic Boost, a ling-heavy squad without it is held near home: it does not move out and
 * does not stay in a containment. Our Metabolic Boost and the volume lift that hold too.
 *
 * <p>A build that is an all-in or a rush is exempt from both terms and moves out on its own timing.
 *
 * <p>Strengths are in the squad's supply unit, where a Zergling is {@link UnitType#supplyRequired()}.
 */
final class SpeedParity {
    /** Strength a ling-heavy squad needs on top of its move out threshold while it has no ling upgrade. */
    static final int UNUPGRADED_LING_BONUS = 20;

    /** Strength a ling-heavy squad with Adrenal Glands needs less than its move out threshold. */
    static final int ADRENAL_DISCOUNT = 2;

    /** The lowest threshold the Adrenal Glands discount brings a threshold to. */
    static final int ADRENAL_FLOOR = 2;

    /** Zerglings alive that lift both terms, enough mass to push without the upgrades. */
    static final int VOLUME_ZERGLINGS = 64;

    private SpeedParity() {
    }

    /** What the speed parity gate does to a squad. */
    enum Term {
        /** Nothing. */
        NONE,
        /** The move out threshold carries the unupgraded ling bonus. */
        LING_UPGRADE,
        /** The squad is held home against an enemy with ling speed. */
        SPEED_PARITY
    }

    /**
     * Whether Zerglings are at least half of a composition's supply.
     *
     * @param composition unit counts by type
     * @return true when the Zerglings' supply is at least the rest's
     */
    static boolean lingHeavy(Map<UnitType, Integer> composition) {
        int total = 0;
        int lings = 0;
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            int supply = entry.getKey().supplyRequired() * entry.getValue();
            total += supply;
            if (entry.getKey() == UnitType.Zerg_Zergling) {
                lings += supply;
            }
        }
        return lings > 0 && lings * 2 >= total;
    }

    /**
     * The change to a ling-heavy squad's move out threshold from its ling upgrades.
     *
     * @param lingHeavy true for a ling-heavy squad
     * @param exempt true when the active build is an all-in or a rush
     * @param volume true when {@link #VOLUME_ZERGLINGS} Zerglings are alive
     * @param metabolicBoost true when we have Metabolic Boost
     * @param adrenalGlands true when we have Adrenal Glands
     * @return the bonus, the negative discount, or 0
     */
    static int upgradeTerm(boolean lingHeavy, boolean exempt, boolean volume, boolean metabolicBoost,
                           boolean adrenalGlands) {
        if (!lingHeavy || exempt) {
            return 0;
        }
        if (adrenalGlands) {
            return -ADRENAL_DISCOUNT;
        }
        if (metabolicBoost || volume) {
            return 0;
        }
        return UNUPGRADED_LING_BONUS;
    }

    /**
     * A threshold with a term applied: a bonus raises it, a discount lowers it but never below
     * {@link #ADRENAL_FLOOR}, or below the threshold itself when that is already lower.
     *
     * @param threshold the move out threshold before the term
     * @param term the result of {@link #upgradeTerm}
     * @return the threshold to use
     */
    static int applyTerm(int threshold, int term) {
        if (term >= 0) {
            return threshold + term;
        }
        return Math.max(threshold + term, Math.min(threshold, ADRENAL_FLOOR));
    }

    /**
     * The threshold the move out decision uses: with the gate off, the threshold unchanged.
     *
     * @param enabled true when the gate switch is on
     * @param threshold the move out threshold before the term
     * @param term the result of {@link #upgradeTerm}
     * @return the threshold to use
     */
    static int gatedThreshold(boolean enabled, int threshold, int term) {
        return enabled ? applyTerm(threshold, term) : threshold;
    }

    /**
     * Whether a squad is exempt from the hold because it is engaged: it is fighting under an active fight lock or turn-back commit, so
     * turning its back on the enemy is left to the combat simulation.
     *
     * @param status the squad's status
     * @param fightLocked true when the squad's fight lock or turn-back commit is active
     * @return true when the hold must not release the squad
     */
    static boolean exemptWhileEngaged(SquadStatus status, boolean fightLocked) {
        return status == SquadStatus.FIGHT && fightLocked;
    }

    /**
     * Whether a squad is sent home for the enemy's speed this frame. Close threats leave the decision to the combat
     * simulation, since a squad that runs from a faster enemy is caught.
     *
     * @param enabled true when the gate switch is on
     * @param holds {@link Reading#isHolds()}
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @return true when the squad is held home
     */
    static boolean holdsHome(boolean enabled, boolean holds, boolean closeThreats) {
        return enabled && holds && !closeThreats;
    }

    /**
     * Whether a ling-heavy squad is held home against an enemy with ling speed.
     *
     * @param opponentRace the opponent's race
     * @param lingHeavy true for a ling-heavy squad
     * @param exempt true when the active build is an all-in or a rush
     * @param volume true when {@link #VOLUME_ZERGLINGS} Zerglings are alive
     * @param metabolicBoost true when we have Metabolic Boost
     * @param enemyMetabolicBoost the evidence for the enemy's Metabolic Boost
     * @return true when the squad should hold near home
     */
    static boolean holdsForEnemySpeed(Race opponentRace, boolean lingHeavy, boolean exempt, boolean volume,
                                      boolean metabolicBoost, EnemySpeedTracker.Evidence enemyMetabolicBoost) {
        return opponentRace == Race.Zerg
                && lingHeavy
                && !exempt
                && !volume
                && !metabolicBoost
                && enemyMetabolicBoost != EnemySpeedTracker.Evidence.NONE;
    }

    /**
     * Names the term in force.
     *
     * @param holds result of {@link #holdsForEnemySpeed}
     * @param upgradeTerm result of {@link #upgradeTerm}
     * @return SPEED_PARITY over LING_UPGRADE, else NONE; a discount is not a term
     */
    static Term termFor(boolean holds, int upgradeTerm) {
        if (holds) {
            return Term.SPEED_PARITY;
        }
        return upgradeTerm > 0 ? Term.LING_UPGRADE : Term.NONE;
    }

    /**
     * Reads the speed parity gate for a ground squad.
     *
     * @param opponentRace the opponent's race
     * @param composition the squad's unit counts by type
     * @param exempt true when the active build is an all-in or a rush
     * @param zerglings Zerglings we have alive
     * @param metabolicBoost true when we have Metabolic Boost
     * @param adrenalGlands true when we have Adrenal Glands
     * @param enemyMetabolicBoost the evidence for the enemy's Metabolic Boost
     * @return the reading
     */
    static Reading read(Race opponentRace, Map<UnitType, Integer> composition, boolean exempt, int zerglings,
                        boolean metabolicBoost, boolean adrenalGlands,
                        EnemySpeedTracker.Evidence enemyMetabolicBoost) {
        boolean lingHeavy = lingHeavy(composition);
        boolean volume = zerglings >= VOLUME_ZERGLINGS;
        int upgradeTerm = upgradeTerm(lingHeavy, exempt, volume, metabolicBoost, adrenalGlands);
        boolean holds = holdsForEnemySpeed(opponentRace, lingHeavy, exempt, volume, metabolicBoost,
                enemyMetabolicBoost);
        return new Reading(holds, upgradeTerm, termFor(holds, upgradeTerm), ourSpeed(metabolicBoost, adrenalGlands),
                enemyMetabolicBoost, zerglings);
    }

    /** What the gate reads for a squad. */
    @Value
    static class Reading {
        boolean holds;
        int upgradeTerm;
        Term term;
        String ourSpeed;
        EnemySpeedTracker.Evidence enemyEvidence;
        int zerglings;
    }

    /**
     * Names our ling speed for telemetry.
     *
     * @param metabolicBoost true when we have Metabolic Boost
     * @param adrenalGlands true when we have Adrenal Glands
     * @return ADRENAL, METABOLIC or NONE
     */
    static String ourSpeed(boolean metabolicBoost, boolean adrenalGlands) {
        if (adrenalGlands) {
            return "ADRENAL";
        }
        return metabolicBoost ? "METABOLIC" : "NONE";
    }
}
