package unit.squad;

import bwapi.Position;
import bwapi.Race;
import bwapi.UnitType;
import lombok.Builder;
import lombok.Getter;
import unit.squad.horizon.UnitStrength;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Squad level decisions of an air harass: whether a Mutalisk squad starts harassing, which enemy base it raids,
 * how much anti-air it accepts, and when it stops.
 *
 * <p>Every decision is a static function over plain values, so it can be tested without a live game. Strengths are
 * priced with the table the combat sim uses ({@link UnitStrength}); the constants are tuning values, not Brood War
 * facts.
 *
 * <p>The tolerance scales with the number of healthy Mutalisks: {@link #toleranceRatio} grows by
 * {@link #TOLERANCE_RATIO_PER_MUTA} for every healthy Mutalisk past {@link #TOLERANCE_FREE_MUTAS}, up to
 * {@link #MAX_TOLERANCE_RATIO}, and the flock accepts that share of its own air-to-ground strength in enemy anti-air.
 * A small flock avoids every zone; a large one may take on a Missile Turret, a Goliath, or whatever stands inside
 * one.
 */
public final class AirHarassEvaluator {

    static final int MIN_HEALTHY_MUTAS = SquadManager.AIR_MOVE_OUT_UNITS;
    static final int EXIT_HEALTHY_MUTAS = 3;
    static final double HEALTHY_HP_FRACTION = 0.6;
    static final int TOLERANCE_FREE_MUTAS = 2;
    static final double TOLERANCE_RATIO_PER_MUTA = 0.05;
    static final double MAX_TOLERANCE_RATIO = 0.6;
    static final int STRIKE_RADIUS = 128;
    static final int ARRIVE_RADIUS = 320;
    static final double HP_LOSS_EXIT_FRACTION = 0.3;
    static final int NO_TARGET_FRAMES = 480;
    static final int HARASS_TICK = 12;
    static final int REENTRY_HOLD_FRAMES = 480;
    static final double CONTAIN_AWAY_WEIGHT = 1.0;
    static final int CONTAIN_AWAY_SCALE = 1536;

    private AirHarassEvaluator() {
    }

    /**
     * Outcome of the entry gates, naming the first gate that refused.
     *
     * <p>NO_TARGET means no known enemy base holds enough heat to raid. DEFENDED means some base does, but every
     * such point lies under more anti-air than the flock tolerates, whether or not the flock cooled the rest of the
     * base by visiting it.
     */
    public enum EntryVerdict {
        ENTER,
        WRONG_MATCHUP,
        NOT_MUTALISKS,
        TOO_FEW,
        BASE_UNDER_ATTACK,
        NO_TARGET,
        DEFENDED
    }

    /**
     * Why a harass ended.
     */
    public enum ExitReason {
        BASE_UNDER_ATTACK,
        TOO_FEW,
        HP_LOSS,
        AA_ARRIVED,
        NO_TARGET,
        WIPED_OUT
    }

    /**
     * One enemy base a harass could raid.
     *
     * @param <T> the base's key
     */
    @Getter
    public static final class BaseOption<T> {
        private final T base;
        private final Position strikePoint;
        private final double heat;
        private final boolean heated;
        private final double containDistance;

        /**
         * @param base the base
         * @param strikePoint hottest point at the base whose anti-air the flock tolerates, or null when none is
         * @param heat heat at the strike point
         * @param heated true when any point at the base holds heat, tolerated or not
         * @param containDistance pixels from the base to the nearest held containment arc, or -1 with none held
         */
        public BaseOption(T base, Position strikePoint, double heat, boolean heated, double containDistance) {
            this.base = base;
            this.strikePoint = strikePoint;
            this.heat = heat;
            this.heated = heated;
            this.containDistance = containDistance;
        }
    }

    /**
     * Inputs to the entry gates.
     */
    @Getter
    @Builder
    public static final class EntryInput {
        private final Race opponentRace;
        private final Map<UnitType, Integer> composition;
        private final int healthyMutas;
        private final boolean basesUnderAttack;
        @Builder.Default
        private final List<BaseOption<?>> options = Collections.emptyList();
    }

    /**
     * Inputs to the exit check.
     */
    @Getter
    @Builder
    public static final class ExitInput {
        private final boolean basesUnderAttack;
        private final int healthyMutas;
        private final double hpLossFraction;
        private final boolean strikeDefended;
        private final double flockDefense;
        private final double tolerance;
    }

    /**
     * Whether a Mutalisk counts as healthy.
     *
     * @param hitPoints its hit points
     * @param maxHitPoints its maximum hit points
     * @return true at or above {@link #HEALTHY_HP_FRACTION} of the maximum
     */
    public static boolean isHealthy(int hitPoints, int maxHitPoints) {
        return maxHitPoints > 0 && hitPoints >= HEALTHY_HP_FRACTION * maxHitPoints;
    }

    /**
     * Share of the flock's own strength it accepts in enemy anti-air.
     *
     * @param healthyMutas healthy Mutalisks in the squad
     * @return the ratio, 0 up to {@link #TOLERANCE_FREE_MUTAS} and never above {@link #MAX_TOLERANCE_RATIO}
     */
    public static double toleranceRatio(int healthyMutas) {
        double ratio = TOLERANCE_RATIO_PER_MUTA * (healthyMutas - TOLERANCE_FREE_MUTAS);
        return Math.max(0, Math.min(MAX_TOLERANCE_RATIO, ratio));
    }

    /**
     * Anti-air strength the flock engages rather than avoids.
     *
     * @param healthyMutas healthy Mutalisks in the squad
     * @return the tolerance, in the combat sim's strength units
     */
    public static double tolerance(int healthyMutas) {
        return toleranceRatio(healthyMutas) * healthyMutas * UnitStrength.airToGround(UnitType.Zerg_Mutalisk);
    }

    /**
     * Whether a squad's air combat units are all Mutalisks. Escorting Overlords are ignored.
     *
     * @param composition unit counts by type
     * @return true when the squad holds Mutalisks and no other air combat unit
     */
    public static boolean mutalisksOnly(Map<UnitType, Integer> composition) {
        int mutas = composition.getOrDefault(UnitType.Zerg_Mutalisk, 0);
        return mutas > 0 && SquadManager.airMoveOutUnits(composition) == mutas;
    }

    /**
     * Whether the harass plays against a race. Against Zerg the air war is Mutalisk against Mutalisk and Scourge,
     * which the harass does not model, and against an unknown race nothing is known yet.
     *
     * @param race opponent race
     * @return true against Terran and Protoss
     */
    public static boolean harassMatchup(Race race) {
        return race == Race.Terran || race == Race.Protoss;
    }

    /**
     * Runs the entry gates in order and names the first one that refuses.
     *
     * @param input squad and intelligence state
     * @return ENTER, or the first refusing gate
     */
    public static EntryVerdict entryVerdict(EntryInput input) {
        if (!harassMatchup(input.getOpponentRace())) {
            return EntryVerdict.WRONG_MATCHUP;
        }
        if (!mutalisksOnly(input.getComposition())) {
            return EntryVerdict.NOT_MUTALISKS;
        }
        if (input.getHealthyMutas() < MIN_HEALTHY_MUTAS) {
            return EntryVerdict.TOO_FEW;
        }
        if (input.isBasesUnderAttack()) {
            return EntryVerdict.BASE_UNDER_ATTACK;
        }
        boolean heated = false;
        boolean tolerated = false;
        for (BaseOption<?> option : input.getOptions()) {
            heated |= option.isHeated();
            tolerated |= option.getStrikePoint() != null;
        }
        if (!heated) {
            return EntryVerdict.NO_TARGET;
        }
        return tolerated ? EntryVerdict.ENTER : EntryVerdict.DEFENDED;
    }

    /**
     * Picks the base to raid: among the bases with a tolerated strike point, the highest
     * {@link #baseScore}.
     *
     * @param options candidate bases
     * @param <T> base key
     * @return the chosen option, or null when no base has a tolerated strike point
     */
    public static <T> BaseOption<T> chooseBase(Collection<BaseOption<T>> options) {
        BaseOption<T> best = null;
        double bestScore = -1;
        for (BaseOption<T> option : options) {
            if (option.getStrikePoint() == null) {
                continue;
            }
            double score = baseScore(option.getHeat(), option.getContainDistance());
            if (score > bestScore) {
                best = option;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * How much a base is worth raiding: its heat, raised by up to {@link #CONTAIN_AWAY_WEIGHT} for a base far from
     * the contain, so the harass hits the side the contain does not hold. With no contain held, the heat alone.
     *
     * @param heat heat at the strike point
     * @param containDistance pixels to the nearest held containment arc, or negative with none held
     * @return the score
     */
    public static double baseScore(double heat, double containDistance) {
        if (containDistance < 0) {
            return heat;
        }
        return heat * (1 + CONTAIN_AWAY_WEIGHT * Math.min(1.0, containDistance / CONTAIN_AWAY_SCALE));
    }

    /**
     * Whether the harass ends on this decision tick, and why.
     *
     * <p>In order: a base of ours under attack, too few healthy Mutalisks left, the flock losing more than
     * {@link #HP_LOSS_EXIT_FRACTION} of the hit points it started with, and anti-air arriving, which is the target
     * base keeping no tolerated strike point or the flock itself standing in more anti-air than it tolerates.
     *
     * @param input squad and intelligence state
     * @return the reason, or null to keep harassing
     */
    public static ExitReason exitReason(ExitInput input) {
        if (input.isBasesUnderAttack()) {
            return ExitReason.BASE_UNDER_ATTACK;
        }
        if (input.getHealthyMutas() < EXIT_HEALTHY_MUTAS) {
            return ExitReason.TOO_FEW;
        }
        if (input.getHpLossFraction() > HP_LOSS_EXIT_FRACTION) {
            return ExitReason.HP_LOSS;
        }
        if (input.isStrikeDefended() || input.getFlockDefense() > input.getTolerance()) {
            return ExitReason.AA_ARRIVED;
        }
        return null;
    }

    /**
     * Share of the hit points the flock started with that it has lost, dead Mutalisks included.
     *
     * @param startHitPoints summed hit points at the start of the harass
     * @param currentHitPoints summed hit points of the Mutalisks alive now
     * @return the lost share, 0 when nothing was lost
     */
    public static double hpLossFraction(int startHitPoints, int currentHitPoints) {
        if (startHitPoints <= 0) {
            return 0;
        }
        return Math.max(0, startHitPoints - currentHitPoints) / (double) startHitPoints;
    }

    /**
     * Whether the squad moves on to another base: the target base is gone, has no heat left, or the flock has been
     * at it for {@link #NO_TARGET_FRAMES} without a target to hit.
     *
     * @param targetGone true when the target base is no longer a known enemy base
     * @param heated true when any point at the target base holds heat
     * @param arrived true once the flock has reached the target base
     * @param now current frame
     * @param lastProgressFrame last frame a Mutalisk had a target, or the frame the base was targeted
     * @return true to retarget
     */
    public static boolean shouldRetarget(boolean targetGone, boolean heated, boolean arrived, int now,
                                         int lastProgressFrame) {
        return targetGone || !heated || arrived && now - lastProgressFrame >= NO_TARGET_FRAMES;
    }

    /**
     * Whether the flock has reached its target.
     *
     * @param center squad center
     * @param strikePoint strike point
     * @return true within {@link #ARRIVE_RADIUS} of it
     */
    public static boolean arrived(Position center, Position strikePoint) {
        return center != null && strikePoint != null && center.getDistance(strikePoint) <= ARRIVE_RADIUS;
    }

    /**
     * Whether an air squad's harass entry is checked this frame: every {@link #HARASS_TICK} frames, and never under
     * a retreat or fight lock.
     *
     * @param airSquad true for an air squad
     * @param retreatLocked true while the retreat lock holds
     * @param fightLocked true while the fight lock holds
     * @param now current frame
     * @return true when the check runs
     */
    public static boolean entryCheckDue(boolean airSquad, boolean retreatLocked, boolean fightLocked, int now) {
        return airSquad && !retreatLocked && !fightLocked && now % HARASS_TICK == 0;
    }

    /**
     * Whether a squad that left a harass holds on a blind sim ADVANCE instead of marching on it. For
     * {@link #REENTRY_HOLD_FRAMES} after the harass ended, the squad goes back out only through the harass entry,
     * or on a sim verdict that measured a real enemy.
     *
     * @param harassExitFrame frame the squad's last harass ended, or 0 when it never harassed
     * @param now current frame
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param status the squad's status entering the tick
     * @return true to hold
     */
    public static boolean holdsBlindAdvance(int harassExitFrame, int now, boolean enemyMeasured, SquadStatus status) {
        if (harassExitFrame <= 0 || enemyMeasured || status == SquadStatus.FIGHT) {
            return false;
        }
        return now - harassExitFrame <= REENTRY_HOLD_FRAMES;
    }

    /**
     * The exit a squad removed for being empty closes: WIPED_OUT for a harassing squad, whose harass would
     * otherwise end with no exit at all, and none for any other status.
     *
     * @param status the squad's status
     * @param size members left in the squad
     * @return WIPED_OUT, or null
     */
    public static ExitReason removalExit(SquadStatus status, int size) {
        return status == SquadStatus.HARASS && size == 0 ? ExitReason.WIPED_OUT : null;
    }

    /**
     * Whether an enemy death is credited to a harass: a harassing Mutalisk was attacking it, or one stood in kill
     * range of it while no unit of another squad did, so a kill made by the army or a runby beside the flock is not
     * booked as a harass kill.
     *
     * @param targeted true when a harassing Mutalisk had the dead enemy as its target
     * @param harassNear true when a harassing Mutalisk stood within the credit radius of the death
     * @param otherNear true when a member of any other fight squad stood within the credit radius of the death
     * @return true to credit the kill
     */
    public static boolean creditsKill(boolean targeted, boolean harassNear, boolean otherNear) {
        return targeted || harassNear && !otherNear;
    }

    /**
     * Whether a squad decision tick is due.
     *
     * @param now current frame
     * @param lastTickFrame frame of the previous decision tick
     * @return true every {@link #HARASS_TICK} frames
     */
    public static boolean decisionTickDue(int now, int lastTickFrame) {
        return now - lastTickFrame >= HARASS_TICK;
    }
}
