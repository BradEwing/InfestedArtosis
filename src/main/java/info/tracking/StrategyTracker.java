package info.tracking;

import bwapi.Game;
import bwapi.Race;
import info.BaseData;
import info.map.GameMap;
import info.tracking.any.OneBase;
import info.tracking.protoss.CannonRush;
import info.tracking.protoss.FFE;
import info.tracking.protoss.OneGateCore;
import info.tracking.protoss.TwoGate;
import info.tracking.terran.SCVRush;
import info.tracking.terran.TwoRaxAcademy;
import info.tracking.zerg.Hydralisk;
import lombok.Getter;
import util.Time;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyTracker {

    @Getter
    private Set<ObservedStrategy> detectedStrategies = new HashSet<>();
    private Set<ObservedStrategy> possibleStrategies = new HashSet<>();
    private final Game game;
    private final ObservedUnitTracker tracker;
    private final BaseData baseData;
    private final GameMap gameMap;

    public StrategyTracker(Game game, Race opponentRace, ObservedUnitTracker tracker, BaseData baseData, GameMap gameMap) {
        this.game = game;
        this.tracker = tracker;
        this.baseData = baseData;
        this.gameMap = gameMap;
        this.init(opponentRace);
    }

    private void init(Race race) {
        possibleStrategies.add(new OneBase());
        if (race == Race.Protoss || race == Race.Unknown) {
            possibleStrategies.add(new FFE());
            possibleStrategies.add(new OneGateCore());
            possibleStrategies.add(new TwoGate());
            possibleStrategies.add(new CannonRush());
        }
        if (race == Race.Terran || race == Race.Unknown) {
            possibleStrategies.add(new TwoRaxAcademy());
            possibleStrategies.add(new SCVRush());
        }
        if (race == Race.Zerg || race == Race.Unknown) {
            possibleStrategies.add(new Hydralisk());
        }
    }

    public void updateRace(Race opponentRace) {
        possibleStrategies = possibleStrategies.stream()
                .filter(s -> s.getRace() == opponentRace || s.getRace() == Race.Unknown)
                .collect(Collectors.toSet());
    }

    public void onFrame() {
        Time currentTime = new Time(game.getFrameCount());
        StrategyDetectionContext context = new StrategyDetectionContext(tracker, currentTime, baseData, gameMap);

        Set<ObservedStrategy> newlyDetected = new HashSet<>();
        for (ObservedStrategy strategy : possibleStrategies) {
            if (strategy.isDetected(context)) {
                newlyDetected.add(strategy);
            }
        }

        detectedStrategies.addAll(newlyDetected);
        possibleStrategies.removeAll(newlyDetected);

    }

    public boolean isDetectedStrategy(String strategyName) {
        for (ObservedStrategy strategy : detectedStrategies) {
            if (strategy.getName().equals(strategyName)) {
                return true;
            }
        }
        return false;
    }

    public String getDetectedStrategiesAsString() {
        return detectedStrategies.stream()
                .map(ObservedStrategy::getName)
                .collect(Collectors.joining(";"));
    }
}
