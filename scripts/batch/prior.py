"""Generate src/main/resources/learning-prior.csv, the per-race learning prior bundled in the jar.

Input is a games.csv of cold-start games (one row per decided game; the columns used are run, opponent, race,
file_race, win, gnum, index, opener, build), as built by the IA-471 data study's extract.py from the batch
manifests, game directories and learning files.

For each race, each opener and each build is rated by its first exposures: the first game an arm played in a
run against an opponent, counted only while the opponent's game number is within --window (games 1-10 by
default). The rate is opponent-balanced, the mean over opponents of each opponent's first-exposure win rate, so a
bot that was played many times does not outweigh the others. It is then shrunk toward the race mean (the same
balanced rate over every arm of that kind) with --shrink pseudo-observations. The pseudo-games are --games
(default 3), or --loser-games (default 5) for an arm whose shrunk rate is at or below --loser-rate with at least
--loser-min first exposures. Pseudo-wins are the product of the shrunk rate and the pseudo-games, to two decimals.
Only runs whose id (a start timestamp) is at or after --since count (default 20261001, the October runs the
study recommends from). Arms
with fewer than --min-n first exposures are left out and stay untried.

A bot that plays Random (Dave Churchill, Randomhammer) is keyed Unknown whatever race it rolled.
A build equal to the opener is no transition and is not rated as a build.

Race is Terran, Protoss, Zerg or Unknown (a Random opponent). The legacy build SpeedlingAllIn is written under
the IA-470 per-race name SpeedlingT, SpeedlingP or SpeedlingZ by the race the game resolved to (the race column),
which for a Random bot is the rolled race; with no resolved race it counts toward no name, as the bot's load path leaves
such a row unmapped.

Command line used for the committed file (run from the repository root):

    py scripts/batch/prior.py C:/Users/bradl/orca/workspaces/InfestedArtosis/beta-459-468/tmp/evals/scratch/PRIOR/games.csv src/main/resources/learning-prior.csv
"""
import argparse
import csv
import sys
from collections import defaultdict
from pathlib import Path

RANDOM_RACES = {"Random", "Unknown"}
RANDOM_BOTS = {"Dave Churchill", "Randomhammer"}
SPEEDLING_BY_RACE = {"Terran": "SpeedlingT", "Protoss": "SpeedlingP", "Zerg": "SpeedlingZ"}
LEGACY_SPEEDLING = "SpeedlingAllIn"
KINDS = (("opener", "opener"), ("build", "build"))
RACE_ORDER = ("Terran", "Protoss", "Zerg", "Unknown")
HEADER = ["race", "kind", "arm", "pseudo_wins", "pseudo_games"]


def race_key(row, random_bots=RANDOM_BOTS):
    if row["race"] in RANDOM_RACES or row["opponent"] in random_bots:
        return "Unknown"
    return row["race"]


def arm_names(kind, name, race, resolved=""):
    """Returns the arm names a row counts toward. SpeedlingAllIn follows the race the game resolved to; a Random
    row whose resolved race is unknown counts toward none."""
    if kind == "build" and name == LEGACY_SPEEDLING:
        if race != "Unknown":
            return [SPEEDLING_BY_RACE[race]]
        if resolved in SPEEDLING_BY_RACE:
            return [SPEEDLING_BY_RACE[resolved]]
        return []
    return [name]


def load_rows(path):
    with open(path, encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    for row in rows:
        row["win"] = int(row["win"])
        row["gnum"] = int(row["gnum"])
        row["index"] = int(row["index"])
    rows.sort(key=lambda r: (r["run"], r["opponent"], r["gnum"], r["index"]))
    return rows


def first_exposures(rows, kind, window, random_bots=RANDOM_BOTS):
    """Returns (race, arm, opponent, win) for the first game of each arm per run and opponent within the window."""
    seen = set()
    exposures = []
    for row in rows:
        race = race_key(row, random_bots)
        if not row[kind] or kind == "build" and row[kind] == row["opener"]:
            continue
        for arm in arm_names(kind, row[kind], race, row["race"]):
            key = (row["run"], row["opponent"], arm)
            if key in seen:
                continue
            seen.add(key)
            if row["gnum"] <= window:
                exposures.append((race, arm, row["opponent"], row["win"]))
    return exposures


def balanced_rate(observations):
    """Mean over opponents of each opponent's win rate; observations are (opponent, win) pairs."""
    per_opponent = defaultdict(lambda: [0, 0])
    for opponent, win in observations:
        per_opponent[opponent][0] += win
        per_opponent[opponent][1] += 1
    if not per_opponent:
        return 0.0
    return sum(wins / games for wins, games in per_opponent.values()) / len(per_opponent)


def shrink(rate, n, mean, strength):
    return (n * rate + strength * mean) / (n + strength)


def build_prior(rows, window=10, shrink_strength=10, games=3, loser_games=5, loser_rate=0.10, loser_min=20,
                min_n=5, since="", random_bots=RANDOM_BOTS):
    rows = [row for row in rows if row["run"] >= since]
    prior = []
    for kind, label in KINDS:
        by_race = defaultdict(list)
        for race, arm, opponent, win in first_exposures(rows, kind, window, random_bots):
            by_race[race].append((arm, opponent, win))
        for race in RACE_ORDER:
            observations = by_race.get(race, [])
            race_mean = balanced_rate([(opponent, win) for _, opponent, win in observations])
            by_arm = defaultdict(list)
            for arm, opponent, win in observations:
                by_arm[arm].append((opponent, win))
            for arm in sorted(by_arm):
                n = len(by_arm[arm])
                if n < min_n:
                    continue
                rate = shrink(balanced_rate(by_arm[arm]), n, race_mean, shrink_strength)
                pseudo_games = loser_games if rate <= loser_rate and n >= loser_min else games
                pseudo_wins = round(min(1.0, rate) * pseudo_games, 2)
                prior.append((race, label, arm, pseudo_wins, pseudo_games))
    return prior


def write_prior(prior, path):
    with open(path, "w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(HEADER)
        writer.writerows(prior)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("games_csv")
    parser.add_argument("output")
    parser.add_argument("--window", type=int, default=10)
    parser.add_argument("--shrink", type=float, default=10)
    parser.add_argument("--games", type=int, default=3)
    parser.add_argument("--loser-games", type=int, default=5)
    parser.add_argument("--loser-rate", type=float, default=0.10)
    parser.add_argument("--loser-min", type=int, default=20)
    parser.add_argument("--min-n", type=int, default=5)
    parser.add_argument("--since", default="20261001")
    parser.add_argument("--random-bots", nargs="*", default=sorted(RANDOM_BOTS))
    args = parser.parse_args(argv)
    prior = build_prior(load_rows(args.games_csv), args.window, args.shrink, args.games, args.loser_games,
                        args.loser_rate, args.loser_min, args.min_n, args.since, set(args.random_bots))
    write_prior(prior, Path(args.output))
    print(f"wrote {len(prior)} rows to {args.output}")


if __name__ == "__main__":
    sys.exit(main())
