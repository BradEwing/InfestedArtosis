package strategy.v2;

import bwapi.Race;
import info.GameState;
import macro.plan.Plan;
import util.Time;

import java.util.Collections;
import java.util.List;

/**
 * This is a transitory class that captures old production planning logic from the ProductionManager.
 * It will be supplanted with the broader implementation of v2.Strategy.
 */
public class Legacy extends Strategy {
    protected Legacy() {
        super("Legacy");
        this.activatedAt = new Time(0);
    }

    @Override
    public List<Plan> plan(GameState gameState) {
        return Collections.emptyList();
    }

    @Override
    public boolean playsRace(Race race) {
        return true;
    }
}
