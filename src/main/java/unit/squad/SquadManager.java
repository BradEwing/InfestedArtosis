package unit.squad;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwem.Base;
import bwem.CPPath;
import info.GameState;
import info.ScoutData;
import info.tracking.ObservedUnitTracker;
import info.tracking.PsiStormTracker;
import info.tracking.StrategyTracker;
import lombok.Getter;

import org.bk.ass.sim.BWMirrorAgentFactory;
import org.bk.ass.sim.Simulator;
import telemetry.SquadDecisions;
import telemetry.SquadLock;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;
import unit.managed.UnitRole;
import util.Arc;
import util.Filter;
import util.Vec2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import util.TargetScorer;

import static java.lang.Math.min;
import static util.Distance.closestPosition;
import static util.Distance.manhattanTileDistance;

public class SquadManager {

    private Game game;
    private GameState gameState;

    private BWMirrorAgentFactory agentFactory;
    private ContainmentEvaluator containmentEvaluator;

    private Squad overlords = new Squad();

    public HashSet<Squad> fightSquads = new HashSet<>();

    @Getter
    private HashMap<Base, Squad> defenseSquads = new HashMap<>();

    @Getter
    private List<Arc> activeContainmentArcs = new ArrayList<>();

    private HashSet<ManagedUnit> disbanded = new HashSet<>();
    private HashSet<ManagedUnit> irradiatedUnits = new HashSet<>();

    public static final double AIR_JOIN_DISTANCE = 128;
    public static final double SQUAD_MERGE_DISTANCE = 256.0;
    private static final double ENEMY_DETECTION_RADIUS = 512.0;

    private static final Set<UnitType> GROUND_SQUAD_TYPES = new HashSet<>();

