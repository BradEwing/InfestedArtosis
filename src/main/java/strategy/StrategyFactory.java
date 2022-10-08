package strategy;

import bwapi.Game;
import strategy.strategies.FivePool;
import strategy.strategies.FourPool;
import strategy.strategies.NinePoolSpeed;
import strategy.strategies.OverPool;
import strategy.strategies.TwelveHatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyFactory {
    private static int FOUR = 4;

    private List<Strategy> allStrategies = new ArrayList<>();
    private Map<String, Strategy> playableStrategies = new HashMap<>();

    public StrategyFactory(int numStartingLocations) {
        initStrategies(numStartingLocations);
    }

    public Strategy getByName(String name) {
        return playableStrategies.get(name);
    }

    public List<String> listAllStrategyNames() {
        return allStrategies.stream().map(s -> s.getName()).collect(Collectors.toList());
    }

    public Set<String> getPlayableStrategies() {
        return playableStrategies.values().stream().map(s -> s.getName()).collect(Collectors.toSet());
    }

    private void initStrategies(int numStartingLocations) {
        allStrategies.add(new NinePoolSpeed());
        allStrategies.add(new TwelveHatch());
        allStrategies.add(new OverPool());
        allStrategies.add(new FivePool());
        allStrategies.add(new FourPool());

        for (Strategy strategy: allStrategies) {
            if (numStartingLocations == FOUR && !strategy.playsFourPlayerMap()) {
                continue;
            }
            playableStrategies.put(strategy.getName(), strategy);
        }
    }
}
