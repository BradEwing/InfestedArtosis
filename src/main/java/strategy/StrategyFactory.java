package strategy;

import strategy.strategies.NinePoolSpeed;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StrategyFactory {

    private Map<String, Strategy> strategyMap = new HashMap<>();

    public StrategyFactory() {
        initStrategies();
    }

    public Strategy getByName(String name) {
        return strategyMap.get(name);
    }

    public List<Strategy> listAll() {
        return strategyMap.values().stream().collect(Collectors.toList());
    }

    private void initStrategies() {
        Strategy ninePoolSpeed = new NinePoolSpeed();
        strategyMap.put(ninePoolSpeed.getName(), ninePoolSpeed);
    }
}
