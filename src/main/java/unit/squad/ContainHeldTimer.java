package unit.squad;

/**
 * How long our ground squads have held a contain, as one union across every squad.
 *
 * <p>A chain starts on the first frame any ground squad is in {@link SquadStatus#CONTAIN} and lasts while at
 * least one is. A frame with none containing does not end the chain until more than {@link #REENTRY_GAP_FRAMES}
 * frames have passed since a squad last contained, so a squad that times out of its contain and re-enters it
 * straight away, or a merge that briefly leaves no squad in CONTAIN, keeps the chain and its start frame.
 *
 * <p>A containing squad sent back by the enemy (attrition, an outranged arc, or a base under attack) breaks the
 * chain at once, whether or not another squad still contains; a squad still in CONTAIN starts a new chain on
 * the next update.
 */
public class ContainHeldTimer {

    /** Frames a chain must last before the contain counts as held: 20 seconds. */
    public static final int HELD_FRAMES = 480;

    /**
     * Longest run of frames with no ground squad in CONTAIN that the chain bridges: 2 seconds, which covers a
     * squad that times out of its contain and re-enters it.
     */
    public static final int REENTRY_GAP_FRAMES = 48;

    /** The value of {@link #getChainStartFrame()} while no chain is running. */
    public static final int NO_CHAIN = -1;

    private int chainStartFrame = NO_CHAIN;

    private int lastContainFrame = NO_CHAIN;

    /**
     * Extends, bridges or ends the chain for this frame.
     *
     * @param frame the current frame
     * @param containing whether any ground squad is in CONTAIN this frame
     */
    public void update(int frame, boolean containing) {
        if (containing) {
            if (chainStartFrame == NO_CHAIN) {
                chainStartFrame = frame;
            }
            lastContainFrame = frame;
            return;
        }
        if (chainStartFrame != NO_CHAIN && frame - lastContainFrame > REENTRY_GAP_FRAMES) {
            reset();
        }
    }

    /**
     * Ends the chain because the enemy sent a containing squad back.
     */
    public void broken() {
        reset();
    }

    /**
     * @return the frame the running chain started on, or {@link #NO_CHAIN}
     */
    public int getChainStartFrame() {
        return chainStartFrame;
    }

    /**
     * @param frame the current frame
     * @return frames since the running chain started, or zero with no chain
     */
    public int heldFrames(int frame) {
        return chainStartFrame == NO_CHAIN ? 0 : frame - chainStartFrame;
    }

    /**
     * @param frame the current frame
     * @return true once the running chain has lasted {@link #HELD_FRAMES}
     */
    public boolean isHeld(int frame) {
        return chainStartFrame != NO_CHAIN && heldFrames(frame) >= HELD_FRAMES;
    }

    private void reset() {
        chainStartFrame = NO_CHAIN;
        lastContainFrame = NO_CHAIN;
    }
}
