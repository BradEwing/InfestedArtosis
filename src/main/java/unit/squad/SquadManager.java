package unit.squad;

import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwem.Base;
import info.GameState;
import info.InformationManager;
import info.ScoutData;
import info.tracking.ObservedUnitTracker;
import info.tracking.PsiStormTracker;
import info.tracking.StrategyTracker;
import org.bk.ass.sim.BWMirrorAgentFactory;
import org.bk.ass.sim.Simulator;
import unit.managed.ManagedUnit;
import unit.managed.UnitRole;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.min;
import static util.Filter.closestHostileUnit;

public class SquadManager {

    private Game game;
    private GameState gameState;

    private BWMirrorAgentFactory agentFactory;

    private InformationManager informationManager;

    private Squad overlords = new Squad();

    public HashSet<Squad> fightSquads = new HashSet<>();

    public HashMap<Base, Squad> defenseSquads = new HashMap<>();

    private final CombatSimulator defaultCombatSimulator;

    private HashSet<ManagedUnit> disbanded = new HashSet<>();

    private static final double MUTALISK_JOIN_DISTANCE = 128;

    public SquadManager(Game game, GameState gameState, InformationManager informationManager) {
        this.game = game;
        this.gameState = gameState;
        // TODO: Information accessed here should come from GameState
        this.informationManager = informationManager;
        this.agentFactory = new BWMirrorAgentFactory();
        this.defaultCombatSimulator = new AssCombatSimulator();
    }

