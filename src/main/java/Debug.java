import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Race;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwapi.WalkPosition;
import bwem.Base;
import config.Config;
import info.BaseData;
import info.GameState;
import info.ResourceCount;
import info.ScoutData;
import info.UnitTypeCount;
import info.map.BuildingPlanner;
import info.map.GroundPath;
import info.map.MapTile;
import info.tracking.ObservedBullet;
import info.tracking.ObservedUnitTracker;
import info.tracking.PsiStormTracker;
import learning.Record;
import learning.OpponentRecord;
import strategy.buildorder.BuildOrder;
import unit.managed.ManagedUnit;
import unit.squad.CombatSimulator.CombatResult;
import unit.squad.Squad;
import unit.squad.SquadManager;
import unit.squad.SquadStatus;
import unit.squad.horizon.HorizonCombatSimulator;
import unit.squad.horizon.HorizonCombatSimulator.DebugSnapshot;
import unit.squad.horizon.HorizonCombatSimulator.UnitDebugEntry;
import unit.managed.UnitRole;
import util.Arc;
import macro.plan.Plan;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Debug is responsible for drawing debug information to the in-game screen.
 * 
 * Values are controlled by the Config class, which is loaded from the .env file.
 */
public class Debug {

    private Game game;
    private Config config;

    private BuildOrder opener;
    private OpponentRecord opponentRecord;

    private GameState gameState;
    private SquadManager squadManager;

    public Debug(Game game, BuildOrder opener, OpponentRecord opponentRecord, GameState gameState, Config config, SquadManager squadManager) {
        this.game = game;
        this.opener = opener;
        this.opponentRecord = opponentRecord;
        this.gameState = gameState;
        this.config = config;
        this.squadManager = squadManager;
    }

    public void onFrame() {
        if (config.debugHud) {
            game.drawTextScreen(4, 8, "Opponent: " + opponentRecord.getName() + " " + getOpponentRecord());
            game.drawTextScreen(4, 16, "GameMap: " + game.mapFileName());
            game.drawTextScreen(4, 24, "Opener: " + opener.getName() + " " + getOpenerRecord(), Text.White);
            game.drawTextScreen(4, 32, "BuildOrder: " + gameState.getActiveBuildOrder().getName() + " " + getBuildOrderRecord(), Text.White);
            game.drawTextScreen(4, 40, "Frame: " + game.getFrameCount());
        }

        if (config.debugBases) {
            drawBases();
        }
        if (config.debugUnitCount) {
            drawUnitCount();
        }
        if (config.debugGameMap) {
            debugGameMap();
        }
        if (config.debugBasePaths) {
            drawAllBasePaths();
        }
        if (config.debugStaticDefenseCoverage) {
            debugStaticDefenseCoverage();
        }
        if (config.debugPsiStorms) {
            debugPsiStorms();
        }
        if (config.debugAccessibleWalkPositions) {
            debugAccessibleWalkPositions();
        }
        if (config.debugBlockingMinerals) {
            debugBlockingMinerals();
        }
        if (config.debugMainBaseTiles) {
            debugMainBaseTiles();
        }
        if (config.debugManagedUnits) {
            for (ManagedUnit managedUnit : gameState.getManagedUnits()) {
                debugManagedUnit(managedUnit);
            }
        }

        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        BaseData baseData = gameState.getBaseData();
        if (buildingPlanner != null && baseData != null) {
            for (Base base : baseData.getMyBases()) {
                if (config.debugBaseCreepTiles) {
                    debugBaseCreepTiles(buildingPlanner, base);
                }
                if (config.debugBaseChoke) {
                    debugBaseChoke(buildingPlanner, base);
                }
                if (config.debugLocationForTechBuilding) {
                    debugLocationForTechBuilding(buildingPlanner, base, UnitType.Zerg_Spawning_Pool);
                }
                if (config.debugNextCreepColonyLocation) {
                    debugNextCreepColonyLocation(buildingPlanner, base);
                }
                if (config.debugNextSporeColonyLocation) {
                    debugNextSporeColonyLocation(buildingPlanner, base);
                }
                if (config.debugMineralBoundingBox) {
                    debugMineralBoundingBox(buildingPlanner, base);
                }
                if (config.debugGeyserBoundingBox) {
                    debugGeyserBoundingBox(buildingPlanner, base);
                }
            }
            if (config.debugReserveTiles) {
                debugReserveTiles(buildingPlanner);
            }
            if (config.debugMacroHatcheryLocation) {
                debugMacroHatcheryLocation(buildingPlanner, gameState.getOpponentRace(), baseData);
            }
        }

        if (config.debugSquads) {
            debugSquads();
        }
        if (config.debugContainment) {
            debugContainment();
        }
        if (config.debugCombatSim) {
            debugCombatSim();
        }
        if (config.debugProductionQueue) {
            debugProductionQueue();
        }
        if (config.debugInProgressQueue) {
            debugInProgressQueue();
        }
        if (config.debugScheduledPlannedItems) {
            debugScheduledPlannedItems();
        }
        if (config.debugResourceReservations) {
            debugResourceReservations();
        }
    }

