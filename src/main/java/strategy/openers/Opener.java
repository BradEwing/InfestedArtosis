package strategy.openers;

import plan.Plan;

import java.util.List;

public interface Opener {

    default String getNameString() {
        return this.getClass().getSimpleName();
    }

    OpenerName getName();

    List<Plan> getBuildOrder();

    boolean isAllIn();
}
