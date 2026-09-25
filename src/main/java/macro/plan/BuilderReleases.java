package macro.plan;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The builders each building plan has lost, which bounds how often a plan can churn through them.
 *
 * <p>A plan stops releasing builders as STRAYED after {@link #MAX_STRAYS_PER_PLAN} strays, and keeps
 * the builder it has from then on. A builder released from a plan, for any reason, is not assigned
 * to that plan again for {@link #RESELECT_BACKOFF_FRAMES}, so the drone that was just released is
 * not re-picked as the closest one.
 *
 * @param <B> the builder type
 */
public class BuilderReleases<B> {
    static final int MAX_STRAYS_PER_PLAN = 2;
    static final int RESELECT_BACKOFF_FRAMES = 480;

    private final Map<Plan, Integer> strays = new HashMap<>();
    private final Map<Plan, Map<B, Integer>> releaseFrames = new HashMap<>();

    /**
     * @param plan the plan the builder was released from
     * @param builder the released builder
     * @param reason why it was released
     * @param frame the frame of the release
     */
    public void record(Plan plan, B builder, BuilderLossReason reason, int frame) {
        if (reason == BuilderLossReason.STRAYED) {
            strays.merge(plan, 1, Integer::sum);
        }
        releaseFrames.computeIfAbsent(plan, p -> new HashMap<>()).put(builder, frame);
    }

    /** Whether the plan may still release a builder as STRAYED. */
    public boolean mayStray(Plan plan) {
        return strays.getOrDefault(plan, 0) < MAX_STRAYS_PER_PLAN;
    }

    /** Whether the builder was released from the plan within the last {@link #RESELECT_BACKOFF_FRAMES}. */
    public boolean isBackedOff(Plan plan, B builder, int frame) {
        Integer released = releaseFrames.getOrDefault(plan, Collections.emptyMap()).get(builder);
        return released != null && frame - released < RESELECT_BACKOFF_FRAMES;
    }

    /** Drops everything recorded for a plan that will not be built again. */
    public void forget(Plan plan) {
        strays.remove(plan);
        releaseFrames.remove(plan);
    }
}
