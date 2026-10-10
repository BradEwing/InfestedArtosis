package info.tracking;

import bwapi.Game;
import bwapi.Race;
import bwapi.UnitType;
import bwem.BWMap;
import info.BaseData;
import info.ScoutData;
import info.map.GameMap;
import info.tracking.any.EarlyRush;
import info.tracking.any.OneBase;
import info.tracking.protoss.CannonRush;
import info.tracking.protoss.FFE;
import info.tracking.protoss.OneGateCore;
import info.tracking.protoss.ProxyGate;
import info.tracking.protoss.TwoGate;
import info.tracking.terran.BunkerMain;
import info.tracking.terran.BunkerNatural;
import info.tracking.terran.SCVRush;
import info.tracking.terran.TerranMech;
import info.tracking.terran.TerranWall;
import info.tracking.terran.TerranWallMain;
import info.tracking.terran.TerranWallNatural;
import info.tracking.terran.TwoRaxAcademy;
import info.tracking.zerg.Hydralisk;
import info.tracking.zerg.NinePoolMainHatch;
import info.tracking.zerg.TwoHatchLing;
import info.tracking.zerg.ZergOpener;
import info.tracking.zerg.ZergOpenerReading;
import info.tracking.zerg.ZergOpenerRecognizer;
import lombok.Getter;
import lombok.Setter;
import telemetry.PlanEvents;
import util.Time;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StrategyTracker {

    private static final Map<String, String> IMPLIED_STRATEGIES = new LinkedHashMap<>();

    private static final Map<String, String> SUPERSEDED_STRATEGIES = new LinkedHashMap<>();

    static {
        IMPLIED_STRATEGIES.put("2Gate", "EarlyRush");
        IMPLIED_STRATEGIES.put("2HatchLing", "EarlyRush");
        IMPLIED_STRATEGIES.put(ProxyGate.NAME, "EarlyRush");

        SUPERSEDED_STRATEGIES.put(ProxyGate.NAME, "2Gate");
    }

    @Getter
    private Set<ObservedStrategy> detectedStrategies = new HashSet<>();
    private Set<ObservedStrategy> possibleStrategies = new HashSet<>();
    private final Game game;
    private final ObservedUnitTracker tracker;
    private final BaseData baseData;
    private final GameMap gameMap;
    private final BWMap bwMap;
    private final ScoutData scoutData;

    /**
     * Whether the learning file shows a Terran wall persisting across recent games against this opponent, as
     * {@link TerranWall#isPersistent} reads it.
     */
    @Setter
    private boolean terranWallPersists;

    /**
     * Whether the learning file shows Terran mech persisting across recent games against this opponent, as
     * {@link TerranMech#isPersistent} reads it.
     */
    @Setter
    private boolean terranMechPersists;

    private final BunkerNatural bunkerNatural = new BunkerNatural();
    private final BunkerMain bunkerMain = new BunkerMain();

    /**
     * Whether a Bunker is alive or last seen alive at the enemy natural, re-read every frame once BunkerNatural has
     * been detected.
     */
    @Getter
    private boolean bunkerNaturalHeld;

    /**
     * Whether a Bunker is alive or last seen alive at the enemy main, re-read every frame once BunkerMain has been
     * detected.
     */
    @Getter
    private boolean bunkerMainHeld;

    /**
     * Whether an enemy Bunker that was seen completed has been seen destroyed.
     */
    @Getter
    private boolean bunkerBroken;

    public StrategyTracker(Game game, Race opponentRace, ObservedUnitTracker tracker, BaseData baseData, GameMap gameMap,
                           BWMap bwMap, ScoutData scoutData) {
        this.game = game;
        this.tracker = tracker;
        this.baseData = baseData;
        this.gameMap = gameMap;
        this.bwMap = bwMap;
        this.scoutData = scoutData;
        this.init(opponentRace);
    }

    private void init(Race race) {
        possibleStrategies.add(new OneBase());
        possibleStrategies.add(new EarlyRush());
        if (race == Race.Protoss || race == Race.Unknown) {
            possibleStrategies.add(new FFE());
            possibleStrategies.add(new OneGateCore());
            possibleStrategies.add(new TwoGate());
            possibleStrategies.add(new ProxyGate());
            possibleStrategies.add(new CannonRush());
        }
        if (race == Race.Terran || race == Race.Unknown) {
            possibleStrategies.add(new TwoRaxAcademy());
            possibleStrategies.add(new TerranMech());
            possibleStrategies.add(new SCVRush());
            possibleStrategies.add(new TerranWallNatural());
            possibleStrategies.add(new TerranWallMain());
            possibleStrategies.add(bunkerNatural);
            possibleStrategies.add(bunkerMain);
        }
        if (race == Race.Zerg || race == Race.Unknown) {
            possibleStrategies.add(new Hydralisk());
            possibleStrategies.add(new TwoHatchLing());
            ZergOpenerReading openerReading = new ZergOpenerReading();
            for (ZergOpener opener : ZergOpener.values()) {
                possibleStrategies.add(new ZergOpenerRecognizer(opener, openerReading));
            }
            possibleStrategies.add(new NinePoolMainHatch());
        }
    }

    public void updateRace(Race opponentRace) {
        possibleStrategies = possibleStrategies.stream()
                .filter(s -> s.getRace() == opponentRace || s.getRace() == Race.Unknown)
                .collect(Collectors.toSet());
    }

    public void onFrame() {
        Time currentTime = new Time(game.getFrameCount());
        StrategyDetectionContext context = new StrategyDetectionContext(tracker, currentTime, baseData, gameMap, bwMap,
                scoutData);

        Set<ObservedStrategy> newlyDetected = new HashSet<>();
        for (ObservedStrategy strategy : possibleStrategies) {
            if (strategy.isDetected(context)) {
                newlyDetected.add(strategy);
            }
        }

        recordDetections(newlyDetected);
        applyBunkerHolds(isDetectedStrategy(BunkerNatural.NAME) && bunkerNatural.isDetected(context),
                isDetectedStrategy(BunkerMain.NAME) && bunkerMain.isDetected(context));
    }

    /**
     * Sets the Bunker holds and whether a Bunker has been broken.
     *
     * @param natural whether a Bunker holds the enemy natural
     * @param main whether a Bunker holds the enemy main
     */
    public void applyBunkerHolds(boolean natural, boolean main) {
        bunkerNaturalHeld = natural;
        bunkerMainHeld = main;
        bunkerBroken = tracker.getCountOfDestroyedCompletedUnits(UnitType.Terran_Bunker) > 0;
    }

    /**
     * Whether a Bunker holds the enemy natural or the enemy main, so a melee squad cannot answer it from range.
     */
    public boolean isBunkerHeld() {
        return bunkerNaturalHeld || bunkerMainHeld;
    }

    /**
     * Moves this frame's detections into the detected set, then resolves supersessions before
     * implications, so a superseded strategy detected on the same frame never reaches the detected set
     * alongside the strategy that supersedes it. Emits one STRATEGY_DETECTED telemetry row, carrying the
     * strategy's detection label, per strategy that is detected at the end of the frame and was not at its
     * start.
     */
    void recordDetections(Set<ObservedStrategy> newlyDetected) {
        Set<ObservedStrategy> detectedBefore = new HashSet<>(detectedStrategies);

        detectedStrategies.addAll(newlyDetected);
        possibleStrategies.removeAll(newlyDetected);

        applyStrategySupersessions();
        applyStrategyImplications();

        for (ObservedStrategy strategy : detectedStrategies) {
            if (!detectedBefore.contains(strategy)) {
                PlanEvents.strategyDetected(strategy.getDetectionLabel());
            }
        }
    }

    /**
     * Retires every strategy a detected strategy supersedes, from the detected and the possible sets, so it
     * is neither reported nor detected again for the rest of the game. ProxyGate supersedes 2Gate: 2Gate reads
     * Zealot and Gateway volume, and ProxyGate is the more specific reading of where the Gateways were built.
     */
    void applyStrategySupersessions() {
        for (Map.Entry<String, String> supersession : SUPERSEDED_STRATEGIES.entrySet()) {
            if (isDetectedStrategy(supersession.getKey())) {
                String superseded = supersession.getValue();
                detectedStrategies.removeIf(s -> s.getName().equals(superseded));
                possibleStrategies.removeIf(s -> s.getName().equals(superseded));
            }
        }
    }

    /**
     * Promotes the strategy each detected strategy implies. 2Gate, ProxyGate and 2HatchLing are all strictly
     * more reliable signals of an early rush than EarlyRush's own evidence, which stops looking at
     * ARRIVAL_DEADLINE and so misses rushes that land later.
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

    public boolean isAnyDetectedStrategy(String... strategyNames) {
        for (String strategyName : strategyNames) {
            if (isDetectedStrategy(strategyName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a Terran wall was detected this game or persists across recent games against this opponent.
     */
    public boolean isTerranWallDetected() {
        return isAnyDetectedStrategy(TerranWallNatural.NAME, TerranWallMain.NAME) || terranWallPersists;
    }

    /**
     * Whether Terran mech was detected this game or persists across recent games against this opponent.
     */
    public boolean isTerranMechKnown() {
        return isDetectedStrategy(TerranMech.NAME) || terranMechPersists;
    }

    /**
     * Whether the learning file shows Terran mech persisting across recent games against this opponent,
     * whatever this game has detected.
     */
    public boolean isTerranMechPersistent() {
        return terranMechPersists;
    }

    /**
     * Writes one STRATEGY_DETECTED row labelled {@link TerranMech#PRIOR_LABEL} when the game started on the
     * persisted mech prior, so the prior that gated the build offer is on the record.
     */
    public void reportTerranMechPrior() {
        if (terranMechPersists) {
            PlanEvents.strategyDetected(TerranMech.PRIOR_LABEL);
        }
    }

    public String getDetectedStrategiesAsString() {
        return detectedStrategies.stream()
                .map(ObservedStrategy::getName)
                .collect(Collectors.joining(";"));
    }
}
