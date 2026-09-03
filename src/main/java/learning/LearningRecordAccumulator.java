package learning;

import bwapi.Race;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class LearningRecordAccumulator {
    private final String opponentName;
    private final Race opponentRace;

    LearningRecordAccumulator(String opponentName, Race opponentRace) {
        this.opponentName = opponentName;
        this.opponentRace = opponentRace;
    }

    OpponentRecord reconstruct(LearningHistory history) {
        OpponentRecord opponent = OpponentRecord.builder()
                .name(opponentName)
                .race(opponentRace.toString())
                .wins(0)
                .losses(0)
                .openerRecord(new HashMap<>())
                .buildOrderRecord(new HashMap<>())
                .mapSpecificOpenerRecord(new HashMap<>())
                .mapSpecificBuildOrderRecord(new HashMap<>())
                .build();
        for (GameRecord game : history.games()) {
            apply(opponent, game);
        }
        return opponent;
    }

    void apply(OpponentRecord opponent, GameRecord game) {
        opponent.getGameTimestamps().add(game.getTimestamp());
        incrementOpponent(opponent, game.isWinner());
        incrementRecord(opponent.getOpenerRecord(), game.getOpener(), game);
        incrementMapRecord(opponent.getMapSpecificOpenerRecord(), game.getOpener(), game);
        creditBuildOrders(opponent, game);
    }

    /**
     * Credits the game result to every distinct build order in the row's semicolon separated
     * chain, skipping blank segments and values equal to the opener, so one game contributes at
     * most one observation per build order.
     */
    private void creditBuildOrders(OpponentRecord opponent, GameRecord game) {
        if (game.getBuildOrder() == null) {
            return;
        }
        Set<String> credited = new HashSet<>();
        for (String segment : game.getBuildOrder().split(";")) {
            String name = segment.trim();
            if (name.isEmpty() || name.equals(game.getOpener()) || !credited.add(name)) {
                continue;
            }
            incrementRecord(opponent.getBuildOrderRecord(), name, game);
            incrementMapRecord(opponent.getMapSpecificBuildOrderRecord(), name, game);
        }
    }

    private void incrementOpponent(OpponentRecord opponent, boolean winner) {
        if (winner) {
            opponent.setWins(opponent.getWins() + 1);
        } else {
            opponent.setLosses(opponent.getLosses() + 1);
        }
    }

    private void incrementRecord(Map<String, Record> records, String strategy, GameRecord game) {
        Record record = records.get(strategy);
        if (record == null) {
            record = Record.builder().opener(strategy).wins(0).losses(0).build();
            records.put(strategy, record);
        }
        if (game.isWinner()) {
            record.setWins(record.getWins() + 1);
            record.addWinTimestamp(game.getTimestamp());
        } else {
            record.setLosses(record.getLosses() + 1);
            record.addLossTimestamp(game.getTimestamp());
        }
    }

    private void incrementMapRecord(Map<String, MapAwareRecord> records, String strategy, GameRecord game) {
        String key = WeightedUCBCalculator.createMapKey(game.getMapName(), strategy);
        MapAwareRecord record = records.get(key);
        if (record == null) {
            record = MapAwareRecord.builder()
                    .strategy(strategy)
                    .mapName(game.getMapName())
                    .opponentName(opponentName)
                    .opponentRace(opponentRace.toString())
                    .wins(0)
                    .losses(0)
                    .build();
            records.put(key, record);
        }
        if (game.isWinner()) {
            record.setWins(record.getWins() + 1);
            record.addWinTimestamp(game.getTimestamp());
        } else {
            record.setLosses(record.getLosses() + 1);
            record.addLossTimestamp(game.getTimestamp());
        }
    }
}
