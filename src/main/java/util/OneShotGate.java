package util;

/**
 * A gate that opens once per armed episode.
 *
 * <p>Wire it as {@code condition && fire()}. The gate latches on the call, not on the condition, so
 * a {@code fire()} the condition did not guard spends the episode on a frame where nothing happened
 * and the action never runs at all. When a later occurrence counts as a new one is the caller's
 * decision, made by rearming.
 */
public class OneShotGate {

    private boolean fired;

    /**
     * @return true only on the first call after construction or {@link #rearm()}
     */
    public boolean fire() {
        if (fired) {
            return false;
        }
        fired = true;
        return true;
    }

    public void rearm() {
        fired = false;
    }
}
