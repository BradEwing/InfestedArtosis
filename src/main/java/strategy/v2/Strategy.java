package strategy.v2;

import bwapi.Race;
import info.GameState;
import macro.plan.Plan;
import util.Time;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class Strategy {
    private final String name;
    protected Time activatedAt;

    protected Strategy(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean shouldTransition(GameState gameState) {
        return false;
    }

    public Set<Strategy> transition(GameState gameState) {
        return new HashSet<>();
    }

    public abstract List<Plan> plan(GameState gameState);

    public abstract boolean playsRace(Race race);
}
