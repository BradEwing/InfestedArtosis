package macro.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The builders walking to their sites, each with the plan it was dispatched for, a fresh
 * {@link BuilderStray} and its last {@link BuilderReading}. A builder is dispatched to one plan at a
 * time, and a builder dispatched again starts a new stray history.
 *
 * <p>The last reading outlives the builder's unit: a builder killed on its walk is reported as it
 * was on the last frame it was read.
 *
 * @param <B> the builder type
 */
public class DispatchedBuilders<B> {
    private final Map<B, Plan> plans = new HashMap<>();
    private final Map<B, BuilderStray> strays = new HashMap<>();
    private final Map<B, BuilderReading> readings = new HashMap<>();

    public void dispatch(B builder, Plan plan) {
        plans.put(builder, plan);
        strays.put(builder, new BuilderStray());
        readings.remove(builder);
    }

    public void undispatch(B builder) {
        plans.remove(builder);
        strays.remove(builder);
        readings.remove(builder);
    }

    public Plan planOf(B builder) {
        return plans.get(builder);
    }

    public BuilderStray strayOf(B builder) {
        return strays.get(builder);
    }

    /** Records a dispatched builder's reading for this frame. A builder not dispatched is ignored. */
    public void read(B builder, BuilderReading reading) {
        if (plans.containsKey(builder)) {
            readings.put(builder, reading);
        }
    }

    /** The builder's most recent reading, or null before it was first read. */
    public BuilderReading lastReadingOf(B builder) {
        return readings.get(builder);
    }

    /** A copy of the builder to plan pairs, safe to iterate while dispatching and undispatching. */
    public List<Map.Entry<B, Plan>> snapshot() {
        return new ArrayList<>(plans.entrySet());
    }
}
