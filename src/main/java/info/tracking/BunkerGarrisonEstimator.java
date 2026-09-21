package info.tracking;

import java.util.ArrayDeque;

/**
 * Estimates how many Marines an enemy Bunker holds from the shots it fires.
 *
 * <p>A loaded Marine fires once per weapon cooldown, so the Marines in a Bunker equal the new Gauss
 * Rifle shots attributed to it over one cooldown window, not the shots in flight on any single frame.
 * A firing episode opens on the first shot after a silence of more than two cooldowns. For the first
 * full window of an episode the estimate can only rise, so a Bunker is never read as lightly held while
 * its first volley is still arriving; once the window has closed the estimate is the largest window count
 * the episode produced, which lets a Bunker that lost or unloaded Marines read lower in its next episode.
 *
 * <p>Silence alone is no evidence, because a Bunker only fires with a target in range. Silence while one
 * of our combat units sits inside a Marine's range for {@code emptyEvidenceFrames} is: the Bunker is
 * recorded as empty. The estimate is -1 until the Bunker has either fired or been proven empty.
 */
public class BunkerGarrisonEstimator {
    private static final int MAX_GARRISON = 4;

    private final int cooldown;
    private final int emptyEvidenceFrames;
    private final ArrayDeque<Integer> shotFrames = new ArrayDeque<>();

    private int estimate = -1;
    private int lastShotFrame = -1;
    private int episodeStartFrame = -1;
    private int episodeMax;
    private boolean episodeCommitted = true;
    private int silentInRangeSince = -1;

    public BunkerGarrisonEstimator(int cooldown, int emptyEvidenceFrames) {
        this.cooldown = cooldown;
        this.emptyEvidenceFrames = emptyEvidenceFrames;
    }

    /**
     * Folds one frame of observation into the estimate.
     *
     * @param newShots Gauss Rifle shots first seen this frame and attributed to this Bunker
     * @param targetInRange whether one of our combat units was inside a Marine's range of the Bunker
     * @param frame current frame
     * @return the garrison estimate, -1 while unknown
     */
    public int observe(int newShots, boolean targetInRange, int frame) {
        while (!shotFrames.isEmpty() && shotFrames.peekFirst() <= frame - cooldown) {
            shotFrames.pollFirst();
        }
        if (newShots > 0) {
            recordShots(newShots, frame);
        } else {
            recordSilence(targetInRange, frame);
        }
        if (!episodeCommitted && frame - episodeStartFrame >= cooldown) {
            estimate = episodeMax;
            episodeCommitted = true;
        }
        return estimate;
    }

    public int getEstimate() {
        return estimate;
    }

    public int getLastShotFrame() {
        return lastShotFrame;
    }

    private void recordShots(int newShots, int frame) {
        if (lastShotFrame < 0 || frame - lastShotFrame > cooldown * 2) {
            episodeStartFrame = frame;
            episodeMax = 0;
            episodeCommitted = false;
        }
        for (int i = 0; i < newShots; i++) {
            shotFrames.addLast(frame);
        }
        episodeMax = Math.max(episodeMax, Math.min(shotFrames.size(), MAX_GARRISON));
        if (episodeCommitted || estimate >= 0 && episodeMax > estimate) {
            estimate = Math.max(estimate, episodeMax);
        }
        lastShotFrame = frame;
        silentInRangeSince = -1;
    }

    private void recordSilence(boolean targetInRange, int frame) {
        if (!targetInRange) {
            silentInRangeSince = -1;
            return;
        }
        if (silentInRangeSince < 0) {
            silentInRangeSince = frame;
        } else if (frame - silentInRangeSince >= emptyEvidenceFrames) {
            estimate = 0;
            episodeCommitted = true;
        }
    }
}
