package unit.squad;

/**
 * Keeps a squad out of a contain for a while after attrition or an outranged arc sent it back, unless its own or the
 * enemy's makeup has changed materially since.
 *
 * <p>The cooldown is armed with the squad's supply and the enemy army supply at the exit. It holds for
 * {@link #COOLDOWN_FRAMES}, and ends early when the squad's supply has grown to {@link #FRIENDLY_GROWTH} times the
 * supply it left with, or the enemy army supply has shrunk to {@link #ENEMY_SHRINK} times the supply it had.
 */
public class ContainmentReentryCooldown {

    /**
     * Frames after the exit during which the squad is kept out of a contain: 1440, one minute.
     */
    static final int COOLDOWN_FRAMES = 1440;

    /**
     * How many times its exit supply the squad must reach to be let back in early.
     */
    static final double FRIENDLY_GROWTH = 1.5;

    /**
     * The fraction of its exit army supply the enemy must fall to for the squad to be let back in early.
     */
    static final double ENEMY_SHRINK = 0.67;

    private int untilFrame;
    private int exitSupply;
    private int exitEnemySupply;

    /**
     * Arms the cooldown at an attrition or outranged exit.
     *
     * @param currentFrame frame of the exit
     * @param squadSupply the squad's supply at the exit
     * @param enemyArmySupply the enemy's known ground army supply at the exit
     */
    public void arm(int currentFrame, int squadSupply, int enemyArmySupply) {
        untilFrame = currentFrame + COOLDOWN_FRAMES;
        exitSupply = squadSupply;
        exitEnemySupply = enemyArmySupply;
    }

    /**
     * Whether the squad is still barred from a contain.
     *
     * @param currentFrame current frame
     * @param squadSupply the squad's supply now
     * @param enemyArmySupply the enemy's known ground army supply now
     * @return true inside the cooldown window while neither side's makeup has changed materially
     */
    public boolean blocks(int currentFrame, int squadSupply, int enemyArmySupply) {
        if (currentFrame >= untilFrame) {
            return false;
        }
        return !friendlyGrew(squadSupply) && !enemyShrank(enemyArmySupply);
    }

    /**
     * Folds another squad's cooldown into this one when squads merge, keeping the one that lasts longest.
     *
     * @param other cooldown of a source squad
     */
    public void absorb(ContainmentReentryCooldown other) {
        if (other.untilFrame > untilFrame) {
            untilFrame = other.untilFrame;
            exitSupply = other.exitSupply;
            exitEnemySupply = other.exitEnemySupply;
        }
    }

    /**
     * Clears the cooldown.
     */
    public void reset() {
        untilFrame = 0;
        exitSupply = 0;
        exitEnemySupply = 0;
    }

    private boolean friendlyGrew(int squadSupply) {
        return squadSupply >= exitSupply * FRIENDLY_GROWTH;
    }

    private boolean enemyShrank(int enemyArmySupply) {
        return enemyArmySupply <= exitEnemySupply * ENEMY_SHRINK;
    }
}
