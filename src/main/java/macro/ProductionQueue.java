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
