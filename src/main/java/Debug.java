import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwapi.UnitType;
import bwem.BWEM;
import bwem.Base;
import info.GameState;
import info.UnitTypeCount;
import learning.OpponentRecord;
import learning.OpenerRecord;
import learning.StrategyRecord;
import strategy.openers.Opener;

import java.util.Map;

// TODO: Move into map dir
public class Debug {
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
        game.drawTextScreen(4, 16, "Map: " + game.mapFileName());
        game.drawTextScreen(4, 24, "Opener: " + opener.getNameString() + " " + getOpenerRecord(), Text.White);
        game.drawTextScreen(4, 32, "Strategy: " + gameState.getActiveStrategy().getName() + " " + getStrategyRecord(), Text.White);
        game.drawTextScreen(4, 40, "Frame: " + game.getFrameCount());

        drawUnitCount();
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
}
