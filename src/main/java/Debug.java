import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwem.BWEM;
import bwem.Base;
import learning.OpponentRecord;
import learning.OpenerRecord;
import strategy.Opener;

// TODO: Move into map dir
public class Debug {
    private BWEM bwem;
    private Game game;

    private Opener opener;
    private OpponentRecord opponentRecord;

    public Debug(BWEM bwem, Game game, Opener opener, OpponentRecord opponentRecord) {
        this.bwem = bwem;
        this.game = game;
        this.opener = opener;
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
        game.drawTextScreen(4, 24, "Opener: " + opener.getName() + " " + getOpenerRecord(), Text.White);
        game.drawTextScreen(4, 40, "Frame: " + game.getFrameCount());
    }

    private String getOpenerRecord() {
        OpenerRecord openerRecord = opponentRecord.getOpenerRecord().get(opener.getName());
        return String.format("%s_%s", openerRecord.getWins(), openerRecord.getLosses());
    }

    private String getOpponentRecord() {
        return String.format("%s_%s", opponentRecord.getWins(), opponentRecord.getLosses());
    }
}
