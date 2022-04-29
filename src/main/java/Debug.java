import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwem.BWEM;
import bwem.Base;
import learning.OpponentRecord;
import learning.StrategyRecord;
import strategy.Strategy;

// TODO: Move into map dir
public class Debug {
    private BWEM bwem;
    private Game game;

    private Strategy strategy;
    private OpponentRecord opponentRecord;

    public Debug(BWEM bwem, Game game, Strategy strategy, OpponentRecord opponentRecord) {
        this.bwem = bwem;
        this.game = game;
        this.strategy = strategy;
        this.opponentRecord = opponentRecord;
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
        game.drawTextScreen(4, 24, "Strategy: " + strategy.getName() + " " + getStrategyRecord(), Text.White);
        game.drawTextScreen(4, 40, "Frame: " + game.getFrameCount());
    }

    private String getStrategyRecord() {
        StrategyRecord strategyRecord = opponentRecord.getOpponentStrategies().get(strategy.getName());
        return String.format("%s_%s", strategyRecord.getWins(), strategyRecord.getLosses());
    }

    private String getOpponentRecord() {
        return String.format("%s_%s", opponentRecord.getWins(), opponentRecord.getLosses());
    }
}
