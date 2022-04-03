import bwapi.Color;
import bwapi.Game;
import bwapi.Position;
import bwapi.Text;
import bwem.BWEM;
import bwem.Base;

// TODO: Move into map dir
public class DebugMap {
    private BWEM bwem;
    private Game game;

    public DebugMap(BWEM bwem, Game game) {
        this.bwem = bwem;
        this.game = game;
    }

    public void onFrame() {
        // Draw bases
        for (final Base b : bwem.getMap().getBases()) {
            game.drawBoxMap(
                    b.getLocation().toPosition(),
                    b.getLocation().toPosition().add(new Position(127, 95)),
                    Color.Blue);
        }

        game.drawTextScreen(4, 16, "Map: " + game.mapFileName());
        game.drawTextScreen(4, 32, "Strategy: ", Text.White);
        game.drawTextScreen(56, 32, "9Pool ZvA", Text.BrightRed);
        game.drawTextScreen(4, 48, "Frame: " + game.getFrameCount());
    }
}
