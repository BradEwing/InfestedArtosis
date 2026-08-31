"""Compact sitrep view over a batch launched by run.py.

Complements report.py with two things report.py does not do:

* per-opponent racial win-rate split with a ZvA (all races) column
* TOTAL / COMBINED rows on both tables

Reads each completed game's ``write_0/{opponent}_*.csv`` and takes the LAST row,
which is that game's own learning row. This works in BOTH accumulate and frozen
mode -- ``read/`` is never written in frozen mode, so it cannot be used here.

Games that played fewer than MIN_GAME_FRAMES frames never really started (BWAPI
pipe failures stop near 1,500 frames and are reported as DRAW by scbw). They are
counted separately and excluded from win rates.

Usage:
  py scripts/batch/sitrep.py [--run latest|<runid>]
"""

import argparse
import collections
import csv
import glob
import json
import os
import statistics

APPDATA = os.environ.get("APPDATA", "")
BATCH_DIR = os.path.join(APPDATA, "scbw", "batches")
GAME_DIR = os.path.join(APPDATA, "scbw", "games")

MIN_GAME_FRAMES = 2000
RACES = ("Protoss", "Terran", "Zerg")


def resolve_run(run):
    if run and run != "latest":
        return os.path.join(BATCH_DIR, run + ".json")
    manifests = glob.glob(os.path.join(BATCH_DIR, "*.json"))
    if not manifests:
        raise SystemExit("no batch manifests found in %s" % BATCH_DIR)
    return max(manifests, key=os.path.getmtime)


def game_frames(game_name):
    """Frames actually played, from the last ``frame_count`` in frames.csv.

    Counting rows instead understates the game by the sample interval: frames.csv
    records one row per 24 frames, so a real but short game lands under 200 rows.
    A 4Pool rush win against a Zerg opponent ends near 4,745 frames -- 198 rows --
    and a row-count threshold of 200 discarded every one of them. Only wins end
    that fast, so the row-count test could not discard a loss and biased the win
    rate down on exactly the arm it was most needed to measure.
    """
    path = os.path.join(GAME_DIR, "GAME_" + game_name, "logs_0", "frames.csv")
    if not os.path.exists(path):
        return 0
    last = 0
    try:
        with open(path, newline="") as handle:
            for row in csv.DictReader(handle):
                last = int(row.get("frame_count") or 0)
    except (OSError, ValueError):
        return 0
    return last


def last_write_row(game_name, opponent):
    pattern = os.path.join(GAME_DIR, "GAME_" + game_name, "write_0", opponent + "_*.csv")
    rows = []
    for path in glob.glob(pattern):
        try:
            with open(path, newline="") as handle:
                rows = list(csv.DictReader(handle))
        except OSError:
            continue
    return rows[-1] if rows else None


def is_win(row):
    return str(row.get("is_winner", "")).strip().lower() in ("true", "1")


def pct(won, lost):
    total = won + lost
    return "%d%%" % round(100.0 * won / total) if total else "-"


def cell(record):
    won, lost = record
    return "%dW-%dL %s" % (won, lost, pct(won, lost)) if won + lost else "-"


