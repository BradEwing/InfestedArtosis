package unit.squad;

import lombok.Getter;

/**
 * The run of collapse tests a containing squad has passed toward the hysteresis gate, see
 * {@link ContainmentCollapse#gate}.
 *
 * <p>A test passed outside the cooldown extends the run. A LOCK_REFUSED test holds it: that refusal reads the squad's
 * fight lock, not the enemies in the sector. A STATIC_COVERED test holds it too until the enemy centroid has been
 * covered on {@link #STATIC_COVERED_DEBOUNCE} consecutive tests, so a centroid flickering across the edge of a zone
 * does not start the run over. Any other test, a missing one or a pass inside the cooldown starts it over.
 */
final class CollapseEntryRun {

    /** Tuning value: consecutive STATIC_COVERED tests that start the run over. */
    static final int STATIC_COVERED_DEBOUNCE = 2;
    /** Frame a run reports when no run is under way. */
    static final int NO_RUN = -1;

    @Getter
    private int passes = 0;
    @Getter
    private int startFrame = NO_RUN;
    private int coveredTests = 0;

    CollapseEntryRun() {
    }

    CollapseEntryRun(CollapseEntryRun source) {
        this.passes = source.passes;
        this.startFrame = source.startFrame;
        this.coveredTests = source.coveredTests;
    }

    /**
     * Records one collapse test.
     *
     * @param outcome the ungated outcome of the test, or null when no test ran or no armed enemy stood in the sector
     * @param coolingDown true while the squad is collapsing or inside its cooldown, see {@link Squad#isCollapseLocked}
     * @param frame frame of the test
     * @return passes in the run, this test included
     */
    int record(ContainmentCollapse.Outcome outcome, boolean coolingDown, int frame) {
        if (outcome == ContainmentCollapse.Outcome.COLLAPSE && !coolingDown) {
            if (passes == 0) {
                startFrame = frame;
            }
            passes++;
            coveredTests = 0;
            return passes;
        }
        if (outcome == ContainmentCollapse.Outcome.LOCK_REFUSED) {
            coveredTests = 0;
            return passes;
        }
        if (outcome == ContainmentCollapse.Outcome.STATIC_COVERED) {
            coveredTests++;
            if (coveredTests < STATIC_COVERED_DEBOUNCE) {
                return passes;
            }
        }
        clear();
        return passes;
    }

    /**
     * Starts the run over, as a new or ended contain episode does.
     */
    void clear() {
        passes = 0;
        startFrame = NO_RUN;
        coveredTests = 0;
    }
}
