import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import info.BaseData;
import info.GameState;
import info.UnitTypeCount;
import info.map.BuildingPlanner;
import info.map.GroundPath;
import info.map.MapTile;
import learning.Record;
import learning.OpponentRecord;
import strategy.buildorder.BuildOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Debug {

    private static int BASE_MINERAL_DISTANCE = 300;

    private BWEM bwem;
    private Game game;

    private BuildOrder opener;
    private OpponentRecord opponentRecord;

    private GameState gameState;

    public Debug(BWEM bwem, Game game, BuildOrder opener, OpponentRecord opponentRecord, GameState gameState) {
        this.bwem = bwem;
        this.game = game;
        this.opener = opener;
        this.opponentRecord = opponentRecord;
        this.gameState = gameState;
    }

    public void onFrame() {
        drawBases();

        game.drawTextScreen(4, 8, "Opponent: " + opponentRecord.getName() + " " + getOpponentRecord());
        game.drawTextScreen(4, 16, "GameMap: " + game.mapFileName());
        game.drawTextScreen(4, 24, "Opener: " + opener.getName() + " " + getOpenerRecord(), Text.White);
        game.drawTextScreen(4, 32, "BuildOrder: " + gameState.getActiveBuildOrder().getName() + " " + getBuildOrderRecord(), Text.White);
        game.drawTextScreen(4, 40, "Frame: " + game.getFrameCount());

        drawUnitCount();
        //debugGameMap();
        //drawAllBasePaths();
        debugBuildingPlanner();
        debugStaticDefenseCoverage();
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
        int y = 128;
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
                        (mapTile.getTile().toPosition()),
                        (mapTile.getTile().add(new TilePosition(1,1)).toPosition()),
                        Color.White);
            }
        }
    }

    private void debugStaticDefenseCoverage() {
        for (Position p: gameState.getStaticDefenseCoverage()) {
            game.drawCircleMap(p, 1, Color.Red);
        }
    }

    private void debugBuildingPlanner() {
        BuildingPlanner buildingPlanner = gameState.getBuildingPlanner();
        BaseData baseData = gameState.getBaseData();
        if (buildingPlanner == null) {
            return;
        }
        for (Base base: baseData.getMyBases()) {
            //buildingPlanner.debugBaseCreepTiles(base);
            //buildingPlanner.debugBaseChoke(base);
            //buildingPlanner.debugLocationForTechBuilding(base, UnitType.Zerg_Spawning_Pool);
            //buildingPlanner.debugReserveTiles();
            //buildingPlanner.debugNextCreepColonyLocation(base);
            //buildingPlanner.debugMineralBoundingBox(base);
            //buildingPlanner.debugGeyserBoundingBox(base);
            //buildingPlanner.debugMacroHatcheryLocation(gameState.getOpponentRace(), baseData);
        }
    }
}
