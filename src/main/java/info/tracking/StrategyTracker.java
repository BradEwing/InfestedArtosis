package info.tracking;

import bwapi.Game;
import bwapi.Race;
import info.tracking.protoss.FFE;
import info.tracking.protoss.OneGateCore;
import info.tracking.protoss.TwoGate;
import info.tracking.terran.TwoRaxAcademy;
import lombok.Getter;
import util.Time;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyTracker {

    @Getter
    private Set<ObservedStrategy> detectedStrategies = new HashSet<>();
    private Set<ObservedStrategy> possibleStrategies = new HashSet<>();
    private Set<ObservedStrategy> discardedStrategies = new HashSet<>();

    private final Game game;
    private final ObservedUnitTracker tracker;

    public StrategyTracker(Game game, Race opponentRace, ObservedUnitTracker tracker) {
        this.game = game;
        this.tracker = tracker;
        this.init(opponentRace);
    }

    private void init(Race race) {
        if (race == Race.Protoss || race == Race.Unknown) {
            possibleStrategies.add(new FFE());
            possibleStrategies.add(new OneGateCore());
            possibleStrategies.add(new TwoGate());
        }
        if (race == Race.Terran || race == Race.Unknown) {
            possibleStrategies.add(new TwoRaxAcademy());
        }
    }

    public void updateRace(Race opponentRace) {
        possibleStrategies = possibleStrategies.stream()
                .filter(s -> s.getRace() == opponentRace)
                .collect(Collectors.toSet());
    }

    public void onFrame() {
        Time currentTime = new Time(game.getFrameCount());

        Set<ObservedStrategy> newlyDetected = new HashSet<>();
        for (ObservedStrategy strategy : possibleStrategies) {
            if (strategy.isDetected(tracker, currentTime)) {
                newlyDetected.add(strategy);
            }
        }

        detectedStrategies.addAll(newlyDetected);
        possibleStrategies.removeAll(newlyDetected);

        Set<ObservedStrategy> newlyDiscarded = new HashSet<>();
        for (ObservedStrategy detected : detectedStrategies) {
            for (ObservedStrategy possible : possibleStrategies) {
                if (!detected.isCompatibleStrategy(possible)) {
                    newlyDiscarded.add(possible);
                }
            }
        }

        possibleStrategies.removeAll(newlyDiscarded);
        discardedStrategies.addAll(newlyDiscarded);
    }

    public boolean isDetectedStrategy(String strategyName) {
        for (ObservedStrategy strategy : detectedStrategies) {
            if (strategy.getName().equals(strategyName)) {
                return true;
            }
        }
        return false;
    }
}
