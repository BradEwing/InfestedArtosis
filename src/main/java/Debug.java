import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.TilePosition;
import bwapi.Unit;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import info.GameState;
import info.UnitTypeCount;
import info.map.GroundPath;
import info.map.MapTile;
import learning.OpponentRecord;
import learning.OpenerRecord;
import learning.StrategyRecord;
import strategy.openers.Opener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Debug {

    private static int BASE_MINERAL_DISTANCE = 300;

    private BWEM bwem;
    private Game game;

    private Opener opener;
    private OpponentRecord opponentRecord;

    private GameState gameState;

    public Debug(BWEM bwem, Game game, Opener opener, OpponentRecord opponentRecord, GameState gameState) {
        this.bwem = bwem;
        this.game = game;
        this.opener = opener;
        this.opponentRecord = opponentRecord;
        this.gameState = gameState;
    }

    public void onFrame() {
        // Draw bases
        for (final Base b : bwem.getMap().getBases()) {
            game.drawBoxMap(
                    b.getLocation().toPosition(),
                    b.getLocation().toPosition().add(new Position(127, 95)),
                    Color.Blue);
        }

        game.drawTextScreen(4, 8, "Opponent: " + opponentRecord.getName() + " " + getOpponentRecord());
        game.drawTextScreen(4, 16, "GameMap: " + game.mapFileName());
        game.drawTextScreen(4, 24, "Opener: " + opener.getNameString() + " " + getOpenerRecord(), Text.White);
        game.drawTextScreen(4, 32, "Strategy: " + gameState.getActiveStrategy().getName() + " " + getStrategyRecord(), Text.White);
        game.drawTextScreen(4, 40, "Frame: " + game.getFrameCount());

        drawUnitCount();
        drawBases();
        //debugGameMap();
        //drawAllBasePaths();
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

    private void drawBases() {
        for (Unit u: gameState.getBaseData().baseHatcheries()) {
            game.drawCircleMap(u.getPosition(), BASE_MINERAL_DISTANCE, Color.Teal);
        }
    }

    private String getOpenerRecord() {
        OpenerRecord openerRecord = opponentRecord.getOpenerRecord().get(opener.getNameString());
        return String.format("%s_%s", openerRecord.getWins(), openerRecord.getLosses());
    }

    private String getOpponentRecord() {
        return String.format("%s_%s", opponentRecord.getWins(), opponentRecord.getLosses());
    }

    private String getStrategyRecord() {
        StrategyRecord strategyRecord = opponentRecord.getStrategyRecordMap().get(gameState.getActiveStrategy().getName());
        return String.format("%s_%s", strategyRecord.getWins(), strategyRecord.getLosses());
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
            game.drawTextMap(
                    (mapTile.getTile().getX() * 32) + 8,
                    (mapTile.getTile().getY() * 32) + 8,
                    String.valueOf(mapTile.isBuildable()),
                    Text.White);
        }
    }
}
