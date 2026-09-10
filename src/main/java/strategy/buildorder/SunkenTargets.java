package strategy.buildorder;

import util.Time;

/**
 * Static defense rules shared by the build order layer, which asks how many sunkens a base wants,
 * and the reaction layer, which decides whether the main is somewhere a sunken may stand.
 *
 * <p>Both layers read the same Barracks threshold. A count asked for at the main only becomes
 * sunkens if BaseData already treats the main as eligible, so a reaction that granted eligibility
 * on a different trigger than the one raising the count would leave a target no base can satisfy.
 */
public final class SunkenTargets {

    /**
     * Enemy Barracks that read as committed bio production rather than a single early rax.
     */
    public static final int BARRACKS_PRESSURE_COUNT = 3;

    public static final int BARRACKS_PRESSURE_SUNKENS = 3;

    public static final int ONE_BASE_SUNKENS = 2;

    private static final Time ONE_BASE_RESPONSE_OPENS = new Time(5, 0);

    private SunkenTargets() {
    }

    /**
     * Whether the Barracks the bot has seen are a bio push it must answer with static defense at
     * the main.
     * <p>
     * Reads living observed Barracks, so the pressure recedes when the production behind it dies
     * rather than when a clock runs out.
     *
     * @param enemyBarracks living enemy Barracks we have observed
     * @return true while the bio production seen so far warrants sunkens at the main
     */
    public static boolean isBarracksPressure(int enemyBarracks) {
        return enemyBarracks >= BARRACKS_PRESSURE_COUNT;
    }

    /**
     * Sunkens per base the enemy's Barracks count asks for, in every matchup.
     * <p>
     * Race agnostic on purpose. The Terran matchup prices Barracks inside its own threat ordered
     * chain, but the build orders that never reach a matchup class - SpeedlingAllIn, which plays
     * every race, and every opener - would otherwise see nothing at all off three Barracks. The
     * count is zero against a non Terran opponent, so this is inert in the matchups it does not
     * describe, and it reads before the opponent's race is even revealed.
     *
     * @param enemyBarracks living enemy Barracks we have observed
     * @return the floor the Barracks count sets, or zero
     */
    public static int barracksPressureSunkens(int enemyBarracks) {
        return isBarracksPressure(enemyBarracks) ? BARRACKS_PRESSURE_SUNKENS : 0;
    }

    /**
     * Sunkens per base an enemy still on one base asks for, in every matchup.
     * <p>
     * An opponent who has not expanded by the time this opens is spending on army instead, which
     * every race answers the same way. The time term is a lower bound rather than a window: 1Base
     * stays in the detected set once it fires, so this holds a floor while the rest of the target
     * moves with what is actually on the field.
     *
     * @param oneBaseDetected whether StrategyTracker has detected 1Base
     * @param gameTime current game time
     * @return the floor the detection sets, or zero
     */
    public static int oneBaseSunkens(boolean oneBaseDetected, Time gameTime) {
        if (!oneBaseDetected || gameTime.lessThanOrEqual(ONE_BASE_RESPONSE_OPENS)) {
            return 0;
        }
        return ONE_BASE_SUNKENS;
    }

    /**
     * The sunkens per base a build order asks for: its matchup term under the race agnostic
     * floors.
     * <p>
     * The floors are taken as maxima, not added on. Detections accumulate and are never retracted,
     * so an enemy that is both on one base and pushing reads both, and adding would price the same
     * army twice. A matchup that already prices one of these higher keeps its own answer.
     *
     * @param matchupSunkens the sunkens the matchup asks for
     * @param oneBaseDetected whether StrategyTracker has detected 1Base
     * @param enemyBarracks living enemy Barracks we have observed
     * @param gameTime current game time
     * @return sunkens per base
     */
    public static int sunkenTarget(int matchupSunkens, boolean oneBaseDetected, int enemyBarracks, Time gameTime) {
        int floor = Math.max(oneBaseSunkens(oneBaseDetected, gameTime), barracksPressureSunkens(enemyBarracks));
        return Math.max(matchupSunkens, floor);
    }
}
