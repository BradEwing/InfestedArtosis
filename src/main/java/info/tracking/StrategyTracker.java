package info.tracking;

import bwapi.Game;
import bwapi.Race;
import bwem.BWMap;
import info.BaseData;
import info.map.GameMap;
import info.tracking.any.EarlyRush;
import info.tracking.any.OneBase;
import info.tracking.protoss.CannonRush;
import info.tracking.protoss.FFE;
import info.tracking.protoss.OneGateCore;
import info.tracking.protoss.TwoGate;
import info.tracking.terran.SCVRush;
import info.tracking.terran.TwoRaxAcademy;
import info.tracking.zerg.Hydralisk;
import info.tracking.zerg.TwoHatchLing;
import lombok.Getter;
import util.Time;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyTracker {

    private static final Map<String, String> IMPLIED_STRATEGIES = new LinkedHashMap<>();

    static {
        IMPLIED_STRATEGIES.put("2Gate", "EarlyRush");
        IMPLIED_STRATEGIES.put("2HatchLing", "EarlyRush");
    }

    @Getter
    private Set<ObservedStrategy> detectedStrategies = new HashSet<>();
    private Set<ObservedStrategy> possibleStrategies = new HashSet<>();
    private final Game game;
    private final ObservedUnitTracker tracker;
    private final BaseData baseData;
    private final GameMap gameMap;
    private final BWMap bwMap;

    public StrategyTracker(Game game, Race opponentRace, ObservedUnitTracker tracker, BaseData baseData, GameMap gameMap, BWMap bwMap) {
        this.game = game;
        this.tracker = tracker;
        this.baseData = baseData;
        this.gameMap = gameMap;
        this.bwMap = bwMap;
        this.init(opponentRace);
    }

    private void init(Race race) {
        possibleStrategies.add(new OneBase());
        possibleStrategies.add(new EarlyRush());
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
            possibleStrategies.add(new TwoHatchLing());
        }
    }

    public void updateRace(Race opponentRace) {
        possibleStrategies = possibleStrategies.stream()
                .filter(s -> s.getRace() == opponentRace || s.getRace() == Race.Unknown)
                .collect(Collectors.toSet());
    }

    public void onFrame() {
        Time currentTime = new Time(game.getFrameCount());
        StrategyDetectionContext context = new StrategyDetectionContext(tracker, currentTime, baseData, gameMap, bwMap);

        Set<ObservedStrategy> newlyDetected = new HashSet<>();
        for (ObservedStrategy strategy : possibleStrategies) {
            if (strategy.isDetected(context)) {
                newlyDetected.add(strategy);
            }
        }

        detectedStrategies.addAll(newlyDetected);
        possibleStrategies.removeAll(newlyDetected);

        applyStrategyImplications();
    }

    /**
     * Promotes the strategy each detected strategy implies. 2Gate and 2HatchLing are both strictly more
     * reliable signals of an early rush than EarlyRush's own evidence, which stops looking at ARRIVAL_DEADLINE
     * and so misses rushes that land later.
     */
    void applyStrategyImplications() {
        for (Map.Entry<String, String> implication : IMPLIED_STRATEGIES.entrySet()) {
            if (isDetectedStrategy(implication.getKey())) {
                promote(implication.getValue());
            }
        }
    }

    /**
     * Moves the possible strategy with the given name into the detected set. The existing instance is
     * promoted rather than a new one constructed, because ObservedStrategy defines no equals/hashCode: a fresh
     * instance would be a distinct member of the identity-based set and getDetectedStrategiesAsString() would
     * emit the name twice.
     */
    private void promote(String strategyName) {
        if (isDetectedStrategy(strategyName)) {
            return;
        }

        ObservedStrategy promoted = null;
        for (ObservedStrategy strategy : possibleStrategies) {
            if (strategy.getName().equals(strategyName)) {
                promoted = strategy;
                break;
            }
        }

        if (promoted != null) {
            detectedStrategies.add(promoted);
            possibleStrategies.remove(promoted);
        }
    }

    boolean isPossibleStrategy(String strategyName) {
        return possibleStrategies.stream().anyMatch(s -> s.getName().equals(strategyName));
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