    static {
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Zergling);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Hydralisk);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Lurker);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Ultralisk);
        GROUND_SQUAD_TYPES.add(UnitType.Zerg_Defiler);
    }

    private static final Set<UnitType> AIR_SQUAD_TYPES = new HashSet<>();

    static {
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Mutalisk);
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Scourge);
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Guardian);
        AIR_SQUAD_TYPES.add(UnitType.Zerg_Devourer);
    }

    private static final int RETREAT_VECTOR_MAGNITUDE = 192;
    private static final int COMBAT_SIM_DURATION_FRAMES = 150;
    private static final double DEFENSE_WIN_THRESHOLD = 0.50;
    private static final int MERGE_CHECK_INTERVAL = 50;
    private static final int DEFENSE_SIM_RANGE = 256;
    private static final int CONTAINMENT_REEVALUATE_INTERVAL = 48;
    private static final int MAX_MOVE_OUT_THRESHOLD = 40;
    private static final int CONTAINMENT_TIMEOUT_FRAMES = 1400;
    private static final int ARC_DEGREES = 90;
    private static final int ARC_RADIUS = 160;
    private static final double REINFORCEMENT_RADIUS = 384.0;
    private static final int TARGETING_RADIUS = 256;
    public static final int GROUND_SPLIT_DISTANCE = 256;
    public static final int AIR_SPLIT_DISTANCE = 768;
    private static final int COMMITMENT_RELEASE_DISTANCE = 512;

    public SquadManager(Game game, GameState gameState) {
        this.game = game;
        this.gameState = gameState;
        this.agentFactory = new BWMirrorAgentFactory();
        this.containmentEvaluator = new ContainmentEvaluator(gameState);
    }

    public void updateFightSquads() {
        disbanded.clear();
        activeContainmentArcs.clear();
        removeEmptySquads();
        mergeSquads();
        splitSquads();
        evictIrradiatedUnits();
        updateIrradiatedUnits();
        assignOverlordsToSquads();

        Set<Squad> removed = new HashSet<>();
        for (Squad fightSquad: fightSquads) {
            fightSquad.onFrame();
            if (fightSquad.shouldDisband()) {
                disbanded.addAll(disbandSquad(fightSquad));
                removed.add(fightSquad);
            }

            evaluateSquadRole(fightSquad);

            for (ManagedUnit mu : fightSquad.getMembers()) {
                if (mu.getUnitType() == UnitType.Zerg_Overlord) {
                    if (gameState.getTechProgression().isOverlordSpeed()) {
                        mu.setRallyPoint(fightSquad.getCenter());
                        mu.setRole(UnitRole.RALLY);
                    } else {
                        fightSquad.removeUnit(mu);
                        overlords.addUnit(mu);
                        mu.setRole(UnitRole.IDLE);
                    }
                }
            }
        }

        fightSquads.removeAll(removed);
    }

    public void updateOverlordSquad() {
        TilePosition mainBaseLocation = gameState.getBaseData().mainBasePosition();
        if (overlords.getRallyPoint() == null) {
            overlords.setRallyPoint(mainBaseLocation.toPosition());
        }

        for (ManagedUnit managedUnit: overlords.getMembers()) {
            if (managedUnit.getUnit().getDistance(mainBaseLocation.toPosition()) < 16) {
                managedUnit.setRole(UnitRole.IDLE);
                continue;
            }

            managedUnit.setRole(UnitRole.RALLY);
            managedUnit.setRallyPoint(mainBaseLocation.toPosition());
        }
    }



    public void updateDefenseSquads() {
        ensureDefenderSquadsHaveTargets();
    }

    private void evictIrradiatedUnits() {
        for (Squad squad : fightSquads) {
            evictIrradiatedFromSquad(squad);
        }
        for (Squad defenseSquad : defenseSquads.values()) {
            evictIrradiatedFromSquad(defenseSquad);
        }
    }

    private void evictIrradiatedFromSquad(Squad squad) {
        List<ManagedUnit> toEvict = new ArrayList<>();
        for (ManagedUnit mu : squad.getMembers()) {
            if (mu.isIrradiated() && !irradiatedUnits.contains(mu)) {
                toEvict.add(mu);
            }
        }
        for (ManagedUnit mu : toEvict) {
            squad.removeUnit(mu);
            mu.setRole(UnitRole.FIGHT);
            mu.setDefendTarget(null);
            mu.setRallyPoint(gameState.getBaseData().mainBasePosition().toPosition());
            irradiatedUnits.add(mu);
            assignIrradiatedTarget(mu);
        }
    }

    private void updateIrradiatedUnits() {
        List<ManagedUnit> recovered = new ArrayList<>();
        for (ManagedUnit mu : irradiatedUnits) {
            if (!mu.isIrradiated()) {
                recovered.add(mu);
                continue;
            }
            assignIrradiatedTarget(mu);
        }
        for (ManagedUnit mu : recovered) {
            irradiatedUnits.remove(mu);
            addManagedFighter(mu);
        }
    }

    private void assignIrradiatedTarget(ManagedUnit managedUnit) {
        Unit unit = managedUnit.getUnit();
        List<Unit> enemyUnits = new ArrayList<>(gameState.getVisibleEnemyUnits());

        List<Unit> filtered = new ArrayList<>();
        for (Unit enemyUnit : enemyUnits) {
            if (unit.getType() == UnitType.Zerg_Lurker && !enemyUnit.isFlying() && enemyUnit.isDetected()) {
                filtered.add(enemyUnit);
                continue;
            }
            if (unit.canAttack(enemyUnit) && enemyUnit.isDetected()
                    && !Filter.isLowPriorityCombatTarget(enemyUnit.getType())) {
                filtered.add(enemyUnit);
            }
        }

        if (filtered.isEmpty()) {
            Base enemyBase = gameState.getBaseData().getMainEnemyBase();
            if (enemyBase != null) {
                managedUnit.setMovementTargetPosition(enemyBase.getLocation());
            }
            return;
        }

        filtered = filterByProximity(filtered, unit);

        Unit bestTarget = TargetScorer.selectTarget(unit, filtered, managedUnit.fightTarget);
        if (bestTarget != null) {
            managedUnit.setFightTarget(bestTarget);
        }
    }

    /**
     * Determines the number of workers required to defend, assigns them to the defense squad and
     * returns the assigned workers so they can be removed from the WorkerManager.
     *
     * @param base base to defend
     * @param baseUnits gatherers to assign to defense squad
     * @param hostileUnits units threatening this base
     * @return gatherers that have been assigned to defend
     */
    public List<ManagedUnit> assignGatherersToDefend(Base base, HashSet<ManagedUnit> baseUnits, List<Unit> hostileUnits) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        List<ManagedUnit> reassignedGatherers = new ArrayList<>();
        if (baseUnits.size() < 3) {
            return reassignedGatherers;
        }

        if (gameState.isCannonRushed()) {
            int totalGatherers = gameState.getGatherersAssignedToBase().values().stream()
                    .mapToInt(HashSet::size)
                    .sum();
            int existingDefenders = defenseSquads.values().stream()
                    .mapToInt(s -> s.getMembers().size())
                    .sum();
            int maxToAssign = totalGatherers / 2 - existingDefenders;
            if (maxToAssign <= 0) {
                return reassignedGatherers;
            }
            int assigned = 0;
            for (ManagedUnit gatherer : baseUnits) {
                if (assigned >= maxToAssign) {
                    break;
                }
                defenseSquad.addUnit(gatherer);
                reassignedGatherers.add(gatherer);
                assigned++;
            }
        } else {
            for (ManagedUnit gatherer : baseUnits) {
                boolean canClear = canDefenseSquadClearThreat(defenseSquad, hostileUnits);
                if (canClear) {
                    break;
                }
                defenseSquad.addUnit(gatherer);
                reassignedGatherers.add(gatherer);
            }
        }

        for (ManagedUnit managedUnit: reassignedGatherers) {
            managedUnit.setRole(UnitRole.DEFEND);
            assignDefenderTarget(managedUnit, hostileUnits);
        }

        return reassignedGatherers;
    }

    public List<ManagedUnit> disbandDefendSquad(Base base) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        List<ManagedUnit> reassignedDefenders = new ArrayList<>(defenseSquad.getMembers());

        for (ManagedUnit defender: reassignedDefenders) {
            defenseSquad.removeUnit(defender);
            defender.setDefendTarget(null);
            defender.setMovementTargetPosition(null);
        }

        return reassignedDefenders;
    }

    /**
     * Disbands a squad and returns all its members for reassignment.
     * Removes the squad from the fight squads collection.
     * Overlords are returned to the overlord squad instead of being added to disbanded list.
     *
     * @param squad Squad to disband
     * @return List of managed units that were in the squad (excluding overlords)
     */
    private List<ManagedUnit> disbandSquad(Squad squad) {
        List<ManagedUnit> members = new ArrayList<>(squad.getMembers());
        List<ManagedUnit> nonOverlordMembers = new ArrayList<>();

        for (ManagedUnit member : members) {
            squad.removeUnit(member);
            
            if (member.getUnitType() == UnitType.Zerg_Overlord) {
                overlords.addUnit(member);
                member.setRole(UnitRole.IDLE);
            } else {
                nonOverlordMembers.add(member);
            }
        }

        return nonOverlordMembers;
    }

    public Set<Base> getDefenseSquadBases() {
        return defenseSquads.keySet();
    }

    private void ensureDefenseSquad(Base base) {
        if (!defenseSquads.containsKey(base)) {
            Squad squad = new Squad();
            squad.setCenter(base.getCenter());
            squad.setRallyPoint(base.getLocation().toPosition());
            defenseSquads.put(base, squad);
        }
    }

    private void assignDefenderTarget(ManagedUnit defender, List<Unit> threats) {
        if (defender.getDefendTarget() != null) {
            return;
        }
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        TilePosition defenderTile = defender.getUnit().getTilePosition();

        Unit bestTarget = null;
        int bestHp = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        for (Unit threat : threats) {
            Position threatPos = tracker.getLastKnownPosition(threat);
            if (threatPos == null) {
                continue;
            }
            int hp = threat.getHitPoints();
            int distance = manhattanTileDistance(defenderTile, threatPos.toTilePosition());
            if (hp < bestHp || hp == bestHp && distance < bestDistance) {
                bestHp = hp;
                bestDistance = distance;
                bestTarget = threat;
            }
        }

        if (bestTarget != null) {
            defender.setDefendTarget(bestTarget);
            Position lastKnown = tracker.getLastKnownPosition(bestTarget);
            if (lastKnown != null) {
                defender.setMovementTargetPosition(lastKnown.toTilePosition());
            }
        }
    }

    private void ensureDefenderSquadsHaveTargets() {
        for (Base base: defenseSquads.keySet()) {
            ensureDefenseSquad(base);
            Squad squad = defenseSquads.get(base);
            if (squad.size() == 0) {
                continue;
            }

            HashSet<Unit> baseThreats = gameState.getBaseToThreatLookup().get(base);
            if (baseThreats == null || baseThreats.isEmpty()) {
                continue;
            }
            for (ManagedUnit defender: squad.getMembers()) {
                ensureDefenderHasTarget(defender, baseThreats);
            }
        }
    }

    private void ensureDefenderHasTarget(ManagedUnit defender, HashSet<Unit> baseThreats) {
        if (defender.getDefendTarget() != null) {
            ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
            Position lastKnown = tracker.getLastKnownPosition(defender.getDefendTarget());
            if (lastKnown == null) {
                defender.setDefendTarget(null);
                defender.setMovementTargetPosition(null);
            } else {
                return;
            }
        }
        assignDefenderTarget(defender, new ArrayList<>(baseThreats));
    }

    private void removeEmptySquads() {
        List<Squad> emptySquads = new ArrayList<>();
        for (Squad squad: fightSquads) {
            if (squad.size() == 0) {
                emptySquads.add(squad);
            }
        }

        for (Squad squad: emptySquads) {
            fightSquads.remove(squad);
        }
    }

    /**
     * Combines pairs of nearby fight squads into a single squad.
     *
     * <p>Merge sets are unordered, so the surviving state is folded by precedence in
     * {@link Squad#inheritStateFrom(java.util.Collection)} rather than being taken from whichever squad happens to be
     * iterated last.
     */
    private void mergeSquads() {
        if (game.getFrameCount() % MERGE_CHECK_INTERVAL != 0) {
            return;
        }

        int currentFrame = game.getFrameCount();
        List<Set<Squad>> toMerge = new ArrayList<>();
        Set<Squad> considered = new HashSet<>();
        for (Squad squad1: fightSquads) {
            for (Squad squad2: fightSquads) {
                if (squad1 == squad2) continue;
                if (considered.contains(squad1) || considered.contains(squad2)) continue;
                if (!squad1.isMergeEligible(currentFrame) || !squad2.isMergeEligible(currentFrame)) continue;
                boolean bothGround = squad1.isGroundSquad() && squad2.isGroundSquad();
                boolean bothAir = squad1.isAirSquad() && squad2.isAirSquad();
                if (!bothGround && !bothAir) continue;
                if (squad1.distance(squad2) < SQUAD_MERGE_DISTANCE) {
                    Set<Squad> mergeSet = new HashSet<>();
                    mergeSet.add(squad1);
                    mergeSet.add(squad2);
                    considered.add(squad1);
                    considered.add(squad2);
                    toMerge.add(mergeSet);
                }
            }
        }

        for (Set<Squad> mergeSet: toMerge) {
            Squad first = mergeSet.iterator().next();
            Squad newSquad;
            if (first.isGroundSquad()) {
                newSquad = newFightSquad(UnitType.Zerg_Zergling);
            } else {
                newSquad = newFightSquad(UnitType.Zerg_Mutalisk);
            }
            newSquad.inheritStateFrom(mergeSet);
            for (Squad mergingSquad: mergeSet) {
                for (ManagedUnit mu : new ArrayList<>(mergingSquad.getMembers())) {
                    newSquad.addUnit(mu);
                }
                fightSquads.remove(mergingSquad);
            }
            newSquad.setSplitFrame(currentFrame);
            fightSquads.add(newSquad);
        }
    }

    private void splitSquads() {
        if (game.getFrameCount() % MERGE_CHECK_INTERVAL != 0) {
            return;
        }

        int currentFrame = game.getFrameCount();
        List<Squad> toAdd = new ArrayList<>();

        for (Squad squad : fightSquads) {
            if (squad.getStatus() == SquadStatus.CONTAIN) continue;
            if (squad.getStatus() == SquadStatus.RALLY) continue;
            if (squad.getStatus() == SquadStatus.RETREAT) continue;
            if (squad.size() < 4) continue;

            int threshold = squad.isAirSquad() ? AIR_SPLIT_DISTANCE : GROUND_SPLIT_DISTANCE;
            List<ManagedUnit> outliers = squad.findOutliers(threshold);
            if (outliers.isEmpty()) continue;

            int moveOutThreshold = calculateMoveOutThreshold(squad);
            int totalStrength = squadStrength(squad);
            int outlierStrength = strengthOf(squad, outliers);
            if (!splitKeepsBothSidesCommittable(totalStrength, outlierStrength, moveOutThreshold)) {
                SquadDecisions.splitSuppressed(squad, moveOutThreshold, totalStrength, outlierStrength);
                continue;
            }

            Squad child = squad.createSibling();
            child.inheritStateFrom(squad);
            child.setSplitFrame(currentFrame);
            squad.setSplitFrame(currentFrame);
            for (ManagedUnit mu : outliers) {
                squad.removeUnit(mu);
                child.addUnit(mu);
            }
            toAdd.add(child);
        }

        fightSquads.addAll(toAdd);
    }

    /**
     * Returns true when both sides of a proposed split would still clear the move out threshold.
     *
     * <p>splitSquads runs before evaluateSquadRole in the same frame, so a split that drops either side under
     * the threshold hands both fragments to the launch gate on the tick they are created and abandons an attack
     * the whole squad was cleared to make. Splitting is a formation fix, not a reason to give up ground.
     *
     * @param squadStrength strength of the squad before the split
     * @param outlierStrength strength of the units leaving for the sibling squad
     * @param moveOutThreshold strength each side needs to stay cleared to move out
     * @return true if the split is safe to perform
     */
    static boolean splitKeepsBothSidesCommittable(int squadStrength, int outlierStrength, int moveOutThreshold) {
        return outlierStrength >= moveOutThreshold && squadStrength - outlierStrength >= moveOutThreshold;
    }

    private int strengthOf(Squad squad, List<ManagedUnit> units) {
        if (squad.isAirSquad() && squad.hasOnly(UnitType.Zerg_Scourge)) {
            return units.size();
        }

        int supply = 0;
        for (ManagedUnit managedUnit : units) {
            supply += managedUnit.getUnitType().supplyRequired();
        }
        return supply;
    }

    private int squadStrength(Squad squad) {
        if (squad.isAirSquad() && squad.hasOnly(UnitType.Zerg_Scourge)) {
            return squad.size();
        }
        return squad.getSupply();
    }

    private List<Unit> enemyUnitsNearSquad(Squad squad) {
        Set<Unit> enemyUnits = gameState.getVisibleEnemyUnits();

        List<Unit> enemies = new ArrayList<>();

        for (Unit u: enemyUnits) {
            final double d = u.getPosition().getDistance(squad.getCenter());
            if (d > ENEMY_DETECTION_RADIUS) {
                continue;
            }
            boolean relevant = squad.getMembers().stream()
                    .anyMatch(member -> member.getUnit().canAttack(u) || u.canAttack(member.getUnit()));
            if (!relevant) {
                continue;
            }
            enemies.add(u);
        }

        return enemies;
    }

    private void rallySquad(Squad squad) {
        squad.setStatus(SquadStatus.RALLY);
        squad.clearCommitment();
        Position rallyPoint = this.getRallyPoint(squad);
        for (ManagedUnit managedUnit: squad.getMembers()) {
            managedUnit.setRallyPoint(rallyPoint);
            managedUnit.setRole(UnitRole.RALLY);
        }
    }

    /**
     * Rallies to the squad closest to the enemy main base, otherwise
     * defaults to the main or natural expansion.
     *
     * Squads actively fighting or holding a containment are eligible rally targets.
     * Exempts the given squad as a rally candidate.
     *
     * <p>With reinforcement staging enabled, every rallying squad is sent to the staging point.
     *
     * @return Position to rally squad to
     */
    private Position getRallyPoint(Squad squad) {
        if (gameState.getConfig().stageReinforcements) {
            return gameState.getSquadRallyPoint();
        }

        List<Squad> eligibleSquads = fightSquads.stream()
                .filter(s -> s != squad)
                .filter(s -> s.getStatus() == SquadStatus.FIGHT || s.getStatus() == SquadStatus.CONTAIN)
                .collect(Collectors.toList());

        final Base enemyMainBase = gameState.getBaseData().getMainEnemyBase();
        if (!eligibleSquads.isEmpty() && enemyMainBase != null) {
            Squad best = null;
            for (Squad s: eligibleSquads) {
                if (best == null) {
                    best = s;
                    continue;
                }
                final double bestDistance = best.getCenter().getDistance(enemyMainBase.getCenter());
                final double candidateDistance = s.getCenter().getDistance(enemyMainBase.getCenter());
                if (candidateDistance < bestDistance) {
                    best = s;
                }
            }

            return best.getCenter().toTilePosition().toPosition();
        }

        return gameState.getSquadRallyPoint();
    }

    /**
     * Determines whether a squad should continue or change its current role.
     * @param squad Squad to evaluate
     */
    private void evaluateSquadRole(Squad squad) {
        final boolean closeThreats = !enemyUnitsNearSquad(squad).isEmpty();

        SquadStatus squadStatus = squad.getStatus();
        if (squadStatus == SquadStatus.CONTAIN) {
            clearCombatSimSnapshot(squad);
            evaluateContainingSquad(squad);
            return;
        }

        SquadAction action = chooseSquadAction(closeThreats, squadStrength(squad), calculateMoveOutThreshold(squad),
                squadStatus, squad.isCommitted(), distanceFromRallyPoint(squad));

        if (action == SquadAction.RALLY) {
            clearCombatSimSnapshot(squad);
            rallySquad(squad);
            return;
        }

        squad.commit(game.getFrameCount());
        if (action == SquadAction.LAUNCH && tryEnterContainment(squad)) {
            return;
        }

        simulateFightSquad(squad);
    }

    /**
     * Branch {@link #evaluateSquadRole} takes for a fight squad that is not already containing.
     */
    enum SquadAction {
        SIMULATE,
        LAUNCH,
        RALLY
    }

    /**
     * Picks the branch for a fight squad that is not already containing.
     *
     * <p>The move out threshold is a launch permission, not a recall rule. A squad already cleared to move out
     * keeps acting while it is committed and still out on the map, so losses or a split dropping it under the
     * threshold do not send it back to the global rally point with no enemy anywhere near it. Commitment is
     * released by {@link #rallySquad} and, for a squad that has walked itself home, by the release distance.
     *
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @param squadStrength supply of the squad, or unit count for a Scourge only squad
     * @param moveOutThreshold strength the squad needs to be cleared to move out
     * @param status status the squad held entering the tick
     * @param committed true when the squad has been cleared to act and has not been recalled since
     * @param distanceFromRallyPoint pixels between the squad center and the global rally point
     * @return branch to take
     */
    static SquadAction chooseSquadAction(boolean closeThreats, int squadStrength, int moveOutThreshold,
                                         SquadStatus status, boolean committed, double distanceFromRallyPoint) {
        if (closeThreats) {
            return SquadAction.SIMULATE;
        }
        if (squadStrength >= moveOutThreshold) {
            return SquadAction.LAUNCH;
        }
        if (status == SquadStatus.FIGHT) {
            return SquadAction.SIMULATE;
        }
        if (committed && distanceFromRallyPoint > COMMITMENT_RELEASE_DISTANCE) {
            return SquadAction.SIMULATE;
        }
        return SquadAction.RALLY;
    }

    private double distanceFromRallyPoint(Squad squad) {
        Position rallyPoint = gameState.getSquadRallyPoint();
        Position center = squad.getCenter();
        if (rallyPoint == null || center == null) {
            return 0;
        }
        return center.getDistance(rallyPoint);
    }

    private int calculateMoveOutThreshold(Squad squad) {
        if (squad.isAirSquad()) {
            return calculateAirSquadMoveOutThreshold(squad);
        }

        if (squad.isGroundSquad()) {
            return calculateGroundSquadMoveOutThreshold(squad);
        }

        return defaultMoveOutThreshold();
    }

    private int calculateAirSquadMoveOutThreshold(Squad squad) {
        if (squad.hasOnly(UnitType.Zerg_Scourge)) {
            return 2;
        }
        if (gameState.getOpponentRace() == Race.Zerg) {
            return 2;
        }
        return 3;
    }

    private int defaultMoveOutThreshold() {
        int staticDefensePenalty = min(gameState.getObservedUnitTracker().getHostileToGroundBuildings().size(), 6);
        int moveOutThreshold = 8 * (1 + staticDefensePenalty);
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (strategyTracker.isDetectedStrategy("2Gate")) {
            final int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            moveOutThreshold += zealots * 2;
        }

        return Math.min(moveOutThreshold, MAX_MOVE_OUT_THRESHOLD);
    }

    private int calculateGroundSquadMoveOutThreshold(Squad squad) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        final boolean isActivelyCannonRushed = gameState.isCannonRushed();
        final boolean isCannonRushed = strategyTracker.isDetectedStrategy("CannonRush");

        if (isCannonRushed) {
            if (isActivelyCannonRushed) {
                Set<Position> basePositions = gameState.getBaseData().getMyBasePositions();
                ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
                int completedCannons = tracker.getCompletedBuildingCountNearPositions(UnitType.Protoss_Photon_Cannon, basePositions, 512);
                if (completedCannons == 0) {
                    return 1;
                }
                return Math.max(6, completedCannons * 3);
            }
            final int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            if (zealots < 1) {
                return 2;
            }
        }

        if (squad.hasOnly(UnitType.Zerg_Lurker)) {
            return 1;
        }

        int threshold = 4;

        int rushThresholdIncrease = 0;
        if (gameState.isEarlyRushed()) {
            rushThresholdIncrease = gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases() * 2;
        }
        if (strategyTracker.isDetectedStrategy("2Gate")) {
            final int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            rushThresholdIncrease = Math.max(rushThresholdIncrease, zealots * 2);
        }
        threshold += rushThresholdIncrease;

        return Math.min(threshold, MAX_MOVE_OUT_THRESHOLD);
    }

    private void simulateFightSquad(Squad squad) {
        HashSet<ManagedUnit> managedFighters = squad.getMembers();

        if (squad.isGroundSquad() && squad.hasOnly(UnitType.Zerg_Lurker)) {
            squad.setStatus(SquadStatus.FIGHT);
            assignFightTargets(squad, managedFighters, false);
            return;
        }

        if (squad.isGroundSquad() && squad.hasOnly(UnitType.Zerg_Defiler)) {
            rallySquad(squad);
            return;
        }

        Set<Position> stormPositions = gameState.getActiveStormPositions();
        if (!stormPositions.isEmpty()) {
            boolean anyUnitInStorm = false;
            for (ManagedUnit managedUnit : managedFighters) {
                Position unitPos = managedUnit.getUnit().getPosition();
                for (Position stormPos : stormPositions) {
                    if (unitPos.getDistance(stormPos) <= PsiStormTracker.STORM_RADIUS) {
                        anyUnitInStorm = true;
                        break;
                    }
                }
                if (anyUnitInStorm) break;
            }

            if (anyUnitInStorm) {
                squad.setStatus(SquadStatus.RETREAT);
                int now = game.getFrameCount();
                for (ManagedUnit managedUnit : managedFighters) {
                    managedUnit.setRole(UnitRole.RETREAT);
                    managedUnit.markRetreatStart(now);
                    Position retreatTarget = calculateStormRetreatPosition(managedUnit.getUnit().getPosition(), stormPositions);
                    managedUnit.setRetreatTarget(retreatTarget);
                }
                squad.startRetreatLock(now);
                return;
            }
        }

        Set<Position> enemyBuildingPositions = gameState.getLastKnownPositionsOfBuildings();
        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();

        if (enemyUnits.isEmpty() && enemyBuildingPositions.isEmpty()) {
            ScoutData scoutData = gameState.getScoutData();
            if (!scoutData.isEnemyBuildingLocationKnown()) {
                squad.setShouldDisband(true);
                return;
            }
        }

        if (enemyUnits.isEmpty() && !enemyBuildingPositions.isEmpty()) {
            Position closestPosition = closestKnownEnemyBuilding(squad.getCenter());
            if (closestPosition != null) {
                squad.setStatus(SquadStatus.FIGHT);
                for (ManagedUnit managedUnit : squad.getMembers()) {
                    managedUnit.setRole(UnitRole.FIGHT);
                    managedUnit.setMovementTargetPosition(closestPosition.toTilePosition());
                }
                return;
            }
        }

        int now = game.getFrameCount();
        boolean retreatLocked = squad.isRetreatLocked(now);
        boolean fightLocked = squad.isFightLocked(now);

        Map<Squad, Double> adjacentSquads = getAdjacentSquads(squad, REINFORCEMENT_RADIUS);
        CombatSimulator.CombatResult result = squad.getCombatSimulator()
                .evaluate(squad, adjacentSquads, gameState);
        SquadDecisions.simEvaluated(squad, result, retreatLocked, fightLocked);

        HorizonCombatSimulator.DebugSnapshot snapshot = lastSnapshot(squad);
        boolean enemyMeasured = snapshot == null || snapshot.isEnemyMeasured();
        boolean threatBeyondRadius = snapshot != null && snapshot.isThreatBeyondRadius();
        double ratio = snapshot != null ? snapshot.getOverallRatio() : 0;
        double retreatThreshold = snapshot != null ? snapshot.getRetreatThreshold() : 0;

        if (squad.getStatus() == SquadStatus.RETREAT && retreatLocked) {
            SquadDecisions.lockSuppressed(squad, SquadLock.RETREAT);
            assignRetreatTargets(squad, managedFighters);
            return;
        }
        if (squad.getStatus() == SquadStatus.FIGHT
                && fightLockHolds(fightLocked, result, enemyMeasured, ratio, retreatThreshold)) {
            SquadDecisions.lockSuppressed(squad, SquadLock.FIGHT);
            assignFightTargets(squad, managedFighters, false);
            return;
        }

        switch (result) {
            case ADVANCE:
                boolean baseThreatened = squad.getStatus() != SquadStatus.FIGHT && baseThreatened();
                if (blindAdvanceHeld(squad.getStatus(), enemyMeasured, threatBeyondRadius, baseThreatened)) {
                    holdSquad(squad, managedFighters);
                    break;
                }
                squad.setStatus(SquadStatus.FIGHT);
                assignFightTargets(squad, managedFighters, true);
                break;

            case RETREAT:
                boolean enteredContain = tryEnterContainment(squad);
                if (!enteredContain) {
                    squad.setStatus(SquadStatus.RETREAT);
                    assignRetreatTargets(squad, managedFighters);
                    squad.startRetreatLock(now);
                }
                break;

            case ENGAGE:
                squad.setStatus(SquadStatus.FIGHT);
                assignFightTargets(squad, managedFighters, true);
                updateFightLock(squad, result, retreatLocked, now);
                break;

            default:
                break;
        }
    }

    static void updateFightLock(Squad squad, CombatSimulator.CombatResult result,
                                boolean retreatLocked, int currentFrame) {
        if (result == CombatSimulator.CombatResult.ENGAGE && !retreatLocked) {
            squad.startFightLock(currentFrame);
        }
    }

    /**
     * Whether an active fight lock still holds against this frame's verdict.
     *
     * <p>Every RETREAT is "measured" by construction: an unmeasured enemy yields a ratio of
     * friendly strength over the minimum enemy floor, which clears every threshold on its own.
     * So the lock only breaks for a RETREAT that was measured against a real enemy and still
     * fell below the matchup's retreat threshold; an in-band RETREAT (below the engage threshold
     * but at or above the retreat threshold) stays suppressed.
     *
     * @param fightLocked whether the squad's fight lock is currently active
     * @param result this frame's combat sim verdict
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param retreatThreshold the matchup's retreat threshold
     * @return true if the lock should still suppress this frame's verdict
     */
    static boolean fightLockHolds(boolean fightLocked, CombatSimulator.CombatResult result,
                                  boolean enemyMeasured, double ratio, double retreatThreshold) {
        if (!fightLocked) return false;
        if (result != CombatSimulator.CombatResult.RETREAT) return true;
        return !enemyMeasured || ratio >= retreatThreshold;
    }

    /**
     * Whether a blind ADVANCE verdict should be held rather than committed as a transition into FIGHT.
     *
     * <p>A squad already in FIGHT is mid-approach and must keep going so the sim can measure the
     * enemy at close range and return a real verdict; only a transition into FIGHT is blocked.
     * An unmeasured ADVANCE is held when the sim itself saw a fresh, attack-capable, non-worker
     * enemy just beyond its engagement radius, or when a mobile ground combat unit is standing on
     * one of our bases.
     *
     * @param status the squad's status entering this tick
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param threatBeyondRadius whether the sim saw a nearby but unmeasured threat this frame
     * @param baseThreatened whether a mobile ground combat unit is on one of our bases
     * @return true if the blind ADVANCE should be held rather than acted on
     */
    static boolean blindAdvanceHeld(SquadStatus status, boolean enemyMeasured,
                                    boolean threatBeyondRadius, boolean baseThreatened) {
        if (status == SquadStatus.FIGHT || enemyMeasured) return false;
        return threatBeyondRadius || baseThreatened;
    }

    private HorizonCombatSimulator.DebugSnapshot lastSnapshot(Squad squad) {
        CombatSimulator sim = squad.getCombatSimulator();
        if (!(sim instanceof HorizonCombatSimulator)) {
            return null;
        }
        return ((HorizonCombatSimulator) sim).getLastSnapshots().get(squad.getId());
    }

    private boolean baseThreatened() {
        return gameState.visibleEnemyMobileGroundCombatUnitsAtOurBases() > 0;
    }

    private void holdSquad(Squad squad, HashSet<ManagedUnit> managedFighters) {
        if (squad.getStatus() == SquadStatus.RETREAT) {
            assignRetreatTargets(squad, managedFighters);
        } else {
            rallySquad(squad);
        }
    }

    private void clearCombatSimSnapshot(Squad squad) {
        CombatSimulator sim = squad.getCombatSimulator();
        if (sim instanceof HorizonCombatSimulator) {
            ((HorizonCombatSimulator) sim).getLastSnapshots().remove(squad.getId());
        }
    }

    private void assignRetreatTargets(Squad squad, HashSet<ManagedUnit> managedFighters) {
        Position rallyPoint = gameState.getSquadRallyPoint();
        HashMap<ManagedUnit, Position> retreatTargets = squad.isGroundSquad()
                ? computeGroundRetreatTargets(squad)
                : null;
        for (ManagedUnit managedUnit : managedFighters) {
            if (managedUnit.getRole() != UnitRole.RETREAT) {
                managedUnit.setReady(true);
            }
            managedUnit.setRole(UnitRole.RETREAT);
            managedUnit.setRallyPoint(rallyPoint);
            if (retreatTargets != null) {
                managedUnit.setRetreatTarget(retreatTargets.get(managedUnit));
            } else {
                managedUnit.setRetreatTarget(managedUnit.getRetreatPosition());
            }
        }
    }

    private void assignFightTargets(Squad squad, HashSet<ManagedUnit> managedFighters, boolean clearRetreat) {
        for (ManagedUnit managedUnit : managedFighters) {
            managedUnit.setRole(UnitRole.FIGHT);
            if (clearRetreat) {
                managedUnit.clearRetreatStart();
            }
            assignEnemyTarget(managedUnit, squad);
        }
    }

    /**
     * Runs the containment entry decision and reports the verdict that produced it.
     *
     * <p>The evaluator calls stay short circuited in their original order: canBreakContainment is
     * consulted only when shouldContain holds, and enterContainment only when both allow it.
     *
     * @param squad squad offered an arc
     * @return true if the squad took the arc and is now containing
     */
    private boolean tryEnterContainment(Squad squad) {
        boolean shouldContain = containmentEvaluator.shouldContain(squad);
        boolean canBreak = shouldContain && containmentEvaluator.canBreakContainment(fightSquads);
        boolean entered = shouldContain && !canBreak && enterContainment(squad);
        SquadDecisions.containmentEvaluated(squad, shouldContain, canBreak, entered);
        return entered;
    }

    private boolean enterContainment(Squad squad) {
        HashSet<Base> enemyBases = gameState.getBaseData().getEnemyBases();
        if (enemyBases.isEmpty()) return false;
        Base containBase = closestBaseTo(squad.getCenter(), enemyBases);
        Position chokePosition = findContainmentChoke(squad.getCenter(), containBase);
        if (chokePosition == null) return false;
        squad.setStatus(SquadStatus.CONTAIN);
        squad.startContainLock(game.getFrameCount());
        assignContainmentPositions(squad, containBase, chokePosition);
        return true;
    }

    private void evaluateContainingSquad(Squad squad) {
        int now = game.getFrameCount();
        HashSet<ManagedUnit> members = squad.getMembers();

        List<Unit> closeEnemies = enemyUnitsNearSquad(squad);
        if (!closeEnemies.isEmpty()) {
            squad.setStatus(SquadStatus.FIGHT);
            assignFightTargets(squad, members, true);
            squad.startFightLock(now);
            return;
        }

        if (squad.isContainLocked(now) && now % CONTAINMENT_REEVALUATE_INTERVAL != 0) {
            return;
        }

        if (basesUnderAttack()) {
            breakAllContainment(now);
            return;
        }

        boolean timedOut = squad.getContainStartFrame() > 0
                && now - squad.getContainStartFrame() >= CONTAINMENT_TIMEOUT_FRAMES;

        if (timedOut || containmentEvaluator.canBreakContainment(fightSquads)) {
            breakAllContainment(now);
            return;
        }

        if (!containmentEvaluator.shouldContain(squad)) {
            squad.clearContainStart();
            squad.setStatus(SquadStatus.RETREAT);
            assignRetreatTargets(squad, members);
            squad.startRetreatLock(now);
            return;
        }

        assignContainmentPositions(squad);
    }

    private void breakAllContainment(int now) {
        for (Squad s : fightSquads) {
            if (s.getStatus() == SquadStatus.CONTAIN) {
                s.clearContainStart();
                s.setStatus(SquadStatus.FIGHT);
                assignFightTargets(s, s.getMembers(), true);
                s.startFightLock(now);
            }
        }
    }

    private boolean basesUnderAttack() {
        for (HashSet<Unit> threats : gameState.getBaseToThreatLookup().values()) {
            if (!threats.isEmpty()) return true;
        }
        return false;
    }

    private void assignContainmentPositions(Squad squad) {
        HashSet<Base> enemyBases = gameState.getBaseData().getEnemyBases();
        if (enemyBases.isEmpty()) return;
        Base containBase = closestBaseTo(squad.getCenter(), enemyBases);
        Position chokePosition = findContainmentChoke(squad.getCenter(), containBase);
        if (chokePosition == null) return;
        assignContainmentPositions(squad, containBase, chokePosition);
    }

    private void assignContainmentPositions(Squad squad, Base containBase, Position chokePosition) {
        Position enemyBasePosition = containBase.getCenter();
        Position faceTarget = new Position(
                2 * chokePosition.getX() - enemyBasePosition.getX(),
                2 * chokePosition.getY() - enemyBasePosition.getY()
        );

        Set<Position> coverage = gameState.getStaticDefenseCoverage();

        int unitCount = squad.size();
        int numPoints = Math.max(unitCount, 4);

        int mapPixelWidth = game.mapWidth() * 32;
        int mapPixelHeight = game.mapHeight() * 32;
        Arc arc = new Arc(chokePosition, faceTarget, ARC_RADIUS, ARC_DEGREES, numPoints);
        Set<WalkPosition> accessiblePositions = gameState.getGameMap().getAccessibleWalkPositions();
        arc.compute(accessiblePositions, coverage, mapPixelWidth, mapPixelHeight);

        if (arc.isEmpty()) return;

        activeContainmentArcs.add(arc);

        List<ManagedUnit> units = new ArrayList<>(squad.getMembers());
        Map<ManagedUnit, Position> assignments = arc.assignUnits(units);
        for (Map.Entry<ManagedUnit, Position> entry : assignments.entrySet()) {
            ManagedUnit mu = entry.getKey();
            mu.setRole(UnitRole.CONTAIN);
            mu.setContainPosition(entry.getValue());
        }
    }

    private Base closestBaseTo(Position pos, Set<Base> bases) {
        Map<TilePosition, Position> baseTiles = new HashMap<>();
        for (Base base : bases) {
            baseTiles.put(base.getLocation(), base.getCenter());
        }
        Position nearest = gameState.getGameMap().findNearestByGround(
                pos.toTilePosition(), baseTiles, Collections.emptySet());
        if (nearest != null) {
            for (Base base : bases) {
                if (base.getCenter().equals(nearest)) return base;
            }
        }
        Base closest = null;
        double minDist = Double.MAX_VALUE;
        for (Base base : bases) {
            double dist = pos.getDistance(base.getCenter());
            if (dist < minDist) {
                minDist = dist;
                closest = base;
            }
        }
        return closest;
    }

    private Position findContainmentChoke(Position squadPos, Base base) {
        CPPath path = gameState.getBwem().getMap().getPath(squadPos, base.getCenter());
        if (!path.isEmpty()) {
            return path.get(path.size() - 1).getCenter().toPosition();
        }
        return null;
    }

    /**
     * Computes a shared retreat anchor for zerglings with perpendicular jitter per unit.
     * Anchor: vector from squad center away from closest enemy cluster.
     * Jitter: perpendicular offsets to reduce clumping.
     */
    private HashMap<ManagedUnit, Position> computeGroundRetreatTargets(Squad squad) {
        HashMap<ManagedUnit, Position> result = new HashMap<>();
        Position center = squad.getCenter();
        List<Unit> enemiesNear = enemyUnitsNearSquad(squad);
        if (enemiesNear.isEmpty()) {
            for (ManagedUnit mu : squad.getMembers()) {
                result.put(mu, center);
            }
            return result;
        }

        double ex = 0;
        double ey = 0;
        int cnt = 0;
        for (Unit e : enemiesNear) {
            Position p = e.getPosition();
            ex += p.getX();
            ey += p.getY();
            cnt++;
        }
        ex /= cnt; ey /= cnt;

        Vec2 away = new Vec2(center.getX() - ex, center.getY() - ey);
        double len = Math.max(1.0, away.length());
        Vec2 awayUnit = away.scale(1.0 / len);
        Vec2 perp = awayUnit.perpendicular();
        Position anchor = awayUnit.scale(RETREAT_VECTOR_MAGNITUDE).clampToMap(game, center);

        int maxX = game.mapWidth() * 32 - 1;
        int maxY = game.mapHeight() * 32 - 1;

        int i = 0;
        for (ManagedUnit mu : squad.getMembers()) {
            int side = (i % 2 == 0) ? 1 : -1;
            double mag = 16 + (i / 2) * 8;
            int rx = (int) Math.round(anchor.getX() + side * perp.x * mag);
            int ry = (int) Math.round(anchor.getY() + side * perp.y * mag);
            rx = Math.max(0, Math.min(rx, maxX));
            ry = Math.max(0, Math.min(ry, maxY));

            if (!game.isWalkable(new WalkPosition(rx, ry))) {
                int bestX = rx;
                int bestY = ry;
                boolean found = false;
                for (int t = 256; t >= 0; t -= 16) {
                    int cx = (int) Math.round(rx + awayUnit.x * t);
                    int cy = (int) Math.round(ry + awayUnit.y * t);
                    cx = Math.max(0, Math.min(cx, maxX));
                    cy = Math.max(0, Math.min(cy, maxY));
                    if (game.isWalkable(new WalkPosition(cx, cy))) {
                        bestX = cx;
                        bestY = cy;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (int t = 16; t <= 128; t += 16) {
                        int cx = (int) Math.round(rx - awayUnit.x * t);
                        int cy = (int) Math.round(ry - awayUnit.y * t);
                        cx = Math.max(0, Math.min(cx, maxX));
                        cy = Math.max(0, Math.min(cy, maxY));
                        if (game.isWalkable(new WalkPosition(cx, cy))) {
                            bestX = cx;
                            bestY = cy;
                            break;
                        }
                    }
                }
                rx = bestX;
                ry = bestY;
            }

            result.put(mu, new Position(rx, ry));
            i++;
        }

        return result;
    }

    private Position calculateStormRetreatPosition(Position unitPos, Set<Position> stormPositions) {
        double totalDx = 0;
        double totalDy = 0;
        double totalWeight = 0;

        for (Position stormPos : stormPositions) {
            double distance = unitPos.getDistance(stormPos);
            if (distance > 0) {
                double weight = 1.0 / (distance * distance / 10000);
                totalDx += (unitPos.getX() - stormPos.getX()) * weight;
                totalDy += (unitPos.getY() - stormPos.getY()) * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0) {
            return unitPos;
        }

        Vec2 weighted = new Vec2(totalDx / totalWeight, totalDy / totalWeight);
        Vec2 retreat = weighted.length() > 0
                ? weighted.normalizeToLength(RETREAT_VECTOR_MAGNITUDE)
                : new Vec2(RETREAT_VECTOR_MAGNITUDE, 0);
        return retreat.clampToMap(game, unitPos);
    }

    private boolean canDefenseSquadClearThreat(Squad squad, List<Unit> enemyUnits) {
        HashSet<ManagedUnit> managedDefenders = squad.getMembers();

        Simulator simulator = new Simulator.Builder().build();

        for (ManagedUnit managedUnit: managedDefenders) {
            if (managedUnit.getUnit().getType() == UnitType.Unknown) {
                continue;
            }
            simulator.addAgentA(agentFactory.of(managedUnit.getUnit()));
        }

        for (Unit enemyUnit: enemyUnits) {
            if (enemyUnit.getType() == UnitType.Unknown) {
                continue;
            }
            if ((int) enemyUnit.getPosition().getDistance(squad.getCenter()) > DEFENSE_SIM_RANGE) {
                continue;
            }
            try {
                simulator.addAgentB(agentFactory.of(enemyUnit));
            } catch (ArithmeticException e) {
                return false;
            }
        }

        simulator.simulate(COMBAT_SIM_DURATION_FRAMES);

        if (simulator.getAgentsB().isEmpty()) {
            return true;
        }

        if (simulator.getAgentsA().isEmpty()) {
            return false;
        }

        final boolean isSCVRush = gameState.getStrategyTracker().isDetectedStrategy("SCVRush");
        float percentRemaining = (float) simulator.getAgentsA().size() / managedDefenders.size();
        final double percentThreshold = isSCVRush ? 0.75 : DEFENSE_WIN_THRESHOLD;
        return percentRemaining >= percentThreshold;
    }

    /**
     * Adds a managed unit to the squad manager. Overlords are sorted into the overlord squad; all other units are
     * added to fight squads.
     * @param managedUnit
     */
    public void addManagedUnit(ManagedUnit managedUnit) {
        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
            addManagedOverlord(managedUnit);
            return;
        }

        addManagedFighter(managedUnit);
    }

    public void onUnitDestroy(Unit unit) {
        for (Squad squad: fightSquads) {
            if (squad.getTarget() == unit) {
                squad.setTarget(null);
            }
        }
        irradiatedUnits.removeIf(mu -> mu.getUnit() == unit);
    }

    private void addManagedFighter(ManagedUnit managedUnit) {
        if (managedUnit.isIrradiated()) {
            managedUnit.setRole(UnitRole.FIGHT);
            managedUnit.setRallyPoint(gameState.getBaseData().mainBasePosition().toPosition());
            irradiatedUnits.add(managedUnit);
            assignIrradiatedTarget(managedUnit);
            return;
        }
        UnitType type = managedUnit.getUnitType();
        Squad squad;
        if (AIR_SQUAD_TYPES.contains(type)) {
            squad = findCloseAirSquad(managedUnit);
        } else {
            squad = findCloseGroundSquad(managedUnit);
        }
        if (squad == null) {
            squad = newFightSquad(type);
        }

        squad.addUnit(managedUnit);
        if (shouldStageSquad(squad)) {
            rallySquad(squad);
            return;
        }

        simulateFightSquad(squad);
    }

    private Squad findCloseGroundSquad(ManagedUnit managedUnit) {
        for (Squad squad : fightSquads) {
            if (!squad.isGroundSquad()) continue;
            if (squad.distance(managedUnit) < SQUAD_MERGE_DISTANCE) {
                return squad;
            }
        }
        return null;
    }

    private Squad findCloseAirSquad(ManagedUnit managedUnit) {
        Squad closestSquad = null;
        double closestDistance = Double.MAX_VALUE;

        for (Squad squad : fightSquads) {
            if (!squad.isAirSquad()) continue;

            if (squad.getStatus() == SquadStatus.RALLY || squad.getStatus() == SquadStatus.FIGHT) {
                double distance = squad.distance(managedUnit);

                boolean canJoin = squad.getStatus() == SquadStatus.RALLY ||
                                distance < AIR_JOIN_DISTANCE;

                if (canJoin && distance < closestDistance) {
                    closestDistance = distance;
                    closestSquad = squad;
                }
            }
        }

        return closestSquad;
    }

    private Squad newFightSquad(UnitType type) {
        Squad newSquad;

        if (AIR_SQUAD_TYPES.contains(type)) {
            newSquad = new AirSquad();
        } else {
            newSquad = new GroundSquad();
        }

        newSquad.setStatus(stagingStatus());
        newSquad.setRallyPoint(this.getRallyPoint(newSquad));
        fightSquads.add(newSquad);
        return newSquad;
    }

    /**
     * Returns RALLY when reinforcement staging is enabled, otherwise FIGHT.
     *
     * @return status to seed a new fight squad with
     */
    private SquadStatus stagingStatus() {
        return gameState.getConfig().stageReinforcements ? SquadStatus.RALLY : SquadStatus.FIGHT;
    }

    /**
     * Returns true when a rallying squad has no enemies within detection range and should stage.
     *
     * @param squad squad the reinforcement was added to
     * @return true if the squad should rally to the staging point
     */
    private boolean shouldStageSquad(Squad squad) {
        if (!gameState.getConfig().stageReinforcements) {
            return false;
        }
        if (squad.getStatus() != SquadStatus.RALLY) {
            return false;
        }
        return enemyUnitsNearSquad(squad).isEmpty();
    }

    private void addManagedOverlord(ManagedUnit overlord) {
        overlords.addUnit(overlord);
    }

    public void removeManagedUnit(ManagedUnit managedUnit) {
        irradiatedUnits.remove(managedUnit);
        UnitType unitType = managedUnit.getUnitType();
        if (unitType != null && unitType == UnitType.Zerg_Overlord) {
            removeManagedOverlord(managedUnit);
            return;
        }

        removeManagedFighter(managedUnit);
    }

    private void removeManagedFighter(ManagedUnit managedUnit) {
        for (Squad squad: fightSquads) {
            if (squad.containsManagedUnit(managedUnit)) {
                squad.removeUnit(managedUnit);
                if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
                    overlords.addUnit(managedUnit);
                }
                return;
            }
        }
        for (Base base: defenseSquads.keySet()) {
            Squad squad = defenseSquads.get(base);
            squad.removeUnit(managedUnit);
        }
    }

    private void removeManagedOverlord(ManagedUnit overlord) {
        overlords.removeUnit(overlord);
        overlord.setRole(UnitRole.IDLE);
    }

    /**
     * Assign target to a fighter and squad.
     *
     * @param managedUnit unit that needs a target
     * @param squad squad that passed fight simulation
     */
    private void assignEnemyTarget(ManagedUnit managedUnit, Squad squad) {
        Unit unit = managedUnit.getUnit();
        if (managedUnit.getUnitType() == UnitType.Zerg_Overlord) {
            if (gameState.getTechProgression().isOverlordSpeed()) {
                managedUnit.setRallyPoint(squad.getCenter());
                managedUnit.setRole(UnitRole.RALLY);
            } else {
                squad.removeUnit(managedUnit);
                overlords.addUnit(managedUnit);
                managedUnit.setRole(UnitRole.IDLE);
            }
            return;
        }
        if (managedUnit.getUnitType() == UnitType.Zerg_Defiler) {
            Set<Unit> enemies = gameState.getVisibleEnemyUnits();
            Unit nearestEnemy = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Unit enemyUnit : enemies) {
                if (!enemyUnit.isDetected()) continue;
                double d = unit.getDistance(enemyUnit);
                if (d < nearestDistance) {
                    nearestDistance = d;
                    nearestEnemy = enemyUnit;
                }
            }
            if (nearestEnemy != null) {
                managedUnit.setFightTarget(nearestEnemy);
            } else {
                managedUnit.setFightTarget(null);
                managedUnit.setRallyPoint(squad.getCenter());
                managedUnit.setRole(UnitRole.RALLY);
            }
            return;
        }
        List<Unit> enemyUnits = new ArrayList<>(gameState.getVisibleEnemyUnits());

        List<Unit> filtered = new ArrayList<>();
        for (Unit enemyUnit: enemyUnits) {
            if (unit.getType() == UnitType.Zerg_Lurker && !enemyUnit.isFlying() && enemyUnit.isDetected()) {
                filtered.add(enemyUnit);
                continue;
            }
            if (unit.canAttack(enemyUnit) && enemyUnit.isDetected() && 
                !Filter.isLowPriorityCombatTarget(enemyUnit.getType())) {
                filtered.add(enemyUnit);
            }
        }

        if (filtered.isEmpty()) {
            assignFallbackMovementTarget(managedUnit, squad);
            return;
        }

        filtered = filterByProximity(filtered, unit);

        if (gameState.isCannonRushed()) {
            Set<Unit> proxied = gameState.getObservedUnitTracker().getProxiedBuildings();
            List<Unit> proxiedTargets = filtered.stream()
                    .filter(proxied::contains)
                    .collect(Collectors.toList());
            if (!proxiedTargets.isEmpty()) {
                filtered = proxiedTargets;
            }
        }

        Unit bestTarget = TargetScorer.selectTarget(unit, filtered, managedUnit.fightTarget);
        if (bestTarget != null) {
            managedUnit.setFightTarget(bestTarget);
        }
    }

    /**
     * Keeps a fighter that has no attackable target moving toward the enemy.
     *
     * <p>The current movement target is held until it is reached; ManagedUnit clears it once the tile
     * is visible. pollScoutTarget() mutates scout assignment accounting and returns a different base
     * on every call, so polling it per frame makes fighters thrash between map corners.
     */
    private void assignFallbackMovementTarget(ManagedUnit managedUnit, Squad squad) {
        if (managedUnit.getMovementTargetPosition() != null) {
            return;
        }

        Position closestBuilding = closestKnownEnemyBuilding(squad.getCenter());
        if (closestBuilding != null) {
            managedUnit.setMovementTargetPosition(closestBuilding.toTilePosition());
            return;
        }

        Base enemyMain = gameState.getBaseData().getMainEnemyBase();
        if (enemyMain != null) {
            managedUnit.setMovementTargetPosition(enemyMain.getLocation());
            return;
        }

        managedUnit.setMovementTargetPosition(gameState.pollScoutTarget());
    }

    /**
     * @return closest last known enemy building position to the given position, or null if none are known
     */
    private Position closestKnownEnemyBuilding(Position from) {
        return closestPosition(from, gameState.getLastKnownPositionsOfBuildings());
    }

    private List<Unit> filterByProximity(List<Unit> candidates, Unit attacker) {
        List<Unit> nearby = new ArrayList<>();
        for (Unit enemy : candidates) {
            if (attacker.getDistance(enemy) <= TARGETING_RADIUS) {
                nearby.add(enemy);
            }
        }
        return nearby.isEmpty() ? candidates : nearby;
    }

    /**
     * Retrieves the largest fight squad or returns null if no fight squads exist.
     * @return Squad OR null
     */
    public Squad largestSquad() {
        if (fightSquads.isEmpty()) {
            return null;
        }
        List<Squad> sorted = fightSquads.stream().sorted().collect(Collectors.toList());
        return sorted.get(sorted.size() - 1);
    }

    public Set<ManagedUnit> getDisbandedUnits() {
        return disbanded;
    }

    /**
     * Assigns overlords to Hydralisk and Mutalisk squads when overlord speed is researched.
     * Prioritizes largest squads first and assigns one overlord per squad.
     * Returns overlords to the main squad if they're the only unit left.
     */
    private void assignOverlordsToSquads() {
        if (!gameState.getTechProgression().isOverlordSpeed()) {
            return;
        }

        returnLoneOverlords();

        List<ManagedUnit> availableOverlords = new ArrayList<>(overlords.getMembers());
        if (availableOverlords.isEmpty()) {
            return;
        }

        List<Squad> targetSquads = getHydraliskAndMutaliskSquads();
        if (targetSquads.isEmpty()) {
            return;
        }

        for (Squad squad : targetSquads) {
            if (availableOverlords.isEmpty()) {
                break;
            }

            if (squadHasOverlord(squad)) {
                continue;
            }

            ManagedUnit overlord = availableOverlords.remove(0);
            overlords.removeUnit(overlord);
            squad.addUnit(overlord);
            overlord.setRole(UnitRole.FIGHT);
        }
    }

    /**
     * Returns overlords to the main overlord squad if they are the only unit type in the squad.
     */
    private void returnLoneOverlords() {
        List<Squad> squadsToRemove = new ArrayList<>();
        
        for (Squad squad : fightSquads) {
            boolean allOverlords = true;
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getUnitType() != UnitType.Zerg_Overlord) {
                    allOverlords = false;
                    break;
                }
            }
            
            if (allOverlords && squad.size() > 0) {
                List<ManagedUnit> overlordsToRemove = new ArrayList<>(squad.getMembers());
                for (ManagedUnit overlord : overlordsToRemove) {
                    squad.removeUnit(overlord);
                    overlords.addUnit(overlord);
                    overlord.setRallyPoint(gameState.getSquadRallyPoint());
                    overlord.setRole(UnitRole.RETREAT);
                }
                squadsToRemove.add(squad);
            }
        }
        fightSquads.removeAll(squadsToRemove);
    }

    /**
     * Returns a list of Hydralisk and Mutalisk squads, sorted by size (largest first).
     */
    private List<Squad> getHydraliskAndMutaliskSquads() {
        return fightSquads.stream()
            .filter(squad -> {
                return squad.getCountOf(UnitType.Zerg_Mutalisk) > 0
                        || squad.getCountOf(UnitType.Zerg_Hydralisk) > 0;
            })
            .sorted((s1, s2) -> Integer.compare(s2.size(), s1.size()))
            .collect(Collectors.toList());
    }

    private Map<Squad, Double> getAdjacentSquads(Squad targetSquad, double maxRadius) {
        Map<Squad, Double> result = new HashMap<>();
        for (Squad squad : fightSquads) {
            if (squad == targetSquad) continue;
            double dist = targetSquad.distance(squad);
            if (dist <= maxRadius) {
                result.put(squad, dist);
            }
        }
        return result;
    }

    /**
     * Checks if a squad already has an overlord assigned to it.
     */
    private boolean squadHasOverlord(Squad squad) {
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getUnitType() == UnitType.Zerg_Overlord) {
                return true;
            }
        }
        return false;
    }
}
