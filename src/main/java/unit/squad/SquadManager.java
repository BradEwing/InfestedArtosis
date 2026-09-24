package unit.squad;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WeaponType;
import bwapi.WalkPosition;
import bwem.Base;
import bwem.CPPath;
import info.GameState;
import info.ScoutData;
import info.map.BaseArea;
import info.tracking.EnemyReachMemory;
import info.tracking.ObservedUnit;
import info.tracking.ObservedUnitTracker;
import info.tracking.PsiStormTracker;
import info.tracking.StrategyTracker;
import lombok.Getter;

import org.bk.ass.sim.Agent;
import org.bk.ass.sim.BWMirrorAgentFactory;
import org.bk.ass.sim.Simulator;
import telemetry.DecisionPath;
import telemetry.DefenseEvent;
import telemetry.RallyReason;
import telemetry.RallyRelease;
import telemetry.RunbyTelemetry;
import telemetry.RunbyTick;
import telemetry.SquadDecisions;
import telemetry.SquadLock;
import telemetry.TargetChoices;
import unit.managed.ManagedUnit;
import unit.squad.horizon.HorizonCombatSimulator;
import unit.managed.UnitRole;
import util.Arc;
import util.Filter;
import util.StaticDefenseZone;
import util.Vec2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.function.Predicate;
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

    private HashMap<Base, Integer> defenseAbandonedUntilFrame = new HashMap<>();

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

    /** Tuning value: air combat units a squad needs to move out against Protoss, Terran or an unknown race. */
    static final int AIR_MOVE_OUT_UNITS = 5;
    /** Tuning value: air combat units a squad needs to move out against Zerg. */
    static final int AIR_MOVE_OUT_UNITS_VS_ZERG = 2;
    /** Tuning value: Scourge a Scourge only squad needs to move out, against any race. */
    static final int SCOURGE_MOVE_OUT_UNITS = 2;

    private static final int RETREAT_VECTOR_MAGNITUDE = 192;
    private static final int COMBAT_SIM_DURATION_FRAMES = 150;
    private static final double DEFENSE_WIN_THRESHOLD = 0.50;
    private static final double SCV_RUSH_DEFENSE_CLEAR_THRESHOLD = 0.75;
    private static final int MERGE_CHECK_INTERVAL = 50;
    private static final int DEFENSE_SIM_RANGE = 256;
    private static final int CONTAINMENT_REEVALUATE_INTERVAL = 48;
    private static final int MAX_MOVE_OUT_THRESHOLD = 40;
    private static final int CONTAINMENT_TIMEOUT_FRAMES = 1400;
    private static final int CONTAINMENT_ENGAGE_RADIUS = 256;
    private static final int ARC_DEGREES = 90;
    private static final int ARC_RADIUS = 160;
    private static final int CONTAIN_DEFENSE_MARGIN = 32;
    private static final double REINFORCEMENT_RADIUS = 384.0;
    private static final int TARGETING_RADIUS = 256;
    public static final int GROUND_SPLIT_DISTANCE = 256;
    public static final int AIR_SPLIT_DISTANCE = 768;
    private static final int COMMITMENT_RELEASE_DISTANCE = 512;
    private static final int RUNBY_AREA_PROXIMITY_TILES = 4;
    private static final int RUNBY_AREA_TILES = 24;
    private static final int LIKELY_SPOT_SEARCH_TILES = 2;
    private static final int RUNBY_KILL_CREDIT_RADIUS = 64;

    private final Map<Base, RunbyTarget> runbyTargets = new HashMap<>();
    private Set<ManagedUnit> outrangedHits = new HashSet<>();

    private final ScoutChase scoutChase = new ScoutChase();

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
        rebuildScoutChase();
        evictIrradiatedUnits();
        updateIrradiatedUnits();
        assignOverlordsToSquads();

        int now = game.getFrameCount();
        outrangedHits = findOutrangedHits(now);
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
        evadeOutrangedHits(now);
    }

    /**
     * Members of every fight squad hit this frame by something they cannot answer, read before any squad decides,
     * so a containing squad and the hit unit both react on the frame the hit is seen. A member standing in an active
     * Psionic Storm or irradiated may be losing hit points to the effect, and its drop is not read as a hit.
     *
     * @param now current frame
     * @return the members with an outranged hit
     */
    private Set<ManagedUnit> findOutrangedHits(int now) {
        Set<ManagedUnit> hit = new HashSet<>();
        for (Squad squad : fightSquads) {
            for (ManagedUnit member : squad.getMembers()) {
                if (!member.wasHitOn(now) || gameState.isTakingNonWeaponDamage(member)) {
                    continue;
                }
                if (ManagedUnit.isOutrangedHit(member.getHitPointsBefore(), member.getUnit().getHitPoints(),
                        member.getRole(), member.hasEnemyWithinReach(ManagedUnit.MELEE_MARGIN))) {
                    hit.add(member);
                }
            }
        }
        return hit;
    }

    /**
     * Moves every member with an outranged hit this frame to the point, within a short ring around it, farthest
     * outside every zone that outranges it. The move is issued this frame, ahead of the unit's ready gate. A
     * burrowed member, or one whose type cannot move, is left to keep attacking from its role.
     *
     * @param now current frame
     */
    private void evadeOutrangedHits(int now) {
        if (outrangedHits.isEmpty()) {
            return;
        }
        List<StaticDefenseZone> threats = gameState.getGroundThreatZones(now);
        Set<WalkPosition> accessible = gameState.getGameMap().getAccessibleWalkPositions();
        int mapPixelWidth = game.mapWidth() * 32;
        int mapPixelHeight = game.mapHeight() * 32;
        Predicate<Position> allowed = point -> isWalkable(point, accessible, mapPixelWidth, mapPixelHeight);
        for (ManagedUnit member : outrangedHits) {
            if (!member.canStepOutNow()
                    || !ManagedUnit.evadesOutrangedHit(member.getRole(), member.isClosingOnTarget())) {
                continue;
            }
            UnitType type = member.getUnitType();
            List<StaticDefenseZone> zones = ContainmentPushback.outrangingZones(threats,
                    EnemyReachMemory.baseGroundRange(type));
            Position seek = member.getRole() == UnitRole.CONTAIN ? member.getContainPosition() : null;
            Position point = RunbyTargeting.findEvadePoint(member.getPosition(), zones,
                    containmentDefensePadding(Collections.singletonList(type)), allowed, seek);
            if (point != null) {
                member.evade(point, now);
            }
        }
    }

    private static boolean isWalkable(Position point, Set<WalkPosition> accessible, int mapPixelWidth,
                                      int mapPixelHeight) {
        if (point.getX() < 0 || point.getY() < 0 || point.getX() >= mapPixelWidth || point.getY() >= mapPixelHeight) {
            return false;
        }
        return accessible.isEmpty() || accessible.contains(new WalkPosition(point));
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

        List<Unit> uncapped = ScoutChase.withoutCappedScouts(filtered, enemy -> isCappedScoutFor(unit, enemy));
        if (uncapped.isEmpty() && !filtered.isEmpty()) {
            rallyToDefensePosition(managedUnit, gameState.defensePosition());
            return;
        }
        filtered = uncapped;
        if (filtered.isEmpty()) {
            scoutChase.release(unit.getID());
            managedUnit.setRole(UnitRole.FIGHT);
            Base enemyBase = gameState.getBaseData().getMainEnemyBase();
            if (enemyBase != null) {
                managedUnit.setMovementTargetPosition(enemyBase.getLocation());
            }
            return;
        }

        filtered = filterByProximity(filtered, unit::getDistance);

        TargetScorer.Selection selection = TargetScorer.selectTarget(unit, filtered, managedUnit.fightTarget);
        if (selection != null) {
            managedUnit.setRole(UnitRole.FIGHT);
            managedUnit.setFightTarget(selection.getTarget());
            recordScoutClaim(unit, selection.getTarget());
        }
    }

    /**
     * Re-evaluates the worker defence of a base, then pulls the gatherers it still needs.
     *
     * <p>Outside a cannon rush the defence is simulated with every assigned defender and every candidate. If
     * that full commitment loses, no candidate is pulled, every assigned defender is released and the base
     * may not pull again for {@link WorkerDefense#ABANDON_HOLD_FRAMES}. Otherwise candidates are added until
     * the defence clears the threat.
     *
     * @param base base to defend
     * @param candidates gatherers that may be pulled, in pull order
     * @param hostileUnits units threatening this base
     * @return gatherers pulled, to be removed from the WorkerManager, and defenders released, to be returned
     */
    public WorkerDefense.Outcome<ManagedUnit> assignGatherersToDefend(Base base, List<ManagedUnit> candidates,
                                                                      List<Unit> hostileUnits) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        if (gameState.isCannonRushed()) {
            return assignCannonRushDefenders(defenseSquad, candidates, hostileUnits);
        }

        int frame = game.getFrameCount();
        if (WorkerDefense.abandonHeld(defenseAbandonedUntilFrame.get(base), frame)) {
            return new WorkerDefense.Outcome<>(false, Collections.emptyList(), Collections.emptyList());
        }

        final double clearThreshold = gameState.getStrategyTracker().isDetectedStrategy("SCVRush")
                ? SCV_RUSH_DEFENSE_CLEAR_THRESHOLD
                : DEFENSE_WIN_THRESHOLD;
        List<DefenseSim> sims = new ArrayList<>();
        List<ManagedUnit> members = new ArrayList<>(defenseSquad.getMembers());
        WorkerDefense.Outcome<ManagedUnit> outcome = WorkerDefense.decide(members, candidates,
                defenders -> {
                    DefenseSim sim = simulateDefense(base, defenders, hostileUnits, DEFENSE_WIN_THRESHOLD);
                    sims.add(sim);
                    return sim.wins();
                },
                defenders -> simulateDefense(base, defenders, hostileUnits, clearThreshold).wins());
        DefenseSim fullCommitment = sims.isEmpty() ? null : sims.get(0);

        if (outcome.isAbandoned()) {
            releaseDefenders(defenseSquad);
            defenseAbandonedUntilFrame.put(base, frame + WorkerDefense.ABANDON_HOLD_FRAMES);
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.ABANDON, candidates.size(), 0,
                    outcome.getReleased().size(), fullCommitment);
            return outcome;
        }

        for (ManagedUnit gatherer : outcome.getPulled()) {
            defenseSquad.addUnit(gatherer);
            gatherer.setRole(UnitRole.DEFEND);
            assignDefenderTarget(gatherer, hostileUnits);
        }
        if (!outcome.getPulled().isEmpty()) {
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.PULL, candidates.size(),
                    outcome.getPulled().size(), 0, fullCommitment);
        }
        return outcome;
    }

    private WorkerDefense.Outcome<ManagedUnit> assignCannonRushDefenders(Squad defenseSquad,
                                                                         List<ManagedUnit> candidates,
                                                                         List<Unit> hostileUnits) {
        List<ManagedUnit> pulled = new ArrayList<>();
        int totalGatherers = gameState.getGatherersAssignedToBase().values().stream()
                .mapToInt(HashSet::size)
                .sum();
        int existingDefenders = defenseSquads.values().stream()
                .mapToInt(s -> s.getMembers().size())
                .sum();
        int maxToAssign = totalGatherers / 2 - existingDefenders;
        for (ManagedUnit gatherer : candidates) {
            if (pulled.size() >= maxToAssign) {
                break;
            }
            defenseSquad.addUnit(gatherer);
            gatherer.setRole(UnitRole.DEFEND);
            assignDefenderTarget(gatherer, hostileUnits);
            pulled.add(gatherer);
        }
        if (!pulled.isEmpty()) {
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.PULL, candidates.size(), pulled.size(), 0,
                    null);
        }
        return new WorkerDefense.Outcome<>(false, pulled, Collections.emptyList());
    }

    public List<ManagedUnit> disbandDefendSquad(Base base) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        List<ManagedUnit> reassignedDefenders = releaseDefenders(defenseSquad);
        if (!reassignedDefenders.isEmpty()) {
            SquadDecisions.defenseEvaluated(defenseSquad, DefenseEvent.RELEASE, 0, 0, reassignedDefenders.size(),
                    null);
        }
        return reassignedDefenders;
    }

    private List<ManagedUnit> releaseDefenders(Squad defenseSquad) {
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
            if (squad.getStatus() == SquadStatus.CONTAIN) {
                endContainment(squad);
            }
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
                if (!mayMerge(squad1.getStatus()) || !mayMerge(squad2.getStatus())) continue;
                boolean bothGround = squad1.isGroundSquad() && squad2.isGroundSquad();
                boolean bothAir = squad1.isAirSquad() && squad2.isAirSquad();
                if (!bothGround && !bothAir) continue;
                boolean scourge1 = holdsOnlyScourge(squad1.getComposition());
                boolean scourge2 = holdsOnlyScourge(squad2.getComposition());
                if (bothAir && !mayMergeAirSquads(scourge1, scourge2)) continue;
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
            SquadDecisions.pathTaken(newSquad, DecisionPath.MERGE_INHERIT);
            for (Squad mergingSquad: mergeSet) {
                if (mergeEndsContainment(mergingSquad.getStatus(), newSquad.getStatus())) {
                    endContainment(mergingSquad);
                }
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
            if (!maySplit(squad.getStatus())) continue;
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
            SquadDecisions.pathTaken(child, DecisionPath.SPLIT_INHERIT);
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
     * Whether a merge closes a source squad's contain episode: the source was containing and the merged squad is
     * not. A merge that stays in CONTAIN carries the episode on in the merged squad.
     *
     * @param source status of a squad being merged
     * @param merged status the merged squad took
     * @return true when the source's episode ends with the merge
     */
    static boolean mergeEndsContainment(SquadStatus source, SquadStatus merged) {
        return source == SquadStatus.CONTAIN && merged != SquadStatus.CONTAIN;
    }

    /**
     * Whether a squad holding a status may merge with a neighbour. A runby squad is kept apart: a merge would
     * fold a squad at home into the enemy base, or hand the runby to a squad that recalls it.
     *
     * @param status the squad's status
     * @return true when the squad may merge
     */
    static boolean mayMerge(SquadStatus status) {
        return status != SquadStatus.RUNBY;
    }

    /**
     * Whether a squad holding a status may split off its outliers. A runby squad spreads out among the workers
     * on purpose, so it is never split.
     *
     * @param status the squad's status
     * @return true when the squad may split
     */
    static boolean maySplit(SquadStatus status) {
        return status != SquadStatus.CONTAIN && status != SquadStatus.RALLY && status != SquadStatus.RETREAT
                && status != SquadStatus.RUNBY;
    }

    /**
     * Whether a new or re-homed unit may join a squad holding a status. A runby squad takes no
     * reinforcements: joining one would re-simulate it and overwrite its status.
     *
     * @param status the squad's status
     * @return true when the squad may take the unit
     */
    static boolean mayJoin(SquadStatus status) {
        return status != SquadStatus.RUNBY;
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
        if (squad.isAirSquad()) {
            Map<UnitType, Integer> composition = new HashMap<>();
            for (ManagedUnit managedUnit : units) {
                composition.merge(managedUnit.getUnitType(), 1, Integer::sum);
            }
            return airMoveOutUnits(composition);
        }

        int supply = 0;
        for (ManagedUnit managedUnit : units) {
            supply += managedUnit.getUnitType().supplyRequired();
        }
        return supply;
    }

    private int squadStrength(Squad squad) {
        if (squad.isAirSquad()) {
            return airMoveOutUnits(squad.getComposition());
        }
        return squad.getSupply();
    }

    /**
     * Counts the air combat units in a composition, the strength an air squad's move out threshold is measured in.
     * Overlords escorting the squad are not counted.
     *
     * @param composition unit counts by type
     * @return number of Mutalisks, Scourge, Guardians and Devourers
     */
    static int airMoveOutUnits(Map<UnitType, Integer> composition) {
        int units = 0;
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            if (AIR_SQUAD_TYPES.contains(entry.getKey())) {
                units += entry.getValue();
            }
        }
        return units;
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

    private void rallySquad(Squad squad, RallyReason reason) {
        boolean defilersOnly = squad.isGroundSquad() && squad.hasOnly(UnitType.Zerg_Defiler);
        SquadDecisions.rallied(squad, rallyReasonFor(defilersOnly, reason));
        SquadDecisions.pathTaken(squad, DecisionPath.RALLY);
        squad.setStatus(SquadStatus.RALLY);
        squad.clearCommitment();
        Position rallyPoint = gameState.getSquadRallyPoint();
        for (ManagedUnit managedUnit: squad.getMembers()) {
            managedUnit.setRallyPoint(rallyPoint);
            managedUnit.setRole(UnitRole.RALLY);
        }
    }

    /**
     * Names why a squad is at the rally point, from the composition rather than the branch alone.
     *
     * <p>A ground squad of Defilers only reaches the rally point two ways. SquadManager has a
     * branch that rallies it instead of simulating a fight, but that branch sits inside
     * {@link #simulateFightSquad}, which a squad under the move out threshold never reaches:
     * {@link #chooseSquadAction} returns RALLY first and {@link #evaluateSquadRole} returns. The
     * ground threshold carries a Lurker term and no Defiler term, so which of the two fires is a
     * question of the squad's supply against a threshold that moves with the matchup.
     *
     * <p>Reading the branch alone therefore filed the same squad under BELOW_MOVE_OUT on most
     * frames, which is the bucket an analyst keeps. Composing the reason from the composition keeps
     * the Defiler episodes separable however they were rallied, which is the whole point of
     * recording the reason.
     *
     * @param defilersOnly true for a ground squad whose composition is Defilers and nothing else
     * @param branchReason reason the calling branch would have recorded
     * @return the reason to log
     */
    static RallyReason rallyReasonFor(boolean defilersOnly, RallyReason branchReason) {
        return defilersOnly ? RallyReason.DEFILER_ONLY : branchReason;
    }

    /**
     * Determines whether a squad should continue or change its current role.
     * @param squad Squad to evaluate
     */
    private void evaluateSquadRole(Squad squad) {
        if (squad.getStatus() == SquadStatus.RUNBY) {
            evaluateRunbySquad(squad);
            return;
        }

        final boolean closeThreats = !enemyUnitsNearSquad(squad).isEmpty();

        SquadStatus squadStatus = squad.getStatus();
        if (squadStatus == SquadStatus.CONTAIN) {
            clearCombatSimSnapshot(squad);
            evaluateContainingSquad(squad);
            return;
        }

        int strength = squadStrength(squad);
        int moveOutThreshold = calculateMoveOutThreshold(squad);
        SquadDecisions.moveOutEvaluated(squad, moveOutThreshold, strength);
        SquadAction action = chooseSquadAction(closeThreats, strength, moveOutThreshold,
                squadStatus, squad.isCommitted(), distanceFromRallyPoint(squad));

        if (squadStatus == SquadStatus.RALLY) {
            SquadDecisions.rallyReleased(squad, releaseFor(action, closeThreats));
        }

        if (action == SquadAction.RALLY) {
            clearCombatSimSnapshot(squad);
            rallySquad(squad, RallyReason.BELOW_MOVE_OUT);
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
     * @param squadStrength supply of a ground squad, or air combat unit count of an air squad
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

    /**
     * Names the term that let a rallying squad stop rallying, for the row that closes the episode.
     *
     * <p>Reads the branch {@link #chooseSquadAction} already picked rather than re-testing its
     * thresholds, so the two cannot drift apart. Only three of its branches are reachable from
     * RALLY: the FIGHT branch requires the squad to already be fighting, which leaves close threats,
     * the move out threshold, and a committed squad that has walked past the release distance.
     *
     * @param action branch chooseSquadAction returned this frame
     * @param closeThreats true when enemies sit inside the squad detection radius
     * @return the release, or NONE while the squad keeps rallying
     */
    static RallyRelease releaseFor(SquadAction action, boolean closeThreats) {
        if (action == SquadAction.RALLY) {
            return RallyRelease.NONE;
        }
        if (closeThreats) {
            return RallyRelease.CLOSE_THREATS;
        }
        if (action == SquadAction.LAUNCH) {
            return RallyRelease.MOVE_OUT_THRESHOLD;
        }
        return RallyRelease.COMMITTED_DOWNFIELD;
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
        return airMoveOutThreshold(holdsOnlyScourge(squad.getComposition()), gameState.getOpponentRace());
    }

    /**
     * Whether a composition's air combat units are all Scourge. Escorting Overlords are ignored, and a
     * composition with no air combat units is not a Scourge squad.
     *
     * @param composition unit counts by type
     * @return true when the composition holds Scourge and no other air combat unit
     */
    static boolean holdsOnlyScourge(Map<UnitType, Integer> composition) {
        int units = airMoveOutUnits(composition);
        return units > 0 && units == composition.getOrDefault(UnitType.Zerg_Scourge, 0);
    }

    /**
     * Whether an air unit may join an air squad. Scourge keep to squads of Scourge so a pair moves out on
     * {@link #SCOURGE_MOVE_OUT_UNITS}, and every other air unit keeps out of them.
     *
     * @param type the joining unit's type
     * @param squadHoldsOnlyScourge true when the squad's air combat units are all Scourge
     * @return true when the unit may join the squad
     */
    static boolean mayJoinAirSquad(UnitType type, boolean squadHoldsOnlyScourge) {
        boolean scourge = type == UnitType.Zerg_Scourge;
        return scourge == squadHoldsOnlyScourge;
    }

    /**
     * Whether two air squads may merge. A Scourge squad merges only with another Scourge squad.
     *
     * @param firstHoldsOnlyScourge true when the first squad's air combat units are all Scourge
     * @param secondHoldsOnlyScourge true when the second squad's air combat units are all Scourge
     * @return true when the squads may merge
     */
    static boolean mayMergeAirSquads(boolean firstHoldsOnlyScourge, boolean secondHoldsOnlyScourge) {
        return firstHoldsOnlyScourge == secondHoldsOnlyScourge;
    }

    /**
     * Air combat units an air squad needs before it is cleared to move out, compared against
     * {@link #airMoveOutUnits}.
     *
     * @param scourgeOnly true when the squad holds Scourge and nothing else
     * @param opponentRace the opponent's race
     * @return threshold in units
     */
    static int airMoveOutThreshold(boolean scourgeOnly, Race opponentRace) {
        if (scourgeOnly) {
            return SCOURGE_MOVE_OUT_UNITS;
        }
        if (opponentRace == Race.Zerg) {
            return AIR_MOVE_OUT_UNITS_VS_ZERG;
        }
        return AIR_MOVE_OUT_UNITS;
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

    /**
     * Runs one tick of a fight squad that is not holding a containment arc.
     *
     * <p>The composition and hazard branches answer first, before anything is measured: a Lurker
     * only squad, a Defiler only squad, and a squad standing in a psionic storm. Every other status
     * is decided at or below the lock reads, so the retreat lock gates it. A branch placed above
     * those reads returns before the simulator runs and neither lock can see it.
     *
     * <p>A squad with nothing detected anywhere still attacks: the sim has no enemy to weigh, so it
     * returns ADVANCE, and the fighters take the remembered enemy building through
     * {@link #assignFallbackMovementTarget}.
     *
     * @param squad fight squad to tick
     */
    private void simulateFightSquad(Squad squad) {
        HashSet<ManagedUnit> managedFighters = squad.getMembers();

        if (squad.isGroundSquad() && squad.hasOnly(UnitType.Zerg_Lurker)) {
            squad.setStatus(SquadStatus.FIGHT);
            SquadDecisions.pathTaken(squad, DecisionPath.LURKER_ONLY);
            assignFightTargets(squad, managedFighters, false);
            return;
        }

        if (squad.isGroundSquad() && squad.hasOnly(UnitType.Zerg_Defiler)) {
            rallySquad(squad, RallyReason.DEFILER_ONLY);
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
                SquadDecisions.pathTaken(squad, DecisionPath.STORM_RETREAT);
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

        boolean noVisionMarch = enemyUnits.isEmpty() && !enemyBuildingPositions.isEmpty();

        int now = game.getFrameCount();
        boolean retreatLocked = squad.isRetreatLocked(now);
        boolean fightLocked = squad.isFightLocked(now);

        Map<Squad, Double> adjacentSquads = getAdjacentSquads(squad, REINFORCEMENT_RADIUS);
        CombatSimulator.CombatResult result = squad.getCombatSimulator()
                .evaluate(squad, adjacentSquads, gameState);
        SquadDecisions.simEvaluated(squad, result, retreatLocked, fightLocked);
        SquadDecisions.pathTaken(squad, requestPath(noVisionMarch, result));

        HorizonCombatSimulator.DebugSnapshot snapshot = lastSnapshot(squad);
        boolean enemyMeasured = snapshot == null || snapshot.isEnemyMeasured();
        boolean threatBeyondRadius = snapshot != null && snapshot.isThreatBeyondRadius();
        double ratio = snapshot != null ? snapshot.getOverallRatio() : 0;
        double engageThreshold = snapshot != null ? snapshot.getEngageThreshold() : 0;

        if (squad.getStatus() == SquadStatus.RETREAT && retreatLocked) {
            SquadDecisions.lockSuppressed(squad, SquadLock.RETREAT);
            SquadDecisions.pathTaken(squad, DecisionPath.RETREAT_LOCK);
            assignRetreatTargets(squad, managedFighters);
            return;
        }
        if (squad.getStatus() == SquadStatus.FIGHT
                && fightLockHolds(fightLocked, result, enemyMeasured, ratio, engageThreshold)) {
            SquadDecisions.lockSuppressed(squad, SquadLock.FIGHT);
            SquadDecisions.pathTaken(squad, DecisionPath.FIGHT_LOCK);
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

    /**
     * Arms or renews the fight lock on an ENGAGE verdict.
     *
     * <p>A retreat lock blocks the fight lock, and so does a squad that has lost supply since the
     * lock it would be renewing was armed (see {@link Squad#canRenewFightLock}).
     *
     * @param squad squad whose verdict this is
     * @param result this frame's combat sim verdict
     * @param retreatLocked whether the squad's retreat lock is currently active
     * @param currentFrame current frame
     */
    static void updateFightLock(Squad squad, CombatSimulator.CombatResult result,
                                boolean retreatLocked, int currentFrame) {
        if (result == CombatSimulator.CombatResult.ENGAGE && !retreatLocked
                && squad.canRenewFightLock(currentFrame)) {
            squad.startFightLock(currentFrame);
        }
    }

    /**
     * Whether an active fight lock still holds against this frame's verdict.
     *
     * <p>The lock holds a squad in FIGHT against ENGAGE and ADVANCE verdicts, and breaks for a
     * RETREAT that was measured against a real enemy below the engage threshold the sim judged it
     * by. Every RETREAT from the Horizon sim is measured and below that threshold by construction,
     * so the lock never discards one. A simulator that leaves no snapshot reports a ratio and a
     * threshold of 0, which holds.
     *
     * @param fightLocked whether the squad's fight lock is currently active
     * @param result this frame's combat sim verdict
     * @param enemyMeasured whether the sim measured a real enemy this frame
     * @param ratio the sim's overall strength ratio this frame
     * @param engageThreshold the engage threshold the sim judged this frame's ratio against
     * @return true if the lock should still suppress this frame's verdict
     */
    static boolean fightLockHolds(boolean fightLocked, CombatSimulator.CombatResult result,
                                  boolean enemyMeasured, double ratio, double engageThreshold) {
        if (!fightLocked) return false;
        if (result != CombatSimulator.CombatResult.RETREAT) return true;
        return !enemyMeasured || ratio >= engageThreshold;
    }

    /**
     * Names the branch asking for this frame's status, read before either lock is consulted.
     *
     * <p>A squad with no detected enemy anywhere cannot be given a fight target by
     * {@link #assignEnemyTarget}, so every member falls through to the remembered building march
     * whatever the verdict says. The march therefore outranks the verdict as the description of
     * what the squad is doing, and the sim columns on the row still carry the verdict itself.
     *
     * @param noVisionMarch true when no enemy unit is detected and an enemy building is remembered
     * @param result this frame's combat sim verdict
     * @return the branch the row should name
     */
    static DecisionPath requestPath(boolean noVisionMarch, CombatSimulator.CombatResult result) {
        if (noVisionMarch) {
            return DecisionPath.NO_VISION_MARCH;
        }
        if (result == null) {
            return DecisionPath.NONE;
        }
        switch (result) {
            case ADVANCE:
                return DecisionPath.SIM_ADVANCE;
            case ENGAGE:
                return DecisionPath.SIM_ENGAGE;
            case RETREAT:
                return DecisionPath.SIM_RETREAT;
            default:
                return DecisionPath.NONE;
        }
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
            rallySquad(squad, RallyReason.HOLD);
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
        boolean underAttack = baseThreatensContainment();
        boolean shouldContain = containmentEvaluator.shouldContain(squad);
        boolean canBreak = shouldContain && containmentEvaluator.canBreakContainment(fightSquads);
        boolean entered = mayEnterContainment(underAttack, shouldContain, canBreak) && enterContainment(squad);
        SquadDecisions.containmentEvaluated(squad, shouldContain, canBreak, entered);
        return entered;
    }

    /**
     * Whether a squad may take a containment arc.
     *
     * <p>A contain is never entered while a base is under attack. A containing squad breaks on that threat on the
     * next frame, so an arc taken under it is dropped again and re-taken on the following sim RETREAT, and the
     * squad alternates CONTAIN and FIGHT instead of retreating.
     *
     * @param basesUnderAttack true when a combat unit threatens one of our bases, see {@link #threatensContainment}
     * @param shouldContain true when containment applies to the squad
     * @param canBreak true when the strength gate clears the army to push in
     * @return true when the squad may enter containment
     */
    static boolean mayEnterContainment(boolean basesUnderAttack, boolean shouldContain, boolean canBreak) {
        return !basesUnderAttack && shouldContain && !canBreak;
    }

    private boolean enterContainment(Squad squad) {
        Arc arc = containmentArc(squad);
        if (arc == null) return false;
        squad.setStatus(SquadStatus.CONTAIN);
        SquadDecisions.pathTaken(squad, DecisionPath.CONTAIN_ENTER);
        squad.startContainLock(game.getFrameCount());
        assignContainmentPositions(squad, arc);
        return true;
    }

    /**
     * Verdicts available to a squad that is already holding a containment arc.
     */
    enum ContainmentVerdict {
        BREAK_ALL,
        RETREAT,
        PUSH_BACK,
        HOLD,
        REPOSITION
    }

    /**
     * Whether a member of a containing squad was hit this frame by something it cannot answer, and if so whether
     * any arc point on the choke is left out of every known reach.
     */
    enum OutrangedHit {
        NONE,
        ARC_KEPT,
        ARC_LOST
    }

    /**
     * Picks what a squad holding a containment arc does this frame.
     *
     * <p>A containing squad always sits within the squad detection radius of the units it contains, so mere
     * proximity carries no information and never ends an episode. Only the strength gate, the timeout, a base
     * under attack, attrition, losing every arc point to outranging fire, or containment ceasing to apply end
     * one. An enemy that has reached the arc holds the squad in place instead of moving it: each unit already
     * returns fire inside its own weapon range, so the squad trades on the line rather than charging a position it
     * has been measured as unable to break.
     *
     * <p>A member hit by something it cannot answer moves the arc out of every known reach, overriding both the
     * throttle and an enemy on the arc; with no arc point left out of reach the squad retreats.
     *
     * <p>Bases under attack, attrition, an outranged hit and a lost arc outrank the re-evaluation throttle and are
     * the only verdicts reachable on a throttled frame. A squad being ground down, hit from out of its reach, or with
     * nowhere left to stand out of reach, acts on the frame it happens rather than at the next re-evaluation tick.
     *
     * <p>Only a base under attack and the strength gate move the whole army; they are the two signals that are
     * true for every squad at once. A squad that has run out its own containment clock disengages by itself
     * rather than committing squads whose gate has not fired.
     *
     * @param basesUnderAttack true when a combat unit threatens one of our bases, see {@link #threatensContainment}
     * @param bleeding true when the squad is losing supply within the attrition window while killing little
     * @param outrangedHit whether a member was hit this frame with no enemy in its own range, and whether an arc
     *     point on the choke stays out of reach of every enemy that outranges the squad
     * @param throttled true when the contain lock holds and this frame is not a re-evaluation tick
     * @param engaged true when a mobile enemy is within contact range of a member
     * @param timedOut true when the episode has run past the containment timeout
     * @param canBreak true when the strength gate clears the army to push in
     * @param shouldContain true when containment still applies to this squad
     * @return verdict for this frame
     */
    static ContainmentVerdict containmentVerdict(boolean basesUnderAttack, boolean bleeding,
                                                 OutrangedHit outrangedHit, boolean throttled, boolean engaged,
                                                 boolean timedOut, boolean canBreak, boolean shouldContain) {
        if (basesUnderAttack) {
            return ContainmentVerdict.BREAK_ALL;
        }
        if (bleeding || outrangedHit == OutrangedHit.ARC_LOST) {
            return ContainmentVerdict.RETREAT;
        }
        if (outrangedHit == OutrangedHit.ARC_KEPT) {
            return ContainmentVerdict.PUSH_BACK;
        }
        if (throttled) {
            return ContainmentVerdict.HOLD;
        }
        if (canBreak) {
            return ContainmentVerdict.BREAK_ALL;
        }
        if (timedOut) {
            return ContainmentVerdict.RETREAT;
        }
        if (!shouldContain) {
            return ContainmentVerdict.RETREAT;
        }
        if (engaged) {
            return ContainmentVerdict.HOLD;
        }
        return ContainmentVerdict.REPOSITION;
    }

    private void evaluateContainingSquad(Squad squad) {
        int now = game.getFrameCount();
        if (now % RunbyEvaluator.RUNBY_TICK == 0 && tryEnterRunby(squad, now)) {
            return;
        }
        HashSet<ManagedUnit> members = squad.getMembers();

        boolean basesUnderAttack = baseThreatensContainment();
        boolean bleeding = !basesUnderAttack && squad.getContainmentAttrition().isBleeding(now, squad.getSupply());
        boolean outrangedHit = !basesUnderAttack && !bleeding && hasOutrangedHit(squad);
        List<StaticDefenseZone> zones = outrangedHit ? containmentZones(squad, now) : Collections.emptyList();
        Arc underFire = outrangedHit ? arcUnderFire(squad, zones) : null;
        boolean arcLost = outrangedHit && underFire == null;
        OutrangedHit hit = outrangedHitVerdict(outrangedHit, arcLost);
        boolean throttled = isContainmentThrottled(squad, now);
        boolean evaluate = !basesUnderAttack && !bleeding && !outrangedHit && !throttled;
        boolean timedOut = evaluate && containmentTimedOut(squad, now);
        boolean canBreak = evaluate && containmentEvaluator.canBreakContainment(fightSquads);
        boolean shouldContain = !evaluate || containmentEvaluator.shouldContain(squad);
        boolean engaged = evaluate && enemiesOnContainmentArc(squad);

        SquadDecisions.outrangedHit(squad, outrangedHit);
        ContainmentVerdict verdict = containmentVerdict(basesUnderAttack, bleeding, hit, throttled, engaged,
                timedOut, canBreak, shouldContain);

        switch (verdict) {
            case BREAK_ALL:
                breakAllContainment(now);
                break;
            case RETREAT:
                retreatFromContainment(squad, members, now, containmentExitPath(bleeding, arcLost));
                break;
            case PUSH_BACK:
                pushBackContainingSquad(squad, zones, underFire);
                break;
            case REPOSITION:
                repositionContainingSquad(squad, members, now);
                break;
            default:
                break;
        }
    }

    /**
     * Names the branch that sent a containing squad back, so attrition and outranging exits are separable from the
     * timeout and the size floor.
     *
     * @param bleeding true when the attrition rule fired
     * @param arcLost true when no arc point stayed out of reach of an outranging enemy
     * @return the decision path of the retreat
     */
    static DecisionPath containmentExitPath(boolean bleeding, boolean arcLost) {
        if (bleeding) {
            return DecisionPath.CONTAIN_ATTRITION;
        }
        if (arcLost) {
            return DecisionPath.CONTAIN_OUTRANGED;
        }
        return DecisionPath.CONTAIN_RETREAT;
    }

    private void retreatFromContainment(Squad squad, HashSet<ManagedUnit> members, int now, DecisionPath path) {
        endContainment(squad);
        squad.setStatus(SquadStatus.RETREAT);
        SquadDecisions.pathTaken(squad, path);
        assignRetreatTargets(squad, members);
        squad.startRetreatLock(now);
    }

    private void endContainment(Squad squad) {
        SquadDecisions.containmentEnded(squad, squad.getContainmentAttrition().getTotalLost());
        squad.clearContainStart();
    }

    /**
     * Folds an outranged hit and whether the recomputed arc kept a point into one verdict input.
     *
     * @param outrangedHit true when a member was hit this frame with no enemy in its own range
     * @param arcLost true when no arc point on the choke stays out of every known reach
     * @return NONE without a hit, else ARC_KEPT or ARC_LOST
     */
    static OutrangedHit outrangedHitVerdict(boolean outrangedHit, boolean arcLost) {
        if (!outrangedHit) {
            return OutrangedHit.NONE;
        }
        return arcLost ? OutrangedHit.ARC_LOST : OutrangedHit.ARC_KEPT;
    }

    private boolean hasOutrangedHit(Squad squad) {
        for (ManagedUnit member : squad.getMembers()) {
            if (outrangedHits.contains(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The arc a squad hit from out of its reach holds next: its current arc recomputed against every zone that
     * outranges it, at learned reach, and pushed back when that loses points.
     *
     * @param squad containing squad
     * @param zones zones the arc must stay out of
     * @return the arc, or null when no point on the choke is left clear
     */
    private Arc arcUnderFire(Squad squad, List<StaticDefenseZone> zones) {
        Arc current = squad.getContainmentArc();
        if (current == null || current.isEmpty()) {
            return containmentArc(squad, zones);
        }
        return ContainmentPushback.recompute(current, zones, containmentDefensePadding(squad.getComposition().keySet()),
                gameState.getGameMap().getAccessibleWalkPositions(), game.mapWidth() * 32, game.mapHeight() * 32);
    }

    /**
     * Puts a squad hit from out of its reach on the recomputed arc, reassigning every member, not only the one that
     * was hit, and reports it on every hit, with no member moved when the recomputed arc left every point in place.
     *
     * @param squad containing squad
     * @param zones zones the arc was recomputed against
     * @param arc recomputed arc
     */
    private void pushBackContainingSquad(Squad squad, List<StaticDefenseZone> zones, Arc arc) {
        Arc current = squad.getContainmentArc();
        Position from = current == null ? null : current.getMidpoint();
        UnitType enemyType = current == null ? null
                : ContainmentPushback.coveringType(current, zones,
                containmentDefensePadding(squad.getComposition().keySet()));
        squad.setContainRadius(Math.max(squad.getContainRadius(), arc.getRadius()));
        int moved = assignContainmentPositions(squad, arc);
        SquadDecisions.containmentPushedBack(squad, from, arc.getMidpoint(), enemyType, moved);
    }

    /**
     * Moves a containing squad onto a freshly computed arc, or retreats it when no arc point is left clear of
     * enemy static defence.
     *
     * @param squad containing squad
     * @param members squad members
     * @param now current frame
     */
    private void repositionContainingSquad(Squad squad, HashSet<ManagedUnit> members, int now) {
        Arc arc = containmentArc(squad);
        if (arc == null) {
            retreatFromContainment(squad, members, now, DecisionPath.CONTAIN_RETREAT);
            return;
        }
        assignContainmentPositions(squad, arc);
    }

    /**
     * Reports whether the contain lock suppresses re-evaluation this frame.
     *
     * <p>The re-evaluation phase is anchored to the frame the episode started rather than to the global frame
     * count, so a squad that takes an arc is throttled for a full interval whatever frame it entered on.
     *
     * @param squad containing squad
     * @param now current frame
     * @return true when the squad keeps its arc without further checks
     */
    static boolean isContainmentThrottled(Squad squad, int now) {
        if (!squad.isContainLocked(now)) {
            return false;
        }
        int elapsed = now - squad.getContainStartFrame();
        return elapsed <= 0 || elapsed % CONTAINMENT_REEVALUATE_INTERVAL != 0;
    }

    private boolean containmentTimedOut(Squad squad, int now) {
        return squad.getContainStartFrame() > 0
                && now - squad.getContainStartFrame() >= CONTAINMENT_TIMEOUT_FRAMES;
    }

    /**
     * Reports whether an enemy has closed onto the arc the squad is holding.
     *
     * <p>Distance is measured from each member rather than from the squad center, so an enemy near one flank
     * reads the same as one in the middle of the squad. Only enemies that can move count: a sieged tank or a
     * static defence emplacement never closed onto anything, and treating one as contact would send the squad
     * into the position it is holding a line against.
     *
     * @param squad containing squad
     * @return true when an enemy that could contest the arc is within contact range of any member
     */
    private boolean enemiesOnContainmentArc(Squad squad) {
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            if (!canPressTheArc(enemy)) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                Unit memberUnit = member.getUnit();
                if (memberUnit.getDistance(enemy) > CONTAINMENT_ENGAGE_RADIUS) {
                    continue;
                }
                if (memberUnit.canAttack(enemy)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether an enemy is the kind of unit that takes ground away from a containment arc: one that can move to
     * it and shoot the ground squad holding it. A drifting overlord or a passing worker is neither.
     *
     * @param enemy visible enemy unit
     * @return true when the unit could contest the arc
     */
    private boolean canPressTheArc(Unit enemy) {
        UnitType type = enemy.getType();
        return type.canMove() && type.groundWeapon() != WeaponType.None;
    }

    private void breakAllContainment(int now) {
        for (Squad s : fightSquads) {
            if (s.getStatus() == SquadStatus.CONTAIN) {
                endContainment(s);
                s.setStatus(SquadStatus.FIGHT);
                SquadDecisions.pathTaken(s, DecisionPath.CONTAIN_BREAK);
                assignFightTargets(s, s.getMembers(), true);
                s.startFightLock(now);
            }
        }
    }

    private boolean basesUnderAttack() {
        Set<Base> morphingBases = gameState.getBaseData().morphingBases();
        Set<Base> heldBases = gameState.getBaseData().getMyBases();
        boolean cannonRushed = gameState.isCannonRushed();
        for (Map.Entry<Base, HashSet<Unit>> entry : gameState.getBaseToThreatLookup().entrySet()) {
            Base base = entry.getKey();
            boolean morphingOnly = !cannonRushed && morphingBases.contains(base) && !heldBases.contains(base);
            List<UnitType> threatTypes = entry.getValue().stream().map(Unit::getType).collect(Collectors.toList());
            if (baseUnderAttack(morphingOnly, threatTypes)) {
                return true;
            }
        }
        return false;
    }

    private boolean baseThreatensContainment() {
        return threatensContainment(gameState.getBaseToThreatLookup().values().stream()
                .flatMap(Collection::stream)
                .map(Unit::getType)
                .collect(Collectors.toList()));
    }

    /**
     * Whether the threats tracked at our bases put a base under attack for containment purposes. Entry and the
     * break both read this one predicate, so a squad is never let onto an arc by a threat that would pull it off
     * again, and a scout circling a base, visible or last known, neither blocks a contain nor breaks one.
     *
     * @param threatTypes types of every enemy unit tracked as a threat to one of our bases
     * @return true when any of them is a combat threat
     */
    static boolean threatensContainment(Collection<UnitType> threatTypes) {
        return threatTypes.stream().anyMatch(SquadManager::isCombatThreat);
    }

    /**
     * Whether a base threat of this type is one a containing squad leaves its arc for: a mobile unit that can
     * fight, on the ground or in the air. A scouting worker, an Overlord, an Observer or a building is not.
     *
     * @param type type of the enemy unit tracked as a threat to one of our bases
     * @return true when the threat ends contains and refuses new ones
     */
    static boolean isCombatThreat(UnitType type) {
        return Filter.isMobileGroundCombatUnit(type) || Filter.isAirCombatUnit(type);
    }

    /**
     * Returns true if a base's tracked threats count as an attack. Any threat does at a base we hold. At a base
     * where our hatchery is still morphing only a mobile ground combat unit does, so a scouting worker or
     * Overlord watching the morph does not break contains or hold advances.
     *
     * @param morphingOnly true if our only hatchery at the base is still morphing
     * @param threatTypes types of the enemy units tracked as threats to the base
     */
    static boolean baseUnderAttack(boolean morphingOnly, Collection<UnitType> threatTypes) {
        if (!morphingOnly) {
            return !threatTypes.isEmpty();
        }
        return threatTypes.stream().anyMatch(Filter::isMobileGroundCombatUnit);
    }

    /**
     * Offers a containing squad a runby into the base it contains.
     *
     * <p>Runs ahead of every containment exit, a base under attack included: a contain that has lost a ling or
     * two, or whose army is needed at home, still runs by when the gates pass, and trades bases instead of
     * walking home. The cheap gates are read first so the target base is only resolved for a squad that could go.
     *
     * @param squad containing squad
     * @param now current frame
     * @return true when the squad entered RUNBY
     */
    private boolean tryEnterRunby(Squad squad, int now) {
        boolean zerglingsOnly = squad.hasOnly(UnitType.Zerg_Zergling);
        boolean metabolicBoost = gameState.getTechProgression().isMetabolicBoost();
        Base base = null;
        RunbyTarget target = null;
        if (zerglingsOnly && metabolicBoost && squad.size() >= RunbyEvaluator.MIN_LINGS) {
            base = runbyBaseNear(squad.getCenter(), Collections.emptySet());
            target = base == null ? null : runbyTarget(base);
        }
        RunbyView view = target == null ? new RunbyView() : runbyView(now, target.area);
        Position anchor = target == null ? null : target.anchor;
        RunbyEvaluator.EntryVerdict verdict = RunbyEvaluator.entryVerdict(RunbyEvaluator.EntryInput.builder()
                .zerglingsOnly(zerglingsOnly)
                .metabolicBoost(metabolicBoost)
                .size(squad.size())
                .squadCenter(squad.getCenter())
                .anchor(anchor)
                .army(view.army)
                .zones(view.zones)
                .build());
        boolean underAttack = basesUnderAttack();
        RunbyTick.RunbyTickBuilder row = RunbyTick.builder()
                .frame(now)
                .squadId(squad.getId())
                .event(RunbyTick.Event.ENTRY_CHECK)
                .verdict(verdict)
                .anchor(anchor)
                .lings(squad.size())
                .basesUnderAttack(underAttack ? 1 : 0);
        if (anchor != null) {
            row.enemyTally(RunbyEvaluator.enemyTally(view.army, view.zones, anchor))
                    .ourTally(RunbyEvaluator.ourTally(squad.size()))
                    .pathTally(RunbyEvaluator.pathTally(view.army, view.zones, squad.getCenter(), anchor));
        }
        RunbyTelemetry.tick(row.build());
        if (verdict != RunbyEvaluator.EntryVerdict.ENTER) {
            return false;
        }
        enterRunby(squad, base, target, now);
        return true;
    }

    private void enterRunby(Squad squad, Base base, RunbyTarget target, int now) {
        endContainment(squad);
        squad.setStatus(SquadStatus.RUNBY);
        squad.commit(now);
        SquadDecisions.pathTaken(squad, DecisionPath.RUNBY_ENTER);
        RunbyState state = new RunbyState(now);
        state.target(base, target.area, target.anchor, target.spots, runbyBudget(squad, target.anchor), now);
        state.setLastTickFrame(now - RunbyEvaluator.RUNBY_TICK);
        squad.setRunbyState(state);
        for (ManagedUnit member : squad.getMembers()) {
            member.setRole(UnitRole.RUNBY);
            member.setContainPosition(null);
            member.setFightTarget(null);
            member.setRunbyDestination(null);
            state.getLastHitPoints().put(member.getUnitID(), member.getUnit().getHitPoints());
        }
    }

    /**
     * Runs one frame of a runby squad: a squad decision every {@link RunbyEvaluator#RUNBY_TICK} frames, then an
     * order for every ling. The contain throttle, the combat sim branch and the containment exits never see a
     * runby squad; it leaves RUNBY only by aborting, by running out of targets, or by emptying.
     *
     * @param squad runby squad
     */
    private void evaluateRunbySquad(Squad squad) {
        int now = game.getFrameCount();
        RunbyState state = squad.getRunbyState();
        if (squad.size() == 0) {
            return;
        }
        if (state == null || state.getTargetArea() == null) {
            exitRunby(squad, DecisionPath.RUNBY_EXIT_NO_TARGETS, now);
            return;
        }

        RunbyView view = runbyView(now, state.getTargetArea());
        boolean inside = state.getTargetArea().contains(squad.getCenter().toTilePosition());
        if (inside && state.getArrivedFrame() < 0) {
            state.setArrivedFrame(now);
        }

        if (RunbyEvaluator.decisionTickDue(now, state.getLastTickFrame())) {
            state.setLastTickFrame(now);
            if (runbyDecisionTick(squad, state, view, inside, now)) {
                return;
            }
        }

        assignRunbyOrders(squad, state, view, now);
    }

    /**
     * One squad decision of a runby: abort while the abort window is open, refresh the winnable fight verdict
     * in HARASS, refresh the seek point, end PENETRATE, and retarget once the base has nothing left.
     *
     * @return true when the squad left RUNBY
     */
    private boolean runbyDecisionTick(Squad squad, RunbyState state, RunbyView view, boolean inside, int now) {
        boolean windowOpen = RunbyEvaluator.abortWindowOpen(state.isAbortWindowClosed(), state.getArrivedFrame(), now);
        if (!windowOpen) {
            state.setAbortWindowClosed(true);
        }

        boolean simDue = windowOpen && inside
                || state.getPhase() == RunbyState.Phase.HARASS
                && RunbyEvaluator.winnableRefreshDue(now, state.getWinnableCheckedFrame());
        CombatSimulator.CombatResult simResult = simDue ? runbySim(squad) : null;
        HorizonCombatSimulator.DebugSnapshot snapshot = simResult == null ? null : lastSnapshot(squad);
        boolean measured = snapshot != null && snapshot.isEnemyMeasured();
        double ratio = snapshot != null ? snapshot.getOverallRatio() : 0;
        double threshold = snapshot != null ? snapshot.getEngageThreshold() : 0;

        if (state.getPhase() == RunbyState.Phase.HARASS && simResult != null) {
            state.setWinnable(simResult == CombatSimulator.CombatResult.ENGAGE && measured);
            state.setWinnableCheckedFrame(now);
        }

        if (windowOpen) {
            state.setEnemyTally(RunbyEvaluator.enemyTally(view.army, view.zones, state.getAnchor()));
            state.setOurTally(RunbyEvaluator.ourTally(squad.size()));
            if (RunbyEvaluator.shouldAbort(true, state.getEnemyTally(), state.getOurTally(), inside, simResult, measured,
                    ratio, threshold)) {
                logRunbyTick(squad, state, view, inside, windowOpen, now);
                exitRunby(squad, DecisionPath.RUNBY_ABORT, now);
                return true;
            }
        }

        List<Position> lings = memberPositions(squad);
        List<Position> visibleWorkers = visibleWorkersIn(view, state.getTargetArea());
        Set<Position> recentWorkers = gameState.getObservedUnitTracker().getRecentWorkerPositionsIn(
                state.getTargetArea()::contains, now, RunbyEvaluator.FRESH_FRAMES);
        RunbyTargeting.markVisited(state.getLikelySpots(), state.getVisitedSpots(), lings, visibleWorkers);
        RunbyTargeting.Goal goal = RunbyTargeting.seekGoal(squad.getCenter(), visibleWorkers, recentWorkers,
                state.getLikelySpots(), state.getVisitedSpots());
        state.setGoal(goal.getPoint());
        state.setGoalType(goal.getType());
        if (goal.getType() != RunbyState.GoalType.NONE) {
            state.setLastProgressFrame(now);
        }

        if (state.getPhase() == RunbyState.Phase.PENETRATE && RunbyEvaluator.penetrateEnds(now,
                state.getPhaseStartFrame(), state.getPenetrateBudgetFrames(), inside,
                safeWorkerInReach(squad, view, state.getTargetArea()))) {
            state.startHarass(now);
            SquadDecisions.runbyPhaseStarted(squad, RunbyState.Phase.PENETRATE, RunbyState.Phase.HARASS,
                    DecisionPath.RUNBY_PHASE);
        }

        boolean targetGone = !gameState.getBaseData().getEnemyBases().contains(state.getTargetBase());
        if (targetGone || RunbyEvaluator.noTargets(now, state.getLastProgressFrame())) {
            logRunbyTick(squad, state, view, inside, windowOpen, now);
            return retargetOrExitRunby(squad, state, now);
        }

        logRunbyTick(squad, state, view, inside, windowOpen, now);
        return false;
    }

    private CombatSimulator.CombatResult runbySim(Squad squad) {
        CombatSimulator.CombatResult result = squad.getCombatSimulator()
                .evaluate(squad, Collections.emptyMap(), gameState);
        SquadDecisions.simEvaluated(squad, result, false, false);
        return result;
    }

    /**
     * Moves a runby squad on to the next known enemy base it has not raided yet, or retreats it when there is
     * none.
     *
     * @return true when the squad left RUNBY
     */
    private boolean retargetOrExitRunby(Squad squad, RunbyState state, int now) {
        Base next = runbyBaseNear(squad.getCenter(), state.getVisitedBases());
        if (next == null) {
            exitRunby(squad, DecisionPath.RUNBY_EXIT_NO_TARGETS, now);
            return true;
        }
        RunbyTarget target = runbyTarget(next);
        RunbyState.Phase from = state.getPhase();
        state.target(next, target.area, target.anchor, target.spots, runbyBudget(squad, target.anchor), now);
        SquadDecisions.runbyPhaseStarted(squad, from, RunbyState.Phase.PENETRATE, DecisionPath.RUNBY_RETARGET);
        return false;
    }

    private void exitRunby(Squad squad, DecisionPath path, int now) {
        for (ManagedUnit member : squad.getMembers()) {
            member.setRunbyDestination(null);
            member.setFightTarget(null);
        }
        squad.setRunbyState(null);
        squad.setStatus(SquadStatus.RETREAT);
        SquadDecisions.pathTaken(squad, path);
        assignRetreatTargets(squad, squad.getMembers());
        squad.startRetreatLock(now);
    }

    /**
     * Gives every ling of a runby squad its order for this frame. Orders are recomputed on every frame and each
     * ling acts on its latest order whenever it is ready.
     */
    private void assignRunbyOrders(Squad squad, RunbyState state, RunbyView view, int now) {
        BaseArea area = state.getTargetArea();
        RunbyTargeting.Situation situation = RunbyTargeting.Situation.builder()
                .phase(state.getPhase())
                .winnable(state.isWinnable())
                .contacts(view.contacts)
                .threats(view.threats)
                .zones(view.zones)
                .seekPoint(state.getGoal())
                .evadeAllowed(point -> isRunbyEvadePoint(area, point))
                .workerAllowed(point -> area.contains(point.toTilePosition()))
                .now(now)
                .build();
        for (ManagedUnit member : squad.getMembers()) {
            if (member.getRole() != UnitRole.RUNBY) {
                member.setRole(UnitRole.RUNBY);
            }
            RunbyTargeting.Ling ling = new RunbyTargeting.Ling(member.getUnitID(), member.getPosition(),
                    RunbyTargeting.reach(member.getUnitType()));
            RunbyTargeting.Decision decision = RunbyTargeting.choose(ling, situation, state.memoryFor(member.getUnitID()));
            if (applyRunbyDecision(member, decision, view, state)) {
                state.setLastProgressFrame(now);
            }
        }
    }

    /**
     * Turns a ling's decision into its order.
     *
     * @return true when the ling was given an enemy to hit, which counts as progress at the target base
     */
    private boolean applyRunbyDecision(ManagedUnit member, RunbyTargeting.Decision decision, RunbyView view,
                                       RunbyState state) {
        switch (decision.getKind()) {
            case EVADE:
            case SEEK:
                member.setFightTarget(null);
                member.setRunbyDestination(decision.getPoint());
                return false;
            case WORKER:
            case BUILDING:
                Unit target = view.units.get(decision.getTargetId());
                if (target == null) {
                    seekOrHold(member, state);
                    return false;
                }
                member.setRunbyDestination(null);
                member.setFightTarget(target);
                return true;
            case FIGHT:
                if (!assignRunbyFightTarget(member, view)) {
                    seekOrHold(member, state);
                    return false;
                }
                member.setRunbyDestination(null);
                return true;
            default:
                seekOrHold(member, state);
                return false;
        }
    }

    /**
     * Sends a ling with nothing to hit to the squad's seek point, or to the anchor when there is none.
     */
    private void seekOrHold(ManagedUnit member, RunbyState state) {
        member.setFightTarget(null);
        member.setRunbyDestination(state.getGoal() != null ? state.getGoal() : state.getAnchor());
    }

    /**
     * Picks a winnable fight target with TargetScorer among the visible enemies the ling can attack.
     *
     * @return true when a target was set, false when no candidate survived the attack filter
     */
    private boolean assignRunbyFightTarget(ManagedUnit member, RunbyView view) {
        Unit unit = member.getUnit();
        List<Unit> candidates = new ArrayList<>();
        for (Unit enemy : view.units.values()) {
            if (RunbyTargeting.isFightTarget(enemy.getType()) && unit.canAttack(enemy)) {
                candidates.add(enemy);
            }
        }
        if (candidates.isEmpty()) {
            return false;
        }
        TargetScorer.Selection selection = TargetScorer.selectTarget(unit,
                filterByProximity(candidates, unit::getDistance), member.fightTarget);
        if (selection == null) {
            return false;
        }
        TargetChoices.chosen(member, member.fightTarget, selection, false);
        member.setFightTarget(selection.getTarget());
        return true;
    }

    private boolean isRunbyEvadePoint(BaseArea area, Position point) {
        int maxX = game.mapWidth() * 32 - 1;
        int maxY = game.mapHeight() * 32 - 1;
        if (point.getX() < 0 || point.getY() < 0 || point.getX() > maxX || point.getY() > maxY) {
            return false;
        }
        return area.contains(point.toTilePosition()) && game.isWalkable(new WalkPosition(point));
    }

    private boolean safeWorkerInReach(Squad squad, RunbyView view, BaseArea area) {
        for (RunbyTargeting.Contact contact : view.contacts) {
            if (!contact.isWorker() || !area.contains(contact.getPosition().toTilePosition())
                    || !RunbyTargeting.isSafe(contact.getPosition(), view.threats)) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getPosition().getDistance(contact.getPosition())
                        <= RunbyTargeting.reach(member.getUnitType())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Position> visibleWorkersIn(RunbyView view, BaseArea area) {
        List<Position> workers = new ArrayList<>();
        for (RunbyTargeting.Contact contact : view.contacts) {
            if (contact.isWorker() && area.contains(contact.getPosition().toTilePosition())) {
                workers.add(contact.getPosition());
            }
        }
        return workers;
    }

    private List<Position> memberPositions(Squad squad) {
        List<Position> positions = new ArrayList<>();
        for (ManagedUnit member : squad.getMembers()) {
            positions.add(member.getPosition());
        }
        return positions;
    }

    /**
     * Records a runby decision tick, measuring the hit points the squad lost since the previous tick. A ling
     * that died since then counts its whole remaining pool.
     */
    private void logRunbyTick(Squad squad, RunbyState state, RunbyView view, boolean inside, boolean windowOpen,
                              int now) {
        Map<Integer, Integer> previous = state.getLastHitPoints();
        Map<Integer, Integer> current = new HashMap<>();
        int hpLost = 0;
        int hpLostExposed = 0;
        int exposed = 0;
        for (ManagedUnit member : squad.getMembers()) {
            int hp = member.getUnit().getHitPoints();
            current.put(member.getUnitID(), hp);
            boolean inReach = RunbyTargeting.minMargin(member.getPosition(), view.threats) <= 0;
            if (inReach) {
                exposed++;
            }
            Integer before = previous.get(member.getUnitID());
            int lost = before == null ? 0 : Math.max(0, before - hp);
            hpLost += lost;
            if (inReach) {
                hpLostExposed += lost;
            }
        }
        for (Map.Entry<Integer, Integer> entry : previous.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                hpLost += entry.getValue();
            }
        }
        previous.clear();
        previous.putAll(current);

        RunbyTelemetry.tick(RunbyTick.builder()
                .frame(now)
                .squadId(squad.getId())
                .event(RunbyTick.Event.TICK)
                .phase(state.getPhase())
                .goalType(state.getGoalType())
                .seekPoint(state.getGoal())
                .anchor(state.getAnchor())
                .abortWindowOpen(windowOpen ? 1 : 0)
                .enemyTally(windowOpen ? state.getEnemyTally() : -1)
                .ourTally(windowOpen ? state.getOurTally() : -1)
                .inBaseArea(inside ? 1 : 0)
                .lings(squad.size())
                .workersVisible(visibleWorkersIn(view, state.getTargetArea()).size())
                .exposedLings(exposed)
                .hpLost(hpLost)
                .hpLostNonWorkerInReach(hpLostExposed)
                .winnable(winnableCell(state))
                .basesUnderAttack(basesUnderAttack() ? 1 : 0)
                .workersKilled(state.getWorkersKilled())
                .buildingsKilled(state.getBuildingsKilled())
                .build());
    }

    private static int winnableCell(RunbyState state) {
        if (state.getPhase() != RunbyState.Phase.HARASS) {
            return -1;
        }
        return state.isWinnable() ? 1 : 0;
    }

    private int runbyBudget(Squad squad, Position anchor) {
        Position center = squad.getCenter();
        int length = gameState.getBwem().getMap().getPathLength(center, anchor);
        double distance = length >= 0 ? length : center.getDistance(anchor);
        return RunbyEvaluator.penetrateBudget(distance);
    }

    /**
     * The known enemy base a runby from here would raid: the nearest by ground, skipping bases already raided.
     *
     * @param from squad center
     * @param excluded bases already raided
     * @return the base, or null when none is left
     */
    private Base runbyBaseNear(Position from, Set<Base> excluded) {
        Set<Base> candidates = new HashSet<>(gameState.getBaseData().getEnemyBases());
        candidates.removeAll(excluded);
        if (candidates.isEmpty()) {
            return null;
        }
        return closestBaseTo(from, candidates);
    }

    /**
     * Resolves the ground a runby raids at a base, and the walkable spots its workers are likely to be at:
     * the mineral line side between the depot and the mineral centroid first, then the geyser side. Each raw
     * spot is snapped to the walkable tile nearest the depot by ground, so a spot under a mineral field is never
     * sent as an order. The anchor is the first spot, or the depot when no spot resolves.
     *
     * @param base the base
     * @return the runby target, cached per base
     */
    private RunbyTarget runbyTarget(Base base) {
        return runbyTargets.computeIfAbsent(base, b -> {
            BaseArea area = BaseArea.from(b, gameState.getBwem().getMap(), RUNBY_AREA_PROXIMITY_TILES,
                    RUNBY_AREA_TILES);
            List<Position> spots = likelyWorkerSpots(b);
            Position anchor = spots.isEmpty() ? b.getCenter() : spots.get(0);
            return new RunbyTarget(area, anchor, spots);
        });
    }

    private List<Position> likelyWorkerSpots(Base base) {
        Position depot = base.getCenter();
        List<Position> raw = new ArrayList<>();
        if (!base.getMinerals().isEmpty()) {
            int sumX = 0;
            int sumY = 0;
            for (bwem.Mineral mineral : base.getMinerals()) {
                sumX += mineral.getCenter().getX();
                sumY += mineral.getCenter().getY();
            }
            int count = base.getMinerals().size();
            raw.add(midpoint(depot, new Position(sumX / count, sumY / count)));
        }
        for (bwem.Geyser geyser : base.getGeysers()) {
            raw.add(midpoint(depot, geyser.getCenter()));
        }

        Set<TilePosition> blocked = new HashSet<>();
        for (bwem.Mineral mineral : base.getMinerals()) {
            blocked.addAll(footprint(mineral.getTopLeft(), mineral.getBottomRight()));
        }
        for (bwem.Geyser geyser : base.getGeysers()) {
            blocked.addAll(footprint(geyser.getTopLeft(), geyser.getBottomRight()));
        }

        List<Position> spots = new ArrayList<>();
        for (Position spot : raw) {
            Map<TilePosition, Position> candidates = new HashMap<>();
            TilePosition center = spot.toTilePosition();
            for (int dx = -LIKELY_SPOT_SEARCH_TILES; dx <= LIKELY_SPOT_SEARCH_TILES; dx++) {
                for (int dy = -LIKELY_SPOT_SEARCH_TILES; dy <= LIKELY_SPOT_SEARCH_TILES; dy++) {
                    TilePosition tile = new TilePosition(center.getX() + dx, center.getY() + dy);
                    if (!blocked.contains(tile) && gameState.getGameMap().isValidTile(tile)) {
                        candidates.put(tile, tile.toPosition().add(new Position(16, 16)));
                    }
                }
            }
            Position snapped = gameState.getGameMap().findNearestByGround(base.getLocation(), candidates, blocked);
            if (snapped != null && !spots.contains(snapped)) {
                spots.add(snapped);
            }
        }
        return spots;
    }

    private static Position midpoint(Position a, Position b) {
        return new Position((a.getX() + b.getX()) / 2, (a.getY() + b.getY()) / 2);
    }

    private static Set<TilePosition> footprint(TilePosition topLeft, TilePosition bottomRight) {
        Set<TilePosition> tiles = new HashSet<>();
        for (int x = topLeft.getX(); x <= bottomRight.getX(); x++) {
            for (int y = topLeft.getY(); y <= bottomRight.getY(); y++) {
                tiles.add(new TilePosition(x, y));
            }
        }
        return tiles;
    }

    /**
     * Reads the enemy once for a runby frame: visible ground targets for the lings, the tracked army with the
     * freshness of each observation, and everything that can hurt a ling with its reach. An army unit threatens
     * while its observation is fresh, or for as long as it may be burrowed where it was last seen inside the
     * target base; a structure that shoots ground threatens wherever it was last seen.
     *
     * @param now current frame
     * @param targetArea ground of the base being raided or offered
     */
    private RunbyView runbyView(int now, BaseArea targetArea) {
        RunbyView view = new RunbyView();
        view.zones = gameState.getStaticDefenseZones();
        EnemyReachMemory reachMemory = gameState.getReachMemory();
        for (EnemyReachMemory.HurtMark mark : reachMemory.liveHurtMarks(now)) {
            view.threats.add(RunbyTargeting.Threat.of(mark));
        }
        for (Unit enemy : gameState.getVisibleEnemyUnits()) {
            UnitType type = enemy.getType();
            if (!enemy.isDetected() || enemy.isFlying() || !enemy.isTargetable()
                    || Filter.isLowPriorityCombatTarget(type)) {
                continue;
            }
            int maxPool = type.maxHitPoints() + type.maxShields();
            double hpFraction = maxPool <= 0 ? 1.0 : (double) (enemy.getHitPoints() + enemy.getShields()) / maxPool;
            view.contacts.add(new RunbyTargeting.Contact(enemy.getID(), type, enemy.getPosition(), hpFraction,
                    Filter.isMeanWorker(enemy)));
            view.units.put(enemy.getID(), enemy);
        }
        for (ObservedUnit observed : gameState.getObservedUnitTracker().getLivingObservedUnits()) {
            UnitType type = observed.getUnitType();
            boolean visible = observed.getUnit().isVisible();
            Position position = observed.getCurrentOrLastKnownPosition();
            if (RunbyEvaluator.isArmyType(type)) {
                boolean fresh = RunbyEvaluator.isFresh(visible, observed.getLastObservedFrame().getFrames(), now);
                Position lastKnown = observed.getLastKnownLocation();
                boolean lurking = RunbyEvaluator.isLurking(visible, type.isBurrowable(),
                        lastKnown != null && targetArea.contains(lastKnown.toTilePosition()));
                boolean cleared = RunbyEvaluator.isCleared(visible,
                        lastKnown != null && game.isVisible(lastKnown.toTilePosition()), lurking);
                view.army.add(new RunbyEvaluator.ArmyUnit(type, position, fresh, cleared));
                if ((fresh || lurking) && position != null) {
                    view.threats.add(RunbyTargeting.Threat.of(type, position, reachMemory.groundReach(type)));
                }
            } else if (Filter.isHostileBuildingToGround(type) && position != null) {
                view.threats.add(RunbyTargeting.Threat.of(type, position, reachMemory.groundReach(type)));
            }
        }
        return view;
    }

    /**
     * The enemy as a runby reads it on one frame.
     */
    private static final class RunbyView {
        private final List<RunbyTargeting.Contact> contacts = new ArrayList<>();
        private final Map<Integer, Unit> units = new HashMap<>();
        private final List<RunbyEvaluator.ArmyUnit> army = new ArrayList<>();
        private final List<RunbyTargeting.Threat> threats = new ArrayList<>();
        private List<StaticDefenseZone> zones = Collections.emptyList();
    }

    /**
     * The ground a runby raids at one base, the point it is measured at, and where its workers are likely to be.
     */
    private static final class RunbyTarget {
        private final BaseArea area;
        private final Position anchor;
        private final List<Position> spots;

        private RunbyTarget(BaseArea area, Position anchor, List<Position> spots) {
            this.area = area;
            this.anchor = anchor;
            this.spots = spots;
        }
    }

    /**
     * Builds the arc a squad would hold at the choke in front of the enemy base closest to it, at the radius the
     * episode has been pushed back to, clear of every zone that outranges the squad at the reach learned over the
     * game.
     *
     * @param squad squad offered the arc
     * @return the computed arc, or null when there is no enemy base or choke, or no arc point is clear
     */
    private Arc containmentArc(Squad squad) {
        return containmentArc(squad, containmentZones(squad, game.getFrameCount()));
    }

    private Arc containmentArc(Squad squad, List<StaticDefenseZone> zones) {
        HashSet<Base> enemyBases = gameState.getBaseData().getEnemyBases();
        if (enemyBases.isEmpty()) return null;
        Base containBase = closestBaseTo(squad.getCenter(), enemyBases);
        Position chokePosition = findContainmentChoke(squad.getCenter(), containBase);
        if (chokePosition == null) return null;

        Position enemyBasePosition = containBase.getCenter();
        Position faceTarget = new Position(
                2 * chokePosition.getX() - enemyBasePosition.getX(),
                2 * chokePosition.getY() - enemyBasePosition.getY()
        );

        Arc arc = new Arc(chokePosition, faceTarget, containmentRadius(squad), ARC_DEGREES,
                Math.max(squad.size(), 4));
        return computeContainmentArc(arc, zones, containmentDefensePadding(squad.getComposition().keySet()),
                gameState.getGameMap().getAccessibleWalkPositions(), game.mapWidth() * 32, game.mapHeight() * 32);
    }

    /**
     * Radius a squad's arc is drawn at: the radius its episode has been pushed back to, or the default radius for a
     * squad starting an episode.
     *
     * @param squad squad offered the arc
     * @return radius in pixels
     */
    static int containmentRadius(Squad squad) {
        return Math.max(ARC_RADIUS, squad.getContainRadius());
    }

    /**
     * Places an arc's points clear of the zones.
     *
     * @param arc uncomputed arc
     * @param zones zones the points must stay out of
     * @param padding pixels added to every zone's reach
     * @param accessible walkable positions, or empty to treat the whole map as walkable
     * @param mapPixelWidth map width in pixels
     * @param mapPixelHeight map height in pixels
     * @return the computed arc, or null when no point is clear
     */
    static Arc computeContainmentArc(Arc arc, Collection<StaticDefenseZone> zones, int padding,
                                     Set<WalkPosition> accessible, int mapPixelWidth, int mapPixelHeight) {
        arc.compute(accessible, zones, padding, mapPixelWidth, mapPixelHeight);
        return arc.isEmpty() ? null : arc;
    }

    /**
     * The ground threat zones a containing squad's arc stays out of, at the reach learned over the game.
     *
     * @param squad containing squad
     * @param now current frame
     * @return every static defence zone, and every other zone that outranges the squad's shortest ranged member
     */
    private List<StaticDefenseZone> containmentZones(Squad squad, int now) {
        return ContainmentPushback.outrangingZones(gameState.getGroundThreatZones(now),
                shortestGroundRange(squad.getComposition().keySet()));
    }

    /**
     * Shortest ground weapon range among unit types that have one.
     *
     * @param types unit types in a squad
     * @return range in pixels, or 0 when no type has a ground weapon
     */
    static int shortestGroundRange(Collection<UnitType> types) {
        int shortest = Integer.MAX_VALUE;
        for (UnitType type : types) {
            if (type.groundWeapon() == WeaponType.None) {
                continue;
            }
            shortest = Math.min(shortest, EnemyReachMemory.baseGroundRange(type));
        }
        return shortest == Integer.MAX_VALUE ? 0 : shortest;
    }

    /**
     * Pixels an arc point must keep beyond a static defence structure's reach: the largest extent of any unit
     * that may stand on the point, plus a margin.
     *
     * @param memberTypes unit types in the squad
     * @return padding in pixels
     */
    static int containmentDefensePadding(Collection<UnitType> memberTypes) {
        int largest = 0;
        for (UnitType type : memberTypes) {
            largest = Math.max(largest, extent(type));
        }
        return largest + CONTAIN_DEFENSE_MARGIN;
    }

    private static int extent(UnitType type) {
        return Math.max(Math.max(type.dimensionLeft(), type.dimensionRight()),
                Math.max(type.dimensionUp(), type.dimensionDown()));
    }

    /**
     * Puts the squad on an arc and gives every member a point of it.
     *
     * @param squad containing squad
     * @param arc computed arc
     * @return members whose contain position changed
     */
    private int assignContainmentPositions(Squad squad, Arc arc) {
        activeContainmentArcs.add(arc);
        squad.setContainmentArc(arc);

        int moved = 0;
        List<ManagedUnit> units = new ArrayList<>(squad.getMembers());
        Map<ManagedUnit, Position> assignments = arc.assignUnits(units);
        for (ManagedUnit mu : units) {
            Position assigned = assignments.get(mu);
            if (assigned == null) {
                assigned = arc.closestPosition(mu.getPosition());
            }
            if (assigned == null) {
                continue;
            }
            if (!assigned.equals(mu.getContainPosition())) {
                moved++;
            }
            mu.setRole(UnitRole.CONTAIN);
            mu.setContainPosition(assigned);
        }
        return moved;
    }

    /**
     * Puts a unit joining a containing squad on the arc the squad already holds, leaving the squad's status alone.
     * The squad's next re-evaluation redistributes the whole arc.
     *
     * @param squad containing squad the unit joined
     * @param managedUnit unit that joined
     */
    private void joinContainment(Squad squad, ManagedUnit managedUnit) {
        Arc arc = squad.getContainmentArc();
        Position assigned = arc == null ? null : arc.closestPosition(managedUnit.getPosition());
        if (assigned == null) {
            managedUnit.setRallyPoint(squad.getCenter());
            managedUnit.setRole(UnitRole.RALLY);
            return;
        }
        managedUnit.setRole(UnitRole.CONTAIN);
        managedUnit.setContainPosition(assigned);
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

    /**
     * Simulates the given defenders against the threats near a base.
     *
     * <p>Enemies farther than {@link #DEFENSE_SIM_RANGE} from the base center are left out. A defender that
     * far away is placed at the base center, so a drone still mining at another base is judged in the fight
     * it would walk into rather than from out of range of it.
     */
    private DefenseSim simulateDefense(Base base, List<ManagedUnit> defenders, List<Unit> enemyUnits,
                                       double threshold) {
        Position center = base.getCenter();
        Simulator simulator = new Simulator.Builder().build();

        for (ManagedUnit managedUnit: defenders) {
            Unit unit = managedUnit.getUnit();
            if (unit.getType() == UnitType.Unknown) {
                continue;
            }
            Agent agent = agentFactory.of(unit);
            if (unit.getPosition().getDistance(center) > DEFENSE_SIM_RANGE) {
                agent.setX(center.getX()).setY(center.getY());
            }
            simulator.addAgentA(agent);
        }

        for (Unit enemyUnit: enemyUnits) {
            if (enemyUnit.getType() == UnitType.Unknown) {
                continue;
            }
            if ((int) enemyUnit.getPosition().getDistance(center) > DEFENSE_SIM_RANGE) {
                continue;
            }
            try {
                simulator.addAgentB(agentFactory.of(enemyUnit));
            } catch (ArithmeticException e) {
                return DefenseSim.unsimulated(simulator.getAgentsA().size(), threshold);
            }
        }

        int defenderAgents = simulator.getAgentsA().size();
        int enemyAgents = simulator.getAgentsB().size();
        simulator.simulate(COMBAT_SIM_DURATION_FRAMES);
        return new DefenseSim(true, defenderAgents, enemyAgents, simulator.getAgentsA().size(),
                simulator.getAgentsB().size(), threshold);
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

    /**
     * Clears references to a destroyed unit and books it against any containing squad: a member's death as a
     * loss, an enemy's death near a member as a kill. Must run before the unit is removed from its squad.
     *
     * @param unit destroyed unit
     */
    public void onUnitDestroy(Unit unit) {
        int now = game.getFrameCount();
        boolean enemy = unit.getPlayer() == game.enemy();
        Map<Squad, Double> killers = new HashMap<>();
        for (Squad squad: fightSquads) {
            if (squad.getTarget() == unit) {
                squad.setTarget(null);
            }
            if (squad.getStatus() != SquadStatus.CONTAIN) {
                continue;
            }
            if (enemy) {
                double distance = closestMemberInKillReach(squad, unit);
                if (distance >= 0) {
                    killers.put(squad, distance);
                }
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (member.getUnit() == unit) {
                    squad.getContainmentAttrition().recordLoss(now, member.getUnitType().supplyRequired());
                    break;
                }
            }
        }
        creditContainKill(killers, now, unit.getType().supplyRequired());
        irradiatedUnits.removeIf(mu -> mu.getUnit() == unit);
        scoutChase.releaseScout(unit.getID());
        scoutChase.release(unit.getID());
        creditRunbyKill(unit);
    }

    /**
     * Credits a dead enemy to the one containing squad whose member stood closest to it, among those with a member
     * that could have landed the killing blow. Squads sharing an arc would otherwise each book the same kill, and
     * the one being ground down would never read as bleeding.
     *
     * @param killers each containing squad with a member in kill reach, mapped to that member's distance
     * @param frame frame of the kill
     * @param supply supply of the dead enemy
     * @return the credited squad, or null when no squad was in reach
     */
    static Squad creditContainKill(Map<Squad, Double> killers, int frame, int supply) {
        Squad credited = killers.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (credited != null) {
            credited.getContainmentAttrition().recordKill(frame, supply);
        }
        return credited;
    }

    /**
     * Credits a dead enemy worker or building to the runby squad that killed it: a member was targeting it, or
     * stood within {@link #RUNBY_KILL_CREDIT_RADIUS} of it.
     *
     * @param unit the destroyed unit
     */
    private void creditRunbyKill(Unit unit) {
        UnitType type = unit.getType();
        boolean worker = Filter.isWorkerType(type);
        if (!worker && !type.isBuilding() || !game.self().isEnemy(unit.getPlayer())) {
            return;
        }
        Position position = unit.getPosition();
        for (Squad squad : fightSquads) {
            RunbyState state = squad.getRunbyState();
            if (squad.getStatus() != SquadStatus.RUNBY || state == null) {
                continue;
            }
            for (ManagedUnit member : squad.getMembers()) {
                if (member.fightTarget == unit || member.getPosition().getDistance(position) <= RUNBY_KILL_CREDIT_RADIUS) {
                    state.creditKill(worker);
                    return;
                }
            }
        }
    }

    private double closestMemberInKillReach(Squad squad, Unit enemy) {
        Position position = enemy.getPosition();
        double closest = -1;
        for (ManagedUnit member : squad.getMembers()) {
            UnitType memberType = member.getUnitType();
            int reach = EnemyReachMemory.groundRange(memberType, weapon -> game.self().weaponMaxRange(weapon));
            double distance = member.getPosition().getDistance(position);
            if (distance > containKillRadius(memberType, reach, enemy.getType())) {
                continue;
            }
            if (closest < 0 || distance < closest) {
                closest = distance;
            }
        }
        return closest;
    }

    /**
     * Centre to centre distance within which a member could have landed the killing blow on an enemy: the member's
     * ground reach from its edge, both units' largest extents, and the contain margin. An enemy that dies farther
     * from every member was killed by something else and is not credited to the contain.
     *
     * @param memberType member unit type
     * @param memberReach member ground reach in pixels, upgrades included
     * @param enemyType destroyed enemy's unit type
     * @return radius in pixels
     */
    static int containKillRadius(UnitType memberType, int memberReach, UnitType enemyType) {
        return memberReach + extent(memberType) + extent(enemyType) + CONTAIN_DEFENSE_MARGIN;
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
        switch (reinforcementPath(squad.getStatus(), shouldStageSquad(squad))) {
            case STAGE:
                rallySquad(squad, RallyReason.STAGING);
                return;
            case JOIN_CONTAINMENT:
                joinContainment(squad, managedUnit);
                return;
            default:
                break;
        }

        RallyRelease release = reinforcementRelease(squad.getStatus());
        if (release != RallyRelease.NONE) {
            SquadDecisions.rallyReleased(squad, release);
        }

        simulateFightSquad(squad);
    }

    /**
     * Branch {@link #addManagedFighter} takes for the squad a reinforcement joined.
     */
    enum ReinforcementPath {
        STAGE,
        JOIN_CONTAINMENT,
        SIMULATE
    }

    /**
     * Picks what a reinforcement does to the squad it joined.
     *
     * <p>Apart from a merge with a squad of higher precedence, a containing squad changes status only through
     * {@link #evaluateContainingSquad}, so a unit joining one takes a point on the arc and never runs the squad
     * through {@link #simulateFightSquad}. A zergling scout can be pulled from a containing squad and handed
     * straight back every 24 frames; simulating on each return would let a blind ADVANCE flip the squad to FIGHT
     * until the next frame re-entered the arc.
     *
     * @param status status the squad held as the reinforcement joined
     * @param stage true when the squad is rallying with no enemy inside its detection radius
     * @return branch to take
     */
    static ReinforcementPath reinforcementPath(SquadStatus status, boolean stage) {
        if (status == SquadStatus.CONTAIN) {
            return ReinforcementPath.JOIN_CONTAINMENT;
        }
        if (stage) {
            return ReinforcementPath.STAGE;
        }
        return ReinforcementPath.SIMULATE;
    }

    /**
     * Names the release for a squad a reinforcement joins outside the staging path.
     *
     * <p>A unit completing runs before the frame's squad loop, so this is the one place a squad can
     * leave RALLY without {@link #evaluateSquadRole} seeing it: {@link #simulateFightSquad} is
     * called here directly and can set FIGHT, RETREAT or CONTAIN before the release hook there ever
     * reads the status. The episode's closing row would carry NONE, which by contract says the row
     * closes nothing.
     *
     * <p>The release is always close threats. Reaching this call at all means
     * {@link #shouldStageSquad} was false, and for a squad already rallying the only term that can
     * make it false is an enemy inside the detection radius.
     *
     * @param status status the squad held as the reinforcement joined
     * @return CLOSE_THREATS for a rallying squad, NONE for any other, which closes no episode
     */
    static RallyRelease reinforcementRelease(SquadStatus status) {
        return status == SquadStatus.RALLY ? RallyRelease.CLOSE_THREATS : RallyRelease.NONE;
    }

    private Squad findCloseGroundSquad(ManagedUnit managedUnit) {
        for (Squad squad : fightSquads) {
            if (!squad.isGroundSquad()) continue;
            if (!mayJoin(squad.getStatus())) continue;
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
            if (!mayJoinAirSquad(managedUnit.getUnitType(), holdsOnlyScourge(squad.getComposition()))) continue;

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

        newSquad.setStatus(SquadStatus.RALLY);
        newSquad.setRallyPoint(gameState.getSquadRallyPoint());
        fightSquads.add(newSquad);
        return newSquad;
    }

    /**
     * Returns true when a rallying squad has no enemies within detection range and should stage.
     *
     * @param squad squad the reinforcement was added to
     * @return true if the squad should rally to the staging point
     */
    private boolean shouldStageSquad(Squad squad) {
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
            scoutChase.release(unit.getID());
            assignFallbackMovementTarget(managedUnit, squad);
            return;
        }

        List<Unit> uncapped = ScoutChase.withoutCappedScouts(filtered, enemy -> isCappedScoutFor(unit, enemy));
        boolean scoutCapped = uncapped.size() < filtered.size();
        if (scoutCapped) {
            Position threatened = gameState.threatenedDefensePosition();
            ScoutChase.Excess excess = ScoutChase.excessAction(uncapped.size(), nearestDistance(unit, uncapped),
                    ENEMY_DETECTION_RADIUS, threatened != null);
            if (excess == ScoutChase.Excess.DEFEND) {
                rallyToDefensePosition(managedUnit, threatened);
                return;
            }
            if (excess == ScoutChase.Excess.FOLLOW_SQUAD) {
                rallyToDefensePosition(managedUnit, squad.getCenter());
                return;
            }
        }

        filtered = filterByProximity(uncapped, unit::getDistance);

        if (gameState.isCannonRushed()) {
            Set<Unit> proxied = gameState.getObservedUnitTracker().getProxiedBuildings();
            List<Unit> proxiedTargets = filtered.stream()
                    .filter(proxied::contains)
                    .collect(Collectors.toList());
            if (!proxiedTargets.isEmpty()) {
                filtered = proxiedTargets;
            }
        }

        TargetScorer.Selection selection = TargetScorer.selectTarget(unit, filtered, managedUnit.fightTarget);
        if (selection != null) {
            TargetChoices.chosen(managedUnit, managedUnit.fightTarget, selection, scoutCapped);
            managedUnit.setFightTarget(selection.getTarget());
            recordScoutClaim(unit, selection.getTarget());
        }
    }

    /**
     * Rebuilds the scout chase ledger from the scouts that fight squad members and irradiated units already
     * target, so the closest chasers keep their scout and the rest are turned away this frame.
     */
    private void rebuildScoutChase() {
        List<ScoutChase.Claim> claims = new ArrayList<>();
        for (Squad squad : fightSquads) {
            addScoutClaims(squad.getMembers(), claims);
        }
        addScoutClaims(irradiatedUnits, claims);
        scoutChase.beginFrame(claims);
    }

    private void addScoutClaims(Collection<ManagedUnit> units, List<ScoutChase.Claim> claims) {
        for (ManagedUnit managedUnit : units) {
            Unit target = managedUnit.fightTarget;
            if (target == null || !target.exists() || !isEnemyScout(target)) {
                continue;
            }
            Unit attacker = managedUnit.getUnit();
            claims.add(new ScoutChase.Claim(target.getID(), attacker.getID(), attacker.getDistance(target),
                    chaserCap(attacker, target)));
        }
    }

    private boolean isEnemyScout(Unit enemy) {
        return ScoutChase.isScout(enemy.getType(), enemy.isAttacking(), enemy.isConstructing(),
                isNearEnemyBase(enemy.getTilePosition()));
    }

    private boolean isNearEnemyBase(TilePosition tile) {
        for (Base base : gameState.getBaseData().getEnemyBases()) {
            if (manhattanTileDistance(base.getLocation(), tile) <= ScoutChase.ENEMY_BASE_TILE_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private static int chaserCap(Unit attacker, Unit scout) {
        return ScoutChase.chaserCap(attacker.getPlayer().topSpeed(attacker.getType()),
                scout.getPlayer().topSpeed(scout.getType()));
    }

    private boolean isCappedScoutFor(Unit attacker, Unit enemy) {
        return isEnemyScout(enemy) && !scoutChase.admits(enemy.getID(), attacker.getID(), chaserCap(attacker, enemy));
    }

    private void recordScoutClaim(Unit attacker, Unit target) {
        if (isEnemyScout(target)) {
            scoutChase.claim(target.getID(), attacker.getID());
        } else {
            scoutChase.release(attacker.getID());
        }
    }

    private static double nearestDistance(Unit attacker, List<Unit> candidates) {
        double nearest = Double.MAX_VALUE;
        for (Unit candidate : candidates) {
            nearest = Math.min(nearest, attacker.getDistance(candidate));
        }
        return nearest;
    }

    /**
     * Sends a unit the scout cap turned away to a position in the RALLY role, rather than marching it on the
     * enemy's buildings.
     */
    private void rallyToDefensePosition(ManagedUnit managedUnit, Position position) {
        scoutChase.release(managedUnit.getUnit().getID());
        managedUnit.setFightTarget(null);
        managedUnit.setRallyPoint(position);
        managedUnit.setRole(UnitRole.RALLY);
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

    /**
     * @return the candidates within {@link #TARGETING_RADIUS} of the attacker, or every candidate when none are
     */
    static <T> List<T> filterByProximity(List<T> candidates, ToDoubleFunction<T> distance) {
        List<T> nearby = new ArrayList<>();
        for (T enemy : candidates) {
            if (distance.applyAsDouble(enemy) <= TARGETING_RADIUS) {
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
