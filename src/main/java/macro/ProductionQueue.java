package macro;

import bwapi.UnitType;
import macro.plan.Plan;
import macro.plan.PlanCancelSource;
import macro.plan.PlanComparator;
import macro.plan.PlanType;
import telemetry.PlanEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ProductionQueue implements Iterable<Plan> {

    /**
     * How long a plan may wait in PLANNED before production stops counting it as demand: two
     * minutes of game time.
     */
    public static final int STALE_PLANNED_FRAMES = 24 * 120;

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

    @Override
    public Iterator<Plan> iterator() {
        return queue.iterator();
    }

    public int unitPlanCount(UnitType unitType) {
        int count = 0;
        for (Plan plan : queue) {
            if (plan.getType() == PlanType.UNIT && plan.getPlannedUnit() == unitType) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * Counts building plans of one type still waiting to be scheduled.
     */
    public int buildingPlanCount(UnitType unitType) {
        int count = 0;
        for (Plan plan : queue) {
            if (plan.getType() == PlanType.BUILDING && plan.getPlannedUnit() == unitType) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * Sums the gas price of the queued plans production is still attempting. A queued plan has not
     * reserved yet, so this gas is invisible to the unreserved bank.
     *
     * <p>A plan that has waited in PLANNED longer than {@link #STALE_PLANNED_FRAMES} is not
     * counted: an upgrade that sits queued for thousands of frames would otherwise hold drones on
     * a floating geyser for all of them. The first gas-priced plan in queue order always counts,
     * however long it has waited, so a cut never strands the plan production reaches next.
     *
     * @param frame the current frame
     * @return the gas priced by queued plans that are still being attempted
     */
    public int gasDemand(int frame) {
        PlanComparator order = new PlanComparator();
        Plan head = null;
        int gas = 0;
        for (Plan plan : queue) {
            if (plan.gasPrice() <= 0) {
                continue;
            }
            if (head == null || order.compare(plan, head) < 0) {
                head = plan;
            }
            if (!isStale(plan, frame)) {
                gas += plan.gasPrice();
            }
        }
        if (head != null && isStale(head, frame)) {
            gas += head.gasPrice();
        }
        return gas;
    }

    /** True when a plan has waited in PLANNED longer than {@link #STALE_PLANNED_FRAMES}. */
    public static boolean isStale(Plan plan, int frame) {
        return plan.plannedFrames(frame) > STALE_PLANNED_FRAMES;
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

    public void removeWhere(Predicate<Plan> predicate, PlanCancelSource source, Consumer<Plan> onRemoved) {
        List<Plan> matched = new ArrayList<>();
        for (Plan plan : queue) {
            if (predicate.test(plan)) {
                matched.add(plan);
            }
        }
        for (Plan plan : matched) {
            queue.remove(plan);
            plan.setCancelSource(source);
            onRemoved.accept(plan);
        }
    }
}
