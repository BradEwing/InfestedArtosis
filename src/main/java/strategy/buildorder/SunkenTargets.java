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

    static final int ONE_BASE_SUNKENS = 2;

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
     * The sunkens per base a build order asks for: its matchup term under the race agnostic 1Base
     * floor.
     * <p>
     * The floor is taken as a maximum, not added on. Detections accumulate and are never
     * retracted, so an enemy that is both on one base and pushing reads both, and adding would
     * price the same army twice.
     *
     * @param matchupSunkens the sunkens the matchup asks for
     * @param oneBaseDetected whether StrategyTracker has detected 1Base
     * @param gameTime current game time
     * @return sunkens per base
     */
    public static int sunkenTarget(int matchupSunkens, boolean oneBaseDetected, Time gameTime) {
        return Math.max(matchupSunkens, oneBaseSunkens(oneBaseDetected, gameTime));
    }
}
