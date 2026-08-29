package macro;

import macro.plan.Plan;
import macro.plan.PlanCancelSite;
import macro.plan.PlanComparator;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ProductionQueue implements Iterable<Plan> {

    private final PriorityQueue<Plan> queue = new PriorityQueue<>(new PlanComparator());

    public void add(Plan plan) {
        queue.add(plan);
        PlanEvents.enqueued(plan);
    }

    public void addAll(List<Plan> plans) {
        queue.addAll(plans);
        PlanEvents.enqueued(plans);
    }

    public Plan poll() {
        return queue.poll();
    }

    public void remove(Plan plan) {
        queue.remove(plan);
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    /**
     * Streams the queued plans. The queue is Iterable rather than a Collection, so callers that
     * want to aggregate over it cannot call stream() on it directly.
     */
    public Stream<Plan> stream() {
        return queue.stream();
    }

    @Override
    public Iterator<Plan> iterator() {
        return queue.iterator();
    }

    public int minPriority() {
        int min = Integer.MAX_VALUE;
        for (Plan plan : queue) {
            if (plan.getPriority() < min) {
                min = plan.getPriority();
            }
        }
        return min;
    }

    public List<Plan> toSortedList() {
        List<Plan> sorted = new ArrayList<>(queue);
        Collections.sort(sorted, new PlanComparator());
        return sorted;
    }

    /**
     * Sets the priority of all plans matching the predicate.
     * Removes and re-inserts each matched plan to preserve the heap invariant.
     */
    public void setPriorityWhere(Predicate<Plan> predicate, int priority) {
        List<Plan> matched = new ArrayList<>();
        for (Plan plan : queue) {
            if (predicate.test(plan)) {
                matched.add(plan);
            }
        }
        for (Plan plan : matched) {
            queue.remove(plan);
            plan.setPriority(priority);
            queue.add(plan);
        }
    }

    /**
     * Removes matching plans, records the cancellation site, and invokes the callback for each.
     *
     * @param predicate selects plans to remove
     * @param site call site responsible for the removal
     * @param onRemoved callback invoked after removing and stamping each plan
     */
    public void removeWhere(Predicate<Plan> predicate, PlanCancelSite site, Consumer<Plan> onRemoved) {
        List<Plan> matched = new ArrayList<>();
        for (Plan plan : queue) {
            if (predicate.test(plan)) {
                matched.add(plan);
            }
        }
        for (Plan plan : matched) {
            queue.remove(plan);
            plan.setCancelSite(site);
            onRemoved.accept(plan);
        }
    }
}
