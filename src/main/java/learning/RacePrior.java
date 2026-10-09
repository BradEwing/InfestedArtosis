package learning;

import bwapi.Race;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-race learning prior bundled in the jar as {@value #RESOURCE}: for each opponent race, the pseudo-games
 * (wins out of games) each opener and build order starts with against an opponent with a short history. A
 * Random opponent, whose race is Unknown until scouted, uses the Unknown rows. A missing or unreadable
 * resource is an empty prior, and malformed rows are skipped.
 */
final class RacePrior {

    static final String RESOURCE = "/learning-prior.csv";
    static final String KIND_OPENER = "opener";
    static final String KIND_BUILD = "build";
    static final String UNKNOWN_RACE = "Unknown";

    /**
     * History length from which the prior is no longer seeded; its weight is then GAMMA to the power of this.
     */
    static final int HISTORY_HORIZON_GAMES = 50;

    /**
     * One arm's pseudo-games.
     */
    static final class Arm {
        private final String name;
        private final double wins;
        private final double games;

        Arm(String name, double wins, double games) {
            this.name = name;
            this.wins = wins;
            this.games = games;
        }

        String name() {
            return name;
        }

        double wins() {
            return wins;
        }

        double games() {
            return games;
        }
    }

    /**
     * What seeding did for one game: whether the prior applied and how many openers and builds it seeded.
     */
    static final class Report {
        private final boolean applied;
        private final String race;
        private final int openers;
        private final int builds;

        Report(boolean applied, String race, int openers, int builds) {
            this.applied = applied;
            this.race = race;
            this.openers = openers;
            this.builds = builds;
        }

        boolean applied() {
            return applied;
        }

        int openers() {
            return openers;
        }

        int builds() {
            return builds;
        }

        /**
         * Returns the telemetry label: applied=y|n;race=...;openers=N;builds=N.
         */
        String label() {
            return "applied=" + (applied ? "y" : "n") + ";race=" + race + ";openers=" + openers + ";builds=" + builds;
        }
    }

    private static final RacePrior EMPTY = new RacePrior(Collections.emptyMap());

    private final Map<String, List<Arm>> arms;

    private RacePrior(Map<String, List<Arm>> arms) {
        this.arms = arms;
    }

    static RacePrior empty() {
        return EMPTY;
    }

    /**
     * Loads the bundled prior, or the empty prior when the resource is missing or unreadable.
     */
    static RacePrior load() {
        try (InputStream stream = RacePrior.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                return EMPTY;
            }
            return parse(stream);
        } catch (IOException | RuntimeException e) {
            return EMPTY;
        }
    }

    static RacePrior parse(InputStream stream) throws IOException {
        Map<String, List<Arm>> parsed = new HashMap<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            parseRow(line, parsed);
        }
        return new RacePrior(parsed);
    }

    private static void parseRow(String line, Map<String, List<Arm>> parsed) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return;
        }
        String[] fields = trimmed.split(",");
        if (fields.length != 5) {
            return;
        }
        String race = fields[0].trim();
        String kind = fields[1].trim();
        String name = fields[2].trim();
        if (name.isEmpty() || !KIND_OPENER.equals(kind) && !KIND_BUILD.equals(kind)) {
            return;
        }
        try {
            double pseudoWins = Double.parseDouble(fields[3].trim());
            double pseudoGames = Double.parseDouble(fields[4].trim());
            if (!(pseudoGames > 0) || !(pseudoWins >= 0) || Double.isInfinite(pseudoGames) || Double.isInfinite(pseudoWins)) {
                return;
            }
            parsed.computeIfAbsent(key(race, kind), k -> new ArrayList<>()).add(new Arm(name, Math.min(pseudoWins, pseudoGames), pseudoGames));
        } catch (NumberFormatException e) {
            return;
        }
    }

    private static String key(String race, String kind) {
        return race + "|" + kind;
    }

    /**
     * Returns the prior key for an opponent race: its name for Terran, Protoss and Zerg, Unknown otherwise.
     */
    static String raceKey(Race race) {
        if (race == Race.Terran || race == Race.Protoss || race == Race.Zerg) {
            return race.toString();
        }
        return UNKNOWN_RACE;
    }

    List<Arm> arms(String race, String kind) {
        return arms.getOrDefault(key(race, kind), Collections.emptyList());
    }

    /**
     * Seeds the race's prior when the switch is on and the opponent has fewer than {@link #HISTORY_HORIZON_GAMES} games
     * of history, otherwise seeds nothing. The prior is seeded afresh each game, since the record is rebuilt from the
     * history file, and decays by GAMMA per real game, so it is below 10% of its weight at the horizon.
     * The pseudo-games go onto the opponent record's existing opener and build order records. An arm the record
     * does not hold, because it is not a legal candidate, is ignored, and an arm absent from the prior stays untried.
     */
    Report seedIfNew(boolean enabled, int historyGames, OpponentRecord opponentRecord,
                     LearningRecordAccumulator accumulator, String race) {
        if (!enabled || historyGames >= HISTORY_HORIZON_GAMES) {
            return new Report(false, race, 0, 0);
        }
        return seed(opponentRecord, accumulator, race);
    }

    private Report seed(OpponentRecord opponentRecord, LearningRecordAccumulator accumulator, String race) {
        int openers = 0;
        for (Arm arm : arms(race, KIND_OPENER)) {
            if (accumulator.applyPrior(opponentRecord, true, arm.name(), arm.wins(), arm.games())) {
                openers++;
            }
        }
        int builds = 0;
        for (Arm arm : arms(race, KIND_BUILD)) {
            if (accumulator.applyPrior(opponentRecord, false, arm.name(), arm.wins(), arm.games())) {
                builds++;
            }
        }
        return new Report(openers + builds > 0, race, openers, builds);
    }
}
