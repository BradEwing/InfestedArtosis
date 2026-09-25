package info;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Per-base record of Creep Colony builders lost on the way to a colony site, and the hold each
 * loss places on planning another colony at that base.
 *
 * <p>A cancelled colony plan frees its base, so without a hold the sunken target is unmet again on
 * the next frame and a fresh pair is planned on the same tile, reserving minerals and sending the
 * next drone into the units that killed the last one. The hold grows linearly with the losses at
 * the base, the way {@link BaseData#expansionHold(int)} does, and is capped so it stays a hold.
 *
 * <p>The loss count outlives the hold. A base that has lost a builder keeps losing the home-site
 * dispatch carve-out until a colony there starts morphing or the base is lost, so the builder sent
 * once the hold lifts still waits for its site and route to clear.
 *
 * <p>Generic over the base type because bwem Base is final with a package private constructor and
 * cannot be built in a test.
 *
 * @param <B> the base type
 */
public class ColonyBuilderBackoff<B> {

    /**
     * One step of the hold. The LUZ950B5 builders died 320 to 450 frames after one another, each
     * the frame after a re-queue, so one step outlasts a single re-queue cycle.
     */
    static final int COLONY_BUILDER_BACKOFF_FRAMES = 480;

    /**
     * Four steps cap the hold at 1,920 frames, about 80 seconds: long enough that a base being
     * farmed stops asking, short enough that a sunken is still wanted when the hold lifts.
     */
    static final int MAX_COLONY_BACKOFF_STEPS = 4;

    private final Map<B, Integer> lostBuilders = new HashMap<>();
    private final Map<B, Integer> heldUntil = new HashMap<>();

    /**
     * Records a builder lost at a base and holds the base for a window that grows with its losses.
     *
     * @param base the base the colony was planned for
     * @param currentFrame frame the builder was lost on
     * @return the frame the hold on the base lifts
     */
    public int recordLoss(B base, int currentFrame) {
        int losses = lostBuilders.getOrDefault(base, 0) + 1;
        lostBuilders.put(base, losses);
        int until = currentFrame + hold(losses);
        heldUntil.put(base, until);
        return until;
    }

    /**
     * @return true while the base's hold has not lifted
     */
    public boolean isHeld(B base, int currentFrame) {
        return currentFrame < heldUntil.getOrDefault(base, 0);
    }

    /**
     * @return true when a builder has been lost at the base since the last colony there started
     *     morphing or the base was lost
     */
    public boolean hasLostBuilder(B base) {
        return lostBuilders.getOrDefault(base, 0) > 0;
    }

    /**
     * @return builders lost at the base since the last reset
     */
    public int losses(B base) {
        return lostBuilders.getOrDefault(base, 0);
    }

    /**
     * Forgets the base's losses and lifts its hold.
     */
    public void reset(B base) {
        lostBuilders.remove(base);
        heldUntil.remove(base);
    }

    /**
     * The candidates that may be offered a colony pair this frame, per {@link #isOpen}.
     *
     * @param candidates bases short of their sunken target
     * @param currentFrame the frame being planned
     * @param siteEnemies enemy mobile ground combat units last known at a base's site, read only
     *     for a base that has lost a builder
     * @return the candidates neither held nor still contested after a loss
     */
    public Set<B> openBases(Set<B> candidates, int currentFrame, ToIntFunction<B> siteEnemies) {
        Set<B> open = new HashSet<>();
        for (B base : candidates) {
            boolean lostBuilder = hasLostBuilder(base);
            int enemies = lostBuilder ? siteEnemies.applyAsInt(base) : 0;
            if (isOpen(isHeld(base, currentFrame), lostBuilder, enemies)) {
                open.add(base);
            }
        }
        return open;
    }

    /**
     * Whether a base may be offered a colony pair this frame. A held base may not. A base that
     * has lost a builder may not while enemies stand at its site either: the pair would reserve
     * its minerals and then wait on the dispatch gate, which is the reservation the hold exists
     * to free.
     *
     * @param held whether the base's hold has not lifted
     * @param lostBuilder whether the base has lost a builder since its last reset
     * @param siteEnemies enemy mobile ground combat units last known at the base's site
     */
    static boolean isOpen(boolean held, boolean lostBuilder, int siteEnemies) {
        return !held && (!lostBuilder || siteEnemies == 0);
    }

    /**
     * @param losses builders lost at a base since its last reset
     * @return the frames a base is held after its latest loss
     */
    static int hold(int losses) {
        return COLONY_BUILDER_BACKOFF_FRAMES * Math.min(losses, MAX_COLONY_BACKOFF_STEPS);
    }
}
