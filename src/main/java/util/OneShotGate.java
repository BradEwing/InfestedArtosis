package util;

/**
 * A gate that opens once per armed episode.
 *
 * <p>Turns a condition that holds for many frames into a single action on the frame it first
 * holds. When a later occurrence counts as a new one is the caller's decision, made by rearming.
 */
public class OneShotGate {

    private boolean fired;

    /**
     * Opens the gate.
     *
     * @return true only on the first call since the gate was armed
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
