package strategy;

import strategy.openers.Opener;
import strategy.strategies.Default;
import strategy.strategies.Hydra;
import strategy.strategies.LingFlood;
import strategy.strategies.Strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyFactory {

    private List<Strategy> allStrategies = new ArrayList<>();
    private Map<String, Strategy> lookup = new HashMap();

    public StrategyFactory() {
        initStrategies();
    }

    public Strategy getByName(String name) {
        return lookup.get(name);
    }

    public List<String> listAllOpenerNames() {
        return allStrategies.stream().map(s -> s.getName()).collect(Collectors.toList());
    }

    /**
     * Get strategies that can be played for this opener.
     *
     * @param opener
     * @return
     */
    public Set<String> getPlayableStrategies(Opener opener) {
        List<Strategy> playableStrategies = lookup.values().stream().filter(s -> s.playsOpener(opener)).collect(Collectors.toList());
        return playableStrategies.stream().map(s -> s.getName()).collect(Collectors.toSet());
    }

    private void initStrategies() {
        allStrategies.add(new Default());
        allStrategies.add(new Hydra());
        allStrategies.add(new LingFlood());

        for (Strategy strategy: allStrategies) {
            lookup.put(strategy.getName(), strategy);
        }
    }
}