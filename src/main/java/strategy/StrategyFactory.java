package strategy;

import bwapi.Race;
import strategy.openers.Opener;
import strategy.strategies.Default;
import strategy.strategies.Hydra;
import strategy.strategies.LingFlood;
import strategy.strategies.Lurker;
import strategy.strategies.Mutalisk;
import strategy.strategies.Strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyFactory {

    private Race opponentRace;

    private List<Strategy> allStrategies = new ArrayList<>();
    private Map<String, Strategy> lookup = new HashMap();

    public StrategyFactory(Race opponentRace) {
        this.opponentRace = opponentRace;
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
        List<Strategy> playableStrategies = lookup.values().stream()
                .filter(s -> s.playsOpener(opener))
                .filter(s -> playsRace(s))
                .collect(Collectors.toList());
        return playableStrategies.stream().map(s -> s.getName()).collect(Collectors.toSet());
    }

    private void initStrategies() {
        allStrategies.add(new Default());
        allStrategies.add(new Hydra());
        allStrategies.add(new LingFlood());
        allStrategies.add(new Mutalisk());
        allStrategies.add(new Lurker());

        for (Strategy strategy: allStrategies) {
            lookup.put(strategy.getName(), strategy);
        }
    }

    private boolean playsRace(Strategy strategy) {
        switch(this.opponentRace) {
            case Zerg:
                return playsZerg(strategy);
            case Terran:
                return playsTerran(strategy);
            case Protoss:
                return playsProtoss(strategy);
            default:
                return playsRandom(strategy);
        }
    }

    private boolean playsZerg(Strategy strategy) {
        switch (strategy.getType()) {
            case LING_FLOOD:
            case MUTALISK:
                return true;
            default:
                return false;
        }
    }

    private boolean playsTerran(Strategy _) {
        return true;
    }

    private boolean playsProtoss(Strategy _) {
        return true;
    }

    private boolean playsRandom(Strategy _) {
        return true;
    }
}