def collect(manifest):
    outcomes = collections.defaultdict(collections.Counter)
    failed = collections.Counter()
    by_race = collections.defaultdict(lambda: collections.defaultdict(lambda: [0, 0]))
    openers = collections.defaultdict(lambda: [0, 0])
    lengths = collections.defaultdict(list)

    for game in manifest.get("games", []):
        outcome = game.get("outcome")
        if not outcome:
            continue
        opponent = game["opponent"]
        outcomes[opponent][outcome] += 1

        if game_frames(game["game_name"]) < MIN_GAME_FRAMES:
            failed[opponent] += 1
            continue

        row = last_write_row(game["game_name"], opponent)
        if row is None:
            continue
        race = row.get("opponent_race", "Unknown")
        if race == "Unknown":
            continue

        index = 0 if is_win(row) else 1
        by_race[opponent][race][index] += 1
        openers[row.get("opener", "?")][index] += 1
        count = row.get("frame_count", "")
        if str(count).isdigit():
            lengths[opponent].append(int(count))

    return outcomes, failed, by_race, openers, lengths


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", default="latest", help="run id or 'latest'")
    args = parser.parse_args()

    path = resolve_run(args.run)
    with open(path) as handle:
        manifest = json.load(handle)

    outcomes, failed, by_race, openers, lengths = collect(manifest)

    launched = len([g for g in manifest.get("games", []) if g.get("launched_at")])
    planned = manifest.get("games_per_opponent", 0) * len(manifest.get("opponents", []))
    mode = "frozen" if manifest.get("frozen") else "accumulate"

    print("Batch %s | %s" % (manifest["run_id"], manifest.get("status", "IN PROGRESS")))
    print("  jar %s (%s) | git %s | mode %s | jobs %s | %d/%d launched"
          % (manifest.get("jar_version"), str(manifest.get("jar_sha256", ""))[:12],
             manifest.get("git_rev"), mode, manifest.get("jobs"), launched, planned))

    print()
    print("By opponent")
    print("  %-18s %6s %5s %5s %5s %5s %5s %7s" %
          ("OPPONENT", "WR", "W", "L", "D", "T", "FAIL", "MED_MIN"))
    totals = collections.Counter()
    total_failed = 0
    for opponent in sorted(outcomes, key=lambda o: -(outcomes[o]["WIN"] /
                           max(1, outcomes[o]["WIN"] + outcomes[o]["LOSS"]))):
        counts = outcomes[opponent]
        won, lost = counts["WIN"], counts["LOSS"]
        med = statistics.median(lengths[opponent]) / 24.0 / 60.0 if lengths[opponent] else 0
        print("  %-18s %6s %5d %5d %5d %5d %5d %7.1f"
              % (opponent, pct(won, lost), won, lost, counts["DRAW"],
                 counts["TIMEOUT"], failed[opponent], med))
        totals.update(counts)
        total_failed += failed[opponent]
    all_len = [v for vals in lengths.values() for v in vals]
    print("  %-18s %6s %5d %5d %5d %5d %5d %7.1f"
          % ("TOTAL", pct(totals["WIN"], totals["LOSS"]), totals["WIN"], totals["LOSS"],
             totals["DRAW"], totals["TIMEOUT"], total_failed,
             statistics.median(all_len) / 24.0 / 60.0 if all_len else 0))

    print()
    print("Racial win rate (Unknown-race rows excluded)")
    header = "  %-18s" % "OPPONENT" + "".join("%-16s" % ("Zv" + r[0]) for r in RACES) + "%-16s" % "ZvA"
    print(header)
    combined = collections.defaultdict(lambda: [0, 0])
    for opponent in sorted(by_race):
        cells = []
        agg = [0, 0]
        for race in RACES:
            record = by_race[opponent][race]
            cells.append(cell(record))
            agg[0] += record[0]
            agg[1] += record[1]
            combined[race][0] += record[0]
            combined[race][1] += record[1]
        cells.append(cell(agg))
        print("  %-18s" % opponent + "".join("%-16s" % c for c in cells))

    grand = [0, 0]
    cells = []
    for race in RACES:
        record = combined[race]
        cells.append(cell(record))
        grand[0] += record[0]
        grand[1] += record[1]
    cells.append(cell(grand))
    print("  %-18s" % "COMBINED" + "".join("%-16s" % c for c in cells))

    print()
    ordered = sorted(openers.items(), key=lambda kv: -(kv[1][0] + kv[1][1]))
    print("Opener: " + " | ".join("%s %d-%d (%s)" % (name, rec[0], rec[1], pct(*rec))
                                  for name, rec in ordered))
    if total_failed:
        print("Excluded %d failed-launch game(s) (<%d frames played; BWAPI pipe failure "
              "reported as DRAW)." % (total_failed, MIN_GAME_FRAMES))


if __name__ == "__main__":
    main()
