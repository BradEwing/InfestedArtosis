package macro.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The builders walking to their sites, each with the plan it was dispatched for and a fresh
 * {@link BuilderStray}. A builder is dispatched to one plan at a time, and a builder dispatched
 * again starts a new stray history.
 *
 * @param <B> the builder type
 */
public class DispatchedBuilders<B> {
    private final Map<B, Plan> plans = new HashMap<>();
    private final Map<B, BuilderStray> strays = new HashMap<>();

    public void dispatch(B builder, Plan plan) {
        plans.put(builder, plan);
        strays.put(builder, new BuilderStray());
    }

    public void undispatch(B builder) {
        plans.remove(builder);
        strays.remove(builder);
    }

    public Plan planOf(B builder) {
        return plans.get(builder);
    }

    public BuilderStray strayOf(B builder) {
        return strays.get(builder);
    }

    /** A copy of the builder to plan pairs, safe to iterate while dispatching and undispatching. */
    public List<Map.Entry<B, Plan>> snapshot() {
        return new ArrayList<>(plans.entrySet());
    }
}