    private void debugBaseCreepTiles(BuildingPlanner buildingPlanner, Base base) {
        boolean excludeGeyserTiles = gameState.getOpponentRace() != Race.Zerg;
        Set<TilePosition> creepTiles = buildingPlanner.findSurroundingCreepTiles(base, excludeGeyserTiles, true);
        for (TilePosition tp: creepTiles) {
            game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Brown);
        }
    }

    private void debugBaseChoke(BuildingPlanner buildingPlanner, Base base) {
        Position closestChoke = buildingPlanner.closestChokeToBase(base);
        if (closestChoke != null) {
            game.drawCircleMap(closestChoke, 2, Color.Yellow);
        }
    }

    private void debugLocationForTechBuilding(BuildingPlanner buildingPlanner, Base base, UnitType unitType) {
        TilePosition tp = buildingPlanner.getLocationForTechBuilding(base, unitType);
        if (tp != null) {
            game.drawBoxMap(tp.toPosition(), tp.add(unitType.tileSize()).toPosition(), Color.White);
        }
    }

    private void debugReserveTiles(BuildingPlanner buildingPlanner) {
        for (TilePosition tp: buildingPlanner.getReservedTiles()) {
            game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.White);
        }
    }

    private void debugNextCreepColonyLocation(BuildingPlanner buildingPlanner, Base base) {
        TilePosition cc = buildingPlanner.getLocationForCreepColony(base, gameState.getOpponentRace());
        if (cc != null) {
            game.drawBoxMap(cc.toPosition(), cc.add(new TilePosition(2, 2)).toPosition(), Color.White);
        }
    }

    private void debugNextSporeColonyLocation(BuildingPlanner buildingPlanner, Base base) {
        TilePosition sc = buildingPlanner.getLocationForSporeColony(base);
        if (sc != null) {
            game.drawBoxMap(sc.toPosition(), sc.add(new TilePosition(2, 2)).toPosition(), Color.Cyan);
        }
    }

    private void debugMineralBoundingBox(BuildingPlanner buildingPlanner, Base base) {
        HashSet<TilePosition> tiles = buildingPlanner.mineralBoundingBox(base);
        if (!tiles.isEmpty()) {
            for (TilePosition tp: tiles) {
                if (tp != null) {
                    game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Blue);
                }
            }
        }
    }

    private void debugGeyserBoundingBox(BuildingPlanner buildingPlanner, Base base) {
        HashSet<TilePosition> tiles = buildingPlanner.geyserBoundingBox(base);
        if (!tiles.isEmpty()) {
            for (TilePosition tp: tiles) {
                if (tp != null) {
                    game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Blue);
                }
            }
        }
    }

    private void debugMacroHatcheryLocation(BuildingPlanner buildingPlanner, Race opponentRace, BaseData baseData) {
        TilePosition location = buildingPlanner.getLocationForMacroHatchery(opponentRace, baseData);
        if (location != null) {
            UnitType hatchType = UnitType.Zerg_Hatchery;
            game.drawBoxMap(
                    location.toPosition(),
                    location.add(hatchType.tileSize()).toPosition(),
                    Color.Green
            );
        }
    }

    private void drawBases() {
        BaseData baseData = gameState.getBaseData();
        for (final Base b : baseData.getMyBases()) {
            game.drawBoxMap(
                    b.getLocation().toPosition(),
                    b.getLocation().toPosition().add(new Position(127, 95)),
                    Color.Blue
            );
        }
        for (final Base b : baseData.availableBases()) {
            game.drawBoxMap(
                    b.getLocation().toPosition(),
                    b.getLocation().toPosition().add(new Position(127, 95)),
                    Color.White
            );
            game.drawTextMap(
                    b.getLocation().toPosition(),
                    String.format("(%d, %d)", b.getLocation().x, b.getLocation().y),
                    Text.White
            );
        }
        for (final Base b : baseData.getEnemyBases()) {
            game.drawBoxMap(
                    b.getLocation().toPosition(),
                    b.getLocation().toPosition().add(new Position(127, 95)),
                    Color.Red
            );
        }
    }

    private void drawUnitCount() {
        int x = 4;
        int y = 160;
        game.drawTextScreen(x, y, "Unit Count");

        UnitTypeCount unitTypeCount = gameState.getUnitTypeCount();
        for (Map.Entry<UnitType, Integer> entry: unitTypeCount.getCountLookup().entrySet()) {
            y += 8;
            game.drawTextScreen(x, y, entry.getKey().toString() + ": " + entry.getValue().toString());
        }
    }

    private String getOpenerRecord() {
        Record openerRecord = opponentRecord.getOpenerRecord().get(opener.getName());
        return String.format("%s_%s", openerRecord.getWins(), openerRecord.getLosses());
    }

    private String getOpponentRecord() {
        return String.format("%s_%s", opponentRecord.getWins(), opponentRecord.getLosses());
    }

    private String getBuildOrderRecord() {
        String activeName = gameState.getActiveBuildOrder().getName();
        String openerName = opener.getName();

        if (activeName.equals(openerName)) {
            Map<String, Record> openerMap = opponentRecord.getOpenerRecord();
            Record rec = (openerMap != null) ? openerMap.get(activeName) : null;
            return (rec != null) ? String.format("%s_%s", rec.getWins(), rec.getLosses()) : "0_0";
        }

        Map<String, Record> boMap = opponentRecord.getBuildOrderRecord();
        Record rec = (boMap != null) ? boMap.get(activeName) : null;
        return (rec != null) ? String.format("%s_%s", rec.getWins(), rec.getLosses()) : "0_0";
    }

    private void drawAllBasePaths() {
        HashMap<Base, GroundPath> pathMap = this.gameState.getBaseData().getBasePaths();
        for (GroundPath path: pathMap.values()) {
            drawPath(path.getPath());
        }
    }

    private void drawPath(List<MapTile> tiles) {
        for (MapTile tile: tiles) {
            TilePosition tp = tile.getTile();
            game.drawBoxMap(
                    tp.toPosition(),
                    tp.add(new TilePosition(1, 1)).toPosition(),
                    Color.Yellow
            );
        }
    }

    private void debugGameMap() {
        for (MapTile mapTile : gameState.getGameMap().getHeatMap()) {
            if (mapTile.isBuildable()) {
                game.drawBoxMap(
                        mapTile.getTile().toPosition(),
                        mapTile.getTile().add(new TilePosition(1,1)).toPosition(),
                        Color.White);
            }
        }
    }

    private void debugStaticDefenseCoverage() {
        for (Position p: gameState.getStaticDefenseCoverage()) {
            game.drawCircleMap(p, 1, Color.Red);
        }
    }

    private void debugPsiStorms() {
        PsiStormTracker tracker = gameState.getPsiStormTracker();
        Set<ObservedBullet> activeStorms = tracker.getActiveStorms();
        int currentFrame = game.getFrameCount();

        for (ObservedBullet storm : activeStorms) {
            game.drawCircleMap(storm.getLastKnownLocation(), PsiStormTracker.STORM_RADIUS, Color.Red, false);

            int remainingFrames = tracker.getRemainingFrames(storm, currentFrame);
            String timeText = String.format("Storm: %d", remainingFrames);
            game.drawTextMap(storm.getLastKnownLocation(), timeText, Text.White);
        }
    }

    /**
     * Debug visualization for accessible WalkPositions from flood fill algorithm.
     * Draws small dots at each accessible WalkPosition.
     */
    private void debugAccessibleWalkPositions() {
        try {
            if (gameState.getGameMap() != null && gameState.getGameMap().getAccessibleWalkPositions() != null) {
                for (WalkPosition wp : gameState.getGameMap().getAccessibleWalkPositions()) {
                    if (Math.random() < 0.01) {
                        game.drawDotMap(wp.toPosition(), Color.Green);
                    }
                }
            }
        } catch (IllegalStateException e) {
        }
    }

    private void debugMainBaseTiles() {
        if (gameState.getGameMap() == null) return;
        for (TilePosition tp : gameState.getGameMap().getMainBaseTiles()) {
            game.drawBoxMap(tp.toPosition(), tp.add(new TilePosition(1, 1)).toPosition(), Color.Cyan);
        }
    }

    private void debugBlockingMinerals() {
        if (gameState.getGameMap() == null) return;
        for (Unit mineral : gameState.getGameMap().getBlockingMinerals()) {
            if (mineral == null) {
                continue;
            }
            Position pos = mineral.getPosition();
            game.drawCircleMap(pos, 32, Color.Orange, false);
            game.drawTextMap(pos, "Blocker", Text.Orange);
        }
    }

    public void debugEnemyTargets() {
        if (!config.debugEnemyTargets) return;
        
        ScoutData scoutData = gameState.getScoutData();
        ObservedUnitTracker tracker = gameState.getObservedUnitTracker();
        for (Unit target: tracker.getBuilding()) {
            game.drawCircleMap(target.getPosition(), 3, Color.Yellow);
        }
        for (Unit target: tracker.getVisibleEnemyUnits()) {
            game.drawCircleMap(target.getPosition(), 3, Color.Red);
        }
        for (TilePosition tilePosition: scoutData.getEnemyBuildingPositions()) {
            game.drawCircleMap(tilePosition.toPosition(), 2, Color.Orange);
        }
    }

    private void debugSquads() {
        int currentFrame = game.getFrameCount();
        for (Squad squad: squadManager.fightSquads) {
            Position center = squad.getCenter();
            for (ManagedUnit mu : squad.getMembers()) {
                game.drawLineMap(center, mu.getUnit().getPosition(), Color.White);
            }

            int mergeRadius = squad.isAirSquad()
                    ? (int) SquadManager.AIR_JOIN_DISTANCE
                    : (int) SquadManager.SQUAD_MERGE_DISTANCE;
            int splitRadius = squad.isAirSquad()
                    ? SquadManager.AIR_SPLIT_DISTANCE
                    : SquadManager.GROUND_SPLIT_DISTANCE;
            game.drawCircleMap(center, mergeRadius, Color.Green);
            game.drawCircleMap(center, splitRadius, Color.Red);

            String label = formatCompositionSquadLabel(squad.getStatus(), squad.getSupply(), squad.getComposition());
            if (!squad.isMergeEligible(currentFrame)) {
                int lockFramesLeft = squad.getSplitFrame() + 100 - currentFrame;
                label += " lock=" + lockFramesLeft;
            }
            game.drawTextMap(center, label, Text.White);
        }
        for (Squad squad: squadManager.getDefenseSquads().values()) {
            Position center = squad.getCenter();
            for (ManagedUnit mu : squad.getMembers()) {
                game.drawLineMap(center, mu.getUnit().getPosition(), Color.White);
            }
            game.drawTextMap(center, String.format("Defenders: %s", squad.size()), Text.White);
        }
    }

    private void debugContainment() {
        for (Arc arc : squadManager.getActiveContainmentArcs()) {
            Position choke = arc.getCenter();
            game.drawCircleMap(choke, 10, Color.Yellow, true);
            game.drawTextMap(choke.getX() + 12, choke.getY() - 6, "Choke", Text.Yellow);
            List<Position> positions = arc.getPositions();
            if (!positions.isEmpty()) {
                Position midPoint = positions.get(positions.size() / 2);
                game.drawLineMap(choke, midPoint, Color.Yellow);
            }
            for (int i = 0; i < positions.size(); i++) {
                Position pos = positions.get(i);
                game.drawCircleMap(pos, 8, Color.Green, true);
                if (i > 0) {
                    game.drawLineMap(positions.get(i - 1), pos, Color.Green);
                }
            }
        }

        for (Squad squad : squadManager.fightSquads) {
            if (squad.getStatus() == SquadStatus.CONTAIN) {
                game.drawTextMap(squad.getCenter(), String.format("CONTAIN (%d)", squad.size()), Text.Green);
            }
        }
    }

    private String formatCompositionSquadLabel(SquadStatus status, int supply, Map<UnitType, Integer> composition) {
        StringBuilder sb = new StringBuilder();
        sb.append(status).append(" sup=").append(supply);
        for (Map.Entry<UnitType, Integer> entry : composition.entrySet()) {
            String shortName = entry.getKey().toString().replace("Zerg_", "");
            sb.append(" ").append(shortName).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    private void debugCombatSim() {
        for (Squad squad : squadManager.fightSquads) {
            if (!(squad.getCombatSimulator() instanceof HorizonCombatSimulator)) continue;
            HorizonCombatSimulator sim = (HorizonCombatSimulator) squad.getCombatSimulator();
            DebugSnapshot snap = sim.getLastSnapshots().get(squad.getId());
            if (snap == null) continue;
            if (game.getFrameCount() - snap.getCapturedFrame() > 24) continue;

            Color resultColor = snap.getResult() == CombatResult.ENGAGE ? Color.Green
                    : snap.getResult() == CombatResult.RETREAT ? Color.Red : Color.Yellow;

            game.drawCircleMap(snap.getSquadCenter(), (int) 256, resultColor);
            game.drawTextMap(snap.getSquadCenter().getX() - 40, snap.getSquadCenter().getY() - 20,
                    String.format("%s ratio=%.2f", snap.getResult(), snap.getOverallRatio()), Text.White);
            game.drawTextMap(snap.getSquadCenter().getX() - 40, snap.getSquadCenter().getY() - 10,
                    String.format("F=%.1f E=%.1f gR=%.2f cR=%.2f",
                            snap.getFriendlyTotal(), snap.getEnemyTotal(), snap.getGroundRatio(), snap.getCombinedRatio()), Text.White);

            for (UnitDebugEntry entry : snap.getFriendlyUnits()) {
                Color c = entry.isAdjacent() ? Color.Cyan : Color.Green;
                game.drawCircleMap(entry.getPosition(), 6, c);
                game.drawLineMap(entry.getPosition(), snap.getSquadCenter(), c);
                String shortName = entry.getType().toString().replace("Zerg_", "");
                game.drawTextMap(entry.getPosition().getX() + 8, entry.getPosition().getY() - 4,
                        String.format("%s %.1f", shortName, entry.getStrength()), Text.Green);
            }

            Position enemyCenter = snap.getEnemyCenter();
            for (UnitDebugEntry entry : snap.getEnemyUnits()) {
                Color enemyColor = entry.isFogOfWar() ? Color.Orange : Color.Red;
                game.drawCircleMap(entry.getPosition(), 6, enemyColor);
                if (enemyCenter != null) {
                    game.drawLineMap(entry.getPosition(), enemyCenter, enemyColor);
                }
                String shortName = entry.getType().toString()
                        .replace("Protoss_", "").replace("Terran_", "").replace("Zerg_", "");
                Text textColor = entry.isFogOfWar() ? Text.Orange : Text.Red;
                game.drawTextMap(entry.getPosition().getX() + 8, entry.getPosition().getY() - 4,
                        String.format("%s %.1f", shortName, entry.getStrength()), textColor);
            }
        }
    }

    public void debugManagedUnit(ManagedUnit managedUnit) {
        if (!config.debugManagedUnits) return;
        
        Position unitPosition = managedUnit.getUnit().getPosition();
        UnitRole role = managedUnit.getRole();
        
        if (role != null) {
            game.drawTextMap(unitPosition, role.toString(), Text.Default);
        }
        if (role == UnitRole.BUILDING) {
            return;
        }

        if (managedUnit.getMovementTargetPosition() != null) {
            Position movementPos = managedUnit.getMovementTargetPosition().toPosition();
            game.drawLineMap(unitPosition, movementPos, Color.White);
        }

        if (managedUnit.retreatTarget != null) {
            game.drawLineMap(unitPosition, managedUnit.retreatTarget, Color.Purple);
        }

        if (managedUnit.fightTarget != null) {
            Position fightTargetPos = managedUnit.fightTarget.getPosition();
            if (fightTargetPos != null) {
                game.drawLineMap(unitPosition, fightTargetPos, Color.Red);
            }
        }

        if (managedUnit.getPlan() != null && managedUnit.getPlan().getBuildPosition() != null) {
            Position buildPosition = managedUnit.getPlan().getBuildPosition().toPosition();
            game.drawLineMap(unitPosition, buildPosition, Color.Cyan);

            int distance = managedUnit.getUnit().getDistance(buildPosition);
            String distanceText = String.format("Distance: %d", distance);
            Position textPosition = unitPosition.add(new Position(8, 8));
            game.drawTextMap(textPosition, distanceText, Text.Cyan);
        }
    }

    public void debugProductionQueue() {
        if (!config.debugProductionQueue) return;
        
        int numDisplayed = 0;
        int x = 4;
        int y = 64;
        for (Plan plan : gameState.getProductionQueue()) {
            game.drawTextScreen(x, y, plan.getName() + " " + plan.getPriority(), Text.Green);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }

        if (numDisplayed < gameState.getProductionQueue().size()) {
            game.drawTextScreen(x, y, String.format("...%s more plans", gameState.getProductionQueue().size() - numDisplayed), Text.GreyGreen);
        }
    }

    public void debugInProgressQueue() {
        if (!config.debugInProgressQueue) return;
        
        int numDisplayed = 0;
        int x = 100;
        int y = 64;
        for (Plan plan : gameState.getAssignedPlannedItems().values()) {
            game.drawTextScreen(x, y, plan.getName() + " " + plan.getPriority(), Text.Green);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }
    }

    public void debugScheduledPlannedItems() {
        if (!config.debugScheduledPlannedItems) return;

        int numDisplayed = 0;
        int x = 196;
        int y = 64;
        for (Plan plan : gameState.getPlansScheduled()) {
            game.drawTextScreen(x, y, plan.getName() + " " + plan.getPriority(), Text.Green);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }
    }

    private void debugResourceReservations() {
        ResourceCount rc = gameState.getResourceCount();
        int x = 292;
        int y = 64;

        int ledgerMinerals = 0;
        int ledgerGas = 0;
        for (int[] entry : rc.getReservationLedger().values()) {
            ledgerMinerals += entry[0];
            ledgerGas += entry[1];
        }

        boolean drifted = ledgerMinerals != rc.getReservedMinerals() || ledgerGas != rc.getReservedGas();
        Text headerColor = drifted ? Text.Red : Text.White;
        game.drawTextScreen(x, y, String.format("Reserved: %dm %dg (ledger: %dm %dg)",
                rc.getReservedMinerals(), rc.getReservedGas(), ledgerMinerals, ledgerGas), headerColor);
        y += 8;

        int numDisplayed = 0;
        for (Map.Entry<String, int[]> entry : rc.getReservationLedger().entrySet()) {
            game.drawTextScreen(x, y, String.format("  %s: %dm %dg", entry.getKey(), entry.getValue()[0], entry.getValue()[1]), Text.GreyGreen);
            y += 8;
            numDisplayed += 1;
            if (numDisplayed == 10) {
                break;
            }
        }

        String warning = rc.getLastClampWarning();
        if (warning != null) {
            game.drawTextScreen(x, y, "[WARN: " + warning + "]", Text.Red);
        }
    }

}
