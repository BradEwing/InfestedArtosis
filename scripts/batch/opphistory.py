"""Join one of our games to the row an opponent's own history file wrote for it.

Usage:
  py scripts/batch/opphistory.py <run-id> <game-name> <history-file> [--tolerance SECONDS]

Supported opponent files, recognised by file name and shape:

* PurpleWave: write_1/_v4_history_<bot>.csv. One comma-separated row per game, NEWEST FIRST. Column 0 is the
  game's wall-clock epoch in milliseconds, column 2 the map, columns 8 onward the strategy tokens; tokens
  starting with '&' are PurpleWave's read of us, the rest are its own plan.
* Microwave: history_<bot>.txt. One semicolon-separated row per game, OLDEST FIRST, starting with a version
  tag such as "v2.7". Field 1 is the game's wall-clock epoch in seconds, field 3 the map, field 10 the strategy.

A row is joined by game id when a field of the row equals the game's name, otherwise by the nearest timestamp:
the row whose time falls inside the game's launch-to-finish window, or within tolerance_s of it, and on the
game's map when the row names one. Files in any other shape return None, as does a game with no usable
timestamp. Two rows equally near the game return None rather than a guess. The join never takes the first or
last row by position.

Game timestamps are the manifest's launched_at and finished_at in local time, and the opponents stamp rows with
epoch time, so the join is correct only when it runs in the timezone of the machine that played the batch.
PurpleWave stamps a row at game end and Microwave at game start; the launch-to-finish window covers both.
"""

import argparse
import sys
from datetime import datetime
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import batchlib as bl

DEFAULT_TOLERANCE_S = 60.0

PURPLEWAVE_FORMAT = "purplewave_v4_history"
MICROWAVE_FORMAT = "microwave_history"


def detect_format(path, lines):
    """The supported format a history file is in, or None."""
    name = Path(path).name
    if name.startswith("_v4_history") and lines and lines[0].count(",") >= 8:
        return PURPLEWAVE_FORMAT
    if name.startswith("history_") and lines and lines[0].startswith("v") and lines[0].count(";") >= 10:
        return MICROWAVE_FORMAT
    return None


def parse_row(fmt, line):
    """(timestamp_seconds, map_name, fields) for one row, or None when the row is malformed."""
    try:
        if fmt == PURPLEWAVE_FORMAT:
            fields = [f.strip() for f in line.split(",")]
            return int(fields[0]) / 1000.0, fields[2], fields
        fields = [f.strip() for f in line.split(";")]
        return float(int(fields[1])), fields[3], fields
    except (ValueError, IndexError):
        return None


def game_window(game):
    """(launch, finish) epoch seconds from the manifest entry, or None without a launch time.
    A game that never finished is a point at its launch."""
    try:
        launch = datetime.fromisoformat(game["launched_at"]).timestamp()
        finish = datetime.fromisoformat(game["finished_at"]).timestamp() if game.get("finished_at") else launch
    except (KeyError, ValueError):
        return None
    return launch, max(launch, finish)


def distance_to_window(timestamp, window):
    launch, finish = window
    if timestamp < launch:
        return launch - timestamp
    return max(0.0, timestamp - finish)


def history_row(game, path, tolerance_s=DEFAULT_TOLERANCE_S):
    """The opponent's row for game as {"format", "timestamp", "map", "fields", "line"}, or None.

    game is a manifest entry with game_name, launched_at, optionally finished_at and map."""
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            lines = [line for line in f.read().splitlines() if line.strip()]
    except OSError:
        return None
    fmt = detect_format(path, lines)
    if fmt is None:
        return None
    rows = []
    for line in lines:
        parsed = parse_row(fmt, line)
        if parsed is not None:
            rows.append((parsed[0], parsed[1], parsed[2], line))

    def build(row):
        return {"format": fmt, "timestamp": row[0], "map": row[1], "fields": row[2], "line": row[3]}

    for row in rows:
        if game["game_name"] in row[2]:
            return build(row)
    window = game_window(game)
    if window is None:
        return None
    wanted_map = game.get("map")
    candidates = [r for r in rows if distance_to_window(r[0], window) <= tolerance_s]
    on_map = [r for r in candidates if r[1] == wanted_map]
    candidates = on_map or [r for r in candidates if not r[1] or not wanted_map]
    if not candidates:
        return None
    ranked = sorted(candidates, key=lambda r: distance_to_window(r[0], window))
    if len(ranked) > 1 and distance_to_window(ranked[0][0], window) == distance_to_window(ranked[1][0], window):
        return None
    return build(ranked[0])


def purplewave_strategy(row):
    """PurpleWave's own plan tokens from a joined row, without the '&' tokens that are its read of us."""
    return [f for f in row["fields"][8:] if f and not f.startswith("&")]


def parse_args():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("run_id")
    p.add_argument("game_name")
    p.add_argument("history_file")
    p.add_argument("--tolerance", type=float, default=DEFAULT_TOLERANCE_S, help="seconds of slack on the window")
    return p.parse_args()


def main():
    args = parse_args()
    manifest = bl.load_manifest(bl.resolve_run_id(args.run_id))
    games = [g for g in manifest.get("games", []) if g["game_name"] == args.game_name]
    if not games:
        raise SystemExit(f"No game {args.game_name} in run {args.run_id}")
    row = history_row(games[0], args.history_file, args.tolerance)
    print("None" if row is None else row["line"])


if __name__ == "__main__":
    main()
