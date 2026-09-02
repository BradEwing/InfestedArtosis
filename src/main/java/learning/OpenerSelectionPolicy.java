package learning;

import strategy.BuildOrderFactory;
import strategy.buildorder.BuildOrder;

import java.util.List;
import java.util.stream.Collectors;

final class OpenerSelectionPolicy {
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