    public void updateFightSquads() {
        debugPainters();
        removeEmptySquads();
        mergeSquads();
        assignOverlordsToSquads();

        Set<Squad> removed = new HashSet<>();
        for (Squad fightSquad: fightSquads) {
            fightSquad.onFrame();
            if (fightSquad.shouldDisband()) {
                disbanded.addAll(disbandSquad(fightSquad));
                removed.add(fightSquad);
            }

            if (fightSquad instanceof MutaliskSquad) {
                MutaliskSquad mutaliskSquad = (MutaliskSquad) fightSquad;
                Position rallyPosition = null;
                Squad rallyToSquad = findBestMutaliskSquadToRallyTo(fightSquad);
                if (rallyToSquad != null) {
                    rallyPosition = rallyToSquad.getCenter();
                }
                mutaliskSquad.executeTactics(gameState, rallyPosition);
            } else if (fightSquad instanceof ScourgeSquad) {
                ScourgeSquad scourgeSquad = (ScourgeSquad) fightSquad;
                scourgeSquad.executeTactics(gameState);
            } else {
                // Handle other squad types with general logic
                evaluateSquadRole(fightSquad);
            }

            for (ManagedUnit mu : fightSquad.getMembers()) {
                if (mu.getUnitType() == UnitType.Zerg_Overlord) {
                    if (gameState.getTechProgression().isOverlordSpeed()) {
                        mu.setRallyPoint(fightSquad.getCenter());
                        mu.setRole(UnitRole.RALLY);
                    } else {
                        // If an Overlord somehow ended up in a fight squad without speed, send it back safely
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

    /**
     * Determines the number of workers required to defend, assigns them to the defense squad and
     * returns the assigned workers, so they can be removed from the WorkerManager.
     *
     * @param base base to defend
     * @param baseUnits gatherers to assign to defense squad
     * @param hostileUnits units threatening this base
     * @return gatherers that have been assigned to defend
     */
    public List<ManagedUnit> assignGathererDefenders(Base base, HashSet<ManagedUnit> baseUnits, List<Unit> hostileUnits) {
        ensureDefenseSquad(base);
        Squad defenseSquad = defenseSquads.get(base);

        List<ManagedUnit> reassignedGatherers = new ArrayList<>();
        if (baseUnits.size() < 3 || baseUnits.size() - reassignedGatherers.size() < 3) {
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
                Boolean canClear = canDefenseSquadClearThreat(defenseSquad, hostileUnits);
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

        List<ManagedUnit> reassignedDefenders = new ArrayList<>();
        for (ManagedUnit defender: defenseSquad.getMembers()) {
            reassignedDefenders.add(defender);
        }

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
            TilePosition threatTile = threatPos.toTilePosition();
            int distance = Math.abs(defenderTile.getX() - threatTile.getX())
                    + Math.abs(defenderTile.getY() - threatTile.getY());
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
            if (defenseSquads.size() == 0) {
                continue;
            }

            HashSet<Unit> baseThreats = gameState.getBaseToThreatLookup().get(base);
            if (baseThreats == null || baseThreats.size() == 0) {
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
        assignDefenderTarget(defender, baseThreats.stream().collect(Collectors.toList()));
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

    private void mergeSquads() {
        // Merge every 50 frames
        if (game.getFrameCount() % 50 != 0) {
            return;
        }

        List<Set<Squad>> toMerge = new ArrayList<>();
        Set<Squad> considered = new HashSet<>();
        for (Squad squad1: fightSquads) {
            for (Squad squad2: fightSquads) {
                if (squad1 == squad2) {
                    continue;
                }
                if (considered.contains(squad1) || considered.contains(squad2)) {
                    continue;
                }
                if (squad1.getType() != squad2.getType()) {
                    continue;
                }
                if (squad1.distance(squad2) < 256) {
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
            UnitType type = mergeSet.iterator().next().getType();
            Squad newSquad = newFightSquad(type);
            for (Squad mergingSquad: mergeSet) {
                if (newSquad.getType() == null) {
                    newSquad.setType(mergingSquad.getType());
                }
                newSquad.merge(mergingSquad);
                fightSquads.remove(mergingSquad);
            }
            fightSquads.add(newSquad);
        }
    }

    private List<Unit> enemyUnitsNearSquad(Squad squad) {
        Set<Unit> enemyUnits = gameState.getVisibleEnemyUnits();

        List<Unit> enemies = new ArrayList<>();

        for (Unit u: enemyUnits) {
            final double d = u.getPosition().getDistance(squad.getCenter());
            if (d > 512.0) {
                continue;
            }
            enemies.add(u);
        }

        return enemies;
    }

    private void rallySquad(Squad squad) {
        Position rallyPoint;
        if (squad.getType() == UnitType.Zerg_Mutalisk) {
            rallyPoint = getMutaliskRallyPoint(squad);
        } else if (squad.getType() == UnitType.Zerg_Lurker) {
            rallyPoint = getLurkerRallyPoint(squad);
        } else {
            rallyPoint = this.getRallyPoint(squad);
        }
        for (ManagedUnit managedUnit: squad.getMembers()) {
            managedUnit.setRallyPoint(rallyPoint);
            managedUnit.setRole(UnitRole.RALLY);
        }
    }

    private Position getMutaliskRallyPoint(Squad squad) {
        final int moveOutThreshold = calculateMoveOutThreshold(squad);
        if (squad.size() < moveOutThreshold) {
            Squad rallyToSquad = findBestMutaliskSquadToRallyTo(squad);
            return rallyToSquad.getCenter();
        }
        Set<Position> enemyWorkerLocations = gameState.getLastKnownLocationOfEnemyWorkers();
        if (enemyWorkerLocations.isEmpty()) {
            return getRallyPoint(squad);
        }

        Position squadCenter = squad.getCenter();
        Position closestWorkerLocation = null;
        double closestDistance = Double.MAX_VALUE;

        for (Position workerLocation : enemyWorkerLocations) {
            double distance = squadCenter.getDistance(workerLocation);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestWorkerLocation = workerLocation;
            }
        }

        return closestWorkerLocation;
    }

    private Position getLurkerRallyPoint(Squad squad) {
        Set<Unit> enemyBuildings = gameState.getEnemyBuildings();
        if (!enemyBuildings.isEmpty()) {
            Unit closestBuilding = closestHostileUnit(squad.getCenter(), new ArrayList<>(enemyBuildings));
            if (closestBuilding != null) {
                Position buildingPos = closestBuilding.getPosition();
                return buildingPos;
            }
        }
        
        return informationManager.getRallyPoint();
    }

    /**
     * Rallies to the squad closest to the enemy main base, otherwise
     * defaults to the main or natural expansion.
     *
     * Exempts the given squad as a rally candidate.
     *
     * Rallys to the closest squad
     * @return TilePosition to rally squad to
     */
    private Position getRallyPoint(Squad squad) {
        List<Squad> eligibleSquads = fightSquads.stream()
                .filter(s -> s != squad)
                .filter(s -> s.getStatus() == SquadStatus.FIGHT)
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

        return informationManager.getRallyPoint();
    }

    /**
     * Determines whether a squad should continue or change its current role.
     * @param squad Squad to evaluate
     */
    private void evaluateSquadRole(Squad squad) {
        final boolean closeThreats = !enemyUnitsNearSquad(squad).isEmpty();

        SquadStatus squadStatus = squad.getStatus();
        if (!closeThreats && squadStatus == SquadStatus.REGROUP) {
            return;
        }

        int moveOutThreshold = calculateMoveOutThreshold(squad);
        if (closeThreats || squad.size() >= moveOutThreshold) {
            simulateFightSquad(squad);
        } else {
            rallySquad(squad);
        }
    }

    private int calculateMoveOutThreshold(Squad squad) {
        UnitType type = squad.getType();
        switch (type) {  
            case Zerg_Zergling:
                return calculateZerglingMoveOutThreshold(squad);
            case Zerg_Mutalisk:
                if (gameState.getOpponentRace() == Race.Zerg) {
                    return 2;
                }
                return 5;
            case Zerg_Hydralisk:
                return 10;
            case Zerg_Lurker:
                return 1;
            default:
                return defaultMoveOutThreshold();
        }
    }

    private int defaultMoveOutThreshold() {
        int staticDefensePenalty = min(informationManager.getEnemyHostileToGroundBuildingsCount(), 6);
        int moveOutThreshold = 8 * (1 + staticDefensePenalty);
        StrategyTracker strategyTracker = gameState.getStrategyTracker();
        if (strategyTracker.isDetectedStrategy("2Gate")) {
            final int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            moveOutThreshold += zealots * 2;
        }

        return Math.min(moveOutThreshold, 40);
    }

    private int calculateZerglingMoveOutThreshold(Squad squad) {
        StrategyTracker strategyTracker = gameState.getStrategyTracker();

        if (gameState.isCannonRushed()) {
            Set<Position> basePositions = gameState.getBaseData().getMyBases()
                .stream()
                .map(Base::getCenter)
                .collect(Collectors.toSet());
            ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
            int completedCannons = tracker.getCompletedBuildingCountNearPositions(UnitType.Protoss_Photon_Cannon, basePositions, 512);
            if (completedCannons == 0) {
                return 1;
            }
            return Math.max(6, completedCannons * 3);
        }

        int staticDefensePenalty = Math.min(informationManager.getEnemyHostileToGroundBuildingsCount(), 6);
        int baseThreshold = gameState.getOpponentRace() == Race.Zerg ? 4 : 12;
        int threshold = baseThreshold * (1 + staticDefensePenalty);

        if (strategyTracker.isDetectedStrategy("2Gate")) {
            int zealots = gameState.enemyUnitCount(UnitType.Protoss_Zealot);
            threshold += zealots * 3;
        }

        int maxThreshold = gameState.getOpponentRace() == Race.Zerg ? 36 : 48;
        if (squad.getStatus() == SquadStatus.FIGHT) {
            maxThreshold = (int) (maxThreshold * 0.75);
        }

        return Math.min(threshold, maxThreshold);
    }

    /**
     * TODO: All simulation should be handled by a CombatSimulator passed to the squad
     * @param squad
     */
    private void simulateFightSquad(Squad squad) {
        // Skip mutalisk squads as they handle their own tactics
        if (squad instanceof MutaliskSquad || squad instanceof ScourgeSquad) {
            return;
        }

        HashSet<ManagedUnit> managedFighters = squad.getMembers();

        // Handle Lurker retreat and contain (unchanged)
        if (squad.getType() == UnitType.Zerg_Lurker) {
            squad.setStatus(SquadStatus.FIGHT);
            for (ManagedUnit managedUnit : managedFighters) {
                managedUnit.setRole(UnitRole.FIGHT);
                assignEnemyTarget(managedUnit, squad);
            }
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

        // Handle building targeting when no visible units
        Set<Position> enemyBuildingPositions = gameState.getLastKnownPositionsOfBuildings();
        Set<Unit> enemyUnits = gameState.getDetectedEnemyUnits();

        // If no enemy units, buildings, or known building locations exist, disband squad to transition to scout
        if (enemyUnits.isEmpty() && enemyBuildingPositions.isEmpty()) {
            ScoutData scoutData = gameState.getScoutData();
            if (!scoutData.isEnemyBuildingLocationKnown()) {
                squad.setShouldDisband(true);
                return;
            }
        }

        if (enemyUnits.isEmpty() && !enemyBuildingPositions.isEmpty()) {
            double closestDistance = Double.MAX_VALUE;
            Position closestPosition = null;
            for (Position position: enemyBuildingPositions) {
                double distance = squad.getCenter().getDistance(position);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestPosition = position;
                }
            }
            squad.setStatus(SquadStatus.FIGHT);
            for (ManagedUnit managedUnit : squad.getMembers()) {
                managedUnit.setRole(UnitRole.FIGHT);
                managedUnit.setMovementTargetPosition(closestPosition.toTilePosition());
            }
            return;
        }

        // Hysteresis gate: allow retreat to override fight locks; block fight if retreat-locked
        int now = game.getFrameCount();
        boolean retreatLocked = squad.isRetreatLocked(now);
        boolean fightLocked = squad.isFightLocked(now);

        if (squad.getStatus() == SquadStatus.RETREAT && retreatLocked) {
            Position naturalRallyPoint = informationManager.getRallyPoint();
            HashMap<ManagedUnit, Position> retreatTargets = squad instanceof ZerglingSquad
                    ? computeZerglingRetreatTargets(squad)
                    : null;
            for (ManagedUnit managedUnit : managedFighters) {
                managedUnit.setRole(UnitRole.RETREAT);
                managedUnit.markRetreatStart(now);
                Integer retreatStartFrame = managedUnit.getRetreatStartFrame();
                if (retreatStartFrame != null && now - retreatStartFrame >= 240) {
                    managedUnit.setRetreatTarget(naturalRallyPoint);
                } else if (retreatTargets != null) {
                    Position rt = retreatTargets.get(managedUnit);
                    managedUnit.setRetreatTarget(rt);
                } else {
                    managedUnit.setRetreatTarget(managedUnit.getRetreatPosition());
                }
            }
            return;
        }
        if (squad.getStatus() == SquadStatus.FIGHT && fightLocked) {
            for (ManagedUnit managedUnit : managedFighters) {
                managedUnit.setRole(UnitRole.FIGHT);
                assignEnemyTarget(managedUnit, squad);
            }
            return;
        }

        // Use combat simulator for engagement decision
        CombatSimulator.CombatResult result = defaultCombatSimulator.evaluate(squad, gameState);

        switch (result) {
            case RETREAT:
                squad.setStatus(SquadStatus.RETREAT);
                Position naturalRallyPoint = informationManager.getRallyPoint();
                HashMap<ManagedUnit, Position> retreatTargets = squad instanceof ZerglingSquad
                        ? computeZerglingRetreatTargets(squad)
                        : null;
                for (ManagedUnit managedUnit : managedFighters) {
                    managedUnit.setRole(UnitRole.RETREAT);
                    managedUnit.markRetreatStart(now);
                    Integer retreatStartFrame = managedUnit.getRetreatStartFrame();
                    if (retreatStartFrame != null && now - retreatStartFrame >= 240) {
                        managedUnit.setRetreatTarget(naturalRallyPoint);
                    } else if (retreatTargets != null) {
                        managedUnit.setRetreatTarget(retreatTargets.get(managedUnit));
                    } else {
                        managedUnit.setRetreatTarget(managedUnit.getRetreatPosition());
                    }
                }
                squad.startRetreatLock(now);
                break;

            case ENGAGE:
                squad.setStatus(SquadStatus.FIGHT);
                for (ManagedUnit managedUnit : managedFighters) {
                    managedUnit.setRole(UnitRole.FIGHT);
                    managedUnit.clearRetreatStart();
                    assignEnemyTarget(managedUnit, squad);
                }
                // Only start fight lock if not currently retreat-locked
                if (!retreatLocked) {
                    squad.startFightLock(now);
                }
                break;

            case REGROUP:
                squad.setStatus(SquadStatus.REGROUP);
                for (ManagedUnit managedUnit : managedFighters) {
                    managedUnit.clearRetreatStart();
                }
                break;
            default:
                return;
        }
    }

    /**
     * Computes a shared retreat anchor for zerglings with perpendicular jitter per unit.
     * Anchor: vector from squad center away from closest enemy cluster.
     * Jitter: perpendicular offsets to reduce clumping.
     */
    private HashMap<ManagedUnit, Position> computeZerglingRetreatTargets(Squad squad) {
        HashMap<ManagedUnit, Position> result = new HashMap<>();
        Position center = squad.getCenter();
        List<Unit> enemiesNear = enemyUnitsNearSquad(squad);
        if (enemiesNear.isEmpty()) {
            for (ManagedUnit mu : squad.getMembers()) {
                result.put(mu, center);
            }
            return result;
        }

        // Compute average enemy position as threat center
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

        // Retreat anchor = center + normalized away vector * 192
        double dx = center.getX() - ex;
        double dy = center.getY() - ey;
        double len = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
        double scale = 192.0 / len;
        double ax = center.getX() + dx * scale;
        double ay = center.getY() + dy * scale;

        // Clamp anchor to map bounds
        int maxX = game.mapWidth() * 32 - 1;
        int maxY = game.mapHeight() * 32 - 1;
        ax = Math.max(0, Math.min(ax, maxX));
        ay = Math.max(0, Math.min(ay, maxY));

        // Perpendicular and away unit vectors
        double px = -dy / len;
        double py = dx / len;
        double ux = dx / len;
        double uy = dy / len;

        int i = 0;
        for (ManagedUnit mu : squad.getMembers()) {
            // Alternate offsets left/right and increase magnitude slightly per unit
            int side = (i % 2 == 0) ? 1 : -1;
            double mag = 16 + (i / 2) * 8; // 16,24,24,32,...
            int rx = (int) Math.round(ax + side * px * mag);
            int ry = (int) Math.round(ay + side * py * mag);
            // Clamp jittered targets to map bounds
            rx = Math.max(0, Math.min(rx, maxX));
            ry = Math.max(0, Math.min(ry, maxY));

            // Ensure walkability: if not walkable, scan along away vector to find a walkable point
            if (!game.isWalkable(new WalkPosition(rx, ry))) {
                int bestX = rx;
                int bestY = ry;
                boolean found = false;
                for (int t = 256; t >= 0; t -= 16) {
                    int cx = (int) Math.round(rx + ux * t);
                    int cy = (int) Math.round(ry + uy * t);
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
                        int cx = (int) Math.round(rx - ux * t);
                        int cy = (int) Math.round(ry - uy * t);
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

        double normalizedDx = totalDx / totalWeight;
        double normalizedDy = totalDy / totalWeight;

        double length = Math.sqrt(normalizedDx * normalizedDx + normalizedDy * normalizedDy);
        if (length > 0) {
            normalizedDx = (normalizedDx / length) * 192;
            normalizedDy = (normalizedDy / length) * 192;
        } else {
            normalizedDx = 192;
            normalizedDy = 0;
        }

        int retreatX = unitPos.getX() + (int) normalizedDx;
        int retreatY = unitPos.getY() + (int) normalizedDy;

        int maxX = game.mapWidth() * 32 - 1;
        int maxY = game.mapHeight() * 32 - 1;
        retreatX = Math.max(0, Math.min(retreatX, maxX));
        retreatY = Math.max(0, Math.min(retreatY, maxY));

        return new Position(retreatX, retreatY);
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
            if ((int) enemyUnit.getPosition().getDistance(squad.getCenter()) > 256) {
                continue;
            }
            try {
                simulator.addAgentB(agentFactory.of(enemyUnit));
            } catch (ArithmeticException e) {
                return false;
            }
        }

        simulator.simulate(150); // Simulate 15 seconds

        if (simulator.getAgentsB().isEmpty()) {
            return true;
        }

        if (simulator.getAgentsA().isEmpty()) {
            return false;
        }

        float percentRemaining = (float) simulator.getAgentsA().size() / managedDefenders.size();
        if (percentRemaining >= 0.50) {
            return true;
        }

        return false;
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
    }

    private void addManagedFighter(ManagedUnit managedUnit) {
        UnitType type = managedUnit.getUnitType();
        Squad squad;
        
        // Special handling for Mutalisks - rally to closest active squad
        if (type == UnitType.Zerg_Mutalisk) {
            squad = findClosestMutaliskSquad(managedUnit);
        } else {
            squad = findCloseSquad(managedUnit, type);   
        }
        if (squad == null) {
            squad = newFightSquad(type);
        }
        
        squad.addUnit(managedUnit);
        simulateFightSquad(squad);
    }

    private Squad findCloseSquad(ManagedUnit managedUnit, UnitType type) {
        for (Squad squad: fightSquads) {
            if (squad.getType() != type) {
                continue;
            }
            if (squad.distance(managedUnit) < 256) {
                return squad;
            }
        }

        return null;
    }

    /**
     * Finds the closest Mutalisk squad that is in RALLY or FIGHT status.
     * For FIGHT squads, only join if within 128 pixels to prevent regroup with mutalisks at base.
     */
    private Squad findClosestMutaliskSquad(ManagedUnit managedUnit) {
        Squad closestSquad = null;
        double closestDistance = Double.MAX_VALUE;

        for (Squad squad : fightSquads) {
            if (squad.getType() != UnitType.Zerg_Mutalisk) {
                continue;
            }

            if (squad.getStatus() == SquadStatus.RALLY || squad.getStatus() == SquadStatus.FIGHT) {
                double distance = squad.distance(managedUnit);

                boolean canJoin = squad.getStatus() == SquadStatus.RALLY ||
                                distance < MUTALISK_JOIN_DISTANCE;

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

        if (type == UnitType.Zerg_Zergling) {
            newSquad = new ZerglingSquad();
            newSquad.setType(type);
        } else if (type == UnitType.Zerg_Mutalisk) {
            newSquad = new MutaliskSquad();
        } else if (type == UnitType.Zerg_Scourge) {
            newSquad = new ScourgeSquad();
        } else {
            newSquad = new Squad();
            newSquad.setType(type);
        }

        newSquad.setStatus(SquadStatus.FIGHT);
        newSquad.setRallyPoint(this.getRallyPoint(newSquad));
        fightSquads.add(newSquad);
        return newSquad;
    }

    private void addManagedOverlord(ManagedUnit overlord) {
        overlords.addUnit(overlord);
    }

    public void removeManagedUnit(ManagedUnit managedUnit) {
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
     * Mutalisk squads should all focus the same unit.
     *
     * Currently, all other unit squads will be assigned the closest enemy unit.
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
        List<Unit> enemyUnits = new ArrayList<>();
        enemyUnits.addAll(gameState.getVisibleEnemyUnits());

        List<Unit> filtered = new ArrayList<>();
        // Attempt to find the closest enemy OUTSIDE fog of war
        for (Unit enemyUnit: enemyUnits) {
            if (unit.getType() == UnitType.Zerg_Lurker && !enemyUnit.isFlying() && enemyUnit.isDetected()) {
                filtered.add(enemyUnit);
                continue;
            }
            if (unit.canAttack(enemyUnit) && enemyUnit.isDetected() && 
                !util.Filter.isLowPriorityCombatTarget(enemyUnit.getType())) {
                filtered.add(enemyUnit);
            }
        }

        if (filtered.isEmpty()) {
            managedUnit.setMovementTargetPosition(informationManager.pollScoutTarget(false));
            return;
        }

        if (gameState.isCannonRushed()) {
            Set<Unit> proxied = gameState.getObservedUnitTracker().getProxiedBuildings();
            List<Unit> proxiedTargets = filtered.stream()
                    .filter(proxied::contains)
                    .collect(Collectors.toList());
            if (!proxiedTargets.isEmpty()) {
                filtered = proxiedTargets;
            }
        }

        Unit closestEnemy = closestHostileUnit(unit, filtered);
        Unit ft = closestEnemy;
        if (squad.getType() == UnitType.Zerg_Mutalisk) {
            if (squad.getTarget() == null) {
                squad.setTarget(closestEnemy);
            }
            ft = squad.getTarget();
        }
        managedUnit.setFightTarget(ft);
    }

    private void debugPainters() { }

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
                    overlord.setRallyPoint(informationManager.getRallyPoint());
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
            .filter(squad -> squad.getType() == UnitType.Zerg_Hydralisk || 
                           squad.getType() == UnitType.Zerg_Mutalisk)
            .sorted((s1, s2) -> Integer.compare(s2.size(), s1.size())) // Sort descending by size
            .collect(Collectors.toList());
    }

    private List<Squad> getSquadsByType(UnitType type) {
        return fightSquads.stream()
            .filter(squad -> squad.getType() == type)
            .collect(Collectors.toList());
    }

    public Squad findBestMutaliskSquadToRallyTo(Squad currentSquad) {
        List<Squad> mutaliskSquads = getSquadsByType(UnitType.Zerg_Mutalisk);
        mutaliskSquads.removeIf(squad -> squad == currentSquad);

        if (mutaliskSquads.isEmpty()) {
            return null;
        }

        Position currentCenter = currentSquad.getCenter();
        mutaliskSquads.sort((s1, s2) -> {
            int sizeCompare = Integer.compare(s2.size(), s1.size());
            if (sizeCompare != 0) {
                return sizeCompare;
            }
            return Double.compare(
                currentCenter.getDistance(s1.getCenter()),
                currentCenter.getDistance(s2.getCenter())
            );
        });

        return mutaliskSquads.get(0);
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


