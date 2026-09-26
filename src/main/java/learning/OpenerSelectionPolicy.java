package learning;

import info.tracking.terran.TerranWall;
import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Selects the opener for a game: the configured override, then the rush response, then weighted D-UCB over the
 * playable openers. 4Pool is left out of the D-UCB candidates when the previous game detected a Terran wall.
 */
final class OpenerSelectionPolicy {

    static final String FOUR_POOL = "4Pool";

    private OpenerSelectionPolicy() {
    }

    static String select(String openerOverride,
                         BuildOrderFactory buildOrderFactory,
                         OpponentRecord opponentRecord,
                         String lastGameDetectedStrategies,
                         String lastGameOpener,
                         String mapName) {
        if (openerOverride != null) {
            BuildOrder forced = buildOrderFactory.getByName(openerOverride);
            if (forced != null && buildOrderFactory.isPlayableOpener(forced)) {
                return forced.getName();
            }
        }

        boolean isRusher = lastGameDetectedStrategies.contains("CannonRush")
                || lastGameDetectedStrategies.contains("SCVRush");
        if (isRusher) {
            BuildOrder overpool = buildOrderFactory.getByName("Overpool");
            if (overpool != null && buildOrderFactory.isPlayableOpener(overpool)) {
                return overpool.getName();
            }
        }

        List<String> playableOpeners = opponentRecord.getOpenerRecord()
                .keySet()
                .stream()
                .filter(name -> buildOrderFactory.isPlayableOpener(buildOrderFactory.getByName(name)))
                .collect(Collectors.toList());
        if (TerranWall.isWallIn(lastGameDetectedStrategies)) {
            playableOpeners.remove(FOUR_POOL);
        }
        if (LearningManager.isBarredFromImmediateRepeat(lastGameOpener, playableOpeners, opponentRecord)) {
            playableOpeners.removeIf(name -> name.equals(lastGameOpener));
        }
        if (playableOpeners.isEmpty()) {
            return null;
        }

        String winner = WeightedUCBCalculator.findBestStrategy(
                playableOpeners,
                mapName,
                opponentRecord.getMapSpecificOpenerRecord(),
                opponentRecord.getOpenerRecord(),
                opponentRecord.totalGames(),
                opponentRecord.getGameTimestamps());
        return LearningManager.applyDormantReprobePolicy(winner, playableOpeners, opponentRecord, mapName);
    }
}
